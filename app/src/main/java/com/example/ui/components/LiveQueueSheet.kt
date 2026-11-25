package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.data.db.TrackEntity
import com.example.player.PlaybackUiState
import com.example.ui.theme.LocalPhotonColors

/**
 * Interactive Live Queue ("Up Next") Manager sheet supporting drag-to-reorder,
 * accessible up/down shifting, track removal, clear upcoming, and saving the queue as a playlist.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveQueueSheet(
    playbackState: PlaybackUiState,
    onJumpToIndex: (Int) -> Unit,
    onReorderQueue: (List<TrackEntity>) -> Unit,
    onRemoveIndex: (Int) -> Unit,
    onClearUpcoming: () -> Unit,
    onSaveQueueAsPlaylist: (name: String, description: String) -> Unit,
    onDismiss: () -> Unit
) {
    val photonColors = LocalPhotonColors.current
    val haptic = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var localQueue by remember(playbackState.queue) { mutableStateOf(playbackState.queue) }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragDeltaY by remember { mutableFloatStateOf(0f) }

    var showSaveForm by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("") }
    var playlistDescription by remember { mutableStateOf("") }

    LaunchedEffect(playbackState.queue) {
        if (draggingIndex == null) {
            localQueue = playbackState.queue
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .testTag("live_queue_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = null,
                        tint = photonColors.photonGlow,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "UP NEXT QUEUE",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        val upcomingCount = (localQueue.size - (playbackState.currentIndex + 1)).coerceAtLeast(0)
                        Text(
                            text = "${localQueue.size} track${if (localQueue.size == 1) "" else "s"} · $upcomingCount upcoming · Hold & drag to reorder",
                            style = MaterialTheme.typography.labelSmall,
                            color = photonColors.photonGlow
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("live_queue_close_btn")
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close queue")
                }
            }

            // Action Row: Save as Playlist & Clear Upcoming
            if (localQueue.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { showSaveForm = !showSaveForm },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("queue_save_as_playlist_btn"),
                        shape = RoundedCornerShape(13.dp),
                        border = BorderStroke(1.dp, photonColors.photonGlow.copy(alpha = 0.65f))
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.PlaylistAdd,
                            contentDescription = null,
                            tint = photonColors.photonGlow,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save as Playlist", maxLines = 1)
                    }

                    val hasUpcoming = playbackState.currentIndex >= 0 && playbackState.currentIndex < localQueue.lastIndex
                    OutlinedButton(
                        onClick = onClearUpcoming,
                        enabled = hasUpcoming,
                        modifier = Modifier
                            .height(44.dp)
                            .testTag("queue_clear_upcoming_btn"),
                        shape = RoundedCornerShape(13.dp)
                    ) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clear Next", maxLines = 1)
                    }
                }
            }

            // Inline Save Queue as Playlist Form
            if (showSaveForm) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = photonColors.glassSurface,
                    border = BorderStroke(1.dp, photonColors.photonGlow.copy(alpha = 0.55f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Save ${localQueue.size} Queued Tracks as Playlist",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = photonColors.photonGlow
                        )
                        OutlinedTextField(
                            value = playlistName,
                            onValueChange = { playlistName = it },
                            label = { Text("Playlist Name") },
                            placeholder = { Text("Late Night Session") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("queue_playlist_name_input")
                        )
                        OutlinedTextField(
                            value = playlistDescription,
                            onValueChange = { playlistDescription = it },
                            label = { Text("Description (optional)") },
                            placeholder = { Text("Saved directly from live queue") },
                            maxLines = 2,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("queue_playlist_desc_input")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showSaveForm = false }) {
                                Text("Cancel")
                            }
                            Button(
                                onClick = {
                                    if (playlistName.isNotBlank()) {
                                        onSaveQueueAsPlaylist(playlistName.trim(), playlistDescription.trim())
                                        playlistName = ""
                                        playlistDescription = ""
                                        showSaveForm = false
                                    }
                                },
                                enabled = playlistName.isNotBlank(),
                                modifier = Modifier.testTag("queue_playlist_confirm_btn")
                            ) {
                                Text("Save Playlist")
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = photonColors.cardBorder.copy(alpha = 0.5f))

            if (localQueue.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Your playback queue is empty. Play any song to build your Up Next session.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(
                        items = localQueue,
                        key = { idx, track -> "${track.id}_$idx" }
                    ) { index, track ->
                        val isCurrent = index == playbackState.currentIndex
                        val isDragging = draggingIndex == index

                        Surface(
                            shape = RoundedCornerShape(15.dp),
                            color = when {
                                isDragging -> MaterialTheme.colorScheme.primaryContainer
                                isCurrent -> photonColors.glassSurface
                                else -> MaterialTheme.colorScheme.surfaceContainer
                            },
                            border = BorderStroke(
                                width = if (isCurrent || isDragging) 1.2.dp else 0.75.dp,
                                color = if (isCurrent || isDragging) photonColors.photonGlow else photonColors.cardBorder.copy(alpha = 0.6f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(if (isDragging) 2f else 0f)
                                .graphicsLayer {
                                    translationY = if (isDragging) dragDeltaY else 0f
                                    scaleX = if (isDragging) 1.02f else 1f
                                    scaleY = if (isDragging) 1.02f else 1f
                                }
                                .clickable { onJumpToIndex(index) }
                                .testTag("live_queue_item_$index")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Drag handle (hold & drag up/down to reorder)
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .pointerInput(localQueue.size) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    draggingIndex = index
                                                    dragDeltaY = 0f
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    dragDeltaY += dragAmount.y
                                                    val currentIdx = draggingIndex ?: return@detectDragGesturesAfterLongPress
                                                    val stepPx = 150f
                                                    if (dragDeltaY > stepPx && currentIdx < localQueue.lastIndex) {
                                                        val updated = localQueue.toMutableList()
                                                        val moved = updated.removeAt(currentIdx)
                                                        updated.add(currentIdx + 1, moved)
                                                        localQueue = updated
                                                        draggingIndex = currentIdx + 1
                                                        dragDeltaY -= stepPx
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    } else if (dragDeltaY < -stepPx && currentIdx > 0) {
                                                        val updated = localQueue.toMutableList()
                                                        val moved = updated.removeAt(currentIdx)
                                                        updated.add(currentIdx - 1, moved)
                                                        localQueue = updated
                                                        draggingIndex = currentIdx - 1
                                                        dragDeltaY += stepPx
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    }
                                                },
                                                onDragEnd = {
                                                    draggingIndex = null
                                                    dragDeltaY = 0f
                                                    onReorderQueue(localQueue)
                                                },
                                                onDragCancel = {
                                                    draggingIndex = null
                                                    dragDeltaY = 0f
                                                }
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DragHandle,
                                        contentDescription = "Drag to reorder track",
                                        tint = if (isCurrent) photonColors.photonGlow else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                PhotonAlbumArt(
                                    artworkUri = track.artworkUri,
                                    contentDescription = track.title,
                                    size = 42.dp,
                                    cornerRadius = 10.dp,
                                    isPlaying = isCurrent && playbackState.isPlaying
                                )

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold
                                        ),
                                        color = if (isCurrent) photonColors.photonGlow else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${track.artist} · ${formatDurationMs(track.durationMs)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (isCurrent) {
                                    PhotonEqualizerBars(
                                        isPlaying = playbackState.isPlaying,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                } else {
                                    IconButton(
                                        onClick = {
                                            if (index > 0) {
                                                val updated = localQueue.toMutableList()
                                                val moved = updated.removeAt(index)
                                                updated.add(index - 1, moved)
                                                localQueue = updated
                                                onReorderQueue(updated)
                                            }
                                        },
                                        enabled = index > 0,
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.ArrowUpward,
                                            contentDescription = "Move track up",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            if (index < localQueue.lastIndex) {
                                                val updated = localQueue.toMutableList()
                                                val moved = updated.removeAt(index)
                                                updated.add(index + 1, moved)
                                                localQueue = updated
                                                onReorderQueue(updated)
                                            }
                                        },
                                        enabled = index < localQueue.lastIndex,
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.ArrowDownward,
                                            contentDescription = "Move track down",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onRemoveIndex(index) },
                                        enabled = localQueue.size > 1,
                                        modifier = Modifier
                                            .size(34.dp)
                                            .testTag("queue_remove_item_$index")
                                    ) {
                                        Icon(
                                            Icons.Rounded.Close,
                                            contentDescription = "Remove from queue",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(17.dp)
                                        )
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
