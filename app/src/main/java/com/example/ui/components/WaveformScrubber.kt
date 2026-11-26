package com.example.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalPhotonColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private const val WAVEFORM_BAR_COUNT = 56

/**
 * Interactive acoustic waveform scrubber for Now Playing.
 * Renders a deterministic per-track waveform envelope with theme-synced played glow,
 * A-B loop highlighted segment markers, haptic bar ticks, and live scrub timestamp preview.
 */
@Composable
fun WaveformScrubber(
    trackId: Long,
    positionMs: Long,
    durationMs: Long,
    abLoopStartMs: Long?,
    abLoopEndMs: Long?,
    isPlaying: Boolean,
    onSeekPreview: (Long?) -> Unit,
    onSeekCommit: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val photonColors = LocalPhotonColors.current
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val density = LocalDensity.current

    val safeDuration = durationMs.coerceAtLeast(1000L)

    // Deterministic organic waveform amplitudes seeded by trackId & duration
    val bars = remember(trackId, safeDuration) {
        val rng = Random(trackId * 31L + safeDuration / 1000L)
        FloatArray(WAVEFORM_BAR_COUNT) { idx ->
            val t = idx.toFloat() / (WAVEFORM_BAR_COUNT - 1).toFloat()
            // Musical envelope: gentle intro, energetic mid-choruses, organic outro
            val songEnvelope = (0.35f + 0.65f * sin(t * PI).toFloat())
            val phraseWave = 0.72f + 0.28f * sin(t * PI * 5.0 + (trackId % 7)).toFloat()
            val detail = 0.55f + 0.45f * rng.nextFloat()
            (songEnvelope * phraseWave * detail).coerceIn(0.16f, 1.0f)
        }
    }

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    var canvasWidthPx by remember { mutableFloatStateOf(1f) }
    var lastTickBar by remember { mutableIntStateOf(-1) }

    val activeFraction = if (isScrubbing) {
        scrubFraction
    } else {
        (positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    }

    val previewMs = (activeFraction * safeDuration).toLong().coerceIn(0L, safeDuration)

    fun updateFromX(xPx: Float, triggerTick: Boolean) {
        val fraction = (xPx / canvasWidthPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
        scrubFraction = fraction
        val targetMs = (fraction * safeDuration).toLong().coerceIn(0L, safeDuration)
        onSeekPreview(targetMs)
        if (triggerTick) {
            val barIdx = (fraction * (WAVEFORM_BAR_COUNT - 1)).roundToInt()
            if (barIdx != lastTickBar && abs(barIdx - lastTickBar) >= 2) {
                lastTickBar = barIdx
                val tickConstant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
                } else {
                    HapticFeedbackConstants.CLOCK_TICK
                }
                if (!view.performHapticFeedback(tickConstant)) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(66.dp)
            .semantics {
                contentDescription = "Audio waveform scrubber, ${formatDurationMs(previewMs)} of ${formatDurationMs(safeDuration)}"
            }
            .testTag("now_playing_seek_slider")
            .onSizeChanged { canvasWidthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(trackId, safeDuration) {
                detectTapGestures { offset ->
                    val fraction = (offset.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                    val targetMs = (fraction * safeDuration).toLong().coerceIn(0L, safeDuration)
                    if (!view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    onSeekCommit(targetMs)
                }
            }
            .pointerInput(trackId, safeDuration) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isScrubbing = true
                        lastTickBar = -1
                        updateFromX(offset.x, triggerTick = false)
                        if (!view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        updateFromX(change.position.x, triggerTick = true)
                    },
                    onDragEnd = {
                        val commitMs = (scrubFraction * safeDuration).toLong().coerceIn(0L, safeDuration)
                        isScrubbing = false
                        onSeekPreview(null)
                        val confirmConstant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            HapticFeedbackConstants.CONFIRM
                        } else {
                            HapticFeedbackConstants.CONTEXT_CLICK
                        }
                        if (!view.performHapticFeedback(confirmConstant)) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        onSeekCommit(commitMs)
                    },
                    onDragCancel = {
                        isScrubbing = false
                        onSeekPreview(null)
                    }
                )
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        // Floating live timestamp preview pill while scrubbing
        AnimatedVisibility(
            visible = isScrubbing,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset {
                    val pillHalfPx = with(density) { 28.dp.toPx() }
                    val x = (activeFraction * canvasWidthPx - pillHalfPx)
                        .coerceIn(0f, (canvasWidthPx - pillHalfPx * 2f).coerceAtLeast(0f))
                    IntOffset(x.roundToInt(), 0)
                }
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = photonColors.backdropTop.copy(alpha = 0.94f),
                border = BorderStroke(1.dp, photonColors.photonGlow)
            ) {
                Text(
                    text = formatDurationMs(previewMs),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = photonColors.photonGlow,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .align(Alignment.BottomCenter)
        ) {
            val w = size.width
            val h = size.height
            val centerY = h * 0.54f
            val playheadX = activeFraction * w

            // A-B Loop Highlight Region
            if (abLoopStartMs != null) {
                val startFrac = (abLoopStartMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
                val startX = startFrac * w
                if (abLoopEndMs != null && abLoopEndMs > abLoopStartMs) {
                    val endFrac = (abLoopEndMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
                    val endX = endFrac * w
                    drawRoundRect(
                        color = photonColors.goldBadge.copy(alpha = 0.18f),
                        topLeft = Offset(startX, 3.dp.toPx()),
                        size = Size((endX - startX).coerceAtLeast(2f), h - 6.dp.toPx()),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                    // B marker line
                    drawLine(
                        color = photonColors.goldBadge,
                        start = Offset(endX, 2.dp.toPx()),
                        end = Offset(endX, h - 2.dp.toPx()),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                // A marker line
                drawLine(
                    color = photonColors.goldBadge,
                    start = Offset(startX, 2.dp.toPx()),
                    end = Offset(startX, h - 2.dp.toPx()),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Draw symmetric acoustic waveform bars
            val slotW = w / WAVEFORM_BAR_COUNT.toFloat()
            val barW = (slotW * 0.58f).coerceIn(2.dp.toPx(), 4.5.dp.toPx())
            val corner = CornerRadius(barW / 2f, barW / 2f)
            val maxBarHalfH = h * 0.44f

            val playedBrush = Brush.verticalGradient(
                colors = listOf(
                    photonColors.photonGlow,
                    photonColors.nebulaAccent
                )
            )
            val unplayedColor = photonColors.cardBorder.copy(alpha = 0.55f)
            val activeAccentColor = photonColors.goldBadge

            for (i in 0 until WAVEFORM_BAR_COUNT) {
                val barCenterX = (i + 0.5f) * slotW
                val barLeft = barCenterX - barW / 2f
                val amp = bars[i]
                val barHalfH = (amp * maxBarHalfH).coerceAtLeast(3.dp.toPx())
                val topY = centerY - barHalfH
                val barH = barHalfH * 2f

                val inAbLoop = abLoopStartMs != null && abLoopEndMs != null &&
                    (barCenterX / w) in (abLoopStartMs.toFloat() / safeDuration)..(abLoopEndMs.toFloat() / safeDuration)

                when {
                    barCenterX <= playheadX -> {
                        if (inAbLoop) {
                            drawRoundRect(
                                color = activeAccentColor,
                                topLeft = Offset(barLeft, topY),
                                size = Size(barW, barH),
                                cornerRadius = corner
                            )
                        } else {
                            drawRoundRect(
                                brush = playedBrush,
                                topLeft = Offset(barLeft, topY),
                                size = Size(barW, barH),
                                cornerRadius = corner
                            )
                        }
                    }
                    inAbLoop -> {
                        drawRoundRect(
                            color = activeAccentColor.copy(alpha = 0.42f),
                            topLeft = Offset(barLeft, topY),
                            size = Size(barW, barH),
                            cornerRadius = corner
                        )
                    }
                    else -> {
                        drawRoundRect(
                            color = unplayedColor,
                            topLeft = Offset(barLeft, topY),
                            size = Size(barW, barH),
                            cornerRadius = corner
                        )
                    }
                }
            }

            // Glowing Playhead Needle
            val needleStroke = if (isScrubbing) 3.dp.toPx() else 2.2.dp.toPx()
            drawLine(
                color = photonColors.photonGlow.copy(alpha = if (isPlaying || isScrubbing) 0.45f else 0.25f),
                start = Offset(playheadX, 1.dp.toPx()),
                end = Offset(playheadX, h - 1.dp.toPx()),
                strokeWidth = needleStroke * 2.5f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White,
                start = Offset(playheadX, 2.dp.toPx()),
                end = Offset(playheadX, h - 2.dp.toPx()),
                strokeWidth = needleStroke,
                cap = StrokeCap.Round
            )
            drawCircle(
                color = photonColors.photonGlow,
                radius = if (isScrubbing) 6.dp.toPx() else 4.dp.toPx(),
                center = Offset(playheadX, centerY)
            )
        }
    }
}
