package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.EqualizerPreset
import com.example.data.model.PhotonEqPresets
import com.example.player.EqTelemetry
import com.example.ui.theme.LocalPhotonColors
import kotlin.math.roundToInt

@Composable
fun EqualizerScreen(
    eqEnabled: Boolean,
    activePresetName: String,
    bandRatios: List<Float>,
    bassBoostStrength: Int,
    virtualizerStrength: Int,
    telemetry: EqTelemetry,
    onToggleEq: (Boolean) -> Unit,
    onSelectPreset: (EqualizerPreset) -> Unit,
    onBandChange: (Int, Float) -> Unit,
    onSpatialChange: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val photonColors = LocalPhotonColors.current
    val freqs = telemetry.bandFrequenciesHz

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 156.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header + Master Toggle
        item(key = "eq_header") {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = photonColors.glassSurface,
                border = BorderStroke(1.dp, photonColors.photonGlow.copy(alpha = if (eqEnabled) 0.65f else 0.25f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Equalizer,
                            contentDescription = null,
                            tint = if (eqEnabled) photonColors.photonGlow else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column {
                            Text(
                                text = "PHOTON EQUALIZER",
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = if (telemetry.hardwareActive) {
                                    "DSP Active · Session #${telemetry.attachedSessionId} · $activePresetName"
                                } else {
                                    "5-Band Graphic Engine · $activePresetName"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (eqEnabled) photonColors.photonGlow else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = eqEnabled,
                        onCheckedChange = onToggleEq,
                        modifier = Modifier.testTag("eq_master_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = photonColors.photonGlow
                        )
                    )
                }
            }
        }

        // Preset Selector Strip
        item(key = "eq_presets") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PhotonEqPresets.builtIn.forEach { preset ->
                    val selected = activePresetName.equals(preset.name, ignoreCase = true)
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectPreset(preset) },
                        label = { Text(preset.name, style = MaterialTheme.typography.labelMedium) },
                        modifier = Modifier.testTag("eq_preset_${preset.name.lowercase().replace(" ", "_")}"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = photonColors.photonGlow.copy(alpha = 0.22f),
                            selectedLabelColor = photonColors.photonGlow
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = photonColors.cardBorder,
                            selectedBorderColor = photonColors.photonGlow
                        )
                    )
                }
            }
        }

        // Real-Time Frequency Response Spline Curve Card
        item(key = "eq_curve") {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = photonColors.glassSurface,
                border = BorderStroke(1.dp, photonColors.cardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "FREQUENCY RESPONSE",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = photonColors.photonGlow
                        )
                        Text(
                            text = "${telemetry.minDb} dB  —  +${telemetry.maxDb} dB",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    PhotonFrequencyCurveCanvas(
                        bandRatios = if (eqEnabled) bandRatios else List(5) { 0f },
                        enabled = eqEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(126.dp)
                    )
                }
            }
        }

        // Interactive 5-Band Custom Graphic Equalizer Faders
        item(key = "eq_faders") {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = photonColors.glassSurface,
                border = BorderStroke(1.dp, photonColors.cardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "GRAPHIC BAND FADERS",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = photonColors.photonGlow
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(232.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        bandRatios.forEachIndexed { idx, ratio ->
                            val freqHz = freqs.getOrElse(idx) { listOf(60, 230, 910, 3600, 14000)[idx] }
                            val dbValue = (ratio * telemetry.maxDb).roundToInt()

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = if (dbValue > 0) "+${dbValue}dB" else "${dbValue}dB",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (eqEnabled) {
                                        if (dbValue > 4) photonColors.goldBadge else photonColors.photonGlow
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )

                                PhotonVerticalFader(
                                    ratio = ratio,
                                    enabled = eqEnabled,
                                    onRatioChange = { newRatio -> onBandChange(idx, newRatio) },
                                    modifier = Modifier
                                        .width(48.dp)
                                        .weight(1f)
                                        .testTag("eq_fader_band_$idx")
                                )

                                Text(
                                    text = formatFreqHz(freqHz),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bass Boost & 3D Cosmic Virtualizer Card
        item(key = "eq_spatial") {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = photonColors.glassSurface,
                border = BorderStroke(1.dp, photonColors.cardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "COSMIC AUDIO ENHANCEMENT",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = photonColors.nebulaAccent
                    )

                    // Bass Boost
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.GraphicEq,
                                    contentDescription = null,
                                    tint = photonColors.photonGlow
                                )
                                Text("Galaxy Bass Boost", style = MaterialTheme.typography.titleSmall)
                            }
                            Text(
                                text = "${(bassBoostStrength / 10)}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = photonColors.photonGlow
                            )
                        }
                        Slider(
                            value = bassBoostStrength.toFloat(),
                            onValueChange = { onSpatialChange(it.roundToInt(), virtualizerStrength) },
                            valueRange = 0f..1000f,
                            enabled = eqEnabled,
                            modifier = Modifier.testTag("eq_bass_boost_slider"),
                            colors = SliderDefaults.colors(
                                thumbColor = photonColors.photonGlow,
                                activeTrackColor = photonColors.photonGlow
                            )
                        )
                    }

                    // Spatial Virtualizer
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.SurroundSound,
                                    contentDescription = null,
                                    tint = photonColors.nebulaAccent
                                )
                                Text("Astral 3D Virtualizer", style = MaterialTheme.typography.titleSmall)
                            }
                            Text(
                                text = "${(virtualizerStrength / 10)}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = photonColors.nebulaAccent
                            )
                        }
                        Slider(
                            value = virtualizerStrength.toFloat(),
                            onValueChange = { onSpatialChange(bassBoostStrength, it.roundToInt()) },
                            valueRange = 0f..1000f,
                            enabled = eqEnabled,
                            modifier = Modifier.testTag("eq_virtualizer_slider"),
                            colors = SliderDefaults.colors(
                                thumbColor = photonColors.nebulaAccent,
                                activeTrackColor = photonColors.nebulaAccent
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotonFrequencyCurveCanvas(
    bandRatios: List<Float>,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val photonColors = LocalPhotonColors.current
    val gridColor = photonColors.cardBorder.copy(alpha = 0.65f)
    val glowCyan = if (enabled) photonColors.photonGlow else MaterialTheme.colorScheme.onSurfaceVariant
    val glowViolet = if (enabled) photonColors.nebulaAccent else MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val padY = 14.dp.toPx()
        val usableH = (h - padY * 2f).coerceAtLeast(1f)
        val centerY = padY + usableH / 2f

        // Horizontal dB reference gridlines
        val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
        drawLine(gridColor, Offset(0f, padY), Offset(w, padY), strokeWidth = 1.dp.toPx(), pathEffect = dash)
        drawLine(gridColor.copy(alpha = 0.9f), Offset(0f, centerY), Offset(w, centerY), strokeWidth = 1.2.dp.toPx())
        drawLine(gridColor, Offset(0f, h - padY), Offset(w, h - padY), strokeWidth = 1.dp.toPx(), pathEffect = dash)

        if (bandRatios.isEmpty()) return@Canvas

        val points = bandRatios.mapIndexed { idx, ratio ->
            val x = if (bandRatios.size == 1) w / 2f else (idx.toFloat() / (bandRatios.size - 1)) * w
            val y = centerY - (ratio.coerceIn(-1f, 1f) * (usableH / 2f))
            Offset(x, y)
        }

        val curvePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 0 until points.lastIndex) {
                val p0 = points[i]
                val p1 = points[i + 1]
                val midX = (p0.x + p1.x) / 2f
                cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
            }
        }

        val fillPath = Path().apply {
            addPath(curvePath)
            lineTo(points.last().x, h)
            lineTo(points.first().x, h)
            close()
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    glowCyan.copy(alpha = if (enabled) 0.32f else 0.10f),
                    glowViolet.copy(alpha = if (enabled) 0.12f else 0.04f),
                    Color.Transparent
                )
            )
        )

        // Outer neon halo stroke
        drawPath(
            path = curvePath,
            brush = Brush.horizontalGradient(listOf(glowCyan, glowViolet, glowCyan)),
            style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round),
            alpha = if (enabled) 0.25f else 0.08f
        )

        // Core crisp frequency line
        drawPath(
            path = curvePath,
            brush = Brush.horizontalGradient(listOf(glowCyan, glowViolet, glowCyan)),
            style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round)
        )

        // Glowing node diamonds
        points.forEach { pt ->
            drawCircle(color = glowCyan.copy(alpha = 0.28f), radius = 8.dp.toPx(), center = pt)
            drawCircle(color = glowCyan, radius = 4.dp.toPx(), center = pt)
        }
    }
}

