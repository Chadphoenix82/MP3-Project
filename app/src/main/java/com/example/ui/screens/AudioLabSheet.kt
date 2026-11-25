package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.library.StudioWavSampleType
import com.example.data.model.CrossfadeCurveShape
import com.example.data.model.EqualizerPreset
import com.example.data.model.PhotonEqPresets
import com.example.data.model.ReplayGainMode
import com.example.data.model.ReplayGainScanProgress
import com.example.data.prefs.AppSettings
import com.example.player.EqTelemetry
import com.example.ui.theme.LocalPhotonColors
import kotlin.math.roundToInt

/**
 * Audio Lab Modal Sheet combining:
 * 1) 10-Band Equalizer + Preamp + Genre/Custom Presets + Bass Boost + Virtualizer
 * 2) Crossfade (0-12s), Gapless Playback & ReplayGain / LUFS Normalization
 * 3) Studio WAV Sample Generator (sine sweeps, pink noise, test tones, synth/drum stems)
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AudioLabModalSheet(
    settings: AppSettings,
    eqTelemetry: EqTelemetry,
    replayGainScanProgress: ReplayGainScanProgress,
    onDismiss: () -> Unit,
    onSetEqEnabled: (Boolean) -> Unit,
    onApplyPreset: (EqualizerPreset) -> Unit,
    onUpdateBand: (Int, Float) -> Unit,
    onSaveCustomPreset: (String) -> Unit,
    onDeleteCustomPreset: (String) -> Unit,
    onSetPreAmpDb: (Float) -> Unit,
    onUpdateSpatial: (bass: Int, virtualizer: Int) -> Unit,
    onSetCrossfadeSeconds: (Int) -> Unit,
    onSetCrossfadeCurve: (CrossfadeCurveShape) -> Unit,
    onSetGaplessEnabled: (Boolean) -> Unit,
    onSetReplayGainMode: (ReplayGainMode) -> Unit,
    onSetReplayGainTargetLufs: (Float) -> Unit,
    onSetPreventClipping: (Boolean) -> Unit,
    onScanLibraryLoudness: () -> Unit,
    onGenerateStudioWavSample: (StudioWavSampleType) -> Unit
) {
    val photonColors = LocalPhotonColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var activeTab by remember { mutableIntStateOf(0) }
    var showSavePresetDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.testTag("audio_lab_modal_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header + Active Audio Session DSP Telemetry
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "PHOTON AUDIO LAB",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${eqTelemetry.engineLabel} · Session #${eqTelemetry.attachedSessionId.takeIf { it > 0 } ?: "Standby"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = photonColors.photonGlow
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (eqTelemetry.hardwareActive) photonColors.losslessBadge.copy(alpha = 0.18f) else photonColors.glassSurface,
                    border = BorderStroke(1.dp, if (eqTelemetry.hardwareActive) photonColors.losslessBadge else photonColors.cardBorder)
                ) {
                    Text(
                        text = if (eqTelemetry.hardwareActive) "EQ LOCKED" else "PCM DSP",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (eqTelemetry.hardwareActive) photonColors.losslessBadge else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    shape = SegmentedButtonDefaults.itemShape(0, 3),
                    icon = { Icon(Icons.Rounded.Equalizer, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("10-Band EQ", maxLines = 1) },
                    modifier = Modifier.testTag("audio_lab_tab_eq")
                )
                SegmentedButton(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    shape = SegmentedButtonDefaults.itemShape(1, 3),
                    icon = { Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Mix & LUFS", maxLines = 1) },
                    modifier = Modifier.testTag("audio_lab_tab_mix")
                )
                SegmentedButton(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    shape = SegmentedButtonDefaults.itemShape(2, 3),
                    icon = { Icon(Icons.Rounded.Waves, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("WAV Lab", maxLines = 1) },
                    modifier = Modifier.testTag("audio_lab_tab_wav")
                )
            }

            when (activeTab) {
                0 -> {
                    // 10-Band Equalizer + Preamp + Bass Boost + Virtualizer
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = photonColors.glassSurface,
                        border = BorderStroke(1.dp, photonColors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("10-Band Mastering EQ", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                Text("Stays bound across crossfade handoffs", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = settings.eqEnabled,
                                onCheckedChange = onSetEqEnabled,
                                modifier = Modifier.testTag("audio_lab_eq_switch")
                            )
                        }
                    }

                    // Presets Carousel (Built-in + Custom DataStore Presets)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("EQ PRESETS", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = photonColors.photonGlow)
                        OutlinedButton(
                            onClick = { showSavePresetDialog = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("audio_lab_save_preset_btn")
                        ) {
                            Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Save Custom", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(settings.allEqPresets(), key = { "${it.isCustom}_${it.name}" }) { preset ->
                            val selected = settings.eqPresetName.equals(preset.name, ignoreCase = true)
                            FilterChip(
                                selected = selected,
                                onClick = { onApplyPreset(preset) },
                                label = { Text(preset.name) },
                                trailingIcon = if (preset.isCustom) {
                                    {
                                        Icon(
                                            Icons.Rounded.Delete,
                                            contentDescription = "Delete ${preset.name}",
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable { onDeleteCustomPreset(preset.name) }
                                        )
                                    }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = photonColors.photonGlow.copy(alpha = 0.24f),
                                    selectedLabelColor = photonColors.photonGlow
                                )
                            )
                        }
                    }

                    // Preamp Slider
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = photonColors.glassSurface,
                        border = BorderStroke(0.8.dp, photonColors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Preamp Gain", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = String.format("%+.1f dB", settings.preAmpDb),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = photonColors.photonGlow
                                )
                            }
                            Slider(
                                value = settings.preAmpDb,
                                onValueChange = onSetPreAmpDb,
                                valueRange = -12f..12f,
                                colors = SliderDefaults.colors(thumbColor = photonColors.photonGlow, activeTrackColor = photonColors.photonGlow)
                            )
                        }
                    }

                    // 10-Band Frequency Faders
                    val bands = PhotonEqPresets.normalizeTo10Bands(settings.eqBandRatios)
                    val freqs = PhotonEqPresets.canonical10BandFrequenciesHz
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = photonColors.glassSurface,
                        border = BorderStroke(0.8.dp, photonColors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            freqs.forEachIndexed { idx, hz ->
                                val ratio = bands[idx]
                                val db = ratio * 12f
                                val label = if (hz >= 1000) "${hz / 1000}kHz" else "${hz}Hz"
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.width(46.dp)
                                    )
                                    Slider(
                                        value = ratio,
                                        onValueChange = { onUpdateBand(idx, it) },
                                        enabled = settings.eqEnabled,
                                        valueRange = -1f..1f,
                                        modifier = Modifier.weight(1f),
                                        colors = SliderDefaults.colors(
                                            thumbColor = photonColors.photonGlow,
                                            activeTrackColor = photonColors.photonGlow
                                        )
                                    )
                                    Text(
                                        text = String.format("%+.0fdB", db),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = photonColors.photonGlow,
                                        modifier = Modifier.width(42.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Bass Boost & Virtualizer
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = photonColors.glassSurface,
                        border = BorderStroke(0.8.dp, photonColors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Bass Boost", style = MaterialTheme.typography.titleSmall)
                                Text("${settings.bassBoostStrength / 10}%", style = MaterialTheme.typography.labelMedium, color = photonColors.photonGlow)
                            }
                            Slider(
                                value = settings.bassBoostStrength.toFloat(),
                                onValueChange = { onUpdateSpatial(it.roundToInt(), settings.virtualizerStrength) },
                                enabled = settings.eqEnabled,
                                valueRange = 0f..1000f
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("3D Virtualizer / Stage Width", style = MaterialTheme.typography.titleSmall)
                                Text("${settings.virtualizerStrength / 10}%", style = MaterialTheme.typography.labelMedium, color = photonColors.nebulaAccent)
                            }
                            Slider(
                                value = settings.virtualizerStrength.toFloat(),
                                onValueChange = { onUpdateSpatial(settings.bassBoostStrength, it.roundToInt()) },
                                enabled = settings.eqEnabled,
                                valueRange = 0f..1000f
                            )
                        }
                    }
                }

                1 -> {
                    // Crossfade (0-12s), Gapless Playback & ReplayGain Normalization
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = photonColors.glassSurface,
                        border = BorderStroke(1.dp, photonColors.photonGlow.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("Crossfade Transitions", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                    Text("Dual-player blend with live EQ session handoff", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(
                                    text = if (settings.crossfadeSeconds == 0) "Off" else "${settings.crossfadeSeconds}s",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = photonColors.photonGlow
                                )
                            }
                            Slider(
                                value = settings.crossfadeSeconds.toFloat(),
                                onValueChange = { onSetCrossfadeSeconds(it.roundToInt()) },
                                valueRange = 0f..12f,
                                steps = 11,
                                modifier = Modifier.testTag("audio_lab_crossfade_slider")
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                CrossfadeCurveShape.entries.forEach { curve ->
                                    FilterChip(
                                        selected = settings.crossfadeCurve == curve,
                                        onClick = { onSetCrossfadeCurve(curve) },
                                        label = { Text(curve.label) }
                                    )
                                }
                            }
                            HorizontalDivider(color = photonColors.cardBorder.copy(alpha = 0.5f))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Gapless Playback", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                    Text("Pre-buffer next track for 0ms album transitions (when crossfade = 0s)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = settings.gaplessPlaybackEnabled,
                                    onCheckedChange = onSetGaplessEnabled,
                                    modifier = Modifier.testTag("audio_lab_gapless_switch")
                                )
                            }
                        }
                    }

                    // ReplayGain / LUFS Normalization Card
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = photonColors.glassSurface,
                        border = BorderStroke(1.dp, photonColors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("ReplayGain 2.0 & ITU-R BS.1770 LUFS", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ReplayGainMode.entries.forEach { mode ->
                                    FilterChip(
                                        selected = settings.replayGainMode == mode,
                                        onClick = { onSetReplayGainMode(mode) },
                                        label = { Text(mode.label) }
                                    )
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Target Loudness", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = String.format("%.1f LUFS", settings.replayGainTargetLufs),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = photonColors.losslessBadge
                                )
                            }
                            Slider(
                                value = settings.replayGainTargetLufs,
                                onValueChange = onSetReplayGainTargetLufs,
                                valueRange = -20f..-10f
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Peak Clipping Protection (-0.1 dBFS)", style = MaterialTheme.typography.bodySmall)
                                Switch(checked = settings.preventClipping, onCheckedChange = onSetPreventClipping)
                            }
                            if (replayGainScanProgress.isScanning) {
                                LinearProgressIndicator(
                                    progress = {
                                        if (replayGainScanProgress.totalCount > 0) {
                                            replayGainScanProgress.scannedCount.toFloat() / replayGainScanProgress.totalCount.toFloat()
                                        } else 0f
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    text = "Scanning ${replayGainScanProgress.scannedCount}/${replayGainScanProgress.totalCount}: ${replayGainScanProgress.currentTrackTitle}",
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else {
                                Button(
                                    onClick = onScanLibraryLoudness,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = photonColors.photonGlow,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Rounded.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Analyze Library Loudness (LUFS)")
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // Generate Studio WAV Samples
                    Text(
                        text = "GENERATE STUDIO WAV SAMPLES (44.1kHz / 16-BIT STEREO)",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = photonColors.photonGlow
                    )
                    Text(
                        text = "Synthesize uncompressed PCM test tones, acoustic sweeps, pink noise, and synth/drum stems with instant import into your local library.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    StudioWavSampleType.entries.forEach { sample ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = photonColors.glassSurface,
                            border = BorderStroke(0.9.dp, photonColors.cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(photonColors.photonGlow.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.Waves, contentDescription = null, tint = photonColors.photonGlow)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(sample.displayName, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                    Text("${sample.subtitle} · ${sample.durationSec}s", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Button(
                                    onClick = { onGenerateStudioWavSample(sample) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("generate_wav_${sample.name.lowercase()}")
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Generate")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSavePresetDialog) {
        var presetName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
            title = { Text("Save Custom EQ Preset") },
            text = {
                OutlinedTextField(
                    value = presetName,
                    onValueChange = { presetName = it },
                    label = { Text("Preset Name") },
                    placeholder = { Text("e.g. Planar Headphones") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (presetName.isNotBlank()) {
                            onSaveCustomPreset(presetName.trim())
                            showSavePresetDialog = false
                        }
                    },
                    enabled = presetName.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSavePresetDialog = false }) { Text("Cancel") }
            }
        )
    }
}
