package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueuePlayNext
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.TrackEntity
import com.example.data.db.WatchedFolderEntity
import com.example.data.model.SortDirection
import com.example.data.model.SortOption
import com.example.ui.components.AudioFormatBadge
import com.example.ui.components.PhotonAlbumArt
import com.example.ui.components.PhotonEqualizerBars
import com.example.ui.components.formatDurationMs
import com.example.ui.theme.LocalPhotonColors
import kotlinx.coroutines.launch

enum class LibraryCategoryTab(val label: String) {
    TRACKS("Tracks"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    GENRES("Genres"),
    COMPOSERS("Composers"),
    FOLDERS("Folders")
}

private val FLAGSHIP_SORT_OPTIONS = listOf(
    SortOption.TITLE,
    SortOption.DATE_ADDED,
    SortOption.DURATION,
    SortOption.BITRATE_CODEC,
    SortOption.PLAY_COUNT,
    SortOption.ARTIST,
    SortOption.ALBUM,
    SortOption.CUSTOM_ORDER
)

private val ALPHABET_INDEX = listOf("#") + ('A'..'Z').map { it.toString() }

@Composable
fun LibraryBrowserScreen(
    tracks: List<TrackEntity>,
    allTracks: List<TrackEntity>,
    watchedFolders: List<WatchedFolderEntity>,
    playCountMap: Map<Long, Int>,
    currentTrackId: Long?,
    isPlaying: Boolean,
    sortOption: SortOption,
    sortDirection: SortDirection,
    onSelectSortOption: (SortOption) -> Unit,
    onToggleSortDirection: () -> Unit,
    onPlayTracks: (List<TrackEntity>, Int, String) -> Unit,
    onShuffleTracks: (List<TrackEntity>, String) -> Unit,
    onPlayNext: (TrackEntity) -> Unit,
    onToggleFavorite: (TrackEntity) -> Unit,
    onAddToPlaylist: (TrackEntity) -> Unit,
    onInspectTrack: (TrackEntity) -> Unit,
    onNavigateToAlbum: (String, String?) -> Unit,
    onNavigateToArtist: (String) -> Unit,
    onImportAudio: () -> Unit,
    onWatchFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    val photonColors = LocalPhotonColors.current
    val scope = rememberCoroutineScope()
    var activeCategory by remember { mutableStateOf(LibraryCategoryTab.TRACKS) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var scrubbingLetter by remember { mutableStateOf<String?>(null) }

    val tracksListState = rememberLazyListState()

    val albums = remember(tracks) {
        tracks.groupBy { it.album.ifBlank { "Unknown Album" } }
            .entries
            .sortedBy { it.key.lowercase() }
            .map { (album, list) -> Triple(album, list.first().artist, list) }
    }

    val artists = remember(tracks) {
        tracks.groupBy { it.artist.ifBlank { "Unknown Artist" } }
            .entries
            .sortedBy { it.key.lowercase() }
            .map { it.key to it.value }
    }

    val genres = remember(tracks) {
        tracks.groupBy { it.genre.ifBlank { "Unknown Genre" } }
            .entries
            .sortedByDescending { it.value.size }
            .map { it.key to it.value }
    }

    val composers = remember(tracks) {
        tracks.groupBy { it.composer.ifBlank { "Uncredited Composer" } }
            .entries
            .sortedBy { it.key.lowercase() }
            .map { it.key to it.value }
    }

    val folderGroups = remember(allTracks, watchedFolders) {
        val folderNameLookup = watchedFolders.associate { it.uri to it.displayName }
        allTracks.groupBy { track ->
            track.folderUri?.takeIf { it.isNotBlank() }
                ?: runCatching {
                    val u = Uri.parse(track.uri)
                    u.path?.substringBeforeLast('/')?.ifBlank { "Device Storage" } ?: "Device Storage"
                }.getOrDefault("Device Storage")
        }.entries
            .sortedByDescending { it.value.size }
            .map { (folderKey, list) ->
                val display = folderNameLookup[folderKey]
                    ?: Uri.decode(folderKey).substringAfterLast('/').substringAfterLast(':').ifBlank { folderKey }
                Triple(folderKey, display, list)
            }
    }

    // Build A–Z jump map for Tracks list
    val letterIndexMap = remember(tracks) {
        val map = mutableMapOf<String, Int>()
        tracks.forEachIndexed { idx, track ->
            val ch = track.title.trim().firstOrNull()?.uppercaseChar()
            val bucket = if (ch != null && ch in 'A'..'Z') ch.toString() else "#"
            if (bucket !in map) map[bucket] = idx
        }
        map
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("upgraded_library_browser")
    ) {
        // Sticky Category Filter Bar
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(LibraryCategoryTab.entries, key = { it.name }) { cat ->
                val selected = cat == activeCategory
                FilterChip(
                    selected = selected,
                    onClick = { activeCategory = cat },
                    label = { Text(cat.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = photonColors.photonGlow.copy(alpha = 0.22f),
                        selectedLabelColor = photonColors.photonGlow
                    ),
                    modifier = Modifier.testTag("library_cat_${cat.name.lowercase()}")
                )
            }
        }

        // Toolbar: Count, Shuffle/Play All, Sort Menu
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { onShuffleTracks(tracks, "Library Shuffle") },
                    enabled = tracks.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = photonColors.photonGlow,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("library_shuffle_all_btn")
                ) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Shuffle (${tracks.size})", style = MaterialTheme.typography.labelMedium)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    OutlinedButton(
                        onClick = { sortMenuExpanded = true },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("library_sort_menu_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(sortOption.label, style = MaterialTheme.typography.labelSmall)
                    }
                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false }
                    ) {
                        FLAGSHIP_SORT_OPTIONS.forEach { opt ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = opt.label,
                                        fontWeight = if (opt == sortOption) FontWeight.Bold else FontWeight.Normal,
                                        color = if (opt == sortOption) photonColors.photonGlow else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    sortMenuExpanded = false
                                    onSelectSortOption(opt)
                                }
                            )
                        }
                    }
                }
                IconButton(
                    onClick = onToggleSortDirection,
                    modifier = Modifier.testTag("library_sort_dir_btn")
                ) {
                    Icon(
                        imageVector = if (sortDirection == SortDirection.ASC) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                        contentDescription = "Toggle sort direction",
                        tint = photonColors.photonGlow
                    )
                }
            }
        }

        if (tracks.isEmpty() && activeCategory != LibraryCategoryTab.FOLDERS) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = photonColors.photonGlow, modifier = Modifier.size(44.dp))
                    Text("No audio tracks in Library", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = onImportAudio) {
                            Icon(Icons.Rounded.LibraryAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Import Audio")
                        }
                        OutlinedButton(onClick = onWatchFolder) {
                            Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Watch Folder")
                        }
                    }
                }
            }
        } else {
            when (activeCategory) {
                LibraryCategoryTab.TRACKS -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = tracksListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 34.dp, top = 4.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(tracks, key = { _, t -> t.id }) { idx, track ->
                                LibraryTrackItemRow(
                                    track = track,
                                    playCount = playCountMap[track.id] ?: 0,
                                    isCurrent = track.id == currentTrackId,
                                    isPlaying = isPlaying,
                                    onClick = { onPlayTracks(tracks, idx, "Library Tracks") },
                                    onPlayNext = { onPlayNext(track) },
                                    onToggleFavorite = { onToggleFavorite(track) },
                                    onAddToPlaylist = { onAddToPlaylist(track) },
                                    onInspect = { onInspectTrack(track) },
                                    onOpenArtist = { onNavigateToArtist(track.artist) },
                                    onOpenAlbum = { onNavigateToAlbum(track.album, track.artist) }
                                )
                            }
                        }

                        // Sticky A–Z Fast-Scroll Scrubber on Trailing Edge
                        AlphabetFastScrubber(
                            onLetterSelected = { letter ->
                                scrubbingLetter = letter
                                val targetIdx = letterIndexMap[letter]
                                    ?: letterIndexMap.entries.firstOrNull { it.key >= letter }?.value
                                if (targetIdx != null) {
                                    scope.launch { tracksListState.scrollToItem(targetIdx) }
                                }
                            },
                            onScrubEnd = { scrubbingLetter = null },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 4.dp, top = 8.dp, bottom = 8.dp)
                                .testTag("library_az_scrubber")
                        )

                        scrubbingLetter?.let { activeLetter ->
                            Surface(
                                shape = CircleShape,
                                color = photonColors.photonGlow,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = activeLetter,
                                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                LibraryCategoryTab.ALBUMS -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(albums, key = { it.first }) { (albumName, artistName, list) ->
                            CollectionSummaryCard(
                                title = albumName,
                                subtitle = "$artistName · ${list.size} tracks · ${formatDurationMs(list.sumOf { it.durationMs })}",
                                artworkUri = list.firstNotNullOfOrNull { it.artworkUri },
                                fallbackIcon = Icons.Rounded.Album,
                                onClick = { onNavigateToAlbum(albumName, artistName) },
                                onPlayAll = { onPlayTracks(list, 0, "Album: $albumName") }
                            )
                        }
                    }
                }

                LibraryCategoryTab.ARTISTS -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(artists, key = { it.first }) { (artistName, list) ->
                            val albumCount = list.map { it.album }.distinct().size
                            CollectionSummaryCard(
                                title = artistName,
                                subtitle = "$albumCount album(s) · ${list.size} tracks",
                                artworkUri = list.firstNotNullOfOrNull { it.artworkUri },
                                fallbackIcon = Icons.Rounded.Person,
                                onClick = { onNavigateToArtist(artistName) },
                                onPlayAll = { onPlayTracks(list, 0, "Artist: $artistName") }
                            )
                        }
                    }
                }

                LibraryCategoryTab.GENRES -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(genres, key = { it.first }) { (genre, list) ->
                            CollectionSummaryCard(
                                title = genre,
                                subtitle = "${list.size} tracks · ${formatDurationMs(list.sumOf { it.durationMs })}",
                                artworkUri = list.firstNotNullOfOrNull { it.artworkUri },
                                fallbackIcon = Icons.Rounded.Category,
                                onClick = { onPlayTracks(list, 0, "Genre: $genre") },
                                onPlayAll = { onPlayTracks(list, 0, "Genre: $genre") }
                            )
                        }
                    }
                }

                LibraryCategoryTab.COMPOSERS -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(composers, key = { it.first }) { (composer, list) ->
                            CollectionSummaryCard(
                                title = composer,
                                subtitle = "${list.size} tracks · ${list.map { it.artist }.distinct().take(2).joinToString(", ")}",
                                artworkUri = list.firstNotNullOfOrNull { it.artworkUri },
                                fallbackIcon = Icons.Rounded.MusicNote,
                                onClick = { onPlayTracks(list, 0, "Composer: $composer") },
                                onPlayAll = { onPlayTracks(list, 0, "Composer: $composer") }
                            )
                        }
                    }
                }

                LibraryCategoryTab.FOLDERS -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item(key = "add_watch_folder_btn") {
                            OutlinedButton(
                                onClick = onWatchFolder,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("folders_add_watch_btn")
                            ) {
                                Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Add Watched Folder (SAF)")
                            }
                        }
                        items(folderGroups, key = { it.first }) { (uriKey, displayName, list) ->
                            CollectionSummaryCard(
                                title = displayName,
                                subtitle = "${list.size} tracks · ${Uri.decode(uriKey).takeLast(38)}",
                                artworkUri = list.firstNotNullOfOrNull { it.artworkUri },
                                fallbackIcon = Icons.Rounded.Folder,
                                onClick = { onPlayTracks(list, 0, "Folder: $displayName") },
                                onPlayAll = { onPlayTracks(list, 0, "Folder: $displayName") }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlphabetFastScrubber(
    onLetterSelected: (String) -> Unit,
    onScrubEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val photonColors = LocalPhotonColors.current
    var heightPx by remember { mutableStateOf(1) }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(photonColors.glassSurface.copy(alpha = 0.65f))
            .onSizeChanged { heightPx = it.height.coerceAtLeast(1) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = onScrubEnd,
                    onDragCancel = onScrubEnd,
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        val fraction = (change.position.y / heightPx.toFloat()).coerceIn(0f, 0.999f)
                        val idx = (fraction * ALPHABET_INDEX.size).toInt().coerceIn(0, ALPHABET_INDEX.lastIndex)
                        onLetterSelected(ALPHABET_INDEX[idx])
                    }
                )
            }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        ALPHABET_INDEX.forEach { letter ->
            Text(
                text = letter,
                fontSize = 9.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.Bold,
                color = photonColors.photonGlow.copy(alpha = 0.9f),
                modifier = Modifier.clickable {
                    onLetterSelected(letter)
                    onScrubEnd()
                }
            )
        }
    }
}

