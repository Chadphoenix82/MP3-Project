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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.example.data.db.TrackEntity
import com.example.ui.PhotonViewModel
import com.example.ui.components.GalaxyBackdrop
import com.example.ui.navigation.PhotonDeepLinks
import com.example.ui.navigation.PhotonNavKey
import com.example.ui.components.DeleteTrackConfirmationDialog
import com.example.ui.components.TrackActionBottomSheet
import com.example.ui.screens.ArtistAlbumDetailScreen
import com.example.ui.screens.AudioLabModalSheet
import com.example.ui.screens.CollectionDetailTarget
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryBrowserScreen
import com.example.ui.screens.LyricsInspectorScreen
import com.example.ui.screens.NowPlayingScreen
import com.example.ui.screens.PhotonMiniPlayerBar
import com.example.ui.screens.PickPlaylistForTrackDialog
import com.example.ui.screens.PlaylistsHubScreen
import com.example.ui.screens.QueueScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsAndFoldersScreen
import com.example.ui.screens.SleepTimerModalDialog
import com.example.ui.theme.LocalPhotonColors
import com.example.ui.theme.PhotonAudioTheme
import com.example.ui.theme.PhotonWallpaperConfig
import kotlinx.coroutines.launch

private enum class TopLevelTab(
    val key: PhotonNavKey,
    val label: String,
    val icon: ImageVector,
    val tag: String
) {
    HOME(PhotonNavKey.HomeKey, "Home", Icons.Rounded.Home, "nav_tab_home"),
    LIBRARY(PhotonNavKey.LibraryKey, "Library", Icons.Rounded.LibraryMusic, "nav_tab_library"),
    PLAYLISTS(PhotonNavKey.PlaylistsKey, "Playlists", Icons.AutoMirrored.Rounded.QueueMusic, "nav_tab_playlists"),
    SEARCH(PhotonNavKey.SearchKey, "Search", Icons.Rounded.Search, "nav_tab_search")
}

class MainActivity : ComponentActivity() {

    private var pendingDeepLinkTarget by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingDeepLinkTarget = extractDeepLinkTarget(intent)

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

