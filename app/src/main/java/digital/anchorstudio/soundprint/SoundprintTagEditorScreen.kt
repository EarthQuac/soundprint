package digital.anchorstudio.soundprint

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SoundprintTagEditorScreen(
    library: MusicLibraryManager,
    onBack: () -> Unit,
    onExportMp3: (SoundprintTagExportRequest) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var refreshVersion by remember { mutableIntStateOf(0) }
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var album by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val tracks = remember(query, refreshVersion) { library.searchSongs(query).take(100) }
    val selected = tracks.firstOrNull { it.id == selectedId }

    LaunchedEffect(selectedId, selected?.title, selected?.artist, selected?.album, selected?.genre) {
        selected?.let {
            title = it.title
            artist = it.artist
            album = it.album
            genre = it.genre
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(onClick = onBack) {
                SoundprintIcon(SoundprintIcons.ArrowLeft, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Back", modifier = Modifier.padding(start = 6.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("Tag Editor", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                Text("Edit song details in your library", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; selectedId = null; status = null },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { SoundprintIcon(SoundprintIcons.Search, contentDescription = null) },
                placeholder = { Text("Find a track…") },
                shape = RoundedCornerShape(14.dp)
            ,
    colors = soundprintOutlinedFieldColors()
,
    colors = soundprintFilledFieldColors()
)
            Spacer(Modifier.height(10.dp))
            Card(
                modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (tracks.isEmpty()) {
                    Text("No tracks found. Import music first.", modifier = Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                } else {
                    LazyColumn {
                        items(tracks, key = { it.id }) { track ->
                            Column(
                                Modifier.fillMaxWidth()
                                    .background(if (selectedId == track.id) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface)
                                    .clickable { selectedId = track.id; status = null }
                                    .padding(horizontal = 14.dp, vertical = 11.dp)
                            ) {
                                Text(track.title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (selectedId == track.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${track.artist} · ${track.album}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            if (selected != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Edit track details", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Title") }, singleLine = true, shape = RoundedCornerShape(12.dp),
    colors = soundprintOutlinedFieldColors()
,
    colors = soundprintFilledFieldColors()
)
                        OutlinedTextField(artist, { artist = it }, Modifier.fillMaxWidth(), label = { Text("Artist") }, singleLine = true, shape = RoundedCornerShape(12.dp),
    colors = soundprintOutlinedFieldColors()
,
    colors = soundprintFilledFieldColors()
)
                        OutlinedTextField(album, { album = it }, Modifier.fillMaxWidth(), label = { Text("Album") }, singleLine = true, shape = RoundedCornerShape(12.dp),
    colors = soundprintOutlinedFieldColors()
,
    colors = soundprintFilledFieldColors()
)
                        OutlinedTextField(genre, { genre = it }, Modifier.fillMaxWidth(), label = { Text("Genre") }, singleLine = true, shape = RoundedCornerShape(12.dp),
    colors = soundprintOutlinedFieldColors()
,
    colors = soundprintFilledFieldColors()
)
                        Button(
                            onClick = {
                                val trackId = selected.id
                                val values = listOf(title, artist, album, genre)
                                scope.launch {
                                    saving = true
                                    status = null
                                    try {
                                        withContext(Dispatchers.IO) {
                                            library.updateSongMetadata(trackId, values[0], values[1], values[2], values[3])
                                        }
                                        refreshVersion++
                                        status = "Library details saved."
                                    } catch (error: Exception) {
                                        status = error.message ?: "Could not save these details."
                                    } finally {
                                        saving = false
                                    }
                                }
                            },
                            enabled = !saving && title.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) { SoundprintIcon(SoundprintIcons.Save, null); Text(if (saving) "Saving…" else "Save library details") }
                        if (selected.mimeType.contains("mpeg", true)) {
                            Button(
                                onClick = { onExportMp3(SoundprintTagExportRequest(selected, title, artist, album, genre)) },
                                enabled = title.isNotBlank() && !saving,
                                modifier = Modifier.fillMaxWidth()
                            ) { SoundprintIcon(SoundprintIcons.Download, null); Text("Export updated MP3 copy") }
                        } else {
                            Text("MP3 export is available for MP3 files. Save library details for other formats.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        status?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            } else {
                Text("Choose a track above to edit its details.", modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
