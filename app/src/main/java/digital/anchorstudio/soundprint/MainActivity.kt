package digital.anchorstudio.soundprint

import android.os.Bundle
import android.app.Activity
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.auth
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.systemBars
import android.net.Uri
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.awaitCancellation
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import androidx.work.WorkManager
import androidx.work.WorkInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.DateFormat
import java.util.Date

private val SoundprintFontFamily = FontFamily(Font(R.font.inter_tight_variable))

private data class SoundprintPalette(
    val background: Color,
    val foreground: Color,
    val surface: Color,
    val surface2: Color,
    val primary: Color,
    val muted: Color,
    val border: Color,
    val onPrimary: Color
)

private val AmberLight = SoundprintPalette(
    background = Color(0xFFFBFAF7), foreground = Color(0xFF17130F),
    surface = Color(0xFFFFFFFF), surface2 = Color(0xFFF2F0EC),
    primary = Color(0xFFDF7A32), muted = Color(0xFF6B655F),
    border = Color(0xFFE0DEDA), onPrimary = Color(0xFF160D07)
)
private val AmberDark = SoundprintPalette(
    background = Color(0xFF0C0A07), foreground = Color(0xFFF3F1EF),
    surface = Color(0xFF161310), surface2 = Color(0xFF201C18),
    primary = Color(0xFFFC9E47), muted = Color(0xFF9C9792),
    border = Color(0x17FFFFFF), onPrimary = Color(0xFF160D07)
)
private val BlueLight = SoundprintPalette(
    background = Color(0xFFFAFCFE), foreground = Color(0xFF0C1723),
    surface = Color(0xFFFFFFFF), surface2 = Color(0xFFEEF4FB),
    primary = Color(0xFF156CDD), muted = Color(0xFF5E6A7A),
    border = Color(0xFFD7DFE8), onPrimary = Color.White
)
private val BlueDark = SoundprintPalette(
    background = Color(0xFF090F1A), foreground = Color(0xFFF1F6FA),
    surface = Color(0xFF121B29), surface2 = Color(0xFF1A2435),
    primary = Color(0xFF59AAF8), muted = Color(0xFF99A6B4),
    border = Color(0x1CFFFFFF), onPrimary = Color(0xFF0A121F)
)
private val EmeraldLight = SoundprintPalette(
    background = Color(0xFFF6FCF7), foreground = Color(0xFF0C1F17),
    surface = Color(0xFFFFFFFF), surface2 = Color(0xFFEBF5EE),
    primary = Color(0xFF007852), muted = Color(0xFF5C6E65),
    border = Color(0xFFD7E1DA), onPrimary = Color(0xFFF4FAF5)
)
private val EmeraldDark = SoundprintPalette(
    background = Color(0xFF05130E), foreground = Color(0xFFEDF4EE),
    surface = Color(0xFF0A1C16), surface2 = Color(0xFF10271F),
    primary = Color(0xFF5ACA94), muted = Color(0xFF99AA9E),
    border = Color(0x1CFFFFFF), onPrimary = Color(0xFF0B1E15)
)
private val MonoLight = SoundprintPalette(
    background = Color(0xFFF8F8F8), foreground = Color(0xFF121212),
    surface = Color.White, surface2 = Color(0xFFF0F0F0),
    primary = Color(0xFF1F1F1F), muted = Color(0xFF696969),
    border = Color(0xFFDBDBDB), onPrimary = Color.White
)
private val MonoDark = SoundprintPalette(
    background = Color(0xFF090909), foreground = Color(0xFFF5F5F5),
    surface = Color(0xFF141414), surface2 = Color(0xFF1D1D1D),
    primary = Color(0xFFE8E8E8), muted = Color(0xFFA4A4A4),
    border = Color(0x1FFFFFFF), onPrimary = Color(0xFF141414)
)
private val LavenderLight = SoundprintPalette(
    background = Color(0xFFFAF9FC), foreground = Color(0xFF19171F),
    surface = Color(0xFFFFFFFF), surface2 = Color(0xFFF0EDF6),
    primary = Color(0xFF765CB8), muted = Color(0xFF716C7C),
    border = Color(0xFFE2DDEB), onPrimary = Color.White
)
private val LavenderDark = SoundprintPalette(
    background = Color(0xFF090A0D), foreground = Color(0xFFF5F5F6),
    surface = Color(0xFF14141B), surface2 = Color(0xFF211E2B),
    primary = Color(0xFFC4B5FD), muted = Color(0xFFA5A5B2),
    border = Color(0xFF2A2933), onPrimary = Color(0xFF171321)
)
private val AuroraLight = SoundprintPalette(
    background = Color(0xFFF5FAFA), foreground = Color(0xFF102326),
    surface = Color(0xFFFFFFFF), surface2 = Color(0xFFE5F1F0),
    primary = Color(0xFF087F83), muted = Color(0xFF587174),
    border = Color(0xFFD2E3E1), onPrimary = Color.White
)
private val AuroraDark = SoundprintPalette(
    background = Color(0xFF071719), foreground = Color(0xFFE9FAF7),
    surface = Color(0xFF0E2426), surface2 = Color(0xFF153437),
    primary = Color(0xFF63E0D2), muted = Color(0xFF9AB9B6),
    border = Color(0xFF244548), onPrimary = Color(0xFF06201F)
)
private val SakuraLight = SoundprintPalette(
    background = Color(0xFFFFF8FA), foreground = Color(0xFF2B1721),
    surface = Color(0xFFFFFFFF), surface2 = Color(0xFFF8E8EE),
    primary = Color(0xFFC34F78), muted = Color(0xFF806773),
    border = Color(0xFFEED5DF), onPrimary = Color.White
)
private val SakuraDark = SoundprintPalette(
    background = Color(0xFF1B1018), foreground = Color(0xFFF9EDF3),
    surface = Color(0xFF291824), surface2 = Color(0xFF382333),
    primary = Color(0xFFF29AB8), muted = Color(0xFFC3A8B5),
    border = Color(0xFF4B3345), onPrimary = Color(0xFF29121F)
)
private fun palette(name: String, dark: Boolean): SoundprintPalette = when (name) {
    "Blue" -> if (dark) BlueDark else BlueLight
    "Emerald" -> if (dark) EmeraldDark else EmeraldLight
    "Mono" -> if (dark) MonoDark else MonoLight
    "Lavender" -> if (dark) LavenderDark else LavenderLight
    "Aurora" -> if (dark) AuroraDark else AuroraLight
    "Sakura" -> if (dark) SakuraDark else SakuraLight
    else -> if (dark) AmberDark else AmberLight
}

private data class PendingArtworkImport(val enqueue: (Boolean) -> Unit)

class MainActivity : ComponentActivity() {
    private val accountReturnRequestCount = mutableIntStateOf(0)

