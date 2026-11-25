package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.db.PlaylistWithSummary
import com.example.data.db.TrackEntity
import com.example.data.library.FolderMonitorStatus
import com.example.data.model.AudioSignalInfo
import com.example.data.model.SortDirection
import com.example.data.model.SortOption
import com.example.ui.components.AudioFormatBadge
import com.example.ui.components.PhotonAlbumArt
import com.example.ui.components.PhotonDeckHero
import com.example.ui.components.PhotonEqualizerBars
import com.example.ui.components.SearchBarWithHistory
import com.example.ui.components.formatDurationMs
import com.example.ui.theme.LocalPhotonColors
import kotlinx.coroutines.launch

private enum class LibraryHubPage(
    val title: String,
    val swipeHint: String
) {
    MUSIC_LIBRARY("Music Library", "Swipe → Genres · Playlists"),
    GENRES("Genres", "Genre Folders · Page 2/3"),
    PLAYLISTS("Playlists", "Playlists · Page 3/3")
}

@Composable
fun LibraryScreen(
    tracks: List<TrackEntity>,
    allLibraryTracks: List<TrackEntity>,
    favoriteTracks: List<TrackEntity>,
    recentlyPlayedTracks: List<TrackEntity>,
    playlists: List<PlaylistWithSummary>,
    selectedPlaylistId: Long?,
    selectedPlaylistTracks: List<TrackEntity>,
    totalLibraryCount: Int,
    currentTrackId: Long?,
    currentTrack: TrackEntity? = null,
    signalInfo: AudioSignalInfo = AudioSignalInfo(),
    isPlaying: Boolean,
    searchQuery: String,
    searchHistory: List<String>,
    sortOption: SortOption,
    sortDirection: SortDirection,
    monitorStatus: FolderMonitorStatus,
    onSearchQueryChange: (String) -> Unit,
    onCommitSearchQuery: (String) -> Unit,
    onRemoveSearchHistoryItem: (String) -> Unit,
    onClearSearchHistory: () -> Unit,
    onSortOptionSelect: (SortOption) -> Unit,
    onToggleSortDirection: () -> Unit,
    onImportFilesClick: () -> Unit,
    onWatchFolderClick: () -> Unit,
    onScanFoldersNow: () -> Unit,
    onGenerateStudioWav: () -> Unit,
    onPlayTrack: (List<TrackEntity>, Int) -> Unit,
    onShuffleAll: (List<TrackEntity>) -> Unit,
    onToggleFavorite: (TrackEntity) -> Unit,
    onAutoCompleteMetadata: (TrackEntity) -> Unit,
    onAddToPlaylist: (TrackEntity) -> Unit,
    onDeleteTrack: (TrackEntity) -> Unit,
    onSelectPlaylist: (Long?) -> Unit,
    onCreatePlaylist: (String, String) -> Unit,
    onRenamePlaylist: (Long, String, String) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onAddTrackToPlaylist: (Long, TrackEntity) -> Unit,
    onRemoveTrackFromPlaylist: (Long, TrackEntity) -> Unit,
    onReorderPlaylistTracks: (Long, List<TrackEntity>) -> Unit,
    onOpenStats: () -> Unit,
    onOpenNowPlaying: () -> Unit = {},
    onTogglePlayPause: () -> Unit = {},
    onActivePageChanged: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val photonColors = LocalPhotonColors.current
    val scope = rememberCoroutineScope()
    val pages = LibraryHubPage.entries
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { pages.size })
    var selectedGenreName by remember { mutableStateOf<String?>(null) }

    val genreCount = remember(allLibraryTracks) { buildGenreFolders(allLibraryTracks).size }

    val filteredPlaylists = remember(playlists, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) playlists
        else playlists.filter {
            it.name.lowercase().contains(q) || it.description.lowercase().contains(q)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        onActivePageChanged(pagerState.currentPage)
    }

    val playTrackWithSearchCommit: (List<TrackEntity>, Int) -> Unit = { list, index ->
        if (searchQuery.isNotBlank()) {
            onCommitSearchQuery(searchQuery)
        }
        onPlayTrack(list, index)
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 1. Pinned Top Search Bar with Recent Query History & Live Suggestions
        SearchBarWithHistory(
            searchQuery = searchQuery,
            searchHistory = searchHistory,
            onSearchQueryChange = onSearchQueryChange,
            onCommitSearchQuery = onCommitSearchQuery,
            onRemoveHistoryItem = onRemoveSearchHistoryItem,
            onClearHistory = onClearSearchHistory
        )

        // 2. Minimal Page Indicator Strip (zero filter tabs — swipe horizontally across dedicated pages)
        val activePage = pages[pagerState.currentPage]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 4.dp)
                .testTag("library_swipe_page_indicator"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = activePage.title.uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = photonColors.photonGlow
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    pages.forEachIndexed { idx, _ ->
                        val isCurrent = idx == pagerState.currentPage
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(if (isCurrent) 20.dp else 7.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isCurrent) photonColors.photonGlow
                                    else photonColors.cardBorder
                                )
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Rounded.Swipe,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = activePage.swipeHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 3. Three Dedicated Swipeable Pages: Music Library -> Genres -> Playlists
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("library_hub_pager")
        ) { pageIndex ->
            when (pages[pageIndex]) {
                LibraryHubPage.MUSIC_LIBRARY -> {
                    MusicLibraryHomePage(
                        tracks = tracks,
                        favoriteTracks = favoriteTracks,
                        recentlyPlayedTracks = recentlyPlayedTracks,
                        totalLibraryCount = totalLibraryCount,
                        genreCount = genreCount,
                        playlistCount = playlists.size,
                        currentTrackId = currentTrackId,
                        currentTrack = currentTrack,
                        signalInfo = signalInfo,
                        isPlaying = isPlaying,
                        searchQuery = searchQuery,
                        sortOption = sortOption,
                        sortDirection = sortDirection,
                        monitorStatus = monitorStatus,
                        onSortOptionSelect = onSortOptionSelect,
                        onToggleSortDirection = onToggleSortDirection,
                        onImportFilesClick = onImportFilesClick,
                        onWatchFolderClick = onWatchFolderClick,
                        onScanFoldersNow = onScanFoldersNow,
                        onGenerateStudioWav = onGenerateStudioWav,
                        onPlayTrack = playTrackWithSearchCommit,
                        onShuffleAll = onShuffleAll,
                        onToggleFavorite = onToggleFavorite,
                        onAutoCompleteMetadata = onAutoCompleteMetadata,
                        onAddToPlaylist = onAddToPlaylist,
                        onDeleteTrack = onDeleteTrack,
                        onJumpToGenres = {
                            scope.launch { pagerState.animateScrollToPage(LibraryHubPage.GENRES.ordinal) }
                        },
                        onJumpToPlaylists = {
                            scope.launch { pagerState.animateScrollToPage(LibraryHubPage.PLAYLISTS.ordinal) }
                        },
                        onOpenStats = onOpenStats,
                        onOpenNowPlaying = onOpenNowPlaying,
                        onTogglePlayPause = onTogglePlayPause
                    )
                }

                LibraryHubPage.GENRES -> {
                    GenresFolderPage(
                        tracks = tracks,
                        totalLibraryCount = totalLibraryCount,
                        currentTrackId = currentTrackId,
                        isPlaying = isPlaying,
                        searchQuery = searchQuery,
                        selectedGenreName = selectedGenreName,
                        onSelectGenre = { genre ->
                            if (genre != null && searchQuery.isNotBlank()) {
                                onCommitSearchQuery(searchQuery)
                            }
                            selectedGenreName = genre
                        },
                        onPlayTrack = playTrackWithSearchCommit,
                        onShuffleAll = onShuffleAll,
                        onAutoCompleteMetadata = onAutoCompleteMetadata,
                        onAddToPlaylist = onAddToPlaylist,
                        onDeleteTrack = onDeleteTrack,
                        onImportFilesClick = onImportFilesClick,
                        onWatchFolderClick = onWatchFolderClick,
                        onGenerateStudioWav = onGenerateStudioWav
                    )
                }

                LibraryHubPage.PLAYLISTS -> {
                    PlaylistsScreen(
                        playlists = filteredPlaylists,
                        allLibraryTracks = allLibraryTracks,
                        selectedPlaylistId = selectedPlaylistId,
                        selectedPlaylistTracks = selectedPlaylistTracks,
                        currentTrackId = currentTrackId,
                        isPlaying = isPlaying,
                        onSelectPlaylist = { id ->
                            if (id != null && searchQuery.isNotBlank()) {
                                onCommitSearchQuery(searchQuery)
                            }
                            onSelectPlaylist(id)
                        },
                        onCreatePlaylist = onCreatePlaylist,
                        onRenamePlaylist = onRenamePlaylist,
                        onDeletePlaylist = onDeletePlaylist,
                        onAddTrackToPlaylist = onAddTrackToPlaylist,
                        onRemoveTrackFromPlaylist = onRemoveTrackFromPlaylist,
                        onReorderPlaylistTracks = onReorderPlaylistTracks,
                        onPlayTracks = playTrackWithSearchCommit,
                        onShuffleTracks = onShuffleAll,
                        onAutoCompleteMetadata = onAutoCompleteMetadata
                    )
                }
            }
        }
    }
}

