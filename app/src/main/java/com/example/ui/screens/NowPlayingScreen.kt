package com.example.ui.screens

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Speed
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
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
import com.example.ui.components.DrivingModeOverlay
import com.example.ui.components.GalaxyBackdrop
import com.example.ui.components.LiveQueueSheet
import com.example.ui.components.PhotonAlbumArt
import com.example.ui.components.PhotonAudioVisualizer
import com.example.ui.components.ProPlaybackStudioSheet
import com.example.ui.components.WaveformScrubber
import com.example.ui.components.formatDurationMs
import com.example.ui.components.formatSampleRate
import com.example.ui.theme.AdaptiveArtworkPalette
import com.example.ui.theme.AlbumArtColorExtractor
import com.example.ui.theme.LocalPhotonColors
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class GestureAxis { NONE, HORIZONTAL_SKIP, VERTICAL_VOLUME }

private enum class DeckHapticType { PLAY_PAUSE, TRACK_SEEK, LIGHT_TICK }

private fun View.performDeckHaptic(type: DeckHapticType) {
    val constant = when (type) {
        DeckHapticType.PLAY_PAUSE -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.CONTEXT_CLICK
            }
        }
        DeckHapticType.TRACK_SEEK -> HapticFeedbackConstants.CONTEXT_CLICK
        DeckHapticType.LIGHT_TICK -> HapticFeedbackConstants.CLOCK_TICK
    }
    if (!performHapticFeedback(constant)) {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
}

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
    onNavigateToArtist: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val track = playbackState.currentTrack ?: return
    val photonColors = LocalPhotonColors.current
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    var dragAccumX by remember { mutableFloatStateOf(0f) }
    var dragAccumY by remember { mutableFloatStateOf(0f) }

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
                detectDragGestures(
                    onDragStart = {
                        dragAccumX = 0f
                        dragAccumY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragAccumX += dragAmount.x
                        dragAccumY += dragAmount.y
                    },
                    onDragEnd = {
                        if (dragAccumY < -65f && abs(dragAccumY) > abs(dragAccumX)) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onExpandClick()
                        } else if (dragAccumX < -90f && abs(dragAccumX) >= abs(dragAccumY)) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSkipNext()
                        } else if (dragAccumX > 90f && abs(dragAccumX) >= abs(dragAccumY)) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSkipPrevious()
                        }
                        dragAccumX = 0f
                        dragAccumY = 0f
                    },
                    onDragCancel = {
                        dragAccumX = 0f
                        dragAccumY = 0f
                    }
                )
            }
            .clickable(onClick = onExpandClick),
        shape = RoundedCornerShape(22.dp),
        color = lerp(photonColors.backdropTop, photonColors.glassSurface, 0.72f).copy(alpha = 0.94f),
        shadowElevation = 14.dp,
        border = BorderStroke(
            width = 1.2.dp,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    photonColors.photonGlow.copy(alpha = 0.85f),
                    photonColors.nebulaAccent.copy(alpha = 0.65f),
                    photonColors.photonGlow.copy(alpha = 0.75f)
                )
            )
        )
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
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .basicMarquee(iterations = Int.MAX_VALUE)
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
                        modifier = (if (!playbackState.isCrossfading && onNavigateToArtist != null && track.artist.isNotBlank()) {
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onNavigateToArtist(track.artist) }
                                .padding(vertical = 2.dp)
                                .testTag("mini_player_artist_link")
                        } else {
                            Modifier
                        }).basicMarquee(iterations = Int.MAX_VALUE)
                    )
                }

                IconButton(
                    onClick = {
                        view.performDeckHaptic(DeckHapticType.TRACK_SEEK)
                        onSkipPrevious()
                    },
                    modifier = Modifier.testTag("mini_prev_button")
                ) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous track")
                }

                Surface(
                    onClick = {
                        view.performDeckHaptic(DeckHapticType.PLAY_PAUSE)
                        onTogglePlayPause()
                    },
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
                    onClick = {
                        view.performDeckHaptic(DeckHapticType.TRACK_SEEK)
                        onSkipNext()
                    },
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
@OptIn(ExperimentalLayoutApi::class)
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
    onAddToPlaylist: (TrackEntity) -> Unit,
    onToggleFavorite: (TrackEntity) -> Unit,
    onJumpToQueueIndex: (Int) -> Unit,
    onReorderQueue: (List<TrackEntity>) -> Unit,
    onRemoveQueueIndex: (Int) -> Unit,
    onClearUpcomingQueue: () -> Unit,
    onSaveQueueAsPlaylist: (String, String) -> Unit,
    onSetPlaybackSpeed: (Float) -> Unit,
    onSetPlaybackPitch: (Float) -> Unit,
    onResetTempoAndPitch: () -> Unit,
    onSetAbLoopStart: () -> Unit,
    onSetAbLoopEnd: () -> Unit,
    onClearAbLoop: () -> Unit,
    onUpdateTrackSoundProfile: (TrackEntity, Float, String?) -> Unit = { _, _, _ -> },
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToAlbum: (albumName: String, preferredArtist: String?) -> Unit = { _, _ -> },
    onOpenQueueScreen: (() -> Unit)? = null,
    onOpenLyricsInspector: ((Long) -> Unit)? = null,
    onOpenAudioLab: (() -> Unit)? = null
) {
    BackHandler(onBack = onCollapse)

    val track = playbackState.currentTrack
    val basePhotonColors = LocalPhotonColors.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val eqEnabled = settings.eqEnabled
    val eqPresetName = settings.eqPresetName
    val eqBandRatios = settings.eqBandRatios
    val gestureControlsEnabled = settings.gestureControlsEnabled

    // Dynamic Album-Art Adaptive Glow extraction
    var extractedPalette by remember { mutableStateOf<AdaptiveArtworkPalette?>(null) }
    LaunchedEffect(track?.artworkUri, settings.dynamicAlbumColorsEnabled, basePhotonColors) {
        extractedPalette = if (settings.dynamicAlbumColorsEnabled && !track?.artworkUri.isNullOrBlank()) {
            AlbumArtColorExtractor.extractPalette(
                context = context,
                artworkUri = track?.artworkUri,
                fallbackPrimary = basePhotonColors.photonGlow,
                fallbackSecondary = basePhotonColors.nebulaAccent,
                fallbackBackdrop = basePhotonColors.backdropTop
            )
        } else {
            null
        }
    }

    val animatedPrimaryGlow by animateColorAsState(
        targetValue = extractedPalette?.primaryGlow ?: basePhotonColors.photonGlow,
        animationSpec = tween(durationMillis = 620),
        label = "adaptive_primary_glow"
    )
    val animatedSecondaryGlow by animateColorAsState(
        targetValue = extractedPalette?.secondaryGlow ?: basePhotonColors.nebulaAccent,
        animationSpec = tween(durationMillis = 620),
        label = "adaptive_secondary_glow"
    )
    val animatedBackdropTint by animateColorAsState(
        targetValue = extractedPalette?.backdropTint ?: basePhotonColors.backdropTop,
        animationSpec = tween(durationMillis = 650),
        label = "adaptive_backdrop_tint"
    )
    val photonColors = remember(basePhotonColors, animatedPrimaryGlow, animatedSecondaryGlow) {
        basePhotonColors.copy(
            photonGlow = animatedPrimaryGlow,
            nebulaAccent = animatedSecondaryGlow
        )
    }

    var showCodecInspector by remember { mutableStateOf(false) }
    var showLiveQueueSheet by remember { mutableStateOf(false) }
    var showStudioSheet by remember { mutableStateOf(false) }
    var showDrivingMode by remember { mutableStateOf(false) }
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

    var scrubPreviewMs by remember { mutableStateOf<Long?>(null) }
    val displayPosMs = scrubPreviewMs ?: playbackState.positionMs
    val durationMs = playbackState.durationMs.coerceAtLeast(1000L)

    CompositionLocalProvider(LocalPhotonColors provides photonColors) {
        GalaxyBackdrop(
            modifier = Modifier.fillMaxSize(),
            adaptiveTint = if (extractedPalette != null) animatedBackdropTint else null
        ) {
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
            // Swipe-Down Dismissal Pill + Top Bar
            var headerDismissDragY by remember { mutableFloatStateOf(0f) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { headerDismissDragY = 0f },
                            onVerticalDrag = { _, dragAmount ->
                                if (dragAmount > 0f) headerDismissDragY += dragAmount
                            },
                            onDragEnd = {
                                if (headerDismissDragY > 80f) onCollapse()
                                headerDismissDragY = 0f
                            },
                            onDragCancel = { headerDismissDragY = 0f }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(photonColors.photonGlow.copy(alpha = 0.45f))
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { headerDismissDragY = 0f },
                            onVerticalDrag = { _, dragAmount ->
                                if (dragAmount > 0f) headerDismissDragY += dragAmount
                            },
                            onDragEnd = {
                                if (headerDismissDragY > 85f) onCollapse()
                                headerDismissDragY = 0f
                            },
                            onDragCancel = { headerDismissDragY = 0f }
                        )
                    },
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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showDrivingMode = true
                        },
                        modifier = Modifier.testTag("now_playing_driving_mode_btn")
                    ) {
                        Icon(
                            Icons.Rounded.DirectionsCar,
                            contentDescription = "Open Driving Mode",
                            tint = photonColors.photonGlow
                        )
                    }
                    if (track != null) {
                        IconButton(
                            onClick = { onOpenMetadataAutoComplete(track) },
                            modifier = Modifier.testTag("now_playing_autotag_btn")
                        ) {
                            Icon(
                                Icons.Rounded.AutoAwesome,
                                contentDescription = "Edit Track Metadata",
                                tint = photonColors.nebulaAccent
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
                val deckPulseTransition = rememberInfiniteTransition(label = "deck_stage_aura")
                val auraBreath by deckPulseTransition.animateFloat(
                    initialValue = 0.72f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = if (playbackState.isPlaying) 1900 else 3600, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "aura_breath"
                )

                // Holographic Photon Deck Stage Aura & Precision Orbital Ring
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val maxRadius = size.minDimension * 0.52f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                photonColors.photonGlow.copy(alpha = (if (playbackState.isPlaying) 0.28f else 0.14f) * auraBreath),
                                photonColors.nebulaAccent.copy(alpha = (if (playbackState.isPlaying) 0.16f else 0.08f) * auraBreath),
                                Color.Transparent
                            ),
                            center = center,
                            radius = maxRadius
                        ),
                        center = center,
                        radius = maxRadius
                    )
                    drawCircle(
                        color = photonColors.photonGlow.copy(alpha = 0.18f * auraBreath),
                        radius = maxRadius * 0.92f,
                        center = center,
                        style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Spacer(modifier = Modifier.width(44.dp))
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    val heartScale by animateFloatAsState(
                        targetValue = if (track.isFavorite) 1.18f else 1.0f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "heart_scale"
                    )
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleFavorite(track)
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("now_playing_favorite_btn")
                    ) {
                        Icon(
                            imageVector = if (track.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = if (track.isFavorite) "Remove from Favorites" else "Add to Favorites",
                            tint = if (track.isFavorite) Color(0xFFFF5C8A) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(26.dp)
                                .graphicsLayer {
                                    scaleX = heartScale
                                    scaleY = heartScale
                                }
                        )
                    }
                }
                // Interactive Artist & Album Navigation Links
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onNavigateToArtist(track.artist)
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = photonColors.photonGlow.copy(alpha = 0.14f),
                        border = BorderStroke(0.85.dp, photonColors.photonGlow.copy(alpha = 0.48f)),
                        modifier = Modifier.testTag("now_playing_artist_link")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = "View artist ${track.artist}",
                                tint = photonColors.photonGlow,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = photonColors.photonGlow,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 200.dp)
                            )
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = photonColors.photonGlow.copy(alpha = 0.8f),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onNavigateToAlbum(track.album, track.artist)
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = photonColors.nebulaAccent.copy(alpha = 0.14f),
                        border = BorderStroke(0.85.dp, photonColors.nebulaAccent.copy(alpha = 0.48f)),
                        modifier = Modifier.testTag("now_playing_album_link")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Album,
                                contentDescription = "View album ${track.album}",
                                tint = photonColors.nebulaAccent,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = track.album,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = photonColors.nebulaAccent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 200.dp)
                            )
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = photonColors.nebulaAccent.copy(alpha = 0.8f),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

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
                            text = "${signalInfo.sourceBitDepthBits}-bit/${formatSampleRate(displayRateHz)} • $displayBitrate kbps",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
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

            // Interactive Acoustic Waveform Scrubber
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                WaveformScrubber(
                    trackId = track.id,
                    positionMs = playbackState.positionMs,
                    durationMs = durationMs,
                    abLoopStartMs = playbackState.abLoopStartMs,
                    abLoopEndMs = playbackState.abLoopEndMs,
                    isPlaying = playbackState.isPlaying,
                    onSeekPreview = { scrubPreviewMs = it },
                    onSeekCommit = { onSeekTo(it) }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatDurationMs(displayPosMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                        Surface(
                            onClick = {
                                view.performDeckHaptic(DeckHapticType.LIGHT_TICK)
                                when {
                                    playbackState.abLoopActive -> onClearAbLoop()
                                    playbackState.abLoopStartMs != null -> onSetAbLoopEnd()
                                    else -> onSetAbLoopStart()
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (playbackState.abLoopStartMs != null) {
                                photonColors.goldBadge.copy(alpha = 0.16f)
                            } else {
                                photonColors.glassSurface
                            },
                            border = BorderStroke(
                                0.9.dp,
                                if (playbackState.abLoopStartMs != null) photonColors.goldBadge.copy(alpha = 0.7f) else photonColors.cardBorder
                            ),
                            modifier = Modifier.testTag("now_playing_ab_loop_badge")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Loop,
                                    contentDescription = "Toggle A-B Loop",
                                    tint = if (playbackState.abLoopStartMs != null) photonColors.goldBadge else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = when {
                                        playbackState.abLoopActive -> "A-B ${formatDurationMs(playbackState.abLoopStartMs!!)}–${formatDurationMs(playbackState.abLoopEndMs ?: 0L)}"
                                        playbackState.abLoopStartMs != null -> "Set B (A: ${formatDurationMs(playbackState.abLoopStartMs)})"
                                        else -> "A-B Loop"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (playbackState.abLoopStartMs != null) photonColors.goldBadge else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
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
                    onClick = {
                        view.performDeckHaptic(DeckHapticType.LIGHT_TICK)
                        onToggleShuffle()
                    },
                    modifier = Modifier.testTag("now_playing_shuffle_btn")
                ) {
                    Icon(
                        Icons.Rounded.Shuffle,
                        contentDescription = "Toggle Shuffle",
                        tint = if (playbackState.shuffleEnabled) photonColors.photonGlow else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = {
                        view.performDeckHaptic(DeckHapticType.TRACK_SEEK)
                        onSkipPrevious()
                    },
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

                val playInteraction = remember { MutableInteractionSource() }
                val playPressed by playInteraction.collectIsPressedAsState()
                val playScale by animateFloatAsState(
                    targetValue = if (playPressed) 0.91f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "now_playing_play_scale"
                )
                Box(
                    modifier = Modifier
                        .size(74.dp)
                        .graphicsLayer {
                            scaleX = playScale
                            scaleY = playScale
                        }
                        .shadow(
                            elevation = 16.dp,
                            shape = CircleShape,
                            ambientColor = photonColors.photonGlow,
                            spotColor = photonColors.photonGlow
                        )
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    lerp(photonColors.photonGlow, Color.White, 0.24f),
                                    photonColors.photonGlow,
                                    photonColors.nebulaAccent
                                )
                            )
                        )
                        .border(1.2.dp, Color.White.copy(alpha = 0.65f), CircleShape)
                        .clickable(
                            interactionSource = playInteraction,
                            indication = null,
                            onClick = {
                                view.performDeckHaptic(DeckHapticType.PLAY_PAUSE)
                                onTogglePlayPause()
                            }
                        )
                        .testTag("now_playing_play_pause_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                        tint = Color(0xFF040818),
                        modifier = Modifier.size(38.dp)
                    )
                }

                IconButton(
                    onClick = {
                        view.performDeckHaptic(DeckHapticType.TRACK_SEEK)
                        onSkipNext()
                    },
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
                    onClick = {
                        view.performDeckHaptic(DeckHapticType.LIGHT_TICK)
                        onCycleRepeat()
                    },
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

            // Bottom Flagship Quick-Action Dock: Queue, Lyrics & Inspector, Audio Lab, Sleep Timer
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
                        icon = Icons.AutoMirrored.Rounded.QueueMusic,
                        label = "Queue (${playbackState.queue.size})",
                        tint = photonColors.photonGlow,
                        tag = "now_playing_live_queue_btn",
                        onClick = {
                            if (onOpenQueueScreen != null) {
                                onCollapse()
                                onOpenQueueScreen()
                            } else {
                                showLiveQueueSheet = true
                            }
                        }
                    )
                    QuickActionIcon(
                        icon = Icons.Rounded.Lyrics,
                        label = "Lyrics & Info",
                        tint = photonColors.losslessBadge,
                        tag = "now_playing_lyrics_inspector_btn",
                        onClick = {
                            if (onOpenLyricsInspector != null) {
                                onCollapse()
                                onOpenLyricsInspector(track.id)
                            } else {
                                showCodecInspector = true
                            }
                        }
                    )
                    QuickActionIcon(
                        icon = Icons.Rounded.Equalizer,
                        label = "Audio Lab",
                        tint = photonColors.nebulaAccent,
                        tag = "now_playing_audio_lab_btn",
                        onClick = {
                            if (onOpenAudioLab != null) {
                                onOpenAudioLab()
                            } else {
                                onCollapse()
                                onOpenEqualizer()
                            }
                        }
                    )
                    QuickActionIcon(
                        icon = Icons.Rounded.Bedtime,
                        label = if (playbackState.sleepTimer.isActive) playbackState.sleepTimer.label else "Sleep",
                        tint = if (playbackState.sleepTimer.isActive) photonColors.goldBadge else MaterialTheme.colorScheme.onSurfaceVariant,
                        tag = "now_playing_dock_sleep_btn",
                        onClick = onOpenSleepTimer
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

    if (showLiveQueueSheet) {
        LiveQueueSheet(
            playbackState = playbackState,
            onJumpToIndex = onJumpToQueueIndex,
            onReorderQueue = onReorderQueue,
            onRemoveIndex = onRemoveQueueIndex,
            onClearUpcoming = onClearUpcomingQueue,
            onSaveQueueAsPlaylist = onSaveQueueAsPlaylist,
            onDismiss = { showLiveQueueSheet = false }
        )
    }

    if (showStudioSheet) {
        ProPlaybackStudioSheet(
            playbackState = playbackState,
            playbackSpeed = settings.playbackSpeed,
            playbackPitch = settings.playbackPitch,
            onSpeedChange = onSetPlaybackSpeed,
            onPitchChange = onSetPlaybackPitch,
            onResetTempoAndPitch = onResetTempoAndPitch,
            onSetLoopStart = onSetAbLoopStart,
            onSetLoopEnd = onSetAbLoopEnd,
            onClearLoop = onClearAbLoop,
            allEqPresets = settings.allEqPresets(),
            onUpdateTrackSoundProfile = onUpdateTrackSoundProfile,
            onDismiss = { showStudioSheet = false }
        )
    }

    AnimatedVisibility(
        visible = showDrivingMode,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        DrivingModeOverlay(
            playbackState = playbackState,
            onTogglePlayPause = onTogglePlayPause,
            onSkipNext = onSkipNext,
            onSkipPrevious = onSkipPrevious,
            onToggleFavorite = onToggleFavorite,
            onToggleShuffle = onToggleShuffle,
            onDismiss = { showDrivingMode = false }
        )
    }
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
