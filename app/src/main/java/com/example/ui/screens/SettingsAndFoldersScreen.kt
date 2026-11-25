package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.data.db.ExcludedFolderEntity
import com.example.data.db.TrackEntity
import com.example.data.db.WatchedFolderEntity
import com.example.data.library.FolderMonitorStatus
import com.example.data.model.CrossfadeCurveShape
import com.example.data.model.SleepTimerState
import com.example.data.model.WidgetThemeMode
import com.example.data.prefs.AppSettings
import com.example.ui.components.AppearanceCustomizationCard
import com.example.ui.components.WidgetStudioCard
import com.example.ui.theme.AppFontChoice
import com.example.ui.theme.AppWallpaperPreset
import com.example.ui.theme.CosmicThemeSkin
import com.example.ui.theme.DeckStylePreset
import com.example.ui.theme.LocalPhotonColors
import kotlin.math.abs
import kotlin.math.roundToInt

private val SUPPORTED_FILE_TYPE_FILTERS = listOf("MP3", "FLAC", "WAV", "OPUS", "M4A", "OGG", "AAC")
private val QUICK_EXCLUDE_FOLDER_NAMES = listOf("Ringtones", "Notifications", "Alarms", "Voice Notes", "WhatsApp Audio")
private val PLAYBACK_SPEED_OPTIONS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
private val MIN_DURATION_OPTIONS_SEC = listOf(0, 15, 30, 60, 90)

