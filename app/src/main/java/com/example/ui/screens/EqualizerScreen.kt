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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.db.TrackEntity
import com.example.data.model.AudioSignalInfo
import com.example.data.model.EqualizerPreset
import com.example.data.model.OutputRouteCategory
import com.example.data.model.PhotonEqPresets
import com.example.data.model.ReverbSpacePreset
import com.example.data.prefs.AppSettings
import com.example.player.AudioSpectrumFrame
import com.example.player.EqTelemetry
import com.example.ui.theme.LocalPhotonColors
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun EqualizerScreen(
    settings: AppSettings,
    telemetry: EqTelemetry,
    signalInfo: AudioSignalInfo,
    currentTrack: TrackEntity?,
    spectrumFlow: StateFlow<AudioSpectrumFrame>,
    onToggleEq: (Boolean) -> Unit,
    onSelectPreset: (EqualizerPreset) -> Unit,
    onBandChange: (Int, Float) -> Unit,
    onSaveCustomPreset: (String, String) -> Unit,
    onDeleteCustomPreset: (String) -> Unit,
    onSpatialChange: (Int, Int) -> Unit,
    onSetPreAmpDb: (Float) -> Unit,
    onSetSoftLimiterEnabled: (Boolean) -> Unit,
    onSetLoudnessNormalizerEnabled: (Boolean) -> Unit,
    onSetStereoBalance: (Float) -> Unit,
    onSetMonoAudioEnabled: (Boolean) -> Unit,
    onSetCrossfeedStrength: (Float) -> Unit,
    onSetReverbPreset: (ReverbSpacePreset) -> Unit,
    onSetAutoEqByOutputEnabled: (Boolean) -> Unit,
    onSetOutputRouteEqPreset: (OutputRouteCategory, String) -> Unit,
    onUpdateTrackSoundProfile: (TrackEntity, Float, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val photonColors = LocalPhotonColors.current
    val haptics = LocalHapticFeedback.current
    val spectrum by spectrumFlow.collectAsStateWithLifecycle()

    val eqEnabled = settings.eqEnabled
    val activePresetName = settings.eqPresetName
    val bandRatios = remember(settings.eqBandRatios) { PhotonEqPresets.normalizeTo10Bands(settings.eqBandRatios) }
    val freqs = telemetry.bandFrequenciesHz.takeIf { it.size == 10 } ?: PhotonEqPresets.canonical10BandFrequenciesHz
    val allPresets = remember(settings.customEqPresets) { settings.allEqPresets() }
    val currentRouteCategory = OutputRouteCategory.fromTransport(signalInfo.outputTransport)

    var showSavePresetDialog by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }

    if (showSavePresetDialog) {
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
            title = { Text("Save Custom 10-Band Preset") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Saves your current 10-band curve, Bass Boost, and 3D Virtualizer settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = newPresetName,
                        onValueChange = { newPresetName = it },
                        label = { Text("Preset name") },
                        placeholder = { Text("e.g. Late Night IEM") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_preset_name_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPresetName.isNotBlank()) {
                            onSaveCustomPreset(newPresetName.trim(), "Custom 10-band studio curve")
                            newPresetName = ""
                            showSavePresetDialog = false
                        }
                    },
                    enabled = newPresetName.isNotBlank(),
                    modifier = Modifier.testTag("confirm_save_preset_btn")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSavePresetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 156.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header + Master Toggle + Save Custom Preset
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
                                text = "PHOTON 10-BAND STUDIO",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = if (telemetry.hardwareActive) {
                                    "${telemetry.engineLabel} · #${telemetry.attachedSessionId} · $activePresetName"
                                } else {
                                    "${telemetry.engineLabel} · $activePresetName"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (eqEnabled) photonColors.photonGlow else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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

        // 2. Preset Strip (Built-in + Saved Custom Presets + Save CTA)
        item(key = "eq_presets") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "EQ PRESETS (${allPresets.size})",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = photonColors.photonGlow
                    )
                    Surface(
                        onClick = { showSavePresetDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        color = photonColors.photonGlow.copy(alpha = 0.16f),
                        border = BorderStroke(0.8.dp, photonColors.photonGlow.copy(alpha = 0.6f)),
                        modifier = Modifier.testTag("save_custom_eq_preset_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Save,
                                contentDescription = null,
                                tint = photonColors.photonGlow,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Save Curve",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = photonColors.photonGlow
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    allPresets.forEach { preset ->
                        val selected = activePresetName.equals(preset.name, ignoreCase = true)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectPreset(preset)
                            },
                            label = { Text(preset.name, style = MaterialTheme.typography.labelMedium) },
                            trailingIcon = if (preset.isCustom) {
                                {
                                    IconButton(
                                        onClick = { onDeleteCustomPreset(preset.name) },
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Delete ${preset.name}",
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            } else null,
                            modifier = Modifier.testTag("eq_preset_${preset.name.lowercase().replace(" ", "_")}"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = photonColors.photonGlow.copy(alpha = 0.22f),
                                selectedLabelColor = photonColors.photonGlow
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = if (preset.isCustom) photonColors.nebulaAccent.copy(alpha = 0.6f) else photonColors.cardBorder,
                                selectedBorderColor = photonColors.photonGlow
                            )
                        )
                    }
                }
            }
        }

        // 3. Real-Time 10-Band Frequency Curve + Live Spectrum Overlay
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
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (spectrum.isLiveAudio) "LIVE SPECTRUM & 10-BAND CURVE" else "10-BAND FREQUENCY RESPONSE",
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
                        bandRatios = if (eqEnabled) bandRatios else List(10) { 0f },
                        liveSpectrumBars = spectrum.bars,
                        enabled = eqEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(142.dp)
                            .testTag("eq_live_spectrum_curve")
                    )
                }
            }
        }

        // 4. Interactive 10-Band Vertical Graphic Faders (Scrollable Studio Console + 0dB Haptic Detent)
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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "10-BAND ISO GRAPHIC FADERS",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = photonColors.photonGlow
                            )
                            Text(
                                text = "Drag faders · Double-tap or feel detent at 0 dB",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                PhotonEqPresets.builtIn.firstOrNull()?.let(onSelectPreset)
                            },
                            modifier = Modifier.testTag("eq_flatten_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.RestartAlt,
                                contentDescription = "Reset curve to Flat",
                                tint = photonColors.photonGlow
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(232.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        bandRatios.forEachIndexed { idx, ratio ->
                            val freqHz = freqs.getOrElse(idx) { PhotonEqPresets.canonical10BandFrequenciesHz[idx] }
                            val dbValue = (ratio * telemetry.maxDb).roundToInt()

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.width(50.dp)
                            ) {
                                Text(
                                    text = if (dbValue > 0) "+${dbValue}dB" else "${dbValue}dB",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (eqEnabled) {
                                        when {
                                            dbValue > 4 -> photonColors.goldBadge
                                            dbValue == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
                                            else -> photonColors.photonGlow
                                        }
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
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. Per-Track Sound Memory Quick Bar (when a track is playing)
        if (currentTrack != null) {
            item(key = "per_track_sound_memory") {
                val hasPinned = !currentTrack.preferredEqPreset.isNullOrBlank() || abs(currentTrack.customGainDb) > 0.05f
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = photonColors.glassSurface,
                    border = BorderStroke(1.dp, if (hasPinned) photonColors.goldBadge.copy(alpha = 0.65f) else photonColors.cardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Bookmark,
                                contentDescription = null,
                                tint = photonColors.goldBadge,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "PER-TRACK SOUND MEMORY",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = photonColors.goldBadge
                                )
                                Text(
                                    text = currentTrack.title,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Text(
                            text = "Pinned EQ: ${currentTrack.preferredEqPreset ?: "Global/Auto"}  ·  Track Trim: ${formatDbSigned(currentTrack.customGainDb)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onUpdateTrackSoundProfile(currentTrack, currentTrack.customGainDb, activePresetName)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("pin_eq_to_track_btn"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Pin \"$activePresetName\"", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (hasPinned) {
                                TextButton(
                                    onClick = { onUpdateTrackSoundProfile(currentTrack, 0f, null) },
                                    modifier = Modifier.testTag("clear_track_memory_btn")
                                ) {
                                    Text("Clear")
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6. Auto-EQ Profiles by Output Device Route (Bluetooth / Wired-USB / Speaker)
        item(key = "auto_eq_output_card") {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = photonColors.glassSurface,
                border = BorderStroke(
                    1.dp,
                    if (settings.autoEqByOutputEnabled) photonColors.photonGlow.copy(alpha = 0.6f) else photonColors.cardBorder
                )
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
                            Icon(Icons.Rounded.AutoMode, contentDescription = null, tint = photonColors.photonGlow)
                            Column {
                                Text(
                                    text = "Auto-EQ by Output Device",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Active: ${signalInfo.outputDeviceName} (${currentRouteCategory.label})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = photonColors.photonGlow,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Switch(
                            checked = settings.autoEqByOutputEnabled,
                            onCheckedChange = onSetAutoEqByOutputEnabled,
                            modifier = Modifier.testTag("auto_eq_output_switch"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = photonColors.photonGlow
                            )
                        )
                    }

                    OutputRoutePresetRow(
                        icon = Icons.Rounded.BluetoothAudio,
                        category = OutputRouteCategory.BLUETOOTH,
                        selectedPresetName = settings.bluetoothEqPreset,
                        isCurrentRoute = currentRouteCategory == OutputRouteCategory.BLUETOOTH,
                        allPresets = allPresets,
                        onSelect = { onSetOutputRouteEqPreset(OutputRouteCategory.BLUETOOTH, it) }
                    )
                    OutputRoutePresetRow(
                        icon = Icons.Rounded.Headphones,
                        category = OutputRouteCategory.WIRED_USB,
                        selectedPresetName = settings.wiredEqPreset,
                        isCurrentRoute = currentRouteCategory == OutputRouteCategory.WIRED_USB,
                        allPresets = allPresets,
                        onSelect = { onSetOutputRouteEqPreset(OutputRouteCategory.WIRED_USB, it) }
                    )
                    OutputRoutePresetRow(
                        icon = Icons.Rounded.Speaker,
                        category = OutputRouteCategory.SPEAKER,
                        selectedPresetName = settings.speakerEqPreset,
                        isCurrentRoute = currentRouteCategory == OutputRouteCategory.SPEAKER,
                        allPresets = allPresets,
                        onSelect = { onSetOutputRouteEqPreset(OutputRouteCategory.SPEAKER, it) }
                    )
                }
            }
        }

        // 7. Studio Audio Mastering DSP Card (Pre-Amp, Soft Limiter, Loudness Normalizer, Balance, Crossfeed, Reverb)
        item(key = "studio_mastering_card") {
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Tune, contentDescription = null, tint = photonColors.photonGlow)
                        Column {
                            Text(
                                text = "STUDIO MASTERING DSP",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = photonColors.photonGlow
                            )
                            Text(
                                text = "Real-time PCM preamp, normalizer, crossfeed & room reverb",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Pre-Amp Gain (-12 dB to +12 dB) with 0dB haptic detent
                    var lastPreAmpSign by remember { mutableFloatStateOf(settings.preAmpDb) }
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Pre-Amp Master Gain", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = formatDbSigned(settings.preAmpDb),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (settings.preAmpDb > 4f) photonColors.goldBadge else photonColors.photonGlow
                            )
                        }
                        Slider(
                            value = settings.preAmpDb,
                            onValueChange = { raw ->
                                val snapped = if (abs(raw) < 0.45f) 0f else (raw * 2f).roundToInt() / 2f
                                if ((lastPreAmpSign != 0f && snapped == 0f) || (lastPreAmpSign * snapped < 0f)) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                lastPreAmpSign = snapped
                                onSetPreAmpDb(snapped)
                            },
                            valueRange = -12f..12f,
                            modifier = Modifier.testTag("dsp_preamp_slider"),
                            colors = SliderDefaults.colors(
                                thumbColor = photonColors.photonGlow,
                                activeTrackColor = photonColors.photonGlow
                            )
                        )
                    }

                    // Soft-Clip Limiter & Loudness Normalizer toggles
                    DspToggleRow(
                        title = "Soft-Clip Limiter",
                        subtitle = "Analog tanh saturation prevents distortion at high gain",
                        checked = settings.softLimiterEnabled,
                        onCheckedChange = onSetSoftLimiterEnabled,
                        tag = "dsp_soft_limiter_switch"
                    )
                    DspToggleRow(
                        title = "Loudness Normalizer",
                        subtitle = "Adaptive RMS leveling keeps quiet & loud tracks consistent",
                        checked = settings.loudnessNormalizerEnabled,
                        onCheckedChange = onSetLoudnessNormalizerEnabled,
                        tag = "dsp_loudness_normalizer_switch"
                    )

                    HorizontalDivider(color = photonColors.cardBorder.copy(alpha = 0.5f))

                    // Stereo L/R Balance with center detent + Mono Downmix
                    var lastBal by remember { mutableFloatStateOf(settings.stereoBalance) }
                    val balLabel = when {
                        abs(settings.stereoBalance) < 0.04f -> "Center"
                        settings.stereoBalance < 0f -> "L ${(abs(settings.stereoBalance) * 100f).roundToInt()}%"
                        else -> "R ${(settings.stereoBalance * 100f).roundToInt()}%"
                    }
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Stereo L / R Balance", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = balLabel,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = photonColors.photonGlow
                            )
                        }
                        Slider(
                            value = settings.stereoBalance,
                            onValueChange = { raw ->
                                val snapped = if (abs(raw) < 0.05f) 0f else raw
                                if ((lastBal != 0f && snapped == 0f) || (lastBal * snapped < 0f)) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                lastBal = snapped
                                onSetStereoBalance(snapped)
                            },
                            valueRange = -1f..1f,
                            enabled = !settings.monoAudioEnabled,
                            modifier = Modifier.testTag("dsp_balance_slider"),
                            colors = SliderDefaults.colors(
                                thumbColor = photonColors.photonGlow,
                                activeTrackColor = photonColors.photonGlow
                            )
                        )
                    }

                    DspToggleRow(
                        title = "Mono Audio Downmix",
                        subtitle = "Combines left & right channels cleanly for single-ear listening",
                        checked = settings.monoAudioEnabled,
                        onCheckedChange = onSetMonoAudioEnabled,
                        tag = "dsp_mono_switch"
                    )

                    HorizontalDivider(color = photonColors.cardBorder.copy(alpha = 0.5f))

                    // Headphone Crossfeed
                    val crossfeedLabel = when {
                        settings.crossfeedStrength <= 0.02f -> "Off"
                        settings.crossfeedStrength < 0.40f -> "Subtle (${(settings.crossfeedStrength * 100).roundToInt()}%)"
                        settings.crossfeedStrength < 0.75f -> "Natural (${(settings.crossfeedStrength * 100).roundToInt()}%)"
                        else -> "Studio (${(settings.crossfeedStrength * 100).roundToInt()}%)"
                    }
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
                                Icon(Icons.Rounded.Hearing, contentDescription = null, tint = photonColors.nebulaAccent)
                                Text("Headphone Crossfeed", style = MaterialTheme.typography.titleSmall)
                            }
                            Text(
                                text = crossfeedLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = photonColors.nebulaAccent
                            )
                        }
                        Slider(
                            value = settings.crossfeedStrength,
                            onValueChange = onSetCrossfeedStrength,
                            valueRange = 0f..1f,
                            enabled = !settings.monoAudioEnabled,
                            modifier = Modifier.testTag("dsp_crossfeed_slider"),
                            colors = SliderDefaults.colors(
                                thumbColor = photonColors.nebulaAccent,
                                activeTrackColor = photonColors.nebulaAccent
                            )
                        )
                    }

                    // Acoustic Room Reverb Selector
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "ACOUSTIC ROOM REVERB",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = photonColors.nebulaAccent
                            )
                            Text(
                                text = settings.reverbPreset.subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ReverbSpacePreset.entries.forEach { space ->
                                val selected = settings.reverbPreset == space
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onSetReverbPreset(space)
                                    },
                                    label = { Text(space.label, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.testTag("reverb_preset_${space.name.lowercase()}"),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = photonColors.nebulaAccent.copy(alpha = 0.24f),
                                        selectedLabelColor = photonColors.nebulaAccent
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = selected,
                                        borderColor = photonColors.cardBorder,
                                        selectedBorderColor = photonColors.nebulaAccent
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // 8. Bass Boost & 3D Cosmic Virtualizer Card
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
                                Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = photonColors.photonGlow)
                                Text("Galaxy Bass Boost", style = MaterialTheme.typography.titleSmall)
                            }
                            Text(
                                text = "${(settings.bassBoostStrength / 10)}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = photonColors.photonGlow
                            )
                        }
                        Slider(
                            value = settings.bassBoostStrength.toFloat(),
                            onValueChange = { onSpatialChange(it.roundToInt(), settings.virtualizerStrength) },
                            valueRange = 0f..1000f,
                            enabled = eqEnabled,
                            modifier = Modifier.testTag("eq_bass_boost_slider"),
                            colors = SliderDefaults.colors(
                                thumbColor = photonColors.photonGlow,
                                activeTrackColor = photonColors.photonGlow
                            )
                        )
                    }

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
                                Icon(Icons.Rounded.SurroundSound, contentDescription = null, tint = photonColors.nebulaAccent)
                                Text("Astral 3D Virtualizer", style = MaterialTheme.typography.titleSmall)
                            }
                            Text(
                                text = "${(settings.virtualizerStrength / 10)}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = photonColors.nebulaAccent
                            )
                        }
                        Slider(
                            value = settings.virtualizerStrength.toFloat(),
                            onValueChange = { onSpatialChange(settings.bassBoostStrength, it.roundToInt()) },
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
private fun OutputRoutePresetRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    category: OutputRouteCategory,
    selectedPresetName: String,
    isCurrentRoute: Boolean,
    allPresets: List<EqualizerPreset>,
    onSelect: (String) -> Unit
) {
    val photonColors = LocalPhotonColors.current
    var expanded by remember { mutableStateOf(false) }

    Surface(
        onClick = { expanded = true },
        shape = RoundedCornerShape(14.dp),
        color = if (isCurrentRoute) photonColors.photonGlow.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(0.9.dp, if (isCurrentRoute) photonColors.photonGlow.copy(alpha = 0.6f) else photonColors.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("output_route_preset_${category.name.lowercase()}")
    ) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isCurrentRoute) photonColors.photonGlow else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = category.label,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        if (isCurrentRoute) {
                            Text(
                                text = "Connected now",
                                style = MaterialTheme.typography.labelSmall,
                                color = photonColors.losslessBadge
                            )
                        }
                    }
                }
                Text(
                    text = selectedPresetName,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = photonColors.photonGlow
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                allPresets.forEach { preset ->
                    DropdownMenuItem(
                        text = { Text(preset.name) },
                        onClick = {
                            onSelect(preset.name)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DspToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    val photonColors = LocalPhotonColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag),
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = photonColors.photonGlow
            )
        )
    }
}