    private fun handleAccountReturnIntent(intent: Intent) {
        if (intent.data?.scheme == "soundprint" && intent.data?.host == "account") {
            accountReturnRequestCount.intValue += 1
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleAccountReturnIntent(intent)
        SoundprintSupabase.client.handleDeeplinks(intent)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val activityWindow = window
        setContent {
            val preferences = remember { getSharedPreferences("soundprint_native_preferences", MODE_PRIVATE) }
            var themeMode by remember { mutableStateOf(preferences.getString("theme", "dark") ?: "dark") }
            var selectedPalette by remember { mutableStateOf(preferences.getString("palette", "Lavender") ?: "Lavender") }
            var enteredLibrary by remember { mutableStateOf(preferences.getBoolean("has_seen_launch_landing", false)) }
            LaunchedEffect(accountReturnRequestCount.intValue) {
                if (accountReturnRequestCount.intValue > 0) {
                    enteredLibrary = true
                    preferences.edit().putBoolean("has_seen_launch_landing", true).apply()
                }
            }
            val libraryRepository = remember { MusicLibraryRepository.getInstance(applicationContext) }
            val libraryManager = remember { MusicLibraryManager.getInstance(applicationContext) }
            val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
            var libraryTrackCount by remember { mutableIntStateOf(0) }
            var librarySources by remember { mutableStateOf(emptyList<MusicLibrarySource>()) }
            var libraryStatus by remember { mutableStateOf("Checking your saved music locations…") }
            var watchedFolderUri by remember { mutableStateOf(preferences.getString("watched_folder_uri", null)) }
            var watchedFolderName by remember { mutableStateOf(preferences.getString("watched_folder_name", null)) }
            var folderWatchEnabled by remember { mutableStateOf(preferences.getBoolean("folder_watch_enabled", false)) }
            var folderWatchLastScan by remember { mutableStateOf(preferences.getLong("folder_watch_last_scan", 0L)) }
            var autoAddNewSongs by remember { mutableStateOf(preferences.getBoolean("auto_add_new_songs", true)) }
            var pendingTagExport by remember { mutableStateOf<SoundprintTagExportRequest?>(null) }
            val folderWatchMutex = remember { Mutex() }
            var importUiProgress by remember { mutableStateOf<SoundprintImportUiProgress?>(null) }
            var pendingArtworkImport by remember { mutableStateOf<PendingArtworkImport?>(null) }
            var linkImportDialogOpen by remember { mutableStateOf(false) }
            var linkImportText by remember { mutableStateOf("") }
            val scope = rememberCoroutineScope()
            suspend fun refreshLibraryState() {
                val snapshot = withContext(Dispatchers.IO) {
                    libraryRepository.count() to libraryRepository.allSources()
                }
                libraryTrackCount = snapshot.first
                librarySources = snapshot.second
            }
            fun enqueueMusicImport(label: String, enqueue: () -> Unit) {
                try {
                    enqueue()
                    libraryStatus = "$label queued. Soundprint will keep working if you rotate the phone or leave the app."
                } catch (error: Exception) {
                    libraryStatus = "$label could not be queued: ${error.message ?: "Check the selected file permissions and try again."}"
                }
            }
            fun finishArtworkImportChoice(pending: PendingArtworkImport, useArtwork: Boolean) {
                preferences.edit().putBoolean("custom_artwork_enabled", useArtwork).apply()
                pendingArtworkImport = null
                pending.enqueue(useArtwork)
                scope.launch {
                    withContext(Dispatchers.IO) { libraryManager.setCustomArtworkEnabled(useArtwork) }
                }
            }
            val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
                if (uri != null) pendingArtworkImport = PendingArtworkImport { useArtwork ->
                    enqueueMusicImport("Folder import") {
                        SoundprintMusicImportQueue.enqueueFolder(applicationContext, uri, useArtwork)
                    }
                }
            }
            val watchedFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
                if (uri != null) {
                    runCatching {
                        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    watchedFolderUri = uri.toString()
                    watchedFolderName = DocumentFile.fromTreeUri(applicationContext, uri)?.name ?: "Music folder"
                    folderWatchEnabled = true
                    folderWatchLastScan = 0L
                    preferences.edit()
                        .putString("watched_folder_uri", uri.toString())
                        .putString("watched_folder_name", watchedFolderName)
                        .putBoolean("folder_watch_enabled", true)
                        .putLong("folder_watch_last_scan", 0L)
                        .apply()
                    enqueueMusicImport("Folder watch setup") {
                        SoundprintMusicImportQueue.enqueueFolder(
                            applicationContext,
                            uri,
                            preferences.getBoolean("custom_artwork_enabled", true)
                        )
                    }
                }
            }
            val singleFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                if (uri != null) pendingArtworkImport = PendingArtworkImport { useArtwork ->
                    enqueueMusicImport("File import") {
                        SoundprintMusicImportQueue.enqueueFiles(applicationContext, listOf(uri), useArtwork)
                    }
                }
            }
            val filesPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                if (uris.isNotEmpty()) pendingArtworkImport = PendingArtworkImport { useArtwork ->
                    enqueueMusicImport("File import") {
                        SoundprintMusicImportQueue.enqueueFiles(applicationContext, uris, useArtwork)
                    }
                }
            }
            val backupSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                if (uri != null) scope.launch {
                    val result = runCatchingSuspend {
                        withContext(Dispatchers.IO) {
                            contentResolver.openOutputStream(uri)?.use { libraryRepository.writeBackup(it) }
                                ?: error("Android could not create the backup file.")
                        }
                    }
                    libraryStatus = result.fold({ "Library backup saved." }, { "Backup failed: ${it.message ?: "Unknown error"}" })
                }
            }
            val backupOpener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) scope.launch {
                    val result = runCatchingSuspend {
                        withContext(Dispatchers.IO) {
                            contentResolver.openInputStream(uri)?.use { libraryRepository.restoreBackup(it, false) }
                                ?: error("Android could not open this backup.")
                        }
                    }
                    if (result.isSuccess) {
                        val scan = runScanWithoutSwallowingCancellation { withContext(Dispatchers.IO) { libraryRepository.rescanAllSavedSources() } }
                        refreshLibraryState()
                        libraryStatus = "Backup restored (${result.getOrNull()} records). " +
                            scan.fold({ "Recovery scan found ${it.unavailable} unavailable locations." }, { "Recovery scan failed: ${it.message}" })
                    } else libraryStatus = "Restore failed: ${result.exceptionOrNull()?.message ?: "Unknown error"}"
                }
            }
            val tagExportSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/mpeg")) { uri ->
                val request = pendingTagExport
                pendingTagExport = null
                if (uri != null && request != null) scope.launch {
                    val result = runCatchingSuspend { exportTaggedMp3Copy(applicationContext, request, uri) }
                    libraryStatus = result.fold(
                        { "Updated MP3 copy exported." },
                        { "MP3 export failed: ${it.message ?: "Unknown error"}" }
                    )
                }
            }
            LaunchedEffect(Unit) {
                WorkManager.getInstance(applicationContext)
                    .getWorkInfosForUniqueWorkFlow("soundprint_music_import_queue")
                    .collect { workInfos ->
                        if (workInfos.isNotEmpty()) {
                            val active = workInfos.lastOrNull { it.state == WorkInfo.State.RUNNING }
                                ?: workInfos.lastOrNull { !it.state.isFinished }
                            if (active != null) {
                                val data = active.progress
                                importUiProgress = SoundprintImportUiProgress(
                                    stage = data.getString(SoundprintMusicImportQueue.PROGRESS_STAGE)
                                        ?: if (active.state == WorkInfo.State.ENQUEUED) "Queued" else "Preparing",
                                    currentTitle = data.getString(SoundprintMusicImportQueue.PROGRESS_TITLE).orEmpty(),
                                    total = data.getInt(SoundprintMusicImportQueue.PROGRESS_TOTAL, 0),
                                    visited = data.getInt(SoundprintMusicImportQueue.PROGRESS_VISITED, 0),
                                    imported = data.getInt(SoundprintMusicImportQueue.PROGRESS_IMPORTED, 0),
                                    updated = data.getInt(SoundprintMusicImportQueue.PROGRESS_UPDATED, 0),
                                    skipped = data.getInt(SoundprintMusicImportQueue.PROGRESS_SKIPPED, 0),
                                    errors = data.getInt(SoundprintMusicImportQueue.PROGRESS_ERRORS, 0),
                                    waiting = active.state == WorkInfo.State.ENQUEUED
                                )
                                libraryStatus = "Music import is running in the background. You can safely leave Soundprint."
                            } else {
                                importUiProgress = null
                                val latest = workInfos.last()
                                val seenBefore = preferences.getString("last_handled_import_work", null) == latest.id.toString()
                                if (!seenBefore) preferences.edit().putString("last_handled_import_work", latest.id.toString()).apply()
                                when (if (seenBefore) null else latest.state) {
                                    WorkInfo.State.SUCCEEDED -> {
                                        refreshLibraryState()
                                        val output = latest.outputData
                                        libraryStatus = "Import complete: ${output.getInt("imported", 0)} added, ${output.getInt("updated", 0)} updated, ${output.getInt("skipped", 0)} skipped, ${output.getInt("errors", 0)} errors."
                                    }
                                    WorkInfo.State.FAILED -> libraryStatus = "Music import failed: ${latest.outputData.getString("error") ?: "Open the notification for details."}"
                                    WorkInfo.State.CANCELLED -> libraryStatus = "Music import was cancelled."
                                    else -> Unit
                                }
                            }
                        } else {
                            importUiProgress = null
                        }
                    }
            }
            LaunchedEffect(Unit) {
                refreshLibraryState()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                val queuedImports = WorkManager.getInstance(applicationContext)
                    .getWorkInfosForUniqueWorkFlow("soundprint_music_import_queue")
                    .first()
                if (queuedImports.any { !it.state.isFinished }) {
                    libraryStatus = "Music import is continuing in the background."
                } else {
                    // Opening the app must stay cheap: the database is the library index.
                    // Folder traversal is an explicit user action from Settings/Home > Rescan.
                    SoundprintImportNotifications(applicationContext).dismiss()
                    libraryStatus = "Library ready. Watched folders are checked automatically while Soundprint is open."
                }
            }
            val lifecycleOwner = LocalLifecycleOwner.current
            LaunchedEffect(watchedFolderUri, folderWatchEnabled, autoAddNewSongs) {
                val watchedUri = watchedFolderUri ?: return@LaunchedEffect
                if (!folderWatchEnabled) return@LaunchedEffect
                while (true) {
                    delay(30_000L)
                    if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) continue
                    val importsRunning = WorkManager.getInstance(applicationContext)
                        .getWorkInfosForUniqueWorkFlow("soundprint_music_import_queue").first()
                        .any { !it.state.isFinished }
                    if (importsRunning) continue
                    folderWatchMutex.withLock {
                        val result = runCatchingSuspend {
                            withContext(Dispatchers.IO) {
                                libraryRepository.scanWatchedFolder(Uri.parse(watchedUri), autoAddNewSongs)
                            }
                        }
                        result.fold(
                            { scan ->
                                folderWatchLastScan = System.currentTimeMillis()
                                preferences.edit().putLong("folder_watch_last_scan", folderWatchLastScan).apply()
                                refreshLibraryState()
                                if (scan.imported > 0) libraryStatus = "Folder watch added ${scan.imported} new songs."
                            },
                            { error ->
                                libraryStatus = "Folder watch failed: ${error.message ?: "Check folder access."}"
                                if (error is SecurityException) {
                                    folderWatchEnabled = false
                                    preferences.edit().putBoolean("folder_watch_enabled", false).apply()
                                }
                            }
                        )
                    }
                }
            }

            suspend fun scanWatchedFolderNow() {
                val raw = watchedFolderUri ?: return
                val importsRunning = WorkManager.getInstance(applicationContext)
                    .getWorkInfosForUniqueWorkFlow("soundprint_music_import_queue").first()
                    .any { !it.state.isFinished }
                if (importsRunning) {
                    libraryStatus = "A music import is still running. Folder scan will wait until it finishes."
                    return
                }
                folderWatchMutex.withLock {
                    val result = runCatchingSuspend {
                        withContext(Dispatchers.IO) {
                            libraryRepository.scanWatchedFolder(Uri.parse(raw), autoAddNewSongs)
                        }
                    }
                    result.fold(
                        { scan ->
                            folderWatchLastScan = System.currentTimeMillis()
                            preferences.edit().putLong("folder_watch_last_scan", folderWatchLastScan).apply()
                            refreshLibraryState()
                            libraryStatus = "Folder scan complete: ${scan.imported} added, ${scan.updated} updated."
                        },
                        { error -> libraryStatus = "Folder scan failed: ${error.message ?: "Unknown error"}" }
                    )
                }
            }

            LaunchedEffect(libraryStatus) {
                val status = libraryStatus.trim()
                if (status.isBlank() ||
                    status.startsWith("Checking your saved music") ||
                    status.startsWith("Library ready") ||
                    status.startsWith("Music import is continuing") ||
                    status.startsWith("Music import is running")
                ) return@LaunchedEffect

                val normalized = status.lowercase()
                when {
                    normalized.contains("failed") || normalized.contains("couldn't") ||
                        normalized.contains("could not") || normalized.contains("failure") ->
                        SoundprintToast.error(status)
                    normalized.contains("complete") || normalized.contains("saved.") ||
                        normalized.contains("restored") || normalized.contains("removed.") ||
                        normalized.contains("cleared.") ->
                        SoundprintToast.success(status)
                    else -> SoundprintToast.info(status)
                }
            }

            val darkTheme = when (themeMode) {
                "light" -> false
                "system" -> isSystemInDarkTheme()
                else -> true
            }
            val colors = palette(selectedPalette, darkTheme)
            DisposableEffect(colors.background, darkTheme) {
                val decorView = activityWindow.decorView
                decorView.setBackgroundColor(colors.background.toArgb())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    // Android 15 enforces edge-to-edge for targetSdk 35+. The bars
                    // must be transparent; the window background supplies the color
                    // visible behind the system-bar insets.
                    activityWindow.statusBarColor = android.graphics.Color.TRANSPARENT
                    activityWindow.navigationBarColor = android.graphics.Color.TRANSPARENT
                } else {
                    activityWindow.statusBarColor = colors.background.toArgb()
                    activityWindow.navigationBarColor = colors.background.toArgb()
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    activityWindow.isNavigationBarContrastEnforced = false
                }
                WindowCompat.getInsetsController(activityWindow, decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
                onDispose { }
            }

            MaterialTheme(
                colorScheme = if (darkTheme) darkColorScheme(
                    primary = colors.primary, onPrimary = colors.onPrimary,
                    background = colors.background, onBackground = colors.foreground,
                    surface = colors.surface, onSurface = colors.foreground,
                    surfaceVariant = colors.surface2, onSurfaceVariant = colors.muted,
                    outline = colors.border
                ) else lightColorScheme(
                    primary = colors.primary, onPrimary = colors.onPrimary,
                    background = colors.background, onBackground = colors.foreground,
                    surface = colors.surface, onSurface = colors.foreground,
                    surfaceVariant = colors.surface2, onSurfaceVariant = colors.muted,
                    outline = colors.border
                )
            ) {
                CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyMedium.copy(fontFamily = SoundprintFontFamily)) {
                Box(Modifier.fillMaxSize()) {
                SoundprintApp(
                    libraryManager = libraryManager,
                    accountReturnRequestCount = accountReturnRequestCount.intValue,
                    themeMode = themeMode,
                    onThemeModeChange = { mode -> themeMode = mode; preferences.edit().putString("theme", mode).apply() },
                    selectedPalette = selectedPalette,
                    onPaletteChange = { name -> selectedPalette = name; preferences.edit().putString("palette", name).apply() },
                    enteredLibrary = enteredLibrary,
                    onEnterLibrary = {
                        enteredLibrary = true
                        preferences.edit().putBoolean("has_seen_launch_landing", true).apply()
                    },
                    libraryTrackCount = libraryTrackCount,
                    librarySources = librarySources,
                    libraryStatus = libraryStatus,
                    watchedFolderName = watchedFolderName,
                    folderWatchEnabled = folderWatchEnabled,
                    folderWatchLastScan = folderWatchLastScan,
                    autoAddNewSongs = autoAddNewSongs,
                    onAutoAddNewSongsChange = { enabled ->
                        autoAddNewSongs = enabled
                        preferences.edit().putBoolean("auto_add_new_songs", enabled).apply()
                    },
                    onChooseWatchedFolder = { watchedFolderPicker.launch(null) },
                    onToggleFolderWatch = { enabled ->
                        folderWatchEnabled = enabled
                        preferences.edit().putBoolean("folder_watch_enabled", enabled).apply()
                    },
                    onForgetWatchedFolder = {
                        watchedFolderUri = null
                        watchedFolderName = null
                        folderWatchEnabled = false
                        folderWatchLastScan = 0L
                        preferences.edit().remove("watched_folder_uri").remove("watched_folder_name")
                            .putBoolean("folder_watch_enabled", false).putLong("folder_watch_last_scan", 0L).apply()
                    },
                    onScanWatchedFolder = { scope.launch { scanWatchedFolderNow() } },
                    onExportTaggedMp3 = { request ->
                        pendingTagExport = request
                        val safeName = (request.title.ifBlank { request.track.title })
                            .replace(Regex("[\\\\/:*?\"<>|]+"), "_")
                        tagExportSaver.launch("$safeName.mp3")
                    },
                    onImportMusic = { folderPicker.launch(null) },
                    // Include generic-MIME files too; the importer validates audio MIME types and extensions.
                    onImportFiles = { filesPicker.launch(arrayOf("audio/*", "application/octet-stream")) },
                    onImportSingle = { singleFilePicker.launch(arrayOf("audio/*", "application/octet-stream")) },
                    onImportLink = { linkImportDialogOpen = true },
                    onRescan = {
                        scope.launch {
                            val result = runScanWithoutSwallowingCancellation { withContext(Dispatchers.IO) { libraryRepository.rescanAllSavedSources() } }
                            refreshLibraryState()
                            libraryStatus = result.fold(
                                { "Rescan complete: ${it.imported} added, ${it.updated} updated, ${it.unavailable} unavailable, ${it.errors.size} errors." },
                                { "Rescan failed: ${it.message ?: "Unknown error"}" }
                            )
                        }
                    },
                    onRemoveSource = { sourceUri ->
                        scope.launch {
                            withContext(Dispatchers.IO) { libraryRepository.removeSource(sourceUri) }
                            refreshLibraryState()
                            libraryStatus = "Music location removed. Original audio files were not deleted."
                        }
                    },
                    onBackup = { backupSaver.launch("soundprint-library-backup.json") },
                    onRestore = { backupOpener.launch(arrayOf("application/json")) },
                    onClearLibrary = {
                        scope.launch {
                            withContext(Dispatchers.IO) { libraryManager.clearAllLibraryData() }
                            refreshLibraryState()
                            libraryStatus = "Library cleared. Your original audio files are still on your device and can be imported again."
                        }
                    }
                )
                SoundprintToastHost(
                    Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp)
                )
                importUiProgress?.let { progress ->
                    SoundprintImportProgressBanner(
                        progress = progress,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = 68.dp, start = 16.dp, end = 16.dp)
                    )
                }
                if (linkImportDialogOpen) {
                    SoundprintDialog(
                        title = "Import from link",
                        icon = SoundprintIcons.Link,
                        onDismissRequest = { linkImportDialogOpen = false; linkImportText = "" },
                        subtitle = "Paste a direct link to an audio file. Public MP3, M4A, FLAC, WAV and other supported audio URLs work.",
                        content = {
                            BasicTextField(
                                value = linkImportText,
                                onValueChange = { linkImportText = it },
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.46f), RoundedCornerShape(13.dp))
                                    .padding(14.dp),
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp),
                                decorationBox = { inner ->
                                    Box {
                                        if (linkImportText.isBlank()) Text("https://example.com/song.mp3", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                                        inner()
                                    }
                                }
                            )
                        },
                        actions = {
                            TextButton(onClick = { linkImportDialogOpen = false; linkImportText = "" }) { Text("Cancel") }
                            TextButton(onClick = {
                                val url = linkImportText.trim()
                                runCatching {
                                    SoundprintMusicImportQueue.enqueueUrl(applicationContext, url, preferences.getBoolean("custom_artwork_enabled", true))
                                }.onSuccess {
                                    libraryStatus = "Link import queued. SoundPrint will download the audio and add it to your library."
                                    linkImportDialogOpen = false
                                    linkImportText = ""
                                }.onFailure {
                                    libraryStatus = it.message ?: "Could not queue this audio link."
                                }
                            }) { Text("Import", fontWeight = FontWeight.SemiBold) }
                        }
                    )
                }
                pendingArtworkImport?.let { pending ->
                    Dialog(
                        onDismissRequest = { finishArtworkImportChoice(pending, false) },
                        properties = DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 28.dp).widthIn(max = 360.dp)
                                .clip(RoundedCornerShape(26.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(22.dp)
                        ) {
                            Box(
                                Modifier.size(46.dp).clip(RoundedCornerShape(15.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)),
                                contentAlignment = Alignment.Center
                            ) {
                                SoundprintIcon(SoundprintIcons.Image, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            Text(
                                "A little more cover.",
                                modifier = Modifier.padding(top = 16.dp),
                                fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                                letterSpacing = (-0.45).sp, color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Add Soundprint artwork to songs missing a cover?",
                                modifier = Modifier.padding(top = 6.dp),
                                fontSize = 13.sp, lineHeight = 19.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                Modifier.fillMaxWidth().padding(top = 22.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { finishArtworkImportChoice(pending, false) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Not now", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Box(
                                    Modifier.weight(1.25f).height(44.dp).clip(RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.primary)
                                        .clickable { finishArtworkImportChoice(pending, true) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Add artwork", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimary)
                                }
                            }
                        }
                    }
                }
                }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAccountReturnIntent(intent)
        SoundprintSupabase.client.handleDeeplinks(intent)
    }
}

private data class TabItem(val label: String, val icon: Int)

private val tabs = listOf(
    TabItem("Home", SoundprintIcons.House),
    TabItem("Songs", SoundprintIcons.EighthNotes),
    TabItem("Albums", SoundprintIcons.Disc3),
    TabItem("Playlists", SoundprintIcons.ListMusic),
    TabItem("Discover", SoundprintIcons.AudioLines)
)

@Composable
private fun SoundprintApp(
    libraryManager: MusicLibraryManager,
    accountReturnRequestCount: Int,
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    selectedPalette: String,
    onPaletteChange: (String) -> Unit,
    enteredLibrary: Boolean,
    onEnterLibrary: () -> Unit,
    libraryTrackCount: Int,
    librarySources: List<MusicLibrarySource>,
    libraryStatus: String,
    watchedFolderName: String?,
    folderWatchEnabled: Boolean,
    folderWatchLastScan: Long,
    autoAddNewSongs: Boolean,
    onAutoAddNewSongsChange: (Boolean) -> Unit,
    onChooseWatchedFolder: () -> Unit,
    onToggleFolderWatch: (Boolean) -> Unit,
    onForgetWatchedFolder: () -> Unit,
    onScanWatchedFolder: () -> Unit,
    onExportTaggedMp3: (SoundprintTagExportRequest) -> Unit,
    onImportMusic: () -> Unit,
    onImportFiles: () -> Unit,
    onImportSingle: () -> Unit,
    onImportLink: () -> Unit,
    onRescan: () -> Unit,
    onRemoveSource: (String) -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onClearLibrary: () -> Unit
) {
    val appContext = LocalContext.current
    val clapScanner = remember { ClapLibraryScanner(appContext, libraryManager) }
    val playbackConnection = remember { SoundprintPlaybackConnection(appContext) }
    val screenScope = rememberCoroutineScope()
    DisposableEffect(clapScanner, playbackConnection) {
        onDispose {
            clapScanner.close()
            playbackConnection.close()
        }
    }
    LaunchedEffect(enteredLibrary, libraryTrackCount) {
        if (enteredLibrary && libraryTrackCount > 0 && clapScanner.autoScanEnabled()) clapScanner.startScan()
    }
    var selectedTab by remember { mutableIntStateOf(0) }
    var settingsOpen by remember { mutableStateOf(false) }
    var accountOpen by remember { mutableStateOf(false) }
    var accountReturnToSignInKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(accountReturnRequestCount) {
        if (accountReturnRequestCount > 0) {
            accountOpen = true
            accountReturnToSignInKey += 1
        }
    }
    var statsOpen by remember { mutableStateOf(false) }
    var proOpen by remember { mutableStateOf(false) }
    var proStatus by remember { mutableStateOf(SoundprintProStatus()) }
    var proMessage by remember { mutableStateOf<String?>(null) }
    val authSessionState by SoundprintSupabase.client.auth.sessionStatus.collectAsState()
    fun enforceFreeTierRestrictions() {
        disableProPlaybackOptions(appContext)
        if (selectedPalette == "Emerald" || selectedPalette == "Mono") onPaletteChange("Amber")
        if (folderWatchEnabled) onToggleFolderWatch(false)
        clapScanner.setAutoScanEnabled(false)
    }
    LaunchedEffect(authSessionState) {
        if (SoundprintProAccess.hasSession()) {
            runCatching { SoundprintProAccess.refresh() }
                .onSuccess {
                    proStatus = it
                    SoundprintProGate.updateEntitlement(it.hasPro)
                    if (!it.hasPro) enforceFreeTierRestrictions()
                }
                .onFailure {
                    proStatus = SoundprintProStatus()
                    SoundprintProGate.updateEntitlement(false)
                    enforceFreeTierRestrictions()
                }
        } else {
            proStatus = SoundprintProStatus()
            SoundprintProGate.updateEntitlement(false)
            enforceFreeTierRestrictions()
        }
    }
    LaunchedEffect(proOpen) {
        if (proOpen) {
            proMessage = "Checking Founder Access availability…"
            runCatching { SoundprintProAccess.refresh() }
                .onSuccess {
                    proStatus = it
                    SoundprintProGate.updateEntitlement(it.hasPro)
                    if (!it.hasPro) enforceFreeTierRestrictions()
                    proMessage = null
                }
                .onFailure {
                    proStatus = SoundprintProStatus()
                    SoundprintProGate.updateEntitlement(false)
                    enforceFreeTierRestrictions()
                    proMessage = it.message ?: "Could not verify Pro access. Try again."
                }
        }
    }
    var nowPlayingOpen by remember { mutableStateOf(false) }
    var tagEditorOpen by remember { mutableStateOf(false) }
    var resumePrompt by remember { mutableStateOf<PlaybackResumePrompt?>(null) }
    val playbackQueueStore = remember { PlaybackQueueStore(appContext) }
    LaunchedEffect(playbackConnection) {
        repeat(120) {
            val controller = playbackConnection.controller
            if (controller != null && controller.mediaItemCount > 0) {
                playbackQueueStore.pendingResumePrompt()?.let { resumePrompt = it }
                return@LaunchedEffect
            }
            delay(250L)
        }
    }
    var homeAlbumToOpen by remember { mutableStateOf<String?>(null) }

    // Keep Android system Back aligned with the app's own screen hierarchy.
    // A nested screen always unwinds first; only the Home/root screen exits.
    BackHandler {
        when {
            nowPlayingOpen -> nowPlayingOpen = false
            tagEditorOpen -> {
                tagEditorOpen = false
                settingsOpen = true
            }
            proOpen -> proOpen = false
            statsOpen -> statsOpen = false
            settingsOpen -> settingsOpen = false
            accountOpen -> accountOpen = false
            selectedTab != 0 -> selectedTab = 0
            else -> (appContext as? Activity)?.finish()
        }
    }

    LaunchedEffect(playbackConnection) {
        while (playbackConnection.controller == null) delay(100)
        val activePlayer = playbackConnection.controller ?: return@LaunchedEffect
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                // Auto-advance must not pull the full-screen player over whatever the person is doing.
                if (mediaItem != null && reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) nowPlayingOpen = true
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying && activePlayer.currentMediaItem != null) nowPlayingOpen = true
            }
        }
        activePlayer.addListener(listener)
        if (activePlayer.isPlaying && activePlayer.currentMediaItem != null) nowPlayingOpen = true
        try { awaitCancellation() } finally { activePlayer.removeListener(listener) }
    }

    Surface(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars), color = MaterialTheme.colorScheme.background) {
        if (!enteredLibrary) {
            PublicHomeScreen(onContinue = onEnterLibrary)
        } else Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val activeScreen = when {
                    nowPlayingOpen -> "now-playing"
                    tagEditorOpen -> "tag-editor"
                    proOpen -> "pro"
                    statsOpen -> "stats"
                    settingsOpen -> "settings"
                    accountOpen -> "account"
                    else -> "tab-$selectedTab"
                }
                AnimatedContent(
                    targetState = activeScreen,
                    transitionSpec = {
                        (fadeIn(tween(220)) + slideInHorizontally(
                            animationSpec = tween(260, easing = FastOutSlowInEasing)
                        ) { it / 18 }) togetherWith
                            (fadeOut(tween(160)) + slideOutHorizontally(
                                animationSpec = tween(200, easing = FastOutSlowInEasing)
                            ) { -it / 24 }) using SizeTransform(clip = false)
                    },
                    label = "soundprint-screen-transition"
                ) { screen ->
                    when (screen) {
                        "now-playing" -> SoundprintNowPlayingScreen(
                            controller = playbackConnection.controller,
                            library = libraryManager,
                            scanner = clapScanner,
                            playback = playbackConnection,
                            onBack = { nowPlayingOpen = false }
                        )
                        "tag-editor" -> SoundprintTagEditorScreen(
                            library = libraryManager,
                            onBack = { tagEditorOpen = false; settingsOpen = true },
                            onExportMp3 = onExportTaggedMp3
                        )
                        "pro" -> ProScreen(
                            onBack = { proOpen = false },
                            entitled = proStatus.hasPro,
                            onClaimFounder = {
                                screenScope.launch {
                                    proMessage = "Checking eligibility and claiming your founder spot…"
                                    runCatching { SoundprintProAccess.claimFounder() }
                                        .onSuccess { result ->
                                            if (result.optBoolean("claimed") || result.optBoolean("already_entitled")) {
                                                val source = result.optString("source").takeUnless { it == "null" }
                                                proStatus = proStatus.copy(hasPro = true, source = source, isFounder = source == "founder")
                                                SoundprintProGate.updateEntitlement(true)
                                            }
                                            runCatching { SoundprintProAccess.refresh() }
                                                .onSuccess { proStatus = it; SoundprintProGate.updateEntitlement(it.hasPro) }
                                            proMessage = when {
                                                result.optBoolean("already_entitled") -> "This account already has active Pro access."
                                                result.optBoolean("claimed") -> "Founder Access claimed. Lifetime Pro is now active on this account."
                                                result.optString("reason") == "sold_out" -> "All founder spots have been claimed."
                                                else -> "Founder Access could not be claimed. Please check eligibility and try again."
                                            }
                                        }
                                        .onFailure { proMessage = it.message ?: "Founder Access could not be claimed. Please try again." }
                                }
                            }
                        )
                        "stats" -> ListeningStatsScreen(libraryManager = libraryManager, onBack = { statsOpen = false })
                        "account" -> SoundprintAccountScreen(
                            onBack = { accountOpen = false },
                            resetToSignInKey = accountReturnToSignInKey
                        )
                        "settings" -> SettingsScreen(
                            libraryManager = libraryManager,
                            scanner = clapScanner,
                            themeMode = themeMode,
                            onThemeModeChange = onThemeModeChange,
                            selectedPalette = selectedPalette,
                            onPaletteChange = { name -> if (name == "Emerald" || name == "Mono") { if (SoundprintProGate.require("Emerald & Monochrome palettes")) onPaletteChange(name) } else onPaletteChange(name) },
                            onBack = { settingsOpen = false },
                            onOpenPro = { settingsOpen = false; proOpen = true },
                            libraryTrackCount = libraryTrackCount,
                            librarySources = librarySources,
                            libraryStatus = libraryStatus,
                            watchedFolderName = watchedFolderName,
                            folderWatchEnabled = folderWatchEnabled,
                            folderWatchLastScan = folderWatchLastScan,
                            autoAddNewSongs = autoAddNewSongs,
                            onAutoAddNewSongsChange = { enabled -> if (!enabled || SoundprintProGate.require("Folder auto-watch")) onAutoAddNewSongsChange(enabled) },
                            onChooseWatchedFolder = { if (SoundprintProGate.require("Folder auto-watch")) onChooseWatchedFolder() },
                            onToggleFolderWatch = { enabled -> if (!enabled || SoundprintProGate.require("Folder auto-watch")) onToggleFolderWatch(enabled) },
                            onForgetWatchedFolder = onForgetWatchedFolder,
                            onScanWatchedFolder = { if (SoundprintProGate.require("Folder auto-watch")) onScanWatchedFolder() },
                            onOpenTagEditor = { settingsOpen = false; tagEditorOpen = true },
                            onImportMusic = onImportMusic,
                            onImportFiles = onImportFiles,
                            onRescan = onRescan,
                            onRemoveSource = onRemoveSource,
                            onBackup = onBackup,
                            onRestore = onRestore,
                            onClearLibrary = {
                                screenScope.launch {
                                    clapScanner.clearLibraryState()
                                    playbackConnection.clearQueue()
                                    onClearLibrary()
                                }
                            },
                            onClearHistory = {
                                screenScope.launch {
                                    withContext(Dispatchers.IO) { libraryManager.clearListeningHistory() }
                                }
                            },
                        )
                        else -> when (screen.removePrefix("tab-").toIntOrNull() ?: 0) {
                            0 -> HomeScreen(
                                libraryManager = libraryManager,
                                playback = playbackConnection,
                                libraryTrackCount = libraryTrackCount,
                                onSettings = { settingsOpen = true },
                                onImportAudio = onImportFiles,
                                onImportSingle = onImportSingle,
                                onImportLink = onImportLink,
                                onImportFolder = onImportMusic,
                                onRescan = onRescan,
                                onListeningStats = { statsOpen = true },
                                onAccount = { accountOpen = true },
                                onOpenPro = { proOpen = true },
                                onOpenSongs = { selectedTab = 1 },
                                onOpenTab = { selectedTab = it },
                                onOpenAlbum = { albumId -> homeAlbumToOpen = albumId; selectedTab = 2 }
                            )
                            1 -> LibraryScreen("Songs", "Your music, all in one place.", SoundprintIcons.EighthNotes, libraryManager, playbackConnection, onImportFiles) { selectedTab = 0 }
                            2 -> SoundprintAlbumsScreen(
                                libraryManager, playbackConnection, onImportFiles, libraryTrackCount,
                                initialAlbumId = homeAlbumToOpen,
                                onInitialAlbumConsumed = { homeAlbumToOpen = null }
                            )
                            3 -> SoundprintPlaylistsScreen(libraryManager, playbackConnection, clapScanner)
                            else -> ClapDiscoverScreen(libraryManager, clapScanner, playbackConnection) { selectedTab = 0 }
                        }
                    }
                }
            }

            if (!nowPlayingOpen && !tagEditorOpen) Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f))
            ) {
                SoundprintMiniPlayer(
                    controller = playbackConnection.controller,
                    library = libraryManager,
                    onOpen = { nowPlayingOpen = true },
                    onToggle = { playbackConnection.toggle() },
                    onNext = { playbackConnection.next() },
                    onClear = { playbackConnection.clearQueue() }
                )
                Spacer(Modifier.height(0.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 672.dp)
                        .align(Alignment.CenterHorizontally)
                        .padding(
                            start = 4.dp, end = 4.dp, top = 0.dp,
                            bottom = 6.dp
                        ),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEachIndexed { index, item ->
                        val selected = selectedTab == index
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    settingsOpen = false
                                    accountOpen = false
                                    statsOpen = false
                                    selectedTab = index
                                }
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .height(32.dp)
                                    .widthIn(min = 56.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (!settingsOpen && !statsOpen && selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        else Color.Transparent
                                    )
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                SoundprintIcon(item.icon, contentDescription = item.label, modifier = Modifier.size(20.dp), tint = if (!settingsOpen && !statsOpen && selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                item.label,
                                fontSize = 11.sp,
                                lineHeight = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                color = if (!settingsOpen && selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        SoundprintProGate.lockedFeature?.let { feature ->
            AlertDialog(
                onDismissRequest = { SoundprintProGate.lockedFeature = null },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SoundprintIcon(SoundprintIcons.Crown, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(9.dp))
                        Text("$feature is a Pro feature")
                    }
                },
                text = { Text("Unlock Soundprint Pro to use $feature. Your music library and basic playback stay free.") },
                confirmButton = {
                    TextButton(onClick = {
                        SoundprintProGate.lockedFeature = null
                        proOpen = true
                    }) { Text("Explore Pro") }
                },
                dismissButton = {
                    TextButton(onClick = { SoundprintProGate.lockedFeature = null }) { Text("Not now") }
                }
            )
        }
        resumePrompt?.let { prompt ->
            AlertDialog(
                onDismissRequest = {
                    playbackQueueStore.clearPendingResumePrompt()
                    resumePrompt = null
                },
                title = { Text("Pick up where you left off?") },
                text = {
                    Text("${prompt.title} · ${prompt.artist}\nYou stopped at ${formatPlaybackResumeTime(prompt.positionMs)}. Would you like to continue or restart this song?")
                },
                confirmButton = {
                    TextButton(onClick = {
                        playbackQueueStore.clearPendingResumePrompt()
                        resumePrompt = null
                        playbackConnection.controller?.play()
                        nowPlayingOpen = true
                    }) { Text("Continue") }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            playbackQueueStore.clearPendingResumePrompt()
                            resumePrompt = null
                            playbackConnection.controller?.seekTo(0L)
                            playbackConnection.controller?.play()
                            nowPlayingOpen = true
                        }) { Text("Restart") }
                        TextButton(onClick = {
                            playbackQueueStore.clearPendingResumePrompt()
                            resumePrompt = null
                        }) { Text("Not now") }
                    }
                }
            )
        }
    }
}

