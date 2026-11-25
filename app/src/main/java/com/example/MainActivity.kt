package com.example

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.db.TrackEntity
import com.example.ui.PhotonViewModel
import com.example.ui.components.GalaxyBackdrop
import com.example.ui.screens.EqualizerScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.MetadataEditorScreen
import com.example.ui.screens.NowPlayingScreen
import com.example.ui.screens.PhotonMiniPlayerBar
import com.example.ui.screens.PickPlaylistForTrackDialog
import com.example.ui.screens.PlaylistsScreen
import com.example.ui.screens.SettingsAndFoldersScreen
import com.example.ui.screens.SleepTimerModalDialog
import com.example.ui.screens.SmartQueueScreen
import com.example.ui.theme.LocalPhotonColors
import com.example.ui.theme.PhotonAudioTheme
import kotlinx.coroutines.launch

enum class DrawerDestination(
    val label: String,
    val description: String,
    val icon: ImageVector
) {
    LIBRARY("Library", "Swipeable All, MP3, FLAC, WAV, Opus, AAC & Ogg pages", Icons.Rounded.LibraryMusic),
    PLAYLISTS("Playlists", "Custom playlists & drag-and-drop sorting", Icons.Rounded.QueueMusic),
    SMART_QUEUE("Smart Queue", "Listening history & genre recommendations", Icons.Rounded.AutoAwesome),
    METADATA_EDITOR("Metadata Editor", "Edit tags, album covers & exact web lookup", Icons.Rounded.EditNote),
    EQUALIZER("Equalizer", "5-band graphic EQ, bass & 3D virtualizer", Icons.Rounded.Equalizer),
    SETTINGS("Settings", "Playback, scanner, exclusions, privacy & updates", Icons.Rounded.Tune)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as PhotonApplication
        val factory = PhotonViewModel.Factory(
            libraryRepository = app.libraryRepository,
            settingsRepository = app.settingsRepository,
            playerController = app.playerController,
            gitHubUpdateChecker = app.gitHubUpdateChecker
        )

        setContent {
            val viewModel: PhotonViewModel = viewModel(factory = factory)
            val settings by viewModel.settings.collectAsStateWithLifecycle()

            PhotonAudioTheme(darkTheme = settings.darkMode) {
                PhotonAudioRoot(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotonAudioRoot(viewModel: PhotonViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val monitorStatus by viewModel.monitorStatus.collectAsStateWithLifecycle()
    val tracks by viewModel.filteredSortedTracks.collectAsStateWithLifecycle()
    val allTracks by viewModel.allTracks.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val watchedFolders by viewModel.watchedFolders.collectAsStateWithLifecycle()
    val excludedFolders by viewModel.excludedFolders.collectAsStateWithLifecycle()
    val selectedPlaylistId by viewModel.selectedPlaylistId.collectAsStateWithLifecycle()
    val selectedPlaylistTracks by viewModel.selectedPlaylistTracks.collectAsStateWithLifecycle()
    val smartQueueSuggestions by viewModel.smartQueueSuggestions.collectAsStateWithLifecycle()
    val totalPlayCount by viewModel.totalPlayCount.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val editorSelectedTrackId by viewModel.editorSelectedTrackId.collectAsStateWithLifecycle()
    val editorSearchState by viewModel.editorSearchState.collectAsStateWithLifecycle()
    val availableUpdate by viewModel.availableUpdate.collectAsStateWithLifecycle()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsStateWithLifecycle()
    val updateCheckStatus by viewModel.updateCheckStatus.collectAsStateWithLifecycle()

    var currentDestination by remember { mutableStateOf(DrawerDestination.LIBRARY) }
    var libraryActivePage by remember { mutableIntStateOf(0) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var nowPlayingExpanded by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var trackForPlaylistPicker by remember { mutableStateOf<TrackEntity?>(null) }

    // Cover Art Photo Picker tracking
    var pendingCoverTrackId by remember { mutableStateOf<Long?>(null) }
    var pickedCoverOverrideUri by remember { mutableStateOf<String?>(null) }

    val navigateTo: (DrawerDestination) -> Unit = { target ->
        currentDestination = target
        scope.launch { drawerState.close() }
    }

    val openTrackInMetadataEditor: (TrackEntity) -> Unit = { track ->
        viewModel.selectTrackForEditor(track)
        nowPlayingExpanded = false
        currentDestination = DrawerDestination.METADATA_EDITOR
    }

    // Apply "Keep Screen On" setting to Activity window cleanly
    DisposableEffect(settings.keepScreenOn) {
        val window = (context as? Activity)?.window
        if (settings.keepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Close navigation drawer on back press if open
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    // Return to Library from secondary screens on back press
    BackHandler(
        enabled = !drawerState.isOpen &&
            !nowPlayingExpanded &&
            currentDestination != DrawerDestination.LIBRARY &&
            selectedPlaylistId == null
    ) {
        currentDestination = DrawerDestination.LIBRARY
    }

    // Request POST_NOTIFICATIONS politely on Android 13+ for media notification controls
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { }
    )
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    // Zero-permission SAF multi-file audio importer (MP3, FLAC, WAV)
    val importFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importAudioFiles(uris)
        }
    }

    // Zero-permission SAF watched folder picker
    val watchFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        treeUri?.let { viewModel.addWatchedFolder(it) }
    }

    // Zero-permission SAF excluded folder picker
    val excludeFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        treeUri?.let { viewModel.addExcludedSafFolder(it) }
    }

    // Zero-permission Android Photo Picker for selecting local album cover art
    val albumCoverPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val targetId = pendingCoverTrackId
        if (uri != null && targetId != null) {
            viewModel.savePickedCoverArtwork(uri = uri, trackId = targetId) { localPath ->
                pickedCoverOverrideUri = localPath
            }
        }
        pendingCoverTrackId = null
    }

    val launchImportAudio = {
        importFilesLauncher.launch(
            arrayOf(
                "audio/mpeg",
                "audio/flac",
                "audio/x-flac",
                "audio/wav",
                "audio/x-wav",
                "audio/opus",
                "audio/ogg",
                "audio/mp4",
                "audio/aac",
                "audio/x-m4a",
                "audio/*"
            )
        )
    }
    val launchWatchFolder = { watchFolderLauncher.launch(null) }
    val launchExcludeFolder = { excludeFolderLauncher.launch(null) }
    val launchPickCoverPhoto: (Long) -> Unit = { trackId ->
        pendingCoverTrackId = trackId
        albumCoverPhotoPicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    val photonColors = LocalPhotonColors.current

    // Standard Compose ModalNavigationDrawer (DrawerLayout + NavigationDrawer pattern)
    // Disable drawer drag gesture when swiping inside MP3/FLAC/WAV pages so horizontal paging feels seamless
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !nowPlayingExpanded && (currentDestination != DrawerDestination.LIBRARY || libraryActivePage == 0),
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .width(308.dp)
                    .testTag("navigation_sidebar_drawer")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    // Galaxy Photon Sidebar Header
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                            .height(142.dp)
                            .clip(RoundedCornerShape(20.dp))
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_galaxy_hero),
                            contentDescription = "Photon Audio galaxy sidebar header",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0x66040818),
                                            Color(0xE8040818)
                                        )
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = "Photon Audio",
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFECF4FF)
                            )
                            Text(
                                text = "${allTracks.size} Tracks · ${playlists.size} Playlists · Local Only",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF38E4FF)
                            )
                        }
                    }

                    Text(
                        text = "MENU",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = photonColors.photonGlow,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                    )

                    DrawerDestination.entries.forEach { destination ->
                        val selected = destination == currentDestination
                        val badgeText = when (destination) {
                            DrawerDestination.LIBRARY -> "${allTracks.size}"
                            DrawerDestination.PLAYLISTS -> "${playlists.size}"
                            DrawerDestination.SMART_QUEUE -> if (smartQueueSuggestions.isNotEmpty()) "${smartQueueSuggestions.size}" else null
                            DrawerDestination.METADATA_EDITOR -> "TAGS"
                            DrawerDestination.EQUALIZER -> if (settings.eqEnabled) "ON" else "OFF"
                            DrawerDestination.SETTINGS -> {
                                val totalExclusions = excludedFolders.size + settings.excludedFileTypes.size
                                if (totalExclusions > 0) "$totalExclusions" else null
                            }
                        }

                        NavigationDrawerItem(
                            label = {
                                Column {
                                    Text(
                                        text = destination.label,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = destination.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            selected = selected,
                            onClick = { navigateTo(destination) },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = destination.label
                                )
                            },
                            badge = badgeText?.let { count ->
                                {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (selected) {
                                            photonColors.photonGlow.copy(alpha = 0.22f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                        },
                                        border = BorderStroke(
                                            0.8.dp,
                                            if (selected) photonColors.photonGlow else photonColors.cardBorder
                                        )
                                    ) {
                                        Text(
                                            text = count,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = if (selected) photonColors.photonGlow else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .padding(NavigationDrawerItemDefaults.ItemPadding)
                                .padding(vertical = 2.dp)
                                .testTag("drawer_item_${destination.name.lowercase()}"),
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = photonColors.photonGlow.copy(alpha = 0.16f),
                                selectedIconColor = photonColors.photonGlow,
                                selectedTextColor = photonColors.photonGlow,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = photonColors.cardBorder.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    NavigationDrawerItem(
                        label = { Text("Auto-Scan Music Now", style = MaterialTheme.typography.titleSmall) },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            viewModel.scanWatchedFoldersNow()
                        },
                        icon = {
                            Icon(
                                Icons.Rounded.Radar,
                                contentDescription = "Scan music now",
                                tint = photonColors.losslessBadge
                            )
                        },
                        modifier = Modifier
                            .padding(NavigationDrawerItemDefaults.ItemPadding)
                            .testTag("drawer_scan_now_item")
                    )

                    NavigationDrawerItem(
                        label = {
                            Text(
                                text = if (playbackState.sleepTimer.isActive) {
                                    "Sleep Timer (${playbackState.sleepTimer.label})"
                                } else {
                                    "Sleep Timer"
                                },
                                style = MaterialTheme.typography.titleSmall
                            )
                        },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            showSleepTimerDialog = true
                        },
                        icon = {
                            Icon(
                                Icons.Rounded.Bedtime,
                                contentDescription = "Sleep timer",
                                tint = if (playbackState.sleepTimer.isActive) photonColors.goldBadge else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier
                            .padding(NavigationDrawerItemDefaults.ItemPadding)
                            .testTag("drawer_sleep_timer_item")
                    )
                }
            }
        }
    ) {
        GalaxyBackdrop(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                Scaffold(
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    topBar = {
                        TopAppBar(
                            navigationIcon = {
                                IconButton(
                                    onClick = { scope.launch { drawerState.open() } },
                                    modifier = Modifier.testTag("sidebar_menu_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Menu,
                                        contentDescription = "Open navigation sidebar",
                                        tint = photonColors.photonGlow,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            },
                            title = {
                                Text(
                                    text = currentDestination.label,
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            actions = {
                                IconButton(
                                    onClick = { showSleepTimerDialog = true },
                                    modifier = Modifier.testTag("top_sleep_timer_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Bedtime,
                                        contentDescription = "Sleep Timer",
                                        tint = if (playbackState.sleepTimer.isActive) {
                                            photonColors.goldBadge
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.setDarkMode(!settings.darkMode) },
                                    modifier = Modifier.testTag("top_theme_toggle_button")
                                ) {
                                    Icon(
                                        imageVector = if (settings.darkMode) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                        contentDescription = "Toggle Dark or Light Mode",
                                        tint = photonColors.photonGlow
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Transparent
                            )
                        )
                    },
                    bottomBar = {
                        if (playbackState.currentTrack != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .windowInsetsPadding(WindowInsets.navigationBars)
                            ) {
                                PhotonMiniPlayerBar(
                                    playbackState = playbackState,
                                    gestureControlsEnabled = settings.gestureControlsEnabled,
                                    onExpandClick = { nowPlayingExpanded = true },
                                    onTogglePlayPause = { viewModel.playerController.togglePlayPause() },
                                    onSkipNext = { viewModel.playerController.skipNext() },
                                    onSkipPrevious = { viewModel.playerController.skipPrevious() }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Crossfade(
                        targetState = currentDestination,
                        animationSpec = tween(durationMillis = 220),
                        label = "drawer_destination_crossfade",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) { destination ->
                        when (destination) {
                            DrawerDestination.LIBRARY -> LibraryScreen(
                                tracks = tracks,
                                totalLibraryCount = allTracks.size,
                                currentTrackId = playbackState.currentTrack?.id,
                                isPlaying = playbackState.isPlaying,
                                searchQuery = searchQuery,
                                sortOption = settings.sortOption,
                                sortDirection = settings.sortDirection,
                                monitorStatus = monitorStatus,
                                onSearchQueryChange = viewModel::updateSearchQuery,
                                onSortOptionSelect = viewModel::selectSortOption,
                                onToggleSortDirection = viewModel::toggleSortDirection,
                                onImportFilesClick = launchImportAudio,
                                onWatchFolderClick = launchWatchFolder,
                                onScanFoldersNow = viewModel::scanWatchedFoldersNow,
                                onGenerateStudioWav = viewModel::generateStarterWavTracks,
                                onPlayTrack = { list, idx -> viewModel.playTracks(list, idx) },
                                onShuffleAll = viewModel::playShuffled,
                                onReorderTracks = viewModel::persistLibraryOrder,
                                onAutoCompleteMetadata = openTrackInMetadataEditor,
                                onAddToPlaylist = { trackForPlaylistPicker = it },
                                onDeleteTrack = viewModel::deleteTrack,
                                onActivePageChanged = { libraryActivePage = it }
                            )

                            DrawerDestination.PLAYLISTS -> PlaylistsScreen(
                                playlists = playlists,
                                allLibraryTracks = allTracks,
                                selectedPlaylistId = selectedPlaylistId,
                                selectedPlaylistTracks = selectedPlaylistTracks,
                                currentTrackId = playbackState.currentTrack?.id,
                                isPlaying = playbackState.isPlaying,
                                onSelectPlaylist = viewModel::selectPlaylist,
                                onCreatePlaylist = viewModel::createPlaylist,
                                onRenamePlaylist = viewModel::renamePlaylist,
                                onDeletePlaylist = viewModel::deletePlaylist,
                                onAddTrackToPlaylist = viewModel::addTrackToPlaylist,
                                onRemoveTrackFromPlaylist = viewModel::removeTrackFromPlaylist,
                                onReorderPlaylistTracks = viewModel::persistPlaylistOrder,
                                onPlayTracks = viewModel::playTracks,
                                onShuffleTracks = viewModel::playShuffled,
                                onAutoCompleteMetadata = openTrackInMetadataEditor
                            )

                            DrawerDestination.SMART_QUEUE -> SmartQueueScreen(
                                playbackState = playbackState,
                                suggestions = smartQueueSuggestions,
                                totalPlayCount = totalPlayCount,
                                autoPlayEnabled = settings.smartQueueAutoPlay,
                                onToggleAutoPlay = viewModel::setSmartQueueAutoPlay,
                                onPlayNext = viewModel::playNextFromSmartQueue,
                                onAddToQueue = viewModel::addSuggestionToQueue,
                                onQueueAllSuggestions = viewModel::queueAllSmartSuggestions,
                                onJumpToQueueIndex = viewModel::jumpToQueueIndex,
                                onClearHistory = viewModel::clearListeningHistory
                            )

                            DrawerDestination.METADATA_EDITOR -> MetadataEditorScreen(
                                allTracks = allTracks,
                                selectedTrackId = editorSelectedTrackId,
                                currentlyPlayingTrackId = playbackState.currentTrack?.id,
                                searchState = editorSearchState,
                                pickedCoverOverrideUri = pickedCoverOverrideUri,
                                onClearPickedCoverOverride = { pickedCoverOverrideUri = null },
                                onSelectTrack = viewModel::selectTrackForEditor,
                                onSearchQueryChange = viewModel::updateEditorSearchQuery,
                                onSearchWeb = { query, track ->
                                    viewModel.searchMetadataForEditor(query = query, targetTrack = track)
                                },
                                onLaunchPhotoPickerForCover = launchPickCoverPhoto,
                                onSaveMetadata = viewModel::saveFullTrackMetadata
                            )

                            DrawerDestination.EQUALIZER -> EqualizerScreen(
                                eqEnabled = settings.eqEnabled,
                                activePresetName = settings.eqPresetName,
                                bandRatios = settings.eqBandRatios,
                                bassBoostStrength = settings.bassBoostStrength,
                                virtualizerStrength = settings.virtualizerStrength,
                                telemetry = playbackState.eqTelemetry,
                                onToggleEq = viewModel::setEqEnabled,
                                onSelectPreset = viewModel::applyEqPreset,
                                onBandChange = viewModel::updateEqBand,
                                onSpatialChange = viewModel::updateSpatialEffects
                            )

                            DrawerDestination.SETTINGS -> SettingsAndFoldersScreen(
                                settings = settings,
                                sleepTimer = playbackState.sleepTimer,
                                watchedFolders = watchedFolders,
                                excludedFolders = excludedFolders,
                                monitorStatus = monitorStatus,
                                isCheckingUpdate = isCheckingUpdate,
                                updateCheckStatus = updateCheckStatus,
                                onSetDarkMode = viewModel::setDarkMode,
                                onSetCrossfadeSeconds = viewModel::setCrossfadeSeconds,
                                onSetPlaybackSpeed = viewModel::setPlaybackSpeed,
                                onSetSkipSilenceEnabled = viewModel::setSkipSilenceEnabled,
                                onSetPauseOnAudioDisconnect = viewModel::setPauseOnAudioDisconnect,
                                onSetHandleAudioFocus = viewModel::setHandleAudioFocus,
                                onSetKeepScreenOn = viewModel::setKeepScreenOn,
                                onSetGestureControlsEnabled = viewModel::setGestureControlsEnabled,
                                onSetAutoEnrichMetadata = viewModel::setAutoEnrichMetadata,
                                onSetAutoScanEnabled = viewModel::setAutoScanEnabled,
                                onToggleExcludedFileType = viewModel::toggleExcludedFileType,
                                onStartSleepTimerMinutes = viewModel::startSleepTimerMinutes,
                                onStartSleepTimerEndOfTrack = viewModel::startSleepTimerEndOfTrack,
                                onCancelSleepTimer = viewModel::cancelSleepTimer,
                                onAddWatchedFolder = launchWatchFolder,
                                onRemoveWatchedFolder = viewModel::removeWatchedFolder,
                                onPickExcludedFolder = launchExcludeFolder,
                                onAddExcludedFolderName = viewModel::addExcludedFolderName,
                                onRemoveExcludedFolder = viewModel::removeExcludedFolder,
                                onScanFoldersNow = viewModel::scanWatchedFoldersNow,
                                onDropTestWavIntoWatchedFolder = viewModel::dropTestTrackToWatchedFolder,
                                onSaveGithubRepo = viewModel::setGithubUpdateRepo,
                                onSetCheckUpdatesOnLaunch = viewModel::setCheckUpdatesOnLaunch,
                                onCheckUpdatesNow = { viewModel.checkForGitHubUpdates(manual = true) },
                                onClearListeningHistory = viewModel::clearListeningHistory,
                                onResetSettingsToDefaults = viewModel::resetSettingsToDefaults
                            )
                        }
                    }
                }

                // Full-Screen Immersive Now Playing Overlay
                AnimatedVisibility(
                    visible = nowPlayingExpanded,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it }),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    NowPlayingScreen(
                        playbackState = playbackState,
                        spectrumFlow = viewModel.spectrumState,
                        settings = settings,
                        onCollapse = { nowPlayingExpanded = false },
                        onTogglePlayPause = { viewModel.playerController.togglePlayPause() },
                        onSeekTo = { viewModel.playerController.seekTo(it) },
                        onSkipNext = { viewModel.playerController.skipNext() },
                        onSkipPrevious = { viewModel.playerController.skipPrevious() },
                        onToggleShuffle = { viewModel.playerController.toggleShuffle() },
                        onCycleRepeat = { viewModel.playerController.cycleRepeatMode() },
                        onGetVolumeFraction = { viewModel.playerController.getVolumeFraction() },
                        onSetVolumeFraction = { viewModel.playerController.setVolumeFraction(it) },
                        onOpenSleepTimer = { showSleepTimerDialog = true },
                        onOpenEqualizer = {
                            nowPlayingExpanded = false
                            currentDestination = DrawerDestination.EQUALIZER
                        },
                        onOpenSmartQueue = {
                            nowPlayingExpanded = false
                            currentDestination = DrawerDestination.SMART_QUEUE
                        },
                        onOpenMetadataAutoComplete = openTrackInMetadataEditor,
                        onAddToPlaylist = { trackForPlaylistPicker = it }
                    )
                }
            }
        }
    }

    // GitHub New Version Available Alert Dialog (opens GitHub download page in browser)
    availableUpdate?.let { update ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpdateAlert(skipThisVersion = false) },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.SystemUpdate,
                    contentDescription = null,
                    tint = photonColors.photonGlow,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Update Available (${update.latestTag})",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${update.releaseTitle} is available on GitHub (installed: v${update.currentVersion}).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(0.8.dp, photonColors.cardBorder)
                    ) {
                        Text(
                            text = update.releaseNotes.take(420),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dismissUpdateAlert(skipThisVersion = false)
                        runCatching {
                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(update.downloadPageUrl))
                            context.startActivity(browserIntent)
                        }
                    },
                    modifier = Modifier.testTag("github_update_open_download_btn")
                ) {
                    Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Download Page")
                }
            },
            dismissButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(
                        onClick = { viewModel.dismissUpdateAlert(skipThisVersion = false) },
                        modifier = Modifier.testTag("github_update_later_btn")
                    ) {
                        Text("Later")
                    }
                    TextButton(
                        onClick = { viewModel.dismissUpdateAlert(skipThisVersion = true) },
                        modifier = Modifier.testTag("github_update_skip_btn")
                    ) {
                        Text("Skip Version")
                    }
                }
            }
        )
    }

    // Sleep Timer Modal
    if (showSleepTimerDialog) {
        SleepTimerModalDialog(
            sleepTimer = playbackState.sleepTimer,
            onSelectMinutes = viewModel::startSleepTimerMinutes,
            onSelectEndOfTrack = viewModel::startSleepTimerEndOfTrack,
            onCancelTimer = viewModel::cancelSleepTimer,
            onDismiss = { showSleepTimerDialog = false }
        )
    }

    // Add Track to Playlist Modal
    trackForPlaylistPicker?.let { track ->
        PickPlaylistForTrackDialog(
            track = track,
            playlists = playlists,
            onSelectPlaylist = { plId -> viewModel.addTrackToPlaylist(plId, track) },
            onCreateAndAdd = { name -> viewModel.createPlaylistAndAddTrack(name, track) },
            onDismiss = { trackForPlaylistPicker = null }
        )
    }
}
