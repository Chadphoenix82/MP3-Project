package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.player.AudioSpectrumFrame
import com.example.ui.theme.LocalPhotonColors
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

enum class VisualizerStyle(val label: String) {
    SPECTRUM("Spectrum"),
    PHOTON_WAVE("Wave"),
    EQ_BANDS("EQ Bands")
}

/**
 * Real-time Photon Galaxy Audio Visualizer synced directly with live ExoPlayer PCM FFT data
 * and shaped by the active Equalizer's 5 frequency bands and dB gain curve.
 */
@Composable
fun PhotonAudioVisualizer(
    spectrumFlow: StateFlow<AudioSpectrumFrame>,
    isPlaying: Boolean,
    eqEnabled: Boolean,
    eqPresetName: String,
    eqBandRatios: List<Float>,
    onOpenEqualizer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spectrum by spectrumFlow.collectAsStateWithLifecycle()
    val photonColors = LocalPhotonColors.current
    var style by remember { mutableStateOf(VisualizerStyle.SPECTRUM) }

    // Peak-hold dots with gentle gravity decay
    val peakCaps = remember { mutableStateListOf<Float>().apply { repeat(28) { add(0f) } } }
    LaunchedEffect(spectrum.bars) {
        for (i in 0 until minOf(peakCaps.size, spectrum.bars.size)) {
            val current = spectrum.bars[i]
            val prevPeak = peakCaps[i]
            peakCaps[i] = if (current >= prevPeak) {
                current
            } else {
                (prevPeak - 0.025f).coerceAtLeast(current)
            }
        }
    }

    val cycleStyle = {
        style = when (style) {
            VisualizerStyle.SPECTRUM -> VisualizerStyle.PHOTON_WAVE
            VisualizerStyle.PHOTON_WAVE -> VisualizerStyle.EQ_BANDS
            VisualizerStyle.EQ_BANDS -> VisualizerStyle.SPECTRUM
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("now_playing_audio_visualizer"),
        shape = RoundedCornerShape(20.dp),
        color = photonColors.glassSurface,
        border = BorderStroke(
            width = 1.dp,
            color = if (isPlaying) {
                photonColors.photonGlow.copy(alpha = 0.35f + spectrum.bassEnergy * 0.45f)
            } else {
                photonColors.cardBorder
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Visualizer Header Row (EQ sync status + visualization style toggle)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clickable(onClick = onOpenEqualizer)
                        .testTag("visualizer_eq_badge")
                ) {
                    Icon(
                        imageVector = when (style) {
                            VisualizerStyle.SPECTRUM -> Icons.Rounded.GraphicEq
                            VisualizerStyle.PHOTON_WAVE -> Icons.Rounded.Waves
                            VisualizerStyle.EQ_BANDS -> Icons.Rounded.Equalizer
                        },
                        contentDescription = null,
                        tint = photonColors.photonGlow,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (eqEnabled) "EQ SYNC · $eqPresetName" else "LIVE SPECTRUM · FLAT",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = photonColors.photonGlow
                    )
                }

                Surface(
                    onClick = cycleStyle,
                    shape = RoundedCornerShape(10.dp),
                    color = photonColors.photonGlow.copy(alpha = 0.14f),
                    border = BorderStroke(0.75.dp, photonColors.photonGlow.copy(alpha = 0.5f)),
                    modifier = Modifier.testTag("visualizer_mode_toggle")
                ) {
                    Text(
                        text = style.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = photonColors.photonGlow,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                    )
                }
            }

            // Real-Time Frequency Canvas
            val cyan = photonColors.photonGlow
            val violet = photonColors.nebulaAccent
            val gold = photonColors.goldBadge
            val gridColor = photonColors.cardBorder.copy(alpha = 0.35f)

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .clickable(onClick = cycleStyle)
                    .testTag("visualizer_canvas")
            ) {
                val w = size.width
                val h = size.height
                if (w <= 0f || h <= 0f) return@Canvas

                // Subtle cosmic reference gridlines
                for (g in 1..3) {
                    val y = h * (g / 4f)
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1f
                    )
                }

                when (style) {
                    VisualizerStyle.SPECTRUM -> {
                        val bars = spectrum.bars
                        val n = bars.size.coerceAtLeast(1)
                        val gap = 3.dp.toPx()
                        val barW = ((w - gap * (n - 1)) / n).coerceAtLeast(2f)

                        for (i in 0 until n) {
                            val amp = bars[i].coerceIn(0.03f, 1f)
                            val barH = (h * amp).coerceAtLeast(3.dp.toPx())
                            val x = i * (barW + gap)
                            val top = h - barH

                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(gold.copy(alpha = 0.95f), cyan, violet),
                                    startY = 0f,
                                    endY = h
                                ),
                                topLeft = Offset(x, top),
                                size = Size(barW, barH),
                                cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                            )

                            // Luminous peak-hold cap
                            val peak = peakCaps.getOrElse(i) { amp }.coerceIn(amp, 1f)
                            if (peak > 0.06f) {
                                val peakY = (h - h * peak - 2.dp.toPx()).coerceAtLeast(0f)
                                drawRoundRect(
                                    color = Color(0xFFECF4FF),
                                    topLeft = Offset(x, peakY),
                                    size = Size(barW, 2.2.dp.toPx()),
                                    cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                                )
                            }
                        }

                        // Overlay subtle Equalizer target response curve
                        if (eqEnabled && spectrum.eqCurvePoints.size == n) {
                            val eqPath = Path()
                            spectrum.eqCurvePoints.forEachIndexed { idx, norm ->
                                val x = idx * (barW + gap) + barW / 2f
                                val y = h - (h * norm.coerceIn(0.08f, 0.92f))
                                if (idx == 0) eqPath.moveTo(x, y) else eqPath.lineTo(x, y)
                            }
                            drawPath(
                                path = eqPath,
                                color = gold.copy(alpha = 0.55f),
                                style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }

                    VisualizerStyle.PHOTON_WAVE -> {
                        val bars = spectrum.bars
                        val n = bars.size
                        if (n >= 2) {
                            val stepX = w / (n - 1).toFloat()
                            val wavePath = Path()
                            val fillPath = Path()

                            val firstY = h - (h * bars[0].coerceIn(0.05f, 0.96f))
                            wavePath.moveTo(0f, firstY)
                            fillPath.moveTo(0f, h)
                            fillPath.lineTo(0f, firstY)

                            for (i in 0 until n - 1) {
                                val x1 = i * stepX
                                val y1 = h - (h * bars[i].coerceIn(0.05f, 0.96f))
                                val x2 = (i + 1) * stepX
                                val y2 = h - (h * bars[i + 1].coerceIn(0.05f, 0.96f))
                                val midX = (x1 + x2) / 2f
                                wavePath.cubicTo(midX, y1, midX, y2, x2, y2)
                                fillPath.cubicTo(midX, y1, midX, y2, x2, y2)
                            }
                            fillPath.lineTo(w, h)
                            fillPath.close()

                            drawPath(
                                path = fillPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        cyan.copy(alpha = 0.45f),
                                        violet.copy(alpha = 0.14f),
                                        Color.Transparent
                                    )
                                )
                            )
                            drawPath(
                                path = wavePath,
                                brush = Brush.horizontalGradient(listOf(violet, cyan, gold, cyan)),
                                style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round)
                            )

                            // Mirrored harmonic inner line
                            val harmonicPath = Path()
                            for (i in 0 until n) {
                                val x = i * stepX
                                val y = h - (h * (bars[i] * 0.55f).coerceIn(0.03f, 0.9f))
                                if (i == 0) harmonicPath.moveTo(x, y) else harmonicPath.lineTo(x, y)
                            }
                            drawPath(
                                path = harmonicPath,
                                color = Color(0xAAECF4FF),
                                style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }

                    VisualizerStyle.EQ_BANDS -> {
                        val bands = spectrum.eqBandLevels
                        val count = bands.size.coerceAtLeast(1)
                        val gap = 12.dp.toPx()
                        val colW = (w - gap * (count - 1)) / count
                        val segments = 9
                        val segGap = 2.5.dp.toPx()
                        val segH = (h - segGap * (segments - 1)) / segments

                        for (b in 0 until count) {
                            val level = bands[b].coerceIn(0f, 1f)
                            val activeSegs = (level * segments).roundToInt().coerceIn(0, segments)
                            val x = b * (colW + gap)

                            for (s in 0 until segments) {
                                val segTop = h - (s + 1) * segH - s * segGap
                                val isLit = s < activeSegs
                                val segColor = when {
                                    !isLit -> gridColor.copy(alpha = 0.35f)
                                    s >= segments - 2 -> gold
                                    s >= segments - 5 -> cyan
                                    else -> violet
                                }
                                drawRoundRect(
                                    color = segColor,
                                    topLeft = Offset(x, segTop),
                                    size = Size(colW, segH),
                                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                                )
                            }
                        }
                    }
                }
            }

            // 5-Band Equalizer Center Frequency Footer (synced with EQ hardware bands + dB gains)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                spectrum.bandFrequenciesHz.take(5).forEachIndexed { idx, hz ->
                    val ratio = if (eqEnabled) eqBandRatios.getOrElse(idx) { 0f } else 0f
                    val db = (ratio * 12f).roundToInt()
                    val dbLabel = when {
                        !eqEnabled -> "0dB"
                        db > 0 -> "+${db}dB"
                        else -> "${db}dB"
                    }
                    val hzLabel = if (hz >= 1000) {
                        val khz = hz / 1000f
                        if (khz % 1f == 0f) "${khz.toInt()}k" else String.format("%.1fk", khz)
                    } else {
                        "${hz}Hz"
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = hzLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = dbLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (eqEnabled && db != 0) photonColors.photonGlow else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}
