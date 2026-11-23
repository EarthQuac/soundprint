package digital.anchorstudio.soundprint

import android.content.Context
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Durable import queue. SAF grants are persisted before work is enqueued and
 * the request itself is stored on disk (WorkManager Data has a small size cap).
 */
object SoundprintMusicImportQueue {
    private const val UNIQUE_WORK = "soundprint_music_import_queue"
    private const val INPUT_REQUEST_ID = "request_id"

    fun enqueueFolder(context: Context, treeUri: Uri, useCustomArtwork: Boolean = false): UUID =
        enqueue(context, "folder", listOf(treeUri), useCustomArtwork)

    fun enqueueFiles(context: Context, uris: List<Uri>, useCustomArtwork: Boolean = true): UUID {
        require(uris.isNotEmpty()) { "Choose at least one audio file." }
        return enqueue(context, "files", uris.distinctBy(Uri::toString), useCustomArtwork)
    }

    fun enqueueUrl(context: Context, url: String, useCustomArtwork: Boolean = true): UUID {
        val parsed = Uri.parse(url.trim())
        require(parsed.scheme in listOf("https", "http") && !parsed.host.isNullOrBlank()) { "Enter a valid http or https audio link." }
        return enqueue(context, "url", emptyList(), useCustomArtwork, parsed.toString())
    }

    private fun enqueue(context: Context, type: String, uris: List<Uri>, useCustomArtwork: Boolean, sourceUrl: String? = null): UUID {
        val app = context.applicationContext
        // Some providers only hand out temporary grants; that is fine while the import runs.
        uris.forEach { uri ->
            runCatching {
                app.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }

        val id = UUID.randomUUID()
        val directory = File(app.filesDir, "music-import-requests").apply { mkdirs() }
        val destination = File(directory, "$id.json")
        val temporary = File(directory, "$id.json.tmp")
        val payload = JSONObject()
            .put("type", type)
            .put("useCustomArtwork", useCustomArtwork)
            .put("sourceUrl", sourceUrl ?: JSONObject.NULL)
            .put("uris", JSONArray().apply { uris.forEach { put(it.toString()) } })
        temporary.writeText(payload.toString(), Charsets.UTF_8)
        check(temporary.renameTo(destination)) { "Could not save the music import request." }

        // Show feedback immediately. WorkManager may take time to start a foreground
        // worker (especially when another import is already running or the device is idle).
        val notifications = SoundprintImportNotifications(app)
        notifications.queued(type)

        try {
            val request = OneTimeWorkRequestBuilder<SoundprintMusicImportWorker>()
                .setInputData(workDataOf(INPUT_REQUEST_ID to id.toString()))
                .addTag(UNIQUE_WORK)
                .build()
            WorkManager.getInstance(app).enqueueUniqueWork(
                UNIQUE_WORK,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request
            )
        } catch (error: Exception) {
            destination.delete()
            notifications.failed(error.message ?: "Soundprint couldn't queue this music import.")
            throw error
        }
        return id
    }

    internal fun requestFile(context: Context, id: String): File =
        File(File(context.applicationContext.filesDir, "music-import-requests"), "$id.json")

    internal const val NOTIFICATION_ID = 4107
    internal const val INPUT_ID = INPUT_REQUEST_ID
    internal const val PROGRESS_STAGE = "progress_stage"
    internal const val PROGRESS_TITLE = "progress_title"
    internal const val PROGRESS_TOTAL = "progress_total"
    internal const val PROGRESS_VISITED = "progress_visited"
    internal const val PROGRESS_IMPORTED = "progress_imported"
    internal const val PROGRESS_UPDATED = "progress_updated"
    internal const val PROGRESS_SKIPPED = "progress_skipped"
    internal const val PROGRESS_ERRORS = "progress_errors"
}

class SoundprintMusicImportWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {

