package digital.anchorstudio.soundprint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TwinSenseAudioAnalysisTest {
    private fun track(id: String, durationMs: Long = 180_000L) = MusicTrack(
        id = id,
        uri = "content://music/$id",
        title = "Same song",
        artist = "Same artist",
        album = "Same album",
        albumArtist = "",
        genre = "",
        durationMs = durationMs,
        trackNumber = 0,
        year = 2024,
        mimeType = "audio/mpeg",
        sizeBytes = 4_000_000L,
        modifiedAt = 10L,
        folder = "",
        artworkUri = null,
        addedAt = 1L,
        isFavorite = false,
        playCount = 0,
        lastPlayedAt = null
    )

    private fun profile(
        track: MusicTrack,
        chroma: List<Double> = List(12) { 1.0 / 12.0 },
        low: Double = 0.25,
        mid: Double = 0.50,
        high: Double = 0.25,
        centroid: Double = 1_800.0,
        flatness: Double = 0.2,
        bpm: Int = 120,
        tempoConfidence: Double = 0.8
    ) = TwinSenseAudioAnalysisProfile(
        trackId = track.id,
        version = TwinSenseAudioAnalysisProfile.ANALYSIS_VERSION,
        sourceSize = track.sizeBytes,
        sourceMime = track.mimeType,
        durationSeconds = track.durationMs / 1000.0,
        sampleRateHz = 44_100,
        spectralCentroidHz = centroid,
        estimatedTempoBpm = bpm,
        tempoConfidence = tempoConfidence,
        spectralFlatness = flatness,
        lowBandRatio = low,
        midBandRatio = mid,
        highBandRatio = high,
        chroma = chroma
    )

    @Test fun identicalProfilesProduceAnAcousticMatch() {
        val first = track("profile-a")
        val second = track("profile-b")
        val match = TwinSenseAudioAnalysis.compare(first, second, profile(first), profile(second))

        val found = requireNotNull(match)
        assertEquals(TwinSenseMatchKind.ACOUSTIC, found.kind)
        assertEquals(93, found.confidence)
        assertTrue(found.reasons.contains("Similar harmonic profile"))
        assertFalse(found.versionWarning)
    }

    @Test fun materiallyDifferentProfilesAreNotMatched() {
        val first = track("profile-a")
        val second = track("profile-b")
        val firstProfile = profile(first)
        val secondProfile = profile(
            second,
            chroma = listOf(1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
            low = 0.9,
            mid = 0.05,
            high = 0.05,
            centroid = 8_000.0,
            flatness = 0.95,
            bpm = 190,
            tempoConfidence = 0.9
        )

        assertNull(TwinSenseAudioAnalysis.compare(first, second, firstProfile, secondProfile))
    }

    @Test fun durationDifferencesRemainManualReviewWarnings() {
        val first = track("profile-a")
        val second = track("profile-b", durationMs = 190_000L)
        val match = TwinSenseAudioAnalysis.compare(first, second, profile(first), profile(second))

        val found = requireNotNull(match)
        assertTrue(found.versionWarning)
        assertEquals(TwinSenseMatchKind.ALTERNATIVE, found.kind)
        assertTrue(found.reasons.any { it.contains("alternate version") })
        assertTrue(found.reasons.any { it.contains("shorter by") })
    }

    @Test fun analysisProfilesRoundTripAndInvalidateOnSourceChanges() {
        val originalTrack = track("profile-a")
        val original = profile(originalTrack)
        val restored = TwinSenseAudioAnalysisProfile.fromJson(original.toJson())

        val found = requireNotNull(restored)
        assertTrue(found.matchesSource(originalTrack))
        assertTrue(!found.matchesSource(originalTrack.copy(sizeBytes = originalTrack.sizeBytes + 1L)))
    }
}