            PhotonAudioTheme(
                darkTheme = settings.darkMode,
                cosmicSkin = settings.cosmicThemeSkin,
                customPrimaryArgb = settings.customAccentPrimaryArgb,
                customSecondaryArgb = settings.customAccentSecondaryArgb,
                customOledBlack = settings.customOledBlackBg,
                fontChoice = settings.appFont,
                deckStylePreset = settings.deckStylePreset,
                wallpaperConfig = PhotonWallpaperConfig(
                    preset = settings.wallpaperPreset,
                    customPhotoPath = settings.customWallpaperPath,
                    dimAlpha = settings.wallpaperDimAlpha
                )
            ) {
                PhotonAudioDeckRoot(
                    viewModel = viewModel,
                    pendingDeepLink = pendingDeepLinkTarget,
                    onConsumeDeepLink = { pendingDeepLinkTarget = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractDeepLinkTarget(intent)?.let { target ->
            pendingDeepLinkTarget = target
        }
    }

    private fun extractDeepLinkTarget(intent: Intent?): String? {
        if (intent == null) return null
        val extra = intent.getStringExtra(PhotonDeepLinks.EXTRA_TARGET)
        if (!extra.isNullOrBlank()) return extra
        val data = intent.data ?: return null
        if (data.scheme == "photonaudio" && data.host == "deck") {
            return when (data.path?.trim('/')) {
                "now-playing" -> PhotonDeepLinks.TARGET_NOW_PLAYING
                "library" -> PhotonDeepLinks.TARGET_LIBRARY
                else -> null
            }
        }
        return null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotonAudioDeckRoot(
    viewModel: PhotonViewModel,
    pendingDeepLink: String?,
    onConsumeDeepLink: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val monitorStatus by viewModel.monitorStatus.collectAsStateWithLifecycle()
    val tracks by viewModel.filteredSortedTracks.collectAsStateWithLifecycle()
    val allTracks by viewModel.allTracks.collectAsStateWithLifecycle()
    val recentlyAddedTracks by viewModel.recentlyAddedTracks.collectAsStateWithLifecycle()
    val availableGenres by viewModel.availableGenres.collectAsStateWithLifecycle()
    val smartShufflePreview by viewModel.smartShufflePreview.collectAsStateWithLifecycle()
    val recentlyPlayedTracks by viewModel.recentlyPlayedTracks.collectAsStateWithLifecycle()
    val mostPlayedTracks by viewModel.mostPlayedTracks.collectAsStateWithLifecycle()
    val forgottenFavorites by viewModel.forgottenFavorites.collectAsStateWithLifecycle()
    val trackPlayCountMap by viewModel.trackPlayCountMap.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val playlistCollages by viewModel.playlistCollageArtworks.collectAsStateWithLifecycle()
    val watchedFolders by viewModel.watchedFolders.collectAsStateWithLifecycle()
    val excludedFolders by viewModel.excludedFolders.collectAsStateWithLifecycle()
    val selectedPlaylistId by viewModel.selectedPlaylistId.collectAsStateWithLifecycle()
    val selectedPlaylistTracks by viewModel.selectedPlaylistTracks.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val editorSearchState by viewModel.editorSearchState.collectAsStateWithLifecycle()
    val availableUpdate by viewModel.availableUpdate.collectAsStateWithLifecycle()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsStateWithLifecycle()
    val updateCheckStatus by viewModel.updateCheckStatus.collectAsStateWithLifecycle()
    val isApiUnlocked by viewModel.isApiUnlocked.collectAsStateWithLifecycle()
    val replayGainScanProgress by viewModel.replayGainScanProgress.collectAsStateWithLifecycle()

    // User-owned Jetpack Navigation 3 SnapshotStateList back stack
    val backStack = remember { mutableStateListOf<PhotonNavKey>(PhotonNavKey.HomeKey) }
    val currentKey = backStack.lastOrNull() ?: PhotonNavKey.HomeKey
    val isTopLevel = currentKey in listOf(
        PhotonNavKey.HomeKey,
        PhotonNavKey.LibraryKey,
        PhotonNavKey.PlaylistsKey,
        PhotonNavKey.SearchKey
    )

    var nowPlayingExpanded by remember { mutableStateOf(false) }
    var showAudioLabSheet by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var trackForPlaylistPicker by remember { mutableStateOf<TrackEntity?>(null) }
    var trackIdForActionSheet by remember { mutableStateOf<Long?>(null) }
    var trackFallbackForActionSheet by remember { mutableStateOf<TrackEntity?>(null) }
    var trackPendingDeviceDelete by remember { mutableStateOf<TrackEntity?>(null) }
    var apiPasswordDialogMode by remember { mutableStateOf<ApiPasswordDialogMode?>(null) }

    val openTrackActionMenu: (TrackEntity) -> Unit = { track ->
        trackIdForActionSheet = track.id
        trackFallbackForActionSheet = track
    }

    // Cover Art & M3U8 SAF pending state
    var pendingCoverTrackId by remember { mutableStateOf<Long?>(null) }
    var pickedCoverOverrideUri by remember { mutableStateOf<String?>(null) }
    var pendingM3u8Export by remember { mutableStateOf<Pair<String, List<TrackEntity>>?>(null) }

    val switchTopLevelTab: (PhotonNavKey) -> Unit = { tabKey ->
        nowPlayingExpanded = false
        if (backStack.size != 1 || backStack.firstOrNull() != tabKey) {
            backStack.clear()
            backStack.add(tabKey)
        }
    }

    val pushRoute: (PhotonNavKey) -> Unit = { key ->
        nowPlayingExpanded = false
        if (backStack.lastOrNull() != key) {
            backStack.add(key)
        }
    }

    val popBack: () -> Unit = {
        if (backStack.size > 1) {
            backStack.removeLastOrNull()
        } else if (backStack.firstOrNull() != PhotonNavKey.HomeKey) {
            backStack.clear()
            backStack.add(PhotonNavKey.HomeKey)
        }
    }

    // Handle widget deep links (Artwork -> Expanded Now Playing Deck, Library icon -> LibraryKey)
    LaunchedEffect(pendingDeepLink) {
        when (pendingDeepLink) {
            PhotonDeepLinks.TARGET_NOW_PLAYING -> {
                nowPlayingExpanded = true
                onConsumeDeepLink()
            }
            PhotonDeepLinks.TARGET_LIBRARY -> {
                switchTopLevelTab(PhotonNavKey.LibraryKey)
                onConsumeDeepLink()
            }
        }
    }

    // Keep Screen On window flag
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

    // Predictive-friendly back handling for modal overlay
    BackHandler(enabled = nowPlayingExpanded) {
        nowPlayingExpanded = false
    }

    // Request POST_NOTIFICATIONS politely on Android 13+
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

    // Zero-permission SAF launchers
    val importFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.importAudioFiles(uris)
    }
    val watchFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri -> treeUri?.let { viewModel.addWatchedFolder(it) } }

    val excludeFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri -> treeUri?.let { viewModel.addExcludedSafFolder(it) } }

    val exportM3u8Launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/x-mpegurl")
    ) { uri ->
        val pending = pendingM3u8Export
        if (uri != null && pending != null) {
            viewModel.exportPlaylistToM3u8(context, uri, pending.first, pending.second)
        }
        pendingM3u8Export = null
    }

