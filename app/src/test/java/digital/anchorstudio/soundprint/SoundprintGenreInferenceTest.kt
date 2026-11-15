package digital.anchorstudio.soundprint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundprintGenreInferenceTest {
    @Test fun combinesClapMoodAndLyricThemes() {
        val profile = ClapMoodProfile(
            trackId = "track",
            sourceSize = 1L,
            sourceModifiedAt = 1L,
            modelVersion = CLAP_PROFILE_VERSION,
            analyzedAt = 1L,
            scores = mapOf("intense" to 1f, "angry" to .7f)
        )

        val suggestions = SoundprintGenreInference.suggest(
            profile,
            "I scream through the darkness, demons in my head, ready for war."
        )

        assertEquals("Metal", suggestions.first().genre)
        assertTrue(suggestions.first().evidence.contains("CLAP mood"))
        assertTrue(suggestions.first().evidence.contains("Lyric themes"))
    }

    @Test fun lyricsCanSuggestGenreBeforeClapProfileExists() {
        val suggestions = SoundprintGenreInference.suggest(
            null,
            "Hallelujah, Lord, your grace and faith will carry me home."
        )

        assertTrue(suggestions.any { it.genre == "Gospel" })
    }

    @Test fun noEvidenceDoesNotInventAGenre() {
        assertTrue(SoundprintGenreInference.suggest(null, "").isEmpty())
    }
}
