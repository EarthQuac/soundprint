package digital.anchorstudio.soundprint

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SoundprintSongsLibraryScreen(
    library: MusicLibraryManager,
    playback: SoundprintPlaybackConnection,
    onImport: () -> Unit,
    onOpenTwinSense: () -> Unit,
    onBack: () -> Unit
) {
    val proAccess = LocalSoundprintProUiAccess.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var tracks by remember { mutableStateOf(emptyList<MusicTrack>()) }
    var recycleItems by remember { mutableStateOf(emptyList<RecycleBinEntry>()) }
    var loading by remember { mutableStateOf(true) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectionMode by remember { mutableStateOf(false) }
    var sortBy by remember { mutableStateOf("Title") }
    var descending by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var recycleOpen by remember { mutableStateOf(false) }
    var confirmEmptyBin by remember { mutableStateOf(false) }
    var editorOpen by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var album by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("") }

    suspend fun refreshLibrary() {
        val loaded = withContext(Dispatchers.IO) {
            library.allSongs(query = query.trim()) to library.recycleBin()
        }
        tracks = loaded.first
        recycleItems = loaded.second
    }

    LaunchedEffect(query) {
        loading = true
        tracks = withContext(Dispatchers.IO) { library.allSongs(query = query.trim()) }
        loading = false
    }
    LaunchedEffect(library) {
        recycleItems = withContext(Dispatchers.IO) { library.recycleBin() }
    }

    val visibleTracks = remember(tracks, sortBy, descending) {
        val sorted = when (sortBy) {
            "Artist" -> tracks.sortedWith(compareBy<MusicTrack> { it.artist.lowercase() }.thenBy { it.title.lowercase() })
            "Album" -> tracks.sortedWith(compareBy<MusicTrack> { it.album.lowercase() }.thenBy { it.trackNumber })
            "Date added" -> tracks.sortedBy { it.addedAt }
            "Duration" -> tracks.sortedBy { it.durationMs }
            else -> tracks.sortedBy { it.title.lowercase() }
        }
        if (descending) sorted.asReversed() else sorted
    }

    fun toggleSelection(id: String) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
        if (selectedIds.isEmpty()) selectionMode = false
    }
    fun openBatchEditor() {
        title = ""; artist = ""; album = ""; genre = ""
        editorOpen = true
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
                    SoundprintIcon(SoundprintIcons.ArrowLeft, "Back")
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Songs", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                    Text("${tracks.size} songs${if (selectedIds.isNotEmpty()) " · ${selectedIds.size} selected" else ""}",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Button(
                onClick = onImport,
                modifier = Modifier.padding(start = 12.dp, top = 10.dp).height(42.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface)
            ) {
                SoundprintIcon(SoundprintIcons.FolderPlus, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add music", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, placeholder = { Text("Filter songs, artists, albums...", fontSize = 14.sp) },
                leadingIcon = { SoundprintIcon(SoundprintIcons.Search, null, Modifier.size(20.dp)) },
                shape = RoundedCornerShape(24.dp), colors = soundprintOutlinedFieldColors()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { sortMenuExpanded = true }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(24.dp)) {
                        SoundprintIcon(SoundprintIcons.ArrowDownUp, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Sort: $sortBy", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                    }
                    DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                        listOf("Title", "Artist", "Album", "Date added", "Duration").forEach { option ->
                            DropdownMenuItem(text = { Text(if (sortBy == option) "✓ $option" else option) }, onClick = { sortBy = option; sortMenuExpanded = false })
                        }
                    }
                }
                OutlinedButton(onClick = { descending = !descending }, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(24.dp)) {
                    SoundprintIcon(SoundprintIcons.ArrowDownUp, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (descending) "Descending" else "Ascending", fontSize = 14.sp, maxLines = 1)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { if (proAccess.entitled) onOpenTwinSense() else proAccess.onOpenPro() },
                    modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface)
                ) {
                    SoundprintIcon(SoundprintIcons.CopyCheck, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("TwinSense", fontSize = 14.sp, maxLines = 1)
                    ProFeatureBadge()
                }
                Button(
                    onClick = { recycleOpen = !recycleOpen; if (recycleOpen) scope.launch { recycleItems = withContext(Dispatchers.IO) { library.recycleBin() } } },
                    modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface)
                ) {
                    SoundprintIcon(SoundprintIcons.Trash, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Recycle Bin", fontSize = 14.sp, maxLines = 1)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { if (visibleTracks.isNotEmpty()) playback.playTracks(visibleTracks) },
                    enabled = visibleTracks.isNotEmpty(), modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                ) {
                    SoundprintIcon(SoundprintIcons.Play, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Play all", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = { if (visibleTracks.isNotEmpty()) playback.playTracks(visibleTracks, shuffle = true) },
                    enabled = visibleTracks.isNotEmpty(), modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface)
                ) {
                    SoundprintIcon(SoundprintIcons.Shuffle, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Shuffle", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (selectedIds.isNotEmpty()) {
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f)) {
                    Row(Modifier.padding(horizontal = 13.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        SoundprintIcon(SoundprintIcons.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                        Text("${selectedIds.size} selected", Modifier.weight(1f).padding(start = 8.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = {
                            val visible = visibleTracks.map { it.id }.toSet()
                            selectedIds = if (visible.isNotEmpty() && visible.all { it in selectedIds }) selectedIds - visible else selectedIds + visible
                            selectionMode = selectedIds.isNotEmpty()
                        }) { Text(if (visibleTracks.isNotEmpty() && visibleTracks.all { it.id in selectedIds }) "Deselect visible" else "Select visible") }
                        OutlinedButton(onClick = ::openBatchEditor, enabled = !saving, shape = CircleShape) {
                            SoundprintIcon(SoundprintIcons.Pencil, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Batch edit")
                        }
                    }
                }
            }
            if (status.isNotBlank()) Text(status, Modifier.padding(start = 3.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
            if (query.isNotBlank()) Text("${visibleTracks.size} matches", Modifier.padding(start = 3.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (recycleOpen) {
            Surface(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Recycle Bin", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text("Items are kept for 30 days. Original audio files are never deleted.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (recycleItems.isNotEmpty()) TextButton(onClick = { confirmEmptyBin = true }) { Text("Empty bin", color = MaterialTheme.colorScheme.error) }
                    }
                    if (recycleItems.isEmpty()) Text("Your Recycle Bin is empty.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else recycleItems.forEach { entry ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.track.title, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${entry.track.artist} · ${entry.reason} · ${batchDaysRemaining(entry.removedAt)} days left", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                            }
                            TextButton(onClick = { scope.launch { withContext(Dispatchers.IO) { library.restoreFromRecycleBin(entry.track.id) }; refreshLibrary() } }) {
                                SoundprintIcon(SoundprintIcons.ArchiveRestore, null, Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Restore", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        if (loading) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { androidx.compose.material3.CircularProgressIndicator() }
        } else if (visibleTracks.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 28.dp, vertical = 34.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SoundprintIcon(SoundprintIcons.Music2, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                Text(if (query.isBlank()) "No music found" else "No matches", fontWeight = FontWeight.SemiBold)
                Text(if (query.isBlank()) "Add music to your device to see your songs here." else "Try another title, artist or album.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                items(visibleTracks, key = { it.id }) { track ->
                    val selected = track.id in selectedIds
                    var menuExpanded by remember(track.id) { mutableStateOf(false) }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else androidx.compose.ui.graphics.Color.Transparent)
                            .combinedClickable(
                                onClick = { if (selectionMode) toggleSelection(track.id) else playback.playTracks(visibleTracks, visibleTracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)) },
                                onLongClick = { if (!selectionMode) selectionMode = true; toggleSelection(track.id) }
                            ).padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectionMode) Checkbox(checked = selected, onCheckedChange = { toggleSelection(track.id) })
                        if (SoundprintProGate.isResolved && SoundprintProGate.hasPro) {
                            NativeArtwork(track.artworkUri, track.id, Modifier.size(48.dp), 10)
                        } else {
                            Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                                SoundprintIcon(SoundprintIcons.Music2, "No artwork", Modifier.size(24.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
                            Text(track.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(track.artist.ifBlank { "Unknown artist" } + if (track.album.isNotBlank() && track.album != "Unknown album") " · ${track.album}" else "", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (track.isFavorite && !selectionMode) {
                            SoundprintIcon(SoundprintIcons.Heart, null, Modifier.size(14.dp), MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                        }
                        if (!selectionMode) {
                            Text(formatBatchDuration(track.durationMs), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Box {
                                androidx.compose.material3.IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(36.dp)) {
                                    SoundprintIcon(SoundprintIcons.EllipsisVertical, "Song options", Modifier.size(19.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                    DropdownMenuItem(text = { Text("Play next") }, leadingIcon = { SoundprintIcon(SoundprintIcons.Play, null) }, onClick = { menuExpanded = false; playback.enqueue(listOf(track), next = true) })
                                    DropdownMenuItem(text = { Text("Add to queue") }, leadingIcon = { SoundprintIcon(SoundprintIcons.ListMusic, null) }, onClick = { menuExpanded = false; playback.enqueue(listOf(track)) })
                                    DropdownMenuItem(text = { Text(if (track.isFavorite) "Remove from favorites" else "Add to favorites") }, leadingIcon = { SoundprintIcon(SoundprintIcons.Heart, null) }, onClick = {
                                        menuExpanded = false
                                        scope.launch { withContext(Dispatchers.IO) { library.toggleFavorite(track.id) }; refreshLibrary() }
                                    })
                                    DropdownMenuItem(text = { Text("Move to Recycle Bin") }, leadingIcon = { SoundprintIcon(SoundprintIcons.Trash, null) }, onClick = {
                                        menuExpanded = false
                                        playback.removeTrackFromQueue(track.id)
                                        scope.launch { withContext(Dispatchers.IO) { library.moveToRecycleBin(track.id, "Removed from song menu") }; refreshLibrary() }
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
                        label = { Text("Title") }, placeholder = { Text("Leave unchanged") }, singleLine = true,
                            colors = soundprintOutlinedFieldColors()
                        )
                    OutlinedTextField(artist, { artist = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Artist") }, placeholder = { Text("Leave unchanged") }, singleLine = true,
                            colors = soundprintOutlinedFieldColors()
                        )
                    OutlinedTextField(album, { album = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Album") }, placeholder = { Text("Leave unchanged") }, singleLine = true,
                            colors = soundprintOutlinedFieldColors()
                        )
                    OutlinedTextField(genre, { genre = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Genre") }, placeholder = { Text("Leave unchanged") }, singleLine = true,
                            colors = soundprintOutlinedFieldColors()
                        )
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

    if (confirmEmptyBin) {
        AlertDialog(
            onDismissRequest = { confirmEmptyBin = false },
            title = { Text("Empty Recycle Bin?") },
            text = { Text("This permanently removes the selected tracks from Soundprint's library and history. Original audio files on your device are not deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmEmptyBin = false
                    scope.launch {
                        withContext(Dispatchers.IO) { library.emptyRecycleBin() }
                        refreshLibrary()
                    }
                }) { Text("Empty bin", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmEmptyBin = false }) { Text("Cancel") } }
        )
    }
}

private fun formatBatchDuration(durationMs: Long): String {
    val seconds = (durationMs / 1000L).coerceAtLeast(0L)
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

private fun batchDaysRemaining(removedAt: Long): Int =
    maxOf(0, 30 - ((System.currentTimeMillis() - removedAt) / 86_400_000L).toInt())
