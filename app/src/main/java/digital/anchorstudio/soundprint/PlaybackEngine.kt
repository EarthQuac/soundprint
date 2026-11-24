package digital.anchorstudio.soundprint

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.audiofx.BassBoost
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SoundprintRepeatMode { OFF, ALL, ONE }

data class PlaybackQueueSnapshot(
    val queueIds: List<String> = emptyList(),
    val currentIndex: Int = -1,
    val positionMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: SoundprintRepeatMode = SoundprintRepeatMode.OFF,
    val savedAtMs: Long = 0L,
    val shuffleIndices: List<Int> = emptyList()
)

data class PlaybackResumePrompt(
    val trackId: String,
    val title: String,
    val artist: String,
    val positionMs: Long
)

internal fun shouldOfferResumePrompt(
    savedAtMs: Long,
    nowMs: Long,
    positionMs: Long,
    rememberPosition: Boolean
): Boolean = rememberPosition && positionMs > 0L && savedAtMs > 0L &&
    nowMs - savedAtMs > 30L * 60L * 1000L

/** Persists queue occurrences (including duplicate songs), the selected item and resume position. */
class PlaybackQueueStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("soundprint_playback_queue", Context.MODE_PRIVATE)

    fun read(): PlaybackQueueSnapshot {
        val raw = preferences.getString(KEY_SNAPSHOT, null) ?: return PlaybackQueueSnapshot()
        return runCatching {
            val json = JSONObject(raw)
            val ids = json.optJSONArray("queueIds") ?: JSONArray()
            PlaybackQueueSnapshot(
                queueIds = (0 until ids.length()).mapNotNull { index -> ids.optString(index).takeIf(String::isNotBlank) },
                currentIndex = json.optInt("currentIndex", -1),
                positionMs = json.optLong("positionMs", 0L).coerceAtLeast(0L),
                shuffleEnabled = json.optBoolean("shuffleEnabled", false),
                repeatMode = runCatching { SoundprintRepeatMode.valueOf(json.optString("repeatMode", "OFF")) }.getOrDefault(SoundprintRepeatMode.OFF),
                savedAtMs = json.optLong("savedAtMs", 0L).coerceAtLeast(0L),
                shuffleIndices = (json.optJSONArray("shuffleIndices") ?: JSONArray()).let { array ->
                    (0 until array.length()).map { array.optInt(it, -1) }.filter { it >= 0 }
                }
            )
        }.getOrDefault(PlaybackQueueSnapshot())
    }

    fun write(snapshot: PlaybackQueueSnapshot) {
        val json = JSONObject().apply {
            put("queueIds", JSONArray(snapshot.queueIds))
            put("currentIndex", snapshot.currentIndex)
            put("positionMs", snapshot.positionMs.coerceAtLeast(0L))
            put("shuffleEnabled", snapshot.shuffleEnabled)
            put("repeatMode", snapshot.repeatMode.name)
            put("savedAtMs", System.currentTimeMillis())
            put("shuffleIndices", JSONArray(snapshot.shuffleIndices))
        }
        preferences.edit().putString(KEY_SNAPSHOT, json.toString()).apply()
    }

    fun clear() {
        preferences.edit().remove(KEY_SNAPSHOT).remove(KEY_RESUME_PROMPT).apply()
    }

    fun writePendingResumePrompt(prompt: PlaybackResumePrompt) {
        val json = JSONObject().apply {
            put("trackId", prompt.trackId)
            put("title", prompt.title)
            put("artist", prompt.artist)
            put("positionMs", prompt.positionMs.coerceAtLeast(0L))
        }
        preferences.edit().putString(KEY_RESUME_PROMPT, json.toString()).apply()
    }

    fun pendingResumePrompt(): PlaybackResumePrompt? {
        val raw = preferences.getString(KEY_RESUME_PROMPT, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            PlaybackResumePrompt(
                trackId = json.optString("trackId"),
                title = json.optString("title"),
                artist = json.optString("artist"),
                positionMs = json.optLong("positionMs", 0L).coerceAtLeast(0L)
            )
        }.getOrNull()
    }

    fun clearPendingResumePrompt() {
        preferences.edit().remove(KEY_RESUME_PROMPT).apply()
    }

    private companion object {
        const val KEY_SNAPSHOT = "snapshot_v1"
        const val KEY_RESUME_PROMPT = "resume_prompt_v1"
    }
}

fun MusicTrack.toSoundprintMediaItem(): MediaItem {
    val artwork = artworkUri?.let { raw -> if (raw.startsWith("/")) Uri.fromFile(File(raw)) else Uri.parse(raw) }
    val metadata = MediaMetadata.Builder()
        .setTitle(title.ifBlank { "Unknown title" })
        .setArtist(artist.ifBlank { "Unknown artist" })
        .setAlbumTitle(album.ifBlank { "Soundprint" })
        .apply { if (artwork != null) setArtworkUri(artwork) }
        .build()
    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(Uri.parse(uri))
        .setMediaMetadata(metadata)
        .build()
}

/**
 * Owns the ExoPlayer and MediaSession for background playback.
 * Media3 handles audio focus, noisy-route changes, media notifications and lock-screen actions.
 */
