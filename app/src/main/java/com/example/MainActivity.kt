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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.db.TrackEntity
import com.example.ui.PhotonViewModel
import com.example.ui.components.GalaxyBackdrop
import com.example.ui.screens.ArtistAlbumDetailScreen
import com.example.ui.screens.CollectionDetailTarget
import com.example.ui.screens.EqualizerScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.ListeningStatsScreen
import com.example.ui.screens.MetadataEditorMode
import com.example.ui.screens.MetadataEditorScreen
import com.example.ui.screens.NowPlayingScreen
import com.example.ui.screens.PhotonMiniPlayerBar
import com.example.ui.screens.PickPlaylistForTrackDialog
import com.example.ui.screens.PlaylistsScreen
import com.example.ui.screens.SettingsAndFoldersScreen
import com.example.ui.screens.SleepTimerModalDialog
import com.example.ui.screens.SmartQueueScreen
import com.example.ui.theme.AppWallpaperPreset
import com.example.ui.theme.LocalPhotonColors
import com.example.ui.theme.PhotonAudioTheme
import com.example.ui.theme.PhotonWallpaperConfig
import java.io.File
import kotlinx.coroutines.launch

enum class DrawerDestination(
    val label: String,
    val description: String,
    val icon: ImageVector
) {
    LIBRARY("Library", "Music Library, Genre Folders & Playlists", Icons.Rounded.LibraryMusic),
    PLAYLISTS("Playlists", "Custom playlists & drag-and-drop sorting", Icons.Rounded.QueueMusic),
    STATS("Listening Stats", "Most played tracks, top genres & favorites", Icons.Rounded.BarChart),
    SMART_QUEUE("Smart Queue", "Listening history & genre recommendations", Icons.Rounded.AutoAwesome),
    METADATA_EDITOR("Metadata Editor", "Edit tags, album covers & exact web lookup", Icons.Rounded.EditNote),
    EQUALIZER("Equalizer", "10-band studio EQ, mastering DSP & reverb", Icons.Rounded.Equalizer),
    SETTINGS("Settings", "Theme, widgets, font, wallpaper & scanner", Icons.Rounded.Tune)
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

            PhotonAudioTheme(
                darkTheme = settings.darkMode,
                cosmicSkin = settings.cosmicThemeSkin,
                customPrimaryArgb = settings.customAccentPrimaryArgb,
                customSecondaryArgb = settings.customAccentSecondaryArgb,
                customOledBlack = settings.customOledBlackBg,
                fontChoice = settings.appFont,
                wallpaperConfig = PhotonWallpaperConfig(
                    preset = settings.wallpaperPreset,
                    customPhotoPath = settings.customWallpaperPath,
                    dimAlpha = settings.wallpaperDimAlpha
                )
            ) {
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
    val favoriteTracks by viewModel.favoriteTracks.collectAsStateWithLifecycle()
    val recentlyPlayedTracks by viewModel.recentlyPlayedTracks.collectAsStateWithLifecycle()
    val mostPlayedTracks by viewModel.mostPlayedTracks.collectAsStateWithLifecycle()
    val genrePlayStats by viewModel.genrePlayStats.collectAsStateWithLifecycle()
    val artistPlayStats by viewModel.artistPlayStats.collectAsStateWithLifecycle()
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
    val isApiUnlocked by viewModel.isApiUnlocked.collectAsStateWithLifecycle()
    val replayGainScanProgress by viewModel.replayGainScanProgress.collectAsStateWithLifecycle()

    var currentDestination by remember { mutableStateOf(DrawerDestination.LIBRARY) }
    var libraryActivePage by remember { mutableIntStateOf(0) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var nowPlayingExpanded by remember { mutableStateOf(false) }
    var collectionBackStack by remember { mutableStateOf<List<CollectionDetailTarget>>(emptyList()) }
    var editorInitialMode by remember { mutableStateOf(MetadataEditorMode.SINGLE) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var trackForPlaylistPicker by remember { mutableStateOf<TrackEntity?>(null) }
    var apiPasswordDialogMode by remember { mutableStateOf<ApiPasswordDialogMode?>(null) }

    val activeCollectionDetail = collectionBackStack.lastOrNull()

    // Cover Art Photo Picker tracking
    var pendingCoverTrackId by remember { mutableStateOf<Long?>(null) }
    var pickedCoverOverrideUri by remember { mutableStateOf<String?>(null) }

    val navigateTo: (DrawerDestination) -> Unit = { target ->
        collectionBackStack = emptyList()
        if (target == DrawerDestination.METADATA_EDITOR) {
            editorInitialMode = MetadataEditorMode.SINGLE
        }
        currentDestination = target
        scope.launch { drawerState.close() }
    }

    val openArtistDetail: (String) -> Unit = { artistName ->
        if (artistName.isNotBlank()) {
            nowPlayingExpanded = false
            val target = CollectionDetailTarget.Artist(artistName)
            collectionBackStack = if (collectionBackStack.lastOrNull() == target) {
                collectionBackStack
            } else {
                collectionBackStack + target
            }
        }
    }

    val openAlbumDetail: (String, String?) -> Unit = { albumName, preferredArtist ->
        if (albumName.isNotBlank()) {
            nowPlayingExpanded = false
            val target = CollectionDetailTarget.Album(albumName, preferredArtist)
            collectionBackStack = if (collectionBackStack.lastOrNull() == target) {
                collectionBackStack
            } else {
                collectionBackStack + target
            }
        }
    }

    val popCollectionDetail: () -> Unit = {
        if (collectionBackStack.isNotEmpty()) {
            collectionBackStack = collectionBackStack.dropLast(1)
        }
    }

    val openTrackInMetadataEditor: (TrackEntity) -> Unit = { track ->
        viewModel.selectTrackForEditor(track)
        editorInitialMode = MetadataEditorMode.SINGLE
        collectionBackStack = emptyList()
        nowPlayingExpanded = false
        currentDestination = DrawerDestination.METADATA_EDITOR
    }

    val openAlbumInMetadataEditor: (String, TrackEntity) -> Unit = { _, anchorTrack ->
        viewModel.selectTrackForEditor(anchorTrack)
        editorInitialMode = MetadataEditorMode.ALBUM
        collectionBackStack = emptyList()
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
            activeCollectionDetail == null &&
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
    // Zero-permission Android Photo Picker for selecting custom app wallpaper
    val customWallpaperPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.saveCustomWallpaperPhoto(uri)
        }
    }

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

    // Standard Compose ModalNavigationDrawer (DrawerLayout + NavigationDrawer pattern)
    // Disable drawer drag gesture when swiping inside MP3/FLAC/WAV pages so horizontal paging feels seamless
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !nowPlayingExpanded && (currentDestination != DrawerDestination.LIBRARY || libraryActivePage == 0),
        drawerContent = {
            val drawerSelectedBg = lerp(photonColors.backdropMid, photonColors.photonGlow, 0.24f).copy(alpha = 0.92f)
            val drawerItemBg = photonColors.glassSurface.copy(alpha = 0.88f)
            val drawerHighlightText = lerp(photonColors.photonGlow, Color.White, 0.22f)
            ModalDrawerSheet(
                drawerContainerColor = photonColors.backdropTop,
                windowInsets = WindowInsets(0, 0, 0, 0),
                modifier = Modifier
                    .width(308.dp)
                    .testTag("navigation_sidebar_drawer")
            ) {
                // Full-bleed Wallpaper overlaying underneath all sidebar navigation buttons
                Box(modifier = Modifier.fillMaxSize()) {
                    val customWallpaperFile = settings.customWallpaperPath?.let { File(it) }?.takeIf { it.exists() }
                    when {
                        settings.wallpaperPreset == AppWallpaperPreset.CUSTOM_PHOTO && customWallpaperFile != null -> {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(customWallpaperFile)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Custom drawer wallpaper",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        settings.wallpaperPreset == AppWallpaperPreset.PHOTON_PRISM -> {
                            Image(
                                painter = painterResource(id = R.drawable.img_photon_deck_hero),
                                contentDescription = "Photon Prism drawer wallpaper",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        else -> {
                            Image(
                                painter = painterResource(id = R.drawable.img_galaxy_hero),
                                contentDescription = "Photon Audio galaxy drawer wallpaper",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        photonColors.backdropTop.copy(alpha = 0.62f),
                                        photonColors.backdropMid.copy(alpha = 0.78f),
                                        photonColors.backdropBottom.copy(alpha = 0.90f)
                                    )
                                )
                            )
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(bottom = 16.dp)
                    ) {
                        // Brand Header floating over the wallpaper with theme-synced frosted contrast
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = photonColors.glassSurface.copy(alpha = 0.88f),
                            border = BorderStroke(1.dp, photonColors.photonGlow.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 14.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Photon Audio",
                                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                Text(
                                    text = "${allTracks.size} Tracks · ${playlists.size} Playlists · Local Only",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = photonColors.photonGlow
                                )
                            }
                        }

                        Text(
                            text = "MENU",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = photonColors.photonGlow,
                            modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp)
                        )

                        DrawerDestination.entries.forEach { destination ->
                            val selected = destination == currentDestination
                            val badgeText = when (destination) {
                                DrawerDestination.LIBRARY -> "${allTracks.size}"
                                DrawerDestination.PLAYLISTS -> "${playlists.size}"
                                DrawerDestination.STATS -> if (totalPlayCount > 0) "$totalPlayCount" else null
                                DrawerDestination.SMART_QUEUE -> if (smartQueueSuggestions.isNotEmpty()) "${smartQueueSuggestions.size}" else null
                                DrawerDestination.METADATA_EDITOR -> "TAGS"
                                DrawerDestination.EQUALIZER -> if (settings.eqEnabled) "ON" else "OFF"
                                DrawerDestination.SETTINGS -> {
                                    val totalExclusions = excludedFolders.size + settings.excludedFileTypes.size
                                    if (totalExclusions > 0) "$totalExclusions" else null
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (selected) drawerSelectedBg else drawerItemBg,
                                border = BorderStroke(
                                    width = if (selected) 1.3.dp else 0.8.dp,
                                    color = if (selected) photonColors.photonGlow else photonColors.cardBorder.copy(alpha = 0.65f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                NavigationDrawerItem(
                                    label = {
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                text = destination.label,
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                color = if (selected) drawerHighlightText else Color.White
                                            )
                                            Text(
                                                text = destination.description,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (selected) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.74f)
                                            )
                                        }
                                    },
                                    selected = selected,
                                    onClick = { navigateTo(destination) },
                                    icon = {
                                        Icon(
                                            imageVector = destination.icon,
                                            contentDescription = destination.label,
                                            tint = if (selected) drawerHighlightText else Color.White.copy(alpha = 0.9f)
                                        )
                                    },
                                    badge = badgeText?.let { count ->
                                        {
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (selected) {
                                                    photonColors.photonGlow.copy(alpha = 0.28f)
                                                } else {
                                                    photonColors.backdropMid.copy(alpha = 0.78f)
                                                },
                                                border = BorderStroke(
                                                    0.9.dp,
                                                    if (selected) drawerHighlightText else photonColors.cardBorder
                                                )
                                            ) {
                                                Text(
                                                    text = count,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.testTag("drawer_item_${destination.name.lowercase()}"),
                                    colors = NavigationDrawerItemDefaults.colors(
                                        selectedContainerColor = Color.Transparent,
                                        unselectedContainerColor = Color.Transparent,
                                        selectedIconColor = drawerHighlightText,
                                        selectedTextColor = drawerHighlightText,
                                        unselectedIconColor = Color.White.copy(alpha = 0.9f),
                                        unselectedTextColor = Color.White
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 18.dp),
                            color = photonColors.photonGlow.copy(alpha = 0.38f)
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = drawerItemBg,
                            border = BorderStroke(0.8.dp, photonColors.losslessBadge.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            NavigationDrawerItem(
                                label = {
                                    Text(
                                        text = "Auto-Scan Music Now",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color.White
                                    )
                                },
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
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.testTag("drawer_scan_now_item"),
                                colors = NavigationDrawerItemDefaults.colors(
                                    unselectedContainerColor = Color.Transparent,
                                    unselectedTextColor = Color.White
                                )
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = drawerItemBg,
                            border = BorderStroke(
                                0.8.dp,
                                if (playbackState.sleepTimer.isActive) {
                                    photonColors.goldBadge.copy(alpha = 0.7f)
                                } else {
                                    photonColors.cardBorder.copy(alpha = 0.6f)
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            NavigationDrawerItem(
                                label = {
                                    Text(
                                        text = if (playbackState.sleepTimer.isActive) {
                                            "Sleep Timer (${playbackState.sleepTimer.label})"
                                        } else {
                                            "Sleep Timer"
                                        },
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color.White
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
                                        tint = if (playbackState.sleepTimer.isActive) photonColors.goldBadge else Color.White.copy(alpha = 0.9f)
                                    )
                                },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.testTag("drawer_sleep_timer_item"),
                                colors = NavigationDrawerItemDefaults.colors(
                                    unselectedContainerColor = Color.Transparent,
                                    unselectedTextColor = Color.White
                                )
                            )
                        }
                    }
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
                                if (activeCollectionDetail != null) {
                                    IconButton(
                                        onClick = popCollectionDetail,
                                        modifier = Modifier.testTag("collection_detail_back_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                            contentDescription = "Navigate back",
                                            tint = photonColors.photonGlow,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                } else {
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
                                }
                            },
                            title = {
                                Text(
                                    text = when (activeCollectionDetail) {
                                        is CollectionDetailTarget.Artist -> "Artist"
                                        is CollectionDetailTarget.Album -> "Album"
                                        null -> currentDestination.label
                                    },
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
                                    onSkipPrevious = { viewModel.playerController.skipPrevious() },
                                    onNavigateToArtist = openArtistDetail
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    if (activeCollectionDetail != null) {
                        ArtistAlbumDetailScreen(
                            target = activeCollectionDetail,
                            allTracks = allTracks,
                            currentTrackId = playbackState.currentTrack?.id,
                            isPlaying = playbackState.isPlaying,
                            onBack = popCollectionDetail,
                            onNavigateToArtist = openArtistDetail,
                            onNavigateToAlbum = openAlbumDetail,
                            onPlayTracks = viewModel::playTracks,
                            onShuffleTracks = viewModel::playShuffled,
                            onPlayNext = viewModel::playNextFromSmartQueue,
                            onToggleFavorite = viewModel::toggleFavorite,
                            onAddToPlaylist = { trackForPlaylistPicker = it },
                            onEditTrackMetadata = openTrackInMetadataEditor,
                            onEditAlbumMetadata = openAlbumInMetadataEditor,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        )
                    } else Crossfade(
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
                                allLibraryTracks = allTracks,
                                favoriteTracks = favoriteTracks,
                                recentlyPlayedTracks = recentlyPlayedTracks,
                                playlists = playlists,
                                selectedPlaylistId = selectedPlaylistId,
                                selectedPlaylistTracks = selectedPlaylistTracks,
                                totalLibraryCount = allTracks.size,
                                currentTrackId = playbackState.currentTrack?.id,
                                currentTrack = playbackState.currentTrack,
                                signalInfo = playbackState.signalInfo,
                                isPlaying = playbackState.isPlaying,
                                searchQuery = searchQuery,
                                searchHistory = settings.searchHistory,
                                sortOption = settings.sortOption,
                                sortDirection = settings.sortDirection,
                                monitorStatus = monitorStatus,
                                onSearchQueryChange = viewModel::updateSearchQuery,
                                onCommitSearchQuery = viewModel::commitSearchQuery,
                                onRemoveSearchHistoryItem = viewModel::removeSearchHistoryItem,
                                onClearSearchHistory = viewModel::clearSearchHistory,
                                onSortOptionSelect = viewModel::selectSortOption,
                                onToggleSortDirection = viewModel::toggleSortDirection,
                                onImportFilesClick = launchImportAudio,
                                onWatchFolderClick = launchWatchFolder,
                                onScanFoldersNow = viewModel::scanWatchedFoldersNow,
                                onGenerateStudioWav = viewModel::generateStarterWavTracks,
                                onPlayTrack = { list, idx -> viewModel.playTracks(list, idx) },
                                onShuffleAll = viewModel::playShuffled,
                                onToggleFavorite = viewModel::toggleFavorite,
                                onAutoCompleteMetadata = openTrackInMetadataEditor,
                                onAddToPlaylist = { trackForPlaylistPicker = it },
                                onDeleteTrack = viewModel::deleteTrack,
                                onSelectPlaylist = viewModel::selectPlaylist,
                                onCreatePlaylist = viewModel::createPlaylist,
                                onRenamePlaylist = viewModel::renamePlaylist,
                                onDeletePlaylist = viewModel::deletePlaylist,
                                onAddTrackToPlaylist = viewModel::addTrackToPlaylist,
                                onRemoveTrackFromPlaylist = viewModel::removeTrackFromPlaylist,
                                onReorderPlaylistTracks = viewModel::persistPlaylistOrder,
                                onOpenStats = { currentDestination = DrawerDestination.STATS },
                                onOpenNowPlaying = { nowPlayingExpanded = true },
                                onTogglePlayPause = { viewModel.playerController.togglePlayPause() },
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

                            DrawerDestination.STATS -> ListeningStatsScreen(
                                totalPlayCount = totalPlayCount,
                                totalLibraryTracks = allTracks.size,
                                favoriteTracks = favoriteTracks,
                                mostPlayedTracks = mostPlayedTracks,
                                genreStats = genrePlayStats,
                                artistStats = artistPlayStats,
                                currentTrackId = playbackState.currentTrack?.id,
                                isPlaying = playbackState.isPlaying,
                                onPlayTracks = viewModel::playTracks,
                                onToggleFavorite = viewModel::toggleFavorite
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
                                onSaveMetadata = viewModel::saveFullTrackMetadata,
                                onSaveBatchMetadata = viewModel::saveBatchTrackMetadata,
                                initialEditorMode = editorInitialMode
                            )

                            DrawerDestination.EQUALIZER -> EqualizerScreen(
                                settings = settings,
                                telemetry = playbackState.eqTelemetry,
                                signalInfo = playbackState.signalInfo,
                                currentTrack = playbackState.currentTrack,
                                allTracks = allTracks,
                                replayGainScanProgress = replayGainScanProgress,
                                spectrumFlow = viewModel.spectrumState,
                                onToggleEq = viewModel::setEqEnabled,
                                onSelectPreset = viewModel::applyEqPreset,
                                onBandChange = viewModel::updateEqBand,
                                onSaveCustomPreset = viewModel::saveCustomEqPreset,
                                onDeleteCustomPreset = viewModel::deleteCustomEqPreset,
                                onSpatialChange = viewModel::updateSpatialEffects,
                                onSetPreAmpDb = viewModel::setPreAmpDb,
                                onSetSoftLimiterEnabled = viewModel::setSoftLimiterEnabled,
                                onSetLoudnessNormalizerEnabled = viewModel::setLoudnessNormalizerEnabled,
                                onSetStereoBalance = viewModel::setStereoBalance,
                                onSetMonoAudioEnabled = viewModel::setMonoAudioEnabled,
                                onSetCrossfeedStrength = viewModel::setCrossfeedStrength,
                                onSetReverbPreset = viewModel::setReverbPreset,
                                onSetAutoEqByOutputEnabled = viewModel::setAutoEqByOutputEnabled,
                                onSetOutputRouteEqPreset = viewModel::setOutputRouteEqPreset,
                                onUpdateTrackSoundProfile = viewModel::updateTrackSoundProfile,
                                onSetReplayGainMode = viewModel::setReplayGainMode,
                                onSetReplayGainTargetLufs = viewModel::setReplayGainTargetLufs,
                                onSetReplayGainPreAmpDb = viewModel::setReplayGainPreAmpDb,
                                onSetPreventClipping = viewModel::setPreventClipping,
                                onScanLibraryLoudness = viewModel::scanLibraryLoudness,
                                onScanCurrentTrackLoudness = viewModel::scanCurrentTrackLoudness
                            )

                            DrawerDestination.SETTINGS -> SettingsAndFoldersScreen(
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
                        onNavigateToArtist = openArtistDetail,
                        onNavigateToAlbum = openAlbumDetail
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
            onCreateAndAdd = { name, desc -> viewModel.createPlaylistAndAddTrack(name, desc, track) },
            onDismiss = { trackForPlaylistPicker = null }
        )
    }

    // Password-Protected API Access Dialog (Create / Unlock / Change Password)
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
