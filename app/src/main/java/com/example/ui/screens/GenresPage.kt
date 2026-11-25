package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.db.TrackEntity
import com.example.ui.components.AudioFormatBadge
import com.example.ui.components.PhotonAlbumArt
import com.example.ui.components.PhotonEqualizerBars
import com.example.ui.components.formatDurationMs
import com.example.ui.theme.LocalPhotonColors

data class GenreFolderGroup(
    val name: String,
    val tracks: List<TrackEntity>,
    val totalDurationMs: Long,
    val sampleArtworkUri: String?
)

/**
 * Groups library tracks into normalized music genre folders.
 */
fun buildGenreFolders(tracks: List<TrackEntity>): List<GenreFolderGroup> {
    if (tracks.isEmpty()) return emptyList()
    val grouped = linkedMapOf<String, MutableList<TrackEntity>>()
    val displayNames = mutableMapOf<String, String>()

    tracks.forEach { track ->
        val raw = track.genre.trim()
        val display = if (raw.isBlank() || raw.equals("Unknown Genre", ignoreCase = true)) {
            "Uncategorized"
        } else {
            raw
        }
        val key = display.lowercase()
        displayNames.putIfAbsent(key, display)
        grouped.getOrPut(key) { mutableListOf() }.add(track)
    }

    return grouped.entries
        .map { (key, list) ->
            GenreFolderGroup(
                name = displayNames[key] ?: "Uncategorized",
                tracks = list,
                totalDurationMs = list.sumOf { it.durationMs.coerceAtLeast(0L) },
                sampleArtworkUri = list.firstOrNull { !it.artworkUri.isNullOrBlank() }?.artworkUri
            )
        }
        .sortedWith(
            compareByDescending<GenreFolderGroup> { it.tracks.size }
                .thenBy { it.name.lowercase() }
        )
}

/**
 * Dedicated swipeable Genres page:
 * Displays every music genre as an interactive folder; tapping a genre folder opens all songs
 * categorized under that genre.
 */
@Composable
fun GenresFolderPage(
    tracks: List<TrackEntity>,
    totalLibraryCount: Int,
    currentTrackId: Long?,
    isPlaying: Boolean,
    searchQuery: String,
    selectedGenreName: String?,
    onSelectGenre: (String?) -> Unit,
    onPlayTrack: (List<TrackEntity>, Int) -> Unit,
    onShuffleAll: (List<TrackEntity>) -> Unit,
    onAutoCompleteMetadata: (TrackEntity) -> Unit,
    onAddToPlaylist: (TrackEntity) -> Unit,
    onDeleteTrack: (TrackEntity) -> Unit,
    onImportFilesClick: () -> Unit,
    onWatchFolderClick: () -> Unit,
    onGenerateStudioWav: () -> Unit,
    modifier: Modifier = Modifier
) {
    val genreFolders = remember(tracks) { buildGenreFolders(tracks) }
    val activeFolder = remember(genreFolders, selectedGenreName) {
        if (selectedGenreName == null) null
        else genreFolders.firstOrNull { it.name.equals(selectedGenreName, ignoreCase = true) }
    }

    if (activeFolder != null) {
        BackHandler { onSelectGenre(null) }
        GenreFolderDetailView(
            folder = activeFolder,
            currentTrackId = currentTrackId,
            isPlaying = isPlaying,
            onBack = { onSelectGenre(null) },
            onPlayTrack = onPlayTrack,
            onShuffleAll = onShuffleAll,
            onAutoCompleteMetadata = onAutoCompleteMetadata,
            onAddToPlaylist = onAddToPlaylist,
            onDeleteTrack = onDeleteTrack,
            modifier = modifier
        )
    } else {
        GenreFoldersGridView(
            folders = genreFolders,
            totalLibraryCount = totalLibraryCount,
            hasSearchQuery = searchQuery.isNotBlank(),
            onOpenFolder = { onSelectGenre(it.name) },
            onPlayFolder = { folder -> if (folder.tracks.isNotEmpty()) onPlayTrack(folder.tracks, 0) },
            onImportFilesClick = onImportFilesClick,
            onWatchFolderClick = onWatchFolderClick,
            onGenerateStudioWav = onGenerateStudioWav,
            modifier = modifier
        )
    }
}

