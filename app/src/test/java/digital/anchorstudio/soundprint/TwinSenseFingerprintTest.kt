package digital.anchorstudio.soundprint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.DeflaterOutputStream
import kotlin.math.PI
import kotlin.math.sin

class TwinSenseFingerprintTest {
    private fun fingerprint(id: String, features: ByteArray, durationMs: Long = 30_000L): TwinSenseFingerprint {
        val compressed = ByteArrayOutputStream().use { output ->
            DeflaterOutputStream(output).use { it.write(features) }
            output.toByteArray()
        }
        return TwinSenseFingerprint(id, 1000L, 1L, durationMs, 22_050, features.size / 24, 24, compressed)
    }

    private fun musicFeatures(seed: Int, frames: Int = 40): ByteArray =
        ByteArray(frames * 24) { index ->
            val frame = index / 24
            val band = index % 24
            val value = 128.0 + 52.0 * sin((frame * (band + 2) + band * seed) * PI / 17.0)
            value.toInt().coerceIn(0, 255).toByte()
        }

    @Test fun normalizesMetadataWithoutDiscardingArtistOrTrackWords() {
        assertEquals("artist song", TwinSenseText.normalize("Artist - Song (Official Audio).mp3"))
        assertTrue(TwinSenseText.hasVersionLabel("Song (Live Version)"))
        assertFalse(TwinSenseText.hasVersionLabel("Song (Copy)"))
    }

    @Test fun detectsSameAudioFingerprintWithSmallFrameOffset() {
        val original = musicFeatures(3)
        val shifted = ByteArray(original.size)
        System.arraycopy(original, 24, shifted, 0, original.size - 24)
        System.arraycopy(original, original.size - 24, shifted, original.size - 24, 24)

        val score = TwinSenseFingerprintComparator.compare(
            fingerprint("first", original), fingerprint("second", shifted)
        )

        assertTrue("The shifted copy should retain a high spectral similarity.", (score ?: 0.0) >= 0.94)
    }

    @Test fun rejectsDifferentAudioFingerprintSequences() {
        val first = musicFeatures(2)
        val second = musicFeatures(11)

        val score = TwinSenseFingerprintComparator.compare(
            fingerprint("first", first), fingerprint("second", second)
        )

        assertTrue("Different sequences should not cross the duplicate threshold.", (score ?: 0.0) < 0.86)
    }

    @Test fun smartCleanupOnlySelectsVerifiedExactAudioCopies() {
        val preferred = track("preferred", title = "Song", artist = "Artist", bitrate = 320_000)
        val copy = track("copy", title = "Song copy", artist = "Artist", bitrate = 128_000)
        val exact = TwinSenseMatch(preferred, copy, TwinSenseMatchKind.EXACT_AUDIO, 100, listOf("Matching SHA-256 signature of sampled beginning, middle and ending bytes"), false)
        val result = TwinSenseScanResult(
            listOf(TwinSenseDuplicateGroup("group", listOf(preferred, copy), listOf(exact), true)),
            2, 0, 0
        )

        val removals = TwinSenseCleanupPlanner.tracksToRemove(result)

        assertEquals(listOf("copy"), removals.map { it.id })
    }

    @Test fun smartCleanupUsesCopyMarkersOnlyWithCorroboratingMetadata() {
        val original = track("original", title = "Track")
        val numberedCopy = track("numbered-copy", title = "Track").copy(fileName = "Track (1).mp3")
        val corroborated = TwinSenseMatch(
            original,
            numberedCopy,
            TwinSenseMatchKind.METADATA,
            82,
            listOf("Matching normalized title", "Matching artist", "Similar duration", "Copy-style filename marker detected; treated only as supporting evidence"),
            false
        )
        val result = TwinSenseScanResult(
            listOf(TwinSenseDuplicateGroup("copy-group", listOf(original, numberedCopy), listOf(corroborated), false)),
            2, 0, 0
        )

        assertEquals(listOf("numbered-copy"), TwinSenseCleanupPlanner.tracksToRemove(result).map { it.id })
    }

    @Test fun smartCleanupNeverAutoRemovesAcousticOrVersionWarnings() {
        val first = track("first")
        val second = track("second")
        val acoustic = TwinSenseMatch(first, second, TwinSenseMatchKind.ACOUSTIC, 96, emptyList(), false)
        val alternate = TwinSenseMatch(first, second, TwinSenseMatchKind.EXACT_AUDIO, 99, emptyList(), true)
        val acousticResult = TwinSenseScanResult(listOf(TwinSenseDuplicateGroup("a", listOf(first, second), listOf(acoustic), true)), 2, 0, 0)
        val alternateResult = TwinSenseScanResult(listOf(TwinSenseDuplicateGroup("b", listOf(first, second), listOf(alternate), true)), 2, 0, 0)

        assertTrue(TwinSenseCleanupPlanner.tracksToRemove(acousticResult).isEmpty())
        assertTrue(TwinSenseCleanupPlanner.tracksToRemove(alternateResult).isEmpty())
    }

    private fun track(id: String, title: String = "Track", artist: String = "Artist", bitrate: Int = 0) = MusicTrack(
        id = id, uri = "content://music/$id", title = title, artist = artist, album = "Album",
        albumArtist = artist, genre = "Electronic", durationMs = 30_000L, trackNumber = 1,
        year = 2025, mimeType = "audio/mpeg", sizeBytes = 1024L, modifiedAt = 1L,
        folder = "Music", artworkUri = if (bitrate > 0) "/artwork/$id.jpg" else null,
        addedAt = 1L, isFavorite = false, playCount = 0, lastPlayedAt = null,
        bitrate = bitrate, sampleRate = 44_100, channelCount = 2
    )
}
