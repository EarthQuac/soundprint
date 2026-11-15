package digital.anchorstudio.soundprint

/**
 * Combines acoustic CLAP mood evidence with lyric-theme evidence. This is a
 * lightweight, explainable genre recommender; it never writes tags by itself.
 */
data class SoundprintGenreSuggestion(
    val genre: String,
    val score: Float,
    val evidence: String
)

object SoundprintGenreInference {
    val genres = listOf(
        "Alternative", "Ambient", "Blues", "Classical", "Country", "Dance",
        "Dream Pop", "Electronic", "Folk", "Funk", "Gospel", "Hip-Hop",
        "House", "Indie", "Jazz", "Metal", "Pop", "Punk", "R&B",
        "Reggae", "Rock", "Soul", "Soundtrack", "Synth-pop", "Techno",
        "Trip-hop"
    )

    private val moodGenres = mapOf(
        "workout" to listOf("Electronic", "Hip-Hop", "Rock", "Metal"),
        "driving" to listOf("Rock", "Electronic", "Pop"),
        "chill" to listOf("Ambient", "R&B", "Indie", "Trip-hop"),
        "focus" to listOf("Classical", "Ambient", "Soundtrack"),
        "party" to listOf("Dance", "House", "Pop", "Hip-Hop"),
        "upbeat" to listOf("Pop", "Dance", "Funk"),
        "happy" to listOf("Pop", "Funk", "Dance"),
        "sad" to listOf("Indie", "R&B", "Alternative", "Soul"),
        "romantic" to listOf("R&B", "Soul", "Pop"),
        "sleep" to listOf("Ambient", "Classical"),
        "late-night" to listOf("R&B", "Jazz", "Trip-hop"),
        "angry" to listOf("Metal", "Punk", "Rock"),
        "dreamy" to listOf("Dream Pop", "Shoegaze", "Ambient"),
        "peaceful" to listOf("Ambient", "Folk", "Classical"),
        "melancholy" to listOf("Alternative", "Indie", "Soul"),
        "confident" to listOf("Hip-Hop", "Rock", "R&B"),
        "nostalgic" to listOf("Synth-pop", "Classic Rock", "Pop"),
        "intense" to listOf("Metal", "Hard Rock", "Electronic", "Alternative")
    )

    private val lyricSignals = listOf(
        "Metal" to listOf("scream", "rage", "chaos", "blood", "fight", "demons", "darkness", "war", "break the chains"),
        "Hip-Hop" to listOf("bars", "flow", "rhymes", "crew", "streets", "hustle", "mic", "beats", "money", "crown"),
        "R&B" to listOf("baby", "touch", "tonight", "desire", "slow dance", "body", "love me", "heart"),
        "Country" to listOf("truck", "whiskey", "small town", "dirt road", "mama", "county", "boots", "blue jeans"),
        "Rock" to listOf("guitar", "fire", "run away", "break free", "scream", "rebel", "highway", "alive"),
        "Pop" to listOf("dance", "tonight", "forever", "party", "lights", "summer", "beautiful", "tonight"),
        "Gospel" to listOf("hallelujah", "jesus", "lord", "heaven", "prayer", "faith", "grace", "amen"),
        "Reggae" to listOf("irie", "rasta", "babylon", "one love", "jah", "island", "freedom"),
        "Jazz" to listOf("midnight", "blue", "smoke", "moonlight", "satin", "swing", "improvisation"),
        "Folk" to listOf("river", "mountain", "home", "old man", "mother", "story", "harvest", "wander"),
        "Soul" to listOf("heartache", "soul", "love", "lonely", "tears", "hold me", "miss you"),
        "Punk" to listOf("system", "revolt", "authority", "anarchy", "riot", "no future", "fight back"),
        "Dance" to listOf("move your body", "dance floor", "hands up", "tonight", "beat", "party"),
        "Ambient" to listOf("silence", "breathe", "floating", "weightless", "stillness", "stars")
    )

    fun suggest(profile: ClapMoodProfile?, lyrics: String): List<SoundprintGenreSuggestion> {
        val scores = linkedMapOf<String, Float>()
        val evidence = linkedMapOf<String, MutableSet<String>>()
        profile?.scores?.forEach { (mood, score) ->
            moodGenres[mood].orEmpty().forEachIndexed { index, genre ->
                val weight = score.coerceIn(0f, 1f) * (1f / (1f + index * .35f))
                scores[genre] = (scores[genre] ?: 0f) + weight * .62f
                evidence.getOrPut(genre) { linkedSetOf() }.add("CLAP mood: $mood")
            }
        }
        val normalizedLyrics = lyrics.lowercase()
        if (normalizedLyrics.isNotBlank()) {
            lyricSignals.forEach { (genre, terms) ->
                val hits = terms.count { term ->
                    Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(term) + "(?![\\p{L}\\p{N}])")
                        .containsMatchIn(normalizedLyrics)
                }
                if (hits > 0) {
                    scores[genre] = (scores[genre] ?: 0f) + (hits.coerceAtMost(4) * .34f)
                    evidence.getOrPut(genre) { linkedSetOf() }.add("Lyric themes")
                }
            }
        }
        return scores.entries
            .filter { it.value >= .18f }
            .sortedByDescending { it.value }
            .distinctBy { it.key.lowercase() }
            .take(5)
            .map { (genre, score) ->
                SoundprintGenreSuggestion(genre, score, evidence[genre].orEmpty().joinToString(" + "))
            }
    }
}
