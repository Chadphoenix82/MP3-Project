package com.example.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.SortDirection
import com.example.data.model.SortOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.photonDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "photon_audio_settings"
)

data class AppSettings(
    val darkMode: Boolean = true,
    val crossfadeSeconds: Int = 4,
    val playbackSpeed: Float = 1.0f,
    val skipSilenceEnabled: Boolean = false,
    val pauseOnAudioDisconnect: Boolean = true,
    val handleAudioFocus: Boolean = true,
    val keepScreenOn: Boolean = false,
    val gestureControlsEnabled: Boolean = true,
    val autoEnrichMetadata: Boolean = true,
    val smartQueueAutoPlay: Boolean = true,
    val autoScanEnabled: Boolean = true,
    val excludedFileTypes: Set<String> = emptySet(),
    val githubUpdateRepo: String = "photonaudio/photon-audio",
    val checkUpdatesOnLaunch: Boolean = true,
    val dismissedUpdateTag: String = "",
    val sortOption: SortOption = SortOption.CUSTOM_ORDER,
    val sortDirection: SortDirection = SortDirection.ASC,
    val eqEnabled: Boolean = true,
    val eqPresetName: String = "Photon Boost",
    val eqBandRatios: List<Float> = listOf(0.55f, 0.22f, -0.05f, 0.38f, 0.62f),
    val bassBoostStrength: Int = 380,
    val virtualizerStrength: Int = 320
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val CROSSFADE_SECONDS = intPreferencesKey("crossfade_seconds")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val SKIP_SILENCE = booleanPreferencesKey("skip_silence")
        val PAUSE_ON_AUDIO_DISCONNECT = booleanPreferencesKey("pause_on_audio_disconnect")
        val HANDLE_AUDIO_FOCUS = booleanPreferencesKey("handle_audio_focus")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val GESTURE_CONTROLS_ENABLED = booleanPreferencesKey("gesture_controls_enabled")
        val AUTO_ENRICH_METADATA = booleanPreferencesKey("auto_enrich_metadata")
        val SMART_QUEUE_AUTO_PLAY = booleanPreferencesKey("smart_queue_auto_play")
        val AUTO_SCAN_ENABLED = booleanPreferencesKey("auto_scan_enabled")
        val EXCLUDED_FILE_TYPES = stringPreferencesKey("excluded_file_types")
        val GITHUB_UPDATE_REPO = stringPreferencesKey("github_update_repo")
        val CHECK_UPDATES_ON_LAUNCH = booleanPreferencesKey("check_updates_on_launch")
        val DISMISSED_UPDATE_TAG = stringPreferencesKey("dismissed_update_tag")
        val SORT_OPTION = stringPreferencesKey("sort_option")
        val SORT_DIRECTION = stringPreferencesKey("sort_direction")
        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val EQ_PRESET_NAME = stringPreferencesKey("eq_preset_name")
        val EQ_BAND_RATIOS = stringPreferencesKey("eq_band_ratios")
        val BASS_BOOST = intPreferencesKey("bass_boost")
        val VIRTUALIZER = intPreferencesKey("virtualizer")
    }

    val settingsFlow: Flow<AppSettings> = context.photonDataStore.data.map { prefs ->
        val parsedRatios = prefs[Keys.EQ_BAND_RATIOS]
            ?.split(",")
            ?.mapNotNull { it.toFloatOrNull()?.coerceIn(-1f, 1f) }
            ?.takeIf { it.size == 5 }
            ?: listOf(0.55f, 0.22f, -0.05f, 0.38f, 0.62f)

        val excludedTypes = prefs[Keys.EXCLUDED_FILE_TYPES]
            ?.split(",")
            ?.map { it.trim().uppercase() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()

        val sortOption = runCatching {
            SortOption.valueOf(prefs[Keys.SORT_OPTION] ?: SortOption.CUSTOM_ORDER.name)
        }.getOrDefault(SortOption.CUSTOM_ORDER)

        val sortDirection = runCatching {
            SortDirection.valueOf(prefs[Keys.SORT_DIRECTION] ?: SortDirection.ASC.name)
        }.getOrDefault(SortDirection.ASC)

        AppSettings(
            darkMode = prefs[Keys.DARK_MODE] ?: true,
            crossfadeSeconds = (prefs[Keys.CROSSFADE_SECONDS] ?: 4).coerceIn(0, 12),
            playbackSpeed = (prefs[Keys.PLAYBACK_SPEED] ?: 1.0f).coerceIn(0.5f, 2.0f),
            skipSilenceEnabled = prefs[Keys.SKIP_SILENCE] ?: false,
            pauseOnAudioDisconnect = prefs[Keys.PAUSE_ON_AUDIO_DISCONNECT] ?: true,
            handleAudioFocus = prefs[Keys.HANDLE_AUDIO_FOCUS] ?: true,
            keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: false,
            gestureControlsEnabled = prefs[Keys.GESTURE_CONTROLS_ENABLED] ?: true,
            autoEnrichMetadata = prefs[Keys.AUTO_ENRICH_METADATA] ?: true,
            smartQueueAutoPlay = prefs[Keys.SMART_QUEUE_AUTO_PLAY] ?: true,
            autoScanEnabled = prefs[Keys.AUTO_SCAN_ENABLED] ?: true,
            excludedFileTypes = excludedTypes,
            githubUpdateRepo = prefs[Keys.GITHUB_UPDATE_REPO]?.takeIf { it.isNotBlank() } ?: "photonaudio/photon-audio",
            checkUpdatesOnLaunch = prefs[Keys.CHECK_UPDATES_ON_LAUNCH] ?: true,
            dismissedUpdateTag = prefs[Keys.DISMISSED_UPDATE_TAG].orEmpty(),
            sortOption = sortOption,
            sortDirection = sortDirection,
            eqEnabled = prefs[Keys.EQ_ENABLED] ?: true,
            eqPresetName = prefs[Keys.EQ_PRESET_NAME] ?: "Photon Boost",
            eqBandRatios = parsedRatios,
            bassBoostStrength = (prefs[Keys.BASS_BOOST] ?: 380).coerceIn(0, 1000),
            virtualizerStrength = (prefs[Keys.VIRTUALIZER] ?: 320).coerceIn(0, 1000)
        )
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.photonDataStore.edit { it[Keys.DARK_MODE] = enabled }
    }

    suspend fun setCrossfadeSeconds(seconds: Int) {
        context.photonDataStore.edit { it[Keys.CROSSFADE_SECONDS] = seconds.coerceIn(0, 12) }
    }

    suspend fun setPlaybackSpeed(speed: Float) {
        context.photonDataStore.edit { it[Keys.PLAYBACK_SPEED] = speed.coerceIn(0.5f, 2.0f) }
    }

    suspend fun setSkipSilenceEnabled(enabled: Boolean) {
        context.photonDataStore.edit { it[Keys.SKIP_SILENCE] = enabled }
    }

    suspend fun setPauseOnAudioDisconnect(enabled: Boolean) {
        context.photonDataStore.edit { it[Keys.PAUSE_ON_AUDIO_DISCONNECT] = enabled }
    }

    suspend fun setHandleAudioFocus(enabled: Boolean) {
        context.photonDataStore.edit { it[Keys.HANDLE_AUDIO_FOCUS] = enabled }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        context.photonDataStore.edit { it[Keys.KEEP_SCREEN_ON] = enabled }
    }

    suspend fun setGestureControlsEnabled(enabled: Boolean) {
        context.photonDataStore.edit { it[Keys.GESTURE_CONTROLS_ENABLED] = enabled }
    }

    suspend fun setAutoEnrichMetadata(enabled: Boolean) {
        context.photonDataStore.edit { it[Keys.AUTO_ENRICH_METADATA] = enabled }
    }

    suspend fun setSmartQueueAutoPlay(enabled: Boolean) {
        context.photonDataStore.edit { it[Keys.SMART_QUEUE_AUTO_PLAY] = enabled }
    }

    suspend fun setAutoScanEnabled(enabled: Boolean) {
        context.photonDataStore.edit { it[Keys.AUTO_SCAN_ENABLED] = enabled }
    }

    suspend fun setGithubUpdateRepo(repo: String) {
        val cleaned = repo.trim().removePrefix("https://github.com/").trim('/')
        if (cleaned.contains("/")) {
            context.photonDataStore.edit {
                it[Keys.GITHUB_UPDATE_REPO] = cleaned
                it[Keys.DISMISSED_UPDATE_TAG] = ""
            }
        }
    }

    suspend fun setCheckUpdatesOnLaunch(enabled: Boolean) {
        context.photonDataStore.edit { it[Keys.CHECK_UPDATES_ON_LAUNCH] = enabled }
    }

    suspend fun setDismissedUpdateTag(tag: String) {
        context.photonDataStore.edit { it[Keys.DISMISSED_UPDATE_TAG] = tag }
    }

    suspend fun toggleExcludedFileType(fileType: String) {
        val normalized = fileType.trim().uppercase()
        if (normalized.isEmpty()) return
        context.photonDataStore.edit { prefs ->
            val current = prefs[Keys.EXCLUDED_FILE_TYPES]
                ?.split(",")
                ?.map { it.trim().uppercase() }
                ?.filter { it.isNotEmpty() }
                ?.toMutableSet()
                ?: mutableSetOf()
            if (!current.add(normalized)) {
                current.remove(normalized)
            }
            prefs[Keys.EXCLUDED_FILE_TYPES] = current.sorted().joinToString(",")
        }
    }

    suspend fun setSort(option: SortOption, direction: SortDirection) {
        context.photonDataStore.edit {
            it[Keys.SORT_OPTION] = option.name
            it[Keys.SORT_DIRECTION] = direction.name
        }
    }

    suspend fun setEqEnabled(enabled: Boolean) {
        context.photonDataStore.edit { it[Keys.EQ_ENABLED] = enabled }
    }

    suspend fun setEqBands(presetName: String, bandRatios: List<Float>) {
        val serialized = bandRatios.take(5).joinToString(",") { "%.3f".format(it.coerceIn(-1f, 1f)) }
        context.photonDataStore.edit {
            it[Keys.EQ_PRESET_NAME] = presetName
            it[Keys.EQ_BAND_RATIOS] = serialized
        }
    }

    suspend fun setSpatialEffects(bassBoost: Int, virtualizer: Int) {
        context.photonDataStore.edit {
            it[Keys.BASS_BOOST] = bassBoost.coerceIn(0, 1000)
            it[Keys.VIRTUALIZER] = virtualizer.coerceIn(0, 1000)
        }
    }

    suspend fun resetToDefaults() {
        context.photonDataStore.edit { it.clear() }
    }
}