private fun formatPlaybackResumeTime(positionMs: Long): String {
    val totalSeconds = (positionMs.coerceAtLeast(0L) / 1000L).toInt()
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

@Composable
private fun PublicHomeScreen(onContinue: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = 24.dp,
                    start = 20.dp,
                    end = 20.dp
                ),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.soundprint_icon),
                contentDescription = null,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "Soundprint",
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.6).sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(
                    bottom = 32.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Your music.",
                fontSize = 38.sp,
                lineHeight = 44.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-1.1).sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                "Your rhythm.",
                fontSize = 38.sp,
                lineHeight = 44.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-1.1).sp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(28.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 320.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onContinue),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Continue to Soundprint",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    libraryManager: MusicLibraryManager,
    playback: SoundprintPlaybackConnection,
    libraryTrackCount: Int,
    onSettings: () -> Unit,
    onImportAudio: () -> Unit,
    onImportSingle: () -> Unit,
    onImportLink: () -> Unit,
    onImportFolder: () -> Unit,
    onRescan: () -> Unit,
    onListeningStats: () -> Unit,
    onAccount: () -> Unit,
    onOpenPro: () -> Unit,
    onOpenSongs: () -> Unit,
    onOpenTab: (Int) -> Unit,
    onOpenAlbum: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var editGenreSuggestions by remember { mutableStateOf(emptyList<SoundprintGenreSuggestion>()) }
    var searchOpen by remember { mutableStateOf(false) }
    var refreshVersion by remember { mutableIntStateOf(0) }
    var playlistTrack by remember { mutableStateOf<MusicTrack?>(null) }
    var playlistChoices by remember { mutableStateOf(emptyList<ManagedPlaylist>()) }
    val homeScope = rememberCoroutineScope()
    var newPlaylistName by remember { mutableStateOf("") }
    var editTrack by remember { mutableStateOf<MusicTrack?>(null) }
    var editTitle by remember { mutableStateOf("") }
    var editArtist by remember { mutableStateOf("") }
    var editAlbum by remember { mutableStateOf("") }
    var editGenre by remember { mutableStateOf("") }
    LaunchedEffect(editTrack?.id) {
        val selected = editTrack
        editGenreSuggestions = if (selected == null) emptyList() else withContext(Dispatchers.IO) {
            val lyrics = context.getSharedPreferences("soundprint_now_playing", 0)
                .getString("lyrics:${selected.id}", "").orEmpty()
            SoundprintGenreInference.suggest(libraryManager.clapMoodProfile(selected), lyrics)
        }
    }
    var recycleConfirmationTrack by remember { mutableStateOf<MusicTrack?>(null) }
    var selectedArtist by remember { mutableStateOf<String?>(null) }
    if (searchOpen) {
        HomeSearchScreen(libraryManager, playback) { searchOpen = false }
        return
    }
    val openArtist = selectedArtist
    if (openArtist != null) {
        // Reading the whole library is a database job; keep it off the UI thread.
        val artistTracks by androidx.compose.runtime.produceState(initialValue = emptyList<MusicTrack>(), openArtist) {
            value = withContext(Dispatchers.IO) { libraryManager.artistTracks(openArtist) }
        }
        ArtistHomeScreen(
            artist = openArtist,
            tracks = artistTracks,
            playback = playback,
            onBack = { selectedArtist = null }
        )
        return
    }
    // Loaded on Dispatchers.IO: querying the full library on the UI thread freezes large libraries.
    var loadedLibraryTracks by remember { mutableStateOf<List<MusicTrack>?>(null) }
    LaunchedEffect(libraryTrackCount, refreshVersion) {
        loadedLibraryTracks = withContext(Dispatchers.IO) {
            libraryManager.allSongs(sort = TrackSort.DATE_ADDED, ascending = false)
        }
    }
    val libraryTracks = loadedLibraryTracks.orEmpty()
    var recentlyPlayedTracks by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var homeAlbums by remember { mutableStateOf<List<LibraryAlbum>>(emptyList()) }
    var selectedShortcut by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(libraryTrackCount, refreshVersion) {
        val homeData = withContext(Dispatchers.IO) {
            libraryManager.recentlyPlayed(8) to libraryManager.albums().take(10)
        }
        recentlyPlayedTracks = homeData.first
        homeAlbums = homeData.second
    }
    selectedShortcut?.let { shortcut ->
        SoundprintHomeShortcutScreen(
            title = shortcut,
            libraryManager = libraryManager,
            playback = playback,
            onBack = { selectedShortcut = null }
        )
        return
    }
    val recentAddedTracks = libraryTracks.take(6)
    val shortcutTiles = listOf(
        Triple("Songs", SoundprintIcons.EighthNotes, 1),
        Triple("Albums", SoundprintIcons.Disc3, 2),
        Triple("Artists", SoundprintIcons.User, -1),
        Triple("Genres", SoundprintIcons.Tags, -1),
        Triple("Playlists", SoundprintIcons.ListMusic, 3),
        Triple("Folders", SoundprintIcons.FolderPlus, -1),
        Triple("Favorites", SoundprintIcons.Heart, -1)
    )
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 20.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Image(painterResource(R.drawable.soundprint_icon), "Soundprint", Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("YOUR LIBRARY", fontSize = 10.sp, letterSpacing = 1.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                    Text("Soundprint", fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, color = MaterialTheme.colorScheme.onBackground)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeaderAction(SoundprintIcons.UserRound, "Account", onClick = onAccount)
                HeaderAction(SoundprintIcons.ChartNoAxesColumnIncreasing, "Listening stats", onClick = onListeningStats)
                HeaderAction(SoundprintIcons.Crown, "Soundprint Pro", tint = MaterialTheme.colorScheme.primary, onClick = onOpenPro)
                HeaderAction(SoundprintIcons.Search, "Search", onClick = { searchOpen = true })
                HeaderAction(SoundprintIcons.Settings, "Settings", onClick = onSettings)
            }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (loadedLibraryTracks != null && libraryTracks.isEmpty()) item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(20.dp)
                ) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                        SoundprintIcon(SoundprintIcons.Music, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(24.dp))
                    Text("A FRESH START", fontSize = 12.sp, letterSpacing = 1.4.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text("Your music goes here.", fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(10.dp))
                    Text("Add the songs already on your device. Your library stays yours, and you can organise it into albums, playlists and favourites as you go.", fontSize = 14.sp, lineHeight = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(24.dp))
                    AddMusicMenuButton(
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        onAudioPicker = onImportAudio,
                        onSinglePicker = onImportSingle,
                         onLinkImport = onImportLink,
                        onFolderPicker = onImportFolder,
                        onRescan = onRescan
                    )
                    Spacer(Modifier.height(28.dp))
                    Spacer(Modifier.height(0.dp))
                    Spacer(Modifier.height(16.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FeatureLabel(SoundprintIcons.Music, "Your own music")
                        FeatureLabel(SoundprintIcons.ListMusic, "Playlists that fit")
                        FeatureLabel(SoundprintIcons.Headphones, "Listen offline")
                    }
                }
            } else {
                item {
                    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(4) { rowIndex ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                repeat(2) { columnIndex ->
                                    val tileIndex = rowIndex * 2 + columnIndex
                                    if (tileIndex < shortcutTiles.size) {
                                        val tile = shortcutTiles[tileIndex]
                                        Row(
                                            Modifier.weight(1f).height(56.dp)
                                                .clip(RoundedCornerShape(22.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f))
                                                .clickable {
                                                    if (tile.third >= 0) onOpenTab(tile.third) else selectedShortcut = tile.first
                                                }
                                                .padding(horizontal = 16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            SoundprintIcon(tile.second, null, Modifier.size(if (tile.first == "Songs") 22.dp else 18.dp), tint = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.width(12.dp))
                                            Text(tile.first, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                    } else {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    AddMusicMenuButton(
                        modifier = Modifier.fillMaxWidth().height(52.dp).padding(bottom = 4.dp),
                        secondary = true,
                        onAudioPicker = onImportAudio,
                        onSinglePicker = onImportSingle,
                         onLinkImport = onImportLink,
                        onFolderPicker = onImportFolder,
                        onRescan = onRescan
                    )
                }
                if (recentlyPlayedTracks.isNotEmpty()) item {
                    Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HomeSectionHeader("Recently played")
                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp)
                        ) {
                            items(recentlyPlayedTracks, key = { it.id }) { track ->
                                Column(
                                    Modifier.width(128.dp).clickable {
                                        playback.playTracks(listOf(track) + libraryTracks.filterNot { it.id == track.id })
                                    }
                                ) {
                                    NativeArtwork(track.artworkUri, track.id, Modifier.size(128.dp), 16)
                                    Text(track.title, Modifier.padding(top = 8.dp), fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                                    Text(track.artist, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                if (homeAlbums.isNotEmpty()) item {
                    Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HomeSectionHeader("Albums", onSeeAll = { onOpenTab(2) })
                        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(homeAlbums, key = { "${it.name}:${it.artist}" }) { album ->
                                Column(
                                    Modifier.width(128.dp).clickable { onOpenAlbum(album.id) }
                                ) {
                                    NativeArtwork(album.artworkUri, "${album.name}:${album.artist}", Modifier.size(128.dp), 16)
                                    Text(album.name, Modifier.padding(top = 8.dp), fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                                    Text(album.artist, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                if (recentAddedTracks.isNotEmpty()) item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HomeSectionHeader("Recently added", onSeeAll = onOpenSongs)
                        recentAddedTracks.forEachIndexed { index, track ->
                            HomeRecentlyAddedRow(
                                track = track,
                                onClick = { playback.playTracks(listOf(track) + libraryTracks.filterNot { it.id == track.id }) },
                                onAction = { action ->
                                    when (action) {
                                        "Play next" -> playback.enqueue(listOf(track), next = true)
                                        "Add to queue" -> playback.enqueue(listOf(track))
                                        "Favorite" -> { libraryManager.toggleFavorite(track.id); refreshVersion++ }
                                        "Playlist" -> homeScope.launch {
                                            playlistChoices = withContext(Dispatchers.IO) { libraryManager.playlists() }
                                            newPlaylistName = ""
                                            playlistTrack = track
                                        }
                                        "Edit" -> {
                                            editTitle = track.title
                                            editArtist = track.artist
                                            editAlbum = track.album
                                            editGenre = track.genre
                                            editTrack = track
                                        }
                                        "Artist" -> selectedArtist = track.artist.ifBlank { "Unknown artist" }
                                        "Recycle" -> recycleConfirmationTrack = track
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    playlistTrack?.let { track ->
        SoundprintDialog(
            title = "Add to playlist",
            icon = SoundprintIcons.ListMusic,
            onDismissRequest = { playlistTrack = null },
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (playlistChoices.isEmpty()) {
                        Text("You don't have a playlist yet. Create one below to add this song.")
                    } else {
                        playlistChoices.forEach { playlist ->
                            TextButton(
                                onClick = {
                                    libraryManager.addToPlaylist(playlist.id, listOf(track.id))
                                    playlistTrack = null
                                    refreshVersion++
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                SoundprintIcon(SoundprintIcons.ListMusic, null)
                                Spacer(Modifier.width(10.dp))
                                Text(playlist.name, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    androidx.compose.material3.OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("New playlist name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            actions = {
                TextButton(onClick = { playlistTrack = null }) { Text("Cancel") }
                TextButton(
                    enabled = newPlaylistName.isNotBlank(),
                    onClick = {
                        libraryManager.createPlaylist(newPlaylistName.trim(), listOf(track.id))
                        newPlaylistName = ""
                        playlistTrack = null
                        refreshVersion++
                    }
                ) { Text("Create and add", fontWeight = FontWeight.SemiBold) }
            }
        )
    }

    editTrack?.let { track ->
        SoundprintDialog(
            title = "Edit song information",
            icon = SoundprintIcons.Pencil,
            onDismissRequest = { editTrack = null },
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedTextField(editTitle, { editTitle = it }, label = { Text("Title") }, singleLine = true)
                    androidx.compose.material3.OutlinedTextField(editArtist, { editArtist = it }, label = { Text("Artist") }, singleLine = true)
                    androidx.compose.material3.OutlinedTextField(editAlbum, { editAlbum = it }, label = { Text("Album") }, singleLine = true)
                    SoundprintGenreMenu(value = editGenre, suggestions = editGenreSuggestions, onSelect = { editGenre = it })
                }
            },
            actions = {
                TextButton(onClick = { editTrack = null }) { Text("Cancel") }
                TextButton(onClick = {
                    libraryManager.updateSongMetadata(track.id, editTitle, editArtist, editAlbum, editGenre)
                    editTrack = null
                    refreshVersion++
                }) { Text("Save changes", fontWeight = FontWeight.SemiBold) }
            }
        )
    }

    recycleConfirmationTrack?.let { track ->
        SoundprintDialog(
            title = "Move song to Recycle Bin?",
            icon = SoundprintIcons.Trash2,
            onDismissRequest = { recycleConfirmationTrack = null },
            subtitle = "\"" + track.title + "\" will be removed from your library and playback queue. You can restore it from the Recycle Bin for 30 days. The original audio file on your device will not be deleted.",
            content = { Spacer(Modifier.height(1.dp)) },
            actions = {
                TextButton(onClick = { recycleConfirmationTrack = null }) { Text("Cancel") }
                TextButton(onClick = {
                    libraryManager.moveToRecycleBin(track.id)
                    playback.removeTrackFromQueue(track.id)
                    recycleConfirmationTrack = null
                    refreshVersion++
                }) { Text("Move to Recycle Bin", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) }
            }
        )
    }
}

@Composable
private fun HomeSectionHeader(title: String, onSeeAll: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp, color = MaterialTheme.colorScheme.onSurface)
        if (onSeeAll != null) {
            Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onSeeAll).padding(vertical = 4.dp, horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("See all", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SoundprintIcon(SoundprintIcons.ChevronRight, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun HomeRecentlyAddedRow(
    track: MusicTrack,
    onClick: () -> Unit,
    onAction: (String) -> Unit
) {
    var menuExpanded by remember(track.id) { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NativeArtwork(track.artworkUri, track.id, Modifier.size(48.dp), 10)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
            Text(
                track.artist + if (track.album.isNotBlank() && track.album != "Unknown album") " · ${track.album}" else "",
                fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (track.isFavorite) {
            SoundprintIcon(SoundprintIcons.Heart, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
        }
        val duration = (track.durationMs.coerceAtLeast(0L) / 1000L).toInt()
        Text("${duration / 60}:${(duration % 60).toString().padStart(2, '0')}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            androidx.compose.material3.IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(36.dp)) {
                SoundprintIcon(SoundprintIcons.EllipsisVertical, contentDescription = "Song options", modifier = Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SoundprintDropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }, width = 232.dp) {
                SoundprintMenuItem("Play next", SoundprintIcons.ListPlus) { menuExpanded = false; onAction("Play next") }
                SoundprintMenuItem("Add to queue", SoundprintIcons.ListMusic) { menuExpanded = false; onAction("Add to queue") }
                SoundprintMenuItem(if (track.isFavorite) "Remove favorite" else "Add to favorites", SoundprintIcons.Heart) { menuExpanded = false; onAction("Favorite") }
                SoundprintMenuItem("Add to playlist", SoundprintIcons.Plus) { menuExpanded = false; onAction("Playlist") }
                SoundprintMenuDivider()
                SoundprintMenuItem("Edit song info", SoundprintIcons.Pencil) { menuExpanded = false; onAction("Edit") }
                SoundprintMenuItem("Go to artist", SoundprintIcons.User) { menuExpanded = false; onAction("Artist") }
                SoundprintMenuItem("Move to Recycle Bin", SoundprintIcons.Trash2, destructive = true) { menuExpanded = false; onAction("Recycle") }
            }
        }
    }
}

@Composable
private fun SoundprintCompactTrackMenu(
    track: MusicTrack,
    onAction: (String) -> Unit
) {
    var expanded by remember(track.id) { mutableStateOf(false) }
    Box {
        androidx.compose.material3.IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(36.dp)
        ) {
            SoundprintIcon(
                SoundprintIcons.EllipsisVertical,
                contentDescription = "Song options",
                modifier = Modifier.size(19.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        SoundprintDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, width = 232.dp) {
            SoundprintMenuItem("Play next", SoundprintIcons.ListPlus) { expanded = false; onAction("Play next") }
            SoundprintMenuItem("Add to queue", SoundprintIcons.ListMusic) { expanded = false; onAction("Add to queue") }
            SoundprintMenuItem(if (track.isFavorite) "Remove favorite" else "Add to favorites", SoundprintIcons.Heart) { expanded = false; onAction("Favorite") }
            SoundprintMenuItem("Add to playlist", SoundprintIcons.Plus) { expanded = false; onAction("Playlist") }
            SoundprintMenuDivider()
            SoundprintMenuItem("Edit song info", SoundprintIcons.Pencil) { expanded = false; onAction("Edit") }
            SoundprintMenuItem("Go to artist", SoundprintIcons.User) { expanded = false; onAction("Artist") }
            SoundprintMenuItem("Move to Recycle Bin", SoundprintIcons.Trash2, destructive = true) { expanded = false; onAction("Recycle") }
        }
    }
}

@Composable
private fun SoundprintHomeShortcutScreen(
    title: String,
    libraryManager: MusicLibraryManager,
    playback: SoundprintPlaybackConnection,
    onBack: () -> Unit
) {
    val groups by androidx.compose.runtime.produceState(initialValue = emptyList<Pair<String, List<MusicTrack>>>(), title) {
        value = withContext(Dispatchers.IO) {
            val tracks = when (title) {
                "Favorites" -> libraryManager.favorites()
                else -> libraryManager.allSongs()
            }
            when (title) {
                "Artists" -> tracks.groupBy { it.artist.ifBlank { "Unknown artist" } }.toList().sortedBy { it.first.lowercase() }
                "Genres" -> tracks.filter { it.genre.isNotBlank() }.groupBy { it.genre.trim() }.toList().sortedBy { it.first.lowercase() }
                "Folders" -> tracks.groupBy { it.folder.ifBlank { "Music" } }.toList().sortedBy { it.first.lowercase() }
                else -> listOf(title to tracks)
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeaderAction(SoundprintIcons.ArrowLeft, "Back", onClick = onBack)
            Spacer(Modifier.width(8.dp))
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(groups, key = { it.first }) { (name, tracks) ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                        if (tracks.isNotEmpty()) playback.playTracks(tracks)
                    }.padding(horizontal = 8.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NativeArtwork(tracks.firstOrNull()?.artworkUri, name, Modifier.size(44.dp), if (title == "Artists") 22 else 9)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${tracks.size} ${if (tracks.size == 1) "song" else "songs"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    SoundprintIcon(SoundprintIcons.Play, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            if (groups.isEmpty()) item {
                Text("Nothing here yet.", Modifier.fillMaxWidth().padding(24.dp), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ArtistHomeScreen(
    artist: String,
    tracks: List<MusicTrack>,
    playback: SoundprintPlaybackConnection,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.IconButton(onClick = onBack) {
                SoundprintIcon(SoundprintIcons.ChevronLeft, contentDescription = "Back")
            }
            Column(Modifier.weight(1f)) {
                Text("ARTIST", fontSize = 10.sp, letterSpacing = 1.4.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(artist, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${tracks.size} songs", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (tracks.isEmpty()) {
            Text("No songs by this artist are currently in your library.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tracks, key = { it.id }) { track ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { playback.playTracks(tracks, tracks.indexOf(track)) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NativeArtwork(track.artworkUri, track.id, Modifier.size(42.dp), 9)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(track.title, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(track.album, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        SoundprintCompactTrackMenu(track) { action ->
                            when (action) {
                                "Play next" -> playback.enqueue(listOf(track), next = true)
                                "Add to queue" -> playback.enqueue(listOf(track))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeSearchScreen(library: MusicLibraryManager, playback: SoundprintPlaybackConnection, onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var songs by remember { mutableStateOf(emptyList<MusicTrack>()) }
    var albums by remember { mutableStateOf(emptyList<LibraryAlbum>()) }
    var artists by remember { mutableStateOf(emptyList<LibraryArtist>()) }
    var playlists by remember { mutableStateOf(emptyList<ManagedPlaylist>()) }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        val term = query.trim()
        if (term.isBlank()) {
            songs = emptyList(); albums = emptyList(); artists = emptyList(); playlists = emptyList()
        } else {
            loading = true
            val result = withContext(Dispatchers.IO) {
                Triple(
                    library.searchSongs(term),
                    library.albums().filter { "${it.name} ${it.artist}".contains(term, true) }.take(8),
                    Pair(
                        library.artists().filter { it.name.contains(term, true) }.take(8),
                        library.playlists().filter { it.name.contains(term, true) }.take(8)
                    )
                )
            }
            songs = result.first
            albums = result.second
            artists = result.third.first
            playlists = result.third.second
            loading = false
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            HeaderAction(SoundprintIcons.ArrowLeft, "Back", onClick = onBack)
            Spacer(Modifier.width(8.dp))
            Text("Search your music", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        }
        SearchBox(query, { query = it }, "Songs, artists, albums or playlists…")
        when {
            query.isBlank() -> Text("Search songs, artists, albums and playlists in your library.", Modifier.padding(20.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            loading -> Text("Searching…", Modifier.padding(20.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            songs.isEmpty() && albums.isEmpty() && artists.isEmpty() && playlists.isEmpty() ->
                Text("No matches found.", Modifier.padding(20.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (songs.isNotEmpty()) {
                    item { Text("SONGS · ${songs.size}", Modifier.padding(start = 4.dp, top = 8.dp), fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(songs, key = { it.id }) { track ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { playback.playTracks(songs, songs.indexOfFirst { it.id == track.id }) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            NativeArtwork(track.artworkUri, track.id, Modifier.size(42.dp), 9)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(track.title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${track.artist} · ${track.album}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            SoundprintCompactTrackMenu(track) { action ->
                                when (action) {
                                    "Play next" -> playback.enqueue(listOf(track), next = true)
                                    "Add to queue" -> playback.enqueue(listOf(track))
                                }
                            }
                        }
                    }
                }
                if (albums.isNotEmpty()) {
                    item { Text("ALBUMS", Modifier.padding(start = 4.dp, top = 12.dp), fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(albums, key = { it.id }) { album ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { playback.playTracks(album.tracks) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            NativeArtwork(album.artworkUri, album.id, Modifier.size(42.dp), 9)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(album.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                Text("${album.artist} · ${album.tracks.size} songs", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            SoundprintIcon(SoundprintIcons.Play, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                if (artists.isNotEmpty()) {
                    item { Text("ARTISTS", Modifier.padding(start = 4.dp, top = 12.dp), fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(artists, key = { it.name }) { artist ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                            val artistTracks = library.artistTracks(artist.name)
                            playback.playTracks(artistTracks)
                        }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            SoundprintIcon(SoundprintIcons.Music2, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(artist.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                Text("${artist.tracks.size} songs", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            SoundprintIcon(SoundprintIcons.Play, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                if (playlists.isNotEmpty()) {
                    item { Text("PLAYLISTS", Modifier.padding(start = 4.dp, top = 12.dp), fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(playlists, key = { it.id }) { playlist ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                            playback.playTracks(library.playlistTracks(playlist.id))
                        }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            SoundprintIcon(SoundprintIcons.ListMusic, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(playlist.name, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                            SoundprintIcon(SoundprintIcons.Play, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddMusicMenuButton(
    modifier: Modifier = Modifier,
    onAudioPicker: () -> Unit,
    onSinglePicker: () -> Unit,
    onLinkImport: () -> Unit,
    onFolderPicker: () -> Unit,
    onRescan: () -> Unit,
    secondary: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(if (secondary) 18.dp else 50.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.32f), RoundedCornerShape(if (secondary) 18.dp else 50.dp))
                .clickable { expanded = true }.padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SoundprintIcon(SoundprintIcons.FolderOpen, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text("Add music", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(7.dp))
            SoundprintIcon(SoundprintIcons.ChevronDown, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SoundprintDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, width = 304.dp) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    "ADD TO YOUR LIBRARY",
                    modifier = Modifier.padding(start = 4.dp, bottom = 3.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold
                )
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.68f))
                        .clickable { expanded = false; onAudioPicker() }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GoogleDriveMark(Modifier.size(25.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Google Drive", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Browse Drive or device files", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    SoundprintIcon(SoundprintIcons.ChevronRight, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ImportMenuRow(SoundprintIcons.FileMusic, "Choose audio files", "Select one or more tracks") { expanded = false; onAudioPicker() }
                ImportMenuRow(SoundprintIcons.FolderOpen, "Choose a folder", "Add music from a folder") { expanded = false; onFolderPicker() }
                ImportMenuRow(SoundprintIcons.Link, "Import from link", "Paste a direct audio URL") { expanded = false; onLinkImport() }
                androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.padding(vertical = 3.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
                )
                ImportMenuRow(SoundprintIcons.RefreshCw, "Rescan folders", "Check saved music locations") { expanded = false; onRescan() }
            }
        }
    }
}

@Composable
private fun ImportMenuRow(icon: Int, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(11.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            SoundprintIcon(icon, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SoundprintIcon(SoundprintIcons.ChevronRight, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GoogleDriveMark(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val s = size.width / 24f
        val green = Color(0xFF0F9D58)
        val yellow = Color(0xFFFFBA00)
        val blue = Color(0xFF4285F4)
        val left = androidx.compose.ui.graphics.Path().apply {
            moveTo(8f*s, 2f*s); lineTo(13f*s, 2f*s); lineTo(4f*s, 18f*s); lineTo(0f, 18f*s)
            close()
        }
        val right = androidx.compose.ui.graphics.Path().apply {
            moveTo(13f*s, 2f*s); lineTo(24f*s, 20f*s); lineTo(19f*s, 20f*s); lineTo(8f*s, 2f*s)
            close()
        }
        val base = androidx.compose.ui.graphics.Path().apply {
            moveTo(4f*s, 18f*s); lineTo(24f*s, 18f*s); lineTo(21f*s, 23f*s); lineTo(1f*s, 23f*s)
            close()
        }
        drawPath(left, green)
        drawPath(right, yellow)
        drawPath(base, blue)
    }
}

@Composable
private fun HeaderAction(icon: Int, description: String, tint: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit = {}) {
    Box(
        modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        SoundprintIcon(icon, description, Modifier.size(20.dp), tint = tint)
    }
}

@Composable
private fun FeatureLabel(icon: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SoundprintIcon(icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    }
}

@Composable
private fun LibraryScreen(
    title: String,
    subtitle: String,
    icon: Int,
    libraryManager: MusicLibraryManager,
    playback: SoundprintPlaybackConnection,
    onImportFiles: () -> Unit,
    onBack: () -> Unit
) {
    when (title) {
        "Songs" -> SongsScreen(libraryManager, playback, onImportFiles, onBack)
        "Albums" -> AlbumsScreen(onBack)
        else -> PlaylistsScreen(onBack)
    }
}

@Composable
private fun SongsScreen(
    libraryManager: MusicLibraryManager,
    playback: SoundprintPlaybackConnection,
    onImportFiles: () -> Unit,
    onBack: () -> Unit
) {
    SoundprintSongsLibraryScreen(libraryManager, playback, onImportFiles, onBack)
}

@Composable
private fun AlbumsScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        PageTitle("Albums", "0 albums", onBack)
        EmptyLibraryState(
            icon = SoundprintIcons.Disc3,
            title = "No albums yet",
            description = "Add music to your device and scan your library — albums are grouped from your files' tags."
        )
    }
}

@Composable
private fun PlaylistsScreen(onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var createOpen by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth()
                
                .padding(
                    start = 12.dp, end = 12.dp,
                    top = 12.dp,
                    bottom = 12.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onBack).padding(8.dp), contentAlignment = Alignment.Center) {
                SoundprintIcon(SoundprintIcons.ArrowLeft, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Playlists", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp, color = MaterialTheme.colorScheme.onBackground)
                Text("0 playlists", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(
                Modifier.size(36.dp).clip(CircleShape).clickable { createOpen = !createOpen },
                contentAlignment = Alignment.Center
            ) { SoundprintIcon(SoundprintIcons.Plus, "New playlist", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp)) }
        }
        Spacer(Modifier.height(0.dp))
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(19.dp)
        ) {
            SearchBox(query, { query = it }, "Search playlists…")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("SMART MIXES", fontSize = 12.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PlaylistMix(SoundprintIcons.Heart, "Liked Songs", "0 songs")
                PlaylistMix(SoundprintIcons.Clock, "Recently Played", "0 songs")
                PlaylistMix(SoundprintIcons.ListMusic, "Recently Added", "0 songs")
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Smart playlists", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Automatically updated from your listening and library", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary)
                            .clickable { createOpen = !createOpen }.padding(horizontal = 12.dp, vertical = 9.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            SoundprintIcon(SoundprintIcons.Plus, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(15.dp))
                            Text("New smart playlist", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
                if (createOpen) {
                    Text("Playlist creation will be connected to your local music library in the next implementation pass.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (query.isNotBlank()) {
                Text("No playlists match your search.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                EmptyLibraryState(
                    icon = SoundprintIcons.ListMusic,
                    title = "No playlists yet",
                    description = "Create a playlist to collect songs, or build a smart playlist that updates automatically."
                )
            }
        }
    }
}

@Composable
private fun DiscoverScreen(onBack: () -> Unit) {
    val moods = listOf(
        "Workout", "Driving", "Chill", "Focus", "Party", "Upbeat",
        "Happy", "Sad", "Romantic", "Sleep", "Late Night", "Angry",
        "Dreamy", "Peaceful", "Melancholy", "Confident", "Nostalgic", "Intense"
    )
    var selectedMood by remember { mutableStateOf<String?>(null) }
    Column(modifier = Modifier.fillMaxSize()) {
        PageTitle("Discover", "Your music, in a different light", onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column {
                Text("YOUR LIBRARY, IN A DIFFERENT LIGHT", fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Text("Find your next mood.", modifier = Modifier.padding(top = 5.dp), fontSize = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp, color = MaterialTheme.colorScheme.onBackground)
                Text("Pick a feeling or a moment. Soundprint finds the tracks in your own library that fit.", modifier = Modifier.padding(top = 4.dp), fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(
                Modifier.fillMaxWidth().padding(vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF4DAA78)))
                    Spacer(Modifier.width(8.dp))
                    Text("Your music library", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text("0 tracks", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Column {
                    Text("Choose a mood", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Tap one to jump straight to matching songs.", modifier = Modifier.padding(top = 3.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                moods.chunked(3).forEach { group ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        group.forEach { mood ->
                            MoodChip(mood, selectedMood == mood, Modifier.weight(1f)) { selectedMood = mood }
                        }
                        if (group.size < 3) repeat(3 - group.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            if (selectedMood != null) {
                Column(
                    Modifier.fillMaxWidth().padding(top = 4.dp)
                        
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
                        .padding(16.dp)
                ) {
                    Text("PICKED FOR THIS MOMENT", fontSize = 10.sp, letterSpacing = 1.3.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Text(selectedMood.orEmpty(), modifier = Modifier.padding(top = 5.dp), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text("0 tracks", modifier = Modifier.padding(top = 4.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Add music to Soundprint, then return here to explore it by mood.", modifier = Modifier.padding(top = 12.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SearchBox(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    Row(
        modifier = Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SoundprintIcon(SoundprintIcons.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(9.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BasicTextField(value, onValueChange, singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface), modifier = Modifier.fillMaxWidth())
        }
        if (value.isNotEmpty()) {
            SoundprintIcon(SoundprintIcons.X, "Clear search", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp).clickable { onValueChange("") })
        }
    }
}

@Composable
private fun UtilityButton(icon: Int, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier.height(40.dp).clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            
            .clickable(onClick = onClick).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        SoundprintIcon(icon, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SoundprintActionButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick).padding(horizontal = 17.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimary) }
}

@Composable
private fun EmptyLibraryState(icon: Int, title: String, description: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            SoundprintIcon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
        }
        Text(title, modifier = Modifier.padding(top = 15.dp), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        Text(description, modifier = Modifier.padding(top = 5.dp), fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SoundprintActionButton("Add music", Modifier.padding(top = 16.dp)) {}
    }
}

@Composable
private fun PlaylistMix(icon: Int, title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface).clickable { }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            SoundprintIcon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SoundprintIcon(SoundprintIcons.ChevronRight, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun MoodChip(mood: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier.height(37.dp).clip(RoundedCornerShape(50))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            
            .clickable(onClick = onClick).padding(horizontal = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(mood, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun PageTitle(title: String, subtitle: String, onBack: () -> Unit, showDivider: Boolean = true) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                start = 12.dp, end = 12.dp,
                top = 12.dp,
                bottom = 12.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onBack)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                SoundprintIcon(SoundprintIcons.ArrowLeft, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.3).sp, color = MaterialTheme.colorScheme.onBackground)
                if (subtitle.isNotBlank()) Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (showDivider) Spacer(Modifier.height(0.dp))
    }
}

@Composable
private fun SettingsScreen(
    libraryManager: MusicLibraryManager,
    scanner: ClapLibraryScanner,
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    selectedPalette: String,
    onPaletteChange: (String) -> Unit,
    onBack: () -> Unit,
    onOpenPro: () -> Unit,
    libraryTrackCount: Int,
    librarySources: List<MusicLibrarySource>,
    libraryStatus: String,
    onImportMusic: () -> Unit,
    onImportFiles: () -> Unit,
    onRescan: () -> Unit,
    onRemoveSource: (String) -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onClearLibrary: () -> Unit,
    onClearHistory: () -> Unit,
    watchedFolderName: String?,
    folderWatchEnabled: Boolean,
    folderWatchLastScan: Long,
    autoAddNewSongs: Boolean,
    onAutoAddNewSongsChange: (Boolean) -> Unit,
    onChooseWatchedFolder: () -> Unit,
    onToggleFolderWatch: (Boolean) -> Unit,
    onForgetWatchedFolder: () -> Unit,
    onScanWatchedFolder: () -> Unit,
    onOpenTagEditor: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val settingsContext = LocalContext.current
    val settingsScope = rememberCoroutineScope()
    val artworkPreferences = remember(settingsContext) { settingsContext.getSharedPreferences("soundprint_native_preferences", 0) }
    val playbackPrefs = remember(settingsContext) { settingsContext.getSharedPreferences("soundprint_playback_options", 0) }
    var gapless by remember { mutableStateOf(playbackPrefs.getBoolean("gapless_enabled", true)) }
    var fadeOnPause by remember { mutableStateOf(playbackPrefs.getBoolean("fade_on_pause", false)) }
    var loudness by remember { mutableStateOf(playbackPrefs.getBoolean("loudness_leveling", true)) }
    var crossfade by remember { mutableIntStateOf(playbackPrefs.getInt("crossfade_seconds", 0).coerceIn(0, 8)) }
    var resume by remember { mutableStateOf(playbackPrefs.getBoolean("resume_playback", true)) }
    var rememberPosition by remember { mutableStateOf(playbackPrefs.getBoolean("remember_position", true)) }
    var seekSeconds by remember { mutableIntStateOf(playbackPrefs.getInt("double_tap_seek_seconds", 10).coerceIn(5, 60)) }
    var replaceQueue by remember { mutableStateOf(playbackPrefs.getBoolean("replace_queue_on_play", false)) }
    var customArtwork by remember { mutableStateOf(artworkPreferences.getBoolean("custom_artwork_enabled", true)) }
    val scanState by scanner.state.collectAsState()
    var backgroundMoodScan by remember { mutableStateOf(scanner.autoScanEnabled()) }
    LaunchedEffect(scanner) { scanner.refreshSummary() }
    DisposableEffect(artworkPreferences) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "custom_artwork_enabled") {
                customArtwork = artworkPreferences.getBoolean("custom_artwork_enabled", true)
            }
        }
        artworkPreferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { artworkPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    var ignoreShortTracks by remember { mutableStateOf(playbackPrefs.getBoolean("ignore_short_tracks", false)) }
    var clearDialog by remember { mutableStateOf(false) }
    var historyDialog by remember { mutableStateOf(false) }
    var helpDialog by remember { mutableStateOf(false) }
    var recycleBinOpen by remember { mutableStateOf(false) }
    var recycleBinEntries by remember { mutableStateOf(emptyList<RecycleBinEntry>()) }
    val needle = query.trim().lowercase()
    fun matches(vararg words: String): Boolean =
        needle.isEmpty() || needle.split(Regex("\\s+")).filter(String::isNotBlank)
            .all { token -> words.any { it.lowercase().contains(token) } }
    val settingRequesters = remember {
        listOf(
            "theme", "palette", "gapless", "fade", "loudness", "equalizer", "crossfade", "mood-scan",
            "resume", "remember-position", "double-tap", "replace-queue", "auto-add",
            "folder-watch", "custom-artwork", "your-music", "rescan", "tag-editor",
            "history", "backup", "restore", "ignore-clips", "clear-library",
            "replay-tutorial", "help-info", "pro", "version"
        ).associateWith { BringIntoViewRequester() }
    }
    var pendingSettingNavigation by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pendingSettingNavigation) {
        val target = pendingSettingNavigation ?: return@LaunchedEffect
        query = ""
        delay(120L)
        settingRequesters[target]?.bringIntoView()
        pendingSettingNavigation = null
    }
    val settingSearchEntries = listOf(
        Triple("Theme", "theme", "appearance light dark system"),
        Triple("Colour palette", "palette", "appearance colors colours amber blue emerald mono lavender purple aurora teal turquoise sakura rose pink cherry blossom"),
        Triple("Gapless playback", "gapless", "audio seamless track transitions"),
        Triple("Fade on pause", "fade", "audio pause stop fade"),
        Triple("Loudness leveling", "loudness", "audio volume consistent"),
        Triple("Equalizer", "equalizer", "audio eq bass treble sound"),
        Triple("Crossfade", "crossfade", "audio transition seconds"),
        Triple("Background mood scan", "mood-scan", "music intelligence background offline CLAP analysis progress"),
        Triple("Resume playback", "resume", "playback startup launch restore last song"),
        Triple("Remember position", "remember-position", "playback continue progress position"),
        Triple("Double-tap to seek", "double-tap", "playback skip seconds now playing"),
        Triple("Replace queue on play", "replace-queue", "queue playlist clear append"),
        Triple("Auto-add newly discovered songs", "auto-add", "queue import newly found audio"),
        Triple("Folder auto-watch", "folder-watch", "queue folder monitor scan"),
        Triple("Custom artwork", "custom-artwork", "library covers artwork"),
        Triple("Your music", "your-music", "library add folder import audio"),
        Triple("Rescan folders", "rescan", "library scan saved locations"),
        Triple("Audio tag editor", "tag-editor", "library edit title artist album genre metadata"),
        Triple("Playback history", "history", "library recently played listening statistics clear"),
        Triple("Backup library", "backup", "library export save json"),
        Triple("Restore backup", "restore", "library import json recover"),
        Triple("Ignore clips under 30s", "ignore-clips", "library short tracks ringtones notifications"),
        Triple("Clear library", "clear-library", "library remove delete music"),
        Triple("Replay tutorial", "replay-tutorial", "tutorial walkthrough"),
        Triple("Help & Info", "help-info", "about help guide answers"),
        Triple("Soundprint Pro", "pro", "about premium features"),
        Triple("Version", "version", "about app build")
    )
    val settingSearchResults = if (needle.isBlank()) emptyList() else settingSearchEntries.filter { (label, _, keywords) ->
        val haystack = "$label $keywords".lowercase()
        needle.split(Regex("\\s+")).filter(String::isNotBlank).all { token -> haystack.contains(token) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PageTitle("Settings", "", onBack, showDivider = false)
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SettingsSearchBox(query, { query = it })
            if (needle.isNotBlank()) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        
                ) {
                    if (settingSearchResults.isEmpty()) {
                        Text("No settings found. Try another search.", Modifier.padding(14.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        settingSearchResults.forEach { (label, key, _) ->
                            Row(
                                Modifier.fillMaxWidth().clickable { pendingSettingNavigation = key }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(label, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    when (key) {
                                        "theme", "palette" -> "Appearance"
                                        "gapless", "fade", "loudness", "equalizer", "crossfade" -> "Audio"
                                        "mood-scan" -> "Music intelligence"
                                        "resume", "remember-position", "double-tap" -> "Playback"
                                        "replace-queue", "auto-add", "folder-watch" -> "Queue"
                                        "custom-artwork", "your-music", "rescan", "tag-editor", "history", "backup", "restore", "ignore-clips", "clear-library" -> "Library"
                                        "replay-tutorial" -> "Tutorial"
                                        else -> "About"
                                    },
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            SettingsDivider()
                        }
                    }
                }
            }
            if (matches("appearance theme colour palette amber blue emerald mono lavender purple")) {
                SettingsSection("APPEARANCE") {
                    Column(Modifier.fillMaxWidth().bringIntoViewRequester(settingRequesters.getValue("theme")).padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Text("Theme", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemeChoice(Modifier.weight(1f), "System", SoundprintIcons.SunMoon, themeMode == "system") { onThemeModeChange("system") }
                            ThemeChoice(Modifier.weight(1f), "Light", SoundprintIcons.Sun, themeMode == "light") { onThemeModeChange("light") }
                            ThemeChoice(Modifier.weight(1f), "Dark", SoundprintIcons.Moon, themeMode == "dark") { onThemeModeChange("dark") }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("Colour palette", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(8.dp))
                        Column(Modifier.bringIntoViewRequester(settingRequesters.getValue("palette")), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PaletteChoice("Lavender", selectedPalette == "Lavender", Modifier.weight(1f)) { onPaletteChange("Lavender") }
                                PaletteChoice("Amber", selectedPalette == "Amber", Modifier.weight(1f)) { onPaletteChange("Amber") }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PaletteChoice("Blue & white", selectedPalette == "Blue", Modifier.weight(1f)) { onPaletteChange("Blue") }
                                PaletteChoice("Emerald", selectedPalette == "Emerald", Modifier.weight(1f)) { onPaletteChange("Emerald") }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PaletteChoice("Mono", selectedPalette == "Mono", Modifier.weight(1f)) { onPaletteChange("Mono") }
                                PaletteChoice("Aurora", selectedPalette == "Aurora", Modifier.weight(1f)) { onPaletteChange("Aurora") }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PaletteChoice("Sakura", selectedPalette == "Sakura", Modifier.weight(1f)) { onPaletteChange("Sakura") }
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            if (matches("audio gapless playback fade pause loudness leveling equalizer crossfade")) {
                SettingsSection("AUDIO") {
                    SettingsToggleRow("Gapless playback · Pro", "Seamless transitions between queued tracks", gapless, modifier = Modifier.bringIntoViewRequester(settingRequesters.getValue("gapless"))) {
                        if (SoundprintProGate.require("Gapless playback")) {
                            gapless = it
                            playbackPrefs.edit().putBoolean("gapless_enabled", it).apply()
                        }
                    }
                    SettingsToggleRow("Fade on pause", "Short fade instead of an abrupt stop", fadeOnPause, modifier = Modifier.bringIntoViewRequester(settingRequesters.getValue("fade"))) {
                        fadeOnPause = it
                        playbackPrefs.edit().putBoolean("fade_on_pause", it).apply()
                    }
                    SettingsToggleRow("Loudness leveling · Pro", "Keep playback volume more consistent", loudness, modifier = Modifier.bringIntoViewRequester(settingRequesters.getValue("loudness"))) {
                        if (SoundprintProGate.require("Pro Audio Lab")) {
                            loudness = it
                            playbackPrefs.edit().putBoolean("loudness_leveling", it).apply()
                        }
                    }
                    SettingsActionRow("Pro Audio Lab", "Unlock the 10-band EQ, bass, treble and loudness controls", "Unlock", modifier = Modifier.bringIntoViewRequester(settingRequesters.getValue("equalizer")), onClick = { SoundprintProGate.require("Pro Audio Lab") })
                    SettingsValueRow("Crossfade · Pro", if (crossfade == 0) "Off" else "$crossfade seconds", modifier = Modifier.bringIntoViewRequester(settingRequesters.getValue("crossfade"))) {
                        Row(
                            Modifier.clip(RoundedCornerShape(15.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f)).padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(0, 2, 4, 6, 8).forEach { seconds ->
                                val selected = crossfade == seconds
                                Box(
                                    Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(11.dp))
                                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable {
                                            if (SoundprintProGate.require("Gapless playback & crossfade")) {
                                                crossfade = seconds
                                                playbackPrefs.edit().putInt("crossfade_seconds", seconds).apply()
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(if (seconds == 0) "Off" else "$seconds s", fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            if (matches("playback resume position double tap seek skip seconds")) {
                SettingsSection("PLAYBACK") {
                    SettingsToggleRow("Resume playback", "Restore the last song when Soundprint opens", resume, modifier = Modifier.bringIntoViewRequester(settingRequesters.getValue("resume"))) {
                        resume = it
                        playbackPrefs.edit().putBoolean("resume_playback", it).apply()
                    }
                    SettingsToggleRow("Remember position", "Continue from where you stopped", rememberPosition, modifier = Modifier.bringIntoViewRequester(settingRequesters.getValue("remember-position"))) {
                        rememberPosition = it
                        playbackPrefs.edit().putBoolean("remember_position", it).apply()
                    }
                    Column(Modifier.fillMaxWidth().bringIntoViewRequester(settingRequesters.getValue("double-tap")).padding(horizontal = 16.dp, vertical = 16.dp)) {
                        Text("Double-tap to seek", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        Text("Choose how far to skip when you double-tap the Now Playing artwork.", modifier = Modifier.padding(top = 3.dp), fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        val seekOptions = listOf(5, 10, 15, 20, 30, 45, 60)
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)).padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            seekOptions.chunked(4).forEach { rowOptions ->
                                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    rowOptions.forEach { seconds ->
                                        val selected = seekSeconds == seconds
                                        Box(
                                            Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(13.dp))
                                                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                                .clickable { seekSeconds = seconds; playbackPrefs.edit().putInt("double_tap_seek_seconds", seconds).apply() },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("$seconds s", fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    repeat(4 - rowOptions.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                        Text("Current: $seekSeconds seconds", modifier = Modifier.padding(top = 8.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (matches("queue replace auto add newly discovered songs folder auto watch scan")) {
                SettingsSection("QUEUE") {
                    SettingsToggleRow("Replace queue on play", "Playing a list clears the previous queue", replaceQueue, modifier = Modifier.bringIntoViewRequester(settingRequesters.getValue("replace-queue"))) {
                        replaceQueue = it
                        playbackPrefs.edit().putBoolean("replace_queue_on_play", it).apply()
                    }
                    SettingsToggleRow("Auto-add newly discovered songs", "Automatically add new audio found by your watched folder", autoAddNewSongs, modifier = Modifier.bringIntoViewRequester(settingRequesters.getValue("auto-add")), onCheckedChange = onAutoAddNewSongsChange)
                    FolderAutoWatchSettingsRow(
                        folderName = watchedFolderName,
                        modifier = Modifier.bringIntoViewRequester(settingRequesters.getValue("folder-watch")),
                        enabled = folderWatchEnabled,
                        lastScan = folderWatchLastScan,
                        onChoose = onChooseWatchedFolder,
                        onToggle = onToggleFolderWatch,
                        onForget = onForgetWatchedFolder,
                        onScan = onScanWatchedFolder
                    )
                }
            }
            if (matches("music intelligence background mood scan offline clap analysis")) {
                SettingsSection("MUSIC INTELLIGENCE") {
                    if (SoundprintProGate.hasPro) {
                        SettingsToggleRow(
                            "Scan entire library in background · Pro",
                            "Build offline mood profiles for personalized Discover recommendations",
                            backgroundMoodScan,
                            modifier = Modifier.bringIntoViewRequester(settingRequesters.getValue("mood-scan"))
                        ) { enabled ->
                            backgroundMoodScan = enabled
                            scanner.setAutoScanEnabled(enabled)
                        }
                        ClapScanSettingsStatus(
                            state = scanState,
                            enabled = backgroundMoodScan,
                            onStart = { if (SoundprintProGate.require("Discover music for your mood")) scanner.startScan(allowModelDownload = true) },
                            onPause = { if (SoundprintProGate.require("Discover music for your mood")) scanner.pause() },
                            onRetry = { if (SoundprintProGate.require("Discover music for your mood")) scanner.startScan(retryFailedOnly = true, allowModelDownload = true) }
                        )
                    } else {
                        SettingsActionRow(
                            "Mood discovery · Pro",
                            "Analyze your library and discover songs by mood and activity",
                            "Unlock",
                            modifier = Modifier.bringIntoViewRequester(settingRequesters.getValue("mood-scan")),
                            onClick = { SoundprintProGate.require("Discover music for your mood") }
                        )
                    }
                }
            }
            if (matches("library custom artwork add music your music rescan folders audio tag editor playback history backup restore ignore clips clear library recycle bin")) {
                SettingsSection("LIBRARY") {
                    SettingsToggleRow(
                        "Custom artwork",
                        "Save Soundprint covers for songs without embedded art. Embedded covers always win; the player still shows a fallback cover when this is off.",
                        customArtwork,
                        modifier = Modifier.bringIntoViewRequester(settingRequesters.getValue("custom-artwork"))
                    ) { enabled ->
                        customArtwork = enabled
                        artworkPreferences.edit().putBoolean("custom_artwork_enabled", enabled).apply()
                        settingsScope.launch {
                            withContext(Dispatchers.IO) { libraryManager.setCustomArtworkEnabled(enabled) }
                        }
                    }
                    SettingsActionRow("Your music", "$libraryTrackCount songs stored on this device", "Add folder", modifier = Modifier.bringIntoViewRequester(settingRequesters.getValue("your-music")), onClick = onImportMusic)
                    SettingsActionRow("Import audio files", "Choose individual audio files from your device", "Import", onClick = onImportFiles)
                    SettingsActionRow("Rescan folders", "Recover saved locations and check for new or changed songs", "Rescan", modifier = Modifier.bringIntoViewRequester(settingRequesters.getValue("rescan")), onClick = onRescan)
                    if (libraryStatus.isNotBlank()) Text(libraryStatus, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (librarySources.isNotEmpty()) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text("IMPORTED LOCATIONS", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            librarySources.forEach { source ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(source.displayName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(if (source.lastScannedAt > 0L) "Saved music location" else "Individual audio file", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    TextButton(onClick = { onRemoveSource(source.uri) }) { Text("Remove", color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
                                }
                            }
                        }
                        SettingsDivider()
                    }
                    SettingsActionRow("Recycle Bin", "Restore songs removed from your library within 30 days", "Open") {
                        settingsScope.launch {
                            recycleBinEntries = withContext(Dispatchers.IO) { libraryManager.recycleBin() }
                            recycleBinOpen = true
                        }
                    }
                    SettingsActionRow("Audio tag editor", "Manually edit title, artist, album and genre — free for everyone", "Open", modifier = Modifier.bringIntoViewRequester(settingRequesters.getValue("tag-editor")), onClick = onOpenTagEditor)
                    SettingsActionRow("Playback history", "Clear recently played songs, play counts, listening time and streaks", "Clear", modifier = Modifier.bringIntoViewRequester(settingRequesters.getValue("history"))) { historyDialog = true }
                    SettingsActionRow("Backup library", "Export your library records, playlists, favourites and listening stats", "Backup", modifier = Modifier.bringIntoViewRequester(settingRequesters.getValue("backup")), onClick = onBackup)
                    SettingsActionRow("Restore backup", "Restore a Soundprint library backup from your device", "Restore", modifier = Modifier.bringIntoViewRequester(settingRequesters.getValue("restore")), onClick = onRestore)
                    SettingsToggleRow("Ignore clips under 30s", "Skips ringtones and notification sounds", ignoreShortTracks, modifier = Modifier.bringIntoViewRequester(settingRequesters.getValue("ignore-clips"))) {
                        ignoreShortTracks = it
                        playbackPrefs.edit().putBoolean("ignore_short_tracks", it).apply()
                    }
                    SettingsActionRow("Clear library", "Removes imported tracks, playlists, favourites and listening history; original audio files stay untouched", "Clear", destructive = true, modifier = Modifier.bringIntoViewRequester(settingRequesters.getValue("clear-library"))) { clearDialog = true }
                }
            }
            if (matches("tutorial replay tutorial")) {
                SettingsSection("TUTORIAL") {
                    SettingsActionRow("Replay tutorial", "Walk through the main Soundprint controls again", "Replay tutorial", modifier = Modifier.bringIntoViewRequester(settingRequesters.getValue("replay-tutorial")))
                }
            }
            if (matches("about help info soundprint pro about legal version privacy terms")) {
                SettingsSection("ABOUT") {
                    SettingsActionRow("Help & Info", "Learn how local playback, stats and backups work", "Open", modifier = Modifier.bringIntoViewRequester(settingRequesters.getValue("help-info"))) { helpDialog = true }
                    SettingsActionRow("Soundprint Pro", "Explore Pro features", "View Pro", modifier = Modifier.bringIntoViewRequester(settingRequesters.getValue("pro")), onClick = onOpenPro)
                    SettingsActionRow("About & Legal", "Privacy, Terms, Pro terms, refunds and copyright", "View policies")
                    SettingsActionRow("Version", "Soundprint 1.0 · offline-first player", "1.0.0", modifier = Modifier.bringIntoViewRequester(settingRequesters.getValue("version")))
                }
            }
        }
    }
    if (recycleBinOpen) AlertDialog(
        onDismissRequest = { recycleBinOpen = false },
        title = { Text("Recycle Bin") },
        text = {
            if (recycleBinEntries.isEmpty()) {
                Text("Your Recycle Bin is empty.")
            } else {
                Column(
                    Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    recycleBinEntries.forEach { entry ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.track.title, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(entry.track.artist, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            TextButton(onClick = {
                                settingsScope.launch {
                                    recycleBinEntries = withContext(Dispatchers.IO) {
                                        libraryManager.restoreFromRecycleBin(entry.track.id)
                                        libraryManager.recycleBin()
                                    }
                                }
                            }) { Text("Restore") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { recycleBinOpen = false }) { Text("Done") } }
    )
    if (clearDialog) AlertDialog(
        onDismissRequest = { clearDialog = false },
        title = { Text("Clear your library?") },
        text = { Text("This removes imported song entries, saved folder and file locations, playlists, favourites, listening history, and library analysis data from Soundprint. It does not delete or move your actual audio files. You can import them again afterwards.") },
        confirmButton = { TextButton(onClick = { clearDialog = false; onClearLibrary() }) { Text("Clear everything", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { clearDialog = false }) { Text("Cancel") } }
    )
    if (historyDialog) AlertDialog(
        onDismissRequest = { historyDialog = false },
        title = { Text("Clear playback history?") },
        text = { Text("This clears recently played songs, play counts, listening time and streaks on this device. Your audio files, library, playlists and favourites will remain.") },
        confirmButton = { TextButton(onClick = { historyDialog = false; onClearHistory() }) { Text("Clear history", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { historyDialog = false }) { Text("Cancel") } }
    )
    if (helpDialog) {
        val configuration = LocalConfiguration.current
        Dialog(
            onDismissRequest = { helpDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).widthIn(max = 512.dp)
                    .heightIn(max = configuration.screenHeightDp.dp * 0.85f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                Text("Help & Info", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text("Practical answers for using Soundprint.", modifier = Modifier.padding(top = 4.dp), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    HelpInfoTopic("Add or find your music", "Settings → Library → Your music → Add folder or Import audio files. To find music in folders you have already allowed Soundprint to access, use Rescan folders. Soundprint plays your local library; adding a file does not upload it to a music service.")
                    HelpInfoTopic("Song artwork", "After importing songs, choose whether Soundprint should show its own artwork on songs that do not already contain cover art. A song’s embedded artwork always takes priority. To remove Soundprint artwork later, open Settings → Library → Custom artwork and switch it off. To use it again, switch it on there.")
                    HelpInfoTopic("Back up your library", "Settings → Library → Backup library. Soundprint creates a .json backup containing your library’s song details, playlists, favourites, listening statistics and supported analysis data. The backup does not include your original audio files, so keep those backed up separately. Large libraries create larger files; wait for the success message and keep the backup somewhere safe, preferably another device or drive.")
                    HelpInfoTopic("Restore a backup", "Settings → Library → Restore backup → select your Soundprint backup .json file → confirm. Soundprint restores the saved library records, playlists, favourites and supported analysis data. Original audio files are not included, so restored tracks may need their original folders or files to be selected again. Back up your current library first if you want to keep it. Only restore a backup you trust.")
                    HelpInfoTopic("Do I need internet?", "No internet is needed to play audio already saved in your library, browse local songs, or use most playback controls. Internet is needed for online lyric lookup and other features that fetch information from online services. Offline, a lookup may fail or show no result; try again when connected. Your local audio stays on your device during normal playback.")
                    HelpInfoTopic("Lyrics not showing?", "Open Now Playing → Lyrics. Check the song title and artist are correct, then retry lookup while connected to the internet. Incorrect metadata can return lyrics for the wrong version. Lyrics may not be available for every song.")
                    HelpInfoTopic("Song info and mood labels", "Edit a song’s details if its title, artist or album is wrong. Mood analysis can take time and some tracks may not be analysed successfully. If you set a mood manually, that choice is saved on this device and should take priority over automatic suggestions.")
                    HelpInfoTopic("Listening stats", "Home → Listening Stats (chart icon) to review your listening history. Stats are based on listening activity recorded by Soundprint on this device; restoring a backup can restore the stats included in that backup.")
                    HelpInfoTopic("Playback volume", "The volume slider inside Soundprint adjusts player volume. Your device’s hardware/system volume is controlled separately.")
                    HelpInfoTopic("Still stuck?", "Try closing and reopening Soundprint, check that the song file is still available, and retry online features with a stable connection. Keep a recent backup before restoring or clearing your library.")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { helpDialog = false }) {
                        Text("Got it", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ClapScanSettingsStatus(
    state: ClapLibraryScanState,
    enabled: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onRetry: () -> Unit
) {
    val progress = if (state.downloadingModel) state.modelPercent.coerceIn(0, 100) / 100f else state.percent / 100f
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Offline mood scan", Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(
                when {
                    state.running && state.downloadingModel -> "Preparing model"
                    state.running -> "Scanning"
                    !enabled -> "Off"
                    state.modelDownloadRequired -> "Setup needed"
                    state.failed > 0 -> "Retry needed"
                    else -> "Ready"
                },
                fontSize = 10.sp,
                color = if (state.failed > 0 && !state.running) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().padding(top = 9.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Text(
            when {
                state.downloadingModel -> "Model download · " + state.modelPercent + "%"
                state.total > 0 -> state.completed.toString() + " of " + state.total + " tracks complete · " + state.pending + " pending · " + state.failed + " failed"
                else -> state.message.ifBlank { "No scan has run yet." }
            },
            modifier = Modifier.padding(top = 7.dp),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (state.currentTitle.isNotBlank()) {
            Text("Now scanning · " + state.currentTitle, modifier = Modifier.padding(top = 4.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
        }
        if (state.message.isNotBlank() && state.total > 0) {
            Text(state.message, modifier = Modifier.padding(top = 4.dp), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (state.failed > 0) TextButton(onClick = onRetry) { Text("Retry failed") }
            if (state.running) TextButton(onClick = onPause) { Text("Pause") }
            else TextButton(onClick = onStart) { Text(if (state.modelDownloadRequired) "Download & scan" else "Scan now") }
        }
        if (state.modelDownloadRequired) {
            Text("A one-time model download is required before offline mood analysis can run.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsSearchBox(value: String, onValueChange: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 2.dp)
            .heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SoundprintIcon(SoundprintIcons.Search, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("Search settings…", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BasicTextField(value, onValueChange, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface), modifier = Modifier.fillMaxWidth())
        }
        if (value.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Text("×", modifier = Modifier.clickable { onValueChange("") }, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsToggleRow(label: String, description: String, checked: Boolean, modifier: Modifier = Modifier, onCheckedChange: (Boolean) -> Unit) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 10.dp)) {
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, lineHeight = 18.sp)
                Text(description, modifier = Modifier.padding(top = 4.dp), fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
        SettingsDivider()
    }
}

@Composable
private fun FolderAutoWatchSettingsRow(
    folderName: String?,
    enabled: Boolean,
    lastScan: Long,
    modifier: Modifier = Modifier,
    onChoose: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onForget: () -> Unit,
    onScan: () -> Unit
) {
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text("Folder auto-watch · Pro", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                val detail = if (folderName.isNullOrBlank()) {
                    "Choose a folder to check every 30 seconds while Soundprint is open."
                } else {
                    "${folderName} · ${if (enabled) "Watching" else "Paused"}" +
                        if (lastScan > 0L) " · Last scan ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(lastScan))}" else " · Not scanned yet"
                }
                Text(detail, modifier = Modifier.padding(top = 4.dp), fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (folderName.isNullOrBlank()) {
                TextButton(onClick = onChoose) { Text("Choose", fontSize = 12.sp) }
            } else {
                Column(horizontalAlignment = Alignment.End) {
                    Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                        TextButton(onClick = onScan) { Text("Scan", fontSize = 10.sp) }
                        TextButton(onClick = { onToggle(!enabled) }) { Text(if (enabled) "Pause" else "Resume", fontSize = 10.sp) }
                    }
                    TextButton(onClick = onForget) { Text("Forget folder", fontSize = 10.sp, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        SettingsDivider()
    }
}

@Composable
private fun SettingsActionRow(
    label: String,
    description: String,
    action: String,
    destructive: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, lineHeight = 18.sp)
                Text(description, modifier = Modifier.padding(top = 4.dp), fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(action, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, maxLines = 1)
        }
        SettingsDivider()
    }
}

@Composable
private fun SettingsValueRow(label: String, description: String, modifier: Modifier = Modifier, control: @Composable () -> Unit) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 6.dp)) {
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                Text(description, modifier = Modifier.padding(top = 4.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            control()
        }
        SettingsDivider()
    }
}

@Composable
private fun HelpInfoTopic(title: String, body: String) {
    Column {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        Text(body, modifier = Modifier.padding(top = 4.dp), fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(title, fontSize = 11.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface)) { content() }
    }
}

@Composable
private fun SettingsDivider() {
    Spacer(Modifier.height(0.dp))
}

@Composable
private fun MoodCountCard(label: String, value: String) {
    Column(Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 10.dp, vertical = 9.dp).width(88.dp)) {
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(value, modifier = Modifier.padding(top = 3.dp), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ThemeChoice(modifier: Modifier, label: String, icon: Int?, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = modifier.height(76.dp).clip(RoundedCornerShape(16.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
            .clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (icon != null) SoundprintIcon(icon, null, Modifier.size(20.dp), tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, modifier = Modifier.padding(top = if (icon == null) 0.dp else 6.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun PaletteChoice(name: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = when (name) {
        "Blue & white" -> listOf(Color(0xFFFFFFFF), Color(0xFF527FE2))
        "Emerald" -> listOf(Color(0xFF10271F), Color(0xFF5ACA94))
        "Mono" -> listOf(Color(0xFF141414), Color(0xFFE8E8E8))
        "Lavender" -> listOf(Color(0xFF14141B), Color(0xFFC4B5FD))
        "Aurora" -> listOf(Color(0xFF0E2426), Color(0xFF63E0D2))
        "Sakura" -> listOf(Color(0xFF291824), Color(0xFFF29AB8))
        else -> listOf(Color(0xFF24211E), Color(0xFFE6A15D))
    }
    Row(
        modifier = modifier.height(76.dp).clip(RoundedCornerShape(16.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
            .clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
            colors.forEach { color -> Box(Modifier.size(20.dp).clip(CircleShape).background(color)) }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            Text(
                when (name) {
                    "Emerald", "Mono" -> "Unlock with Pro"
                    "Amber" -> "Original warm dark theme"
                    "Lavender" -> "Charcoal with soft lavender accents"
                    "Aurora" -> "Midnight teal with aurora glow"
                    "Sakura" -> "Soft cherry blossom rose"
                    else -> "Cool blue on clean white"
                },
                fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private suspend fun <T> runScanWithoutSwallowingCancellation(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Result.failure(error)
}

private fun disableProPlaybackOptions(context: android.content.Context) {
    context.getSharedPreferences("soundprint_playback_options", 0).edit()
        .putBoolean("gapless_enabled", false)
        .putBoolean("loudness_leveling", false)
        .putBoolean("stop_after_current", false)
        .putInt("crossfade_seconds", 0)
        .remove("sleep_ends_at")
        .apply()
}
