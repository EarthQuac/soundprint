package digital.anchorstudio.soundprint

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Android/Kotlin port of Cadence's version-6 local audio-analysis profile.
 * The same profile fields and weighted comparison signals are used on both platforms.
 */
internal data class TwinSenseAudioAnalysisProfile(
    val trackId: String,
    val version: Int,
    val sourceSize: Long,
    val sourceMime: String,
    val durationSeconds: Double,
    val sampleRateHz: Int,
    val spectralCentroidHz: Double,
    val estimatedTempoBpm: Int,
    val tempoConfidence: Double,
    val spectralFlatness: Double,
    val lowBandRatio: Double,
    val midBandRatio: Double,
    val highBandRatio: Double,
    val chroma: List<Double>
) {
    fun matchesSource(track: MusicTrack): Boolean =
        trackId == track.id && version == ANALYSIS_VERSION &&
            sourceSize == track.sizeBytes && sourceMime == track.mimeType &&
            durationSeconds.isFinite() && chroma.size == 12

    fun toJson(): String = JSONObject()
        .put("trackId", trackId)
        .put("version", version)
        .put("sourceSize", sourceSize)
        .put("sourceMime", sourceMime)
        .put("durationSeconds", durationSeconds)
        .put("sampleRateHz", sampleRateHz)
        .put("spectralCentroidHz", spectralCentroidHz)
        .put("estimatedTempoBpm", estimatedTempoBpm)
        .put("tempoConfidence", tempoConfidence)
        .put("spectralFlatness", spectralFlatness)
        .put("lowBandRatio", lowBandRatio)
        .put("midBandRatio", midBandRatio)
        .put("highBandRatio", highBandRatio)
        .put("chroma", JSONArray().apply { chroma.forEach { put(it) } })
        .toString()

    companion object {
        const val ANALYSIS_VERSION = 6

        fun fromJson(raw: String): TwinSenseAudioAnalysisProfile? = runCatching {
            val json = JSONObject(raw)
            val chromaJson = json.optJSONArray("chroma") ?: return null
            val chroma = (0 until chromaJson.length()).map { chromaJson.getDouble(it) }
            val profile = TwinSenseAudioAnalysisProfile(
                trackId = json.getString("trackId"),
                version = json.getInt("version"),
                sourceSize = json.getLong("sourceSize"),
                sourceMime = json.getString("sourceMime"),
                durationSeconds = json.getDouble("durationSeconds"),
                sampleRateHz = json.getInt("sampleRateHz"),
                spectralCentroidHz = json.getDouble("spectralCentroidHz"),
                estimatedTempoBpm = json.getInt("estimatedTempoBpm"),
                tempoConfidence = json.getDouble("tempoConfidence"),
                spectralFlatness = json.getDouble("spectralFlatness"),
                lowBandRatio = json.getDouble("lowBandRatio"),
                midBandRatio = json.getDouble("midBandRatio"),
                highBandRatio = json.getDouble("highBandRatio"),
                chroma = chroma
            )
            if (chroma.size != 12 || !profile.numericValues().all { it.isFinite() }) null else profile
        }.getOrNull()
    }

    private fun numericValues() = listOf(
        durationSeconds, spectralCentroidHz, tempoConfidence, spectralFlatness,
        lowBandRatio, midBandRatio, highBandRatio
    ) + chroma
}

internal object TwinSenseAudioAnalysis {
    private const val FFT_SIZE = 2048
    private const val MAX_WINDOWS = 20
    private const val MIN_WINDOW_SPACING_SECONDS = 6.0
    private const val ENVELOPE_FRAME_SIZE = 1024
    private const val ENVELOPE_HOP_SIZE = 512

