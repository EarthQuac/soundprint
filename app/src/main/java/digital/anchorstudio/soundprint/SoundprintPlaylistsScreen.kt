package digital.anchorstudio.soundprint

import androidx.compose.ui.res.painterResource
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private data class PlaylistPageData(
    val tracks: List<MusicTrack> = emptyList(),
    val playlists: List<ManagedPlaylist> = emptyList(),
    val smartPlaylists: List<NativeSmartPlaylist> = emptyList(),
    val smartResults: Map<String, List<MusicTrack>> = emptyMap(),
    val liked: List<MusicTrack> = emptyList(),
    val recent: List<MusicTrack> = emptyList(),
    val added: List<MusicTrack> = emptyList()
)

private data class PlaylistMixItem(val id: String, val title: String, val tracks: List<MusicTrack>, val icon: Int)

private val smartOperators = listOf(
    SmartOperator.EQ to "is",
    SmartOperator.NEQ to "is not",
    SmartOperator.CONTAINS to "contains",
    SmartOperator.NOT_CONTAINS to "doesn't contain",
    SmartOperator.GT to "greater than",
    SmartOperator.GTE to "at least",
    SmartOperator.LT to "less than",
    SmartOperator.LTE to "at most"
)

private val smartFields = listOf(
    SmartField.PLAY_COUNT to ("Play count" to "number"),
    SmartField.LAST_PLAYED_DAYS_AGO to ("Last played (days ago)" to "number"),
    SmartField.NEVER_PLAYED to ("Never played" to "boolean"),
    SmartField.SKIP_COUNT to ("Skip count" to "number"),
    SmartField.USUAL_HOUR to ("Usual listening hour" to "number"),
    SmartField.DATE_ADDED to ("Date added (days ago)" to "number"),
    SmartField.GENRE to ("Genre" to "text"),
    SmartField.ARTIST to ("Artist" to "text"),
    SmartField.ALBUM to ("Album" to "text"),
    SmartField.FOLDER to ("Folder" to "text"),
    SmartField.YEAR to ("Year" to "number"),
    SmartField.DURATION to ("Duration (seconds)" to "number"),
    SmartField.FAVORITE to ("Favorite" to "boolean"),
    SmartField.HAS_LYRICS to ("Has lyrics" to "boolean"),
    SmartField.MOOD_LABEL to ("Mood" to "text"),
    SmartField.ENERGY to ("Energy (0–100)" to "number"),
    SmartField.TEMPO to ("Tempo (BPM)" to "number")
)

private fun smartFieldLabel(field: SmartField) = smartFields.first { it.first == field }.second.first
private fun smartFieldType(field: SmartField) = smartFields.first { it.first == field }.second.second
private fun operatorLabel(operator: SmartOperator) = smartOperators.first { it.first == operator }.second
private fun freshSmartPlaylist() = NativeSmartPlaylist(
    name = "New smart playlist",
    groups = listOf(SmartConditionGroup(conditions = listOf(SmartCondition(field = SmartField.GENRE, operator = SmartOperator.CONTAINS, value = ""))))
)
private fun freshCondition() = SmartCondition(field = SmartField.GENRE, operator = SmartOperator.CONTAINS, value = "")
private fun freshGroup() = SmartConditionGroup(conditions = listOf(freshCondition()))

