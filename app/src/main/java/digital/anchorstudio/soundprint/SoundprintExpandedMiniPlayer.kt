package digital.anchorstudio.soundprint

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

@Composable
fun SoundprintExpandedMiniPlayer(
    controller: MediaController?,
    library: MusicLibraryManager,
    onMaximize: () -> Unit
) {
    var track by remember { mutableStateOf<MusicTrack?>(null) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(controller) {
        while (controller != null) {
            position = controller.currentPosition.coerceAtLeast(0L)
            duration = controller.duration.takeIf { it > 0L && it != androidx.media3.common.C.TIME_UNSET } ?: 0L
            delay(250)
        }
    }
    val trackId = controller?.currentMediaItem?.mediaId.orEmpty()
    LaunchedEffect(trackId) {
        track = if (trackId.isBlank()) null else withContext(Dispatchers.IO) { library.song(trackId) }
    }
    val item = controller?.currentMediaItem ?: return
    val title = track?.title?.takeIf(String::isNotBlank) ?: item.mediaMetadata.title?.toString().orEmpty()
    val artist = track?.artist?.takeIf(String::isNotBlank) ?: item.mediaMetadata.artist?.toString().orEmpty()
    val artwork = track?.artworkUri
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .98f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TrackArtwork(artwork, title, Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)), seed = trackId)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(title, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                Text(artist, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            IconButton(onClick = onMaximize) {
                SoundprintIcon(SoundprintIcons.Expand, "Open full player", modifier = Modifier.size(21.dp))
            }
        }
        Slider(
            value = if (duration > 0L) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
            onValueChange = { dragging = true; position = (it * duration).toLong() },
            onValueChangeFinished = { controller?.seekTo(position); dragging = false },
            enabled = duration > 0L
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatMiniTime(position), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(onClick = { controller?.seekToPreviousMediaItem() }, modifier = Modifier.size(40.dp)) {
                    SoundprintIcon(SoundprintIcons.SkipBack, "Previous track", modifier = Modifier.size(21.dp))
                }
                IconButton(onClick = { if (controller?.isPlaying == true) controller.pause() else controller?.play() }, modifier = Modifier.size(44.dp)) {
                    SoundprintIcon(if (controller?.isPlaying == true) SoundprintIcons.Pause else SoundprintIcons.Play, if (controller?.isPlaying == true) "Pause" else "Play", modifier = Modifier.size(24.dp))
                }
                IconButton(onClick = { controller?.seekToNextMediaItem() }, modifier = Modifier.size(40.dp)) {
                    SoundprintIcon(SoundprintIcons.SkipForward, "Next track", modifier = Modifier.size(21.dp))
                }
            }
            Text(formatMiniTime(duration), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatMiniTime(ms: Long): String {
    val seconds = (ms.coerceAtLeast(0L) / 1000L)
    return "%d:%02d".format(seconds / 60L, seconds % 60L)
}
