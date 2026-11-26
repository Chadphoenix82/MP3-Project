package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.db.PlaylistWithSummary
import com.example.data.db.TrackEntity
import com.example.ui.components.TrackRowItem
import com.example.ui.components.formatDurationMs
import com.example.ui.theme.LocalPhotonColors

private enum class SmartRuleType(val title: String, val subtitle: String, val icon: ImageVector) {
    FAVORITES("Favorites", "Hearted audiophile picks", Icons.Rounded.Favorite),
    LONG_TRACKS("Tracks > 5 mins", "Extended & progressive cuts", Icons.Rounded.Schedule),
    LAST_30_DAYS("Added in Last 30 Days", "Freshly imported arrivals", Icons.Rounded.NewReleases)
}

@Composable
fun PlaylistsHubScreen(
    playlists: List<PlaylistWithSummary>,
    playlistCollages: Map<Long, List<String>>,
    allTracks: List<TrackEntity>,
    selectedPlaylistId: Long?,
    selectedPlaylistTracks: List<TrackEntity>,
    currentTrackId: Long?,
    isPlaying: Boolean,
    onSelectPlaylist: (Long?) -> Unit,
    onCreatePlaylist: (String, String) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onReorderPlaylistTracks: (Long, List<TrackEntity>) -> Unit,
    onRemoveTrackFromPlaylist: (Long, TrackEntity) -> Unit,
    onPlayTracks: (List<TrackEntity>, Int, String) -> Unit,
    onShuffleTracks: (List<TrackEntity>, String) -> Unit,
    onOpenTrackMenu: (TrackEntity) -> Unit,
    onRequestExportM3u8: (playlistName: String, tracks: List<TrackEntity>) -> Unit,
    onRequestImportM3u8: () -> Unit,
    modifier: Modifier = Modifier
) {
    val photonColors = LocalPhotonColors.current
    var showCreateDialog by remember { mutableStateOf(false) }
    var activeSmartRule by remember { mutableStateOf<SmartRuleType?>(null) }

    val thirtyDaysAgo = remember { System.currentTimeMillis() - 30L * 24L * 60L * 60L * 1000L }
    val smartTracksMap = remember(allTracks, thirtyDaysAgo) {
        mapOf(
            SmartRuleType.FAVORITES to allTracks.filter { it.isFavorite },
            SmartRuleType.LONG_TRACKS to allTracks.filter { it.durationMs >= 300_000L },
            SmartRuleType.LAST_30_DAYS to allTracks.filter { it.dateAdded >= thirtyDaysAgo }.sortedByDescending { it.dateAdded }
        )
    }

    val selectedPlaylist = playlists.firstOrNull { it.id == selectedPlaylistId }

    // Detail View for Smart Rule Playlist OR Custom Playlist
    if (activeSmartRule != null) {
        val rule = activeSmartRule!!
        val tracks = smartTracksMap[rule].orEmpty()
        PlaylistDetailPane(
            title = rule.title,
            subtitle = "${rule.subtitle} · ${tracks.size} tracks",
            collageUris = tracks.mapNotNull { it.artworkUri?.takeIf(String::isNotBlank) }.distinct().take(4),
            tracks = tracks,
            currentTrackId = currentTrackId,
            isPlaying = isPlaying,
            allowReorderAndRemove = false,
            onBack = { activeSmartRule = null },
            onPlayAll = { onPlayTracks(tracks, 0, rule.title) },
            onShuffle = { onShuffleTracks(tracks, rule.title) },
            onExportM3u8 = { onRequestExportM3u8(rule.title, tracks) },
            onTrackClick = { ordered, idx -> onPlayTracks(ordered, idx, rule.title) },
            onOpenTrackMenu = onOpenTrackMenu,
            onCommitReorder = {},
            onRemoveTrack = {},
            modifier = modifier
        )
        return
    }

    if (selectedPlaylist != null) {
        PlaylistDetailPane(
            title = selectedPlaylist.name,
            subtitle = selectedPlaylist.description.ifBlank { "${selectedPlaylistTracks.size} tracks" },
            collageUris = playlistCollages[selectedPlaylist.id].orEmpty(),
            tracks = selectedPlaylistTracks,
            currentTrackId = currentTrackId,
            isPlaying = isPlaying,
            allowReorderAndRemove = true,
            onBack = { onSelectPlaylist(null) },
            onPlayAll = { onPlayTracks(selectedPlaylistTracks, 0, "Playlist: ${selectedPlaylist.name}") },
            onShuffle = { onShuffleTracks(selectedPlaylistTracks, "Playlist: ${selectedPlaylist.name}") },
            onExportM3u8 = { onRequestExportM3u8(selectedPlaylist.name, selectedPlaylistTracks) },
            onTrackClick = { ordered, idx -> onPlayTracks(ordered, idx, "Playlist: ${selectedPlaylist.name}") },
            onOpenTrackMenu = onOpenTrackMenu,
            onCommitReorder = { reordered -> onReorderPlaylistTracks(selectedPlaylist.id, reordered) },
            onRemoveTrack = { track -> onRemoveTrackFromPlaylist(selectedPlaylist.id, track) },
            modifier = modifier
        )
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("playlists_hub_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Action Bar: New Playlist + Import .m3u8
        item(key = "playlist_actions") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { showCreateDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = photonColors.photonGlow,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("create_playlist_btn")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("New Playlist")
                }
                OutlinedButton(
                    onClick = onRequestImportM3u8,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("import_m3u8_btn")
                ) {
                    Icon(Icons.Rounded.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Import .m3u8")
                }
            }
        }

        // Smart Rule Playlists Section
        item(key = "smart_rules_header") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = photonColors.photonGlow, modifier = Modifier.size(18.dp))
                Text(
                    text = "SMART RULE PLAYLISTS",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = photonColors.photonGlow
                )
            }
        }

        items(SmartRuleType.entries, key = { "smart_${it.name}" }) { rule ->
            val tracks = smartTracksMap[rule].orEmpty()
            val artUris = tracks.mapNotNull { it.artworkUri?.takeIf(String::isNotBlank) }.distinct().take(4)
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = photonColors.glassSurface,
                border = BorderStroke(0.9.dp, photonColors.photonGlow.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { activeSmartRule = rule }
                    .testTag("smart_playlist_${rule.name.lowercase()}")
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PlaylistCollageArtwork(uris = artUris, fallbackIcon = rule.icon, size = 56.dp)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(rule.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        Text("${tracks.size} tracks · ${rule.subtitle}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { onPlayTracks(tracks, 0, rule.title) }, enabled = tracks.isNotEmpty()) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = "Play ${rule.title}", tint = photonColors.photonGlow)
                    }
                }
            }
        }

        // Custom Playlists Section
        item(key = "custom_playlists_header") {
            Text(
                text = "YOUR PLAYLISTS (${playlists.size})",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = photonColors.photonGlow,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        if (playlists.isEmpty()) {
            item(key = "empty_custom_playlists") {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = photonColors.glassSurface,
                    border = BorderStroke(0.9.dp, photonColors.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No custom playlists yet. Tap New Playlist or Import .m3u8 above to create one.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            }
        } else {
            items(playlists, key = { "pl_${it.id}" }) { pl ->
                val collage = playlistCollages[pl.id]
                    ?: listOfNotNull(pl.coverUri?.takeIf(String::isNotBlank))
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = photonColors.glassSurface,
                    border = BorderStroke(0.9.dp, photonColors.cardBorder.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onSelectPlaylist(pl.id) }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PlaylistCollageArtwork(uris = collage, fallbackIcon = Icons.Rounded.QueueMusic, size = 56.dp)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(pl.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = "${pl.trackCount} tracks${if (pl.description.isNotBlank()) " · ${pl.description}" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { onDeletePlaylist(pl.id) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete ${pl.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create Playlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description (optional)") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreatePlaylist(name, desc)
                        showCreateDialog = false
                    },
                    enabled = name.isNotBlank()
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PlaylistDetailPane(
    title: String,
    subtitle: String,
    collageUris: List<String>,
    tracks: List<TrackEntity>,
    currentTrackId: Long?,
    isPlaying: Boolean,
    allowReorderAndRemove: Boolean,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onExportM3u8: () -> Unit,
    onTrackClick: (List<TrackEntity>, Int) -> Unit,
    onOpenTrackMenu: (TrackEntity) -> Unit,
    onCommitReorder: (List<TrackEntity>) -> Unit,
    onRemoveTrack: (TrackEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val photonColors = LocalPhotonColors.current
    val haptic = LocalHapticFeedback.current

    var localTracks by remember { mutableStateOf(tracks) }
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var itemHeightPx by remember { mutableFloatStateOf(168f) }

    LaunchedEffect(tracks) {
        if (draggedIndex == -1) {
            localTracks = tracks
        }
    }

    fun startDrag(index: Int) {
        if (!allowReorderAndRemove || index !in localTracks.indices) return
        draggedIndex = index
        dragOffsetY = 0f
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun onDragDelta(deltaY: Float) {
        if (draggedIndex == -1) return
        dragOffsetY += deltaY
        val threshold = (itemHeightPx * 0.6f).coerceAtLeast(60f)

        while (dragOffsetY > threshold && draggedIndex < localTracks.lastIndex) {
            val next = draggedIndex + 1
            val mutable = localTracks.toMutableList()
            val item = mutable.removeAt(draggedIndex)
            mutable.add(next, item)
            localTracks = mutable
            draggedIndex = next
            dragOffsetY -= itemHeightPx
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        while (dragOffsetY < -threshold && draggedIndex > 0) {
            val prev = draggedIndex - 1
            val mutable = localTracks.toMutableList()
            val item = mutable.removeAt(draggedIndex)
            mutable.add(prev, item)
            localTracks = mutable
            draggedIndex = prev
            dragOffsetY += itemHeightPx
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    fun finishDrag() {
        if (draggedIndex != -1) {
            val committed = localTracks.toList()
            draggedIndex = -1
            dragOffsetY = 0f
            onCommitReorder(committed)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to playlists", tint = photonColors.photonGlow)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
                onClick = onExportM3u8,
                enabled = localTracks.isNotEmpty(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.testTag("export_m3u8_btn")
            ) {
                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(".m3u8", style = MaterialTheme.typography.labelSmall)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlaylistCollageArtwork(uris = collageUris, fallbackIcon = Icons.Rounded.QueueMusic, size = 76.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${localTracks.size} tracks · ${formatDurationMs(localTracks.sumOf { it.durationMs })}",
                    style = MaterialTheme.typography.labelMedium,
                    color = photonColors.photonGlow
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onPlayAll,
                        enabled = localTracks.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = photonColors.photonGlow, contentColor = MaterialTheme.colorScheme.onPrimary)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Play All")
                    }
                    OutlinedButton(onClick = onShuffle, enabled = localTracks.isNotEmpty()) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Shuffle")
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(localTracks, key = { _, t -> "pl_track_${t.id}" }) { idx, track ->
                val isDraggingThis = idx == draggedIndex
                val liftScale by animateFloatAsState(
                    targetValue = if (isDraggingThis) 1.025f else 1f,
                    label = "playlist_drag_scale"
                )
                val handleModifier = if (allowReorderAndRemove) {
                    Modifier.pointerInput(track.id) {
                        detectDragGestures(
                            onDragStart = {
                                val currentIdx = localTracks.indexOfFirst { it.id == track.id }
                                if (currentIdx >= 0) startDrag(currentIdx)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDragDelta(dragAmount.y)
                            },
                            onDragEnd = { finishDrag() },
                            onDragCancel = { finishDrag() }
                        )
                    }
                } else null

                TrackRowItem(
                    track = track,
                    isCurrent = track.id == currentTrackId,
                    isPlaying = isPlaying,
                    isDragging = isDraggingThis,
                    onClick = { onTrackClick(localTracks, idx) },
                    onOpenTrackMenu = onOpenTrackMenu,
                    dragHandleModifier = handleModifier,
                    extraTrailingContent = if (allowReorderAndRemove) {
                        {
                            IconButton(
                                onClick = { onRemoveTrack(track) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.RemoveCircleOutline,
                                    contentDescription = "Remove from playlist",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else null,
                    rowTestTag = "playlist_track_row_${track.id}",
                    modifier = Modifier
                        .zIndex(if (isDraggingThis) 10f else 0f)
                        .onSizeChanged { size ->
                            if (size.height > 0) itemHeightPx = size.height.toFloat()
                        }
                        .graphicsLayer {
                            translationY = if (isDraggingThis) dragOffsetY else 0f
                            scaleX = liftScale
                            scaleY = liftScale
                        }
                )
            }
        }
    }
}

/**
 * Auto-generated 4-tile cover art collage for playlists (or clean 1-tile / icon fallback).
 */
@Composable
fun PlaylistCollageArtwork(
    uris: List<String>,
    fallbackIcon: ImageVector,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val photonColors = LocalPhotonColors.current
    val context = LocalContext.current
    val cleaned = uris.filter { it.isNotBlank() }.take(4)

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(photonColors.photonGlow.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        when {
            cleaned.isEmpty() -> {
                Icon(fallbackIcon, contentDescription = null, tint = photonColors.photonGlow, modifier = Modifier.size(size * 0.45f))
            }
            cleaned.size < 4 -> {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(cleaned.first()).crossfade(true).build(),
                    contentDescription = "Playlist cover art",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                val half = size / 2
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.weight(1f)) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(cleaned[0]).crossfade(true).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(half)
                        )
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(cleaned[1]).crossfade(true).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(half)
                        )
                    }
                    Row(modifier = Modifier.weight(1f)) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(cleaned[2]).crossfade(true).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(half)
                        )
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(cleaned[3]).crossfade(true).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(half)
                        )
                    }
                }
            }
        }
    }
}
