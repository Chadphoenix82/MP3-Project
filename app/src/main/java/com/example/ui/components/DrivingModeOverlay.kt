package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.db.TrackEntity
import com.example.player.PlaybackUiState
import com.example.ui.theme.LocalPhotonColors

/**
 * Glanceable, high-contrast Driving Mode overlay with oversized tactile buttons
 * designed for safe one-tap playback control at arm's length.
 */
@Composable
fun DrivingModeOverlay(
    playbackState: PlaybackUiState,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleFavorite: (TrackEntity) -> Unit,
    onToggleShuffle: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    val photonColors = LocalPhotonColors.current
    val haptic = LocalHapticFeedback.current
    val track = playbackState.currentTrack
    val durationMs = playbackState.durationMs.coerceAtLeast(1000L)
    val progress = (playbackState.positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        lerp(Color(0xFF03050C), photonColors.backdropTop, 0.45f),
                        Color(0xFF02040A),
                        lerp(Color(0xFF03050C), photonColors.backdropBottom, 0.35f)
                    )
                )
            )
            .testTag("driving_mode_overlay")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 22.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar: Mode Pill + High-Contrast Exit Pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(99.dp),
                    color = photonColors.photonGlow.copy(alpha = 0.16f),
                    border = BorderStroke(1.2.dp, photonColors.photonGlow.copy(alpha = 0.7f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DirectionsCar,
                            contentDescription = null,
                            tint = photonColors.photonGlow,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "DRIVING MODE",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                            color = photonColors.photonGlow
                        )
                    }
                }

                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(99.dp),
                    color = Color.White.copy(alpha = 0.14f),
                    border = BorderStroke(1.2.dp, Color.White.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("driving_mode_exit_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Exit Driving Mode",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Exit",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
            }

            // Center Track Stage: Large Album Art + High-Contrast Typography + Progress
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 540.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PhotonAlbumArt(
                    artworkUri = track?.artworkUri,
                    contentDescription = track?.let { "Album art for ${it.title}" } ?: "No track playing",
                    size = 164.dp,
                    cornerRadius = 26.dp,
                    isPlaying = playbackState.isPlaying,
                    showPhotonRing = true
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = track?.title ?: "Ready to Drive",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track?.artist ?: "Select music to begin",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = photonColors.photonGlow,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(99.dp)),
                        color = photonColors.photonGlow,
                        trackColor = Color.White.copy(alpha = 0.18f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatDurationMs(playbackState.positionMs),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White.copy(alpha = 0.85f)
                        )
                        Text(
                            text = formatDurationMs(durationMs),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White.copy(alpha = 0.65f)
                        )
                    }
                }
            }

            // Bottom Oversized Tactile Transport Deck
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DrivingCircleButton(
                        sizeDp = 80,
                        containerColor = Color.White.copy(alpha = 0.12f),
                        borderColor = Color.White.copy(alpha = 0.3f),
                        tag = "driving_mode_prev_btn",
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSkipPrevious()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = "Previous track",
                            tint = Color.White,
                            modifier = Modifier.size(44.dp)
                        )
                    }

                    // Oversized 108dp Play/Pause Button with Spring Micro-Interaction
                    val playInteraction = remember { MutableInteractionSource() }
                    val playPressed by playInteraction.collectIsPressedAsState()
                    val playScale by animateFloatAsState(
                        targetValue = if (playPressed) 0.92f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "driving_play_scale"
                    )
                    Box(
                        modifier = Modifier
                            .size(108.dp)
                            .graphicsLayer {
                                scaleX = playScale
                                scaleY = playScale
                            }
                            .shadow(
                                elevation = 24.dp,
                                shape = CircleShape,
                                ambientColor = photonColors.photonGlow,
                                spotColor = photonColors.photonGlow
                            )
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        lerp(photonColors.photonGlow, Color.White, 0.22f),
                                        photonColors.photonGlow,
                                        photonColors.nebulaAccent
                                    )
                                )
                            )
                            .border(2.dp, Color.White.copy(alpha = 0.72f), CircleShape)
                            .clickable(
                                interactionSource = playInteraction,
                                indication = null,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onTogglePlayPause()
                                }
                            )
                            .testTag("driving_mode_play_pause_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                            tint = Color(0xFF030714),
                            modifier = Modifier.size(58.dp)
                        )
                    }

                    DrivingCircleButton(
                        sizeDp = 80,
                        containerColor = Color.White.copy(alpha = 0.12f),
                        borderColor = Color.White.copy(alpha = 0.3f),
                        tag = "driving_mode_next_btn",
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSkipNext()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = "Next track",
                            tint = Color.White,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                // Secondary Oversized Favorite & Shuffle Action Pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    val isFav = track?.isFavorite == true
                    Surface(
                        onClick = {
                            if (track != null) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onToggleFavorite(track)
                            }
                        },
                        enabled = track != null,
                        shape = RoundedCornerShape(20.dp),
                        color = if (isFav) Color(0x38FF5C8A) else Color.White.copy(alpha = 0.1f),
                        border = BorderStroke(
                            1.2.dp,
                            if (isFav) Color(0xFFFF5C8A) else Color.White.copy(alpha = 0.26f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(62.dp)
                            .testTag("driving_mode_favorite_btn")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = if (isFav) "Remove from Favorites" else "Add to Favorites",
                                tint = if (isFav) Color(0xFFFF5C8A) else Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (isFav) "Favorited" else "Favorite",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleShuffle()
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = if (playbackState.shuffleEnabled) {
                            photonColors.photonGlow.copy(alpha = 0.24f)
                        } else {
                            Color.White.copy(alpha = 0.1f)
                        },
                        border = BorderStroke(
                            1.2.dp,
                            if (playbackState.shuffleEnabled) photonColors.photonGlow else Color.White.copy(alpha = 0.26f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(62.dp)
                            .testTag("driving_mode_shuffle_btn")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shuffle,
                                contentDescription = "Toggle Shuffle",
                                tint = if (playbackState.shuffleEnabled) photonColors.photonGlow else Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (playbackState.shuffleEnabled) "Shuffle ON" else "Shuffle",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrivingCircleButton(
    sizeDp: Int,
    containerColor: Color,
    borderColor: Color,
    tag: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.91f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "driving_btn_scale"
    )
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(containerColor)
            .border(1.4.dp, borderColor, CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