@Composable
private fun PhotonVerticalFader(
    ratio: Float,
    enabled: Boolean,
    onRatioChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val photonColors = LocalPhotonColors.current
    val trackBg = photonColors.cardBorder.copy(alpha = 0.55f)
    val activeTop = if (enabled) photonColors.photonGlow else MaterialTheme.colorScheme.onSurfaceVariant
    val activeBottom = if (enabled) photonColors.nebulaAccent else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    val h = size.height.toFloat().coerceAtLeast(1f)
                    val newRatio = (1f - (offset.y / h) * 2f).coerceIn(-1f, 1f)
                    onRatioChange(newRatio)
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures { change, _ ->
                    change.consume()
                    val h = size.height.toFloat().coerceAtLeast(1f)
                    val newRatio = (1f - (change.position.y / h) * 2f).coerceIn(-1f, 1f)
                    onRatioChange(newRatio)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2f
            val padY = 12.dp.toPx()
            val trackH = (size.height - padY * 2f).coerceAtLeast(1f)
            val trackW = 8.dp.toPx()
            val zeroY = padY + trackH / 2f
            val thumbY = zeroY - (ratio.coerceIn(-1f, 1f) * (trackH / 2f))

            // Background rail
            drawRoundRect(
                color = trackBg,
                topLeft = Offset(centerX - trackW / 2f, padY),
                size = Size(trackW, trackH),
                cornerRadius = CornerRadius(trackW / 2f, trackW / 2f)
            )

            // Center 0dB tick
            drawLine(
                color = photonColors.starColor.copy(alpha = 0.45f),
                start = Offset(centerX - 10.dp.toPx(), zeroY),
                end = Offset(centerX + 10.dp.toPx(), zeroY),
                strokeWidth = 1.dp.toPx()
            )

            // Active fill from 0dB to thumb
            val fillTop = minOf(zeroY, thumbY)
            val fillHeight = kotlin.math.abs(zeroY - thumbY)
            if (fillHeight > 1f) {
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(activeTop, activeBottom)),
                    topLeft = Offset(centerX - trackW / 2f, fillTop),
                    size = Size(trackW, fillHeight),
                    cornerRadius = CornerRadius(trackW / 2f, trackW / 2f)
                )
            }

            // Glowing Photon fader cap
            drawCircle(
                color = activeTop.copy(alpha = 0.28f),
                radius = 13.dp.toPx(),
                center = Offset(centerX, thumbY)
            )
            drawCircle(
                color = activeTop,
                radius = 8.dp.toPx(),
                center = Offset(centerX, thumbY)
            )
            drawCircle(
                color = Color.White,
                radius = 3.dp.toPx(),
                center = Offset(centerX, thumbY)
            )
        }
    }
}

private fun formatFreqHz(hz: Int): String {
    return if (hz >= 1000) {
        val khz = hz / 1000f
        if (khz % 1f == 0f) "${khz.toInt()}kHz" else "%.1fkHz".format(khz)
    } else {
        "${hz}Hz"
    }
}
