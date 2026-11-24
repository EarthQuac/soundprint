package digital.anchorstudio.soundprint

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private const val TWINSENSE_VERSION = 2
private const val FEATURE_BANDS = 24
private const val FFT_SIZE = 4096
// TwinSense fingerprints a bounded opening sample instead of retaining an entire decoded song in RAM.
private const val MAX_PCM_SAMPLES = 4_000_000
// TwinSense works on decoded PCM, never on the compressed file bytes. Keep both
// the decoder buffer and the resampled analysis buffer explicitly bounded.
private const val MAX_SOURCE_FILE_BYTES = 1_073_741_824L
private const val MAX_RESAMPLED_SAMPLES = 12_000_000
private const val MAX_TRACK_DURATION_MS = 12L * 60L * 60L * 1000L

data class TwinSenseFingerprint(
    val trackId: String,
    val sourceSize: Long,
    val sourceModifiedAt: Long,
    val durationMs: Long,
    val sampleRate: Int,
    val frameCount: Int,
    val featureBands: Int,
    val compressedFeatures: ByteArray,
    val generatedAt: Long = System.currentTimeMillis(),
    val version: Int = TWINSENSE_VERSION
)

enum class TwinSenseMatchKind { EXACT_AUDIO, ACOUSTIC, METADATA }

data class TwinSenseMatch(
    val first: MusicTrack,
    val second: MusicTrack,
    val kind: TwinSenseMatchKind,
    val confidence: Int,
    val reasons: List<String>,
    val versionWarning: Boolean
)

data class TwinSenseDuplicateGroup(
    val id: String,
    val tracks: List<MusicTrack>,
    val matches: List<TwinSenseMatch>,
    val containsExactAudio: Boolean
)

data class TwinSenseScanProgress(
    val completed: Int,
    val total: Int,
    val currentTitle: String,
    val fingerprintsReused: Int,
    val failed: Int
) {
    val percent: Int get() = if (total == 0) 100 else (completed * 100 / total).coerceIn(0, 100)
}

data class TwinSenseScanResult(
    val groups: List<TwinSenseDuplicateGroup>,
    val tracksScanned: Int,
    val fingerprintsReused: Int,
    val failed: Int,
    val completedAt: Long = System.currentTimeMillis()
) {
    val matchCount: Int get() = groups.sumOf { it.matches.size }
}

fun formatTwinSenseDuration(seconds: Double): String {
    if (!seconds.isFinite() || seconds <= 0.0) return "Duration unknown"
    val whole = seconds.toInt()
    return "${whole / 60}:${(whole % 60).toString().padStart(2, '0')}"
}

object TwinSenseText {
    private val versionTerms = Regex("\\b(remix|live|acoustic|instrumental|extended|radio edit|edit|demo|remaster(?:ed)?|version)\\b", RegexOption.IGNORE_CASE)
    fun normalize(value: String?): String = (value ?: "")
        .normalizeCompat()
        .lowercase(Locale.ROOT)
        .replace(Regex("\\.(mp3|m4a|aac|flac|wav|ogg|opus|wma|aiff?)$"), "")
        .replace(Regex("\\b(official audio|official video|lyrics?|audio|video|remaster(?:ed)?|explicit|clean|copy|duplicate|\\d{4} remaster)\\b"), " ")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    fun hasVersionLabel(value: String?): Boolean = versionTerms.containsMatchIn(value.orEmpty())
    private fun String.normalizeCompat(): String = try { java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFKC) } catch (_: Exception) { this }
}

/** Decodes local content URIs and fingerprints actual PCM audio, not filenames or sampled file bytes. */
class TwinSenseFingerprintEngine(private val context: Context) {
    suspend fun create(track: MusicTrack, onProgress: (Int) -> Unit = {}): TwinSenseFingerprint =
        withContext(Dispatchers.IO) {
            require(track.sizeBytes in 0..MAX_SOURCE_FILE_BYTES) {
                "TwinSense skipped this file because it is larger than 1 GB. The rest of your scan can continue safely."
            }
            require(track.durationMs in 0..MAX_TRACK_DURATION_MS) {
                "TwinSense skipped this file because its reported duration is unusually large."
            }
            val decoded = decodePcm(track, onProgress)
            val analysisRate = decoded.sampleRate.coerceAtMost(22_050)
            val mono = resampleMono(decoded.samples, decoded.sampleRate, analysisRate)
            val features = extractFeatures(mono, analysisRate)
            require(features.isNotEmpty()) { "Not enough decodable audio to create a Soundprint fingerprint." }
            val compressed = ByteArrayOutputStream().use { output ->
                DeflaterOutputStream(output).use { it.write(features) }
                output.toByteArray()
            }
            TwinSenseFingerprint(
                trackId = track.id,
                sourceSize = track.sizeBytes,
                sourceModifiedAt = track.modifiedAt,
                durationMs = track.durationMs.takeIf { it > 0L }
                    ?: if (decoded.sampleRate > 0) decoded.samples.size.toLong() * 1000L / decoded.sampleRate else 0L,
                sampleRate = analysisRate,
                frameCount = features.size / FEATURE_BANDS,
                featureBands = FEATURE_BANDS,
                compressedFeatures = compressed
            )
        }

