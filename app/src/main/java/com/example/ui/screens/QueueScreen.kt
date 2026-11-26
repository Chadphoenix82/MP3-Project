package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.data.db.TrackEntity
import com.example.player.PlaybackUiState
import com.example.ui.components.TrackRowItem
import com.example.ui.components.formatDurationMs
import com.example.ui.theme.LocalPhotonColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    playbackState: PlaybackUiState,
    onJumpToIndex: (Int) -> Unit,
    onReorderQueue: (List<TrackEntity>) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onClearUpcoming: () -> Unit,
    onSaveQueueAsPlaylist: (name: String, description: String) -> Unit,
    onOpenTrackMenu: (TrackEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val photonColors = LocalPhotonColors.current
    val haptic = LocalHapticFeedback.current
    val currentIndex = playbackState.currentIndex

    var localQueue by remember { mutableStateOf(playbackState.queue) }
    var draggedQueueIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var itemHeightPx by remember { mutableFloatStateOf(168f) }
    var showSaveDialog by remember { mutableStateOf(false) }

    LaunchedEffect(playbackState.queue) {
        if (draggedQueueIndex == -1) {
            localQueue = playbackState.queue
        }
    }

    val upcomingStartIndex = (currentIndex + 1).coerceAtLeast(0)

    fun startQueueDrag(queueIdx: Int) {
        if (queueIdx < upcomingStartIndex || queueIdx > localQueue.lastIndex) return
        draggedQueueIndex = queueIdx
        dragOffsetY = 0f
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun onQueueDragDelta(deltaY: Float) {
        if (draggedQueueIndex == -1) return
        dragOffsetY += deltaY
        val threshold = (itemHeightPx * 0.6f).coerceAtLeast(60f)

        while (dragOffsetY > threshold && draggedQueueIndex < localQueue.lastIndex) {
            val next = draggedQueueIndex + 1
            val mutable = localQueue.toMutableList()
            val item = mutable.removeAt(draggedQueueIndex)
            mutable.add(next, item)
            localQueue = mutable
            draggedQueueIndex = next
            dragOffsetY -= itemHeightPx
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }

        while (dragOffsetY < -threshold && draggedQueueIndex > upcomingStartIndex) {
            val prev = draggedQueueIndex - 1
            val mutable = localQueue.toMutableList()
            val item = mutable.removeAt(draggedQueueIndex)
            mutable.add(prev, item)
            localQueue = mutable
            draggedQueueIndex = prev
            dragOffsetY += itemHeightPx
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    fun finishQueueDrag() {
        if (draggedQueueIndex != -1) {
            val committed = localQueue.toList()
            draggedQueueIndex = -1
            dragOffsetY = 0f
            onReorderQueue(committed)
        }
    }

    val upcomingEntries = remember(localQueue, currentIndex) {
        if (currentIndex < 0 || currentIndex >= localQueue.lastIndex) emptyList()
        else ((currentIndex + 1)..localQueue.lastIndex).map { idx -> idx to localQueue[idx] }
    }

    val playingNextEntries = remember(upcomingEntries, playbackState.priorityQueueIds) {
        upcomingEntries.filter { (_, track) -> track.id in playbackState.priorityQueueIds }
    }

    val upNextFromSourceEntries = remember(upcomingEntries, playbackState.priorityQueueIds) {
        upcomingEntries.filter { (_, track) -> track.id !in playbackState.priorityQueueIds }
    }

    val remainingDurationMs = remember(localQueue, currentIndex, playbackState.positionMs, playbackState.durationMs) {
        val currentRem = (playbackState.durationMs - playbackState.positionMs).coerceAtLeast(0L)
        val upcomingDur = upcomingEntries.sumOf { it.second.durationMs.coerceAtLeast(0L) }
        currentRem + upcomingDur
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("live_queue_screen")
    ) {
        // Total Remaining Duration & Save Queue Action Header
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = photonColors.glassSurface,
            border = BorderStroke(1.dp, photonColors.photonGlow.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "LIVE UP-NEXT QUEUE",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = photonColors.photonGlow
                        )
                        Text(
                            text = "${localQueue.size} tracks · ${formatDurationMs(remainingDurationMs)} remaining",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Icon(Icons.Rounded.QueueMusic, contentDescription = null, tint = photonColors.photonGlow)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showSaveDialog = true },
                        enabled = localQueue.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = photonColors.photonGlow,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_queue_as_playlist_btn")
                    ) {
                        Icon(Icons.Rounded.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save as Playlist", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = onClearUpcoming,
                        enabled = upcomingEntries.isNotEmpty(),
                        modifier = Modifier.testTag("clear_upcoming_queue_btn")
                    ) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Clear")
                    }
                }
            }
        }

        if (localQueue.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Your playback queue is empty. Play any track or Smart Auto-Mix to populate it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Active Track Section
                playbackState.currentTrack?.let { current ->
                    item(key = "now_playing_header") {
                        Text(
                            text = "NOW PLAYING",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = photonColors.photonGlow
                        )
                    }
                    item(key = "now_playing_row_${current.id}") {
                        TrackRowItem(
                            track = current,
                            isCurrent = true,
                            isPlaying = playbackState.isPlaying,
                            onClick = { if (currentIndex >= 0) onJumpToIndex(currentIndex) },
                            onOpenTrackMenu = onOpenTrackMenu,
                            rowTestTag = "queue_now_playing_row_${current.id}"
                        )
                    }
                }

                // Section 1: Playing Next (Priority Queued Tracks)
                if (playingNextEntries.isNotEmpty()) {
                    item(key = "playing_next_header") {
                        Text(
                            text = "PLAYING NEXT (${playingNextEntries.size})",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = photonColors.goldBadge,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(playingNextEntries, key = { (idx, t) -> "prio_${idx}_${t.id}" }) { (queueIdx, track) ->
                        val isDraggingThis = draggedQueueIndex == queueIdx
                        QueueSwipeableTrackRow(
                            track = track,
                            isDragging = isDraggingThis,
                            dragOffsetY = if (isDraggingThis) dragOffsetY else 0f,
                            onJump = { onJumpToIndex(queueIdx) },
                            onOpenTrackMenu = onOpenTrackMenu,
                            onRemove = { onRemoveFromQueue(queueIdx) },
                            onMeasureHeight = { h -> if (h > 0) itemHeightPx = h.toFloat() },
                            dragHandleModifier = Modifier.pointerInput(track.id, queueIdx) {
                                detectDragGestures(
                                    onDragStart = {
                                        val latestIdx = localQueue.indexOfFirst { it.id == track.id }
                                            .takeIf { it >= upcomingStartIndex } ?: queueIdx
                                        startQueueDrag(latestIdx)
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        onQueueDragDelta(dragAmount.y)
                                    },
                                    onDragEnd = { finishQueueDrag() },
                                    onDragCancel = { finishQueueDrag() }
                                )
                            }
                        )
                    }
                }

                // Section 2: Up Next from [Source]
                if (upNextFromSourceEntries.isNotEmpty()) {
                    item(key = "up_next_source_header") {
                        Text(
                            text = "UP NEXT FROM ${playbackState.queueSourceLabel.uppercase()} (${upNextFromSourceEntries.size})",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = photonColors.photonGlow,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(upNextFromSourceEntries, key = { (idx, t) -> "src_${idx}_${t.id}" }) { (queueIdx, track) ->
                        val isDraggingThis = draggedQueueIndex == queueIdx
                        QueueSwipeableTrackRow(
                            track = track,
                            isDragging = isDraggingThis,
                            dragOffsetY = if (isDraggingThis) dragOffsetY else 0f,
                            onJump = { onJumpToIndex(queueIdx) },
                            onOpenTrackMenu = onOpenTrackMenu,
                            onRemove = { onRemoveFromQueue(queueIdx) },
                            onMeasureHeight = { h -> if (h > 0) itemHeightPx = h.toFloat() },
                            dragHandleModifier = Modifier.pointerInput(track.id, queueIdx) {
                                detectDragGestures(
                                    onDragStart = {
                                        val latestIdx = localQueue.indexOfFirst { it.id == track.id }
                                            .takeIf { it >= upcomingStartIndex } ?: queueIdx
                                        startQueueDrag(latestIdx)
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        onQueueDragDelta(dragAmount.y)
                                    },
                                    onDragEnd = { finishQueueDrag() },
                                    onDragCancel = { finishQueueDrag() }
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        var name by remember { mutableStateOf("Queue · ${playbackState.queueSourceLabel}") }
        var desc by remember { mutableStateOf("Saved from Photon Audio Live Queue (${localQueue.size} tracks)") }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save Queue as Playlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Playlist Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveQueueAsPlaylist(name.trim(), desc.trim())
                        showSaveDialog = false
                    },
                    enabled = name.isNotBlank()
                ) {
                    Text("Save Playlist")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSwipeableTrackRow(
    track: TrackEntity,
    isDragging: Boolean,
    dragOffsetY: Float,
    onJump: () -> Unit,
    onOpenTrackMenu: (TrackEntity) -> Unit,
    onRemove: () -> Unit,
    onMeasureHeight: (Int) -> Unit,
    dragHandleModifier: Modifier
) {
    val liftScale by animateFloatAsState(
        targetValue = if (isDragging) 1.025f else 1f,
        label = "queue_drag_scale"
    )
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onRemove()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        gesturesEnabled = !isDragging,
        modifier = Modifier
            .zIndex(if (isDragging) 10f else 0f)
            .onSizeChanged { onMeasureHeight(it.height) }
            .graphicsLayer {
                translationY = dragOffsetY
                scaleX = liftScale
                scaleY = liftScale
            },
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.85f))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = "Remove from queue", tint = Color.White)
            }
        },
        content = {
            TrackRowItem(
                track = track,
                isDragging = isDragging,
                onClick = onJump,
                onOpenTrackMenu = onOpenTrackMenu,
                dragHandleModifier = dragHandleModifier,
                rowTestTag = "queue_track_row_${track.id}"
            )
        }
    )
}
