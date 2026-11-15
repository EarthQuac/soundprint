package digital.anchorstudio.soundprint

import android.content.Context
import android.net.Uri
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

    fun enqueueFolder(context: Context, treeUri: Uri, useCustomArtwork: Boolean = true): UUID =
        enqueue(context, "folder", listOf(treeUri), useCustomArtwork)

    fun enqueueFiles(context: Context, uris: List<Uri>, useCustomArtwork: Boolean = true): UUID {
        require(uris.isNotEmpty()) { "Choose at least one audio file." }
        return enqueue(context, "files", uris.distinctBy(Uri::toString), useCustomArtwork)
    }

    private fun enqueue(context: Context, type: String, uris: List<Uri>, useCustomArtwork: Boolean): UUID {
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
            val useCustomArtwork = payload.optBoolean("useCustomArtwork", true)
            val urisJson = payload.getJSONArray("uris")
            val uris = buildList {
                for (index in 0 until urisJson.length()) add(Uri.parse(urisJson.getString(index)))
            }
            require(uris.isNotEmpty()) { "The import request contains no files." }

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
