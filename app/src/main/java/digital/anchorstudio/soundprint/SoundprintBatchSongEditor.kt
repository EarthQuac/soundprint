package digital.anchorstudio.soundprint

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun SoundprintSongsLibraryScreen(
    library: MusicLibraryManager,
    playback: SoundprintPlaybackConnection,
    onImport: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var tracks by remember { mutableStateOf(emptyList<MusicTrack>()) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectionMode by remember { mutableStateOf(false) }
    var editorOpen by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var album by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("") }

    LaunchedEffect(query) {
        tracks = withContext(Dispatchers.IO) { library.allSongs(query = query.trim()) }
    }

    fun toggleSelection(id: String) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }
    fun openBatchEditor() {
        title = ""; artist = ""; album = ""; genre = ""
        editorOpen = true
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.IconButton(onClick = onBack) {
                SoundprintIcon(SoundprintIcons.ArrowLeft, contentDescription = "Back")
            }
            Column(Modifier.weight(1f)) {
                Text("Songs", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                Text("${tracks.size} songs${if (selectedIds.isNotEmpty()) " · ${selectedIds.size} selected" else " in your library"}",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selectionMode) {
                TextButton(onClick = { selectedIds = emptySet(); selectionMode = false }) { Text("Cancel") }
            } else {
                TextButton(onClick = { selectionMode = true }) { Text("Select") }
                TextButton(onClick = onImport) { Text("Add music") }
            }
        }
        Spacer(Modifier.height(0.dp))
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(15.dp),
                placeholder = { Text("Search songs, artists, albums…", fontSize = 12.sp) },
                leadingIcon = { SoundprintIcon(SoundprintIcons.Search, null, modifier = Modifier.size(18.dp)) }
            )
            if (selectedIds.isNotEmpty()) {
                Surface(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f)
                ) {
                    Row(
                        Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SoundprintIcon(SoundprintIcons.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                        Text(
                            "${selectedIds.size} selected",
                            Modifier.weight(1f).padding(start = 8.dp),
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        TextButton(onClick = {
                            val visible = tracks.map { it.id }.toSet()
                            selectedIds = if (visible.isNotEmpty() && visible.all { it in selectedIds }) selectedIds - visible
                                else selectedIds + visible
                        }) {
                            Text(if (tracks.isNotEmpty() && tracks.all { it.id in selectedIds }) "Deselect visible" else "Select visible")
                        }
                        OutlinedButton(
                            onClick = ::openBatchEditor,
                            enabled = !saving,
                            shape = CircleShape
                        ) {
                            SoundprintIcon(SoundprintIcons.Pencil, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Edit")
                        }
                    }
                }
            } else if (tracks.isNotEmpty()) {
                Text(
                    "Select songs to edit their information together.",
                    Modifier.padding(start = 3.dp, top = 9.dp),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (status.isNotBlank()) {
                Text(status, Modifier.padding(start = 3.dp, top = 7.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
            }
        }

        if (tracks.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SoundprintIcon(SoundprintIcons.Music2, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(34.dp))
                Text(if (query.isBlank()) "No songs yet" else "No matching songs", Modifier.padding(top = 12.dp),
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(if (query.isBlank()) "Import music to start building your library." else "Try another title, artist or album.",
                    Modifier.padding(top = 5.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                items(tracks, key = { it.id }) { track ->
                    val selected = track.id in selectedIds
                    var menuExpanded by remember(track.id) { mutableStateOf(false) }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable {
                                if (selectionMode) toggleSelection(track.id)
                                else playback.playTracks(tracks, tracks.indexOfFirst { it.id == track.id })
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectionMode) {
                            Checkbox(checked = selected, onCheckedChange = { toggleSelection(track.id) })
                        }
                        NativeArtwork(track.artworkUri, track.id, Modifier.size(48.dp), 10)
                        Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
                            Text(track.title, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                track.artist + if (track.album.isNotBlank() && track.album != "Unknown album") " · ${track.album}" else "",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (track.isFavorite && !selectionMode) {
                            SoundprintIcon(SoundprintIcons.Heart, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                        }
                        if (!selectionMode) {
                            Text(formatBatchDuration(track.durationMs), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Box {
                                androidx.compose.material3.IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(36.dp)) {
                                    SoundprintIcon(SoundprintIcons.EllipsisVertical, contentDescription = "Song options", modifier = Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                    DropdownMenuItem(text = { Text("Play next") }, onClick = {
                                        menuExpanded = false
                                        playback.enqueue(listOf(track), next = true)
                                    })
                                    DropdownMenuItem(text = { Text("Add to queue") }, onClick = {
                                        menuExpanded = false
                                        playback.enqueue(listOf(track))
                                    })
                                    DropdownMenuItem(text = { Text(if (track.isFavorite) "Remove from favorites" else "Add to favorites") }, onClick = {
                                        menuExpanded = false
                                        library.toggleFavorite(track.id)
                                        scope.launch { tracks = withContext(Dispatchers.IO) { library.allSongs(query = query.trim()) } }
                                    })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (editorOpen) {
        AlertDialog(
            onDismissRequest = { if (!saving) editorOpen = false },
            title = {
                Column {
                    Text("Edit ${selectedIds.size} songs", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Text("Only fields you fill in will be changed.", Modifier.padding(top = 4.dp),
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    OutlinedTextField(title, { title = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Title") }, placeholder = { Text("Leave unchanged") }, singleLine = true)
                    OutlinedTextField(artist, { artist = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Artist") }, placeholder = { Text("Leave unchanged") }, singleLine = true)
                    OutlinedTextField(album, { album = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Album") }, placeholder = { Text("Leave unchanged") }, singleLine = true)
                    OutlinedTextField(genre, { genre = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Genre") }, placeholder = { Text("Leave unchanged") }, singleLine = true)
                    Text("This updates the selected tracks in SoundPrint's library. Your audio files are not modified.",
                        fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !saving && listOf(title, artist, album, genre).any { it.isNotBlank() },
                    onClick = {
                        val ids = selectedIds.toList()
                        saving = true
                        scope.launch {
                            val changed = withContext(Dispatchers.IO) {
                                library.updateSongsMetadata(
                                    ids,
                                    title = title.takeIf { it.isNotBlank() },
                                    artist = artist.takeIf { it.isNotBlank() },
                                    album = album.takeIf { it.isNotBlank() },
                                    genre = genre.takeIf { it.isNotBlank() }
                                )
                            }
                            tracks = withContext(Dispatchers.IO) { library.allSongs(query = query.trim()) }
                            selectedIds = emptySet()
                            selectionMode = false
                            status = "Updated $changed songs"
                            saving = false
                            editorOpen = false
                        }
                    }
                ) { Text(if (saving) "Saving…" else "Apply to ${selectedIds.size} songs") }
            },
            dismissButton = {
                TextButton(enabled = !saving, onClick = { editorOpen = false }) { Text("Cancel") }
            }
        )
    }
}

private fun formatBatchDuration(durationMs: Long): String {
    val seconds = (durationMs / 1000L).coerceAtLeast(0L)
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
