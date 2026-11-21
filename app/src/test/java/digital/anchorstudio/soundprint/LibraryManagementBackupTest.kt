package digital.anchorstudio.soundprint

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryManagementBackupTest {
    private lateinit var context: Context

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("soundprint_music_library.db")
        context.deleteDatabase("soundprint_library_management.db")
    }

    @After fun tearDown() {
        context.deleteDatabase("soundprint_music_library.db")
        context.deleteDatabase("soundprint_library_management.db")
    }

    @Test fun backupRestoreIncludesHistoryManagedPlaylistsAndSmartPlaylists() {
        val manager = MusicLibraryManager(context)
        val database = MusicLibraryDatabase.getInstance(context)
        val track = MusicTrack(
            id = UUID.randomUUID().toString(), uri = "content://music/backup-managed",
            title = "Backup track", artist = "Artist", album = "Album", albumArtist = "Artist",
            genre = "Electronic", durationMs = 180_000L, trackNumber = 1, year = 2025,
            mimeType = "audio/mpeg", sizeBytes = 1024L, modifiedAt = 100L, folder = "Music",
            artworkUri = null, addedAt = 100L, isFavorite = false, playCount = 0, lastPlayedAt = null
        )
        database.upsert(track)
        manager.recordPlay(track.id, 0L, track.durationMs)
        manager.recordListeningTime(track.id, 12_000L, track.durationMs)
        manager.createPlaylist("Road trip", listOf(track.id))
        manager.saveSmartPlaylist(
            NativeSmartPlaylist(
                name = "Electronic favorites",
                groups = listOf(SmartConditionGroup(conditions = listOf(
                    SmartCondition(field = SmartField.GENRE, operator = SmartOperator.EQ, value = "Electronic")
                )))
            )
        )

        val output = ByteArrayOutputStream()
        manager.writeBackup(output)
        manager.clearAllLibraryData()
        manager.restoreBackup(ByteArrayInputStream(output.toByteArray()), replaceExisting = true)

        val stats = manager.listeningStats().getValue(track.id)
        assertEquals(1, stats.plays)
        assertEquals(12_000L, stats.totalListenedMs)
        assertEquals(listOf(track.id), manager.playlists().single { it.name == "Road trip" }.trackIds)
        assertTrue(manager.smartPlaylists().any { it.name == "Electronic favorites" })
    }
}
