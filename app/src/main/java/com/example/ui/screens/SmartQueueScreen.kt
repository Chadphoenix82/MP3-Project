package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueuePlayNext
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.db.TrackEntity
import com.example.data.library.SmartQueueSuggestion
import com.example.player.PlaybackUiState
import com.example.ui.components.AudioFormatBadge
import com.example.ui.components.PhotonAlbumArt
import com.example.ui.components.PhotonEqualizerBars
import com.example.ui.theme.LocalPhotonColors

@Composable
fun SmartQueueScreen(
    playbackState: PlaybackUiState,
    suggestions: List<SmartQueueSuggestion>,
    totalPlayCount: Int,
    autoPlayEnabled: Boolean,
    onToggleAutoPlay: (Boolean) -> Unit,
    onPlayNext: (TrackEntity) -> Unit,
    onAddToQueue: (TrackEntity) -> Unit,
    onQueueAllSuggestions: () -> Unit,
    onJumpToQueueIndex: (Int) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val photonColors = LocalPhotonColors.current
    val currentTrack = playbackState.currentTrack
    val upcomingQueue = if (playbackState.currentIndex >= 0 && playbackState.currentIndex < playbackState.queue.lastIndex) {
        playbackState.queue.mapIndexed { idx, track -> idx to track }
            .drop(playbackState.currentIndex + 1)
    } else {
        emptyList()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 156.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Smart Queue Engine Header Card
        item(key = "smart_queue_header") {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = photonColors.glassSurface,
                border = BorderStroke(1.2.dp, photonColors.photonGlow.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = photonColors.photonGlow
                            )
                            Column {
                                Text(
                                    text = "SMART QUEUE",
                                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = buildString {
                                        if (currentTrack != null) {
                                            append("Genre: ${currentTrack.genre} · ")
                                        }
                                        append("$totalPlayCount Room play${if (totalPlayCount == 1) "" else "s"} logged")
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = photonColors.photonGlow
                                )
                            }
                        }

                        if (totalPlayCount > 0) {
                            IconButton(
                                onClick = onClearHistory,
                                modifier = Modifier.testTag("clear_listening_history_btn")
                            ) {
                                Icon(
                                    Icons.Rounded.DeleteSweep,
                                    contentDescription = "Clear listening history",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-Queue Next Tracks",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "Automatically appends top genre & history matches as your queue finishes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = autoPlayEnabled,
                            onCheckedChange = onToggleAutoPlay,
                            modifier = Modifier.testTag("smart_queue_autoplay_switch"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = photonColors.photonGlow
                            )
                        )
                    }

                    if (suggestions.isNotEmpty()) {
                        Button(
                            onClick = onQueueAllSuggestions,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("queue_all_suggestions_btn"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Queue All ${suggestions.size} Smart Suggestions")
                        }
                    }
                }
            }
        }

        // 2. Currently Playing Context Banner
        if (currentTrack != null) {
            item(key = "smart_queue_now_playing") {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, photonColors.photonGlow.copy(alpha = 0.55f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PhotonAlbumArt(
                            artworkUri = currentTrack.artworkUri,
                            contentDescription = "Current track art",
                            size = 48.dp,
                            cornerRadius = 11.dp,
                            isPlaying = playbackState.isPlaying
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "NOW PLAYING SEED",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = photonColors.photonGlow
                            )
                            Text(
                                text = currentTrack.title,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${currentTrack.artist} · ${currentTrack.genre}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        PhotonEqualizerBars(isPlaying = playbackState.isPlaying)
                    }
                }
            }
        }

        // 3. Smart Recommendations Section
        item(key = "suggestions_section_label") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Icon(
                    Icons.Rounded.History,
                    contentDescription = null,
                    tint = photonColors.nebulaAccent,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "RECOMMENDED NEXT",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = photonColors.nebulaAccent
                )
            }
        }

        if (suggestions.isEmpty()) {
            item(key = "empty_smart_queue") {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = photonColors.glassSurface,
                    border = BorderStroke(1.dp, photonColors.cardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Add More Tracks to Activate Smart Queue",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Import MP3, FLAC, or WAV tracks and play a few songs. Smart Queue analyzes your Room listening history and current track genre to line up your next tracks automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(suggestions, key = { "sq_${it.track.id}" }) { suggestion ->
                SmartSuggestionCard(
                    suggestion = suggestion,
                    onPlayNext = { onPlayNext(suggestion.track) },
                    onAddToQueue = { onAddToQueue(suggestion.track) }
                )
            }
        }

        // 4. Upcoming Active Queue
        if (upcomingQueue.isNotEmpty()) {
            item(key = "up_next_label") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = null,
                        tint = photonColors.photonGlow,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "UP NEXT IN QUEUE (${upcomingQueue.size})",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = photonColors.photonGlow
                    )
                }
            }

            itemsIndexed(
                items = upcomingQueue,
                key = { pos, pair -> "upnext_${pos}_${pair.second.id}" }
            ) { _, (queueIdx, track) ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("queue_item_${track.id}")
                        .clickable { onJumpToQueueIndex(queueIdx) },
                    shape = RoundedCornerShape(16.dp),
                    color = photonColors.glassSurface,
                    border = BorderStroke(0.8.dp, photonColors.cardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PhotonAlbumArt(
                            artworkUri = track.artworkUri,
                            contentDescription = "Queued track art",
                            size = 44.dp,
                            cornerRadius = 10.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${track.artist} · ${track.genre}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = "Play now",
                            tint = photonColors.photonGlow
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SmartSuggestionCard(
    suggestion: SmartQueueSuggestion,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit
) {
    val photonColors = LocalPhotonColors.current
    val track = suggestion.track

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("smart_suggestion_${track.id}"),
        shape = RoundedCornerShape(18.dp),
        color = photonColors.glassSurface,
        border = BorderStroke(1.dp, photonColors.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PhotonAlbumArt(
                    artworkUri = track.artworkUri,
                    contentDescription = "Suggested album art",
                    size = 50.dp,
                    cornerRadius = 12.dp
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
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        AudioFormatBadge(format = track.format)
                    }
                    Text(
                        text = "${track.artist} · ${track.album}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = photonColors.photonGlow.copy(alpha = 0.14f),
                            border = BorderStroke(0.6.dp, photonColors.photonGlow.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "${suggestion.matchPercent}% MATCH",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = photonColors.photonGlow,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = suggestion.reason,
                            style = MaterialTheme.typography.labelSmall,
                            color = photonColors.nebulaAccent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onPlayNext,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .testTag("smart_play_next_${track.id}"),
                    shape = RoundedCornerShape(11.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Rounded.QueuePlayNext, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Play Next", style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    onClick = onAddToQueue,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .testTag("smart_add_queue_${track.id}"),
                    shape = RoundedCornerShape(11.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add to Queue", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
