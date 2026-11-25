package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.remember
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
import com.example.data.db.TrackEntity
import com.example.data.library.FolderMonitorStatus
import com.example.data.model.SortDirection
import com.example.data.model.SortOption
import com.example.ui.components.ReorderableTrackLazyColumn
import com.example.ui.theme.LocalPhotonColors

private enum class LibraryFormatPage(
    val title: String,
    val subtitle: String,
    val formatKey: String?
) {
    ALL("All Tracks", "Complete MP3, FLAC, WAV, Opus, AAC & Ogg Library", null),
    MP3("MP3", "Dedicated MP3 Audio Collection", "MP3"),
    FLAC("FLAC", "Dedicated Lossless FLAC Collection", "FLAC"),
    WAV("WAV", "Dedicated Uncompressed Studio WAV Collection", "WAV"),
    OPUS("Opus", "Dedicated Modern Opus Audio Collection", "OPUS"),
    AAC("M4A / AAC", "Dedicated M4A & AAC Audio Collection", "AAC"),
    OGG("Ogg Vorbis", "Dedicated Ogg Vorbis Audio Collection", "OGG")
}

@Composable
fun LibraryScreen(
    tracks: List<TrackEntity>,
    totalLibraryCount: Int,
    currentTrackId: Long?,
    isPlaying: Boolean,
    searchQuery: String,
    sortOption: SortOption,
    sortDirection: SortDirection,
    monitorStatus: FolderMonitorStatus,
    onSearchQueryChange: (String) -> Unit,
    onSortOptionSelect: (SortOption) -> Unit,
    onToggleSortDirection: () -> Unit,
    onImportFilesClick: () -> Unit,
    onWatchFolderClick: () -> Unit,
    onScanFoldersNow: () -> Unit,
    onGenerateStudioWav: () -> Unit,
    onPlayTrack: (List<TrackEntity>, Int) -> Unit,
    onShuffleAll: (List<TrackEntity>) -> Unit,
    onReorderTracks: (List<TrackEntity>) -> Unit,
    onAutoCompleteMetadata: (TrackEntity) -> Unit,
    onAddToPlaylist: (TrackEntity) -> Unit,
    onDeleteTrack: (TrackEntity) -> Unit,
    onActivePageChanged: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val photonColors = LocalPhotonColors.current
    val pages = LibraryFormatPage.entries
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { pages.size })

    LaunchedEffect(pagerState.currentPage) {
        onActivePageChanged(pagerState.currentPage)
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 1. Pinned Top Search Bar (right at the top of the screen)
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .testTag("library_search_input"),
            placeholder = {
                Text(
                    "Search songs, artist, genre, album, publisher, year...",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = "Search library",
                    tint = photonColors.photonGlow
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = photonColors.photonGlow,
                unfocusedBorderColor = photonColors.cardBorder,
                focusedContainerColor = photonColors.glassSurface,
                unfocusedContainerColor = photonColors.glassSurface
            )
        )

        // Minimal Page Indicator Strip (no filter tabs — swipe horizontally across dedicated pages)
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
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    pages.forEachIndexed { idx, _ ->
                        val isCurrent = idx == pagerState.currentPage
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(if (isCurrent) 18.dp else 6.dp)
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
                    text = if (pagerState.currentPage == 0) {
                        "Swipe for 6 codec pages"
                    } else {
                        "Page ${pagerState.currentPage + 1}/${pages.size}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 2. Dedicated Swipeable Pages (All Tracks, MP3, FLAC, WAV, Opus, M4A/AAC, Ogg Vorbis — zero tabs)
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("library_format_pager")
        ) { pageIndex ->
            val page = pages[pageIndex]
            val pageTracks = remember(tracks, page) {
                if (page.formatKey == null) {
                    tracks
                } else {
                    tracks.filter { it.format.equals(page.formatKey, ignoreCase = true) }
                }
            }

            DedicatedFormatTrackPage(
                page = page,
                pageTracks = pageTracks,
                totalLibraryCount = totalLibraryCount,
                currentTrackId = currentTrackId,
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
                onPlayTrack = onPlayTrack,
                onShuffleAll = onShuffleAll,
                onReorderTracks = onReorderTracks,
                onAutoCompleteMetadata = onAutoCompleteMetadata,
                onAddToPlaylist = onAddToPlaylist,
                onDeleteTrack = onDeleteTrack
            )
        }
    }
}