@Composable
private fun LibraryTrackItemRow(
    track: TrackEntity,
    playCount: Int,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onInspect: () -> Unit,
    onOpenArtist: () -> Unit,
    onOpenAlbum: () -> Unit
) {
    val photonColors = LocalPhotonColors.current
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isCurrent) photonColors.photonGlow.copy(alpha = 0.14f) else photonColors.glassSurface,
        border = BorderStroke(
            width = if (isCurrent) 1.2.dp else 0.8.dp,
            color = if (isCurrent) photonColors.photonGlow else photonColors.cardBorder.copy(alpha = 0.65f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PhotonAlbumArt(track.artworkUri, track.title, size = 48.dp, cornerRadius = 10.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isCurrent) photonColors.photonGlow else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AudioFormatBadge(track.format)
                    Text(
                        text = buildString {
                            append(track.artist)
                            append(" · ")
                            append(formatDurationMs(track.durationMs))
                            if (playCount > 0) append(" · ${playCount}x")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (isCurrent) {
                PhotonEqualizerBars(isPlaying = isPlaying)
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Track actions")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Play Next") },
                        leadingIcon = { Icon(Icons.Rounded.QueuePlayNext, contentDescription = null) },
                        onClick = { menuExpanded = false; onPlayNext() }
                    )
                    DropdownMenuItem(
                        text = { Text(if (track.isFavorite) "Remove Favorite" else "Favorite") },
                        leadingIcon = { Icon(if (track.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, contentDescription = null) },
                        onClick = { menuExpanded = false; onToggleFavorite() }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to Playlist") },
                        leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, contentDescription = null) },
                        onClick = { menuExpanded = false; onAddToPlaylist() }
                    )
                    DropdownMenuItem(
                        text = { Text("Lyrics & Tag Inspector") },
                        leadingIcon = { Icon(Icons.Rounded.EditNote, contentDescription = null) },
                        onClick = { menuExpanded = false; onInspect() }
                    )
                    DropdownMenuItem(
                        text = { Text("Go to Artist") },
                        leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                        onClick = { menuExpanded = false; onOpenArtist() }
                    )
                    DropdownMenuItem(
                        text = { Text("Go to Album") },
                        leadingIcon = { Icon(Icons.Rounded.Album, contentDescription = null) },
                        onClick = { menuExpanded = false; onOpenAlbum() }
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectionSummaryCard(
    title: String,
    subtitle: String,
    artworkUri: String?,
    fallbackIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    onPlayAll: () -> Unit
) {
    val photonColors = LocalPhotonColors.current
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = photonColors.glassSurface,
        border = BorderStroke(0.9.dp, photonColors.cardBorder.copy(alpha = 0.7f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!artworkUri.isNullOrBlank()) {
                PhotonAlbumArt(artworkUri, title, size = 54.dp, cornerRadius = 12.dp)
            } else {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(photonColors.photonGlow.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(fallbackIcon, contentDescription = null, tint = photonColors.photonGlow, modifier = Modifier.size(26.dp))
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onPlayAll) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play $title", tint = photonColors.photonGlow)
            }
        }
    }
}