    private data class DecodedAudio(val samples: FloatArray, val sampleRate: Int)

    private suspend fun decodePcm(track: MusicTrack, onProgress: (Int) -> Unit): DecodedAudio {
        require(track.sizeBytes <= MAX_SOURCE_FILE_BYTES) {
            "This file is larger than TwinSense's 1 GB safety limit and was skipped to protect memory."
        }
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        val samples = FloatSamples()
        var sourceRate = track.sampleRate.takeIf { it > 0 } ?: 44_100
        var channels = track.channelCount.takeIf { it > 0 } ?: 2
        var durationUs = track.durationMs.coerceIn(0L, MAX_TRACK_DURATION_MS) * 1000L
        try {
            extractor.setDataSource(context, Uri.parse(track.uri), null)
            var audioFormat: MediaFormat? = null
            var audioTrackIndex = -1
            for (index in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(index)
                if (candidate.getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")) {
                    audioFormat = candidate
                    audioTrackIndex = index
                    break
                }
            }
            val format = audioFormat ?: error("Android could not find a decodable audio stream.")
            val mime = format.getString(MediaFormat.KEY_MIME) ?: error("Audio format is missing its decoder type.")
            if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sourceRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            if (format.containsKey(MediaFormat.KEY_DURATION)) durationUs = format.getLong(MediaFormat.KEY_DURATION)
            require(sourceRate > 0 && channels > 0) { "Audio stream has invalid sample properties." }

            extractor.selectTrack(audioTrackIndex)
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()
            val info = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false
            var outputEncoding = AudioFormat.ENCODING_PCM_16BIT
            var outputChannels = channels
            var outputRate = sourceRate
            var lastProgress = -1

            while (!outputEnded) {
                coroutineContext.ensureActive()
                if (!inputEnded) {
                    val inputIndex = decoder.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val input = decoder.getInputBuffer(inputIndex) ?: error("Audio decoder input buffer is unavailable.")
                        input.clear()
                        val size = extractor.readSampleData(input, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded = true
                        } else {
                            val presentationTime = extractor.sampleTime.coerceAtLeast(0L)
                            decoder.queueInputBuffer(inputIndex, 0, size, presentationTime, 0)
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = decoder.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = decoder.outputFormat
                        if (outputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) outputRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        if (outputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) outputChannels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) outputEncoding = outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outputIndex >= 0) {
                        try {
                            if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                val output = decoder.getOutputBuffer(outputIndex)
                                if (output != null) appendPcm(output, info.offset, info.size, outputChannels, outputEncoding, samples)
                                if (durationUs > 0L && info.presentationTimeUs >= 0L) {
                                    val progress = ((info.presentationTimeUs.toDouble() / durationUs.toDouble()) * 100.0).toInt().coerceIn(0, 99)
                                    if (progress != lastProgress) { lastProgress = progress; onProgress(progress) }
                                }
                            }
                            if (samples.size >= MAX_PCM_SAMPLES || info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputEnded = true
                        } finally {
                            decoder.releaseOutputBuffer(outputIndex, false)
                        }
                    }
                }
            }
            onProgress(100)
            require(samples.size >= sourceRate / 2) { "The audio decoder returned too little audio to analyse." }
            return DecodedAudio(samples.toArray(), outputRate.takeIf { it > 0 } ?: sourceRate)
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun appendPcm(buffer: java.nio.ByteBuffer, offset: Int, size: Int, channels: Int, encoding: Int, target: FloatSamples) {
        val data = buffer.duplicate().order(java.nio.ByteOrder.LITTLE_ENDIAN)
        data.position(offset)
        data.limit(offset + size)
        val bytesPerSample = when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> 4
            AudioFormat.ENCODING_PCM_8BIT -> 1
            else -> 2
        }
        val frameBytes = bytesPerSample * channels.coerceAtLeast(1)
        while (data.remaining() >= frameBytes && target.size < MAX_PCM_SAMPLES) {
            var sum = 0.0
            repeat(channels.coerceAtLeast(1)) {
                val value = when (encoding) {
                    AudioFormat.ENCODING_PCM_FLOAT -> data.float.toDouble()
                    AudioFormat.ENCODING_PCM_8BIT -> ((data.get().toInt() and 0xff) - 128) / 128.0
                    else -> data.short.toDouble() / 32768.0
                }
                sum += value.coerceIn(-1.0, 1.0)
            }
            target.add((sum / channels.coerceAtLeast(1)).toFloat())
        }
    }

