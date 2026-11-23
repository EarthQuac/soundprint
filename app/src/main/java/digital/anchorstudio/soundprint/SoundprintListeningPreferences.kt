package digital.anchorstudio.soundprint

import android.content.Context
import android.content.SharedPreferences

/**
 * Free, device-local controls for listening history and automatic personalization.
 * Defaults preserve the existing Soundprint experience for current users.
 */
internal object SoundprintListeningPreferences {
    private const val PREFS_NAME = "soundprint_listening_preferences"

    const val KEY_HISTORY_ENABLED = "history_enabled"
    const val KEY_MUSIC_RECOMMENDATIONS_ENABLED = "music_recommendations_enabled"
    const val KEY_PLAYLIST_RECOMMENDATIONS_ENABLED = "playlist_recommendations_enabled"
    const val KEY_AUTOMATIC_PLAYLISTS_ENABLED = "automatic_playlists_enabled"
    const val KEY_BACKGROUND_ACTIVITY_ENABLED = "background_activity_enabled"

    fun preferences(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun historyEnabled(context: Context): Boolean =
        preferences(context).getBoolean(KEY_HISTORY_ENABLED, true)

    fun musicRecommendationsEnabled(context: Context): Boolean =
        preferences(context).getBoolean(KEY_MUSIC_RECOMMENDATIONS_ENABLED, true)

    fun playlistRecommendationsEnabled(context: Context): Boolean =
        preferences(context).getBoolean(KEY_PLAYLIST_RECOMMENDATIONS_ENABLED, true)

    fun automaticPlaylistsEnabled(context: Context): Boolean =
        preferences(context).getBoolean(KEY_AUTOMATIC_PLAYLISTS_ENABLED, true)

    fun backgroundActivityEnabled(context: Context): Boolean =
        preferences(context).getBoolean(KEY_BACKGROUND_ACTIVITY_ENABLED, true)
}