@Composable
private fun GenreFoldersGridView(
    folders: List<GenreFolderGroup>,
    totalLibraryCount: Int,
    hasSearchQuery: Boolean,
    onOpenFolder: (GenreFolderGroup) -> Unit,
    onPlayFolder: (GenreFolderGroup) -> Unit,
    onImportFilesClick: () -> Unit,
    onWatchFolderClick: () -> Unit,
    onGenerateStudioWav: () -> Unit,
    modifier: Modifier = Modifier
) {
    val photonColors = LocalPhotonColors.current
    val folderAccents = listOf(
        photonColors.photonGlow,
        photonColors.nebulaAccent,
        photonColors.losslessBadge,
        photonColors.goldBadge,
        Color(0xFFF472B6),
        Color(0xFF22D3EE)
    )

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 158.dp),
        modifier = modifier
            .fillMaxSize()
            .testTag("genres_folder_grid"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 156.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "genres_header", span = { GridItemSpan(maxLineSpan) }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = photonColors.glassSurface,
                border = BorderStroke(1.dp, photonColors.photonGlow.copy(alpha = 0.45f))
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
                            text = "MUSIC GENRES",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${folders.size} genre folder${if (folders.size == 1) "" else "s"} · Tap any folder to browse songs",
                            style = MaterialTheme.typography.labelMedium,
                            color = photonColors.photonGlow
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(photonColors.photonGlow.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Folder,
                            contentDescription = "Genre folders",
                            tint = photonColors.photonGlow,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        if (folders.isEmpty()) {
            item(key = "genres_empty", span = { GridItemSpan(maxLineSpan) }) {
                Surface(
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
                        Icon(
                            imageVector = Icons.Rounded.Folder,
                            contentDescription = null,
                            tint = photonColors.photonGlow,
                            modifier = Modifier.size(42.dp)
                        )
                        Text(
                            text = if (hasSearchQuery) "No Matching Genre Folders" else "No Genre Folders Yet",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = if (hasSearchQuery) {
                                "Try clearing the search bar to see all music genre folders."
                            } else if (totalLibraryCount == 0) {
                                "Import audio files or generate studio WAV tracks to organize your music automatically into genre folders."
                            } else {
                                "No tracks matched this view."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!hasSearchQuery && totalLibraryCount == 0) {
                            Button(
                                onClick = onGenerateStudioWav,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("genres_generate_wav_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Generate Studio WAV Tracks")
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
                                    Text("Import Files")
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
        } else {
            items(
                items = folders,
                key = { it.name.lowercase() }
            ) { folder ->
                val accent = folderAccents[(folder.name.hashCode().and(0x7fffffff)) % folderAccents.size]
                GenreFolderCard(
                    folder = folder,
                    accent = accent,
                    onClick = { onOpenFolder(folder) },
                    onQuickPlay = { onPlayFolder(folder) }
                )
            }
        }
    }
}

@Composable
private fun GenreFolderCard(
    folder: GenreFolderGroup,
    accent: Color,
    onClick: () -> Unit,
    onQuickPlay: () -> Unit
) {
    val photonColors = LocalPhotonColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("genre_folder_${folder.name.lowercase().replace(" ", "_")}")
    ) {
        // Stylized folder tab lip on top-left
        Box(
            modifier = Modifier
                .padding(start = 14.dp)
                .width(64.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .background(accent.copy(alpha = 0.52f))
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(
                topStart = 6.dp,
                topEnd = 18.dp,
                bottomStart = 18.dp,
                bottomEnd = 18.dp
            ),
            color = photonColors.glassSurface,
            border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                accent.copy(alpha = 0.14f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!folder.sampleArtworkUri.isNullOrBlank()) {
                            PhotonAlbumArt(
                                artworkUri = folder.sampleArtworkUri,
                                contentDescription = "${folder.name} folder artwork",
                                size = 44.dp,
                                cornerRadius = 11.dp
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(accent.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Folder,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }

                        Surface(
                            onClick = onQuickPlay,
                            shape = CircleShape,
                            color = accent.copy(alpha = 0.2f),
                            border = BorderStroke(0.9.dp, accent.copy(alpha = 0.65f)),
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("genre_quick_play_${folder.name.lowercase().replace(" ", "_")}")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = "Play ${folder.name}",
                                    tint = accent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = folder.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${folder.tracks.size} song${if (folder.tracks.size == 1) "" else "s"} · ${formatDurationMs(folder.totalDurationMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GenreFolderDetailView(
    folder: GenreFolderGroup,
    currentTrackId: Long?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlayTrack: (List<TrackEntity>, Int) -> Unit,
    onShuffleAll: (List<TrackEntity>) -> Unit,
    onAutoCompleteMetadata: (TrackEntity) -> Unit,
    onAddToPlaylist: (TrackEntity) -> Unit,
    onDeleteTrack: (TrackEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val photonColors = LocalPhotonColors.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("genre_detail_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 156.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "genre_detail_header") {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = photonColors.glassSurface,
                border = BorderStroke(1.dp, photonColors.photonGlow.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("genre_detail_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back to genre folders",
                                tint = photonColors.photonGlow
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(photonColors.photonGlow.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Folder,
                                contentDescription = null,
                                tint = photonColors.photonGlow,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = folder.name,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${folder.tracks.size} song${if (folder.tracks.size == 1) "" else "s"} · ${formatDurationMs(folder.totalDurationMs)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = photonColors.photonGlow
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onPlayTrack(folder.tracks, 0) },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("genre_play_all_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Play All")
                        }
                        OutlinedButton(
                            onClick = { onShuffleAll(folder.tracks) },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("genre_shuffle_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Shuffle")
                        }
                    }
                }
            }
        }

        itemsIndexed(
            items = folder.tracks,
            key = { _, item -> item.id }
        ) { index, track ->
            val isCurrent = track.id == currentTrackId
            var menuExpanded by remember { mutableStateOf(false) }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("genre_track_${track.id}")
                    .clickable { onPlayTrack(folder.tracks, index) },
                shape = RoundedCornerShape(16.dp),
                color = if (isCurrent) MaterialTheme.colorScheme.surfaceContainerHigh else photonColors.glassSurface,
                border = BorderStroke(
                    if (isCurrent) 1.3.dp else 0.85.dp,
                    if (isCurrent) photonColors.photonGlow.copy(alpha = 0.78f) else photonColors.cardBorder.copy(alpha = 0.65f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PhotonAlbumArt(
                        artworkUri = track.artworkUri,
                        contentDescription = "Album art for ${track.title}",
                        size = 48.dp,
                        cornerRadius = 11.dp,
                        isPlaying = isCurrent && isPlaying
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isCurrent) photonColors.photonGlow else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (isCurrent) {
                                PhotonEqualizerBars(isPlaying = isPlaying)
                            }
                        }
                        Text(
                            text = "${track.artist} • ${track.album}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AudioFormatBadge(format = track.format)
                            Text(
                                text = formatDurationMs(track.durationMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "Options for ${track.title}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit Metadata") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = photonColors.photonGlow)
                                },
                                onClick = {
                                    menuExpanded = false
                                    onAutoCompleteMetadata(track)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Add to Playlist") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.PlaylistAdd, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    onAddToPlaylist(track)
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
                                    onDeleteTrack(track)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