@Composable
private fun DedicatedFormatTrackPage(
    page: LibraryFormatPage,
    pageTracks: List<TrackEntity>,
    totalLibraryCount: Int,
    currentTrackId: Long?,
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
    onReorderTracks: (List<TrackEntity>) -> Unit,
    onAutoCompleteMetadata: (TrackEntity) -> Unit,
    onAddToPlaylist: (TrackEntity) -> Unit,
    onDeleteTrack: (TrackEntity) -> Unit
) {
    val photonColors = LocalPhotonColors.current
    val dragAllowed = page == LibraryFormatPage.ALL && searchQuery.isBlank()

    ReorderableTrackLazyColumn(
        tracks = pageTracks,
        currentTrackId = currentTrackId,
        isPlaying = isPlaying,
        dragEnabled = dragAllowed,
        onTrackClick = onPlayTrack,
        onTracksReordered = onReorderTracks,
        onAutoCompleteMetadata = onAutoCompleteMetadata,
        onAddToPlaylist = onAddToPlaylist,
        onRemoveTrack = onDeleteTrack,
        contentPadding = PaddingValues(top = 6.dp, bottom = 156.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("library_page_${page.name.lowercase()}"),
        headerContent = {
            if (page == LibraryFormatPage.ALL) {
                item(key = "hero_banner") {
                    PhotonLibraryHero(
                        totalTracks = totalLibraryCount,
                        monitorStatus = monitorStatus,
                        onImportFilesClick = onImportFilesClick,
                        onWatchFolderClick = onWatchFolderClick,
                        onScanFoldersNow = onScanFoldersNow,
                        onPlayAll = { if (pageTracks.isNotEmpty()) onPlayTrack(pageTracks, 0) },
                        onShuffleAll = { onShuffleAll(pageTracks) }
                    )
                }
            } else {
                item(key = "format_page_banner_${page.name}") {
                    FormatDedicatedHeroCard(
                        page = page,
                        trackCount = pageTracks.size,
                        onPlayAll = { if (pageTracks.isNotEmpty()) onPlayTrack(pageTracks, 0) },
                        onShuffleAll = { onShuffleAll(pageTracks) },
                        onImportFilesClick = onImportFilesClick
                    )
                }
            }

            // Comprehensive Sort Controls (Custom, Artist, Genre, Album, Publisher, Year, Title, Added)
            item(key = "sort_bar_${page.name}") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                                    modifier = Modifier.testTag("sort_chip_${page.name.lowercase()}_${option.name.lowercase()}"),
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
                                modifier = Modifier.testTag("toggle_sort_direction_${page.name.lowercase()}")
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

                    AnimatedVisibility(visible = pageTracks.isNotEmpty() && dragAllowed) {
                        Text(
                            text = "Tip: Long-press any track or drag its handle to reorder",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            if (pageTracks.isEmpty()) {
                item(key = "empty_state_${page.name}") {
                    EmptyLibraryCard(
                        page = page,
                        hasAnyTracks = totalLibraryCount > 0,
                        hasSearchQuery = searchQuery.isNotBlank(),
                        onImportFilesClick = onImportFilesClick,
                        onWatchFolderClick = onWatchFolderClick,
                        onGenerateStudioWav = onGenerateStudioWav
                    )
                }
            }
        }
    )
}

@Composable
private fun FormatDedicatedHeroCard(
    page: LibraryFormatPage,
    trackCount: Int,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onImportFilesClick: () -> Unit
) {
    val photonColors = LocalPhotonColors.current
    val accent = when (page) {
        LibraryFormatPage.FLAC -> photonColors.losslessBadge
        LibraryFormatPage.WAV -> photonColors.goldBadge
        LibraryFormatPage.OPUS -> Color(0xFF22D3EE)
        LibraryFormatPage.AAC -> photonColors.nebulaAccent
        LibraryFormatPage.OGG -> Color(0xFFF472B6)
        else -> photonColors.photonGlow
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(22.dp),
        color = photonColors.glassSurface,
        border = BorderStroke(1.2.dp, accent.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "${page.title} AUDIO",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = accent
                )
                Text(
                    text = page.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$trackCount ${page.title} track${if (trackCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (trackCount > 0) {
                    Surface(
                        onClick = onShuffleAll,
                        shape = CircleShape,
                        color = accent.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Shuffle,
                                contentDescription = "Shuffle ${page.title}",
                                tint = accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Surface(
                        onClick = onPlayAll,
                        shape = CircleShape,
                        color = accent,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                contentDescription = "Play all ${page.title}",
                                tint = Color(0xFF040818),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = onImportFilesClick,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Import")
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotonLibraryHero(
    totalTracks: Int,
    monitorStatus: FolderMonitorStatus,
    onImportFilesClick: () -> Unit,
    onWatchFolderClick: () -> Unit,
    onScanFoldersNow: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit
) {
    val photonColors = LocalPhotonColors.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.2.dp, photonColors.photonGlow.copy(alpha = 0.45f)),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_galaxy_hero),
                contentDescription = "Photon Galaxy cosmic nebula header",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(206.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(206.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0x99040818),
                                Color(0xCC070F2B),
                                Color(0xF2040818)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "PHOTON DECK",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFECF4FF)
                        )
                        Text(
                            text = "$totalTracks track${if (totalTracks == 1) "" else "s"} • MP3 · FLAC · WAV · Opus · AAC · Ogg",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF38E4FF),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (totalTracks > 0) {
                            Surface(
                                onClick = onShuffleAll,
                                shape = CircleShape,
                                color = Color(0x3338E4FF),
                                border = BorderStroke(1.dp, Color(0x8838E4FF)),
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("hero_shuffle_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.Shuffle,
                                        contentDescription = "Shuffle library",
                                        tint = Color(0xFFECF4FF),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Surface(
                                onClick = onPlayAll,
                                shape = CircleShape,
                                color = Color(0xFF38E4FF),
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("hero_play_all_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.PlayArrow,
                                        contentDescription = "Play all tracks",
                                        tint = Color(0xFF040818),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Live Folder Monitor Status Strip
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0x880B1A45),
                    border = BorderStroke(0.8.dp, Color(0x5538E4FF))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (monitorStatus.isScanning || monitorStatus.isEnrichingMetadata) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF38E4FF)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Radar,
                                contentDescription = "Folder monitor active",
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = monitorStatus.lastEventMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFD6E8FF),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            onClick = onScanFoldersNow,
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0x3338E4FF),
                            modifier = Modifier.testTag("scan_folders_now_chip")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Refresh,
                                    contentDescription = "Scan watched folders",
                                    tint = Color(0xFF38E4FF),
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Scan",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF38E4FF)
                                )
                            }
                        }
                    }
                }

                // Quick Import & Watch Folder Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onImportFilesClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("import_audio_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF38E4FF),
                            contentColor = Color(0xFF040818)
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) {
                        Icon(Icons.Rounded.AudioFile, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import Files", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = onWatchFolderClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("watch_folder_button"),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xAA38E4FF)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFECF4FF)),
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) {
                        Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Watch Folder", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLibraryCard(
    page: LibraryFormatPage,
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(22.dp),
        color = photonColors.glassSurface,
        border = BorderStroke(1.dp, photonColors.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(photonColors.photonGlow.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.LibraryMusic,
                    contentDescription = null,
                    tint = photonColors.photonGlow,
                    modifier = Modifier.size(32.dp)
                )
            }

            Text(
                text = when {
                    hasSearchQuery -> "No Matching ${page.title}"
                    page != LibraryFormatPage.ALL -> "No ${page.title} Files Yet"
                    hasAnyTracks -> "No Matching Tracks"
                    else -> "Your Photon Galaxy Is Ready"
                },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = when {
                    hasSearchQuery -> "Try clearing the top search bar to see all ${page.title.lowercase()} tracks."
                    page == LibraryFormatPage.WAV -> "Import WAV audio files from your device or generate playable studio WAV tracks below."
                    page != LibraryFormatPage.ALL -> "Import ${page.title} files or watch a music folder to automatically populate your dedicated ${page.title} page."
                    else -> "Import MP3, FLAC, WAV, Opus, M4A/AAC, or Ogg Vorbis files, link a watched folder for automatic import & web metadata discovery, or synthesize studio WAV tracks to test playback, EQ, and crossfade immediately."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!hasSearchQuery) {
                if (page == LibraryFormatPage.ALL || page == LibraryFormatPage.WAV) {
                    Button(
                        onClick = onGenerateStudioWav,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("generate_studio_wav_button_${page.name.lowercase()}"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generate Playable Studio WAV Tracks")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onImportFilesClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Pick Audio")
                    }
                    OutlinedButton(
                        onClick = onWatchFolderClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Watch Folder")
                    }
                }
            }
        }
    }
}