    private fun resampleMono(input: FloatArray, inputRate: Int, outputRate: Int): FloatArray {
        if (inputRate == outputRate) return input
        val outputSize = (input.size.toLong() * outputRate / inputRate)
            .coerceIn(0L, MAX_RESAMPLED_SAMPLES.toLong()).toInt()
        val output = FloatArray(outputSize)
        val ratio = inputRate.toDouble() / outputRate
        for (index in output.indices) {
            val position = index * ratio
            val left = position.toInt().coerceIn(0, input.lastIndex)
            val right = (left + 1).coerceAtMost(input.lastIndex)
            val fraction = (position - left).toFloat()
            output[index] = input[left] * (1f - fraction) + input[right] * fraction
        }
        return output
    }

    private fun extractFeatures(samples: FloatArray, sampleRate: Int): ByteArray {
        if (samples.size < FFT_SIZE) return ByteArray(0)
        val hop = sampleRate / 2
        val window = DoubleArray(FFT_SIZE) { index -> 0.5 - 0.5 * cos(2.0 * PI * index / (FFT_SIZE - 1)) }
        val real = DoubleArray(FFT_SIZE)
        val imaginary = DoubleArray(FFT_SIZE)
        val result = ByteArray(((samples.size - FFT_SIZE) / hop + 1) * FEATURE_BANDS)
        val bandEdges = IntArray(FEATURE_BANDS + 1) { band ->
            val frequency = 70.0 * (9_500.0 / 70.0).pow(band.toDouble() / FEATURE_BANDS)
            (frequency * FFT_SIZE / sampleRate).toInt().coerceIn(1, FFT_SIZE / 2 - 1)
        }
        var cursor = 0
        var start = 0
        while (start + FFT_SIZE <= samples.size) {
            for (index in 0 until FFT_SIZE) {
                real[index] = samples[start + index] * window[index]
                imaginary[index] = 0.0
            }
            fft(real, imaginary)
            val energies = DoubleArray(FEATURE_BANDS)
            for (band in 0 until FEATURE_BANDS) {
                val low = bandEdges[band]
                val high = max(low + 1, bandEdges[band + 1])
                var energy = 0.0
                for (bin in low until high) energy += real[bin] * real[bin] + imaginary[bin] * imaginary[bin]
                energies[band] = ln(energy / max(1, high - low) + 1e-12)
            }
            val mean = energies.average()
            val deviation = sqrt(energies.sumOf { (it - mean) * (it - mean) } / FEATURE_BANDS).coerceAtLeast(1e-6)
            for (band in 0 until FEATURE_BANDS) {
                val normalized = ((energies[band] - mean) / deviation * 32.0 + 128.0).toInt().coerceIn(0, 255)
                result[cursor++] = normalized.toByte()
            }
            start += hop
        }
        return if (cursor == result.size) result else result.copyOf(cursor)
    }

    private fun fft(real: DoubleArray, imaginary: DoubleArray) {
        val n = real.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imaginary[i]; imaginary[i] = imaginary[j]; imaginary[j] = ti
            }
        }
        var length = 2
        while (length <= n) {
            val angle = -2.0 * PI / length
            val wLengthReal = cos(angle)
            val wLengthImaginary = kotlin.math.sin(angle)
            var start = 0
            while (start < n) {
                var wReal = 1.0
                var wImaginary = 0.0
                for (offset in 0 until length / 2) {
                    val even = start + offset
                    val odd = even + length / 2
                    val oddReal = real[odd] * wReal - imaginary[odd] * wImaginary
                    val oddImaginary = real[odd] * wImaginary + imaginary[odd] * wReal
                    real[odd] = real[even] - oddReal
                    imaginary[odd] = imaginary[even] - oddImaginary
                    real[even] += oddReal
                    imaginary[even] += oddImaginary
                    val nextReal = wReal * wLengthReal - wImaginary * wLengthImaginary
                    wImaginary = wReal * wLengthImaginary + wImaginary * wLengthReal
                    wReal = nextReal
                }
                start += length
            }
            length = length shl 1
        }
    }

    private class FloatSamples {
        private var values = FloatArray(65_536)
        var size = 0
            private set
        fun add(value: Float) {
            if (size >= MAX_PCM_SAMPLES) return
            if (size == values.size) values = values.copyOf((values.size * 2).coerceAtMost(MAX_PCM_SAMPLES))
            values[size++] = value
        }
        fun toArray() = values.copyOf(size)
    }
}