@Composable
fun SettingsAndFoldersScreen(
    settings: AppSettings,
    sleepTimer: SleepTimerState,
    currentTrack: TrackEntity?,
    isPlaying: Boolean,
    shuffleEnabled: Boolean,
    watchedFolders: List<WatchedFolderEntity>,
    excludedFolders: List<ExcludedFolderEntity>,
    monitorStatus: FolderMonitorStatus,
    isApiUnlocked: Boolean,
    isCheckingUpdate: Boolean,
    updateCheckStatus: String?,
    onRequestUnlockApi: () -> Unit,
    onLockApi: () -> Unit,
    onRequestChangeApiPassword: () -> Unit,
    onSetDarkMode: (Boolean) -> Unit,
    onSelectCosmicSkin: (CosmicThemeSkin) -> Unit,
    onSetCustomThemeColors: (Long, Long, Boolean) -> Unit,
    onSetDynamicAlbumColors: (Boolean) -> Unit,
    onSelectAppFont: (AppFontChoice) -> Unit,
    onSelectWallpaperPreset: (AppWallpaperPreset) -> Unit,
    onPickCustomWallpaper: () -> Unit,
    onSetWallpaperDimAlpha: (Float) -> Unit,
    onSetWidgetThemeMode: (WidgetThemeMode) -> Unit,
    onSetWidgetGlassOpacity: (Float) -> Unit,
    onWidgetPinnedFeedback: (String) -> Unit,
    onSetCrossfadeSeconds: (Int) -> Unit,
    onSetCrossfadeCurve: (CrossfadeCurveShape) -> Unit,
    onSetPlaybackSpeed: (Float) -> Unit,
    onSetSkipSilenceEnabled: (Boolean) -> Unit,
    onSetPauseOnAudioDisconnect: (Boolean) -> Unit,
    onSetHandleAudioFocus: (Boolean) -> Unit,
    onSetKeepScreenOn: (Boolean) -> Unit,
    onSetGestureControlsEnabled: (Boolean) -> Unit,
    onSetAutoEnrichMetadata: (Boolean) -> Unit,
    onSetAutoScanEnabled: (Boolean) -> Unit,
    onToggleExcludedFileType: (String) -> Unit,
    onStartSleepTimerMinutes: (Int) -> Unit,
    onStartSleepTimerEndOfTrack: () -> Unit,
    onCancelSleepTimer: () -> Unit,
    onAddWatchedFolder: () -> Unit,
    onRemoveWatchedFolder: (String) -> Unit,
    onPickExcludedFolder: () -> Unit,
    onAddExcludedFolderName: (String) -> Unit,
    onRemoveExcludedFolder: (String) -> Unit,
    onScanFoldersNow: () -> Unit,
    onDropTestWavIntoWatchedFolder: () -> Unit,
    onSaveGithubRepo: (String) -> Unit,
    onSetCheckUpdatesOnLaunch: (Boolean) -> Unit,
    onCheckUpdatesNow: () -> Unit,
    onClearListeningHistory: () -> Unit,
    onResetSettingsToDefaults: () -> Unit,
    onApplyDeckStylePreset: (DeckStylePreset) -> Unit = {},
    onImportAudio: () -> Unit = {},
    onSetMinTrackDurationSec: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val photonColors = LocalPhotonColors.current
    var customExcludeFolderInput by remember { mutableStateOf("") }
    var githubRepoInput by remember(settings.githubUpdateRepo) { mutableStateOf(settings.githubUpdateRepo) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 156.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "settings_title") {
            Column {
                Text(
                    text = "SETTINGS & SCANNER",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Theme · Font · Wallpaper · Playback · Scanner · Protected API",
                    style = MaterialTheme.typography.labelMedium,
                    color = photonColors.photonGlow
                )
            }
        }

        // Appearance, Custom Theme, Font & Wallpaper Studio
        item(key = "appearance_studio_card") {
            AppearanceCustomizationCard(
                settings = settings,
                onApplyDeckStylePreset = onApplyDeckStylePreset,
                onSelectCosmicSkin = onSelectCosmicSkin,
                onSetCustomThemeColors = onSetCustomThemeColors,
                onSetDynamicAlbumColors = onSetDynamicAlbumColors,
                onSelectAppFont = onSelectAppFont,
                onSelectWallpaperPreset = onSelectWallpaperPreset,
                onPickCustomWallpaper = onPickCustomWallpaper,
                onSetWallpaperDimAlpha = onSetWallpaperDimAlpha
            )
        }

        // Home Screen Widget Studio (2x1, 4x2, 4x3+ previews + Pin to Home)
        item(key = "widget_studio_item") {
            WidgetStudioCard(
                settings = settings,
                currentTrack = currentTrack,
                isPlaying = isPlaying,
                shuffleEnabled = shuffleEnabled,
                onSetWidgetThemeMode = onSetWidgetThemeMode,
                onSetWidgetGlassOpacity = onSetWidgetGlassOpacity,
                onWidgetPinnedFeedback = onWidgetPinnedFeedback
            )
        }

        // 0. Commonly Used Playback & Audio Settings Card
        item(key = "common_playback_settings_card") {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = photonColors.glassSurface,
                border = BorderStroke(1.dp, photonColors.photonGlow.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Rounded.Tune, contentDescription = null, tint = photonColors.photonGlow)
                        Column {
                            Text(
                                text = "Playback & Audio Settings",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Speed, headphone disconnect, audio focus, gestures & display",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Playback Speed Chips
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Speed,
                                contentDescription = null,
                                tint = photonColors.photonGlow,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "PLAYBACK SPEED (${String.format("%.2gx", settings.playbackSpeed)})",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = photonColors.photonGlow
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PLAYBACK_SPEED_OPTIONS.forEach { spd ->
                                val selected = abs(settings.playbackSpeed - spd) < 0.02f
                                val label = if (spd == 1.0f) "1.0x" else "${spd}x"
                                FilterChip(
                                    selected = selected,
                                    onClick = { onSetPlaybackSpeed(spd) },
                                    label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                                    modifier = Modifier.testTag("playback_speed_${label.replace(".", "_")}"),
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

                    HorizontalDivider(color = photonColors.cardBorder.copy(alpha = 0.5f))

                    // Pause on Headphone / Bluetooth Disconnect
                    SettingsToggleRow(
                        icon = Icons.Rounded.Headphones,
                        title = "Pause When Headphones Disconnect",
                        subtitle = "Automatically pauses playback if wired or Bluetooth audio unplugs",
                        checked = settings.pauseOnAudioDisconnect,
                        onCheckedChange = onSetPauseOnAudioDisconnect,
                        tag = "setting_pause_on_disconnect_switch"
                    )

                    // Audio Focus & Ducking
                    SettingsToggleRow(
                        icon = Icons.Rounded.GraphicEq,
                        title = "Audio Focus & Auto-Ducking",
                        subtitle = "Pause or lower volume during calls and navigation prompts",
                        checked = settings.handleAudioFocus,
                        onCheckedChange = onSetHandleAudioFocus,
                        tag = "setting_audio_focus_switch"
                    )

                    // Skip Silence
                    SettingsToggleRow(
                        icon = Icons.Rounded.Waves,
                        title = "Skip Silence",
                        subtitle = "Trims silent gaps automatically during audio playback",
                        checked = settings.skipSilenceEnabled,
                        onCheckedChange = onSetSkipSilenceEnabled,
                        tag = "setting_skip_silence_switch"
                    )

                    // Touch Gesture Controls
                    SettingsToggleRow(
                        icon = Icons.Rounded.Swipe,
                        title = "Now Playing Touch Gestures",
                        subtitle = "Swipe left/right to skip tracks and up/down to adjust volume",
                        checked = settings.gestureControlsEnabled,
                        onCheckedChange = onSetGestureControlsEnabled,
                        tag = "setting_gestures_switch"
                    )

                    // Keep Screen On
                    SettingsToggleRow(
                        icon = Icons.Rounded.ScreenLockPortrait,
                        title = "Keep Screen On",
                        subtitle = "Prevents screen timeout while Photon Audio is open",
                        checked = settings.keepScreenOn,
                        onCheckedChange = onSetKeepScreenOn,
                        tag = "setting_keep_screen_on_switch"
                    )
                }
            }
        }

        // 1. Auto-Detect Music Scanner & Monitored Folders
        item(key = "folder_monitor_card") {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = photonColors.glassSurface,
                border = BorderStroke(1.1.dp, photonColors.photonGlow.copy(alpha = 0.55f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (monitorStatus.isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.4.dp,
                                color = photonColors.photonGlow
                            )
                        } else {
                            Icon(Icons.Rounded.Radar, contentDescription = null, tint = photonColors.losslessBadge)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-Detect Music Scanner",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = monitorStatus.lastEventMessage,
                                style = MaterialTheme.typography.labelSmall,
                                color = photonColors.photonGlow,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Auto-Scan Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-Detect New Audio Files",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "Continuously monitors watched folders for new music files",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.autoScanEnabled,
                            onCheckedChange = onSetAutoScanEnabled,
                            modifier = Modifier.testTag("auto_scan_switch"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = photonColors.photonGlow
                            )
                        )
                    }

                    // Primary Scan & Watch Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onScanFoldersNow,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("settings_scan_now_btn"),
                            shape = RoundedCornerShape(13.dp)
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scan Now")
                        }

                        OutlinedButton(
                            onClick = onAddWatchedFolder,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("settings_add_folder_btn"),
                            shape = RoundedCornerShape(13.dp)
                        ) {
                            Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Watch Folder")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onImportAudio,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("settings_import_audio_btn"),
                            shape = RoundedCornerShape(13.dp),
                            border = BorderStroke(1.dp, photonColors.photonGlow.copy(alpha = 0.6f))
                        ) {
                            Icon(
                                Icons.Rounded.Add,
                                contentDescription = null,
                                tint = photonColors.photonGlow,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import Audio", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = onDropTestWavIntoWatchedFolder,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("drop_test_wav_btn"),
                            shape = RoundedCornerShape(13.dp)
                        ) {
                            Icon(
                                Icons.Rounded.GraphicEq,
                                contentDescription = null,
                                tint = photonColors.nebulaAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test WAV", maxLines = 1)
                        }
                    }

                    // Active Watched Folders List
                    watchedFolders.forEach { folder ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(0.75.dp, photonColors.cardBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.Folder,
                                    contentDescription = null,
                                    tint = if (folder.isAppInternal) photonColors.photonGlow else photonColors.goldBadge
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = folder.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${folder.trackCount} track(s) · Active watched folder",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (!folder.isAppInternal) {
                                    IconButton(onClick = { onRemoveWatchedFolder(folder.uri) }) {
                                        Icon(
                                            Icons.Rounded.DeleteOutline,
                                            contentDescription = "Stop watching ${folder.displayName}",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Exclusions Card (Exclude File Types & Exclude Folders)
        item(key = "exclusions_card") {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = photonColors.glassSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Rounded.FolderOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Column {
                            Text(
                                text = "Scan Exclusions",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Exclude specific file types or folders from music scans",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Minimum Track Duration Filter (exclude ringtones & short notifications)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = if (settings.minTrackDurationSec <= 0) {
                                "MINIMUM TRACK DURATION FILTER (OFF)"
                            } else {
                                "HIDE SHORT CLIPS & RINGTONES (< ${settings.minTrackDurationSec}s)"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = photonColors.photonGlow
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MIN_DURATION_OPTIONS_SEC.forEach { sec ->
                                val selected = settings.minTrackDurationSec == sec
                                FilterChip(
                                    selected = selected,
                                    onClick = { onSetMinTrackDurationSec(sec) },
                                    label = {
                                        Text(
                                            text = if (sec == 0) "Off" else "${sec}s",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    },
                                    modifier = Modifier.testTag("min_duration_chip_${sec}s"),
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

                    HorizontalDivider(color = photonColors.cardBorder.copy(alpha = 0.5f))

                    // Exclude File Types chips
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "EXCLUDE FILE TYPES (TAP TO BLOCK)",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = photonColors.photonGlow
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SUPPORTED_FILE_TYPE_FILTERS.forEach { ext ->
                                val isExcluded = ext in settings.excludedFileTypes
                                FilterChip(
                                    selected = isExcluded,
                                    onClick = { onToggleExcludedFileType(ext) },
                                    label = {
                                        Text(
                                            text = if (isExcluded) "✕ .$ext" else ".$ext",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    },
                                    leadingIcon = if (isExcluded) {
                                        {
                                            Icon(
                                                Icons.Rounded.Block,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    } else null,
                                    modifier = Modifier.testTag("exclude_type_${ext.lowercase()}"),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.22f),
                                        selectedLabelColor = MaterialTheme.colorScheme.error,
                                        selectedLeadingIconColor = MaterialTheme.colorScheme.error
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isExcluded,
                                        borderColor = photonColors.cardBorder,
                                        selectedBorderColor = MaterialTheme.colorScheme.error
                                    )
                                )
                            }
                        }
                    }

                    // Exclude Folders controls (SAF picker + folder name input + quick chips)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "EXCLUDE FOLDERS",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = photonColors.photonGlow
                        )

                        OutlinedButton(
                            onClick = onPickExcludedFolder,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("pick_excluded_folder_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.FolderOff, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Pick Device Folder to Exclude")
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = customExcludeFolderInput,
                                onValueChange = { customExcludeFolderInput = it },
                                placeholder = { Text("Folder name (e.g. Podcasts)") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("exclude_folder_name_input")
                            )
                            Button(
                                onClick = {
                                    if (customExcludeFolderInput.isNotBlank()) {
                                        onAddExcludedFolderName(customExcludeFolderInput)
                                        customExcludeFolderInput = ""
                                    }
                                },
                                enabled = customExcludeFolderInput.isNotBlank(),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .height(54.dp)
                                    .testTag("add_excluded_folder_name_btn")
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = "Add excluded folder name")
                            }
                        }

                        // Quick common folder exclusion chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            QUICK_EXCLUDE_FOLDER_NAMES.forEach { presetName ->
                                val alreadyExcluded = excludedFolders.any {
                                    it.displayName.equals(presetName, ignoreCase = true)
                                }
                                if (!alreadyExcluded) {
                                    FilterChip(
                                        selected = false,
                                        onClick = { onAddExcludedFolderName(presetName) },
                                        label = {
                                            Text("+ $presetName", style = MaterialTheme.typography.labelSmall)
                                        },
                                        modifier = Modifier.testTag("quick_exclude_${presetName.lowercase().replace(" ", "_")}")
                                    )
                                }
                            }
                        }

                        // Active Excluded Folders list
                        if (excludedFolders.isEmpty()) {
                            Text(
                                text = "No folders excluded yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            excludedFolders.forEach { excluded ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Rounded.FolderOff,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = excluded.displayName,
                                                style = MaterialTheme.typography.titleSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = if (excluded.isUriTree) "Excluded device folder" else "Excluded folder name rule",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(
                                            onClick = { onRemoveExcludedFolder(excluded.identifier) },
                                            modifier = Modifier.testTag("remove_excluded_${excluded.displayName.lowercase().replace(" ", "_")}")
                                        ) {
                                            Icon(
                                                Icons.Rounded.Close,
                                                contentDescription = "Remove exclusion ${excluded.displayName}",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Gapless Playback & Crossfade Transitions Card
        item(key = "crossfade_card") {
            Surface(
                shape = RoundedCornerShape(20.dp),
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Rounded.Waves, contentDescription = null, tint = photonColors.photonGlow)
                            Column {
                                Text(
                                    text = "Gapless & Crossfade",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = if (settings.crossfadeSeconds == 0) {
                                        "True gapless seamless transition"
                                    } else {
                                        settings.crossfadeCurve.subtitle
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            text = if (settings.crossfadeSeconds == 0) "Gapless" else "${settings.crossfadeSeconds}s",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (settings.crossfadeSeconds == 0) photonColors.losslessBadge else photonColors.photonGlow
                        )
                    }

                    CrossfadeVisualCurve(
                        crossfadeSeconds = settings.crossfadeSeconds,
                        curveShape = settings.crossfadeCurve,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    )

                    Slider(
                        value = settings.crossfadeSeconds.toFloat(),
                        onValueChange = { onSetCrossfadeSeconds(it.roundToInt()) },
                        valueRange = 0f..12f,
                        steps = 11,
                        modifier = Modifier.testTag("crossfade_slider"),
                        colors = SliderDefaults.colors(
                            thumbColor = photonColors.photonGlow,
                            activeTrackColor = photonColors.photonGlow
                        )
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CrossfadeCurveShape.entries.forEach { shape ->
                            val selected = settings.crossfadeCurve == shape
                            FilterChip(
                                selected = selected,
                                onClick = { onSetCrossfadeCurve(shape) },
                                enabled = settings.crossfadeSeconds > 0,
                                label = { Text(shape.label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag("crossfade_curve_${shape.name.lowercase()}"),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = photonColors.photonGlow.copy(alpha = 0.22f),
                                    selectedLabelColor = photonColors.photonGlow
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = settings.crossfadeSeconds > 0,
                                    selected = selected,
                                    borderColor = photonColors.cardBorder,
                                    selectedBorderColor = photonColors.photonGlow
                                )
                            )
                        }
                    }
                }
            }
        }

        // 4. Sleep Timer Card
        item(key = "sleep_timer_card") {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = photonColors.glassSurface,
                border = BorderStroke(
                    1.dp,
                    if (sleepTimer.isActive) photonColors.goldBadge else photonColors.cardBorder
                )
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Bedtime,
                                contentDescription = null,
                                tint = if (sleepTimer.isActive) photonColors.goldBadge else photonColors.nebulaAccent
                            )
                            Column {
                                Text(
                                    text = "Sleep Timer",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = if (sleepTimer.isActive) "Active · ${sleepTimer.label}" else "Gentle 5s volume fade before pausing",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (sleepTimer.isActive) photonColors.goldBadge else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (sleepTimer.isActive) {
                            OutlinedButton(
                                onClick = onCancelSleepTimer,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("cancel_sleep_timer_btn")
                            ) {
                                Text("Cancel")
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(5, 15, 30, 45, 60).forEach { mins ->
                            FilterChip(
                                selected = false,
                                onClick = { onStartSleepTimerMinutes(mins) },
                                label = { Text("${mins}m", style = MaterialTheme.typography.labelMedium) },
                                modifier = Modifier.testTag("sleep_preset_${mins}m"),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                            )
                        }
                        FilterChip(
                            selected = sleepTimer.endOfTrack,
                            onClick = onStartSleepTimerEndOfTrack,
                            label = { Text("End of Track", style = MaterialTheme.typography.labelMedium) },
                            modifier = Modifier.testTag("sleep_preset_end_of_track")
                        )
                    }
                }
            }
        }

        // 5. Dark / Light Galaxy Theme
        item(key = "theme_card") {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = photonColors.glassSurface,
                border = BorderStroke(1.dp, photonColors.cardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (settings.darkMode) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                            contentDescription = null,
                            tint = photonColors.photonGlow
                        )
                        Column {
                            Text(
                                text = if (settings.darkMode) "Deep Blue Galaxy Dark Mode" else "Astral Daylight Mode",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Photon-inspired cosmic palette",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = settings.darkMode,
                        onCheckedChange = onSetDarkMode,
                        modifier = Modifier.testTag("dark_mode_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = photonColors.photonGlow
                        )
                    )
                }
            }
        }

        // 5b. Unlocked Web Metadata Lookup API Card
        item(key = "web_metadata_api_card") {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = photonColors.glassSurface,
                border = BorderStroke(1.dp, photonColors.photonGlow.copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = photonColors.photonGlow,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = "Web Metadata Lookup API",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Auto-complete album art, publisher & release date on import and in Metadata Editor",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = settings.autoEnrichMetadata,
                        onCheckedChange = onSetAutoEnrichMetadata,
                        modifier = Modifier.testTag("auto_enrich_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = photonColors.photonGlow
                        )
                    )
                }
            }
        }

        // 6. Password-Protected Hidden Release API Vault Card
        item(key = "protected_api_vault_card") {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = photonColors.glassSurface,
                border = BorderStroke(
                    1.dp,
                    if (isApiUnlocked) photonColors.photonGlow.copy(alpha = 0.55f)
                    else photonColors.goldBadge.copy(alpha = 0.55f)
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
                            Icon(
                                imageVector = if (isApiUnlocked) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = if (isApiUnlocked) photonColors.photonGlow else photonColors.goldBadge
                            )
                            Column {
                                Text(
                                    text = if (isApiUnlocked) "Protected Release API Controls (Unlocked)" else "Protected Release API Access",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = if (isApiUnlocked) {
                                        "GitHub Releases API unlocked for this session"
                                    } else {
                                        "Hidden behind password · Installed v${BuildConfig.VERSION_NAME}"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isApiUnlocked) photonColors.photonGlow else photonColors.goldBadge
                                )
                            }
                        }
                    }

                    if (!isApiUnlocked) {
                        Text(
                            text = if (settings.hasApiPassword) {
                                "GitHub release endpoints and repository settings are hidden. Enter your password to access them."
                            } else {
                                "GitHub release endpoints and repository settings are hidden by default. Create a local password to unlock them."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onRequestUnlockApi,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("settings_unlock_api_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.Password, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (settings.hasApiPassword) "Enter Password to Unlock API" else "Set Password & Unlock API")
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onLockApi,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("settings_lock_api_btn"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Lock & Hide")
                            }
                            OutlinedButton(
                                onClick = onRequestChangeApiPassword,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("settings_change_api_password_btn"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Rounded.Password, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Change Password")
                            }
                        }

                        HorizontalDivider(color = photonColors.cardBorder.copy(alpha = 0.5f))

                        // GitHub Releases API Section
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.SystemUpdate, contentDescription = null, tint = photonColors.photonGlow, modifier = Modifier.size(18.dp))
                            Text(
                                text = "GitHub Releases API",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Notify on New GitHub Release",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "Alerts you with the GitHub download link when connected to the internet",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.checkUpdatesOnLaunch,
                                onCheckedChange = onSetCheckUpdatesOnLaunch,
                                modifier = Modifier.testTag("github_auto_check_switch"),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = photonColors.photonGlow
                                )
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = githubRepoInput,
                                onValueChange = { githubRepoInput = it },
                                label = { Text("GitHub Repository (owner/repo)") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("github_repo_input")
                            )
                            OutlinedButton(
                                onClick = { onSaveGithubRepo(githubRepoInput) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .height(54.dp)
                                    .testTag("save_github_repo_btn")
                            ) {
                                Text("Save")
                            }
                        }

                        Button(
                            onClick = onCheckUpdatesNow,
                            enabled = !isCheckingUpdate,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("check_github_updates_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isCheckingUpdate) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Checking GitHub...")
                            } else {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Check GitHub for Updates Now")
                            }
                        }

                        if (!updateCheckStatus.isNullOrBlank()) {
                            Text(
                                text = updateCheckStatus,
                                style = MaterialTheme.typography.labelSmall,
                                color = photonColors.photonGlow
                            )
                        }
                    }
                }
            }
        }

        // 7. 100% On-Device Privacy & Local Data Management Card
        item(key = "privacy_and_storage_card") {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = photonColors.glassSurface,
                border = BorderStroke(1.dp, photonColors.losslessBadge.copy(alpha = 0.55f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, tint = photonColors.losslessBadge)
                        Column {
                            Text(
                                text = "100% On-Device Privacy",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Zero data collection · Zero analytics · Zero cloud accounts",
                                style = MaterialTheme.typography.labelSmall,
                                color = photonColors.losslessBadge
                            )
                        }
                    }

                    Text(
                        text = "All music files, playlists, custom album covers, edited metadata, listening history, and preferences are saved strictly on your device. Internet access is used only when you look up song metadata or check GitHub for a new release.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onClearListeningHistory,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("settings_clear_history_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clear History", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = onResetSettingsToDefaults,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("settings_reset_defaults_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reset Defaults", maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = photonColors.photonGlow,
                modifier = Modifier.size(20.dp)
            )
            Column {
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
        }
        Spacer(modifier = Modifier.width(8.dp))
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
private fun CrossfadeVisualCurve(
    crossfadeSeconds: Int,
    curveShape: CrossfadeCurveShape,
    modifier: Modifier = Modifier
) {
    val photonColors = LocalPhotonColors.current
    val cyan = photonColors.photonGlow
    val violet = photonColors.nebulaAccent
    val overlapRatio = (crossfadeSeconds / 12f).coerceIn(0f, 1f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val padY = 6.dp.toPx()
        val topY = padY
        val bottomY = h - padY
        val usableH = (bottomY - topY).coerceAtLeast(1f)
        val center = w / 2f
        val halfOverlap = (w * 0.36f) * overlapRatio

        val outStart = center - halfOverlap
        val outEnd = center + halfOverlap

        val outPath = Path().apply {
            moveTo(0f, topY)
            lineTo(outStart, topY)
            if (crossfadeSeconds > 0 && halfOverlap > 1f) {
                val steps = 28
                for (i in 1..steps) {
                    val t = i.toFloat() / steps.toFloat()
                    val (outG, _) = curveShape.computeGains(t)
                    val x = outStart + (outEnd - outStart) * t
                    val y = bottomY - usableH * outG
                    lineTo(x, y)
                }
            } else {
                lineTo(center, bottomY)
            }
            lineTo(w, bottomY)
        }
        drawPath(
            path = outPath,
            color = cyan,
            style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
        )

        val inPath = Path().apply {
            moveTo(0f, bottomY)
            lineTo(outStart, bottomY)
            if (crossfadeSeconds > 0 && halfOverlap > 1f) {
                val steps = 28
                for (i in 1..steps) {
                    val t = i.toFloat() / steps.toFloat()
                    val (_, inG) = curveShape.computeGains(t)
                    val x = outStart + (outEnd - outStart) * t
                    val y = bottomY - usableH * inG
                    lineTo(x, y)
                }
            } else {
                lineTo(center, topY)
            }
            lineTo(w, topY)
        }
        drawPath(
            path = inPath,
            color = violet,
            style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
        )

        if (crossfadeSeconds > 0) {
            val (midOut, _) = curveShape.computeGains(0.5f)
            drawCircle(
                color = photonColors.goldBadge,
                radius = 4.dp.toPx(),
                center = Offset(center, bottomY - usableH * midOut)
            )
        }
    }
}
