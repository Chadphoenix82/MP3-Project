package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.db.TrackEntity
import com.example.data.model.OutputTransportType
import com.example.data.prefs.AppSettings
import com.example.player.AudioSpectrumFrame
import com.example.player.PlaybackUiState
import com.example.player.RepeatModeState
import com.example.ui.components.AudioCodecInspectorSheet
import com.example.ui.components.AudioFormatBadge
import com.example.ui.components.GalaxyBackdrop
import com.example.ui.components.PhotonAlbumArt
import com.example.ui.components.PhotonAudioVisualizer
import com.example.ui.components.formatDurationMs
import com.example.ui.components.formatSampleRate
import com.example.ui.theme.LocalPhotonColors
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class GestureAxis { NONE, HORIZONTAL_SKIP, VERTICAL_VOLUME }

/**
 * Persistent mini-player bar with quick swipe-left/right track skip gesture.
 */
@Composable
fun PhotonMiniPlayerBar(
    playbackState: PlaybackUiState,
    gestureControlsEnabled: Boolean = true,
    onExpandClick: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = playbackState.currentTrack ?: return
    val photonColors = LocalPhotonColors.current
    val haptic = LocalHapticFeedback.current
    var dragAccumX by remember { mutableFloatStateOf(0f) }

    val progress = if (playbackState.durationMs > 0L) {
        (playbackState.positionMs.toFloat() / playbackState.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("mini_player_bar")
            .pointerInput(track.id, gestureControlsEnabled) {
                if (!gestureControlsEnabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { dragAccumX = 0f },
                    onHorizontalDrag = { _, dragAmount -> dragAccumX += dragAmount },
                    onDragEnd = {
                        if (dragAccumX < -90f) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSkipNext()
                        } else if (dragAccumX > 90f) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSkipPrevious()
                        }
                        dragAccumX = 0f
                    }
                )
            }
            .clickable(onClick = onExpandClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 10.dp,
        border = BorderStroke(1.1.dp, photonColors.photonGlow.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PhotonAlbumArt(
                    artworkUri = track.artworkUri,
                    contentDescription = "Now playing art",
                    size = 46.dp,
                    cornerRadius = 11.dp,
                    isPlaying = playbackState.isPlaying
                )
                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
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
                        text = if (playbackState.isCrossfading) {
                            "Crossfading to next track..."
                        } else {
                            track.artist
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (playbackState.isCrossfading) photonColors.photonGlow else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onSkipPrevious,
                    modifier = Modifier.testTag("mini_prev_button")
                ) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous track")
                }

                Surface(
                    onClick = onTogglePlayPause,
                    shape = CircleShape,
                    color = photonColors.photonGlow,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("mini_play_pause_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                IconButton(
                    onClick = onSkipNext,
                    modifier = Modifier.testTag("mini_next_button")
                ) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next track")
                }
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp),
                color = photonColors.photonGlow,
                trackColor = photonColors.cardBorder.copy(alpha = 0.4f)
            )
        }
    }
}

/**
 * Full-screen Photon Galaxy Now Playing Deck with touch-based gesture controls:
 * - Swipe Left / Right on the artwork deck to skip Next / Previous tracks.
 * - Swipe Up / Down on the artwork deck to smoothly adjust media volume with live HUD feedback.
 */