    fun analyze(track: MusicTrack, samples: FloatArray, sampleRate: Int): TwinSenseAudioAnalysisProfile {
        require(samples.isNotEmpty() && sampleRate > 0) { "This audio file contains no decodable samples." }
        val duration = if (track.durationMs > 0L) track.durationMs / 1000.0 else samples.size.toDouble() / sampleRate
        val windowCount = ceil(duration / MIN_WINDOW_SPACING_SECONDS).toInt().coerceIn(1, MAX_WINDOWS)
        val maxStart = max(0, samples.size - FFT_SIZE)
        val hop = if (windowCount > 1) maxStart.toDouble() / (windowCount - 1) else 0.0
        val real = DoubleArray(FFT_SIZE)
        val imaginary = DoubleArray(FFT_SIZE)

        var centroidSum = 0.0
        var centroidWindowCount = 0
        var flatnessSum = 0.0
        var lowBandSum = 0.0
        var midBandSum = 0.0
        var highBandSum = 0.0
        var spectralWindowCount = 0
        val chromaSums = DoubleArray(12)

        for (windowIndex in 0 until windowCount) {
            val start = min(maxStart, floor(hop * windowIndex).toInt())
            real.fill(0.0)
            imaginary.fill(0.0)
            for (i in 0 until FFT_SIZE) {
                val sample = samples.getOrElse(start + i) { 0f }.toDouble()
                val hann = 0.5 * (1.0 - cos((2.0 * PI * i) / (FFT_SIZE - 1)))
                real[i] = sample * hann
            }
            fft(real, imaginary)

            var magnitudeSum = 0.0
            var weightedFrequencySum = 0.0
            var logMagnitudeSum = 0.0
            var positiveBins = 0
            var lowPower = 0.0
            var midPower = 0.0
            var highPower = 0.0
            for (bin in 1..(FFT_SIZE / 2)) {
                val magnitude = kotlin.math.hypot(real[bin], imaginary[bin])
                val frequency = bin.toDouble() * sampleRate / FFT_SIZE
                magnitudeSum += magnitude
                weightedFrequencySum += magnitude * frequency
                logMagnitudeSum += ln(max(magnitude, 1e-12))
                positiveBins++
                val power = magnitude * magnitude
                val midi = 69.0 + 12.0 * log2(max(1.0, frequency) / 440.0)
                val pitchClass = ((midi.roundToInt() % 12) + 12) % 12
                chromaSums[pitchClass] += magnitude
                when {
                    frequency < 250.0 -> lowPower += power
                    frequency < 2_000.0 -> midPower += power
                    else -> highPower += power
                }
            }
            if (magnitudeSum > 0.0) {
                centroidSum += weightedFrequencySum / magnitudeSum
                centroidWindowCount++
                val geometricMean = exp(logMagnitudeSum / max(1, positiveBins))
                flatnessSum += min(1.0, geometricMean / (magnitudeSum / max(1, positiveBins)))
                val totalBandPower = lowPower + midPower + highPower
                if (totalBandPower > 0.0) {
                    lowBandSum += lowPower / totalBandPower
                    midBandSum += midPower / totalBandPower
                    highBandSum += highPower / totalBandPower
                }
                spectralWindowCount++
            }
        }

        val chromaTotal = chromaSums.sum()
        val chroma = if (chromaTotal > 0.0) chromaSums.map { it / chromaTotal } else List(12) { 0.0 }
        val tempo = estimateTempo(samples, sampleRate)
        return TwinSenseAudioAnalysisProfile(
            trackId = track.id,
            version = TwinSenseAudioAnalysisProfile.ANALYSIS_VERSION,
            sourceSize = track.sizeBytes,
            sourceMime = track.mimeType,
            durationSeconds = duration,
            sampleRateHz = sampleRate,
            spectralCentroidHz = if (centroidWindowCount > 0) centroidSum / centroidWindowCount else 0.0,
            estimatedTempoBpm = tempo.bpm,
            tempoConfidence = tempo.confidence,
            spectralFlatness = if (spectralWindowCount > 0) flatnessSum / spectralWindowCount else 0.0,
            lowBandRatio = if (spectralWindowCount > 0) lowBandSum / spectralWindowCount else 0.0,
            midBandRatio = if (spectralWindowCount > 0) midBandSum / spectralWindowCount else 0.0,
            highBandRatio = if (spectralWindowCount > 0) highBandSum / spectralWindowCount else 0.0,
            chroma = chroma
        )
    }

