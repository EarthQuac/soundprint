package digital.anchorstudio.soundprint

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

data class SoundprintTagExportRequest(
    val track: MusicTrack,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String
)

suspend fun exportTaggedMp3Copy(
    context: Context,
    request: SoundprintTagExportRequest,
    destination: Uri
) = withContext(Dispatchers.IO) {
    require(request.title.isNotBlank()) { "A title is required." }
    val resolver = context.applicationContext.contentResolver
    val input = resolver.openInputStream(Uri.parse(request.track.uri))
        ?: error("The original MP3 could not be opened.")
    val output = resolver.openOutputStream(destination, "w")
        ?: error("Android could not create the exported MP3.")
    input.use { source ->
        output.use { target ->
            val header = ByteArray(10)
            var headerRead = 0
            while (headerRead < header.size) {
                val count = source.read(header, headerRead, header.size - headerRead)
                if (count < 0) break
                headerRead += count
            }
            val hasId3 = headerRead == 10 && header[0] == 0x49.toByte() &&
                header[1] == 0x44.toByte() && header[2] == 0x33.toByte()
            target.write(buildId3v24Tag(request))
            if (!hasId3 && headerRead > 0) target.write(header, 0, headerRead)
            if (hasId3) {
                val tagSize = ((header[6].toInt() and 0x7f) shl 21) or
                    ((header[7].toInt() and 0x7f) shl 14) or
                    ((header[8].toInt() and 0x7f) shl 7) or
                    (header[9].toInt() and 0x7f)
                var remaining = tagSize.toLong().coerceAtLeast(0L)
                while (remaining > 0L) {
                    val skipped = source.skip(remaining)
                    if (skipped > 0L) remaining -= skipped
                    else {
                        if (source.read() < 0) error("The original MP3 tag is truncated.")
                        remaining--
                    }
                }
            }
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = source.read(buffer)
                if (count < 0) break
                target.write(buffer, 0, count)
            }
            target.flush()
        }
    }
}

private fun buildId3v24Tag(request: SoundprintTagExportRequest): ByteArray {
    fun syncSafe(size: Int) = byteArrayOf(
        ((size shr 21) and 0x7f).toByte(),
        ((size shr 14) and 0x7f).toByte(),
        ((size shr 7) and 0x7f).toByte(),
        (size and 0x7f).toByte()
    )
    fun frame(id: String, value: String): ByteArray {
        val body = byteArrayOf(3) + value.toByteArray(Charsets.UTF_8)
        return ByteArrayOutputStream().apply {
            write(id.toByteArray(Charsets.US_ASCII))
            write(syncSafe(body.size))
            write(byteArrayOf(0, 0))
            write(body)
        }.toByteArray()
    }
    val frames = listOf(
        frame("TIT2", request.title.trim()),
        frame("TPE1", request.artist.trim()),
        frame("TALB", request.album.trim()),
        frame("TCON", request.genre.trim())
    )
    val body = ByteArrayOutputStream().apply { frames.forEach(::write) }.toByteArray()
    return ByteArrayOutputStream().apply {
        write(byteArrayOf(0x49, 0x44, 0x33, 4, 0, 0))
        write(syncSafe(body.size))
        write(body)
    }.toByteArray()
}
