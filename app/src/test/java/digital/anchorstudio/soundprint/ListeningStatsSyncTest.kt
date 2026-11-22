package digital.anchorstudio.soundprint

import android.content.Context
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class ListeningStatsSyncTest {
    private lateinit var context:Context
    private lateinit var manager:MusicLibraryManager
    private lateinit var track:MusicTrack
    @Before fun setUp() {
        context=RuntimeEnvironment.getApplication()
        context.deleteDatabase("soundprint_library_management.db")
        context.deleteDatabase("soundprint_music_library.db")
        context.getSharedPreferences("soundprint_account_security",Context.MODE_PRIVATE).edit().clear().commit()
        manager=MusicLibraryManager(context)
        track=MusicTrack(
            id=UUID.randomUUID().toString(),uri="content://music/sync-test",title="Sync test",artist="Anchor",
            album="Tests",albumArtist="Anchor",genre="Test",durationMs=120_000L,trackNumber=1,year=2026,
            mimeType="audio/mpeg",sizeBytes=100L,modifiedAt=1L,folder="Music",artworkUri=null,addedAt=1L,
            isFavorite=false,playCount=0,lastPlayedAt=null,contentHash="sync-test"
        )
        MusicLibraryDatabase(context).apply { upsert(track); close() }
    }
    @After fun tearDown() {
        context.getSharedPreferences("soundprint_account_security",Context.MODE_PRIVATE).edit().clear().commit()
        context.deleteDatabase("soundprint_library_management.db")
        context.deleteDatabase("soundprint_music_library.db")
    }
    @Test fun offlineEventsStayLocalWithoutAnAccountOwner() {
        manager.recordPlay(track.id,0L,track.durationMs)
        manager.recordListeningTime(track.id,15_000L,track.durationMs)
        val events=manager.listeningEvents()
        assertEquals(2,events.size)
        assertTrue(events.all { it.ownerUserId==null && it.synced })
        assertTrue(manager.pendingListeningEvents("account-a").isEmpty())
    }
    @Test fun signedInEventsAreDurablyQueuedForUpload() {
        context.getSharedPreferences("soundprint_account_security",Context.MODE_PRIVATE).edit()
            .putString("listening_stats_sync_user_id","account-a").commit()
        manager.recordPlay(track.id,0L,track.durationMs)
        manager.recordListeningTime(track.id,15_000L,track.durationMs)
        val pending=manager.pendingListeningEvents("account-a")
        assertEquals(2,pending.size)
        assertTrue(pending.all { !it.synced && it.syncId!=null })
        assertEquals(2,pending.map { it.syncId }.distinct().size)
    }
    @Test fun cloudMergeIsIdempotent() {
        val event=LibraryHistoryEvent(0L,track.id,HistoryEventType.LISTEN,100L,12_000L,track.durationMs,"stable-id","account-a",true)
        manager.mergeRemoteListeningEvent(event)
        manager.mergeRemoteListeningEvent(event)
        assertEquals(1,manager.listeningEvents().size)
        assertEquals("stable-id",manager.listeningEvents().single().syncId)
        assertTrue(manager.pendingListeningEvents("account-a").isEmpty())
    }
    @Test fun resetQueuesRemoteDeletionForTheCurrentAccount() {
        context.getSharedPreferences("soundprint_account_security",Context.MODE_PRIVATE).edit()
            .putString("listening_stats_sync_user_id","account-a").commit()
        manager.recordPlay(track.id,0L,track.durationMs)
        manager.clearListeningHistory()
        assertTrue(context.getSharedPreferences("soundprint_account_security",Context.MODE_PRIVATE)
            .getBoolean("listening_stats_clear_cloud_account-a",false))
        assertTrue(manager.listeningEvents().isEmpty())
    }
}