    val importM3u8Launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importPlaylistFromM3u8(context, uri)
        }
    }

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

    val customWallpaperPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.saveCustomWallpaperPhoto(uri)
    }

    val launchImportAudio = {
        importFilesLauncher.launch(
            arrayOf(
                "audio/mpeg", "audio/flac", "audio/x-flac",
                "audio/wav", "audio/x-wav", "audio/opus",
                "audio/ogg", "audio/mp4", "audio/aac",
                "audio/x-m4a", "audio/*"
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
    val launchPickCustomWallpaper = {
        customWallpaperPhotoPicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    val photonColors = LocalPhotonColors.current

    val topBarTitle = when (currentKey) {
        PhotonNavKey.HomeKey -> "Photon Audio"
        PhotonNavKey.LibraryKey -> "Library"
        PhotonNavKey.PlaylistsKey -> "Playlists"
        PhotonNavKey.SearchKey -> "Search"
        is PhotonNavKey.AlbumDetailKey -> currentKey.albumId
        is PhotonNavKey.ArtistDetailKey -> currentKey.artistId
        PhotonNavKey.QueueKey -> "Up Next Queue"
        is PhotonNavKey.LyricsInspectorKey -> "Lyrics & Inspector"
        PhotonNavKey.SettingsKey -> "Settings"
    }

    GalaxyBackdrop(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    TopAppBar(
                        navigationIcon = {
                            if (!isTopLevel) {
                                IconButton(
                                    onClick = popBack,
                                    modifier = Modifier.testTag("top_bar_back_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = "Navigate back",
                                        tint = photonColors.photonGlow
                                    )
                                }
                            }
                        },
                        title = {
                            Text(
                                text = topBarTitle,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        actions = {
                            IconButton(
                                onClick = { showAudioLabSheet = true },
                                modifier = Modifier.testTag("top_audio_lab_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Equalizer,
                                    contentDescription = "Open Audio Lab",
                                    tint = if (settings.eqEnabled) photonColors.photonGlow else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (playbackState.sleepTimer.isActive) {
                                IconButton(
                                    onClick = { showSleepTimerDialog = true },
                                    modifier = Modifier.testTag("top_sleep_timer_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Bedtime,
                                        contentDescription = "Sleep Timer Active",
                                        tint = photonColors.goldBadge
                                    )
                                }
                            }
                            if (currentKey != PhotonNavKey.SettingsKey) {
                                IconButton(
                                    onClick = { pushRoute(PhotonNavKey.SettingsKey) },
                                    modifier = Modifier.testTag("top_settings_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Settings,
                                        contentDescription = "Open Settings",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )
                },
                bottomBar = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Persistent Docked Mini-Player directly above NavigationBar
                        if (playbackState.currentTrack != null) {
                            PhotonMiniPlayerBar(
                                playbackState = playbackState,
                                gestureControlsEnabled = settings.gestureControlsEnabled,
                                onExpandClick = { nowPlayingExpanded = true },
                                onTogglePlayPause = { viewModel.playerController.togglePlayPause() },
                                onSkipNext = { viewModel.playerController.skipNext() },
                                onSkipPrevious = { viewModel.playerController.skipPrevious() },
                                onNavigateToArtist = { artist ->
                                    pushRoute(PhotonNavKey.ArtistDetailKey(artist))
                                }
                            )
                        }

                        // Persistent 4-Tab Material 3 NavigationBar
                        NavigationBar(
                            containerColor = photonColors.backdropTop.copy(alpha = 0.94f),
                            tonalElevation = 0.dp,
                            modifier = Modifier.testTag("photon_bottom_nav_bar")
                        ) {
                            TopLevelTab.entries.forEach { tab ->
                                val selected = currentKey == tab.key ||
                                    (tab == TopLevelTab.LIBRARY && (currentKey is PhotonNavKey.AlbumDetailKey || currentKey is PhotonNavKey.ArtistDetailKey))
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { switchTopLevelTab(tab.key) },
                                    icon = {
                                        Icon(
                                            imageVector = tab.icon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = tab.label,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                            ),
                                            maxLines = 1
                                        )
                                    },
                                    modifier = Modifier.testTag(tab.tag),
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = photonColors.photonGlow,
                                        selectedTextColor = photonColors.photonGlow,
                                        indicatorColor = photonColors.photonGlow.copy(alpha = 0.16f),
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                // Jetpack Navigation 3 NavDisplay with Predictive Back Support
                NavDisplay(
                    backStack = backStack,
                    onBack = popBack,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    entryProvider = entryProvider {
                        entry<PhotonNavKey.HomeKey> {
                            HomeScreen(
                                playbackState = playbackState,
                                allTracks = allTracks,
                                recentlyAddedTracks = recentlyAddedTracks,
                                mostPlayedTracks = mostPlayedTracks,
                                forgottenFavorites = forgottenFavorites,
                                availableGenres = availableGenres,
                                smartShufflePreview = smartShufflePreview,
                                preferredSmartShuffleGenres = settings.smartShuffleGenres,
                                smartShuffleRecentWeight = settings.smartShuffleRecentWeight,
                                onResumeOrExpandDeck = {
                                    if (playbackState.currentTrack != null) {
                                        nowPlayingExpanded = true
                                    } else if (recentlyAddedTracks.isNotEmpty()) {
                                        viewModel.playTracks(recentlyAddedTracks, 0, "Recently Added")
                                        nowPlayingExpanded = true
                                    }
                                },
                                onTogglePlayPause = { viewModel.playerController.togglePlayPause() },
                                onPlayTracks = { list, index, label ->
                                    viewModel.playTracks(list, index, label)
                                },
                                onOpenTrackMenu = openTrackActionMenu,
                                onLaunchSmartShuffle = viewModel::playSmartShuffle,
                                onToggleSmartShuffleGenre = viewModel::toggleSmartShuffleGenre,
                                onAddCustomSmartShuffleGenre = viewModel::addCustomSmartShuffleGenre,
                                onClearSmartShuffleGenres = viewModel::clearSmartShuffleGenres,
                                onUpdateSmartShuffleRecentWeight = viewModel::setSmartShuffleRecentWeight,
                                onSmartMix25Shuffle = viewModel::playSmartMix25Shuffle,
                                onSmartMixLossless = viewModel::playSmartMixLosslessOnly,
                                onSmartMixRecentlyImported = viewModel::playSmartMixRecentlyImported,
                                onImportAudio = launchImportAudio,
                                onGenerateStarterWavs = viewModel::generateStarterWavTracks,
                                onNavigateToAlbum = { album, artist ->
                                    pushRoute(PhotonNavKey.AlbumDetailKey(album, artist))
                                },
                                onNavigateToArtist = { artist ->
                                    pushRoute(PhotonNavKey.ArtistDetailKey(artist))
                                }
                            )
                        }

                        entry<PhotonNavKey.LibraryKey> {
                            LibraryBrowserScreen(
                                tracks = tracks,
                                allTracks = allTracks,
                                watchedFolders = watchedFolders,
                                playCountMap = trackPlayCountMap,
                                currentTrackId = playbackState.currentTrack?.id,
                                isPlaying = playbackState.isPlaying,
                                sortOption = settings.sortOption,
                                sortDirection = settings.sortDirection,
                                onSelectSortOption = viewModel::selectSortOption,
                                onToggleSortDirection = viewModel::toggleSortDirection,
                                onPlayTracks = { list, idx, src -> viewModel.playTracks(list, idx, src) },
                                onShuffleTracks = { list, src -> viewModel.playShuffled(list, src) },
                                onOpenTrackMenu = openTrackActionMenu,
                                onNavigateToAlbum = { album, artist ->
                                    pushRoute(PhotonNavKey.AlbumDetailKey(album, artist))
                                },
                                onNavigateToArtist = { artist ->
                                    pushRoute(PhotonNavKey.ArtistDetailKey(artist))
                                },
                                onImportAudio = launchImportAudio,
                                onWatchFolder = launchWatchFolder
                            )
                        }

                        entry<PhotonNavKey.PlaylistsKey> {
                            PlaylistsHubScreen(
                                playlists = playlists,
                                playlistCollages = playlistCollages,
                                allTracks = allTracks,
                                selectedPlaylistId = selectedPlaylistId,
                                selectedPlaylistTracks = selectedPlaylistTracks,
                                currentTrackId = playbackState.currentTrack?.id,
                                isPlaying = playbackState.isPlaying,
                                onSelectPlaylist = viewModel::selectPlaylist,
                                onCreatePlaylist = viewModel::createPlaylist,
                                onDeletePlaylist = viewModel::deletePlaylist,
                                onReorderPlaylistTracks = viewModel::persistPlaylistOrder,
                                onRemoveTrackFromPlaylist = viewModel::removeTrackFromPlaylist,
                                onPlayTracks = { list, idx, src -> viewModel.playTracks(list, idx, src) },
                                onShuffleTracks = { list, src -> viewModel.playShuffled(list, src) },
                                onOpenTrackMenu = openTrackActionMenu,
                                onRequestExportM3u8 = { name, list ->
                                    pendingM3u8Export = name to list
                                    val safeFile = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                                    exportM3u8Launcher.launch("${safeFile.ifBlank { "photon_playlist" }}.m3u8")
                                },
                                onRequestImportM3u8 = {
                                    importM3u8Launcher.launch(
                                        arrayOf("audio/x-mpegurl", "application/vnd.apple.mpegurl", "audio/mpegurl", "text/plain", "*/*")
                                    )
                                }
                            )
                        }

                        entry<PhotonNavKey.SearchKey> {
                            SearchScreen(
                                query = searchQuery,
                                searchHistory = settings.searchHistory,
                                allTracks = allTracks,
                                recentlyPlayedTracks = recentlyPlayedTracks,
                                playlists = playlists,
                                currentTrackId = playbackState.currentTrack?.id,
                                isPlaying = playbackState.isPlaying,
                                onQueryChange = viewModel::updateSearchQuery,
                                onCommitQuery = viewModel::commitSearchQuery,
                                onRemoveHistoryItem = viewModel::removeSearchHistoryItem,
                                onClearHistory = viewModel::clearSearchHistory,
                                onPlayTracks = { list, idx, src -> viewModel.playTracks(list, idx, src) },
                                onOpenTrackMenu = openTrackActionMenu,
                                onNavigateToAlbum = { album, artist ->
                                    pushRoute(PhotonNavKey.AlbumDetailKey(album, artist))
                                },
                                onNavigateToArtist = { artist ->
                                    pushRoute(PhotonNavKey.ArtistDetailKey(artist))
                                },
                                onOpenPlaylist = { playlistId ->
                                    viewModel.selectPlaylist(playlistId)
                                    switchTopLevelTab(PhotonNavKey.PlaylistsKey)
                                }
                            )
                        }

                        entry<PhotonNavKey.AlbumDetailKey> { key ->
                            ArtistAlbumDetailScreen(
                                target = CollectionDetailTarget.Album(key.albumId, key.preferredArtist),
                                allTracks = allTracks,
                                currentTrackId = playbackState.currentTrack?.id,
                                isPlaying = playbackState.isPlaying,
                                onBack = popBack,
                                onNavigateToArtist = { artist -> pushRoute(PhotonNavKey.ArtistDetailKey(artist)) },
                                onNavigateToAlbum = { album, artist -> pushRoute(PhotonNavKey.AlbumDetailKey(album, artist)) },
                                onPlayTracks = { list, idx -> viewModel.playTracks(list, idx, key.albumId) },
                                onShuffleTracks = { list -> viewModel.playShuffled(list, "${key.albumId} (Shuffled)") },
                                onToggleFavorite = viewModel::toggleFavorite,
                                onOpenTrackMenu = openTrackActionMenu,
                                onEditAlbumMetadata = { _, anchorTrack ->
                                    viewModel.selectTrackForEditor(anchorTrack)
                                    pushRoute(PhotonNavKey.LyricsInspectorKey(anchorTrack.id))
                                }
                            )
                        }

                        entry<PhotonNavKey.ArtistDetailKey> { key ->
                            ArtistAlbumDetailScreen(
                                target = CollectionDetailTarget.Artist(key.artistId),
                                allTracks = allTracks,
                                currentTrackId = playbackState.currentTrack?.id,
                                isPlaying = playbackState.isPlaying,
                                onBack = popBack,
                                onNavigateToArtist = { artist -> pushRoute(PhotonNavKey.ArtistDetailKey(artist)) },
                                onNavigateToAlbum = { album, artist -> pushRoute(PhotonNavKey.AlbumDetailKey(album, artist)) },
                                onPlayTracks = { list, idx -> viewModel.playTracks(list, idx, key.artistId) },
                                onShuffleTracks = { list -> viewModel.playShuffled(list, "${key.artistId} (Shuffled)") },
                                onToggleFavorite = viewModel::toggleFavorite,
                                onOpenTrackMenu = openTrackActionMenu,
                                onEditAlbumMetadata = { _, anchorTrack ->
                                    viewModel.selectTrackForEditor(anchorTrack)
                                    pushRoute(PhotonNavKey.LyricsInspectorKey(anchorTrack.id))
                                }
                            )
                        }

                        entry<PhotonNavKey.QueueKey> {
                            QueueScreen(
                                playbackState = playbackState,
                                onJumpToIndex = viewModel::jumpToQueueIndex,
                                onReorderQueue = viewModel::reorderActiveQueue,
                                onRemoveFromQueue = viewModel::removeTrackFromActiveQueue,
                                onClearUpcoming = viewModel::clearUpcomingQueue,
                                onSaveQueueAsPlaylist = viewModel::saveActiveQueueAsPlaylist,
                                onOpenTrackMenu = openTrackActionMenu
                            )
                        }

                        entry<PhotonNavKey.LyricsInspectorKey> { key ->
                            val inspectedTrack = remember(key.trackId, allTracks, playbackState.currentTrack) {
                                allTracks.firstOrNull { it.id == key.trackId } ?: playbackState.currentTrack
                            }
                            LyricsInspectorScreen(
                                track = inspectedTrack,
                                playbackState = playbackState,
                                editorSearchState = editorSearchState,
                                pickedCoverOverrideUri = pickedCoverOverrideUri,
                                onClearPickedCoverOverride = { pickedCoverOverrideUri = null },
                                onLoadLyrics = viewModel::loadTrackLyrics,
                                onFetchWebLyrics = viewModel::fetchTrackLyricsOnline,
                                onSaveCustomLyrics = viewModel::saveCustomLyrics,
                                onSeekTo = { pos -> viewModel.playerController.seekTo(pos) },
                                onUpdateWebSearchQuery = viewModel::updateEditorSearchQuery,
                                onSearchWebMetadata = { query, target ->
                                    viewModel.searchMetadataForEditor(query = query, targetTrack = target)
                                },
                                onPickCoverPhoto = launchPickCoverPhoto,
                                onSaveMetadata = { trackId, title, artist, album, trackNum, genre, publisher, releaseDate, composer, notes, artwork ->
                                    viewModel.saveFullTrackMetadata(
                                        trackId = trackId,
                                        title = title,
                                        artist = artist,
                                        album = album,
                                        trackNumber = trackNum,
                                        genre = genre,
                                        publisher = publisher,
                                        releaseDate = releaseDate,
                                        composer = composer,
                                        notes = notes,
                                        artworkUri = artwork
                                    )
                                }
                            )
                        }

                        entry<PhotonNavKey.SettingsKey> {
                            SettingsAndFoldersScreen(
                                settings = settings,
                                sleepTimer = playbackState.sleepTimer,
                                currentTrack = playbackState.currentTrack,
                                isPlaying = playbackState.isPlaying,
                                shuffleEnabled = playbackState.shuffleEnabled,
                                watchedFolders = watchedFolders,
                                excludedFolders = excludedFolders,
                                monitorStatus = monitorStatus,
                                isApiUnlocked = isApiUnlocked,
                                isCheckingUpdate = isCheckingUpdate,
                                updateCheckStatus = updateCheckStatus,
                                onRequestUnlockApi = {
                                    apiPasswordDialogMode = if (settings.hasApiPassword) {
                                        ApiPasswordDialogMode.UNLOCK
                                    } else {
                                        ApiPasswordDialogMode.CREATE
                                    }
                                },
                                onLockApi = viewModel::lockApiAccess,
                                onRequestChangeApiPassword = {
                                    apiPasswordDialogMode = ApiPasswordDialogMode.CHANGE
                                },
                                onSetDarkMode = viewModel::setDarkMode,
                                onSelectCosmicSkin = viewModel::setCosmicThemeSkin,
                                onSetCustomThemeColors = viewModel::setCustomThemeColors,
                                onSetDynamicAlbumColors = viewModel::setDynamicAlbumColorsEnabled,
                                onSelectAppFont = viewModel::setAppFont,
                                onSelectWallpaperPreset = viewModel::setWallpaperPreset,
                                onPickCustomWallpaper = launchPickCustomWallpaper,
                                onSetWallpaperDimAlpha = viewModel::setWallpaperDimAlpha,
                                onSetWidgetThemeMode = viewModel::setWidgetThemeMode,
                                onSetWidgetGlassOpacity = viewModel::setWidgetGlassOpacity,
                                onWidgetPinnedFeedback = { msg ->
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                },
                                onSetCrossfadeSeconds = viewModel::setCrossfadeSeconds,
                                onSetCrossfadeCurve = viewModel::setCrossfadeCurve,
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
                                onResetSettingsToDefaults = viewModel::resetSettingsToDefaults,
                                onApplyDeckStylePreset = viewModel::applyDeckStylePreset,
                                onImportAudio = launchImportAudio,
                                onSetMinTrackDurationSec = viewModel::setMinTrackDurationSec
                            )
                        }
                    }
                )
            }

            // Full-Screen Immersive Now Playing Deck Overlay
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
                    onOpenEqualizer = { showAudioLabSheet = true },
                    onOpenSmartQueue = { pushRoute(PhotonNavKey.QueueKey) },
                    onOpenMetadataAutoComplete = { track ->
                        viewModel.selectTrackForEditor(track)
                        pushRoute(PhotonNavKey.LyricsInspectorKey(track.id))
                    },
                    onAddToPlaylist = { trackForPlaylistPicker = it },
                    onToggleFavorite = viewModel::toggleFavorite,
                    onJumpToQueueIndex = viewModel::jumpToQueueIndex,
                    onReorderQueue = viewModel::reorderActiveQueue,
                    onRemoveQueueIndex = viewModel::removeTrackFromActiveQueue,
                    onClearUpcomingQueue = viewModel::clearUpcomingQueue,
                    onSaveQueueAsPlaylist = viewModel::saveActiveQueueAsPlaylist,
                    onSetPlaybackSpeed = viewModel::setPlaybackSpeed,
                    onSetPlaybackPitch = viewModel::setPlaybackPitch,
                    onResetTempoAndPitch = viewModel::resetTempoAndPitch,
                    onSetAbLoopStart = viewModel::setAbLoopStart,
                    onSetAbLoopEnd = viewModel::setAbLoopEnd,
                    onClearAbLoop = viewModel::clearAbLoop,
                    onUpdateTrackSoundProfile = viewModel::updateTrackSoundProfile,
                    onNavigateToArtist = { artist -> pushRoute(PhotonNavKey.ArtistDetailKey(artist)) },
                    onNavigateToAlbum = { album, artist -> pushRoute(PhotonNavKey.AlbumDetailKey(album, artist)) },
                    onOpenQueueScreen = { pushRoute(PhotonNavKey.QueueKey) },
                    onOpenLyricsInspector = { trackId ->
                        viewModel.selectTrackForEditorById(trackId)
                        pushRoute(PhotonNavKey.LyricsInspectorKey(trackId))
                    },
                    onOpenAudioLab = { showAudioLabSheet = true }
                )
            }
        }
    }

    // Audio Lab Modal Sheet (10-Band EQ, Crossfade & Gapless Engine, ReplayGain, Studio WAV Generator)
    if (showAudioLabSheet) {
        AudioLabModalSheet(
            settings = settings,
            eqTelemetry = playbackState.eqTelemetry,
            replayGainScanProgress = replayGainScanProgress,
            onDismiss = { showAudioLabSheet = false },
            onSetEqEnabled = viewModel::setEqEnabled,
            onApplyPreset = viewModel::applyEqPreset,
            onUpdateBand = viewModel::updateEqBand,
            onSaveCustomPreset = { name -> viewModel.saveCustomEqPreset(name) },
            onDeleteCustomPreset = viewModel::deleteCustomEqPreset,
            onSetPreAmpDb = viewModel::setPreAmpDb,
            onUpdateSpatial = viewModel::updateSpatialEffects,
            onSetCrossfadeSeconds = viewModel::setCrossfadeSeconds,
            onSetCrossfadeCurve = viewModel::setCrossfadeCurve,
            onSetGaplessEnabled = viewModel::setGaplessPlaybackEnabled,
            onSetReplayGainMode = viewModel::setReplayGainMode,
            onSetReplayGainTargetLufs = viewModel::setReplayGainTargetLufs,
            onSetPreventClipping = viewModel::setPreventClipping,
            onScanLibraryLoudness = { viewModel.scanLibraryLoudness(forceRescan = false) },
            onGenerateStudioWavSample = viewModel::generateStudioWavSample
        )
    }

    // GitHub New Version Available Alert Dialog
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
                    update.apkFileName?.let { apkName ->
                        val sizeMb = update.apkSizeBytes?.let { bytes ->
                            String.format(" · %.1f MB", bytes / (1024.0 * 1024.0))
                        }.orEmpty()
                        Text(
                            text = "Direct APK: $apkName$sizeMb",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = photonColors.losslessBadge
                        )
                    }
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
                val targetUrl = update.apkDownloadUrl ?: update.downloadPageUrl
                Button(
                    onClick = {
                        viewModel.dismissUpdateAlert(skipThisVersion = false)
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)))
                        }
                    },
                    modifier = Modifier.testTag("github_update_open_download_btn")
                ) {
                    Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (update.apkDownloadUrl != null) "Download APK" else "Download Page")
                }
            },
            dismissButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(
                        onClick = { viewModel.dismissUpdateAlert(skipThisVersion = false) },
                        modifier = Modifier.testTag("github_update_later_btn")
                    ) { Text("Later") }
                    TextButton(
                        onClick = { viewModel.dismissUpdateAlert(skipThisVersion = true) },
                        modifier = Modifier.testTag("github_update_skip_btn")
                    ) { Text("Skip Version") }
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

    // Universal Press-and-Hold Track Action Sheet (reacts live to Room updates like Favorite toggles)
    val activeActionSheetTrack = remember(trackIdForActionSheet, allTracks, trackFallbackForActionSheet) {
        val targetId = trackIdForActionSheet ?: return@remember null
        allTracks.firstOrNull { it.id == targetId } ?: trackFallbackForActionSheet
    }
    activeActionSheetTrack?.let { selectedTrack ->
        TrackActionBottomSheet(
            track = selectedTrack,
            onDismiss = {
                trackIdForActionSheet = null
                trackFallbackForActionSheet = null
            },
            onToggleFavorite = viewModel::toggleFavorite,
            onPlayNext = viewModel::playNextFromSmartQueue,
            onAddToQueue = viewModel::addSuggestionToQueue,
            onAddToPlaylist = { trackForPlaylistPicker = it },
            onGoToAlbum = { album, artist ->
                pushRoute(PhotonNavKey.AlbumDetailKey(album, artist))
            },
            onGoToArtist = { artist ->
                pushRoute(PhotonNavKey.ArtistDetailKey(artist))
            },
            onOpenLyricsAndTagEditor = { track ->
                viewModel.selectTrackForEditor(track)
                pushRoute(PhotonNavKey.LyricsInspectorKey(track.id))
            },
            onShareAudioFile = { track ->
                viewModel.shareTrackAudio(context, track)
            },
            onSetAsRingtone = { track ->
                viewModel.setTrackAsRingtone(context, track)
            },
            onRequestDeleteFromDevice = { track ->
                trackPendingDeviceDelete = track
            }
        )
    }

    // Delete from Device Confirmation Dialog
    trackPendingDeviceDelete?.let { doomedTrack ->
        DeleteTrackConfirmationDialog(
            track = doomedTrack,
            onConfirmDelete = {
                viewModel.deleteTrackFromDevice(context, doomedTrack)
                trackPendingDeviceDelete = null
            },
            onDismiss = { trackPendingDeviceDelete = null }
        )
    }

    // Add Track to Playlist Modal
    trackForPlaylistPicker?.let { track ->
        PickPlaylistForTrackDialog(
            track = track,
            playlists = playlists,
            onSelectPlaylist = { plId -> viewModel.addTrackToPlaylist(plId, track) },
            onCreateAndAdd = { name, desc -> viewModel.createPlaylistAndAddTrack(name, desc, track) },
            onDismiss = { trackForPlaylistPicker = null }
        )
    }

    // Password-Protected API Access Dialog
    apiPasswordDialogMode?.let { mode ->
        ApiPasswordDialog(
            mode = mode,
            onCreatePassword = { pass, confirm, onErr ->
                viewModel.createApiPassword(pass, confirm) { err ->
                    if (err == null) apiPasswordDialogMode = null else onErr(err)
                }
            },
            onUnlock = { pass, onErr ->
                viewModel.unlockApiWithPassword(pass) { err ->
                    if (err == null) apiPasswordDialogMode = null else onErr(err)
                }
            },
            onChangePassword = { curr, next, confirm, onErr ->
                viewModel.changeApiPassword(curr, next, confirm) { err ->
                    if (err == null) apiPasswordDialogMode = null else onErr(err)
                }
            },
            onDismiss = { apiPasswordDialogMode = null }
        )
    }
}

private enum class ApiPasswordDialogMode {
    CREATE,
    UNLOCK,
    CHANGE
}

@Composable
private fun ApiPasswordDialog(
    mode: ApiPasswordDialogMode,
    onCreatePassword: (String, String, (String) -> Unit) -> Unit,
    onUnlock: (String, (String) -> Unit) -> Unit,
    onChangePassword: (String, String, String, (String) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val photonColors = LocalPhotonColors.current
    var currentPass by remember(mode) { mutableStateOf("") }
    var password by remember(mode) { mutableStateOf("") }
    var confirmPass by remember(mode) { mutableStateOf("") }
    var showPassword by remember(mode) { mutableStateOf(false) }
    var errorMessage by remember(mode) { mutableStateOf<String?>(null) }

    val visualTransform = if (showPassword) VisualTransformation.None else PasswordVisualTransformation()
    val visibilityToggle: @Composable () -> Unit = {
        IconButton(onClick = { showPassword = !showPassword }) {
            Icon(
                imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                contentDescription = if (showPassword) "Hide password" else "Show password"
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = photonColors.goldBadge,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = when (mode) {
                    ApiPasswordDialogMode.CREATE -> "Set API Access Password"
                    ApiPasswordDialogMode.UNLOCK -> "Unlock Hidden API"
                    ApiPasswordDialogMode.CHANGE -> "Change API Password"
                },
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = when (mode) {
                        ApiPasswordDialogMode.CREATE -> "Create a local password to protect and hide external API controls. Stored strictly on-device using salted PBKDF2 hashing."
                        ApiPasswordDialogMode.UNLOCK -> "Enter your password to access hidden external API endpoints and lookup settings."
                        ApiPasswordDialogMode.CHANGE -> "Enter your current password and choose a new local password."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (mode == ApiPasswordDialogMode.CHANGE) {
                    OutlinedTextField(
                        value = currentPass,
                        onValueChange = {
                            currentPass = it
                            errorMessage = null
                        },
                        label = { Text("Current Password") },
                        singleLine = true,
                        visualTransformation = visualTransform,
                        trailingIcon = visibilityToggle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("api_current_password_input")
                    )
                }

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = null
                    },
                    label = {
                        Text(
                            when (mode) {
                                ApiPasswordDialogMode.UNLOCK -> "API Password"
                                ApiPasswordDialogMode.CREATE -> "New Password (min 4 chars)"
                                ApiPasswordDialogMode.CHANGE -> "New Password (min 4 chars)"
                            }
                        )
                    },
                    singleLine = true,
                    visualTransformation = visualTransform,
                    trailingIcon = visibilityToggle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("api_password_input")
                )

                if (mode == ApiPasswordDialogMode.CREATE || mode == ApiPasswordDialogMode.CHANGE) {
                    OutlinedTextField(
                        value = confirmPass,
                        onValueChange = {
                            confirmPass = it
                            errorMessage = null
                        },
                        label = { Text("Confirm Password") },
                        singleLine = true,
                        visualTransformation = visualTransform,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("api_confirm_password_input")
                    )
                }

                errorMessage?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when (mode) {
                        ApiPasswordDialogMode.CREATE -> onCreatePassword(password, confirmPass) { errorMessage = it }
                        ApiPasswordDialogMode.UNLOCK -> onUnlock(password) { errorMessage = it }
                        ApiPasswordDialogMode.CHANGE -> onChangePassword(currentPass, password, confirmPass) { errorMessage = it }
                    }
                },
                modifier = Modifier.testTag("api_password_submit_btn")
            ) {
                Text(
                    when (mode) {
                        ApiPasswordDialogMode.CREATE -> "Set & Unlock"
                        ApiPasswordDialogMode.UNLOCK -> "Unlock"
                        ApiPasswordDialogMode.CHANGE -> "Update"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
