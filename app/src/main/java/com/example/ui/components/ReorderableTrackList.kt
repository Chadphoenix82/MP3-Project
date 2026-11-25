package com.example.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.data.db.TrackEntity
import com.example.ui.theme.LocalPhotonColors

/**
 * Seamless drag-and-drop song list supporting BOTH:
 * 1) Long-pressing anywhere on a track row to lift & sort, AND
 * 2) Direct drag on the right-side drag grip icon for immediate reordering.
 */
@Composable
fun ReorderableTrackLazyColumn(
    tracks: List<TrackEntity>,
    currentTrackId: Long?,
    isPlaying: Boolean,
    dragEnabled: Boolean,
    onTrackClick: (List<TrackEntity>, Int) -> Unit,
    onTracksReordered: (List<TrackEntity>) -> Unit,
    onAutoCompleteMetadata: (TrackEntity) -> Unit,
    onAddToPlaylist: ((TrackEntity) -> Unit)? = null,
    onRemoveTrack: (TrackEntity) -> Unit,
    removeLabel: String = "Remove from Library",
    contentPadding: PaddingValues = PaddingValues(bottom = 148.dp),
    headerContent: (LazyListScope.() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()

    var localTracks by remember { mutableStateOf(tracks) }
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var itemHeightPx by remember { mutableFloatStateOf(180f) }

    // Sync with upstream Room updates whenever user is not actively dragging
    LaunchedEffect(tracks) {
        if (draggedIndex == -1) {
            localTracks = tracks
        }
    }

    fun startDrag(index: Int) {
        if (!dragEnabled || index !in localTracks.indices) return
        draggedIndex = index
        dragOffsetY = 0f
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun onDragDelta(deltaY: Float) {
        if (draggedIndex == -1) return
        dragOffsetY += deltaY
        val threshold = (itemHeightPx * 0.62f).coerceAtLeast(64f)

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
            onTracksReordered(committed)
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
    ) {
        headerContent?.invoke(this)

        itemsIndexed(
            items = localTracks,
            key = { _, item -> item.id }
        ) { index, track ->
            val isDraggingThis = index == draggedIndex
            val isCurrent = track.id == currentTrackId

            val liftElevation by animateDpAsState(
                targetValue = if (isDraggingThis) 16.dp else 0.dp,
                label = "drag_elevation"
            )
            val liftScale by animateFloatAsState(
                targetValue = if (isDraggingThis) 1.025f else 1f,
                label = "drag_scale"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .zIndex(if (isDraggingThis) 10f else 0f)
                    .onSizeChanged { size ->
                        if (size.height > 0) {
                            itemHeightPx = size.height.toFloat() + 24f
                        }
                    }
                    .graphicsLayer {
                        translationY = if (isDraggingThis) dragOffsetY else 0f
                        scaleX = liftScale
                        scaleY = liftScale
                    }
                    .pointerInput(track.id, dragEnabled) {
                        if (!dragEnabled) return@pointerInput
                        detectDragGesturesAfterLongPress(
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
            ) {
                PhotonTrackCard(
                    track = track,
                    isCurrent = isCurrent,
                    isPlaying = isCurrent && isPlaying,
                    isDragging = isDraggingThis,
                    dragEnabled = dragEnabled,
                    elevation = liftElevation,
                    onClick = { onTrackClick(localTracks, index) },
                    onHandleDragStart = {
                        val currentIdx = localTracks.indexOfFirst { it.id == track.id }
                        if (currentIdx >= 0) startDrag(currentIdx)
                    },
                    onHandleDragDelta = { onDragDelta(it) },
                    onHandleDragEnd = { finishDrag() },
                    onAutoCompleteMetadata = { onAutoCompleteMetadata(track) },
                    onAddToPlaylist = onAddToPlaylist?.let { cb -> { cb(track) } },
                    onRemoveTrack = { onRemoveTrack(track) },
                    removeLabel = removeLabel
                )
            }
        }
    }
}

@Composable
private fun PhotonTrackCard(
    track: TrackEntity,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isDragging: Boolean,
    dragEnabled: Boolean,
    elevation: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    onHandleDragStart: () -> Unit,
    onHandleDragDelta: (Float) -> Unit,
    onHandleDragEnd: () -> Unit,
    onAutoCompleteMetadata: () -> Unit,
    onAddToPlaylist: (() -> Unit)?,
    onRemoveTrack: () -> Unit,
    removeLabel: String
) {
    val photonColors = LocalPhotonColors.current
    var menuExpanded by remember { mutableStateOf(false) }

    val borderColor = when {
        isDragging -> photonColors.photonGlow
        isCurrent -> photonColors.photonGlow.copy(alpha = 0.78f)
        else -> photonColors.cardBorder.copy(alpha = 0.65f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("track_card_${track.id}")
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (isDragging || isCurrent) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            photonColors.glassSurface
        },
        shadowElevation = elevation,
        border = BorderStroke(if (isDragging || isCurrent) 1.4.dp else 0.85.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Optional drag handle on left for instant one-touch drag-and-drop
            if (dragEnabled) {
                Box(
                    modifier = Modifier
                        .size(width = 32.dp, height = 48.dp)
                        .testTag("drag_handle_${track.id}")
                        .pointerInput(track.id) {
                            detectDragGestures(
                                onDragStart = { onHandleDragStart() },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    onHandleDragDelta(dragAmount.y)
                                },
                                onDragEnd = { onHandleDragEnd() },
                                onDragCancel = { onHandleDragEnd() }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DragHandle,
                        contentDescription = "Drag to reorder ${track.title}",
                        tint = if (isDragging) photonColors.photonGlow else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            PhotonAlbumArt(
                artworkUri = track.artworkUri,
                contentDescription = "Album art for ${track.title}",
                size = 52.dp,
                cornerRadius = 12.dp,
                isPlaying = isPlaying
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
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
                    text = buildString {
                        append(track.artist)
                        if (track.album.isNotBlank() && track.album != "Unknown Album") {
                            append(" • ")
                            append(track.album)
                        }
                    },
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
                        text = buildString {
                            append(formatDurationMs(track.durationMs))
                            if (track.genre.isNotBlank() && track.genre != "Unknown Genre") {
                                append(" · ")
                                append(track.genre)
                            }
                            if (track.releaseYear != null) {
                                append(" · ")
                                append(track.releaseYear)
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (track.publisher.isNotBlank() && track.publisher != "Independent") {
                    Text(
                        text = track.publisher,
                        style = MaterialTheme.typography.labelSmall,
                        color = photonColors.nebulaAccent.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.testTag("track_menu_${track.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Track options for ${track.title}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Auto-Complete Metadata") },
                        leadingIcon = {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = photonColors.photonGlow)
                        },
                        onClick = {
                            menuExpanded = false
                            onAutoCompleteMetadata()
                        }
                    )
                    if (onAddToPlaylist != null) {
                        DropdownMenuItem(
                            text = { Text("Add to Playlist") },
                            leadingIcon = {
                                Icon(Icons.Rounded.PlaylistAdd, contentDescription = null)
                            },
                            onClick = {
                                menuExpanded = false
                                onAddToPlaylist()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(removeLabel) },
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