@Composable
fun NowPlayingScreen(
    playbackState: PlaybackUiState,
    spectrumFlow: StateFlow<AudioSpectrumFrame>,
    settings: AppSettings,
    onCollapse: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onGetVolumeFraction: () -> Float,
    onSetVolumeFraction: (Float) -> Float,
    onOpenSleepTimer: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenSmartQueue: () -> Unit,
    onOpenMetadataAutoComplete: (TrackEntity) -> Unit,
    onAddToPlaylist: (TrackEntity) -> Unit
) {
    BackHandler(onBack = onCollapse)

    val track = playbackState.currentTrack
    val photonColors = LocalPhotonColors.current
    val haptic = LocalHapticFeedback.current
    val eqEnabled = settings.eqEnabled
    val eqPresetName = settings.eqPresetName
    val eqBandRatios = settings.eqBandRatios
    val gestureControlsEnabled = settings.gestureControlsEnabled

    var showCodecInspector by remember { mutableStateOf(false) }
    var gestureAxis by remember { mutableStateOf(GestureAxis.NONE) }
    var horizontalOffsetPx by remember { mutableFloatStateOf(0f) }
    var activeVolumeFraction by remember { mutableFloatStateOf(onGetVolumeFraction()) }
    var showVolumeHud by remember { mutableStateOf(false) }

    val animatedSwipeX by animateFloatAsState(
        targetValue = if (gestureAxis == GestureAxis.HORIZONTAL_SKIP) {
            horizontalOffsetPx.coerceIn(-220f, 220f)
        } else 0f,
        label = "artwork_swipe_x"
    )

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableFloatStateOf(0f) }
    val displayPosMs = if (isScrubbing) scrubPositionMs.toLong() else playbackState.positionMs
    val durationMs = playbackState.durationMs.coerceAtLeast(1000L)

    GalaxyBackdrop(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier.testTag("now_playing_collapse_btn")
                ) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Collapse player",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "PHOTON STREAM",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = photonColors.photonGlow
                    )
                    if (playbackState.crossfadeSeconds > 0) {
                        Text(
                            text = if (playbackState.isCrossfading) {
                                "CROSSFADING..."
                            } else {
                                "Crossfade ${playbackState.crossfadeSeconds}s"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (playbackState.isCrossfading) photonColors.goldBadge else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onOpenSleepTimer,
                    modifier = Modifier.testTag("now_playing_sleep_timer_btn")
                ) {
                    Icon(
                        Icons.Rounded.Bedtime,
                        contentDescription = "Sleep Timer",
                        tint = if (playbackState.sleepTimer.isActive) photonColors.goldBadge else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (track == null) {
                Spacer(modifier = Modifier.height(60.dp))
                Text("Select a track from your library to begin", style = MaterialTheme.typography.titleMedium)
                return@Column
            }

            // Interactive Gesture Deck around Album Art (Swipe Left/Right = Skip, Swipe Up/Down = Volume)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 360.dp)
                    .height(310.dp)
                    .testTag("now_playing_gesture_deck")
                    .pointerInput(track.id, gestureControlsEnabled) {
                        if (!gestureControlsEnabled) return@pointerInput
                        detectDragGestures(
                            onDragStart = {
                                gestureAxis = GestureAxis.NONE
                                horizontalOffsetPx = 0f
                                activeVolumeFraction = onGetVolumeFraction()
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                if (gestureAxis == GestureAxis.NONE) {
                                    if (abs(dragAmount.x) > abs(dragAmount.y) + 4f) {
                                        gestureAxis = GestureAxis.HORIZONTAL_SKIP
                                    } else if (abs(dragAmount.y) > abs(dragAmount.x) + 4f) {
                                        gestureAxis = GestureAxis.VERTICAL_VOLUME
                                        showVolumeHud = true
                                    }
                                }

                                when (gestureAxis) {
                                    GestureAxis.HORIZONTAL_SKIP -> {
                                        horizontalOffsetPx += dragAmount.x
                                    }
                                    GestureAxis.VERTICAL_VOLUME -> {
                                        val deltaFraction = -dragAmount.y / 420f
                                        activeVolumeFraction = onSetVolumeFraction(activeVolumeFraction + deltaFraction)
                                    }
                                    GestureAxis.NONE -> Unit
                                }
                            },
                            onDragEnd = {
                                if (gestureAxis == GestureAxis.HORIZONTAL_SKIP) {
                                    if (horizontalOffsetPx < -110f) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onSkipNext()
                                    } else if (horizontalOffsetPx > 110f) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onSkipPrevious()
                                    }
                                }
                                gestureAxis = GestureAxis.NONE
                                horizontalOffsetPx = 0f
                                showVolumeHud = false
                            },
                            onDragCancel = {
                                gestureAxis = GestureAxis.NONE
                                horizontalOffsetPx = 0f
                                showVolumeHud = false
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                PhotonAlbumArt(
                    artworkUri = track.artworkUri,
                    contentDescription = "Album artwork for ${track.title}",
                    size = 258.dp,
                    cornerRadius = 26.dp,
                    isPlaying = playbackState.isPlaying,
                    showPhotonRing = true,
                    modifier = Modifier.graphicsLayer {
                        translationX = animatedSwipeX * 0.45f
                        rotationZ = (animatedSwipeX / 220f) * 5.5f
                    }
                )

                // Directional Skip Gesture Overlay HUD
                androidx.compose.animation.AnimatedVisibility(
                    visible = gestureAxis == GestureAxis.HORIZONTAL_SKIP && abs(horizontalOffsetPx) > 36f,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xDD040818),
                        border = BorderStroke(1.2.dp, photonColors.photonGlow)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (horizontalOffsetPx < 0) Icons.Rounded.SkipNext else Icons.Rounded.SkipPrevious,
                                contentDescription = null,
                                tint = photonColors.photonGlow
                            )
                            Text(
                                text = if (horizontalOffsetPx < 0) "NEXT TRACK" else "PREVIOUS TRACK",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }
                }

                // Vertical Volume Gesture HUD Overlay
                androidx.compose.animation.AnimatedVisibility(
                    visible = showVolumeHud,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xE6060E28),
                        border = BorderStroke(1.2.dp, photonColors.photonGlow),
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = "Volume level",
                                tint = photonColors.photonGlow,
                                modifier = Modifier.size(20.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .width(10.dp)
                                    .height(120.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(Color(0x4438E4FF)),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(activeVolumeFraction.coerceIn(0.04f, 1f))
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(photonColors.photonGlow, photonColors.nebulaAccent)
                                            )
                                        )
                                )
                            }
                            Text(
                                text = "${(activeVolumeFraction * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Real-Time EQ-Synced Audio Visualizer (Spectrum Bars / Photon Wave / 5-Band EQ Meters)
            PhotonAudioVisualizer(
                spectrumFlow = spectrumFlow,
                isPlaying = playbackState.isPlaying,
                eqEnabled = eqEnabled,
                eqPresetName = eqPresetName,
                eqBandRatios = eqBandRatios,
                onOpenEqualizer = {
                    onCollapse()
                    onOpenEqualizer()
                },
                modifier = Modifier.widthIn(max = 560.dp)
            )

            // Gesture Hint Pill
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = photonColors.glassSurface,
                border = BorderStroke(0.8.dp, photonColors.cardBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Rounded.Swipe,
                        contentDescription = null,
                        tint = photonColors.photonGlow,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Swipe ←/→ Skip Track  •  Swipe ↑/↓ Volume",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Track Metadata & Audio Specs
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${track.artist} • ${track.album}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = photonColors.photonGlow,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val signalInfo = playbackState.signalInfo
                val displayRateHz = signalInfo.sourceSampleRateHz.takeIf { it > 0 } ?: track.sampleRateHz
                val displayBitrate = signalInfo.liveBitrateKbps.takeIf { it > 0 } ?: track.bitrateKbps
                val decoderBadgeLabel = when {
                    signalInfo.decoderDetected && signalInfo.isHardwareAccelerated -> "HW DSP"
                    signalInfo.decoderDetected -> "SW CPU"
                    track.format.equals("WAV", ignoreCase = true) -> "PCM"
                    else -> "LIVE"
                }
                val decoderBadgeColor = if (signalInfo.isHardwareAccelerated) {
                    photonColors.losslessBadge
                } else {
                    photonColors.photonGlow
                }
                val outputIcon = when (signalInfo.outputTransport) {
                    OutputTransportType.BLUETOOTH_A2DP,
                    OutputTransportType.BLUETOOTH_LE -> Icons.Rounded.BluetoothAudio
                    OutputTransportType.USB_DAC -> Icons.Rounded.Usb
                    OutputTransportType.WIRED_HEADSET -> Icons.Rounded.Headphones
                    OutputTransportType.SPEAKER,
                    OutputTransportType.OTHER -> Icons.Rounded.Speaker
                }

                // Interactive Live Signal Path Pill (opens Audio Codec & Signal Inspector)
                Surface(
                    onClick = { showCodecInspector = true },
                    shape = RoundedCornerShape(16.dp),
                    color = photonColors.glassSurface,
                    border = BorderStroke(1.dp, photonColors.photonGlow.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .testTag("now_playing_signal_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AudioFormatBadge(format = track.format)
                        Text(
                            text = "${signalInfo.sourceBitDepthBits}-bit · ${formatSampleRate(displayRateHz)} · $displayBitrate kbps",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = decoderBadgeColor.copy(alpha = 0.16f),
                            border = BorderStroke(0.7.dp, decoderBadgeColor.copy(alpha = 0.6f))
                        ) {
                            Text(
                                text = decoderBadgeLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = decoderBadgeColor,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                        Icon(
                            imageVector = outputIcon,
                            contentDescription = signalInfo.outputDeviceName,
                            tint = photonColors.photonGlow,
                            modifier = Modifier.size(15.dp)
                        )
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = "Inspect audio codec and signal path",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                if (track.publisher.isNotBlank() && track.publisher != "Independent") {
                    Text(
                        text = buildString {
                            append(track.genre)
                            append(" · Published by ")
                            append(track.publisher)
                            if (track.releaseYear != null) append(" · ${track.releaseYear}")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = photonColors.nebulaAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                } else if (track.releaseYear != null) {
                    Text(
                        text = "${track.genre} · ${track.releaseYear}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Seek Scrubber
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
            ) {
                Slider(
                    value = displayPosMs.toFloat().coerceIn(0f, durationMs.toFloat()),
                    onValueChange = {
                        isScrubbing = true
                        scrubPositionMs = it
                    },
                    onValueChangeFinished = {
                        onSeekTo(scrubPositionMs.toLong())
                        isScrubbing = false
                    },
                    valueRange = 0f..durationMs.toFloat(),
                    modifier = Modifier.testTag("now_playing_seek_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = photonColors.photonGlow,
                        activeTrackColor = photonColors.photonGlow,
                        inactiveTrackColor = photonColors.cardBorder
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDurationMs(displayPosMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (playbackState.sleepTimer.isActive) {
                        Text(
                            text = "Sleep: ${playbackState.sleepTimer.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = photonColors.goldBadge
                        )
                    }
                    Text(
                        text = formatDurationMs(durationMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Transport Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onToggleShuffle,
                    modifier = Modifier.testTag("now_playing_shuffle_btn")
                ) {
                    Icon(
                        Icons.Rounded.Shuffle,
                        contentDescription = "Toggle Shuffle",
                        tint = if (playbackState.shuffleEnabled) photonColors.photonGlow else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onSkipPrevious,
                    modifier = Modifier
                        .size(54.dp)
                        .testTag("now_playing_prev_btn")
                ) {
                    Icon(
                        Icons.Rounded.SkipPrevious,
                        contentDescription = "Previous track",
                        modifier = Modifier.size(34.dp)
                    )
                }

                Surface(
                    onClick = onTogglePlayPause,
                    shape = CircleShape,
                    color = photonColors.photonGlow,
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .size(72.dp)
                        .testTag("now_playing_play_pause_btn")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onSkipNext,
                    modifier = Modifier
                        .size(54.dp)
                        .testTag("now_playing_next_btn")
                ) {
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = "Next track",
                        modifier = Modifier.size(34.dp)
                    )
                }

                IconButton(
                    onClick = onCycleRepeat,
                    modifier = Modifier.testTag("now_playing_repeat_btn")
                ) {
                    Icon(
                        imageVector = if (playbackState.repeatMode == RepeatModeState.ONE) {
                            Icons.Rounded.RepeatOne
                        } else {
                            Icons.Rounded.Repeat
                        },
                        contentDescription = "Cycle Repeat Mode",
                        tint = if (playbackState.repeatMode != RepeatModeState.OFF) {
                            photonColors.photonGlow
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            // Bottom Quick Utility Strip (Signal Inspector, Auto-Tag, Smart Q, Equalizer, Add to Playlist)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp),
                shape = RoundedCornerShape(18.dp),
                color = photonColors.glassSurface,
                border = BorderStroke(0.9.dp, photonColors.cardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    QuickActionIcon(
                        icon = Icons.Rounded.GraphicEq,
                        label = "Signal",
                        tint = photonColors.losslessBadge,
                        tag = "now_playing_signal_btn",
                        onClick = { showCodecInspector = true }
                    )
                    QuickActionIcon(
                        icon = Icons.Rounded.AutoAwesome,
                        label = "Auto-Tag",
                        tint = photonColors.photonGlow,
                        tag = "now_playing_autotag_btn",
                        onClick = { onOpenMetadataAutoComplete(track) }
                    )
                    QuickActionIcon(
                        icon = Icons.AutoMirrored.Rounded.QueueMusic,
                        label = "Smart Q",
                        tint = photonColors.goldBadge,
                        tag = "now_playing_smart_queue_btn",
                        onClick = {
                            onCollapse()
                            onOpenSmartQueue()
                        }
                    )
                    QuickActionIcon(
                        icon = Icons.Rounded.Equalizer,
                        label = "Equalizer",
                        tint = photonColors.nebulaAccent,
                        tag = "now_playing_eq_btn",
                        onClick = {
                            onCollapse()
                            onOpenEqualizer()
                        }
                    )
                    QuickActionIcon(
                        icon = Icons.Rounded.PlaylistAdd,
                        label = "Playlist",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        tag = "now_playing_add_playlist_btn",
                        onClick = { onAddToPlaylist(track) }
                    )
                }
            }
        }
    }

    if (showCodecInspector && track != null) {
        AudioCodecInspectorSheet(
            track = track,
            signalInfo = playbackState.signalInfo,
            settings = settings,
            onOpenEqualizer = {
                showCodecInspector = false
                onCollapse()
                onOpenEqualizer()
            },
            onDismiss = { showCodecInspector = false }
        )
    }
}

@Composable
private fun QuickActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    tag: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag(tag)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