@Composable
private fun PhotonFrequencyCurveCanvas(
    bandRatios: List<Float>,
    liveSpectrumBars: List<Float>,
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

        // Background Live Real-Time Spectrum Analyzer Bars behind EQ spline
        if (liveSpectrumBars.isNotEmpty()) {
            val n = liveSpectrumBars.size
            val gap = 2.5.dp.toPx()
            val barW = ((w - gap * (n - 1)) / n).coerceAtLeast(2f)
            for (i in 0 until n) {
                val amp = liveSpectrumBars[i].coerceIn(0f, 1f)
                if (amp > 0.015f) {
                    val barH = (h * amp * 0.88f).coerceAtLeast(2.dp.toPx())
                    val x = i * (barW + gap)
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                glowCyan.copy(alpha = 0.34f),
                                glowViolet.copy(alpha = 0.16f),
                                Color.Transparent
                            ),
                            startY = h - barH,
                            endY = h
                        ),
                        topLeft = Offset(x, h - barH),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                    )
                }
            }
        }

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
                    glowCyan.copy(alpha = if (enabled) 0.28f else 0.08f),
                    glowViolet.copy(alpha = if (enabled) 0.10f else 0.03f),
                    Color.Transparent
                )
            )
        )

        drawPath(
            path = curvePath,
            brush = Brush.horizontalGradient(listOf(glowCyan, glowViolet, glowCyan)),
            style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round),
            alpha = if (enabled) 0.25f else 0.08f
        )

        drawPath(
            path = curvePath,
            brush = Brush.horizontalGradient(listOf(glowCyan, glowViolet, glowCyan)),
            style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round)
        )

        points.forEach { pt ->
            drawCircle(color = glowCyan.copy(alpha = 0.28f), radius = 6.5.dp.toPx(), center = pt)
            drawCircle(color = glowCyan, radius = 3.2.dp.toPx(), center = pt)
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
    val haptics = LocalHapticFeedback.current
    val trackBg = photonColors.cardBorder.copy(alpha = 0.55f)
    val activeTop = if (enabled) photonColors.photonGlow else MaterialTheme.colorScheme.onSurfaceVariant
    val activeBottom = if (enabled) photonColors.nebulaAccent else MaterialTheme.colorScheme.onSurfaceVariant
    var lastRatio by remember(ratio) { mutableFloatStateOf(ratio) }

    Box(
        modifier = modifier
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onDoubleTap = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        lastRatio = 0f
                        onRatioChange(0f)
                    },
                    onTap = { offset ->
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        val raw = (1f - (offset.y / h) * 2f).coerceIn(-1f, 1f)
                        val snapped = if (abs(raw) < 0.06f) 0f else raw
                        if (snapped == 0f) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        lastRatio = snapped
                        onRatioChange(snapped)
                    }
                )
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures { change, _ ->
                    change.consume()
                    val h = size.height.toFloat().coerceAtLeast(1f)
                    val raw = (1f - (change.position.y / h) * 2f).coerceIn(-1f, 1f)
                    val snapped = if (abs(raw) < 0.055f) 0f else raw
                    if ((lastRatio != 0f && snapped == 0f) || (lastRatio * snapped < 0f)) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    lastRatio = snapped
                    onRatioChange(snapped)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2f
            val padY = 12.dp.toPx()
            val trackH = (size.height - padY * 2f).coerceAtLeast(1f)
            val trackW = 7.dp.toPx()
            val zeroY = padY + trackH / 2f
            val thumbY = zeroY - (ratio.coerceIn(-1f, 1f) * (trackH / 2f))

            drawRoundRect(
                color = trackBg,
                topLeft = Offset(centerX - trackW / 2f, padY),
                size = Size(trackW, trackH),
                cornerRadius = CornerRadius(trackW / 2f, trackW / 2f)
            )

            // Center 0 dB detent mark
            drawLine(
                color = photonColors.starColor.copy(alpha = 0.55f),
                start = Offset(centerX - 10.dp.toPx(), zeroY),
                end = Offset(centerX + 10.dp.toPx(), zeroY),
                strokeWidth = 1.1.dp.toPx()
            )

            val fillTop = minOf(zeroY, thumbY)
            val fillHeight = abs(zeroY - thumbY)
            if (fillHeight > 1f) {
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(activeTop, activeBottom)),
                    topLeft = Offset(centerX - trackW / 2f, fillTop),
                    size = Size(trackW, fillHeight),
                    cornerRadius = CornerRadius(trackW / 2f, trackW / 2f)
                )
            }

            drawCircle(
                color = activeTop.copy(alpha = 0.28f),
                radius = 12.dp.toPx(),
                center = Offset(centerX, thumbY)
            )
            drawCircle(
                color = activeTop,
                radius = 7.5.dp.toPx(),
                center = Offset(centerX, thumbY)
            )
            drawCircle(
                color = Color.White,
                radius = 2.8.dp.toPx(),
                center = Offset(centerX, thumbY)
            )
        }
    }
}

private fun formatFreqHz(hz: Int): String {
    return if (hz >= 1000) {
        val khz = hz / 1000f
        if (khz % 1f == 0f) "${khz.toInt()}k" else "%.1fk".format(khz)
    } else {
        "${hz}"
    }
}

private fun formatDbSigned(db: Float): String {
    return when {
        abs(db) < 0.05f -> "0.0 dB"
        db > 0f -> "+%.1f dB".format(db)
        else -> "%.1f dB".format(db)
    }
}
