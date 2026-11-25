package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.db.TrackEntity
import com.example.data.model.EqualizerPreset
import com.example.data.model.PhotonEqPresets
import com.example.player.PlaybackUiState
import com.example.ui.theme.LocalPhotonColors
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

private val QUICK_SPEED_PRESETS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

/**
 * Pro Playback Studio Modal Sheet for real-time Playback Speed, Pitch Shift, and A-B Loop controls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProPlaybackStudioSheet(
    playbackState: PlaybackUiState,
    playbackSpeed: Float,
    playbackPitch: Float,
    onSpeedChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit,
    onResetTempoAndPitch: () -> Unit,
    onSetLoopStart: () -> Unit,
    onSetLoopEnd: () -> Unit,
    onClearLoop: () -> Unit,
    allEqPresets: List<EqualizerPreset> = PhotonEqPresets.builtIn,
    onUpdateTrackSoundProfile: (TrackEntity, Float, String?) -> Unit = { _, _, _ -> },
    onDismiss: () -> Unit
) {
    val photonColors = LocalPhotonColors.current
    val haptic = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val semitones = (12.0 * ln(playbackPitch.toDouble().coerceAtLeast(0.5)) / ln(2.0)).toFloat()
    val semitoneLabel = if (abs(semitones) < 0.08f) "0.0 st" else String.format("%+.1f st", semitones)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 26.dp)
                .testTag("pro_playback_studio_sheet"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        tint = photonColors.photonGlow,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "STUDIO TEMPO, PITCH & A-B LOOP",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Real-time Media3 DSP tempo & segment repeat",
                            style = MaterialTheme.typography.labelSmall,
                            color = photonColors.photonGlow
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("studio_sheet_close_btn")
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close studio controls")
                }
            }

            // 1. Speed Control Card
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = photonColors.glassSurface,
                border = BorderStroke(1.dp, photonColors.cardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Rounded.Speed, contentDescription = null, tint = photonColors.photonGlow, modifier = Modifier.size(18.dp))
                            Text(
                                text = "Playback Speed",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Text(
                            text = String.format("%.2fx", playbackSpeed),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = photonColors.photonGlow
                        )
                    }

                    Slider(
                        value = playbackSpeed,
                        onValueChange = { onSpeedChange(((it * 20f).toInt() / 20f).coerceIn(0.5f, 2.0f)) },
                        valueRange = 0.5f..2.0f,
                        modifier = Modifier.testTag("studio_speed_slider"),
                        colors = SliderDefaults.colors(
                            thumbColor = photonColors.photonGlow,
                            activeTrackColor = photonColors.photonGlow,
                            inactiveTrackColor = photonColors.cardBorder
                        )
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        QUICK_SPEED_PRESETS.forEach { preset ->
                            val selected = abs(playbackSpeed - preset) < 0.03f
                            FilterChip(
                                selected = selected,
                                onClick = { onSpeedChange(preset) },
                                label = { Text("${preset}x", style = MaterialTheme.typography.labelMedium) },
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
            }

            // 2. Pitch Control Card
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = photonColors.glassSurface,
                border = BorderStroke(1.dp, photonColors.cardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = photonColors.nebulaAccent, modifier = Modifier.size(18.dp))
                            Text(
                                text = "Pitch Shift",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Text(
                            text = "${String.format("%.2fx", playbackPitch)} ($semitoneLabel)",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = photonColors.nebulaAccent
                        )
                    }

                    Slider(
                        value = playbackPitch,
                        onValueChange = { onPitchChange(((it * 20f).toInt() / 20f).coerceIn(0.5f, 1.5f)) },
                        valueRange = 0.5f..1.5f,
                        modifier = Modifier.testTag("studio_pitch_slider"),
                        colors = SliderDefaults.colors(
                            thumbColor = photonColors.nebulaAccent,
                            activeTrackColor = photonColors.nebulaAccent,
                            inactiveTrackColor = photonColors.cardBorder
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = onResetTempoAndPitch,
                            enabled = abs(playbackSpeed - 1.0f) > 0.01f || abs(playbackPitch - 1.0f) > 0.01f,
                            modifier = Modifier.testTag("studio_reset_tempo_pitch_btn")
                        ) {
                            Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset Speed & Pitch (1.0x)")
                        }
                    }
                }
            }

            HorizontalDivider(color = photonColors.cardBorder.copy(alpha = 0.5f))

            // 3. Precision A-B Loop Card
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = photonColors.glassSurface,
                border = BorderStroke(
                    width = if (playbackState.abLoopActive) 1.3.dp else 1.dp,
                    color = if (playbackState.abLoopActive) photonColors.goldBadge else photonColors.cardBorder
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Loop,
                                contentDescription = null,
                                tint = if (playbackState.abLoopActive) photonColors.goldBadge else photonColors.photonGlow,
                                modifier = Modifier.size(19.dp)
                            )
                            Text(
                                text = "A-B Segment Loop",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Text(
                            text = when {
                                playbackState.abLoopActive ->
                                    "${formatDurationMs(playbackState.abLoopStartMs ?: 0L)} → ${formatDurationMs(playbackState.abLoopEndMs ?: 0L)}"
                                playbackState.abLoopStartMs != null ->
                                    "A: ${formatDurationMs(playbackState.abLoopStartMs)} · Tap Set B"
                                else ->
                                    "Now: ${formatDurationMs(playbackState.positionMs)}"
                            },
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (playbackState.abLoopActive) photonColors.goldBadge else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSetLoopStart()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("ab_loop_set_a_btn"),
                            shape = RoundedCornerShape(13.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (playbackState.abLoopStartMs != null) photonColors.goldBadge else MaterialTheme.colorScheme.primaryContainer,
                                contentColor = if (playbackState.abLoopStartMs != null) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Text(
                                text = playbackState.abLoopStartMs?.let { "A · ${formatDurationMs(it)}" } ?: "Set A",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1
                            )
                        }

                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSetLoopEnd()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("ab_loop_set_b_btn"),
                            shape = RoundedCornerShape(13.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (playbackState.abLoopActive) photonColors.photonGlow else MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = if (playbackState.abLoopActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Text(
                                text = playbackState.abLoopEndMs?.let { "B · ${formatDurationMs(it)}" } ?: "Set B",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1
                            )
                        }

                        OutlinedButton(
                            onClick = onClearLoop,
                            enabled = playbackState.abLoopStartMs != null || playbackState.abLoopEndMs != null,
                            modifier = Modifier
                                .height(46.dp)
                                .testTag("ab_loop_clear_btn"),
                            shape = RoundedCornerShape(13.dp)
                        ) {
                            Text("Clear")
                        }
                    }
                }
            }

            // 4. Per-Track Sound Memory Card (Gain Offset + Preferred EQ Profile)
            playbackState.currentTrack?.let { activeTrack ->
                val hasTrackOverride = abs(activeTrack.customGainDb) > 0.05f || !activeTrack.preferredEqPreset.isNullOrBlank()
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = photonColors.glassSurface,
                    border = BorderStroke(
                        width = if (hasTrackOverride) 1.2.dp else 1.dp,
                        color = if (hasTrackOverride) photonColors.photonGlow.copy(alpha = 0.7f) else photonColors.cardBorder
                    ),
                    modifier = Modifier.testTag("studio_track_sound_memory_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.GraphicEq,
                                    contentDescription = null,
                                    tint = photonColors.photonGlow,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Per-Track Sound Memory",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            Text(
                                text = String.format("%+.1f dB", activeTrack.customGainDb),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = if (abs(activeTrack.customGainDb) > 0.05f) photonColors.photonGlow else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Slider(
                            value = activeTrack.customGainDb.coerceIn(-6f, 6f),
                            onValueChange = { raw ->
                                val snapped = if (abs(raw) < 0.32f) 0f else (raw * 2f).roundToInt() / 2f
                                if (snapped == 0f && abs(activeTrack.customGainDb) > 0.05f) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                onUpdateTrackSoundProfile(activeTrack, snapped, activeTrack.preferredEqPreset)
                            },
                            valueRange = -6f..6f,
                            modifier = Modifier.testTag("studio_track_gain_slider"),
                            colors = SliderDefaults.colors(
                                thumbColor = photonColors.photonGlow,
                                activeTrackColor = photonColors.photonGlow,
                                inactiveTrackColor = photonColors.cardBorder
                            )
                        )

                        Text(
                            text = "PREFERRED EQ PRESET FOR THIS TRACK",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val isAutoEq = activeTrack.preferredEqPreset.isNullOrBlank()
                            FilterChip(
                                selected = isAutoEq,
                                onClick = { onUpdateTrackSoundProfile(activeTrack, activeTrack.customGainDb, null) },
                                label = { Text("Global EQ", style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = photonColors.photonGlow.copy(alpha = 0.22f),
                                    selectedLabelColor = photonColors.photonGlow
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isAutoEq,
                                    borderColor = photonColors.cardBorder,
                                    selectedBorderColor = photonColors.photonGlow
                                )
                            )
                            allEqPresets.forEach { preset ->
                                val selected = activeTrack.preferredEqPreset.equals(preset.name, ignoreCase = true)
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onUpdateTrackSoundProfile(activeTrack, activeTrack.customGainDb, preset.name)
                                    },
                                    label = { Text(preset.name, style = MaterialTheme.typography.labelSmall) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = photonColors.nebulaAccent.copy(alpha = 0.22f),
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

                        if (hasTrackOverride) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = { onUpdateTrackSoundProfile(activeTrack, 0f, null) },
                                    modifier = Modifier.testTag("studio_track_profile_reset_btn")
                                ) {
                                    Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Reset Track Profile")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