    private fun downloadAudioToMediaStore(rawUrl: String): Uri {
        val parsed = Uri.parse(rawUrl)
        require(parsed.scheme == "https" || parsed.scheme == "http") { "Only http and https links are supported." }
        val name = parsed.lastPathSegment?.takeIf { it.contains('.') } ?: "SoundPrint-audio.mp3"
        val safeName = name.replace(Regex("[\\\\/:*?\"<>|]+"), "_").take(120)
        val extension = safeName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val supported = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "wma", "aiff", "aif", "ape", "alac", "3gp", "amr")
        require(extension in supported) { "Use a direct link to a supported audio file (MP3, M4A, FLAC, WAV, OGG and more)." }
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, safeName)
            put(MediaStore.Audio.Media.MIME_TYPE, if (extension == "mp3") "audio/mpeg" else "audio/$extension")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/SoundPrint")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }
        val resolver = applicationContext.contentResolver
        val destination = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Android could not create the imported audio file.")
        try {
            val connection = URL(rawUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "SoundPrint Android")
            try {
                connection.connect()
                require(connection.responseCode in 200..299) { "The audio link returned HTTP " + connection.responseCode + "." }
                val contentType = connection.contentType?.substringBefore(';')?.lowercase(Locale.ROOT).orEmpty()
                require(contentType.startsWith("audio/") || contentType in setOf("application/octet-stream", "binary/octet-stream")) {
                    "That link does not appear to point directly to an audio file."
                }
                require(connection.contentLengthLong <= 250L * 1024L * 1024L) { "Audio files must be 250 MB or smaller." }
                val output = resolver.openOutputStream(destination, "w")
                    ?: throw IllegalStateException("Android could not open the imported audio file.")
                var total = 0L
                connection.inputStream.use { input ->
                    output.use { out ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            if (isStopped) throw CancellationException("Import cancelled.")
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            require(total <= 250L * 1024L * 1024L) { "Audio files must be 250 MB or smaller." }
                            out.write(buffer, 0, read)
                        }
                    }
                }
                require(total > 0L) { "The audio link returned an empty file." }
            } finally {
                connection.disconnect()
            }
            if (Build.VERSION.SDK_INT >= 29) {
                resolver.update(destination, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            }
            return destination
        } catch (error: Exception) {
            resolver.delete(destination, null, null)
            throw error
        }
    }

    override suspend fun doWork(): Result {
        val requestId = inputData.getString(SoundprintMusicImportQueue.INPUT_ID)
            ?: return Result.failure()
        val requestFile = SoundprintMusicImportQueue.requestFile(applicationContext, requestId)
        val notifications = SoundprintImportNotifications(applicationContext)

        return try {
            if (!requestFile.isFile) {
                throw IllegalStateException("The saved import request is missing. Please select the music again.")
            }
            val payload = JSONObject(requestFile.readText(Charsets.UTF_8))
            val type = payload.getString("type")
            val proArtworkEntitled = applicationContext
                .getSharedPreferences("soundprint_playback_options", Context.MODE_PRIVATE)
                .getBoolean("pro_entitled", false)
            val useCustomArtwork = payload.optBoolean("useCustomArtwork", false) && proArtworkEntitled
            val urisJson = payload.getJSONArray("uris")
            val uris = buildList {
                for (index in 0 until urisJson.length()) add(Uri.parse(urisJson.getString(index)))
            }
            val sourceUrl = payload.optString("sourceUrl").takeIf { it.isNotBlank() && it != "null" }
            require(uris.isNotEmpty() || (type == "url" && sourceUrl != null)) { "The import request contains no files." }

            setForeground(notifications.foregroundInfo(
                if (type == "folder") "Scanning selected folder" else "Preparing selected audio",
                0,
                0,
                true
            ))

            setProgress(workDataOf(
                SoundprintMusicImportQueue.PROGRESS_STAGE to "Preparing",
                SoundprintMusicImportQueue.PROGRESS_TITLE to "Getting your music ready",
                SoundprintMusicImportQueue.PROGRESS_TOTAL to 0,
                SoundprintMusicImportQueue.PROGRESS_VISITED to 0,
                SoundprintMusicImportQueue.PROGRESS_IMPORTED to 0,
                SoundprintMusicImportQueue.PROGRESS_UPDATED to 0,
                SoundprintMusicImportQueue.PROGRESS_SKIPPED to 0,
                SoundprintMusicImportQueue.PROGRESS_ERRORS to 0
            ))
            val repository = MusicLibraryRepository.getInstance(applicationContext)
            val publishProgress: (LibraryScanProgress) -> Unit = { progress ->
                notifications.update(progress)
                setProgressAsync(workDataOf(
                    SoundprintMusicImportQueue.PROGRESS_STAGE to progress.stage,
                    SoundprintMusicImportQueue.PROGRESS_TITLE to progress.currentTitle,
                    SoundprintMusicImportQueue.PROGRESS_TOTAL to progress.total,
                    SoundprintMusicImportQueue.PROGRESS_VISITED to progress.visited,
                    SoundprintMusicImportQueue.PROGRESS_IMPORTED to progress.imported,
                    SoundprintMusicImportQueue.PROGRESS_UPDATED to progress.updated,
                    SoundprintMusicImportQueue.PROGRESS_SKIPPED to progress.skipped,
                    SoundprintMusicImportQueue.PROGRESS_ERRORS to progress.errors
                ))
            }
            val result = when (type) {
                "folder" -> repository.importTree(uris.single(), useCustomArtwork, publishProgress)
                "files" -> repository.importFiles(uris, useCustomArtwork, publishProgress)
                "url" -> {
                    val downloaded = downloadAudioToMediaStore(sourceUrl!!)
                    repository.importFiles(listOf(downloaded), useCustomArtwork, publishProgress)
                }
                else -> throw IllegalArgumentException("Unknown music import type.")
            }
            notifications.complete(result)
            requestFile.delete()
            Result.success(
                Data.Builder()
                    .putInt("imported", result.imported)
                    .putInt("updated", result.updated)
                    .putInt("skipped", result.skipped)
                    .putInt("errors", result.errors.size)
                    .build()
            )
        } catch (cancelled: CancellationException) {
            // WorkManager owns cancellation and will remove its foreground notification.
            notifications.dismiss()
            throw cancelled
        } catch (error: Exception) {
            requestFile.delete()
            notifications.failed(error.message ?: "Soundprint couldn't finish importing your music.")
            Result.failure(workDataOf("error" to (error.message ?: "Music import failed.")))
        }
    }
}