@UnstableApi
class SoundprintPlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var standbyPlayer: ExoPlayer
    private lateinit var servicePlayerListener: Player.Listener
    private lateinit var mediaSession: MediaSession
    private lateinit var queueStore: PlaybackQueueStore
    private lateinit var library: MusicLibraryManager
    private val listeningWriteQueue = java.util.ArrayDeque<Triple<String, Long, Long>>()
    private var listeningWriteInFlight = false
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private val playbackOptions by lazy {
        getSharedPreferences("soundprint_playback_options", Context.MODE_PRIVATE)
    }
    private var sleepFadeStartedAt = 0L
    private var sleepLastEndAt = 0L
    private var sleepBaseVolume = 1f
    private var crossfadePending = false
    private var crossfadeActive = false
    private var crossfadeNextIndex = C.INDEX_UNSET
    private var crossfadeSourceIndex = C.INDEX_UNSET
    private var crossfadeStartElapsedMs = 0L
    private var crossfadeDurationMs = 0L
    private var crossfadeBaseVolume = 1f
    private var crossfadeQueueItems: List<MediaItem> = emptyList()
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var loudnessSessionId = 0
    private var standbyLoudnessEnhancer: LoudnessEnhancer? = null
    private var standbyLoudnessSessionId = 0
    private var playlistBassBoost: BassBoost? = null
    private var playlistBassSessionId = 0
    private var standbyPlaylistBassBoost: BassBoost? = null
    private var standbyPlaylistBassSessionId = 0
    private var activeTrackId: String? = null
    private var activeOccurrenceKey: String? = null
    private var activeMediaIndex = -1
    private var transitionSequence = 0L
    private var recordedOccurrenceKey: String? = null
    private var lastSamplePositionMs = 0L
    private var lastSampleElapsedMs = 0L
    private var pendingListeningMs = 0L
    private var lastPersistElapsedMs = 0L
    private var lastTrackDurationMs = 0L
    private lateinit var playbackAudioAttributes: AudioAttributes
    @Volatile private var queueRestoreFinished = false
    private var destroyed = false

    private val maintenance = object : Runnable {
        override fun run() {
            if (!::player.isInitialized) return
            sampleListeningTime()
            drainListeningWrites()
            val now = SystemClock.elapsedRealtime()
            if (now - lastPersistElapsedMs >= PERSIST_INTERVAL_MS) persistQueue()
            applyPlaybackOptions()
            prepareOrCheckCrossfade()
            if (player.isPlaying || crossfadePending || crossfadeActive) handler.postDelayed(this, SAMPLE_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        queueStore = PlaybackQueueStore(this)
        library = MusicLibraryManager.getInstance(applicationContext)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        playbackAudioAttributes = audioAttributes
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.shuffleOrder = NoRepeatShuffleOrder(0)
        // Two players must never compete for audio focus: the standby player stays silent until a
        // crossfade hands over, otherwise Android can tell the playing track to stop.
        standbyPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, false)
            .setHandleAudioBecomingNoisy(true)
            .build()
        standbyPlayer.shuffleOrder = NoRepeatShuffleOrder(0)
        mediaSession = MediaSession.Builder(this, player).build()
        handler.post(maintenance)
        servicePlayerListener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK && (crossfadeActive || crossfadePending)) {
                    cancelCrossfade(restoreVolume = true)
                }
                val nextId = mediaItem?.mediaId?.takeIf(String::isNotBlank)
                val previousId = activeTrackId
                val nextIndex = player.currentMediaItemIndex
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK && previousId != null &&
                    (previousId != nextId || activeMediaIndex != nextIndex)
                ) {
                    recordSkip(previousId, lastSamplePositionMs, lastTrackDurationMs)
                }
                flushListeningTime()
                activeTrackId = nextId
                activeMediaIndex = nextIndex
                lastTrackDurationMs = player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L
                transitionSequence += 1L
                activeOccurrenceKey = if (nextId == null) null else "${transitionSequence}:${nextIndex}:${nextId}"
                recordedOccurrenceKey = null
                lastSamplePositionMs = player.currentPosition.coerceAtLeast(0L)
                lastSampleElapsedMs = SystemClock.elapsedRealtime()
                if (player.isPlaying) recordCurrentPlayIfNeeded()
                persistQueue()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    handler.removeCallbacks(maintenance)
                    handler.postDelayed(maintenance, SAMPLE_INTERVAL_MS)
                    recordCurrentPlayIfNeeded()
                    applyPlaybackOptions()
                    lastTrackDurationMs = player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L
                    lastSamplePositionMs = player.currentPosition.coerceAtLeast(0L)
                    lastSampleElapsedMs = SystemClock.elapsedRealtime()
                } else {
                    if (crossfadeActive || crossfadePending) cancelCrossfade(restoreVolume = true)
                    flushListeningTime()
                    lastSampleElapsedMs = 0L
                }
                persistQueue()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                if (shuffleModeEnabled && player.mediaItemCount > 0 &&
                    player.currentMediaItemIndex in 0 until player.mediaItemCount
                ) {
                    // Start at the current track so enabling shuffle mid-queue doesn't skip
                    // the portion of the shuffled permutation that precedes the current item.
                    player.shuffleOrder = NoRepeatShuffleOrder.startingWith(
                        player.mediaItemCount,
                        player.currentMediaItemIndex
                    )
                }
                persistQueue()
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                lastSamplePositionMs = newPosition.positionMs.coerceAtLeast(0L)
                lastSampleElapsedMs = SystemClock.elapsedRealtime()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) flushListeningTime()
                persistQueue()
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("SoundprintPlayback", "Audio playback failed for ${player.currentMediaItem?.localConfiguration?.uri}.", error)
                flushListeningTime()
                persistQueue()
            }
        }
        player.addListener(servicePlayerListener)
        applyPlaybackOptions()
        val savedQueue = queueStore.read()
        serviceScope.launch {
            val tracks = if (savedQueue.queueIds.isEmpty()) emptyList() else withContext(Dispatchers.IO) { library.allSongs() }
            withContext(Dispatchers.Main) { restoreQueue(savedQueue, tracks) }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        persistQueue()
        if (!player.isPlaying) stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        destroyed = true
        handler.removeCallbacks(maintenance)
        handler.removeCallbacks(crossfadeTicker)
        if (::player.isInitialized && ::standbyPlayer.isInitialized && (crossfadeActive || crossfadePending)) {
            cancelCrossfade(restoreVolume = false)
        }
        flushListeningTime()
        persistQueue()
        if (::mediaSession.isInitialized) mediaSession.release()
        if (::player.isInitialized) player.release()
        if (::standbyPlayer.isInitialized) standbyPlayer.release()
        runCatching { loudnessEnhancer?.release() }
        loudnessEnhancer = null
        loudnessSessionId = 0
        runCatching { standbyLoudnessEnhancer?.release() }
        standbyLoudnessEnhancer = null
        standbyLoudnessSessionId = 0
        runCatching { playlistBassBoost?.release() }
        playlistBassBoost = null
        playlistBassSessionId = 0
        runCatching { standbyPlaylistBassBoost?.release() }
        standbyPlaylistBassBoost = null
        standbyPlaylistBassSessionId = 0
        drainListeningWrites()
        // Let queued history writes finish, then stop the scope so nothing outlives the service.
        val writesRunning = synchronized(listeningWriteQueue) { listeningWriteInFlight }
        if (!writesRunning) serviceScope.cancel()
        super.onDestroy()
    }

    private fun prepareOrCheckCrossfade() {
        if (!::player.isInitialized || !::standbyPlayer.isInitialized) return
        if (crossfadeActive) return
        if (crossfadePending) {
            if (player.currentMediaItemIndex != crossfadeSourceIndex) {
                cancelCrossfade(restoreVolume = true)
                return
            }
            if (standbyPlayer.playbackState == Player.STATE_READY && standbyPlayer.playWhenReady.not()) {
                runCatching {
                    val remaining = (player.duration - player.currentPosition).coerceAtLeast(1L)
                    crossfadeDurationMs = minOf(crossfadeDurationMs, remaining)
                    standbyPlayer.volume = 0f
                    standbyPlayer.play()
                    crossfadeActive = true
                    crossfadePending = false
                    crossfadeStartElapsedMs = SystemClock.elapsedRealtime()
                    handler.removeCallbacks(crossfadeTicker)
                    handler.post(crossfadeTicker)
                }.onFailure {
                    Log.w("SoundprintPlayback", "Crossfade could not start; continuing with the normal playlist transition.", it)
                    cancelCrossfade(restoreVolume = true)
                }
            }
            return
        }

        val fadeSeconds = if (playbackOptions.getBoolean("pro_entitled", false)) {
            playbackOptions.getInt(KEY_CROSSFADE_SECONDS, 0).coerceIn(0, 8)
        } else 0
        if (fadeSeconds <= 0 || !player.isPlaying ||
            player.repeatMode == Player.REPEAT_MODE_ONE ||
            playbackOptions.getBoolean(KEY_STOP_AFTER_CURRENT, false) ||
            playbackOptions.getLong(KEY_SLEEP_ENDS_AT, 0L) > 0L
        ) return
        val duration = player.duration
        if (duration <= 0L || duration == C.TIME_UNSET) return
        val nextIndex = player.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET || nextIndex !in 0 until player.mediaItemCount) return
        val remaining = duration - player.currentPosition
        val fadeMs = fadeSeconds * 1000L
        if (remaining <= 0L || remaining > fadeMs) return

        crossfadeQueueItems = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }
        crossfadeSourceIndex = player.currentMediaItemIndex
        crossfadeNextIndex = nextIndex
        crossfadeDurationMs = minOf(fadeMs, remaining)
        crossfadeBaseVolume = player.volume.coerceIn(0f, 1f)
        runCatching {
            standbyPlayer.stop()
            standbyPlayer.clearMediaItems()
            standbyPlayer.setMediaItems(crossfadeQueueItems, nextIndex, 0L)
            standbyPlayer.shuffleOrder = player.shuffleOrder
            standbyPlayer.shuffleModeEnabled = player.shuffleModeEnabled
            standbyPlayer.repeatMode = player.repeatMode
            standbyPlayer.volume = 0f
            standbyPlayer.prepare()
            crossfadePending = true
        }.onFailure {
            Log.w("SoundprintPlayback", "Could not preload the next track for crossfade.", it)
            cancelCrossfade(restoreVolume = true)
        }
    }

    private val crossfadeTicker = object : Runnable {
        override fun run() {
            if (!crossfadeActive || !::player.isInitialized || !::standbyPlayer.isInitialized) return
            val progress = ((SystemClock.elapsedRealtime() - crossfadeStartElapsedMs).toFloat() /
                crossfadeDurationMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
            player.volume = crossfadeBaseVolume * (1f - progress)
            standbyPlayer.volume = crossfadeBaseVolume * progress
            if (progress >= 1f) {
                completeCrossfade()
            } else {
                handler.postDelayed(this, CROSSFADE_TICK_MS)
            }
        }
    }

    private fun completeCrossfade() {
        if (!crossfadeActive) return
        val outgoing = player
        val incoming = standbyPlayer
        val position = incoming.currentPosition.coerceAtLeast(0L)
        val nextIndex = crossfadeNextIndex
        val queueItems = crossfadeQueueItems
        runCatching {
            outgoing.removeListener(servicePlayerListener)
            outgoing.pause()
            outgoing.volume = 0f
            mediaSession.setPlayer(incoming)
            player = incoming
            standbyPlayer = outgoing
            standbyPlayer.setAudioAttributes(playbackAudioAttributes, false)
            player.setAudioAttributes(playbackAudioAttributes, true)
            player.addListener(servicePlayerListener)
            player.volume = crossfadeBaseVolume
            applyPlaybackOptions()
            standbyPlayer.volume = 0f
            standbyPlayer.stop()
            standbyPlayer.clearMediaItems()
            standbyPlayer.setMediaItems(queueItems, nextIndex, position)
            standbyPlayer.shuffleOrder = player.shuffleOrder
            standbyPlayer.shuffleModeEnabled = player.shuffleModeEnabled
            standbyPlayer.repeatMode = player.repeatMode
            standbyPlayer.prepare()

            flushListeningTime()
            activeTrackId = player.currentMediaItem?.mediaId
            activeMediaIndex = player.currentMediaItemIndex
            lastTrackDurationMs = player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L
            transitionSequence += 1L
            activeOccurrenceKey = activeTrackId?.let { "$transitionSequence:$activeMediaIndex:$it" }
            recordedOccurrenceKey = null
            lastSamplePositionMs = position
            lastSampleElapsedMs = SystemClock.elapsedRealtime()
            recordCurrentPlayIfNeeded()
            persistQueue()
        }.onFailure {
            Log.e("SoundprintPlayback", "Crossfade handover failed.", it)
            runCatching { mediaSession.setPlayer(outgoing) }
            player = outgoing
            standbyPlayer = incoming
            runCatching {
                standbyPlayer.setAudioAttributes(playbackAudioAttributes, false)
                player.setAudioAttributes(playbackAudioAttributes, true)
            }
            player.addListener(servicePlayerListener)
            player.volume = crossfadeBaseVolume
            standbyPlayer.removeListener(servicePlayerListener)
            standbyPlayer.pause()
        }
        crossfadeActive = false
        crossfadePending = false
        crossfadeNextIndex = C.INDEX_UNSET
        crossfadeSourceIndex = C.INDEX_UNSET
        crossfadeQueueItems = emptyList()
        handler.removeCallbacks(crossfadeTicker)
    }

    private fun cancelCrossfade(restoreVolume: Boolean) {
        handler.removeCallbacks(crossfadeTicker)
        if (::standbyPlayer.isInitialized) {
            runCatching { standbyPlayer.pause(); standbyPlayer.volume = 0f; standbyPlayer.stop(); standbyPlayer.clearMediaItems() }
        }
        if (restoreVolume && ::player.isInitialized) player.volume = crossfadeBaseVolume
        crossfadePending = false
        crossfadeActive = false
        crossfadeNextIndex = C.INDEX_UNSET
        crossfadeSourceIndex = C.INDEX_UNSET
        crossfadeQueueItems = emptyList()
    }

    /**
     * Sleep timer state is stored outside the screen, so it survives leaving Now Playing
     * and service recreation. The final 15 seconds fade down smoothly before pausing.
     */
    private fun applyPlaybackOptions() {
        if (!::player.isInitialized) return
        val proEntitled = playbackOptions.getBoolean("pro_entitled", false)
        val endAt = playbackOptions.getLong(KEY_SLEEP_ENDS_AT, 0L)
        val stopAfterCurrent = proEntitled && playbackOptions.getBoolean(KEY_STOP_AFTER_CURRENT, false)
        // ExoPlayer playlist transitions are gapless by default; do not pause between items.
        // Stop-after-current deliberately pauses at the end boundary instead.
        val gaplessEnabled = proEntitled && playbackOptions.getBoolean(KEY_GAPLESS_ENABLED, false)
        player.pauseAtEndOfMediaItems = stopAfterCurrent || !gaplessEnabled
        if (::standbyPlayer.isInitialized) standbyPlayer.pauseAtEndOfMediaItems = stopAfterCurrent || !gaplessEnabled
        applyLoudnessLeveling()
        applyPlaylistBassBoost()

        val wasCrossfading = crossfadePending || crossfadeActive
        if (!proEntitled && wasCrossfading) cancelCrossfade(restoreVolume = true)
        val volumeBeforeTimer = if (wasCrossfading) crossfadeBaseVolume else player.volume
        if ((endAt > 0L || stopAfterCurrent) && wasCrossfading) {
            cancelCrossfade(restoreVolume = true)
        }
        if (endAt <= 0L) {
            val hadActiveTimer = sleepLastEndAt > 0L
            sleepFadeStartedAt = 0L
            sleepLastEndAt = 0L
            if (hadActiveTimer) player.volume = sleepBaseVolume
            return
        }
        if (sleepLastEndAt != endAt) {
            if (sleepLastEndAt == 0L) sleepBaseVolume = volumeBeforeTimer
            sleepLastEndAt = endAt
            sleepFadeStartedAt = 0L
        }
        val remaining = endAt - System.currentTimeMillis()
        if (remaining <= 0L) {
            player.pause()
            player.volume = sleepBaseVolume
            playbackOptions.edit().remove(KEY_SLEEP_ENDS_AT).apply()
            sleepFadeStartedAt = 0L
            sleepLastEndAt = 0L
            return
        }
        if (remaining <= SLEEP_FADE_MS) {
            if (sleepFadeStartedAt == 0L) sleepFadeStartedAt = System.currentTimeMillis()
            player.volume = sleepBaseVolume * (remaining.toFloat() / SLEEP_FADE_MS.toFloat()).coerceIn(0f, 1f)
        } else {
            player.volume = sleepBaseVolume
        }
    }

    private fun applyLoudnessLeveling() {
        if (!::player.isInitialized) return
        val enabled = playbackOptions.getBoolean("pro_entitled", false) &&
            playbackOptions.getBoolean(KEY_LOUDNESS_LEVELING, false)
        applyLoudnessToSession(player.audioSessionId, enabled, standby = false)
        if (::standbyPlayer.isInitialized) {
            applyLoudnessToSession(standbyPlayer.audioSessionId, enabled, standby = true)
        }
    }

    private fun applyLoudnessToSession(sessionId: Int, enabled: Boolean, standby: Boolean) {
        if (sessionId <= 0) return
        val currentSessionId = if (standby) standbyLoudnessSessionId else loudnessSessionId
        if (currentSessionId != sessionId) {
            if (standby) {
                runCatching { standbyLoudnessEnhancer?.release() }
                standbyLoudnessEnhancer = runCatching {
                    LoudnessEnhancer(sessionId).also { effect ->
                        effect.setTargetGain(250)
                        effect.enabled = enabled
                    }
                }.onFailure { Log.w("SoundprintPlayback", "Crossfade loudness leveling could not attach.", it) }
                    .getOrNull()
                standbyLoudnessSessionId = sessionId
            } else {
                runCatching { loudnessEnhancer?.release() }
                loudnessEnhancer = runCatching {
                    LoudnessEnhancer(sessionId).also { effect ->
                        effect.setTargetGain(250)
                        effect.enabled = enabled
                    }
                }.onFailure { Log.w("SoundprintPlayback", "Loudness leveling could not attach.", it) }
                    .getOrNull()
                loudnessSessionId = sessionId
            }
        } else {
            runCatching {
                if (standby) standbyLoudnessEnhancer?.enabled = enabled
                else loudnessEnhancer?.enabled = enabled
            }
        }
    }

    /** Playlist bass is attached to both player sessions so crossfade keeps the same sound. */
    private fun applyPlaylistBassBoost() {
        if (!::player.isInitialized) return
        val entitled = playbackOptions.getBoolean("pro_entitled", false)
        val enabled = entitled && playbackOptions.getBoolean("playlist_bass_boost_enabled", false)
        applyBassBoostToSession(player.audioSessionId, enabled, standby = false)
        if (::standbyPlayer.isInitialized) {
            applyBassBoostToSession(standbyPlayer.audioSessionId, enabled, standby = true)
        }
    }

    private fun applyBassBoostToSession(sessionId: Int, enabled: Boolean, standby: Boolean) {
        if (sessionId <= 0) return
        val currentSessionId = if (standby) standbyPlaylistBassSessionId else playlistBassSessionId
        if (currentSessionId != sessionId) {
            if (standby) {
                runCatching { standbyPlaylistBassBoost?.release() }
                standbyPlaylistBassBoost = runCatching {
                    BassBoost(0, sessionId).also { effect ->
                        effect.setStrength(700.toShort())
                        effect.enabled = enabled
                    }
                }.onFailure { Log.w("SoundprintPlayback", "Crossfade bass boost is not supported by this session.", it) }
                    .getOrNull()
                standbyPlaylistBassSessionId = sessionId
            } else {
                runCatching { playlistBassBoost?.release() }
                playlistBassBoost = runCatching {
                    BassBoost(0, sessionId).also { effect ->
                        effect.setStrength(700.toShort())
                        effect.enabled = enabled
                    }
                }.onFailure { Log.w("SoundprintPlayback", "Playlist bass boost is not supported by this audio session.", it) }
                    .getOrNull()
                playlistBassSessionId = sessionId
            }
        } else {
            runCatching {
                if (standby) standbyPlaylistBassBoost?.enabled = enabled
                else playlistBassBoost?.enabled = enabled
            }
        }
    }

    private fun restoreQueue(saved: PlaybackQueueSnapshot, availableLibraryTracks: List<MusicTrack>) {
        if (destroyed || !::player.isInitialized) return
        try {
            restoreSavedQueue(saved, availableLibraryTracks)
        } finally {
            queueRestoreFinished = true
        }
    }

    private fun restoreSavedQueue(saved: PlaybackQueueSnapshot, availableLibraryTracks: List<MusicTrack>) {
        // A user may tap a song while the service is still loading its saved queue.
        // Never let late queue restoration replace that newly requested playback.
        if (player.mediaItemCount > 0 || player.isPlaying) return
        if (!playbackOptions.getBoolean(KEY_RESUME_PLAYBACK, true)) {
            queueStore.clear()
            queueStore.clearPendingResumePrompt()
            return
        }
        if (saved.queueIds.isEmpty()) return
        val availableTracks = availableLibraryTracks.associateBy { it.id }
        val resolved = saved.queueIds.mapIndexedNotNull { oldIndex, id ->
            availableTracks[id]?.let { oldIndex to it }
        }
        if (resolved.isEmpty()) {
            queueStore.clear()
            return
        }
        val requestedIndex = saved.currentIndex.coerceIn(0, saved.queueIds.lastIndex)
        val restoredIndex = resolved.indexOfFirst { it.first == requestedIndex }
            .takeIf { it >= 0 }
            ?: resolved.indexOfFirst { it.first > requestedIndex }.takeIf { it >= 0 }
            ?: resolved.lastIndex
        player.shuffleModeEnabled = saved.shuffleEnabled
        player.repeatMode = saved.repeatMode.toPlayerRepeatMode()
        // Match Cadence: remember the position only when enabled. If the app has been
        // away for more than 30 minutes, ask whether to continue or restart.
        val sameTrack = resolved[restoredIndex].first == requestedIndex
        val rememberPosition = playbackOptions.getBoolean(KEY_REMEMBER_POSITION, true)
        val resumeAtMs = if (sameTrack && rememberPosition) saved.positionMs else 0L
        if (!sameTrack || !rememberPosition || resumeAtMs <= 0L) {
            queueStore.clearPendingResumePrompt()
        } else if (shouldOfferResumePrompt(
            saved.savedAtMs,
            System.currentTimeMillis(),
            resumeAtMs,
            rememberPosition
        )) {
            val track = resolved[restoredIndex].second
            queueStore.writePendingResumePrompt(
                PlaybackResumePrompt(track.id, track.title, track.artist, resumeAtMs)
            )
        }
        player.setMediaItems(resolved.map { it.second.toSoundprintMediaItem() }, restoredIndex, resumeAtMs)
        val restoredShuffleIndices = saved.shuffleIndices.mapNotNull { savedIndex ->
            resolved.indexOfFirst { it.first == savedIndex }.takeIf { it >= 0 }
        }
        player.shuffleOrder = NoRepeatShuffleOrder.fromOrder(resolved.size, restoredShuffleIndices)
        player.shuffleModeEnabled = saved.shuffleEnabled
        player.prepare()
        activeTrackId = player.currentMediaItem?.mediaId
        activeMediaIndex = restoredIndex
        activeOccurrenceKey = if (activeTrackId == null) null else "restored:${restoredIndex}:${activeTrackId}"
    }

    private fun persistQueue() {
        if (!::player.isInitialized) return
        // Until the saved queue has been restored, an empty player must not wipe it from storage.
        if (!queueRestoreFinished && player.mediaItemCount == 0) return
        val ids = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
        if (ids.isEmpty()) {
            queueStore.clear()
            return
        }
        queueStore.write(
            PlaybackQueueSnapshot(
                queueIds = ids,
                currentIndex = player.currentMediaItemIndex,
                positionMs = player.currentPosition.coerceAtLeast(0L),
                shuffleEnabled = player.shuffleModeEnabled,
                repeatMode = player.repeatMode.toSoundprintRepeatMode(),
                shuffleIndices = (player.shuffleOrder as? NoRepeatShuffleOrder)?.snapshotIndices().orEmpty()
            )
        )
        lastPersistElapsedMs = SystemClock.elapsedRealtime()
    }

    private fun recordCurrentPlayIfNeeded() {
        val id = player.currentMediaItem?.mediaId ?: return
        val occurrence = activeOccurrenceKey ?: "${player.currentMediaItemIndex}:${id}"
        if (recordedOccurrenceKey == occurrence) return
        recordedOccurrenceKey = occurrence
        val duration = player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L
        serviceScope.launch {
            try {
                library.recordPlay(id, 0L, duration)
            } catch (error: Exception) {
                Log.e("SoundprintPlayback", "Play statistics write failed for track $id.", error)
            }
        }
    }

    private fun recordSkip(id: String, positionMs: Long, durationMs: Long) {
        serviceScope.launch {
            try {
                library.recordSkip(id, positionMs, durationMs)
            } catch (error: Exception) {
                Log.e("SoundprintPlayback", "Skip statistics write failed for track $id.", error)
            }
        }
    }

    private fun sampleListeningTime() {
        if (!player.isPlaying) return
        val now = SystemClock.elapsedRealtime()
        val position = player.currentPosition.coerceAtLeast(0L)
        lastTrackDurationMs = player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L) ?: lastTrackDurationMs
        val previousPosition = lastSamplePositionMs
        val elapsed = if (lastSampleElapsedMs == 0L) 0L else now - lastSampleElapsedMs
        val delta = position - previousPosition
        if (activeTrackId != null && delta > 0L && elapsed > 0L && delta <= elapsed + 1_500L && delta <= 3_000L) {
            pendingListeningMs += delta
            if (pendingListeningMs >= LISTENING_FLUSH_MS) flushListeningTime()
        }
        lastSamplePositionMs = position
        lastSampleElapsedMs = now
    }

    private fun flushListeningTime() {
        val id = activeTrackId
        val listened = pendingListeningMs
        pendingListeningMs = 0L
        if (id != null && listened > 0L) {
            synchronized(listeningWriteQueue) {
                listeningWriteQueue.addLast(Triple(id, listened, lastTrackDurationMs))
            }
        }
        drainListeningWrites()
    }

    /** All history writes are serialized on Dispatchers.IO; transient failures are retried a few times. */
    private fun drainListeningWrites() {
        val shouldStart = synchronized(listeningWriteQueue) {
            if (listeningWriteInFlight || listeningWriteQueue.isEmpty()) false
            else {
                listeningWriteInFlight = true
                true
            }
        }
        if (!shouldStart) return
        serviceScope.launch {
            var failedAttempts = 0
            while (true) {
                val batch = synchronized(listeningWriteQueue) {
                    if (listeningWriteQueue.isEmpty()) null else listeningWriteQueue.first()
                } ?: break
                try {
                    library.recordListeningTime(batch.first, batch.second, batch.third)
                    synchronized(listeningWriteQueue) {
                        if (listeningWriteQueue.firstOrNull() == batch) listeningWriteQueue.removeFirst()
                    }
                    failedAttempts = 0
                } catch (error: Exception) {
                    failedAttempts += 1
                    // "Song not found" (the track was removed from the library) can never succeed.
                    val giveUp = error is IllegalArgumentException || failedAttempts >= MAX_LISTENING_WRITE_ATTEMPTS || destroyed
                    if (giveUp) {
                        Log.w("SoundprintPlayback", "Dropping a listening statistics write that could not be saved.", error)
                        synchronized(listeningWriteQueue) {
                            if (listeningWriteQueue.firstOrNull() == batch) listeningWriteQueue.removeFirst()
                        }
                        failedAttempts = 0
                    } else {
                        Log.e("SoundprintPlayback", "Listening statistics write failed; retrying.", error)
                        delay(5_000L)
                    }
                }
            }
            synchronized(listeningWriteQueue) { listeningWriteInFlight = false }
            if (destroyed) serviceScope.cancel()
        }
    }

    private fun SoundprintRepeatMode.toPlayerRepeatMode() = when (this) {
        SoundprintRepeatMode.ALL -> Player.REPEAT_MODE_ALL
        SoundprintRepeatMode.ONE -> Player.REPEAT_MODE_ONE
        SoundprintRepeatMode.OFF -> Player.REPEAT_MODE_OFF
    }

    private fun Int.toSoundprintRepeatMode() = when (this) {
        Player.REPEAT_MODE_ALL -> SoundprintRepeatMode.ALL
        Player.REPEAT_MODE_ONE -> SoundprintRepeatMode.ONE
        else -> SoundprintRepeatMode.OFF
    }

    private companion object {
        const val SAMPLE_INTERVAL_MS = 1_000L
        const val PERSIST_INTERVAL_MS = 5_000L
        const val LISTENING_FLUSH_MS = 10_000L
        const val RESUME_PROMPT_AFTER_MS = 30L * 60L * 1000L
        const val KEY_REMEMBER_POSITION = "remember_position"
        const val KEY_RESUME_PLAYBACK = "resume_playback"
        const val MAX_LISTENING_WRITE_ATTEMPTS = 5
        const val SLEEP_FADE_MS = 15_000L
        const val CROSSFADE_TICK_MS = 50L
        const val KEY_SLEEP_ENDS_AT = "sleep_ends_at"
        const val KEY_STOP_AFTER_CURRENT = "stop_after_current"
        const val KEY_CROSSFADE_SECONDS = "crossfade_seconds"
        const val KEY_GAPLESS_ENABLED = "gapless_enabled"
        const val KEY_LOUDNESS_LEVELING = "loudness_leveling"
    }
}