    fun compare(
        first: MusicTrack,
        second: MusicTrack,
        firstProfile: TwinSenseAudioAnalysisProfile,
        secondProfile: TwinSenseAudioAnalysisProfile
    ): TwinSenseMatch? {
        val durationDifference = if (first.durationMs > 0L && second.durationMs > 0L)
            abs(first.durationMs - second.durationMs) / 1000.0 else 0.0
        val durationRatio = if (first.durationMs > 0L && second.durationMs > 0L)
            durationDifference / max(first.durationMs, second.durationMs).toDouble() * 1000.0 else 0.0
        val versionWarning = durationDifference > 3.0 && durationRatio > 0.02
        data class Signal(val name: String, val similarity: Double, val weight: Double)
        val signals = mutableListOf<Signal>()

        val chromaA = firstProfile.chroma
        val chromaB = secondProfile.chroma
        if (chromaA.size == 12 && chromaB.size == 12) {
            val dot = chromaA.indices.sumOf { chromaA[it] * chromaB[it] }
            val normA = sqrt(chromaA.sumOf { it * it })
            val normB = sqrt(chromaB.sumOf { it * it })
            if (normA > 1e-6 && normB > 1e-6) {
                signals += Signal("Similar harmonic profile", (dot / (normA * normB)).coerceIn(0.0, 1.0), 0.4)
            }
        }

        val bandsA = listOf(firstProfile.lowBandRatio, firstProfile.midBandRatio, firstProfile.highBandRatio)
        val bandsB = listOf(secondProfile.lowBandRatio, secondProfile.midBandRatio, secondProfile.highBandRatio)
        if ((bandsA + bandsB).all { it.isFinite() }) {
            val distance = bandsA.indices.sumOf { abs(bandsA[it] - bandsB[it]) }
            signals += Signal("Similar low/mid/high energy balance", max(0.0, 1.0 - distance / 2.0), 0.25)
        }

        if (firstProfile.spectralCentroidHz > 0.0 && secondProfile.spectralCentroidHz > 0.0) {
            val ratio = min(firstProfile.spectralCentroidHz, secondProfile.spectralCentroidHz) /
                max(firstProfile.spectralCentroidHz, secondProfile.spectralCentroidHz)
            signals += Signal("Similar spectral brightness", ratio, 0.15)
        }

        if (firstProfile.spectralFlatness >= 0.0 && secondProfile.spectralFlatness >= 0.0) {
            signals += Signal(
                "Similar tonal/noise texture",
                max(0.0, 1.0 - abs(firstProfile.spectralFlatness - secondProfile.spectralFlatness)),
                0.1
            )
        }

        if (firstProfile.estimatedTempoBpm > 0 && secondProfile.estimatedTempoBpm > 0 &&
            firstProfile.tempoConfidence >= 0.2 && secondProfile.tempoConfidence >= 0.2
        ) {
            val tempoRatio = min(firstProfile.estimatedTempoBpm, secondProfile.estimatedTempoBpm).toDouble() /
                max(firstProfile.estimatedTempoBpm, secondProfile.estimatedTempoBpm)
            val tempoSimilarity = max(tempoRatio, max(0.0, 1.0 - abs(tempoRatio * 2.0 - 1.0)))
            signals += Signal("Similar estimated rhythm", tempoSimilarity, 0.1)
        }

        val totalWeight = signals.sumOf { it.weight }
        if (totalWeight <= 0.0) return null
        val similarity = signals.sumOf { it.similarity * it.weight } / totalWeight
        if (similarity < if (versionWarning) 0.86 else 0.93) return null

        val reasons = signals.filter { it.similarity >= 0.82 }.map { it.name }.toMutableList()
        reasons += "Compared locally using decoded audio-analysis measurements"
        if (first.artist.isNotBlank() && second.artist.isNotBlank()) {
            if (first.artist.equals(second.artist, ignoreCase = true)) reasons += "Matching artist metadata"
            else reasons += "Artist metadata differs (${first.artist} vs ${second.artist})"
        }
        if (first.album.isNotBlank() && second.album.isNotBlank()) {
            if (first.album.equals(second.album, true)) reasons += "Matching album metadata"
            else reasons += "Album metadata differs (${first.album} vs ${second.album})"
        }
        if (first.albumArtist.isNotBlank() && second.albumArtist.isNotBlank()) {
            if (first.albumArtist.equals(second.albumArtist, true)) reasons += "Matching album artist metadata"
            else reasons += "Album artist metadata differs (${first.albumArtist} vs ${second.albumArtist})"
        }
        if (first.genre.isNotBlank() && second.genre.isNotBlank()) {
            if (first.genre.equals(second.genre, true)) reasons += "Matching genre metadata (${first.genre})"
            else reasons += "Genre metadata differs (${first.genre} vs ${second.genre})"
        }
        if (first.year > 0 && second.year > 0) {
            if (first.year == second.year) reasons += "Matching release year (${first.year})"
            else reasons += "Different release years (${first.year} vs ${second.year})"
        }
        if (first.trackNumber > 0 && second.trackNumber > 0 && first.trackNumber != second.trackNumber) {
            reasons += "Different track numbers (${first.trackNumber} vs ${second.trackNumber})"
        }
        if (first.discNumber > 0 && second.discNumber > 0 && first.discNumber != second.discNumber) {
            reasons += "Different disc numbers (${first.discNumber} vs ${second.discNumber})"
        }
        if (first.durationMs > 0L && second.durationMs > 0L) {
            if (versionWarning) {
                val shorter = if (first.durationMs < second.durationMs) "First track" else "Second track"
                reasons += "Possible alternate version: $shorter is shorter by ${formatTwinSenseDuration(durationDifference)} (${formatTwinSenseDuration(first.durationMs / 1000.0)} vs ${formatTwinSenseDuration(second.durationMs / 1000.0)})"
            } else reasons += "Similar duration (${formatTwinSenseDuration(first.durationMs / 1000.0)})"
        }
        if (first.sizeBytes > 0L && second.sizeBytes > 0L && first.sizeBytes != second.sizeBytes) {
            reasons += "Different file sizes (${first.sizeBytes} vs ${second.sizeBytes} bytes)"
        }
        if (first.bitrate > 0 && second.bitrate > 0) {
            reasons += if (first.bitrate == second.bitrate) "Same bitrate (${first.bitrate / 1000} kbps)"
            else "Different bitrates (${first.bitrate / 1000} vs ${second.bitrate / 1000} kbps)"
        }
        if (first.sampleRate > 0 && second.sampleRate > 0) {
            reasons += if (first.sampleRate == second.sampleRate) "Same source sample rate (${first.sampleRate} Hz)"
            else "Different source sample rates (${first.sampleRate} vs ${second.sampleRate} Hz)"
        }
        if (first.channelCount > 0 && second.channelCount > 0) {
            reasons += if (first.channelCount == second.channelCount) "Same channel count (${first.channelCount})"
            else "Different channel counts (${first.channelCount} vs ${second.channelCount})"
        }
        val codecA = first.encoding.ifBlank { first.mimeType }
        val codecB = second.encoding.ifBlank { second.mimeType }
        if (codecA.isNotBlank() && codecB.isNotBlank()) {
            reasons += if (codecA.equals(codecB, true)) "Same audio encoding ($codecA)"
            else "Different audio encodings ($codecA vs $codecB)"
        }
        val confidence = min(94, (72.0 + (similarity - 0.86) * 150.0).roundToInt())
        val kind = if (versionWarning) TwinSenseMatchKind.ALTERNATIVE else TwinSenseMatchKind.ACOUSTIC
        return TwinSenseMatch(first, second, kind, confidence, reasons.distinct(), versionWarning)
    }

