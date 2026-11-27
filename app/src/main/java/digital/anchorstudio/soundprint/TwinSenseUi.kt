package digital.anchorstudio.soundprint

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import java.text.DateFormat
import java.util.Date
import kotlin.math.max

@Composable
internal fun TwinSenseScreen(
    library: MusicLibraryManager,
    onBack: () -> Unit,
    initialRecycleBinOpen: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scanner = remember(context, library) { TwinSenseScanner(context.applicationContext, library) }

    var songs by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var recycleItems by remember { mutableStateOf<List<RecycleBinEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var recycleOpen by remember(initialRecycleBinOpen) { mutableStateOf(initialRecycleBinOpen) }
    var scanResult by remember { mutableStateOf<TwinSenseScanResult?>(null) }
    var scanProgress by remember { mutableStateOf(TwinSenseScanProgress(0, 0, "", 0, 0)) }
    var scanning by remember { mutableStateOf(false) }
    var advancedScanning by remember { mutableStateOf(false) }
    var cleaning by remember { mutableStateOf(false) }
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

    LaunchedEffect(advancedScanning, SoundprintProGate.isResolved, SoundprintProGate.hasPro) {
        if (advancedScanning && SoundprintProGate.isResolved && !SoundprintProGate.hasPro) scanJob?.cancel()
    }

    LaunchedEffect(library) {
        runCatching { refreshLibrary() }
            .onFailure { scanError = it.message ?: "Could not load your music library." }
        scanResult = scanner.loadSavedResults()
        loading = false
    }

    val cleanupTracks = remember(scanResult) {
        scanResult?.let { TwinSenseCleanupPlanner.tracksToRemove(it) }.orEmpty()
    }
    val matches = remember(scanResult) { scanResult?.groups?.flatMap { it.matches }.orEmpty() }

    fun startTwinSenseScan(advanced: Boolean) {
        if (scanning || songs.size < 2) return
        if (advanced && !SoundprintProGate.require("TwinSense Advanced Scan")) return
        scanError = ""
        scanning = true
        advancedScanning = advanced
        scanProgress = TwinSenseScanProgress(0, songs.size, "", 0, 0)
        scanJob = scope.launch {
            try {
                scanResult = scanner.scan(advanced = advanced) { scanProgress = it }
                val failed = scanResult?.failed ?: 0
                if (failed > 0) {
                    scanError = "$failed tracks could not be read or analyzed. Available tracks were still compared."
                }
                refreshLibrary()
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
        TwinSensePageHeader(
            title = if (recycleOpen) "Recycle Bin" else "TwinSense",
            subtitle = if (recycleOpen) "${recycleItems.size} songs · kept for 30 days" else "Duplicate audio finder",
            onBack = onBack,
            actionLabel = if (recycleOpen) "Scan" else "Recycle Bin",
            actionIcon = if (recycleOpen) SoundprintIcons.AudioWaveform else SoundprintIcons.Trash,
            onAction = {
                recycleOpen = !recycleOpen
                scanError = ""
                if (recycleOpen) {
                    scope.launch { runCatching { refreshLibrary() }.onFailure { scanError = it.message ?: "Could not load Recycle Bin." } }
                } else {
                    scope.launch { runCatching { refreshLibrary() }.onFailure { scanError = it.message ?: "Could not load your library." } }
                }
            }
        )

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (recycleOpen) {
            RecycleBinContent(
                items = recycleItems,
                error = scanError,
                onRestore = { id ->
                    scope.launch {
                        val restored = runCatching {
                            withContext(Dispatchers.IO) { library.restoreFromRecycleBin(id) }
                        }
                        if (restored.isFailure) scanError = restored.exceptionOrNull()?.message ?: "Could not restore this track."
                        refreshLibrary()
                    }
                },
                onEmpty = { confirmEmptyBin = true },
                onBrowseSongs = onBack
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                item {
                    TwinSenseScannerSection(
                        songsCount = songs.size,
                        scanning = scanning,
                        advancedScanning = advancedScanning,
                        progress = scanProgress,
                        scanError = scanError,
                        result = scanResult,
                        matches = matches,
                        cleanupCount = cleanupTracks.size,
                        cleaning = cleaning,
                        onBasicScan = { startTwinSenseScan(advanced = false) },
                        onAdvancedScan = { startTwinSenseScan(advanced = true) },
                        onSmartCleanup = { confirmSmartCleanup = true },
                        onRemove = { pendingRemoval = it }
                    )
                }
            }
        }
    }

    val removalTarget = pendingRemoval
    if (removalTarget != null) {
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("Move this track to Recycle Bin?") },
            text = {
                Text("${removalTarget.title} will leave your library but can be restored for 30 days. The original audio file will remain untouched.")
            },
            confirmButton = {
                TextButton(onClick = {
                    val track = removalTarget
                    pendingRemoval = null
                    scope.launch {
                        val moved = runCatching {
                            withContext(Dispatchers.IO) { library.moveToRecycleBin(track.id, "TwinSense duplicate review") }
                        }
                        if (moved.isSuccess) {
                            scanResult?.let { previous ->
                                val updated = scanner.withoutTracks(previous, setOf(track.id))
                                scanResult = updated
                                scanner.saveResult(updated)
                            }
                        } else {
                            scanError = moved.exceptionOrNull()?.message ?: "The track could not be moved. It remains in your library."
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
            text = {
                Text(
                    "Move ${cleanupTracks.size} likely duplicate tracks to Recycle Bin? TwinSense keeps the best-quality original. It can include byte-identical copies and copy-named tracks only when the base title, artist and duration support the match. Possible alternatives and duration-mismatched versions are never auto-removed. The original audio files on your device are never deleted."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmSmartCleanup = false
                    if (cleaning) return@TextButton
                    cleaning = true
                    scope.launch {
                        val moved = mutableSetOf<String>()
                        val failed = mutableSetOf<String>()
                        withContext(Dispatchers.IO) {
                            cleanupTracks.forEach { track ->
                                runCatching { library.moveToRecycleBin(track.id, "TwinSense Smart Cleanup") }
                                    .onSuccess { moved += track.id }
                                    .onFailure { failed += track.id }
                            }
                        }
                        scanResult?.let { previous ->
                            val updated = scanner.withoutTracks(previous, moved)
                            scanResult = updated
                            scanner.saveResult(updated)
                        }
                        if (failed.isNotEmpty()) {
                            scanError = "${failed.size} duplicate tracks could not be moved and remain in your library."
                        }
                        cleaning = false
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
            text = {
                Text("This permanently removes the selected tracks from Soundprint's library and history. Original audio files on your device are not deleted.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmEmptyBin = false
                    scope.launch {
                        val emptied = runCatching { withContext(Dispatchers.IO) { library.emptyRecycleBin() } }
                        if (emptied.isFailure) scanError = emptied.exceptionOrNull()?.message ?: "Could not empty Recycle Bin."
                        refreshLibrary()
                    }
                }) { Text("Empty bin", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmEmptyBin = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun TwinSenseScannerSection(
    songsCount: Int,
    scanning: Boolean,
    advancedScanning: Boolean,
    progress: TwinSenseScanProgress,
    scanError: String,
    result: TwinSenseScanResult?,
    matches: List<TwinSenseMatch>,
    cleanupCount: Int,
    cleaning: Boolean,
    onBasicScan: () -> Unit,
    onAdvancedScan: () -> Unit,
    onSmartCleanup: () -> Unit,
    onRemove: (MusicTrack) -> Unit
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f), RoundedCornerShape(22.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.11f)),
                    contentAlignment = Alignment.Center
                ) {
                    SoundprintIcon(SoundprintIcons.AudioWaveform, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("TwinSense", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Find duplicates and alternate versions", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (result != null && !scanning) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(matches.size.toString(), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        Text(if (matches.size == 1) "MATCH" else "MATCHES", fontSize = 9.sp, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text(
                "Choose how deeply to compare your music. Both scans run locally on this device.",
                fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SoundprintIcon(SoundprintIcons.CopyCheck, null, Modifier.size(16.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Basic", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Text("Quick file-signature comparison", fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, minLines = 2)
                    Button(
                        onClick = onBasicScan, enabled = !scanning && songsCount >= 2,
                        modifier = Modifier.fillMaxWidth().height(40.dp), shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        if (scanning && !advancedScanning) CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                        else SoundprintIcon(SoundprintIcons.ScanLine, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (scanning && !advancedScanning) "Scanning ${progress.percent}%" else if (result != null) "Run again" else "Start scan", fontSize = 11.sp, maxLines = 1)
                    }
                }
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SoundprintIcon(SoundprintIcons.AudioWaveform, null, Modifier.size(16.dp), MaterialTheme.colorScheme.primary)
                        Text("Advanced", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        if (!SoundprintProGate.hasPro || !SoundprintProGate.isResolved) {
                            Text("PRO", fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Text("Audio analysis for deeper matches", fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, minLines = 2)
                    OutlinedButton(
                        onClick = onAdvancedScan, enabled = !scanning && songsCount >= 2,
                        modifier = Modifier.fillMaxWidth().height(40.dp), shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        if (scanning && advancedScanning) CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                        else SoundprintIcon(SoundprintIcons.AudioWaveform, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text(if (scanning && advancedScanning) "Analysing ${progress.percent}%" else "Advanced scan", fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
            if (songsCount < 2 && !scanning) Text("Add at least two tracks to start a scan.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (scanning) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(progress = { progress.percent / 100f }, modifier = Modifier.fillMaxWidth().height(4.dp))
                    Text("Checking music stored on this device. Audio files are never uploaded.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (scanError.isNotBlank()) Text(scanError, Modifier.fillMaxWidth(), fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            if (result != null && !scanning && matches.isEmpty()) Text("No duplicate tracks found.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (result != null && !scanning && matches.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        val duplicateCount = matches.count { it.kind != TwinSenseMatchKind.ALTERNATIVE && !it.versionWarning }
                        val alternativeCount = matches.size - duplicateCount
                        Text("TwinSense findings", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("$duplicateCount possible duplicates · $alternativeCount possible alternatives", Modifier.padding(top = 2.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (cleanupCount > 0) {
                        Button(
                            onClick = onSmartCleanup,
                            enabled = !cleaning && !scanning,
                            modifier = Modifier.height(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                        ) {
                            SoundprintIcon(SoundprintIcons.Trash2, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(if (cleaning) "Cleaning…" else "Smart Cleanup · $cleanupCount", fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            matches.forEach { match ->
                TwinSenseMatchRow(match = match, onRemove = onRemove)
            }
        } else if (result == null && !scanning) {
            Text(
                "Compare the audio in your library to find exact copies and possible matches.",
                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TwinSenseMatchRow(match: TwinSenseMatch, onRemove: (MusicTrack) -> Unit) {
    var reasonsExpanded by remember(match.first.id, match.second.id) { mutableStateOf(true) }
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                when {
                    match.kind == TwinSenseMatchKind.EXACT_AUDIO -> "Exact audio match"
                    match.kind == TwinSenseMatchKind.ALTERNATIVE || match.versionWarning -> "Possible alternative"
                    else -> "Possible duplicate"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (match.kind == TwinSenseMatchKind.EXACT_AUDIO) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("${match.confidence}% confidence", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (match.kind == TwinSenseMatchKind.ALTERNATIVE || match.versionWarning) Text("Keep both · review version", fontSize = 11.sp, color = MaterialTheme.colorScheme.tertiary)
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TwinSenseComparedTrack(track = match.first, index = 0, onRemove = onRemove)
            TwinSenseComparedTrack(track = match.second, index = 1, onRemove = onRemove)
        }
        if (match.reasons.isNotEmpty()) {
            Text(
                if (reasonsExpanded) "Why TwinSense reached this conclusion · Hide" else "Why TwinSense reached this conclusion",
                Modifier.padding(top = 12.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(horizontal = 8.dp, vertical = 5.dp)
                    .clickable { reasonsExpanded = !reasonsExpanded },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (reasonsExpanded) {
                Column(Modifier.padding(start = 16.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    match.reasons.forEach { reason ->
                        Text("• $reason", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun TwinSenseComparedTrack(track: MusicTrack, index: Int, onRemove: (MusicTrack) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                SoundprintIcon(SoundprintIcons.Music2, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(track.title.ifBlank { "Unknown title" }, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist.ifBlank { "Unknown artist" } + if (track.album.isNotBlank()) " · ${track.album}" else "", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(
            Modifier.padding(start = 48.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(formatTwinSenseDuration(track.durationMs / 1000.0), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (track.sizeBytes > 0L) "${"%.1f".format(track.sizeBytes / (1024.0 * 1024.0))} MB" else "Size unknown", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (track.bitrate > 0) Text("${track.bitrate / 1000} kbps", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (track.sampleRate > 0) Text("${track.sampleRate} Hz", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (track.channelCount > 0) Text("${track.channelCount} ch", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val codec = track.encoding.ifBlank { track.mimeType }
            if (codec.isNotBlank()) Text(codec, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(
            onClick = { onRemove(track) },
            modifier = Modifier.padding(start = 36.dp, top = 4.dp).height(32.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
        ) {
            SoundprintIcon(SoundprintIcons.Trash2, null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("Remove ${if (index == 0) "first" else "second"} track", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun RecycleBinContent(
    items: List<RecycleBinEntry>,
    error: String,
    onRestore: (String) -> Unit,
    onEmpty: () -> Unit,
    onBrowseSongs: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    SoundprintIcon(SoundprintIcons.Trash2, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("Your deleted songs", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Restore songs back to your library anytime within 30 days. The original files on your device are never deleted.",
                        Modifier.padding(top = 4.dp),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (items.isNotEmpty()) {
                    TextButton(onClick = onEmpty, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)) {
                        Text("Empty bin", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        if (error.isNotBlank()) {
            item { Text(error, Modifier.padding(horizontal = 16.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
        }
        if (items.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 24.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(16.dp))
                        .padding(horizontal = 24.dp, vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SoundprintIcon(SoundprintIcons.Trash2, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(32.dp))
                    Text("Recycle Bin is empty", Modifier.padding(top = 12.dp), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("Songs you remove from your library will show up here.", Modifier.padding(top = 4.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = onBrowseSongs, modifier = Modifier.padding(top = 16.dp), shape = RoundedCornerShape(50)) {
                        Text("Browse songs", fontSize = 12.sp)
                    }
                }
            }
        } else {
            items(items, key = { it.track.id }) { entry ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    NativeArtwork(entry.track.artworkUri, entry.track.id, Modifier.size(48.dp), 9)
                    Column(Modifier.weight(1f)) {
                        Text(entry.track.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${entry.track.artist} · ${formatTwinSenseDuration(entry.track.durationMs / 1000.0)}",
                            Modifier.padding(top = 2.dp),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${entry.reason} · ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(entry.removedAt))}",
                            Modifier.padding(top = 4.dp),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(onClick = { onRestore(entry.track.id) }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)) {
                        SoundprintIcon(SoundprintIcons.RotateCcw, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Restore", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun TwinSensePageHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    actionLabel: String,
    actionIcon: Int,
    onAction: () -> Unit
) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                SoundprintIcon(SoundprintIcons.ArrowLeft, "Back", modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                SoundprintIcon(actionIcon, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text(actionLabel, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

private fun daysRemaining(removedAt: Long): Int =
    max(0, 30 - ((System.currentTimeMillis() - removedAt) / 86_400_000L).toInt())