/** Compares compressed spectral-frame sequences, tolerating small codec and encoder differences. */
object TwinSenseFingerprintComparator {
    fun compare(first: TwinSenseFingerprint, second: TwinSenseFingerprint): Double? {
        if (first.version != TWINSENSE_VERSION || second.version != TWINSENSE_VERSION) return null
        if (first.featureBands != second.featureBands || first.frameCount < 8 || second.frameCount < 8) return null
        val durationRatio = min(first.durationMs, second.durationMs).toDouble() / max(first.durationMs, second.durationMs).coerceAtLeast(1L)
        if (durationRatio < 0.94) return null
        val a = decompress(first)
        val b = decompress(second)
        val bands = first.featureBands
        if (a.size != first.frameCount * bands || b.size != second.frameCount * bands) return null

        val maxOffset = min(4, min(first.frameCount, second.frameCount) / 12)
        var best = 0.0
        for (offset in -maxOffset..maxOffset) {
            val startA = max(0, -offset)
            val startB = max(0, offset)
            val count = min(first.frameCount - startA, second.frameCount - startB)
            if (count < 8) continue
            var total = 0.0
            var compared = 0
            val stride = max(1, count / 180)
            var frame = 0
            while (frame < count) {
                total += cosineFrame(a, (startA + frame) * bands, b, (startB + frame) * bands, bands)
                compared++
                frame += stride
            }
            if (compared > 0) best = max(best, total / compared)
        }
        return best.takeIf { it > 0.0 }
    }

    private fun cosineFrame(a: ByteArray, offsetA: Int, b: ByteArray, offsetB: Int, bands: Int): Double {
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (index in 0 until bands) {
            val av = (a[offsetA + index].toInt() and 0xff) - 128
            val bv = (b[offsetB + index].toInt() and 0xff) - 128
            dot += av.toDouble() * bv
            normA += av.toDouble() * av
            normB += bv.toDouble() * bv
        }
        if (normA <= 1e-9 || normB <= 1e-9) return 0.0
        return (dot / sqrt(normA * normB)).coerceIn(-1.0, 1.0)
    }

    private fun decompress(fingerprint: TwinSenseFingerprint): ByteArray =
        InflaterInputStream(ByteArrayInputStream(fingerprint.compressedFeatures)).use { it.readBytes() }
}

/** Generates/reuses fingerprints, builds candidate pairs, and groups duplicate matches locally. */
class TwinSenseScanner(context: Context, private val library: MusicLibraryManager) {
    private val appContext = context.applicationContext
    private val engine = TwinSenseFingerprintEngine(appContext)
    private val preferences = appContext.getSharedPreferences("soundprint_soundprint", Context.MODE_PRIVATE)

    suspend fun loadSavedResults(): TwinSenseScanResult? = withContext(Dispatchers.IO) {
        val raw = preferences.getString("last_scan", null) ?: return@withContext null
        runCatching {
            val root = JSONObject(raw)
            val array = root.optJSONArray("matches") ?: JSONArray()
            val matches = (0 until array.length()).mapNotNull { index ->
                val item = array.getJSONObject(index)
                val first = library.song(item.getString("firstId")) ?: return@mapNotNull null
                val second = library.song(item.getString("secondId")) ?: return@mapNotNull null
                val reasonsArray = item.optJSONArray("reasons") ?: JSONArray()
                val reasons = (0 until reasonsArray.length()).map { reasonsArray.getString(it) }
                TwinSenseMatch(first, second, TwinSenseMatchKind.valueOf(item.getString("kind")),
                    item.optInt("confidence"), reasons, item.optBoolean("versionWarning"))
            }
            TwinSenseScanResult(buildGroups(matches), root.optInt("tracksScanned"),
                root.optInt("fingerprintsReused"), root.optInt("failed"), root.optLong("completedAt"))
        }.getOrNull()
    }

    fun saveResult(result: TwinSenseScanResult) {
        val matches = result.groups.flatMap { it.matches }
        val root = JSONObject().put("tracksScanned", result.tracksScanned)
            .put("fingerprintsReused", result.fingerprintsReused).put("failed", result.failed)
            .put("completedAt", result.completedAt)
            .put("matches", JSONArray().apply {
                matches.forEach { match ->
                    put(JSONObject().put("firstId", match.first.id).put("secondId", match.second.id)
                        .put("kind", match.kind.name).put("confidence", match.confidence)
                        .put("versionWarning", match.versionWarning)
                        .put("reasons", JSONArray(match.reasons)))
                }
            })
        preferences.edit().putString("last_scan", root.toString()).apply()
    }