    private data class Tempo(val bpm: Int, val confidence: Double)

    private fun estimateTempo(samples: FloatArray, sampleRate: Int): Tempo {
        val envelope = ArrayList<Double>((samples.size / ENVELOPE_HOP_SIZE) + 1)
        var start = 0
        while (start < samples.size) {
            val end = min(samples.size, start + ENVELOPE_FRAME_SIZE)
            var sum = 0.0
            for (index in start until end) {
                val sample = samples[index].toDouble()
                sum += sample * sample
            }
            envelope += sqrt(sum / max(1, end - start))
            start += ENVELOPE_HOP_SIZE
        }
        if (envelope.size < 20) return Tempo(0, 0.0)

        val onset = DoubleArray(envelope.size)
        var mean = 0.0
        for (index in 1 until envelope.size) {
            onset[index] = max(0.0, envelope[index] - envelope[index - 1])
            mean += onset[index]
        }
        mean /= max(1, onset.size - 1)
        if (mean < 1e-5) return Tempo(0, 0.0)
        for (index in onset.indices) onset[index] = max(0.0, onset[index] - mean * 0.25)

        val envelopeRate = sampleRate.toDouble() / ENVELOPE_HOP_SIZE
        val minLag = max(1, floor((60.0 * envelopeRate) / 200.0).toInt())
        val maxLag = min(onset.size - 2, ceil((60.0 * envelopeRate) / 55.0).toInt())
        var bestLag = 0
        var bestScore = 0.0
        var scoreSum = 0.0
        var scoreCount = 0
        for (lag in minLag..maxLag) {
            var dot = 0.0
            var leftPower = 0.0
            var rightPower = 0.0
            for (index in lag until onset.size) {
                val left = onset[index]
                val right = onset[index - lag]
                dot += left * right
                leftPower += left * left
                rightPower += right * right
            }
            val score = dot / sqrt(max(1e-12, leftPower * rightPower))
            scoreSum += score
            scoreCount++
            if (score > bestScore) {
                bestScore = score
                bestLag = lag
            }
        }
        val bpm = if (bestLag > 0) (60.0 * envelopeRate / bestLag).roundToInt() else 0
        val meanScore = scoreSum / max(1, scoreCount)
        val confidence = ((bestScore - meanScore) / max(0.05, 1.0 - meanScore)).coerceIn(0.0, 1.0)
        return Tempo(if (bpm in 55..200) bpm else 0, confidence)
    }

