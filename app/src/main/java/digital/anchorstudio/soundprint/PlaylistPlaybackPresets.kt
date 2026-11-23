package digital.anchorstudio.soundprint

import android.content.Context

internal enum class PlaylistRepeatPreset { OFF, ALL, ONE }

internal data class PlaylistPlaybackPreset(
    val enabled: Boolean = false,
    val extraBass: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: PlaylistRepeatPreset = PlaylistRepeatPreset.OFF,
    val loudnessLeveling: Boolean = false
)

/** Per-playlist preferences are kept independently of the playlist's song list. */
internal class PlaylistPlaybackPresetStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("soundprint_playlist_playback_presets", Context.MODE_PRIVATE)

    fun get(playlistId: String): PlaylistPlaybackPreset {
        val key = playlistId
        return PlaylistPlaybackPreset(
            enabled = prefs.getBoolean("${key}_enabled", false),
            extraBass = prefs.getBoolean("${key}_bass", false),
            shuffle = prefs.getBoolean("${key}_shuffle", false),
            repeat = runCatching {
                PlaylistRepeatPreset.valueOf(prefs.getString("${key}_repeat", "OFF") ?: "OFF")
            }.getOrDefault(PlaylistRepeatPreset.OFF),
            loudnessLeveling = prefs.getBoolean("${key}_leveling", false)
        )
    }

    fun set(playlistId: String, preset: PlaylistPlaybackPreset) {
        prefs.edit()
            .putBoolean("${playlistId}_enabled", preset.enabled)
            .putBoolean("${playlistId}_bass", preset.extraBass)
            .putBoolean("${playlistId}_shuffle", preset.shuffle)
            .putString("${playlistId}_repeat", preset.repeat.name)
            .putBoolean("${playlistId}_leveling", preset.loudnessLeveling)
            .apply()
    }
}
