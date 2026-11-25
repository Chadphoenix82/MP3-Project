package com.example.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.db.TrackEntity
import com.example.player.PlaybackUiState
import com.example.ui.components.AudioFormatBadge
import com.example.ui.components.PhotonAlbumArt
import com.example.ui.components.PhotonEqualizerBars
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
    modifier: Modifier = Modifier
) {
    val photonColors = LocalPhotonColors.current
    val queue = playbackState.queue
    val currentIndex = playbackState.currentIndex
    var showSaveDialog by remember { mutableStateOf(false) }

    val upcomingEntries = remember(queue, currentIndex) {
        if (currentIndex < 0 || currentIndex >= queue.lastIndex) emptyList()
        else ((currentIndex + 1)..queue.lastIndex).map { idx -> idx to queue[idx] }
    }

    val playingNextEntries = remember(upcomingEntries, playbackState.priorityQueueIds) {
        upcomingEntries.filter { (_, track) -> track.id in playbackState.priorityQueueIds }
    }

    val upNextFromSourceEntries = remember(upcomingEntries, playbackState.priorityQueueIds) {
        upcomingEntries.filter { (_, track) -> track.id !in playbackState.priorityQueueIds }
    }

    val remainingDurationMs = remember(queue, currentIndex, playbackState.positionMs, playbackState.durationMs) {
        val currentRem = (playbackState.durationMs - playbackState.positionMs).coerceAtLeast(0L)
        val upcomingDur = upcomingEntries.sumOf { it.second.durationMs.coerceAtLeast(0L) }
        currentRem + upcomingDur
    }

    fun swapIndices(fromIdx: Int, toIdx: Int) {
        if (fromIdx !in queue.indices || toIdx !in queue.indices || fromIdx == toIdx) return
        val mutable = queue.toMutableList()
        val moved = mutable.removeAt(fromIdx)
        mutable.add(toIdx, moved)
        onReorderQueue(mutable)
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
                            text = "${queue.size} tracks · ${formatDurationMs(remainingDurationMs)} remaining",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Icon(Icons.Rounded.QueueMusic, contentDescription = null, tint = photonColors.photonGlow)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showSaveDialog = true },
                        enabled = queue.isNotEmpty(),
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

        if (queue.isEmpty()) {
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
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = photonColors.photonGlow.copy(alpha = 0.14f),
                            border = BorderStroke(1.2.dp, photonColors.photonGlow),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                PhotonAlbumArt(current.artworkUri, current.title, size = 48.dp, cornerRadius = 10.dp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(current.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(current.artist, style = MaterialTheme.typography.bodySmall, color = photonColors.photonGlow, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                PhotonEqualizerBars(isPlaying = playbackState.isPlaying)
                            }
                        }
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
                        QueueSwipeableRow(
                            track = track,
                            canMoveUp = queueIdx > currentIndex + 1,
                            canMoveDown = queueIdx < queue.lastIndex,
                            onJump = { onJumpToIndex(queueIdx) },
                            onMoveUp = { swapIndices(queueIdx, queueIdx - 1) },
                            onMoveDown = { swapIndices(queueIdx, queueIdx + 1) },
                            onRemove = { onRemoveFromQueue(queueIdx) }
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
                        QueueSwipeableRow(
                            track = track,
                            canMoveUp = queueIdx > currentIndex + 1,
                            canMoveDown = queueIdx < queue.lastIndex,
                            onJump = { onJumpToIndex(queueIdx) },
                            onMoveUp = { swapIndices(queueIdx, queueIdx - 1) },
                            onMoveDown = { swapIndices(queueIdx, queueIdx + 1) },
                            onRemove = { onRemoveFromQueue(queueIdx) }
                        )
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        var name by remember { mutableStateOf("Queue · ${playbackState.queueSourceLabel}") }
        var desc by remember { mutableStateOf("Saved from Photon Audio Live Queue (${queue.size} tracks)") }
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
private fun QueueSwipeableRow(
    track: TrackEntity,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onJump: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    val photonColors = LocalPhotonColors.current
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
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = photonColors.glassSurface,
                border = BorderStroke(0.8.dp, photonColors.cardBorder.copy(alpha = 0.65f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onJump)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DragHandle,
                        contentDescription = "Reorder handle",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    PhotonAlbumArt(track.artworkUri, track.title, size = 44.dp, cornerRadius = 10.dp)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AudioFormatBadge(track.format)
                            Text(
                                text = "${track.artist} · ${formatDurationMs(track.durationMs)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move up", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move down", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    )
}
