package digital.anchorstudio.soundprint

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaylistPlaybackPresetStoreTest {
    private lateinit var context: Context
    private lateinit var store: PlaylistPlaybackPresetStore

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("soundprint_playlist_playback_presets", Context.MODE_PRIVATE)
            .edit().clear().commit()
        store = PlaylistPlaybackPresetStore(context)
    }

    @Test fun presetsPersistIndependentlyPerPlaylist() {
        val workouts = PlaylistPlaybackPreset(
            enabled = true,
            extraBass = true,
            shuffle = true,
            repeat = PlaylistRepeatPreset.ALL,
            loudnessLeveling = true
        )
        val chill = PlaylistPlaybackPreset(enabled = true, repeat = PlaylistRepeatPreset.ONE)

        store.set("workouts", workouts)
        store.set("chill", chill)

        assertEquals(workouts, store.get("workouts"))
        assertEquals(chill, store.get("chill"))
        assertEquals(PlaylistPlaybackPreset(), store.get("unknown"))
    }

    @Test fun invalidSavedRepeatFallsBackToOff() {
        context.getSharedPreferences("soundprint_playlist_playback_presets", Context.MODE_PRIVATE)
            .edit().putString("broken_repeat", "NOT_A_REPEAT").commit()

        assertEquals(PlaylistRepeatPreset.OFF, store.get("broken").repeat)
    }

    @Test fun deletingPlaylistRemovesOnlyItsPreset() {
        store.set("workouts", PlaylistPlaybackPreset(enabled = true, extraBass = true))
        store.set("chill", PlaylistPlaybackPreset(enabled = true, shuffle = true))

        store.delete("workouts")

        assertEquals(PlaylistPlaybackPreset(), store.get("workouts"))
        assertTrue(store.get("chill").enabled)
        assertTrue(store.get("chill").shuffle)
        assertFalse(store.get("chill").extraBass)
    }
}