/**
 * Home Music Library page featuring a BlackPlayer EX-style contained side-by-side swipable
 * song columns shelf so songs never dominate the entire vertical screen.
 */
@Composable
private fun MusicLibraryHomePage(
    tracks: List<TrackEntity>,
    favoriteTracks: List<TrackEntity>,
    recentlyPlayedTracks: List<TrackEntity>,
    totalLibraryCount: Int,
    genreCount: Int,
    playlistCount: Int,
    currentTrackId: Long?,
    currentTrack: TrackEntity?,
    signalInfo: AudioSignalInfo,
    isPlaying: Boolean,
    searchQuery: String,
    sortOption: SortOption,
    sortDirection: SortDirection,
    monitorStatus: FolderMonitorStatus,
    onSortOptionSelect: (SortOption) -> Unit,
    onToggleSortDirection: () -> Unit,
    onImportFilesClick: () -> Unit,
    onWatchFolderClick: () -> Unit,
    onScanFoldersNow: () -> Unit,
    onGenerateStudioWav: () -> Unit,
    onPlayTrack: (List<TrackEntity>, Int) -> Unit,
    onShuffleAll: (List<TrackEntity>) -> Unit,
    onToggleFavorite: (TrackEntity) -> Unit,
    onAutoCompleteMetadata: (TrackEntity) -> Unit,
    onAddToPlaylist: (TrackEntity) -> Unit,
    onDeleteTrack: (TrackEntity) -> Unit,
    onJumpToGenres: () -> Unit,
    onJumpToPlaylists: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onTogglePlayPause: () -> Unit
) {
    val photonColors = LocalPhotonColors.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("library_page_music_library"),
        contentPadding = PaddingValues(top = 6.dp, bottom = 156.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Signature Photon Deck Hero Console
        item(key = "hero_banner") {
            PhotonDeckHero(
                totalTracks = totalLibraryCount,
                genreCount = genreCount,
                playlistCount = playlistCount,
                isPlaying = isPlaying,
                monitorStatus = monitorStatus,
                onImportFilesClick = onImportFilesClick,
                onWatchFolderClick = onWatchFolderClick,
                onScanFoldersNow = onScanFoldersNow,
                onPlayAll = { if (tracks.isNotEmpty()) onPlayTrack(tracks, 0) },
                onShuffleAll = { onShuffleAll(tracks) },
                currentTrack = currentTrack,
                signalInfo = signalInfo,
                onOpenNowPlaying = onOpenNowPlaying,
                onTogglePlayPause = onTogglePlayPause
            )
        }

        // Compact Favorites & Recently Played Quick Shelf
        if (favoriteTracks.isNotEmpty() || recentlyPlayedTracks.isNotEmpty()) {
            item(key = "home_highlights_shelf") {
                HomeQuickHighlightsStrip(
                    favoriteTracks = favoriteTracks,
                    recentlyPlayedTracks = recentlyPlayedTracks,
                    currentTrackId = currentTrackId,
                    isPlaying = isPlaying,
                    onPlayTrack = onPlayTrack,
                    onOpenStats = onOpenStats
                )
            }
        }

        // Compact Sort Controls
        item(key = "sort_bar") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SORT",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = photonColors.photonGlow
                )
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SortOption.entries.forEach { option ->
                        val selected = sortOption == option
                        FilterChip(
                            selected = selected,
                            onClick = { onSortOptionSelect(option) },
                            label = {
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            trailingIcon = if (selected && option != SortOption.CUSTOM_ORDER) {
                                {
                                    Icon(
                                        imageVector = if (sortDirection == SortDirection.ASC) {
                                            Icons.Rounded.ArrowUpward
                                        } else {
                                            Icons.Rounded.ArrowDownward
                                        },
                                        contentDescription = "Sort direction",
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            } else null,
                            modifier = Modifier.testTag("sort_chip_${option.name.lowercase()}"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = photonColors.photonGlow,
                                selectedTrailingIconColor = photonColors.photonGlow
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = photonColors.cardBorder.copy(alpha = 0.7f),
                                selectedBorderColor = photonColors.photonGlow
                            )
                        )
                    }
                }
                if (sortOption != SortOption.CUSTOM_ORDER) {
                    IconButton(
                        onClick = onToggleSortDirection,
                        modifier = Modifier.testTag("toggle_sort_direction")
                    ) {
                        Icon(
                            imageVector = if (sortDirection == SortDirection.ASC) {
                                Icons.Rounded.ArrowUpward
                            } else {
                                Icons.Rounded.ArrowDownward
                            },
                            contentDescription = "Toggle sort direction",
                            tint = photonColors.photonGlow
                        )
                    }
                }
            }
        }

        // BlackPlayer EX-style Contained Side-by-Side Swipable Song Columns
        if (tracks.isEmpty()) {
            item(key = "empty_state") {
                EmptyLibraryCard(
                    hasAnyTracks = totalLibraryCount > 0,
                    hasSearchQuery = searchQuery.isNotBlank(),
                    onImportFilesClick = onImportFilesClick,
                    onWatchFolderClick = onWatchFolderClick,
                    onGenerateStudioWav = onGenerateStudioWav
                )
            }
        } else {
            item(key = "side_by_side_song_columns") {
                BlackPlayerSideBySideSongColumns(
                    tracks = tracks,
                    currentTrackId = currentTrackId,
                    isPlaying = isPlaying,
                    onPlayTrack = onPlayTrack,
                    onToggleFavorite = onToggleFavorite,
                    onAutoCompleteMetadata = onAutoCompleteMetadata,
                    onAddToPlaylist = onAddToPlaylist,
                    onDeleteTrack = onDeleteTrack
                )
            }
        }

        // Compact Quick Access Deck to Genre Folders & Playlists (keeps Home balanced so songs don't dominate)
        item(key = "home_explore_deck") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HomeExploreCard(
                    icon = Icons.Rounded.Folder,
                    title = "Genre Folders",
                    subtitle = "$genreCount genre${if (genreCount == 1) "" else "s"}",
                    accent = photonColors.losslessBadge,
                    tag = "home_jump_genres_card",
                    onClick = onJumpToGenres,
                    modifier = Modifier.weight(1f)
                )
                HomeExploreCard(
                    icon = Icons.Rounded.QueueMusic,
                    title = "Playlists",
                    subtitle = "$playlistCount playlist${if (playlistCount == 1) "" else "s"}",
                    accent = photonColors.nebulaAccent,
                    tag = "home_jump_playlists_card",
                    onClick = onJumpToPlaylists,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * BlackPlayer EX-inspired side-by-side swipable song columns shelf.
 * Groups all songs into neat vertical columns placed side-by-side in a snapping horizontal carousel
 * so songs stay compact and never take over the entire Home menu screen.
 */
@Composable
private fun BlackPlayerSideBySideSongColumns(
    tracks: List<TrackEntity>,
    currentTrackId: Long?,
    isPlaying: Boolean,
    onPlayTrack: (List<TrackEntity>, Int) -> Unit,
    onToggleFavorite: (TrackEntity) -> Unit,
    onAutoCompleteMetadata: (TrackEntity) -> Unit,
    onAddToPlaylist: (TrackEntity) -> Unit,
    onDeleteTrack: (TrackEntity) -> Unit
) {
    val photonColors = LocalPhotonColors.current
    val rowsPerColumn = when {
        tracks.size <= 2 -> 1
        tracks.size <= 6 -> 2
        tracks.size <= 11 -> 3
        else -> 4
    }

    val columns = remember(tracks, rowsPerColumn) {
        tracks.mapIndexed { index, track -> index to track }.chunked(rowsPerColumn)
    }

    val rowState = rememberLazyListState()
    val snapBehavior = rememberSnapFlingBehavior(lazyListState = rowState)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_song_columns_section"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "ALL SONGS",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = photonColors.photonGlow.copy(alpha = 0.14f),
                    border = BorderStroke(0.75.dp, photonColors.photonGlow.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = "${tracks.size}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = photonColors.photonGlow,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = if (columns.size > 1) {
                    "${columns.size} columns · Swipe ↔"
                } else {
                    "Side-by-side view"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // Keep columns narrow enough that two columns appear visibly side-by-side on phones & tablets
            val columnWidth = if (columns.size > 1) {
                (maxWidth * 0.72f).coerceIn(228.dp, 296.dp)
            } else {
                (maxWidth - 32.dp).coerceAtMost(360.dp)
            }

            LazyRow(
                state = rowState,
                flingBehavior = snapBehavior,
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_song_columns_row")
            ) {
                itemsIndexed(
                    items = columns,
                    key = { colIdx, col -> "col_${colIdx}_${col.firstOrNull()?.second?.id ?: 0L}" }
                ) { _, columnEntries ->
                    Surface(
                        modifier = Modifier.width(columnWidth),
                        shape = RoundedCornerShape(18.dp),
                        color = photonColors.glassSurface,
                        border = BorderStroke(0.9.dp, photonColors.cardBorder.copy(alpha = 0.75f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            columnEntries.forEach { (trackIndex, track) ->
                                CompactColumnTrackTile(
                                    track = track,
                                    isCurrent = track.id == currentTrackId,
                                    isPlaying = track.id == currentTrackId && isPlaying,
                                    onClick = { onPlayTrack(tracks, trackIndex) },
                                    onToggleFavorite = { onToggleFavorite(track) },
                                    onAutoCompleteMetadata = { onAutoCompleteMetadata(track) },
                                    onAddToPlaylist = { onAddToPlaylist(track) },
                                    onRemoveTrack = { onDeleteTrack(track) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactColumnTrackTile(
    track: TrackEntity,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAutoCompleteMetadata: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onRemoveTrack: () -> Unit
) {
    val photonColors = LocalPhotonColors.current
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("track_card_${track.id}")
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isCurrent) {
            photonColors.photonGlow.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f)
        },
        border = BorderStroke(
            width = if (isCurrent) 1.1.dp else 0.6.dp,
            color = if (isCurrent) photonColors.photonGlow.copy(alpha = 0.75f) else photonColors.cardBorder.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, top = 7.dp, bottom = 7.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PhotonAlbumArt(
                artworkUri = track.artworkUri,
                contentDescription = "Album art for ${track.title}",
                size = 42.dp,
                cornerRadius = 9.dp,
                isPlaying = isPlaying
            )
            Spacer(modifier = Modifier.width(9.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = if (isCurrent) photonColors.photonGlow else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isCurrent) {
                        PhotonEqualizerBars(isPlaying = isPlaying)
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    AudioFormatBadge(format = track.format)
                    Text(
                        text = formatDurationMs(track.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1
                    )
                }
            }

            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .size(34.dp)
                    .testTag("track_fav_${track.id}")
            ) {
                Icon(
                    imageVector = if (track.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (track.isFavorite) "Remove favorite" else "Add favorite",
                    tint = if (track.isFavorite) Color(0xFFFF5C8A) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.size(16.dp)
                )
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("track_menu_${track.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Options for ${track.title}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (track.isFavorite) "Remove from Favorites" else "Add to Favorites") },
                        leadingIcon = {
                            Icon(
                                if (track.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = null,
                                tint = Color(0xFFFF5C8A)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onToggleFavorite()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Edit Metadata") },
                        leadingIcon = {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = photonColors.photonGlow)
                        },
                        onClick = {
                            menuExpanded = false
                            onAutoCompleteMetadata()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to Playlist") },
                        leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onAddToPlaylist()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Remove from Library") },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onRemoveTrack()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeQuickHighlightsStrip(
    favoriteTracks: List<TrackEntity>,
    recentlyPlayedTracks: List<TrackEntity>,
    currentTrackId: Long?,
    isPlaying: Boolean,
    onPlayTrack: (List<TrackEntity>, Int) -> Unit,
    onOpenStats: () -> Unit
) {
    val photonColors = LocalPhotonColors.current
    var showFavorites by remember(favoriteTracks.isEmpty(), recentlyPlayedTracks.isEmpty()) {
        mutableStateOf(favoriteTracks.isNotEmpty())
    }
    val activeTracks = if (showFavorites && favoriteTracks.isNotEmpty()) {
        favoriteTracks
    } else if (recentlyPlayedTracks.isNotEmpty()) {
        recentlyPlayedTracks
    } else {
        favoriteTracks
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_highlights_strip"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = showFavorites,
                    onClick = { showFavorites = true },
                    enabled = favoriteTracks.isNotEmpty(),
                    label = { Text("Favorites (${favoriteTracks.size})", style = MaterialTheme.typography.labelMedium) },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.Favorite,
                            contentDescription = null,
                            tint = Color(0xFFFF5C8A),
                            modifier = Modifier.size(15.dp)
                        )
                    },
                    modifier = Modifier.testTag("home_tab_favorites")
                )
                FilterChip(
                    selected = !showFavorites,
                    onClick = { showFavorites = false },
                    enabled = recentlyPlayedTracks.isNotEmpty(),
                    label = { Text("Recent (${recentlyPlayedTracks.size})", style = MaterialTheme.typography.labelMedium) },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.History,
                            contentDescription = null,
                            tint = photonColors.photonGlow,
                            modifier = Modifier.size(15.dp)
                        )
                    },
                    modifier = Modifier.testTag("home_tab_recent")
                )
            }

            Text(
                text = "Stats →",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = photonColors.photonGlow,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpenStats)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .testTag("home_open_stats_link")
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(
                items = activeTracks.take(12),
                key = { idx, track -> "highlight_${if (showFavorites) "fav" else "rec"}_${track.id}_$idx" }
            ) { idx, track ->
                val isCurrent = track.id == currentTrackId
                Surface(
                    onClick = { onPlayTrack(activeTracks, idx) },
                    shape = RoundedCornerShape(15.dp),
                    color = if (isCurrent) photonColors.photonGlow.copy(alpha = 0.16f) else photonColors.glassSurface,
                    border = BorderStroke(
                        width = if (isCurrent) 1.2.dp else 0.8.dp,
                        color = if (isCurrent) photonColors.photonGlow else photonColors.cardBorder.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier
                        .width(168.dp)
                        .testTag("home_highlight_card_${track.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PhotonAlbumArt(
                            artworkUri = track.artworkUri,
                            contentDescription = track.title,
                            size = 40.dp,
                            cornerRadius = 9.dp,
                            isPlaying = isCurrent && isPlaying
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isCurrent) photonColors.photonGlow else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeExploreCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val photonColors = LocalPhotonColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = photonColors.glassSurface,
        border = BorderStroke(
            width = 1.1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    accent.copy(alpha = 0.72f),
                    accent.copy(alpha = 0.22f)
                )
            )
        ),
        shadowElevation = 6.dp,
        modifier = modifier.testTag(tag)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.14f),
                            Color.Transparent
                        )
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 13.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    accent.copy(alpha = 0.28f),
                                    accent.copy(alpha = 0.10f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accent,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFF0F7FF),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyLibraryCard(
    hasAnyTracks: Boolean,
    hasSearchQuery: Boolean,
    onImportFilesClick: () -> Unit,
    onWatchFolderClick: () -> Unit,
    onGenerateStudioWav: () -> Unit
) {
    val photonColors = LocalPhotonColors.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(22.dp),
        color = photonColors.glassSurface,
        border = BorderStroke(1.dp, photonColors.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(photonColors.photonGlow.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.LibraryMusic,
                    contentDescription = null,
                    tint = photonColors.photonGlow,
                    modifier = Modifier.size(30.dp)
                )
            }

            Text(
                text = when {
                    hasSearchQuery -> "No Matching Songs"
                    hasAnyTracks -> "No Songs in View"
                    else -> "Your Galaxy is ready"
                },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = when {
                    hasSearchQuery -> "Try clearing the top search bar to see all songs in your music library."
                    else -> "Import audio files from your device, watch a local music folder, or generate studio WAV tracks to browse them in side-by-side columns, genre folders, and playlists."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!hasSearchQuery) {
                Button(
                    onClick = onGenerateStudioWav,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("generate_studio_wav_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate Playable Studio WAV Tracks")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onImportFilesClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Pick Audio")
                    }
                    OutlinedButton(
                        onClick = onWatchFolderClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Watch Folder")
                    }
                }
            }
        }
    }
}