    fun withoutTracks(result: TwinSenseScanResult, removedIds: Set<String>): TwinSenseScanResult {
        val remaining = result.groups.flatMap { it.matches }
            .filterNot { it.first.id in removedIds || it.second.id in removedIds }
        return result.copy(groups = buildGroups(remaining))
    }

    /**
     * Basic Scan is deliberately conservative: one decoder at a time, so it remains
     * compatible with lower-memory devices. Advanced Scan is Pro and runs two
     * independent decoders concurrently. Neither mode inserts artificial delays
     * or reduces the audio coverage/accuracy of the free scan.
     */
    private suspend fun sampledSignature(track: MusicTrack): String = withContext(Dispatchers.IO) {
        require(track.sizeBytes > 0L) { "The file size is unavailable." }
        val cacheKey = "sampled_signature_${track.id}"
        val cached = preferences.getString(cacheKey, null)
        if (cached != null &&
            preferences.getLong("${cacheKey}_size", -1L) == track.sizeBytes &&
            preferences.getLong("${cacheKey}_modified", -1L) == track.modifiedAt
        ) return@withContext cached

        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("${track.sizeBytes}\u0000${track.mimeType.lowercase(Locale.ROOT)}".toByteArray(Charsets.UTF_8))
        val chunkSize = 48 * 1024
        val offsets = listOf(
            0L,
            max(0L, track.sizeBytes / 2L - chunkSize / 2L),
            max(0L, track.sizeBytes - chunkSize)
        ).distinct()
        val descriptor = appContext.contentResolver.openFileDescriptor(Uri.parse(track.uri), "r")
            ?: error("Android could not open this audio file.")
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
            val channel = input.channel
            offsets.forEach { offset ->
                channel.position(offset)
                val byteCount = min(chunkSize.toLong(), (track.sizeBytes - offset).coerceAtLeast(0L)).toInt()
                if (byteCount > 0) {
                    val buffer = ByteBuffer.allocate(byteCount)
                    while (buffer.hasRemaining()) {
                        val read = channel.read(buffer)
                        if (read <= 0) break
                    }
                    digest.update(buffer.array(), 0, buffer.position())
                }
            }
        }
        val signature = digest.digest().joinToString("") { "%02x".format(it) }
        preferences.edit()
            .putString(cacheKey, signature)
            .putLong("${cacheKey}_size", track.sizeBytes)
            .putLong("${cacheKey}_modified", track.modifiedAt)
            .apply()
        signature
    }

    private fun normalizedTitle(track: MusicTrack): String {
        val title = track.title.takeIf { it.isNotBlank() && !it.equals("Unknown title", true) }
            ?: Uri.parse(track.uri).lastPathSegment?.let(Uri::decode).orEmpty()
        return TwinSenseText.normalize(title)
    }

    private fun compareMetadata(first: MusicTrack, second: MusicTrack): TwinSenseMatch? {
        val titleA = normalizedTitle(first)
        val titleB = normalizedTitle(second)
        if (titleA.isBlank() || titleA != titleB) return null
        val reasons = mutableListOf("Matching normalized title")
        var score = 40
        val artistA = TwinSenseText.normalize(first.artist)
        val artistB = TwinSenseText.normalize(second.artist)
        val albumA = TwinSenseText.normalize(first.album)
        val albumB = TwinSenseText.normalize(second.album)
        if (artistA.isNotBlank() && artistA != "unknown artist" && artistA == artistB) {
            score += 25
            reasons += "Matching artist"
        }
        if (albumA.isNotBlank() && albumA != "unknown album" && albumA == albumB) {
            score += 8
            reasons += "Matching album"
        }
        val validDurations = first.durationMs > 0L && second.durationMs > 0L
        val durationDifference = if (validDurations) kotlin.math.abs(first.durationMs - second.durationMs) else 0L
        val durationRatio = if (validDurations) durationDifference.toDouble() / max(first.durationMs, second.durationMs) else 0.0
        val versionWarning = validDurations && durationDifference > 3_000L && durationRatio > 0.02
        if (validDurations && !versionWarning) {
            score += 12
            reasons += "Similar duration"
        } else if (versionWarning) {
            reasons += "Different duration — could be an intro, edit or alternate version"
        }
        if (first.sizeBytes > 0L && first.sizeBytes == second.sizeBytes) {
            score += 4
            reasons += "Same file size"
        }
        if (first.sampleRate > 0 && second.sampleRate > 0 && first.sampleRate == second.sampleRate) {
            score += 2
            reasons += "Same sample rate (${first.sampleRate} Hz)"
        }
        if (first.channelCount > 0 && second.channelCount > 0 && first.channelCount == second.channelCount) {
            score += 2
            reasons += "Same channel count (${first.channelCount})"
        }
        val bitrateA = first.bitrate / 1000
        val bitrateB = second.bitrate / 1000
        if (bitrateA > 0 && bitrateA == bitrateB) {
            score += 2
            reasons += "Same bitrate (${bitrateA} kbps)"
        }
        if (first.mimeType.isNotBlank() && second.mimeType.isNotBlank() &&
            TwinSenseText.normalize(first.mimeType) == TwinSenseText.normalize(second.mimeType)
        ) {
            score += 2
            reasons += "Matching encoding (${first.mimeType})"
        }
        if (versionWarning) {
            return if (score >= 65) TwinSenseMatch(first, second, TwinSenseMatchKind.METADATA, min(score, 94), reasons, true) else null
        }
        if (score < 65) return null
        return TwinSenseMatch(first, second, TwinSenseMatchKind.METADATA, min(score, 98), reasons, false)
    }

    suspend fun scan(
        advanced: Boolean = false,
        onProgress: (TwinSenseScanProgress) -> Unit = {}
    ): TwinSenseScanResult {
        val tracks = withContext(Dispatchers.IO) { library.allSongs() }
        val sampled = ConcurrentHashMap<String, String>()
        val profiles = ConcurrentHashMap<String, TwinSenseFingerprint>()
        val failedIds = ConcurrentHashMap.newKeySet<String>()
        val reused = AtomicInteger(0)
        val completed = AtomicInteger(0)
        val nextIndex = AtomicInteger(0)

        suspend fun sampleTrack(track: MusicTrack) {
            coroutineContext.ensureActive()
            try {
                sampled[track.id] = sampledSignature(track)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                failedIds.add(track.id)
            } finally {
                val done = completed.incrementAndGet()
                onProgress(TwinSenseScanProgress(done, tracks.size, track.title, reused.get(), failedIds.size))
                if (!advanced) delay(150L)
            }
        }

        val sampleWorkers = if (advanced) min(6, tracks.size) else min(1, tracks.size)
        coroutineScope {
            repeat(sampleWorkers) {
                launch {
                    while (true) {
                        val index = nextIndex.getAndIncrement()
                        if (index >= tracks.size) break
                        sampleTrack(tracks[index])
                    }
                }
            }
        }

        if (advanced && tracks.isNotEmpty()) {
            completed.set(0)
            nextIndex.set(0)
            val decoderSlots = Semaphore(4)
            suspend fun analyzeTrack(track: MusicTrack) {
                coroutineContext.ensureActive()
                try {
                    decoderSlots.withPermit {
                        val cached = withContext(Dispatchers.IO) {
                            library.twinSenseFingerprint(track.id, track.sizeBytes, track.modifiedAt)
                        }
                        val profile = cached ?: engine.create(track)
                        if (cached != null) reused.incrementAndGet()
                        else withContext(Dispatchers.IO) { library.saveTwinSenseFingerprint(profile) }
                        profiles[track.id] = profile
                    }
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                    failedIds.add(track.id)
                } finally {
                    val done = completed.incrementAndGet()
                    val percent = 35 + (done * 65 / tracks.size).coerceIn(0, 65)
                    onProgress(TwinSenseScanProgress(done, tracks.size, track.title, reused.get(), failedIds.size).copy(completed = percent))
                }
            }
            coroutineScope {
                repeat(min(4, tracks.size)) {
                    launch {
                        while (true) {
                            val index = nextIndex.getAndIncrement()
                            if (index >= tracks.size) break
                            analyzeTrack(tracks[index])
                        }
                    }
                }
            }
        }

        val candidates = linkedMapOf<String, Pair<MusicTrack, MusicTrack>>()
        fun add(first: MusicTrack, second: MusicTrack) {
            if (first.id == second.id) return
            val pair = if (first.id < second.id) first to second else second to first
            candidates["${pair.first.id}:${pair.second.id}"] = pair
        }
        val fingerprintGroups = tracks.filter { it.sizeBytes > 0L && sampled.containsKey(it.id) }
            .groupBy { "${it.sizeBytes}:${sampled[it.id]}" }
        fingerprintGroups.values.forEach { group ->
            for (i in group.indices) for (j in i + 1 until group.size) add(group[i], group[j])
        }
        val titleGroups = tracks.map { it to normalizedTitle(it) }
            .filter { it.second.isNotBlank() }
            .groupBy { it.second }
        titleGroups.values.forEach { group ->
            for (i in group.indices) for (j in i + 1 until group.size) add(group[i].first, group[j].first)
        }

        val matches = mutableListOf<TwinSenseMatch>()
        for ((first, second) in candidates.values) {
            coroutineContext.ensureActive()
            val firstSignature = sampled[first.id]
            val secondSignature = sampled[second.id]
            if (first.sizeBytes > 0L && first.sizeBytes == second.sizeBytes &&
                firstSignature != null && firstSignature == secondSignature
            ) {
                matches += TwinSenseMatch(
                    first, second, TwinSenseMatchKind.EXACT_AUDIO, 99,
                    listOf("Matching file size", "Matching SHA-256 signature of sampled beginning, middle and ending bytes"),
                    false
                )
                continue
            }

            if (advanced && normalizedTitle(first).isNotBlank() &&
                normalizedTitle(first) == normalizedTitle(second)
            ) {
                val firstProfile = profiles[first.id]
                val secondProfile = profiles[second.id]
                if (firstProfile != null && secondProfile != null) {
                    val similarity = TwinSenseFingerprintComparator.compare(firstProfile, secondProfile)
                    if (similarity != null && similarity >= 0.93) {
                        val durationDifference = kotlin.math.abs(first.durationMs - second.durationMs)
                        val durationRatio = if (first.durationMs > 0L && second.durationMs > 0L)
                            durationDifference.toDouble() / max(first.durationMs, second.durationMs) else 0.0
                        val versionWarning = durationDifference > 3_000L && durationRatio > 0.02
                        val confidence = min(94, (72 + (similarity - 0.86) * 150).toInt())
                        matches += TwinSenseMatch(
                            first, second, TwinSenseMatchKind.ACOUSTIC, confidence,
                            listOf("Similar local audio-analysis profile", "Compared cached spectral fingerprint frames") +
                                if (versionWarning) listOf("Different duration — preserve and review as a possible alternate version") else emptyList(),
                            versionWarning
                        )
                        continue
                    }
                }
            }
            compareMetadata(first, second)?.let(matches::add)
        }

        matches.sortWith(compareByDescending<TwinSenseMatch> { it.kind == TwinSenseMatchKind.EXACT_AUDIO }
            .thenByDescending { it.confidence })
        val result = TwinSenseScanResult(
            buildGroups(matches),
            tracks.size - failedIds.size,
            reused.get(),
            failedIds.size
        )
        saveResult(result)
        return result
    }

    private fun buildGroups(matches: List<TwinSenseMatch>): List<TwinSenseDuplicateGroup> {
        val adjacency = linkedMapOf<String, MutableSet<String>>()
        val trackById = linkedMapOf<String, MusicTrack>()
        matches.forEach { match ->
            trackById[match.first.id] = match.first
            trackById[match.second.id] = match.second
            adjacency.getOrPut(match.first.id) { linkedSetOf() }.add(match.second.id)
            adjacency.getOrPut(match.second.id) { linkedSetOf() }.add(match.first.id)
        }
        val visited = mutableSetOf<String>()
        val groups = mutableListOf<TwinSenseDuplicateGroup>()
        adjacency.keys.forEach { start ->
            if (!visited.add(start)) return@forEach
            val stack = ArrayDeque<String>()
            val members = mutableListOf<String>()
            stack.add(start)
            while (stack.isNotEmpty()) {
                val id = stack.removeLast()
                members += id
                adjacency[id].orEmpty().forEach { neighbor -> if (visited.add(neighbor)) stack.add(neighbor) }
            }
            val memberSet = members.toSet()
            val groupMatches = matches.filter { it.first.id in memberSet && it.second.id in memberSet }
            groups += TwinSenseDuplicateGroup(
                id = members.sorted().joinToString(":"),
                tracks = members.mapNotNull { trackById[it] }.sortedBy { it.title.lowercase(Locale.ROOT) },
                matches = groupMatches,
                containsExactAudio = groupMatches.any { it.kind == TwinSenseMatchKind.EXACT_AUDIO }
            )
        }
        return groups.sortedWith(compareByDescending<TwinSenseDuplicateGroup> { it.containsExactAudio }
            .thenByDescending { it.matches.maxOfOrNull(TwinSenseMatch::confidence) ?: 0 })
    }
}