@Composable
fun SoundprintPlaylistsScreen(
    libraryManager: MusicLibraryManager,
    playback: SoundprintPlaybackConnection,
    clapScanner: ClapLibraryScanner
) {
    val proAccess = LocalSoundprintProUiAccess.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val scan by clapScanner.state.collectAsState()
    var refreshVersion by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val prefs = SoundprintListeningPreferences.preferences(context)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key in setOf(
                    SoundprintListeningPreferences.KEY_HISTORY_ENABLED,
                    SoundprintListeningPreferences.KEY_PLAYLIST_RECOMMENDATIONS_ENABLED,
                    SoundprintListeningPreferences.KEY_AUTOMATIC_PLAYLISTS_ENABLED
                )
            ) refreshVersion++
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    var data by remember { mutableStateOf<PlaylistPageData?>(null) }
    var query by remember { mutableStateOf("") }
    var createPlaylistOpen by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("") }
    var editorOpen by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf(freshSmartPlaylist()) }
    var selectedPlaylistId by remember { mutableStateOf<String?>(null) }
    var selectedSmartId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(SoundprintProGate.isResolved, SoundprintProGate.hasPro) {
        if (!SoundprintProGate.isResolved || !SoundprintProGate.hasPro) {
            editorOpen = false
            selectedSmartId = null
        }
    }

    LaunchedEffect(Unit) { clapScanner.refreshSummary() }
    LaunchedEffect(scan.completed, scan.failed, scan.running, scan.lastCompletedTrackId, refreshVersion) {
        data = withContext(Dispatchers.IO) {
            val tracks = libraryManager.allSongs()
            val smart = libraryManager.smartPlaylists()
            val results = smart.associate { it.id to libraryManager.evaluateSmartPlaylist(it.id) }
            val recent = if (SoundprintListeningPreferences.historyEnabled(context)) {
                libraryManager.listeningHistory(500).mapNotNull { it.second }.distinctBy { it.id }.take(30)
            } else emptyList()
            PlaylistPageData(
                tracks = tracks,
                playlists = libraryManager.playlists(),
                smartPlaylists = smart,
                smartResults = results,
                liked = tracks.filter { it.isFavorite },
                recent = recent,
                added = tracks.sortedByDescending { it.addedAt }.take(20)
            )
        }
    }

    val current = data
    val openPlaylistId = selectedPlaylistId
    if (openPlaylistId != null) {
        PlaylistDetailNative(
            playlistId = openPlaylistId,
            libraryManager = libraryManager,
            playback = playback,
            onBack = { selectedPlaylistId = null },
            onChanged = { refreshVersion++ }
        )
        return
    }
    val openSmartId = selectedSmartId
    if (openSmartId != null) {
        SmartPlaylistDetailNative(
            smartId = openSmartId,
            libraryManager = libraryManager,
            playback = playback,
            clapScanner = clapScanner,
            onBack = { selectedSmartId = null },
            refreshVersion = refreshVersion
        )
        return
    }

    val playlists = current?.playlists.orEmpty()
    val searchTerm = query.trim()
    val filtered = playlists.filter { it.name.contains(searchTerm, ignoreCase = true) }
    val playlistSongMatches = if (searchTerm.isBlank() || current == null) emptyList() else {
        playlists.mapNotNull { playlist ->
            val playlistTracks = playlist.trackIds.mapNotNull { id -> current.tracks.firstOrNull { it.id == id } }
            val matchingTracks = playlistTracks.filter { track ->
                "${track.title} ${track.artist} ${track.album}".contains(searchTerm, ignoreCase = true)
            }
            if (matchingTracks.isNotEmpty() || playlist.name.contains(searchTerm, ignoreCase = true)) {
                playlist to if (matchingTracks.isNotEmpty()) matchingTracks else playlistTracks
            } else null
        }
    }
    val showPlaylistRecommendations = SoundprintListeningPreferences.playlistRecommendationsEnabled(androidx.compose.ui.platform.LocalContext.current)
    val mixes = current?.let {
        listOf(
            PlaylistMixItem("liked", "Liked Songs", it.liked, SoundprintIcons.Heart),
            PlaylistMixItem("recent", "Recently Played", it.recent, SoundprintIcons.Clock),
            PlaylistMixItem("added", "Recently Added", it.added, SoundprintIcons.ListMusic)
        )
    }.orEmpty()

    Column(Modifier.fillMaxSize()) {
        PlaylistPageHeader(
            title = "Playlists",
            subtitle = "${playlists.size} ${if (playlists.size == 1) "playlist" else "playlists"}",
            action = { createPlaylistOpen = true }
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 14.dp, bottom = if (LocalSoundprintDarkTheme.current) 26.dp else 54.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            PlaylistSearchBox(query, { query = it }, "Search songs, playlists, artists or albums…")
            if (searchTerm.isBlank() && showPlaylistRecommendations) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("SMART MIXES", fontSize = 11.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    mixes.forEach { mix ->
                        Row(
                            Modifier.fillMaxWidth()
                                .then(if (LocalSoundprintDarkTheme.current) Modifier else Modifier.shadow(4.dp, RoundedCornerShape(16.dp), ambientColor = LocalSoundprintThemeTokens.current.shadow, spotColor = LocalSoundprintThemeTokens.current.shadow))
                                .clip(RoundedCornerShape(16.dp)).background(LocalSoundprintThemeTokens.current.card)
                                .then(if (LocalSoundprintDarkTheme.current) Modifier else Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.62f), RoundedCornerShape(16.dp)))
                                .clickable { selectedSmartId = mix.id }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                                SoundprintIcon(mix.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                            Column(Modifier.weight(1f).padding(start = 11.dp)) {
                                Text(mix.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                Text("${mix.tracks.size} songs", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            SoundprintIcon(SoundprintIcons.ChevronRight, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                        }
                    }
                }
            }
            if (searchTerm.isBlank()) {
                val proUi = LocalSoundprintProUiAccess.current
                val hasPro = proUi.entitled
                val darkTheme = LocalSoundprintDarkTheme.current
                val lockedColors = soundprintProLockedTokens(darkTheme)
                Box(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth()
                            .graphicsLayer(alpha = if (hasPro || !darkTheme) 1f else 0.46f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(Modifier.weight(1f, fill = true)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        "Smart playlists",
                                        modifier = Modifier.weight(1f, fill = false).padding(horizontal = 3.dp, vertical = 2.dp),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (!hasPro && !darkTheme) lockedColors.text else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Visible
                                    )
                                    ProFeatureBadge(sparkleSize = if (LocalSoundprintDarkTheme.current) 14.dp else 12.dp)
                                }
                                Text(
                                    if (SoundprintListeningPreferences.automaticPlaylistsEnabled(androidx.compose.ui.platform.LocalContext.current)) "Automatically updated from your listening and library" else "Updates paused in Settings · your saved rules are kept",
                                    fontSize = 11.sp,
                                    color = if (!hasPro && !darkTheme) lockedColors.muted else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Button(
                                onClick = { editingId = null; draft = freshSmartPlaylist(); editorOpen = true },
                                modifier = Modifier.height(36.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (!hasPro && !darkTheme) lockedColors.iconContainer else MaterialTheme.colorScheme.primary,
                                    contentColor = if (!hasPro && !darkTheme) lockedColors.text else MaterialTheme.colorScheme.onPrimary
                                ),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                            ) {
                                SoundprintIcon(SoundprintIcons.Plus, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("New playlist", fontSize = 12.sp, maxLines = 1, softWrap = false)
                            }
                        }
                        current?.smartPlaylists.orEmpty().forEach { rule ->
                            val count = current?.smartResults?.get(rule.id)?.size ?: 0
                            Row(
                                Modifier.fillMaxWidth()
                                    .then(if (LocalSoundprintDarkTheme.current) Modifier else Modifier.shadow(4.dp, RoundedCornerShape(16.dp), ambientColor = LocalSoundprintThemeTokens.current.shadow, spotColor = LocalSoundprintThemeTokens.current.shadow))
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (!hasPro && !darkTheme) lockedColors.surface else LocalSoundprintThemeTokens.current.card)
                                    .then(
                                        if (!hasPro && !darkTheme) Modifier.border(1.dp, lockedColors.border, RoundedCornerShape(16.dp))
                                        else if (darkTheme) Modifier
                                        else Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.62f), RoundedCornerShape(16.dp))
                                    )
                                    .padding(11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(Modifier.weight(1f).clickable { selectedSmartId = rule.id }, verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(if (!hasPro && !darkTheme) lockedColors.iconContainer else MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                                        SoundprintIcon(SoundprintIcons.ListMusic, null, tint = if (!hasPro && !darkTheme) lockedColors.icon else MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    }
                                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                        Text(rule.name, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (!hasPro && !darkTheme) lockedColors.text else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("$count songs", fontSize = 10.sp, color = if (!hasPro && !darkTheme) lockedColors.muted else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    SoundprintIcon(SoundprintIcons.ChevronRight, null, tint = if (!hasPro && !darkTheme) lockedColors.icon else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                }
                                if (rule.preset) {
                                    Text("Preset", fontSize = 9.sp, color = if (!hasPro && !darkTheme) lockedColors.muted else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 5.dp))
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        SoundprintIcon(SoundprintIcons.Pencil, "Edit ${rule.name}", Modifier.size(17.dp).clip(CircleShape).clickable {
                                            editingId = rule.id
                                            draft = rule.copy(groups = rule.groups.map { it.copy(conditions = it.conditions.toList()) })
                                            editorOpen = true
                                        }.padding(2.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.width(8.dp))
                                        SoundprintIcon(SoundprintIcons.Copy, "Duplicate ${rule.name}", Modifier.size(17.dp).clickable {
                                            val copy = rule.copy(
                                                id = UUID.randomUUID().toString(),
                                                name = "${rule.name} copy",
                                                preset = false,
                                                groups = rule.groups.map { group -> group.copy(id = UUID.randomUUID().toString(), conditions = group.conditions.map { it.copy(id = UUID.randomUUID().toString()) }) }
                                            )
                                            libraryManager.saveSmartPlaylist(copy)
                                            refreshVersion++
                                        }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.width(8.dp))
                                        SoundprintIcon(SoundprintIcons.Trash2, "Delete ${rule.name}", Modifier.size(17.dp).clip(RoundedCornerShape(8.dp)).clickable {
                                            libraryManager.deleteSmartPlaylist(rule.id)
                                            refreshVersion++
                                        }.padding(4.dp), tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                    if (!hasPro) {
                        Box(
                            Modifier.matchParentSize()
                                .clip(RoundedCornerShape(18.dp))
                                .clickable(onClick = proUi.onOpenPro)
                        )
                    }
                }
    
            }
            if (current == null) {
                Text("Loading your playlists…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (searchTerm.isNotBlank()) {
                // Search results stay expanded beneath the search field as the user types.
                if (playlistSongMatches.isEmpty()) {
                    Text(
                        "No playlists or songs match this search.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface).padding(20.dp)
                    )
                } else {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface).padding(9.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        playlistSongMatches.forEach { (playlist, matchedTracks) ->
                            Column(Modifier.fillMaxWidth()) {
                                val cover = playlist.trackIds.firstNotNullOfOrNull { id ->
                                    current?.tracks?.firstOrNull { it.id == id }?.artworkUri
                                }
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp))
                                        .clickable { selectedPlaylistId = playlist.id }
                                        .padding(horizontal = 4.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    NativeArtwork(cover, playlist.id, Modifier.size(42.dp), 9)
                                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                        Text(
                                            playlist.name,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "${matchedTracks.size} matching ${if (matchedTracks.size == 1) "song" else "songs"}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    SoundprintIcon(
                                        SoundprintIcons.ChevronRight,
                                        null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                matchedTracks.forEach { track ->
                                    NativeTrackRow(
                                        track = track,
                                        onClick = {
                                            val allPlaylistTracks = playlist.trackIds.mapNotNull { id ->
                                                current?.tracks?.firstOrNull { it.id == id }
                                            }
                                            val index = allPlaylistTracks.indexOfFirst { it.id == track.id }
                                            if (index >= 0) playback.playTracks(allPlaylistTracks, index)
                                        },
                                        modifier = Modifier.padding(start = 18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (filtered.isEmpty()) {
                Text("No playlists yet", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(20.dp))
                Text("Create a playlist to collect songs, or build a smart playlist that updates automatically.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    filtered.forEach { playlist ->
                        val cover = playlist.trackIds.firstNotNullOfOrNull { id -> current?.tracks?.firstOrNull { it.id == id }?.artworkUri }
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { selectedPlaylistId = playlist.id }.padding(horizontal = 9.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            NativeArtwork(cover, playlist.id, Modifier.size(48.dp), 10)
                            Column(Modifier.weight(1f).padding(start = 11.dp)) {
                                Text(playlist.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${playlist.trackIds.size} songs", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            SoundprintIcon(SoundprintIcons.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
                        }
                    }
                }
            }
        }
    }

    if (createPlaylistOpen) {
        AlertDialog(
            onDismissRequest = { createPlaylistOpen = false },
            title = { Text("New playlist") },
            text = { OutlinedTextField(value = playlistName, onValueChange = { playlistName = it }, singleLine = true, placeholder = { Text("Playlist name") },
                colors = soundprintOutlinedFieldColors()
            ) },
            confirmButton = {
                TextButton(enabled = playlistName.isNotBlank(), onClick = {
                    libraryManager.createPlaylist(playlistName.trim())
                    playlistName = ""
                    createPlaylistOpen = false
                    refreshVersion++
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { createPlaylistOpen = false }) { Text("Cancel") } }
        )
    }
    if (editorOpen) {
        SmartPlaylistEditorDialog(
            draft = draft,
            onDraftChange = { draft = it },
            editing = editingId != null,
            onDismiss = { editorOpen = false },
            onSave = {
                libraryManager.saveSmartPlaylist(draft.copy(name = draft.name.trim(), preset = false))
                editorOpen = false
                refreshVersion++
            }
        )
    }
}

@Composable
private fun PlaylistPresetSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            Text(description, modifier = Modifier.padding(top = 3.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 15.sp)
        }
        SoundprintSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PlaylistDetailNative(
    playlistId: String,
    libraryManager: MusicLibraryManager,
    playback: SoundprintPlaybackConnection,
    onBack: () -> Unit,
    onChanged: () -> Unit
) {
    var refresh by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val proAccess = LocalSoundprintProUiAccess.current
    val presetStore = remember(context) { PlaylistPlaybackPresetStore(context) }
    var preset by remember(playlistId) { mutableStateOf(presetStore.get(playlistId)) }
    LaunchedEffect(playlistId) { preset = presetStore.get(playlistId) }
    fun updatePreset(next: PlaylistPlaybackPreset) {
        if (!proAccess.entitled) {
            proAccess.onOpenPro()
            return
        }
        preset = next
        presetStore.set(playlistId, next)
    }
    var renameOpen by remember { mutableStateOf(false) }
    var addOpen by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var candidateQuery by remember { mutableStateOf("") }
    var tracks by remember { mutableStateOf(emptyList<MusicTrack>()) }
    var libraryTracks by remember { mutableStateOf(emptyList<MusicTrack>()) }
    var detailLoaded by remember { mutableStateOf(false) }
    var playlist by remember { mutableStateOf<ManagedPlaylist?>(null) }
    LaunchedEffect(playlistId, refresh) {
        val loaded = withContext(Dispatchers.IO) {
            val item = libraryManager.playlists().firstOrNull { it.id == playlistId }
            Triple(item, libraryManager.playlistTracks(playlistId), libraryManager.allSongs())
        }
        playlist = loaded.first
        tracks = loaded.second
        libraryTracks = loaded.third
        detailLoaded = true
    }
    val selected = playlist
    if (selected == null) {
        Column(Modifier.fillMaxSize()) {
            PlaylistPageHeader("Playlist", "", back = onBack)
            Text(if (detailLoaded) "Playlist not found" else "Loading playlist…", Modifier.padding(24.dp), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        PlaylistPageHeader(selected.name, "${selected.trackIds.size} songs", back = onBack)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Spacer(Modifier.height(18.dp))
            val cover = tracks.firstNotNullOfOrNull { it.artworkUri }
            NativeArtwork(cover, selected.id, Modifier.size(160.dp).align(Alignment.CenterHorizontally), 24)
            Text(selected.name, Modifier.fillMaxWidth().padding(top = 13.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text("${tracks.size} songs", Modifier.fillMaxWidth().padding(top = 3.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Button(
                    onClick = {
                        if (preset.enabled && proAccess.entitled) {
                            playback.playTracks(tracks, shuffle = preset.shuffle, playlistPreset = preset)
                        } else playback.playTracks(tracks)
                    },
                    enabled = tracks.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    SoundprintIcon(SoundprintIcons.Play, null); Spacer(Modifier.width(5.dp)); Text("Play")
                }
                Button(
                    onClick = {
                        if (!proAccess.entitled || !preset.enabled) {
                            playback.playTracks(tracks, shuffle = true)
                        } else {
                            val shuffledPreset = preset.copy(shuffle = true)
                            playback.playTracks(tracks, shuffle = true, playlistPreset = shuffledPreset)
                        }
                    },
                    enabled = tracks.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    SoundprintIcon(SoundprintIcons.Shuffle, null); Spacer(Modifier.width(5.dp)); Text("Shuffle")
                }
            }
            ProFeatureGate(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(top = 18.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .46f))
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Playlist Intelligence", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Custom playback for this playlist", modifier = Modifier.padding(top = 4.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    ProFeatureBadge()
                }
                Text("Playlist style", Modifier.padding(top = 12.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text("Apply a sound and playback setup for this playlist", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                NativeChoice("Choose a style", listOf(
                    "custom" to "Custom",
                    "workout" to "Workout · Bass + energy",
                    "bass" to "Bass Boost",
                    "party" to "Party · Shuffle",
                    "chill" to "Chill · Smooth",
                    "focus" to "Focus · Levelled"
                )) { style ->
                    val next = when (style) {
                        "workout" -> preset.copy(enabled = true, extraBass = true, shuffle = true, loudnessLeveling = true, repeat = PlaylistRepeatPreset.ALL)
                        "bass" -> preset.copy(enabled = true, extraBass = true, shuffle = false, loudnessLeveling = false, repeat = PlaylistRepeatPreset.OFF)
                        "party" -> preset.copy(enabled = true, extraBass = true, shuffle = true, loudnessLeveling = true, repeat = PlaylistRepeatPreset.ALL)
                        "chill" -> preset.copy(enabled = true, extraBass = false, shuffle = false, loudnessLeveling = true, repeat = PlaylistRepeatPreset.ALL)
                        "focus" -> preset.copy(enabled = true, extraBass = false, shuffle = false, loudnessLeveling = true, repeat = PlaylistRepeatPreset.OFF)
                        else -> preset.copy(enabled = true)
                    }
                    updatePreset(next)
                }
                PlaylistPresetSwitch("Use preset", "Automatically apply these settings on Play", preset.enabled) { updatePreset(preset.copy(enabled = it)) }
                PlaylistPresetSwitch("Extra Bass", "Adds low-end punch", preset.extraBass) { updatePreset(preset.copy(enabled = true, extraBass = it)) }
                PlaylistPresetSwitch("Shuffle", "Mix this playlist automatically", preset.shuffle) { updatePreset(preset.copy(enabled = true, shuffle = it)) }
                PlaylistPresetSwitch("Loudness leveling", "Keep track volume more consistent", preset.loudnessLeveling) { updatePreset(preset.copy(enabled = true, loudnessLeveling = it)) }
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Repeat", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        Text("Off, repeat playlist, or repeat one", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = {
                        if (!proAccess.entitled) proAccess.onOpenPro()
                        else updatePreset(preset.copy(enabled = true, repeat = when (preset.repeat) {
                            PlaylistRepeatPreset.OFF -> PlaylistRepeatPreset.ALL
                            PlaylistRepeatPreset.ALL -> PlaylistRepeatPreset.ONE
                            PlaylistRepeatPreset.ONE -> PlaylistRepeatPreset.OFF
                        }))
                    }) {
                        Text(when (preset.repeat) {
                            PlaylistRepeatPreset.OFF -> "Off"
                            PlaylistRepeatPreset.ALL -> "Playlist"
                            PlaylistRepeatPreset.ONE -> "One"
                        })
                    }
                }
            }
            }
            Row(Modifier.fillMaxWidth().padding(top = 7.dp), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = { addOpen = true }) { SoundprintIcon(SoundprintIcons.AddMusic, null); Text("Add songs") }
                TextButton(onClick = { name = selected.name; renameOpen = true }) { SoundprintIcon(SoundprintIcons.Pencil, null); Text("Rename") }
                TextButton(onClick = { presetStore.delete(selected.id); libraryManager.deletePlaylist(selected.id); onBack(); onChanged() }) { SoundprintIcon(SoundprintIcons.Trash2, null); Text("Delete") }
            }
            Spacer(Modifier.height(10.dp))
            if (tracks.isEmpty()) {
                EmptyPlaylistState(onAdd = { addOpen = true })
            } else {
                tracks.forEachIndexed { index, track ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        NativeTrackRow(track, onClick = { if (preset.enabled && proAccess.entitled) playback.playTracks(tracks, index, shuffle = preset.shuffle, playlistPreset = preset) else playback.playTracks(tracks, index) }, onRemove = { libraryManager.removeFromPlaylist(selected.id, track.id); refresh++; onChanged() }, modifier = Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            SoundprintIcon(SoundprintIcons.ArrowUp, "Move up", Modifier.size(16.dp).clickable(enabled = index > 0) { libraryManager.reorderPlaylist(selected.id, index, index - 1); refresh++ }.padding(1.dp), tint = if (index > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline)
                            SoundprintIcon(SoundprintIcons.ArrowDown, "Move down", Modifier.size(16.dp).clickable(enabled = index < tracks.lastIndex) { libraryManager.reorderPlaylist(selected.id, index, index + 1); refresh++ }.padding(1.dp), tint = if (index < tracks.lastIndex) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }
    if (renameOpen) AlertDialog(
        onDismissRequest = { renameOpen = false }, title = { Text("Rename playlist") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true,
            colors = soundprintOutlinedFieldColors()
        ) },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { libraryManager.renamePlaylist(playlistId, name.trim()); renameOpen = false; refresh++; onChanged() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = { renameOpen = false }) { Text("Cancel") } }
    )
    if (addOpen) AddSongsDialog(
        allTracks = libraryTracks,
        existingIds = selected?.trackIds.orEmpty(),
        query = candidateQuery,
        onQuery = { candidateQuery = it },
        onAdd = { id -> libraryManager.addToPlaylist(playlistId, listOf(id)); refresh++; onChanged() },
        onDismiss = { addOpen = false }
    )
}

@Composable
private fun SmartPlaylistDetailNative(
    smartId: String,
    libraryManager: MusicLibraryManager,
    playback: SoundprintPlaybackConnection,
    clapScanner: ClapLibraryScanner,
    onBack: () -> Unit,
    refreshVersion: Int
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scan by clapScanner.state.collectAsState()
    var tracks by remember { mutableStateOf(emptyList<MusicTrack>()) }
    var title by remember { mutableStateOf("") }
    var isRule by remember { mutableStateOf(false) }
    LaunchedEffect(smartId, refreshVersion, scan.completed, scan.lastCompletedTrackId, scan.failed) {
        val loaded = withContext(Dispatchers.IO) {
            when (smartId) {
                "liked" -> Triple("Liked Songs", libraryManager.allSongs().filter { it.isFavorite }, false)
                "recent" -> Triple("Recently Played", if (SoundprintListeningPreferences.historyEnabled(context)) libraryManager.listeningHistory(500).mapNotNull { it.second }.distinctBy { it.id }.take(30) else emptyList(), false)
                "added" -> Triple("Recently Added", libraryManager.allSongs().sortedByDescending { it.addedAt }.take(20), false)
                else -> {
                    val rule = libraryManager.smartPlaylists().firstOrNull { it.id == smartId }
                    Triple(rule?.name ?: "Smart playlist", libraryManager.evaluateSmartPlaylist(smartId), rule != null)
                }
            }
        }
        title = loaded.first
        tracks = loaded.second
        isRule = loaded.third
    }
    Column(Modifier.fillMaxSize()) {
        PlaylistPageHeader(title, "${tracks.size} songs", back = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Spacer(Modifier.height(18.dp))
            NativeArtwork(tracks.firstNotNullOfOrNull { it.artworkUri }, smartId, Modifier.size(160.dp).align(Alignment.CenterHorizontally), 24)
            Text(title, Modifier.fillMaxWidth().padding(top = 13.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text("${tracks.size} songs${if (isRule) " · Updates automatically" else ""}", Modifier.fillMaxWidth().padding(top = 3.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Button(onClick = { playback.playTracks(tracks) }, enabled = tracks.isNotEmpty(), modifier = Modifier.weight(1f), shape = CircleShape) {
                    SoundprintIcon(SoundprintIcons.Play, null); Spacer(Modifier.width(5.dp)); Text("Play")
                }
                Button(
                    onClick = { playback.playTracks(tracks, shuffle = true) },
                    enabled = tracks.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    SoundprintIcon(SoundprintIcons.Shuffle, null); Spacer(Modifier.width(5.dp)); Text("Shuffle")
                }
            }
            Spacer(Modifier.height(12.dp))
            if (tracks.isEmpty()) {
                Text("No matching songs yet", Modifier.fillMaxWidth().padding(top = 32.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text("This playlist updates automatically when songs in your library match its rules.", Modifier.fillMaxWidth().padding(top = 6.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else tracks.forEachIndexed { index, track ->
                NativeTrackRow(track, onClick = { playback.playTracks(tracks, index) })
            }
        }
    }
}

@Composable
private fun SmartPlaylistEditorDialog(
    draft: NativeSmartPlaylist,
    onDraftChange: (NativeSmartPlaylist) -> Unit,
    editing: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    var advancedOpen by remember(draft.id) { mutableStateOf(false) }
    fun updateCondition(groupId: String, conditionId: String, patch: SmartCondition) {
        onDraftChange(draft.copy(groups = draft.groups.map { group ->
            if (group.id != groupId) group else group.copy(conditions = group.conditions.map { if (it.id == conditionId) patch else it })
        }))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing) "Edit smart playlist" else "Create smart playlist") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = draft.name, onValueChange = { onDraftChange(draft.copy(name = it)) }, singleLine = true, placeholder = { Text("Playlist name") }, modifier = Modifier.fillMaxWidth(),
                    colors = soundprintOutlinedFieldColors()
                )
                Text("Quick start", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                NativeChoice("Choose a starting point", listOf("never" to "Never played", "recent" to "Recently added", "favorite" to "Favorites", "energy" to "High energy", "custom" to "Start from scratch")) { preset ->
                    val base = draft.groups.firstOrNull()?.conditions?.firstOrNull() ?: freshCondition()
                    val (field, operator, value, name) = when (preset) {
                        "never" -> listOf(SmartField.NEVER_PLAYED, SmartOperator.EQ, "true", "Never Played")
                        "recent" -> listOf(SmartField.DATE_ADDED, SmartOperator.LTE, "7", "Recently Added")
                        "favorite" -> listOf(SmartField.FAVORITE, SmartOperator.EQ, "true", "Favorites")
                        "energy" -> listOf(SmartField.ENERGY, SmartOperator.GTE, "70", "High Energy")
                        else -> listOf(SmartField.GENRE, SmartOperator.CONTAINS, "", "New smart playlist")
                    }
                    val condition = base.copy(field = field as SmartField, operator = operator as SmartOperator, value = value as String)
                    val group = (draft.groups.firstOrNull() ?: freshGroup()).copy(conditions = listOf(condition))
                    onDraftChange(draft.copy(name = name as String, groups = listOf(group)))
                }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).clickable { advancedOpen = !advancedOpen }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Advanced settings", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Groups, multiple rules, sorting and song limit", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(if (advancedOpen) "Hide" else "Show", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
                if (advancedOpen) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Match groups", Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        NativeChoice(draft.groupsJoin.name, listOf("AND" to "All groups", "OR" to "Any group")) { value -> onDraftChange(draft.copy(groupsJoin = SmartJoin.valueOf(value))) }
                    }
                }
                draft.groups.forEachIndexed { groupIndex, group ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Group ${groupIndex + 1}", Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            if (advancedOpen) NativeChoice(group.join.name, listOf("AND" to "Match all", "OR" to "Match any")) { value ->
                                onDraftChange(draft.copy(groups = draft.groups.map { if (it.id == group.id) it.copy(join = SmartJoin.valueOf(value)) else it }))
                            }
                            if (advancedOpen && draft.groups.size > 1) Text("×", Modifier.clickable { onDraftChange(draft.copy(groups = draft.groups.filterNot { it.id == group.id })) }.padding(7.dp), fontSize = 20.sp)
                        }
                        group.conditions.forEach { condition ->
                            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.background).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                NativeChoice(condition.field.name, smartFields.map { it.first.name to it.second.first }) { value ->
                                    updateCondition(group.id, condition.id, condition.copy(field = SmartField.valueOf(value), operator = SmartOperator.EQ, value = ""))
                                }
                                NativeChoice(condition.operator.name, smartOperators.map { it.first.name to it.second }) { value ->
                                    updateCondition(group.id, condition.id, condition.copy(operator = SmartOperator.valueOf(value)))
                                }
                                if (smartFieldType(condition.field) == "boolean") {
                                    NativeChoice(condition.value, listOf("true" to "Yes", "false" to "No", "" to "Choose…")) { value ->
                                        updateCondition(group.id, condition.id, condition.copy(value = value))
                                    }
                                } else {
                                    OutlinedTextField(
                                        value = condition.value,
                                        onValueChange = { updateCondition(group.id, condition.id, condition.copy(value = it)) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        placeholder = { Text(if (smartFieldType(condition.field) == "number") "Number" else "Value") }
                                    ,
                                        colors = soundprintOutlinedFieldColors()
                                    )
                                }
                                if (advancedOpen) TextButton(enabled = group.conditions.size > 1, onClick = {
                                    onDraftChange(draft.copy(groups = draft.groups.map { if (it.id == group.id) it.copy(conditions = it.conditions.filterNot { c -> c.id == condition.id }) else it }))
                                }) { Text("Remove condition") }
                            }
                        }
                        if (advancedOpen) TextButton(onClick = { onDraftChange(draft.copy(groups = draft.groups.map { if (it.id == group.id) it.copy(conditions = it.conditions + freshCondition()) else it })) }) {
                            SoundprintIcon(SoundprintIcons.Plus, null); Text("Add condition")
                        }
                    }
                }
                if (advancedOpen) Button(
                    onClick = { onDraftChange(draft.copy(groups = draft.groups + freshGroup())) },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    SoundprintIcon(SoundprintIcons.Plus, null); Text("Add group")
                }
                if (advancedOpen) Text("Sort and limit", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                if (advancedOpen) NativeChoice(draft.sortField.name, SmartSortField.entries.map { it.name to it.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase) }) { value ->
                    onDraftChange(draft.copy(sortField = SmartSortField.valueOf(value)))
                }
                if (advancedOpen) NativeChoice(if (draft.ascending) "asc" else "desc", listOf("desc" to "Descending", "asc" to "Ascending")) { value ->
                    onDraftChange(draft.copy(ascending = value == "asc"))
                }
                var limitText by remember(draft.id) { mutableStateOf(draft.limit?.toString().orEmpty()) }
                if (advancedOpen) OutlinedTextField(value = limitText, onValueChange = {
                    limitText = it.filter(Char::isDigit).take(4)
                    onDraftChange(draft.copy(limit = limitText.toIntOrNull()?.coerceIn(1, 1000)))
                }, singleLine = true, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Limit (optional)") },
                    colors = soundprintOutlinedFieldColors()
                )
                if (advancedOpen) Text("Mood and energy rules use saved music matches. Tempo and lyrics filters need those details to be available in your library.", fontSize = 10.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(enabled = draft.name.isNotBlank() && draft.groups.isNotEmpty() && draft.groups.all { it.conditions.isNotEmpty() && it.conditions.all { c -> c.value.isNotBlank() } }, onClick = onSave) { Text("Save smart playlist") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun NativeChoice(value: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = options.firstOrNull { it.first == value }?.second ?: value
    Box {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.surface)
            
            .clickable { expanded = true }.padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("⌄", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (key, text) -> DropdownMenuItem(text = { Text(text, fontSize = 12.sp) }, onClick = { onSelect(key); expanded = false }) }
        }
    }
}

@Composable
private fun AddSongsDialog(
    allTracks: List<MusicTrack>,
    existingIds: List<String>,
    query: String,
    onQuery: (String) -> Unit,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val candidates = allTracks.filter { it.id !in existingIds && "${it.title} ${it.artist} ${it.album}".contains(query.trim(), ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add songs") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                PlaylistSearchBox(query, onQuery, "Search songs to add…")
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
                    candidates.forEach { track ->
                        Row(Modifier.fillMaxWidth().clickable { onAdd(track.id) }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            NativeArtwork(track.artworkUri, track.id, Modifier.size(42.dp), 8)
                            Column(Modifier.weight(1f).padding(start = 9.dp)) {
                                Text(track.title, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(track.artist, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            SoundprintIcon(SoundprintIcons.AddMusic, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (candidates.isEmpty()) Text("No songs match this search.", Modifier.padding(14.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun EmptyPlaylistState(onAdd: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(60.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            SoundprintIcon(SoundprintIcons.ListMusic, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(26.dp))
        }
        Text("This playlist is empty", Modifier.padding(top = 12.dp), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text("Add songs from your library to start building it.", Modifier.padding(top = 4.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onAdd, modifier = Modifier.padding(top = 12.dp), shape = CircleShape) { SoundprintIcon(SoundprintIcons.AddMusic, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Add songs") }
    }
}

@Composable
private fun NativeTrackRow(track: MusicTrack, onClick: () -> Unit, onRemove: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Row(modifier.weightlessClickRow(onClick), verticalAlignment = Alignment.CenterVertically) {
        NativeArtwork(track.artworkUri, track.id, Modifier.size(44.dp), 8)
        Column(Modifier.weight(1f).padding(start = 10.dp, end = 7.dp)) {
            Text(track.title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${track.artist}${if (track.album.isNotBlank()) " · ${track.album}" else ""}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(formatNativeDuration(track.durationMs), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onRemove != null) {
            SoundprintIcon(SoundprintIcons.X, "Remove", Modifier.padding(start = 7.dp).size(17.dp).clickable(onClick = onRemove), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun Modifier.weightlessClickRow(onClick: () -> Unit): Modifier = this.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 8.dp)

@Composable
private fun PlaylistPageHeader(title: String, subtitle: String, action: (() -> Unit)? = null, back: (() -> Unit)? = null) {
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (back != null) {
                Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = back), contentAlignment = Alignment.Center) {
                    SoundprintIcon(SoundprintIcons.ArrowLeft, null, tint = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (action != null) Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = action), contentAlignment = Alignment.Center) {
                SoundprintIcon(SoundprintIcons.Plus, "New playlist", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(0.dp))
    }
}

@Composable
private fun PlaylistSearchBox(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    Row(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)
        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.92f), RoundedCornerShape(16.dp))
        .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        SoundprintIcon(SoundprintIcons.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(9.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(value, onValueChange, singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface), modifier = Modifier.fillMaxWidth())
        }
        if (value.isNotEmpty()) SoundprintIcon(SoundprintIcons.X, "Clear search", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp).clickable { onValueChange("") })
    }
}

@Composable
internal fun NativeArtwork(uri: String?, seed: String, modifier: Modifier = Modifier, radius: Int = 12) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, uri, seed) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (uri.isNullOrBlank()) return@runCatching null
                val stream = if (uri.startsWith("/")) File(uri).inputStream() else context.contentResolver.openInputStream(Uri.parse(uri))
                stream?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            }.getOrNull()
        }
    }
    Box(modifier.clip(RoundedCornerShape(radius.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        val loadedBitmap = bitmap
        if (loadedBitmap != null) {
            Image(loadedBitmap, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            // Missing or unavailable artwork uses the Lucide track-note placeholder.
            // Bundled custom covers are only stored as artwork when the Pro feature is enabled.
            SoundprintIcon(
                SoundprintIcons.Music2,
                contentDescription = "No artwork",
                modifier = Modifier.size(26.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatNativeDuration(durationMs: Long): String {
    val total = (durationMs / 1000L).coerceAtLeast(0L)
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}