    private fun fft(real: DoubleArray, imaginary: DoubleArray) {
        val length = real.size
        var j = 0
        for (i in 1 until length) {
            var bit = length shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val realValue = real[i]
                real[i] = real[j]
                real[j] = realValue
                val imaginaryValue = imaginary[i]
                imaginary[i] = imaginary[j]
                imaginary[j] = imaginaryValue
            }
        }
        var size = 2
        while (size <= length) {
            val half = size shr 1
            val angle = -2.0 * PI / size
            val stepReal = cos(angle)
            val stepImaginary = kotlin.math.sin(angle)
            var start = 0
            while (start < length) {
                var twiddleReal = 1.0
                var twiddleImaginary = 0.0
                for (offset in 0 until half) {
                    val even = start + offset
                    val odd = even + half
                    val oddReal = real[odd] * twiddleReal - imaginary[odd] * twiddleImaginary
                    val oddImaginary = real[odd] * twiddleImaginary + imaginary[odd] * twiddleReal
                    val evenReal = real[even]
                    val evenImaginary = imaginary[even]
                    real[even] = evenReal + oddReal
                    imaginary[even] = evenImaginary + oddImaginary
                    real[odd] = evenReal - oddReal
                    imaginary[odd] = evenImaginary - oddImaginary
                    val nextTwiddleReal = twiddleReal * stepReal - twiddleImaginary * stepImaginary
                    twiddleImaginary = twiddleReal * stepImaginary + twiddleImaginary * stepReal
                    twiddleReal = nextTwiddleReal
                }
                start += size
            }
            size = size shl 1
        }
    }
}
