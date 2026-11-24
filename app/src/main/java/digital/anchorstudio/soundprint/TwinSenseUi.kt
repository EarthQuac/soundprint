package digital.anchorstudio.soundprint

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

@Composable
internal fun SongsLibraryScreen(
    library: MusicLibraryManager,
    playback: SoundprintPlaybackConnection,
    onImportFiles: () -> Unit,
    onBack: () -> Unit,
    initialTwinSenseOpen: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scanner = remember(context, library) { TwinSenseScanner(context.applicationContext, library) }
    var songs by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var recycleItems by remember { mutableStateOf<List<RecycleBinEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf("Title") }
    var descending by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var soundprintOpen by remember { mutableStateOf(initialTwinSenseOpen) }
    var recycleOpen by remember { mutableStateOf(false) }
    var scanResult by remember { mutableStateOf<TwinSenseScanResult?>(null) }
    var scanProgress by remember { mutableStateOf(TwinSenseScanProgress(0, 0, "", 0, 0)) }
    var scanning by remember { mutableStateOf(false) }
    var advancedScanning by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf("") }
    var scanJob by remember { mutableStateOf<Job?>(null) }
    var pendingRemoval by remember { mutableStateOf<MusicTrack?>(null) }
    var confirmSmartCleanup by remember { mutableStateOf(false) }
    var confirmEmptyBin by remember { mutableStateOf(false) }

    suspend fun refreshLibrary() {
        val loaded = withContext(Dispatchers.IO) {
            library.allSongs() to library.recycleBin()
        }
        songs = loaded.first
        recycleItems = loaded.second
    }

    LaunchedEffect(SoundprintProGate.isResolved, SoundprintProGate.hasPro) {
        // Basic Scan is free and must keep running. Only stop an Advanced Scan
        // if the verified Pro entitlement is lost or becomes unresolved.
        if ((!SoundprintProGate.isResolved || !SoundprintProGate.hasPro) && advancedScanning) {
            scanJob?.cancel()
        }
    }

    LaunchedEffect(library) {
        runCatching { refreshLibrary() }
        scanResult = scanner.loadSavedResults()
        loading = false
    }

    val filteredSongs = remember(songs, query, sort, descending) {
        val needle = query.trim()
        val list = songs.filter { needle.isBlank() || listOf(it.title, it.artist, it.album, it.genre, it.folder).any { value -> value.contains(needle, true) } }
        val sorted = when (sort) {
            "Artist" -> list.sortedWith(compareBy<MusicTrack> { it.artist.lowercase() }.thenBy { it.title.lowercase() })
            "Album" -> list.sortedWith(compareBy<MusicTrack> { it.album.lowercase() }.thenBy { it.trackNumber })
            "Date added" -> list.sortedBy { it.addedAt }
            "Duration" -> list.sortedBy { it.durationMs }
            else -> list.sortedBy { it.title.lowercase() }
        }
        if (descending) sorted.reversed() else sorted
    }

    val cleanupTracks = remember(scanResult) {
        scanResult?.let { TwinSenseCleanupPlanner.tracksToRemove(it) }.orEmpty()
    }

    val hasTwinSensePro = SoundprintProGate.isResolved && SoundprintProGate.hasPro

    fun startTwinSenseScan(advanced: Boolean) {
        if (scanning) return
        if (advanced && !SoundprintProGate.require("TwinSense Advanced Scan")) return
        scanError = ""
        scanning = true
        advancedScanning = advanced
        scanProgress = TwinSenseScanProgress(0, songs.size, "", 0, 0)
        scanJob = scope.launch {
            try {
                scanResult = scanner.scan(advanced = advanced) { scanProgress = it }
                if ((scanResult?.failed ?: 0) > 0) {
                    scanError = "${scanResult?.failed} tracks could not be decoded. Available tracks were still compared."
                }
            } catch (cancel: CancellationException) {
                scanError = "Scan cancelled. Completed fingerprints remain saved for the next scan."
            } catch (error: Exception) {
                scanError = error.message ?: "TwinSense could not complete this scan."
            } finally {
                scanning = false
                advancedScanning = false
                scanJob = null
            }
        }
    }


    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
                    SoundprintIcon(SoundprintIcons.ArrowLeft, contentDescription = "Back")
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (initialTwinSenseOpen) "TwinSense" else "Songs", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                    Text(if (initialTwinSenseOpen) "${songs.size} tracks to compare" else "${songs.size} songs", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Button(
                onClick = onImportFiles,
                modifier = Modifier.padding(start = 12.dp, top = 10.dp).height(42.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                SoundprintIcon(SoundprintIcons.FolderPlus, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add music", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Filter songs, artists, albums...", fontSize = 14.sp) },
                        leadingIcon = { SoundprintIcon(SoundprintIcons.Search, null, modifier = Modifier.size(20.dp)) },
                        shape = RoundedCornerShape(24.dp),
                        colors = soundprintOutlinedFieldColors()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { sortMenu = true },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                SoundprintIcon(SoundprintIcons.ArrowDownUp, null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Sort: $sort", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                            }
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                listOf("Title", "Artist", "Album", "Date added", "Duration").forEach { option ->
                                    DropdownMenuItem(text = { Text(option) }, onClick = { sort = option; sortMenu = false })
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = { descending = !descending },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            SoundprintIcon(SoundprintIcons.ArrowDownUp, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (descending) "Descending" else "Ascending", fontSize = 14.sp, maxLines = 1)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { soundprintOpen = !soundprintOpen; if (soundprintOpen) recycleOpen = false },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            SoundprintIcon(SoundprintIcons.CopyCheck, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("TwinSense", fontSize = 14.sp, maxLines = 1)
                            ProFeatureBadge()
                        }
                        Button(
                            onClick = {
                                recycleOpen = !recycleOpen
                                if (recycleOpen) soundprintOpen = false
                                scope.launch { recycleItems = withContext(Dispatchers.IO) { library.recycleBin() } }
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            SoundprintIcon(SoundprintIcons.Trash, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Recycle Bin", fontSize = 14.sp, maxLines = 1)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { if (filteredSongs.isNotEmpty()) playback.playTracks(filteredSongs) },
                            enabled = filteredSongs.isNotEmpty(),
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(28.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            SoundprintIcon(SoundprintIcons.Play, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Play all", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = { if (filteredSongs.isNotEmpty()) playback.playTracks(filteredSongs, shuffle = true) },
                            enabled = filteredSongs.isNotEmpty(),
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(28.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            SoundprintIcon(SoundprintIcons.Shuffle, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Shuffle", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (query.isNotBlank()) Text("${filteredSongs.size} matches", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (soundprintOpen) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Spacer(Modifier.height(0.dp))
                        Column(Modifier.padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SoundprintIcon(SoundprintIcons.ShieldCheck, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                        Text("TwinSense", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                        ProFeatureBadge()
                                    }
                                    Text("Compare the actual audio stored on this device.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (!hasTwinSensePro) {
                                    Button(
                                        onClick = { startTwinSenseScan(advanced = false) },
                                        enabled = !scanning && songs.size >= 2,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (scanning && !advancedScanning) {
                                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                        } else {
                                            SoundprintIcon(SoundprintIcons.AudioWaveform, null, modifier = Modifier.size(16.dp))
                                        }
                                        Spacer(Modifier.width(6.dp))
                                        Text(if (scanning && !advancedScanning) "Scanning…" else "Basic Scan", maxLines = 1, fontSize = 12.sp)
                                    }
                                }
                                ProFeatureGate(Modifier.weight(1f)) {
                                OutlinedButton(
                                    onClick = {
                                        if (hasTwinSensePro) startTwinSenseScan(advanced = true)
                                        else SoundprintProGate.require("TwinSense Advanced Scan")
                                    },
                                    enabled = !scanning && songs.size >= 2,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (scanning && advancedScanning) {
                                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    } else {
                                        SoundprintIcon(SoundprintIcons.Zap, null, modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        if (scanning && advancedScanning) "Scanning…" else "Advanced Scan",
                                        maxLines = 1,
                                        fontSize = 12.sp
                                    )
                                    if (!hasTwinSensePro) ProFeatureBadge()
                                }
                                }
                            }
                            if (scanning) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { scanJob?.cancel() }) { Text("Cancel", fontSize = 11.sp) }
                                }
                            }
                            if (scanning) {
                                LinearProgressIndicator(progress = { scanProgress.percent / 100f }, modifier = Modifier.fillMaxWidth())
                                Text(
                                    "${if (advancedScanning) "Advanced" else "Basic"} · ${scanProgress.percent}% · ${scanProgress.completed}/${scanProgress.total} · ${scanProgress.currentTitle}",
                                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                            val finishedScan = scanResult
                            if (finishedScan != null && !scanning) {
                                val result = finishedScan
                                Text(
                                    "${result.tracksScanned} tracks scanned · ${result.fingerprintsReused} cached fingerprints reused · ${result.groups.size} duplicate groups · ${result.matchCount} matching pairs",
                                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (cleanupTracks.isNotEmpty()) {
                                    ProFeatureGate {
                                        OutlinedButton(onClick = { if (SoundprintProGate.require("TwinSense")) confirmSmartCleanup = true }) {
                                            SoundprintIcon(SoundprintIcons.Trash2, null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(7.dp))
                                            Text("Smart Cleanup · ${cleanupTracks.size}")
                                        }
                                    }
                                }
                                if (result.groups.isEmpty()) {
                                    Text("No duplicate audio found in your library.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    result.groups.forEachIndexed { index, group ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                            shape = RoundedCornerShape(16.dp),
                                            
                                        ) {
                                            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text("Duplicate group ${index + 1}", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                                    Text("${group.tracks.size} tracks", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                group.tracks.forEach { track ->
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Column(Modifier.weight(1f)) {
                                                            Text(track.title, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                            Text("${track.artist} · ${formatTwinSenseDuration(track.durationMs / 1000.0)} · ${track.sizeBytes / (1024 * 1024)} MB",
                                                                fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        }
                                                        TextButton(onClick = { pendingRemoval = track }) { Text("Remove", color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
                                                    }
                                                }
                                                group.matches.forEach { match ->
                                                    val label = when (match.kind) {
                                                        TwinSenseMatchKind.EXACT_AUDIO -> "Exact audio"
                                                        TwinSenseMatchKind.ACOUSTIC -> "Acoustic match"
                                                        TwinSenseMatchKind.METADATA -> "Possible match"
                                                    }
                                                    Text("$label · ${match.confidence}% confidence${if (match.versionWarning) " · possible alternate version" else ""}",
                                                        fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                                    match.reasons.forEach { reason ->
                                                        Text("• $reason", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else if (!scanning) {
                                Text(
                                    if (songs.size < 2) "Add at least two tracks before scanning." else "Basic Scan is free and processes one track at a time. Advanced Scan uses parallel processing for Pro members. Audio never leaves this device.",
                                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (scanError.isNotBlank()) Text(scanError, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(Modifier.height(0.dp))
                    }
                }
            }

            if (recycleOpen) {
                item {
                    Card(
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Recycle Bin", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                    Text("Items are kept for 30 days. Original audio files are never deleted.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (recycleItems.isNotEmpty()) TextButton(onClick = { confirmEmptyBin = true }) {
                                    Text("Empty bin", color = MaterialTheme.colorScheme.error)
                                }
                            }
                            if (recycleItems.isEmpty()) {
                                Text("Your Recycle Bin is empty.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else recycleItems.forEach { entry ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(entry.track.title, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${entry.track.artist} · ${entry.reason} · ${daysRemaining(entry.removedAt)} days left",
                                            fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                                    }
                                    TextButton(onClick = {
                                        scope.launch {
                                            runCatchingSuspend { withContext(Dispatchers.IO) { library.restoreFromRecycleBin(entry.track.id) } }
                                            refreshLibrary()
                                        }
                                    }) {
                                        SoundprintIcon(SoundprintIcons.ArchiveRestore, null, modifier = Modifier.size(15.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Restore", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (loading) {
                item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            } else if (filteredSongs.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 34.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SoundprintIcon(SoundprintIcons.Music2, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                        Text(if (query.isBlank()) "No music found" else "No matches", fontWeight = FontWeight.SemiBold)
                        Text(if (query.isBlank()) "Add music to your device to see your songs here." else "Try another title, artist or album.",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(filteredSongs, key = { it.id }) { track ->
                    NativeSongLibraryRow(
                        track = track,
                        library = library,
                        playback = playback,
                        onPlay = { playback.playTracks(filteredSongs, filteredSongs.indexOfFirst { it.id == track.id }.coerceAtLeast(0)) },
                        onChanged = { scope.launch { refreshLibrary() } }
                    )
                }
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }

    val removalTarget = pendingRemoval
    if (removalTarget != null) {
        val track = removalTarget
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("Move this track to Recycle Bin?") },
            text = { Text("${track.title} will leave your library but can be restored for 30 days. The original audio file will remain untouched.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRemoval = null
                    scope.launch {
                        runCatchingSuspend { withContext(Dispatchers.IO) { library.moveToRecycleBin(track.id, "TwinSense duplicate review") } }
                        scanResult?.let { previous ->
                            val updated = scanner.withoutTracks(previous, setOf(track.id))
                            scanResult = updated
                            scanner.saveResult(updated)
                        }
                        refreshLibrary()
                    }
                }) { Text("Move to Recycle Bin", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingRemoval = null }) { Text("Cancel") } }
        )
    }

    if (confirmSmartCleanup) {
        AlertDialog(
            onDismissRequest = { confirmSmartCleanup = false },
            title = { Text("Run Smart Cleanup?") },
            text = { Text("Move ${cleanupTracks.size} verified duplicate copies to Recycle Bin? The best copy in each exact-audio group will be kept. Similar matches and alternate versions are never auto-removed.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSmartCleanup = false
                    scope.launch {
                        val moved = mutableSetOf<String>()
                        withContext(Dispatchers.IO) {
                            cleanupTracks.forEach { track ->
                                runCatching { library.moveToRecycleBin(track.id, "TwinSense Smart Cleanup") }
                                    .onSuccess { moved += track.id }
                            }
                        }
                        scanResult?.let { previous ->
                            val updated = scanner.withoutTracks(previous, moved)
                            scanResult = updated
                            scanner.saveResult(updated)
                        }
                        refreshLibrary()
                    }
                }) { Text("Clean up duplicates", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmSmartCleanup = false }) { Text("Cancel") } }
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
                        recycleItems = emptyList()
                        refreshLibrary()
                    }
                }) { Text("Empty bin", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmEmptyBin = false }) { Text("Cancel") } }
        )
    }
}


@Composable
private fun NativeSongLibraryRow(
    track: MusicTrack,
    library: MusicLibraryManager,
    playback: SoundprintPlaybackConnection,
    onPlay: () -> Unit,
    onChanged: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var editOpen by remember { mutableStateOf(false) }
    var playlistsOpen by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var artistOpen by remember { mutableStateOf(false) }
    var title by remember(track.id) { mutableStateOf(track.title) }
    var artist by remember(track.id) { mutableStateOf(track.artist) }
    var album by remember(track.id) { mutableStateOf(track.album) }
    var genre by remember(track.id) { mutableStateOf(track.genre) }
    val playlists = remember(playlistsOpen) { if (playlistsOpen) library.playlists() else emptyList() }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(12.dp)).clickable(onClick = onPlay)
            .padding(horizontal = 2.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (SoundprintProGate.isResolved && SoundprintProGate.hasPro) {
            NativeArtwork(track.artworkUri, track.id, Modifier.size(54.dp), 12)
        } else {
            Box(
                Modifier.size(54.dp).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                SoundprintIcon(
                    SoundprintIcons.Music2,
                    contentDescription = "No artwork",
                    modifier = Modifier.size(26.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist.ifBlank { "Unknown artist" }, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(formatTwinSenseDuration(track.durationMs / 1000.0), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            androidx.compose.material3.IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(36.dp)) {
                SoundprintIcon(SoundprintIcons.EllipsisVertical, contentDescription = "Song options", modifier = Modifier.size(19.dp))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Play next") }, leadingIcon = { SoundprintIcon(SoundprintIcons.Play, null) }, onClick = { menuOpen = false; playback.enqueue(listOf(track), next = true) })
                DropdownMenuItem(text = { Text("Add to queue") }, leadingIcon = { SoundprintIcon(SoundprintIcons.ListMusic, null) }, onClick = { menuOpen = false; playback.enqueue(listOf(track)) })
                DropdownMenuItem(text = { Text(if (track.isFavorite) "Remove from favourites" else "Add to favourites") }, leadingIcon = { SoundprintIcon(SoundprintIcons.Heart, null) }, onClick = {
                    menuOpen = false
                    scope.launch { withContext(Dispatchers.IO) { library.toggleFavorite(track.id) }; onChanged() }
                })
                DropdownMenuItem(text = { Text("Add to playlist") }, leadingIcon = { SoundprintIcon(SoundprintIcons.ListPlus, null) }, onClick = { menuOpen = false; playlistsOpen = true })
                DropdownMenuItem(text = { Text("Edit song info") }, leadingIcon = { SoundprintIcon(SoundprintIcons.Pencil, null) }, onClick = { menuOpen = false; editOpen = true })
                DropdownMenuItem(text = { Text("Go to artist") }, leadingIcon = { SoundprintIcon(SoundprintIcons.Music2, null) }, onClick = { menuOpen = false; artistOpen = true })
                DropdownMenuItem(text = { Text("Move to Recycle Bin") }, leadingIcon = { SoundprintIcon(SoundprintIcons.Trash2, null) }, onClick = {
                    menuOpen = false
                    playback.removeTrackFromQueue(track.id)
                    scope.launch { withContext(Dispatchers.IO) { library.moveToRecycleBin(track.id, "Removed from song menu") }; onChanged() }
                })
            }
        }
    }

    if (editOpen) {
        AlertDialog(
            onDismissRequest = { editOpen = false },
            title = { Text("Edit song info") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true,
                        colors = soundprintOutlinedFieldColors()
                    )
                    OutlinedTextField(value = artist, onValueChange = { artist = it }, label = { Text("Artist") }, singleLine = true,
                        colors = soundprintOutlinedFieldColors()
                    )
                    OutlinedTextField(value = album, onValueChange = { album = it }, label = { Text("Album") }, singleLine = true,
                        colors = soundprintOutlinedFieldColors()
                    )
                    OutlinedTextField(value = genre, onValueChange = { genre = it }, label = { Text("Genre") }, singleLine = true,
                        colors = soundprintOutlinedFieldColors()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    editOpen = false
                    scope.launch {
                        withContext(Dispatchers.IO) { library.updateSongMetadata(track.id, title, artist, album, genre) }
                        onChanged()
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editOpen = false }) { Text("Cancel") } }
        )
    }

    if (artistOpen) {
        val artistTracks = remember(track.artist) { library.artistTracks(track.artist) }
        AlertDialog(
            onDismissRequest = { artistOpen = false },
            title = { Text(track.artist) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${artistTracks.size} songs", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    artistTracks.take(12).forEachIndexed { index, artistTrack ->
                        TextButton(onClick = { artistOpen = false; playback.playTracks(artistTracks, index) }) {
                            Text(artistTrack.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { artistOpen = false }) { Text("Close") } }
        )
    }

    if (playlistsOpen) {
        AlertDialog(
            onDismissRequest = { playlistsOpen = false },
            title = { Text("Add to playlist") },
            text = {
                if (playlists.isEmpty()) Text("You haven't created a playlist yet.")
                else Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    playlists.forEach { playlist ->
                        TextButton(onClick = {
                            playlistsOpen = false
                            scope.launch { withContext(Dispatchers.IO) { library.addToPlaylist(playlist.id, listOf(track.id)) }; onChanged() }
                        }) { Text(playlist.name) }
                    }
                }
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("New playlist name") },
                    singleLine = true
                ,
                    colors = soundprintOutlinedFieldColors()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newPlaylistName.isNotBlank(),
                    onClick = {
                        val name = newPlaylistName.trim()
                        playlistsOpen = false
                        newPlaylistName = ""
                        scope.launch {
                            withContext(Dispatchers.IO) { library.createPlaylist(name, listOf(track.id)) }
                            onChanged()
                        }
                    }
                ) { Text("Create playlist") }
            },
            dismissButton = { TextButton(onClick = { playlistsOpen = false }) { Text("Close") } }
        )
    }
}

private fun daysRemaining(removedAt: Long): Int =
    max(0, 30 - ((System.currentTimeMillis() - removedAt) / 86_400_000L).toInt())
