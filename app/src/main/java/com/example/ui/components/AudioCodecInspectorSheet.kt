package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.db.TrackEntity
import com.example.data.model.AudioSignalInfo
import com.example.data.model.CodecQualityTier
import com.example.data.model.OutputTransportType
import com.example.data.prefs.AppSettings
import com.example.ui.theme.LocalPhotonColors

/**
 * Interactive 4-Stage Live Audio Codec & Signal Path Inspector sheet:
 * 1. Source Stream (codec, quality tier, bit depth, sample rate, channels, live bitrate)
 * 2. Decoder Engine (active Media3 decoder, Hardware DSP vs Software CPU badge, decoded PCM depth)
 * 3. DSP & Equalizer Stage (EQ preset/bypass, bass boost, virtualizer, crossfade, playback speed)
 * 4. Active Output Route (Bluetooth A2DP / BLE Audio / USB-C DAC / Wired / Speaker + supported codecs)
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AudioCodecInspectorSheet(
    track: TrackEntity,
    signalInfo: AudioSignalInfo,
    settings: AppSettings,
    onOpenEqualizer: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val photonColors = LocalPhotonColors.current
    var showLegend by remember { mutableStateOf(false) }

    val tierColor = when (signalInfo.qualityTier) {
        CodecQualityTier.UNCOMPRESSED -> photonColors.goldBadge
        CodecQualityTier.LOSSLESS -> photonColors.losslessBadge
        CodecQualityTier.LOSSY -> photonColors.photonGlow
    }

    val decoderColor = if (signalInfo.isHardwareAccelerated) {
        photonColors.losslessBadge
    } else {
        photonColors.photonGlow
    }

    val outputIcon = when (signalInfo.outputTransport) {
        OutputTransportType.BLUETOOTH_A2DP,
        OutputTransportType.BLUETOOTH_LE -> Icons.Rounded.BluetoothAudio
        OutputTransportType.USB_DAC -> Icons.Rounded.Usb
        OutputTransportType.WIRED_HEADSET -> Icons.Rounded.Headphones
        else -> Icons.Rounded.Speaker
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.testTag("audio_codec_inspector_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Sheet Title Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AUDIO SIGNAL PATH",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = photonColors.photonGlow,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                AudioFormatBadge(format = track.format)
            }

            Spacer(modifier = Modifier.height(6.dp))

            // STAGE 1: Source Stream
            SignalStageCard(
                stepNumber = "01",
                stageTitle = "Source Stream",
                icon = Icons.Rounded.AudioFile,
                accent = tierColor,
                badgeText = signalInfo.qualityTier.label.uppercase(),
                tag = "signal_stage_source"
            ) {
                Text(
                    text = signalInfo.codecDisplayName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (signalInfo.mimeType.isNotBlank()) {
                    Text(
                        text = "MIME: ${signalInfo.mimeType}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MetricChip(label = "Bit Depth", value = "${signalInfo.sourceBitDepthBits}-bit", accent = tierColor)
                    MetricChip(label = "Sample Rate", value = formatSampleRate(signalInfo.sourceSampleRateHz), accent = tierColor)
                    MetricChip(
                        label = "Bitrate",
                        value = if (signalInfo.liveBitrateKbps > 0) "${signalInfo.liveBitrateKbps} kbps" else "${track.bitrateKbps} kbps",
                        accent = tierColor
                    )
                    MetricChip(label = "Channels", value = signalInfo.channelLayoutLabel, accent = tierColor)
                }
            }

            SignalConnectorLine(topColor = tierColor, bottomColor = decoderColor)

            // STAGE 2: Decoder Engine (Hardware DSP vs Software CPU)
            SignalStageCard(
                stepNumber = "02",
                stageTitle = "Decoder Engine",
                icon = Icons.Rounded.Memory,
                accent = decoderColor,
                badgeText = when {
                    !signalInfo.decoderDetected -> "AUTO"
                    signalInfo.isHardwareAccelerated -> "HARDWARE DSP"
                    else -> "SOFTWARE CPU"
                },
                tag = "signal_stage_decoder"
            ) {
                Text(
                    text = signalInfo.decoderName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (signalInfo.isHardwareAccelerated) {
                        "Hardware-accelerated silicon audio decoding for low power & minimal latency."
                    } else {
                        "Precision software decoding via Media3 AudioSink PCM pipeline."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MetricChip(label = "Decoded PCM", value = "${signalInfo.decodedPcmBitDepthBits}-bit", accent = decoderColor)
                    MetricChip(label = "Pipeline Rate", value = formatSampleRate(signalInfo.outputSampleRateHz), accent = decoderColor)
                    MetricChip(label = "Speed", value = "${String.format("%.2gx", settings.playbackSpeed)}", accent = decoderColor)
                }
            }

            SignalConnectorLine(topColor = decoderColor, bottomColor = photonColors.nebulaAccent)

            // STAGE 3: DSP & Equalizer Stage
            SignalStageCard(
                stepNumber = "03",
                stageTitle = "DSP & Equalizer Stage",
                icon = Icons.Rounded.Equalizer,
                accent = photonColors.nebulaAccent,
                badgeText = if (settings.eqEnabled) settings.eqPresetName.uppercase() else "BYPASSED",
                tag = "signal_stage_dsp",
                onClick = {
                    onDismiss()
                    onOpenEqualizer()
                }
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MetricChip(
                        label = "Equalizer",
                        value = if (settings.eqEnabled) "5-Band Active" else "Flat / Off",
                        accent = photonColors.nebulaAccent
                    )
                    MetricChip(
                        label = "Bass Boost",
                        value = if (settings.eqEnabled) "${settings.bassBoostStrength / 10}%" else "Off",
                        accent = photonColors.nebulaAccent
                    )
                    MetricChip(
                        label = "3D Virtualizer",
                        value = if (settings.eqEnabled) "${settings.virtualizerStrength / 10}%" else "Off",
                        accent = photonColors.nebulaAccent
                    )
                    MetricChip(
                        label = "Crossfade",
                        value = if (settings.crossfadeSeconds > 0) "${settings.crossfadeSeconds}s" else "Gapless",
                        accent = photonColors.nebulaAccent
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tap to tune 5-band Equalizer & live FFT response",
                    style = MaterialTheme.typography.labelSmall,
                    color = photonColors.photonGlow
                )
            }

            SignalConnectorLine(topColor = photonColors.nebulaAccent, bottomColor = photonColors.photonGlow)

            // STAGE 4: Active Output Route (Bluetooth / USB DAC / Wired / Speaker)
            SignalStageCard(
                stepNumber = "04",
                stageTitle = "Active Output Route",
                icon = outputIcon,
                accent = photonColors.photonGlow,
                badgeText = signalInfo.outputTransport.label.uppercase(),
                tag = "signal_stage_output"
            ) {
                Text(
                    text = signalInfo.outputDeviceName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "SUPPORTED OUTPUT CODECS & ENCODINGS",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = photonColors.photonGlow
                )
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    signalInfo.supportedOutputCodecs.forEach { codec ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = photonColors.photonGlow.copy(alpha = 0.14f),
                            border = BorderStroke(0.75.dp, photonColors.photonGlow.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = codec,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = photonColors.photonGlow,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Supported Rates: ${signalInfo.supportedSampleRatesHz.joinToString(" · ") { formatSampleRate(it) }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Collapsible Plain-English Codec Quick Legend
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = photonColors.glassSurface,
                border = BorderStroke(0.9.dp, photonColors.cardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showLegend = !showLegend }
                    .testTag("codec_legend_toggle")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Rounded.HelpOutline,
                                contentDescription = null,
                                tint = photonColors.photonGlow,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "What do these codec metrics mean?",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                        Icon(
                            imageVector = if (showLegend) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = "Toggle codec guide",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AnimatedVisibility(visible = showLegend) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            LegendRow(
                                title = "Uncompressed (WAV)",
                                detail = "Raw PCM studio audio with zero compression and maximum fidelity."
                            )
                            LegendRow(
                                title = "Lossless (FLAC)",
                                detail = "Shrinks file size by 30–50% while restoring every original audio sample bit-for-bit."
                            )
                            LegendRow(
                                title = "Lossy (MP3, AAC, Opus, Ogg)",
                                detail = "Compact files optimized for human hearing. Opus & AAC preserve detail efficiently even at lower bitrates."
                            )
                            LegendRow(
                                title = "Hardware DSP vs. Software CPU",
                                detail = "Hardware decoders offload work to dedicated audio silicon for lower battery use; software decoders run on the CPU for universal format compatibility."
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SignalStageCard(
    stepNumber: String,
    stageTitle: String,
    icon: ImageVector,
    accent: Color,
    badgeText: String,
    tag: String,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val photonColors = LocalPhotonColors.current
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = photonColors.glassSurface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .testTag(tag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                    }
                    Text(
                        text = "$stepNumber · ${stageTitle.uppercase()}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = accent
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = accent.copy(alpha = 0.16f),
                    border = BorderStroke(0.7.dp, accent.copy(alpha = 0.6f))
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = accent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun SignalConnectorLine(topColor: Color, bottomColor: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(2.5.dp)
                .height(14.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(topColor, bottomColor)))
        )
    }
}

@Composable
private fun MetricChip(label: String, value: String, accent: Color) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(0.7.dp, accent.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun LegendRow(title: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = LocalPhotonColors.current.photonGlow
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

fun formatSampleRate(hz: Int): String {
    if (hz <= 0) return "44.1 kHz"
    val khz = hz / 1000f
    return if (khz % 1f == 0f) "${khz.toInt()} kHz" else String.format("%.1f kHz", khz)
}