object TwinSenseCleanupPlanner {
    /** Only verified identical-audio groups are auto-cleaned; fuzzy/alternate versions stay manual. */
    fun tracksToRemove(result: TwinSenseScanResult): List<MusicTrack> {
        val removals = mutableListOf<MusicTrack>()
        result.groups.filter { group ->
            group.matches.isNotEmpty() && group.matches.all {
                it.kind == TwinSenseMatchKind.EXACT_AUDIO && !it.versionWarning
            }
        }.forEach { group ->
            val ranked = group.tracks.sortedWith(
                compareByDescending<MusicTrack> { quality(it) }
                    .thenBy { it.addedAt }
                    .thenBy { it.id }
            )
            removals += ranked.drop(1)
        }
        return removals.distinctBy { it.id }
    }

    /** Detect copy suffixes; require matching artist/album and duration within 2.5s. */
    fun nameBasedDuplicateGroups(tracks: List<MusicTrack>): List<List<MusicTrack>> {
        val candidates = tracks.map { track ->
            val name = sourceFileName(track) ?: track.title
            val normalized = normalizeCopiedName(name)
            Triple(track, normalized.first, normalized.second)
        }
        val groups = mutableListOf<List<MusicTrack>>()
        candidates.groupBy { (track, base, _) ->
            listOf(base, track.artist.trim().lowercase(), track.album.trim().lowercase())
        }.values.forEach { sameIdentity ->
            if (sameIdentity.none { it.third }) return@forEach
            val remaining = sameIdentity.map { it.first }.toMutableList()
            while (remaining.isNotEmpty()) {
                val seed = remaining.removeAt(0)
                val close = remaining.filter { other ->
                    kotlin.math.abs(other.durationMs - seed.durationMs) <= 2_500L &&
                        (seed.artist.isNotBlank() || seed.album.isNotBlank())
                }
                if (close.isNotEmpty()) {
                    groups += (listOf(seed) + close).distinctBy { it.id }
                    remaining.removeAll(close.toSet())
                }
            }
        }
        return groups.filter { it.size > 1 }.sortedByDescending { it.size }
    }