/** Activity-side connection to the service. Screens can share one connection and observe its Player. */
class SoundprintPlaybackConnection(
    context: Context,
    private val onConnected: (MediaController) -> Unit = {},
    private val onPlayRequested: () -> Unit = {}
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executor { command -> mainHandler.post(command) }
    @Volatile var controller: MediaController? = null
        private set
    @Volatile private var closed = false
    private data class PendingPlay(val tracks: List<MusicTrack>, val startIndex: Int, val shuffle: Boolean)
    @Volatile private var pendingPlay: PendingPlay? = null
    private var pauseFadeRunnable: Runnable? = null
    private var pauseFadeBaseVolume = 1f

    private val future = MediaController.Builder(
        context.applicationContext,
        SessionToken(context.applicationContext, ComponentName(context, SoundprintPlaybackService::class.java))
    ).buildAsync()

    init {
        future.addListener({
            val connected = try {
                future.get()
            } catch (error: Exception) {
                Log.e("SoundprintPlayback", "Could not connect to the Android playback service.", error)
                return@addListener
            }
            if (closed) connected.release() else {
                controller = connected
                onConnected(connected)
                val queued = synchronized(this) { pendingPlay.also { pendingPlay = null } }
                if (queued != null && !closed) startPlayback(connected, queued)
            }
        }, executor)
    }

    internal fun playTracks(
        tracks: List<MusicTrack>,
        startIndex: Int = 0,
        shuffle: Boolean = false,
        playlistPreset: PlaylistPlaybackPreset? = null
    ) {
        if (tracks.isEmpty() || closed) return
        val presetEntitled = pausePreferences.getBoolean("pro_entitled", false)
        if (playlistPreset != null && presetEntitled) applyPlaylistPreset(playlistPreset)
        else restorePlaylistPreset()
        val requestedIndex = startIndex.coerceIn(tracks.indices)
        val ignoreClips = pausePreferences.getBoolean("ignore_short_tracks", false)
        val eligible = tracks.withIndex().filter { !ignoreClips || it.value.durationMs >= 30_000L }
        if (eligible.isEmpty()) return
        val mappedStartIndex = eligible.indexOfFirst { it.index >= requestedIndex }.takeIf { it >= 0 } ?: eligible.lastIndex
        val effectiveShuffle = shuffle
        val selectedTrackId = eligible[mappedStartIndex].value.id
        val playbackTracks = if (effectiveShuffle) eligible.map { it.value }.distinctBy { it.id } else eligible.map { it.value }
        val playbackStartIndex = playbackTracks.indexOfFirst { it.id == selectedTrackId }.coerceAtLeast(0)
        val request = PendingPlay(playbackTracks, playbackStartIndex, effectiveShuffle)
        val player = controller
        if (player == null) {
            synchronized(this) {
                val connected = controller
                if (connected == null) {
                    // A tap made while MediaController is connecting must not be discarded.
                    pendingPlay = request
                    return
                }
                startPlayback(connected, request)
            }
        } else startPlayback(player, request)
    }

    private fun startPlayback(player: MediaController, request: PendingPlay) {
        if (closed || request.tracks.isEmpty()) return
        runCatching {
            if (pausePreferences.getBoolean("playlist_preset_active", false)) {
                val repeatPreset = runCatching {
                    PlaylistRepeatPreset.valueOf(pausePreferences.getString("playlist_repeat_mode", "OFF") ?: "OFF")
                }.getOrDefault(PlaylistRepeatPreset.OFF)
                player.repeatMode = when (repeatPreset) {
                    PlaylistRepeatPreset.ALL -> Player.REPEAT_MODE_ALL
                    PlaylistRepeatPreset.ONE -> Player.REPEAT_MODE_ONE
                    PlaylistRepeatPreset.OFF -> Player.REPEAT_MODE_OFF
                }
            }
            val mediaItems = request.tracks.map { track ->
                require(track.uri.isNotBlank()) { "The selected song has no playable file location: ${track.title}" }
                track.toSoundprintMediaItem()
            }
            if (!pausePreferences.getBoolean("replace_queue_on_play", false) && player.mediaItemCount > 0) {
                val targetTrackId = request.tracks[request.startIndex.coerceIn(request.tracks.indices)].id
                player.shuffleModeEnabled = false
                if (request.shuffle) deduplicateQueueByMediaId(player)
                val existingIds = if (request.shuffle) {
                    (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }.toSet()
                } else emptySet()
                val tracksToAppend = if (request.shuffle) request.tracks.filterNot { it.id in existingIds } else request.tracks
                if (tracksToAppend.isNotEmpty()) player.addMediaItems(tracksToAppend.map(MusicTrack::toSoundprintMediaItem))
                if (request.shuffle) {
                    val targetIndex = (0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId == targetTrackId }
                    if (targetIndex != null) player.seekTo(targetIndex, 0L)
                    player.shuffleModeEnabled = true
                } else {
                    val firstAddedIndex = player.mediaItemCount - tracksToAppend.size
                    player.seekTo(firstAddedIndex + request.startIndex.coerceIn(request.tracks.indices), 0L)
                }
            } else {
                if (request.shuffle) player.shuffleModeEnabled = false
                player.setMediaItems(mediaItems, request.startIndex.coerceIn(request.tracks.indices), 0L)
                player.shuffleModeEnabled = request.shuffle
            }
            player.prepare()
            player.play()
        }.onSuccess {
            mainHandler.post {
                if (!closed) onPlayRequested()
            }
        }.onFailure { error ->
            Log.e("SoundprintPlayback", "Could not start the requested music queue.", error)
        }
    }

    fun playAt(index: Int) {
        val player = controller ?: return
        if (index !in 0 until player.mediaItemCount) return
        player.seekTo(index, 0L)
        player.play()
    }

    fun toggle() {
        val player = controller ?: return
        if (!player.isPlaying) {
            cancelPauseFade(restoreVolume = false)
            player.play()
            return
        }
        if (pauseFadeRunnable != null) {
            cancelPauseFade(restoreVolume = true)
            return
        }
        val options = pausePreferences
        if (!options.getBoolean(KEY_FADE_ON_PAUSE, false)) {
            player.pause()
            return
        }
        pauseFadeBaseVolume = player.volume.coerceIn(0f, 1f)
        val startedAt = SystemClock.elapsedRealtime()
        val durationMs = PAUSE_FADE_DURATION_MS
        lateinit var fade: Runnable
        fade = Runnable {
            if (controller !== player || !player.isPlaying) {
                cancelPauseFade(restoreVolume = true)
                return@Runnable
            }
            val progress = ((SystemClock.elapsedRealtime() - startedAt).toFloat() / durationMs).coerceIn(0f, 1f)
            player.volume = pauseFadeBaseVolume * (1f - progress)
            if (progress >= 1f) {
                pauseFadeRunnable = null
                player.pause()
                player.volume = pauseFadeBaseVolume
            } else {
                mainHandler.postDelayed(fade, PAUSE_FADE_TICK_MS)
            }
        }
        pauseFadeRunnable = fade
        mainHandler.post(fade)
    }

    private val controllerContext: Context = context.applicationContext
    private val pausePreferences: android.content.SharedPreferences by lazy {
        controllerContext.getSharedPreferences("soundprint_playback_options", Context.MODE_PRIVATE)
    }

    private fun deduplicateQueueByMediaId(player: MediaController) {
        if (player.mediaItemCount < 2) return
        val currentIndex = player.currentMediaItemIndex
        val currentId = player.currentMediaItem?.mediaId
        val seen = HashSet<String>()
        if (!currentId.isNullOrBlank()) seen.add(currentId)
        for (index in player.mediaItemCount - 1 downTo 0) {
            if (index == currentIndex) continue
            val id = player.getMediaItemAt(index).mediaId
            if (id.isBlank() || !seen.add(id)) player.removeMediaItem(index)
        }
    }

    private fun cancelPauseFade(restoreVolume: Boolean) {
        pauseFadeRunnable?.let(mainHandler::removeCallbacks)
        pauseFadeRunnable = null
        if (restoreVolume) controller?.volume = pauseFadeBaseVolume
    }

    fun next() { controller?.seekToNextMediaItem() }

    fun previous() {
        val player = controller ?: return
        if (player.currentPosition > PREVIOUS_RESTART_THRESHOLD_MS) player.seekTo(0L)
        else player.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) { controller?.seekTo(positionMs.coerceAtLeast(0L)) }

    fun enqueue(tracks: List<MusicTrack>, next: Boolean = false) {
        if (tracks.isEmpty()) return
        val ignoreClips = pausePreferences.getBoolean("ignore_short_tracks", false)
        val eligibleTracks = tracks.filter { !ignoreClips || it.durationMs >= 30_000L }
        if (eligibleTracks.isEmpty()) return
        val player = controller ?: return
        if (player.mediaItemCount == 0) {
            playTracks(eligibleTracks)
            return
        }
        val queueTracks = if (player.shuffleModeEnabled) {
            val queuedIds = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }.toSet()
            eligibleTracks.distinctBy { it.id }.filterNot { it.id in queuedIds }
        } else eligibleTracks
        if (queueTracks.isEmpty()) return
        val insertionIndex = if (next) (player.currentMediaItemIndex + 1).coerceAtLeast(0) else player.mediaItemCount
        player.addMediaItems(insertionIndex, queueTracks.map(MusicTrack::toSoundprintMediaItem))
    }

    /** Replace only the upcoming tracks, keeping the currently playing item untouched. */
    fun replaceUpcoming(tracks: List<MusicTrack>) {
        val player = controller ?: return
        val currentIndex = player.currentMediaItemIndex
        if (currentIndex < 0) {
            if (tracks.isNotEmpty()) playTracks(tracks)
            return
        }
        for (index in player.mediaItemCount - 1 downTo currentIndex + 1) player.removeMediaItem(index)
        val currentId = player.currentMediaItem?.mediaId
        val replacementTracks = if (player.shuffleModeEnabled) tracks.distinctBy { it.id }.filterNot { it.id == currentId } else tracks
        if (replacementTracks.isNotEmpty()) player.addMediaItems(currentIndex + 1, replacementTracks.map(MusicTrack::toSoundprintMediaItem))
    }

    fun removeFromQueue(index: Int) {
        val player = controller ?: return
        if (index in 0 until player.mediaItemCount) player.removeMediaItem(index)
    }

    fun removeTrackFromQueue(trackId: String) {
        val player = controller ?: return
        for (index in player.mediaItemCount - 1 downTo 0) {
            if (player.getMediaItemAt(index).mediaId == trackId) player.removeMediaItem(index)
        }
    }

    fun moveInQueue(from: Int, to: Int) {
        val player = controller ?: return
        if (from in 0 until player.mediaItemCount && to in 0 until player.mediaItemCount && from != to) player.moveMediaItem(from, to)
    }

    fun clearQueue() { controller?.clearMediaItems() }

    fun setShuffle(enabled: Boolean) {
        val player = controller ?: return
        if (enabled) {
            deduplicateQueueByMediaId(player)
            // Reset the bag with the current track first; all other queue entries follow once.
            player.shuffleModeEnabled = false
            player.shuffleModeEnabled = true
        } else {
            player.shuffleModeEnabled = false
        }
    }

    /** Apply an explicit per-playlist Pro preset before starting that playlist's queue. */
    internal fun applyPlaylistPreset(preset: PlaylistPlaybackPreset) {
        val entitled = pausePreferences.getBoolean("pro_entitled", false)
        if (!entitled) {
            restorePlaylistPreset()
            return
        }
        if (!pausePreferences.getBoolean("playlist_preset_active", false)) {
            val player = controller
            val currentRepeat = player?.repeatMode ?: Player.REPEAT_MODE_OFF
            val repeatName = when (currentRepeat) {
                Player.REPEAT_MODE_ALL -> PlaylistRepeatPreset.ALL.name
                Player.REPEAT_MODE_ONE -> PlaylistRepeatPreset.ONE.name
                else -> PlaylistRepeatPreset.OFF.name
            }
            pausePreferences.edit()
                .putBoolean("playlist_saved_shuffle", player?.shuffleModeEnabled ?: false)
                .putString("playlist_saved_repeat", repeatName)
                .putBoolean("playlist_saved_loudness_leveling", pausePreferences.getBoolean("loudness_leveling", false))
                .putBoolean("playlist_preset_active", true)
                .apply()
        }
        pausePreferences.edit()
            .putBoolean("playlist_bass_boost_enabled", entitled && preset.extraBass)
            .putBoolean("loudness_leveling", entitled && preset.loudnessLeveling)
            .putString("playlist_repeat_mode", if (entitled) preset.repeat.name else PlaylistRepeatPreset.OFF.name)
            .apply()
        controller?.let { player ->
            player.shuffleModeEnabled = entitled && preset.shuffle
            player.repeatMode = if (!entitled) Player.REPEAT_MODE_OFF else when (preset.repeat) {
                PlaylistRepeatPreset.ALL -> Player.REPEAT_MODE_ALL
                PlaylistRepeatPreset.ONE -> Player.REPEAT_MODE_ONE
                PlaylistRepeatPreset.OFF -> Player.REPEAT_MODE_OFF
            }
        }
    }

    private fun restorePlaylistPreset() {
        if (!pausePreferences.getBoolean("playlist_preset_active", false)) return
        val savedShuffle = pausePreferences.getBoolean("playlist_saved_shuffle", false)
        val savedRepeat = runCatching {
            PlaylistRepeatPreset.valueOf(pausePreferences.getString("playlist_saved_repeat", "OFF") ?: "OFF")
        }.getOrDefault(PlaylistRepeatPreset.OFF)
        val savedLoudnessLeveling = pausePreferences.getBoolean("playlist_saved_loudness_leveling", false)
        pausePreferences.edit()
            .putBoolean("playlist_bass_boost_enabled", false)
            .putBoolean("loudness_leveling", savedLoudnessLeveling)
            .putString("playlist_repeat_mode", savedRepeat.name)
            .remove("playlist_saved_loudness_leveling")
            .putBoolean("playlist_preset_active", false)
            .apply()
        controller?.let { player ->
            player.shuffleModeEnabled = savedShuffle
            player.repeatMode = when (savedRepeat) {
                PlaylistRepeatPreset.ALL -> Player.REPEAT_MODE_ALL
                PlaylistRepeatPreset.ONE -> Player.REPEAT_MODE_ONE
                PlaylistRepeatPreset.OFF -> Player.REPEAT_MODE_OFF
            }
        }
    }

    fun cycleRepeat() {
        val player = controller ?: return
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun close() {
        closed = true
        controller?.release()
        controller = null
        if (!future.isDone) future.cancel(true)
    }

    private companion object {
        const val PREVIOUS_RESTART_THRESHOLD_MS = 4_000L
        const val PAUSE_FADE_DURATION_MS = 320L
        const val PAUSE_FADE_TICK_MS = 20L
        const val KEY_FADE_ON_PAUSE = "fade_on_pause"
    }
}