    fun tracksToRemoveByName(group: List<MusicTrack>): List<MusicTrack> {
        if (group.size < 2) return emptyList()
        val ranked = group.sortedWith(
            compareBy<MusicTrack> { isCopiedName(sourceFileName(it) ?: it.title) }
                .thenByDescending { quality(it) }
                .thenBy { it.addedAt }
                .thenBy { it.id }
        )
        return ranked.drop(1)
    }

    private fun sourceFileName(track: MusicTrack): String? =
        runCatching { android.net.Uri.parse(track.uri).lastPathSegment?.let { android.net.Uri.decode(it) } }
            .getOrNull()
            ?.takeIf { it.contains('.') && it.substringAfterLast('.').length in 2..5 }

    private fun isCopiedName(value: String): Boolean = normalizeCopiedName(value).second

    private fun normalizeCopiedName(value: String): Pair<String, Boolean> {
        val withoutExtension = value.substringBeforeLast('.', value).trim()
        val prefixCopy = Regex("(?i)^copy\\s+of\\s+").containsMatchIn(withoutExtension)
        val cleaned = withoutExtension
            .replace(Regex("(?i)^copy\\s+of\\s+"), "")
            .replace(Regex("(?i)\\s*[-_ ]*(copy|duplicate|duplicated|dup)(?:\\s*\\(\\d+\\)|\\s*\\d+)?$"), "")
            .replace(Regex("(?i)\\s*[-_ ]*\\(\\d+\\)$"), "")
            .replace(Regex("(?i)\\s*[-_ ]*\\[\\d+\\]$"), "")
            .replace(Regex("(?i)\\s*[-_ ]+\\d+$"), "")
            .trim()
        val marked = prefixCopy || cleaned.length < withoutExtension.length
        return cleaned.lowercase().replace(Regex("\\s+"), " ") to marked
    }

    private fun quality(track: MusicTrack): Int =
        (if (track.title.isNotBlank() && track.title != "Unknown title") 8 else 0) +
        (if (track.artist.isNotBlank() && track.artist != "Unknown artist") 6 else 0) +
        (if (track.album.isNotBlank() && track.album != "Unknown album") 4 else 0) +
        (if (track.durationMs > 0) 3 else 0) +
        (track.bitrate / 32_000).coerceIn(0, 20) +
        (if (track.sampleRate > 0) 2 else 0) +
        (if (track.channelCount > 0) 1 else 0) +
        (if (!track.artworkUri.isNullOrBlank()) 2 else 0) +
        (if (track.isAvailable) 3 else -100) +
        (track.sizeBytes / (1024L * 1024L)).coerceAtMost(10L).toInt()
}
