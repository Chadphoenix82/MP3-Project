package com.example.data.prefs

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import android.net.Uri
import androidx.datastore.preferences.core.longPreferencesKey
import com.example.data.model.CrossfadeCurveShape
import com.example.data.model.EqualizerPreset
import com.example.data.model.OutputRouteCategory
import com.example.data.model.PhotonEqPresets
import com.example.data.model.ReplayGainMode
import com.example.data.model.ReverbSpacePreset
import com.example.data.model.SortDirection
import com.example.data.model.SortOption
import com.example.data.model.StudioDspConfig
import com.example.data.model.WidgetThemeMode
import com.example.ui.theme.AppFontChoice
import com.example.ui.theme.AppWallpaperPreset
import com.example.ui.theme.CosmicThemeSkin
import com.example.ui.theme.DeckStylePreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import org.json.JSONArray
import org.json.JSONObject

private val Context.photonDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "photon_audio_settings"
)

data class AppSettings(
    val darkMode: Boolean = true,
    val cosmicThemeSkin: CosmicThemeSkin = CosmicThemeSkin.PHOTON_CYAN,
    val customAccentPrimaryArgb: Long = 0xFFFF5C8A,
    val customAccentSecondaryArgb: Long = 0xFF38E4FF,
    val customOledBlackBg: Boolean = false,
    val appFont: AppFontChoice = AppFontChoice.PUBLIC_SANS,
    val wallpaperPreset: AppWallpaperPreset = AppWallpaperPreset.COSMIC_CANVAS,
    val customWallpaperPath: String? = null,
    val wallpaperDimAlpha: Float = 0.62f,
    val dynamicAlbumColorsEnabled: Boolean = true,
    val crossfadeSeconds: Int = 4,
    val crossfadeCurve: CrossfadeCurveShape = CrossfadeCurveShape.EQUAL_POWER,
    val playbackSpeed: Float = 1.0f,
    val playbackPitch: Float = 1.0f,
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
    val eqBandRatios: List<Float> = PhotonEqPresets.default10BandRatios,
    val customEqPresets: List<EqualizerPreset> = emptyList(),
    val bassBoostStrength: Int = 380,
    val virtualizerStrength: Int = 320,
    val preAmpDb: Float = 0f,
    val softLimiterEnabled: Boolean = true,
    val loudnessNormalizerEnabled: Boolean = false,
    val replayGainMode: ReplayGainMode = ReplayGainMode.TRACK,
    val replayGainTargetLufs: Float = -14f,
    val replayGainPreAmpDb: Float = 0f,
    val replayGainFallbackPreAmpDb: Float = 0f,
    val preventClipping: Boolean = true,
    val stereoBalance: Float = 0f,
    val monoAudioEnabled: Boolean = false,
    val crossfeedStrength: Float = 0f,
    val reverbPreset: ReverbSpacePreset = ReverbSpacePreset.OFF,
    val autoEqByOutputEnabled: Boolean = false,
    val bluetoothEqPreset: String = "Photon Boost",
    val wiredEqPreset: String = "Studio FLAC",
    val speakerEqPreset: String = "Cosmic Vocal",
    val widgetThemeMode: WidgetThemeMode = WidgetThemeMode.APP_THEME,
    val widgetGlassOpacity: Float = 0.88f,
    val hasApiPassword: Boolean = false,
    val searchHistory: List<String> = emptyList(),
    val minTrackDurationSec: Int = 0,
    val gaplessPlaybackEnabled: Boolean = true,
    val deckStylePreset: DeckStylePreset? = null
) {
    fun toStudioDspConfig(trackGainOffsetDb: Float = 0f): StudioDspConfig = StudioDspConfig(
        preAmpDb = preAmpDb,
        softLimiterEnabled = softLimiterEnabled,
        loudnessNormalizerEnabled = loudnessNormalizerEnabled,
        stereoBalance = stereoBalance,
        monoAudioEnabled = monoAudioEnabled,
        crossfeedStrength = crossfeedStrength,
        reverbPreset = reverbPreset,
        trackGainOffsetDb = trackGainOffsetDb,
        replayGainMode = replayGainMode,
        replayGainTargetLufs = replayGainTargetLufs,
        replayGainPreAmpDb = replayGainPreAmpDb,
        replayGainFallbackPreAmpDb = replayGainFallbackPreAmpDb,
        preventClipping = preventClipping
    )

    fun allEqPresets(): List<EqualizerPreset> = PhotonEqPresets.builtIn + customEqPresets

    fun eqPresetForOutput(category: OutputRouteCategory): String = when (category) {
        OutputRouteCategory.BLUETOOTH -> bluetoothEqPreset
        OutputRouteCategory.WIRED_USB -> wiredEqPreset
        OutputRouteCategory.SPEAKER -> speakerEqPreset
    }
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val COSMIC_THEME_SKIN = stringPreferencesKey("cosmic_theme_skin")
        val CUSTOM_ACCENT_PRIMARY = longPreferencesKey("custom_accent_primary")
        val CUSTOM_ACCENT_SECONDARY = longPreferencesKey("custom_accent_secondary")
        val CUSTOM_OLED_BLACK = booleanPreferencesKey("custom_oled_black")
        val APP_FONT = stringPreferencesKey("app_font")
        val WALLPAPER_PRESET = stringPreferencesKey("wallpaper_preset")
        val CUSTOM_WALLPAPER_PATH = stringPreferencesKey("custom_wallpaper_path")
        val WALLPAPER_DIM_ALPHA = floatPreferencesKey("wallpaper_dim_alpha")
        val DYNAMIC_ALBUM_COLORS = booleanPreferencesKey("dynamic_album_colors")
        val CROSSFADE_SECONDS = intPreferencesKey("crossfade_seconds")
        val CROSSFADE_CURVE = stringPreferencesKey("crossfade_curve")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val PLAYBACK_PITCH = floatPreferencesKey("playback_pitch")
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
        val CUSTOM_EQ_PRESETS_JSON = stringPreferencesKey("custom_eq_presets_json")
        val BASS_BOOST = intPreferencesKey("bass_boost")
        val VIRTUALIZER = intPreferencesKey("virtualizer")
        val PRE_AMP_DB = floatPreferencesKey("pre_amp_db")
        val SOFT_LIMITER = booleanPreferencesKey("soft_limiter")
        val LOUDNESS_NORMALIZER = booleanPreferencesKey("loudness_normalizer")
        val REPLAY_GAIN_MODE = stringPreferencesKey("replay_gain_mode")
        val REPLAY_GAIN_TARGET_LUFS = floatPreferencesKey("replay_gain_target_lufs")
        val REPLAY_GAIN_PRE_AMP_DB = floatPreferencesKey("replay_gain_pre_amp_db")
        val REPLAY_GAIN_FALLBACK_PRE_AMP_DB = floatPreferencesKey("replay_gain_fallback_pre_amp_db")
        val PREVENT_CLIPPING = booleanPreferencesKey("prevent_clipping")
        val STEREO_BALANCE = floatPreferencesKey("stereo_balance")
        val MONO_AUDIO = booleanPreferencesKey("mono_audio")
        val CROSSFEED_STRENGTH = floatPreferencesKey("crossfeed_strength")
        val REVERB_PRESET = stringPreferencesKey("reverb_preset")
        val AUTO_EQ_BY_OUTPUT = booleanPreferencesKey("auto_eq_by_output")
        val BLUETOOTH_EQ_PRESET = stringPreferencesKey("bluetooth_eq_preset")
        val WIRED_EQ_PRESET = stringPreferencesKey("wired_eq_preset")
        val SPEAKER_EQ_PRESET = stringPreferencesKey("speaker_eq_preset")
        val WIDGET_THEME_MODE = stringPreferencesKey("widget_theme_mode")
        val WIDGET_GLASS_OPACITY = floatPreferencesKey("widget_glass_opacity")
        val API_PASSWORD_HASH = stringPreferencesKey("api_password_hash")
        val API_PASSWORD_SALT = stringPreferencesKey("api_password_salt")
        val SEARCH_HISTORY_JSON = stringPreferencesKey("search_history_json")
        val MIN_TRACK_DURATION_SEC = intPreferencesKey("min_track_duration_sec")
        val GAPLESS_PLAYBACK = booleanPreferencesKey("gapless_playback")
        val DECK_STYLE_PRESET = stringPreferencesKey("deck_style_preset")
    }

    val settingsFlow: Flow<AppSettings> = context.photonDataStore.data.map { prefs ->
        val rawRatios = prefs[Keys.EQ_BAND_RATIOS]
            ?.split(",")
            ?.mapNotNull { it.toFloatOrNull()?.coerceIn(-1f, 1f) }
        val parsedRatios = if (rawRatios != null && rawRatios.size >= 5) {
            PhotonEqPresets.normalizeTo10Bands(rawRatios)
        } else {
            PhotonEqPresets.default10BandRatios
        }

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

        val cosmicSkin = runCatching {
            CosmicThemeSkin.valueOf(prefs[Keys.COSMIC_THEME_SKIN] ?: CosmicThemeSkin.PHOTON_CYAN.name)
        }.getOrDefault(CosmicThemeSkin.PHOTON_CYAN)

        val appFont = runCatching {
            AppFontChoice.valueOf(prefs[Keys.APP_FONT] ?: AppFontChoice.PUBLIC_SANS.name)
        }.getOrDefault(AppFontChoice.PUBLIC_SANS)

        val wallpaperPreset = runCatching {
            AppWallpaperPreset.valueOf(prefs[Keys.WALLPAPER_PRESET] ?: AppWallpaperPreset.COSMIC_CANVAS.name)
        }.getOrDefault(AppWallpaperPreset.COSMIC_CANVAS)

        val crossfadeCurve = runCatching {
            CrossfadeCurveShape.valueOf(prefs[Keys.CROSSFADE_CURVE] ?: CrossfadeCurveShape.EQUAL_POWER.name)
        }.getOrDefault(CrossfadeCurveShape.EQUAL_POWER)

        val reverbPreset = runCatching {
            ReverbSpacePreset.valueOf(prefs[Keys.REVERB_PRESET] ?: ReverbSpacePreset.OFF.name)
        }.getOrDefault(ReverbSpacePreset.OFF)

        val widgetThemeMode = runCatching {
            WidgetThemeMode.valueOf(prefs[Keys.WIDGET_THEME_MODE] ?: WidgetThemeMode.APP_THEME.name)
        }.getOrDefault(WidgetThemeMode.APP_THEME)

        val replayGainMode = runCatching {
            ReplayGainMode.valueOf(prefs[Keys.REPLAY_GAIN_MODE] ?: ReplayGainMode.TRACK.name)
        }.getOrDefault(ReplayGainMode.TRACK)

        val deckStylePreset = prefs[Keys.DECK_STYLE_PRESET]
            ?.takeIf { it.isNotBlank() }
            ?.let { raw -> runCatching { DeckStylePreset.valueOf(raw) }.getOrNull() }

        AppSettings(
            darkMode = prefs[Keys.DARK_MODE] ?: true,
            cosmicThemeSkin = cosmicSkin,
            customAccentPrimaryArgb = prefs[Keys.CUSTOM_ACCENT_PRIMARY] ?: 0xFFFF5C8A,
            customAccentSecondaryArgb = prefs[Keys.CUSTOM_ACCENT_SECONDARY] ?: 0xFF38E4FF,
            customOledBlackBg = prefs[Keys.CUSTOM_OLED_BLACK] ?: false,
            appFont = appFont,
            wallpaperPreset = wallpaperPreset,
            customWallpaperPath = prefs[Keys.CUSTOM_WALLPAPER_PATH]?.takeIf { it.isNotBlank() },
            wallpaperDimAlpha = (prefs[Keys.WALLPAPER_DIM_ALPHA] ?: 0.62f).coerceIn(0.20f, 0.90f),
            dynamicAlbumColorsEnabled = prefs[Keys.DYNAMIC_ALBUM_COLORS] ?: true,
            crossfadeSeconds = (prefs[Keys.CROSSFADE_SECONDS] ?: 4).coerceIn(0, 12),
            crossfadeCurve = crossfadeCurve,
            playbackSpeed = (prefs[Keys.PLAYBACK_SPEED] ?: 1.0f).coerceIn(0.5f, 2.0f),
            playbackPitch = (prefs[Keys.PLAYBACK_PITCH] ?: 1.0f).coerceIn(0.5f, 1.5f),
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
            customEqPresets = parseCustomEqPresetsJson(prefs[Keys.CUSTOM_EQ_PRESETS_JSON]),
            bassBoostStrength = (prefs[Keys.BASS_BOOST] ?: 380).coerceIn(0, 1000),
            virtualizerStrength = (prefs[Keys.VIRTUALIZER] ?: 320).coerceIn(0, 1000),
            preAmpDb = (prefs[Keys.PRE_AMP_DB] ?: 0f).coerceIn(-12f, 12f),
            softLimiterEnabled = prefs[Keys.SOFT_LIMITER] ?: true,
            loudnessNormalizerEnabled = prefs[Keys.LOUDNESS_NORMALIZER] ?: false,
            replayGainMode = replayGainMode,
            replayGainTargetLufs = (prefs[Keys.REPLAY_GAIN_TARGET_LUFS] ?: -14f).coerceIn(-20f, -10f),
            replayGainPreAmpDb = (prefs[Keys.REPLAY_GAIN_PRE_AMP_DB] ?: 0f).coerceIn(-6f, 6f),
            replayGainFallbackPreAmpDb = (prefs[Keys.REPLAY_GAIN_FALLBACK_PRE_AMP_DB] ?: 0f).coerceIn(-6f, 6f),
            preventClipping = prefs[Keys.PREVENT_CLIPPING] ?: true,
            stereoBalance = (prefs[Keys.STEREO_BALANCE] ?: 0f).coerceIn(-1f, 1f),
            monoAudioEnabled = prefs[Keys.MONO_AUDIO] ?: false,
            crossfeedStrength = (prefs[Keys.CROSSFEED_STRENGTH] ?: 0f).coerceIn(0f, 1f),
            reverbPreset = reverbPreset,
            autoEqByOutputEnabled = prefs[Keys.AUTO_EQ_BY_OUTPUT] ?: false,
            bluetoothEqPreset = prefs[Keys.BLUETOOTH_EQ_PRESET]?.takeIf { it.isNotBlank() } ?: "Photon Boost",
            wiredEqPreset = prefs[Keys.WIRED_EQ_PRESET]?.takeIf { it.isNotBlank() } ?: "Studio FLAC",
            speakerEqPreset = prefs[Keys.SPEAKER_EQ_PRESET]?.takeIf { it.isNotBlank() } ?: "Cosmic Vocal",
            widgetThemeMode = widgetThemeMode,
            widgetGlassOpacity = (prefs[Keys.WIDGET_GLASS_OPACITY] ?: 0.88f).coerceIn(0.45f, 1.0f),
            hasApiPassword = !prefs[Keys.API_PASSWORD_HASH].isNullOrBlank() && !prefs[Keys.API_PASSWORD_SALT].isNullOrBlank(),
            searchHistory = parseSearchHistoryJson(prefs[Keys.SEARCH_HISTORY_JSON]),
            minTrackDurationSec = (prefs[Keys.MIN_TRACK_DURATION_SEC] ?: 0).coerceIn(0, 180),
            gaplessPlaybackEnabled = prefs[Keys.GAPLESS_PLAYBACK] ?: true,
            deckStylePreset = deckStylePreset
        )
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.photonDataStore.edit { it[Keys.DARK_MODE] = enabled }
    }

    suspend fun setCosmicThemeSkin(skin: CosmicThemeSkin) {
        context.photonDataStore.edit {
            it[Keys.COSMIC_THEME_SKIN] = skin.name
            it[Keys.DARK_MODE] = true
        }
    }

    suspend fun setCustomThemeColors(primaryArgb: Long, secondaryArgb: Long, oledBlack: Boolean) {
        context.photonDataStore.edit {
            it[Keys.COSMIC_THEME_SKIN] = CosmicThemeSkin.CUSTOM.name
            it[Keys.CUSTOM_ACCENT_PRIMARY] = primaryArgb
            it[Keys.CUSTOM_ACCENT_SECONDARY] = secondaryArgb
            it[Keys.CUSTOM_OLED_BLACK] = oledBlack
            it[Keys.DARK_MODE] = true
        }
    }

    suspend fun setAppFont(font: AppFontChoice) {
        context.photonDataStore.edit {
            it[Keys.APP_FONT] = font.name
            it.remove(Keys.DECK_STYLE_PRESET)
        }
    }

    suspend fun applyDeckStylePreset(preset: DeckStylePreset) {
        context.photonDataStore.edit {
            it[Keys.DECK_STYLE_PRESET] = preset.name
            it[Keys.APP_FONT] = preset.displayFont.name
            it[Keys.COSMIC_THEME_SKIN] = preset.recommendedSkin.name
            it[Keys.DARK_MODE] = preset.darkMode
        }
    }

    suspend fun setMinTrackDurationSec(seconds: Int) {
        context.photonDataStore.edit {
            it[Keys.MIN_TRACK_DURATION_SEC] = seconds.coerceIn(0, 180)
        }
    }

    suspend fun setGaplessPlaybackEnabled(enabled: Boolean) {
        context.photonDataStore.edit {
            it[Keys.GAPLESS_PLAYBACK] = enabled
        }
    }

    suspend fun setWallpaperPreset(preset: AppWallpaperPreset) {
        context.photonDataStore.edit { it[Keys.WALLPAPER_PRESET] = preset.name }
    }

    suspend fun setWallpaperDimAlpha(alpha: Float) {
        context.photonDataStore.edit { it[Keys.WALLPAPER_DIM_ALPHA] = alpha.coerceIn(0.20f, 0.90f) }
    }

    suspend fun saveCustomWallpaperFromUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, "wallpapers").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val outFile = File(dir, "custom_wallpaper_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null
            val path = outFile.absolutePath
            context.photonDataStore.edit {
                it[Keys.CUSTOM_WALLPAPER_PATH] = path
                it[Keys.WALLPAPER_PRESET] = AppWallpaperPreset.CUSTOM_PHOTO.name
            }
            path
        }.getOrNull()
    }

    suspend fun setDynamicAlbumColorsEnabled(enabled: Boolean) {
        context.photonDataStore.edit { it[Keys.DYNAMIC_ALBUM_COLORS] = enabled }
    }

    suspend fun setCrossfadeSeconds(seconds: Int) {
        context.photonDataStore.edit { it[Keys.CROSSFADE_SECONDS] = seconds.coerceIn(0, 12) }
    }

    suspend fun setCrossfadeCurve(curve: CrossfadeCurveShape) {
        context.photonDataStore.edit { it[Keys.CROSSFADE_CURVE] = curve.name }
    }

    suspend fun setPlaybackSpeed(speed: Float) {
        context.photonDataStore.edit { it[Keys.PLAYBACK_SPEED] = speed.coerceIn(0.5f, 2.0f) }
    }

    suspend fun setPlaybackPitch(pitch: Float) {
        context.photonDataStore.edit { it[Keys.PLAYBACK_PITCH] = pitch.coerceIn(0.5f, 1.5f) }
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
        val normalized = PhotonEqPresets.normalizeTo10Bands(bandRatios)
        val serialized = normalized.joinToString(",") { "%.3f".format(it.coerceIn(-1f, 1f)) }
        context.photonDataStore.edit {
            it[Keys.EQ_PRESET_NAME] = presetName
            it[Keys.EQ_BAND_RATIOS] = serialized
        }
    }

    suspend fun saveCustomEqPreset(preset: EqualizerPreset) {
        val cleanedName = preset.name.trim().take(28)
        if (cleanedName.isEmpty()) return
        val normalized = preset.copy(
            name = cleanedName,
            description = preset.description.ifBlank { "Custom 10-band studio curve" },
            bandRatios = PhotonEqPresets.normalizeTo10Bands(preset.bandRatios),
            isCustom = true
        )
        context.photonDataStore.edit { prefs ->
            val existing = parseCustomEqPresetsJson(prefs[Keys.CUSTOM_EQ_PRESETS_JSON])
                .filterNot { it.name.equals(cleanedName, ignoreCase = true) }
            val updated = (listOf(normalized) + existing).take(MAX_CUSTOM_PRESETS)
            prefs[Keys.CUSTOM_EQ_PRESETS_JSON] = serializeCustomEqPresetsJson(updated)
            prefs[Keys.EQ_PRESET_NAME] = cleanedName
            prefs[Keys.EQ_BAND_RATIOS] = normalized.bandRatios.joinToString(",") { "%.3f".format(it) }
            prefs[Keys.BASS_BOOST] = normalized.bassBoost.coerceIn(0, 1000)
            prefs[Keys.VIRTUALIZER] = normalized.virtualizer.coerceIn(0, 1000)
        }
    }

    suspend fun deleteCustomEqPreset(presetName: String) {
        context.photonDataStore.edit { prefs ->
            val updated = parseCustomEqPresetsJson(prefs[Keys.CUSTOM_EQ_PRESETS_JSON])
                .filterNot { it.name.equals(presetName, ignoreCase = true) }
            prefs[Keys.CUSTOM_EQ_PRESETS_JSON] = serializeCustomEqPresetsJson(updated)
            if (prefs[Keys.EQ_PRESET_NAME].equals(presetName, ignoreCase = true)) {
                prefs[Keys.EQ_PRESET_NAME] = "Custom"
            }
        }
    }

    suspend fun setSpatialEffects(bassBoost: Int, virtualizer: Int) {
        context.photonDataStore.edit {
            it[Keys.BASS_BOOST] = bassBoost.coerceIn(0, 1000)
            it[Keys.VIRTUALIZER] = virtualizer.coerceIn(0, 1000)
        }
    }

    suspend fun setPreAmpDb(db: Float) {
        context.photonDataStore.edit { it[Keys.PRE_AMP_DB] = db.coerceIn(-12f, 12f) }
    }

    suspend fun setSoftLimiterEnabled(enabled: Boolean) {
        context.photonDataStore.edit { it[Keys.SOFT_LIMITER] = enabled }
    }

    suspend fun setLoudnessNormalizerEnabled(enabled: Boolean) {
        context.photonDataStore.edit { it[Keys.LOUDNESS_NORMALIZER] = enabled }
    }

    suspend fun setReplayGainMode(mode: ReplayGainMode) {
        context.photonDataStore.edit { it[Keys.REPLAY_GAIN_MODE] = mode.name }
    }

    suspend fun setReplayGainTargetLufs(lufs: Float) {
        context.photonDataStore.edit { it[Keys.REPLAY_GAIN_TARGET_LUFS] = lufs.coerceIn(-20f, -10f) }
    }

    suspend fun setReplayGainPreAmpDb(db: Float) {
        context.photonDataStore.edit { it[Keys.REPLAY_GAIN_PRE_AMP_DB] = db.coerceIn(-6f, 6f) }
    }

    suspend fun setReplayGainFallbackPreAmpDb(db: Float) {
        context.photonDataStore.edit { it[Keys.REPLAY_GAIN_FALLBACK_PRE_AMP_DB] = db.coerceIn(-6f, 6f) }
    }

    suspend fun setPreventClipping(enabled: Boolean) {
        context.photonDataStore.edit { it[Keys.PREVENT_CLIPPING] = enabled }
    }

    suspend fun setStereoBalance(balance: Float) {
        context.photonDataStore.edit { it[Keys.STEREO_BALANCE] = balance.coerceIn(-1f, 1f) }
    }

    suspend fun setMonoAudioEnabled(enabled: Boolean) {
        context.photonDataStore.edit { it[Keys.MONO_AUDIO] = enabled }
    }

    suspend fun setCrossfeedStrength(strength: Float) {
        context.photonDataStore.edit { it[Keys.CROSSFEED_STRENGTH] = strength.coerceIn(0f, 1f) }
    }

    suspend fun setReverbPreset(preset: ReverbSpacePreset) {
        context.photonDataStore.edit { it[Keys.REVERB_PRESET] = preset.name }
    }

    suspend fun setAutoEqByOutputEnabled(enabled: Boolean) {
        context.photonDataStore.edit { it[Keys.AUTO_EQ_BY_OUTPUT] = enabled }
    }

    suspend fun setOutputRouteEqPreset(category: OutputRouteCategory, presetName: String) {
        context.photonDataStore.edit { prefs ->
            when (category) {
                OutputRouteCategory.BLUETOOTH -> prefs[Keys.BLUETOOTH_EQ_PRESET] = presetName
                OutputRouteCategory.WIRED_USB -> prefs[Keys.WIRED_EQ_PRESET] = presetName
                OutputRouteCategory.SPEAKER -> prefs[Keys.SPEAKER_EQ_PRESET] = presetName
            }
        }
    }

    suspend fun setWidgetThemeMode(mode: WidgetThemeMode) {
        context.photonDataStore.edit { it[Keys.WIDGET_THEME_MODE] = mode.name }
    }

    suspend fun setWidgetGlassOpacity(opacity: Float) {
        context.photonDataStore.edit { it[Keys.WIDGET_GLASS_OPACITY] = opacity.coerceIn(0.45f, 1.0f) }
    }

    suspend fun setApiPassword(rawPassword: String) {
        val saltBytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hashBytes = derivePasswordHash(rawPassword, saltBytes)
        val encodedSalt = Base64.encodeToString(saltBytes, Base64.NO_WRAP)
        val encodedHash = Base64.encodeToString(hashBytes, Base64.NO_WRAP)
        context.photonDataStore.edit {
            it[Keys.API_PASSWORD_SALT] = encodedSalt
            it[Keys.API_PASSWORD_HASH] = encodedHash
        }
    }

    suspend fun verifyApiPassword(candidatePassword: String): Boolean {
        val prefs = context.photonDataStore.data.first()
        val encodedSalt = prefs[Keys.API_PASSWORD_SALT] ?: return false
        val encodedHash = prefs[Keys.API_PASSWORD_HASH] ?: return false
        return runCatching {
            val saltBytes = Base64.decode(encodedSalt, Base64.NO_WRAP)
            val expectedHash = Base64.decode(encodedHash, Base64.NO_WRAP)
            val actualHash = derivePasswordHash(candidatePassword, saltBytes)
            MessageDigest.isEqual(expectedHash, actualHash)
        }.getOrDefault(false)
    }

    private fun derivePasswordHash(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, 65_536, 256)
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    suspend fun addSearchHistoryQuery(rawQuery: String) {
        val cleaned = rawQuery.trim()
        if (cleaned.isEmpty()) return
        context.photonDataStore.edit { prefs ->
            val current = parseSearchHistoryJson(prefs[Keys.SEARCH_HISTORY_JSON])
            val updated = buildList {
                add(cleaned)
                current.forEach { existing ->
                    if (!existing.equals(cleaned, ignoreCase = true) && size < MAX_SEARCH_HISTORY) {
                        add(existing)
                    }
                }
            }
            prefs[Keys.SEARCH_HISTORY_JSON] = serializeSearchHistoryJson(updated)
        }
    }

    suspend fun removeSearchHistoryQuery(query: String) {
        val target = query.trim()
        if (target.isEmpty()) return
        context.photonDataStore.edit { prefs ->
            val updated = parseSearchHistoryJson(prefs[Keys.SEARCH_HISTORY_JSON])
                .filterNot { it.equals(target, ignoreCase = true) }
            prefs[Keys.SEARCH_HISTORY_JSON] = serializeSearchHistoryJson(updated)
        }
    }

    suspend fun clearSearchHistory() {
        context.photonDataStore.edit { prefs ->
            prefs.remove(Keys.SEARCH_HISTORY_JSON)
        }
    }

    private fun parseSearchHistoryJson(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList(array.length()) {
                for (i in 0 until array.length()) {
                    val item = array.optString(i).trim()
                    if (item.isNotEmpty()) add(item)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun serializeSearchHistoryJson(items: List<String>): String {
        val array = JSONArray()
        items.forEach { array.put(it) }
        return array.toString()
    }

    private fun parseCustomEqPresetsJson(raw: String?): List<EqualizerPreset> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList(array.length()) {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val name = obj.optString("name").trim()
                    if (name.isEmpty()) continue
                    val desc = obj.optString("description", "Custom 10-band studio curve")
                    val bandsJson = obj.optJSONArray("bands")
                    val parsedBands = mutableListOf<Float>()
                    if (bandsJson != null) {
                        for (b in 0 until bandsJson.length()) {
                            parsedBands.add(bandsJson.optDouble(b, 0.0).toFloat().coerceIn(-1f, 1f))
                        }
                    }
                    add(
                        EqualizerPreset(
                            name = name,
                            description = desc,
                            bandRatios = PhotonEqPresets.normalizeTo10Bands(parsedBands),
                            bassBoost = obj.optInt("bass", 0).coerceIn(0, 1000),
                            virtualizer = obj.optInt("virt", 0).coerceIn(0, 1000),
                            isCustom = true
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun serializeCustomEqPresetsJson(presets: List<EqualizerPreset>): String {
        val array = JSONArray()
        presets.forEach { preset ->
            val obj = JSONObject()
            obj.put("name", preset.name)
            obj.put("description", preset.description)
            obj.put("bass", preset.bassBoost)
            obj.put("virt", preset.virtualizer)
            val bands = JSONArray()
            PhotonEqPresets.normalizeTo10Bands(preset.bandRatios).forEach { bands.put(it.toDouble()) }
            obj.put("bands", bands)
            array.put(obj)
        }
        return array.toString()
    }

    companion object {
        private const val MAX_SEARCH_HISTORY = 12
        private const val MAX_CUSTOM_PRESETS = 16
    }

    suspend fun resetToDefaults() {
        context.photonDataStore.edit { prefs ->
            val savedHash = prefs[Keys.API_PASSWORD_HASH]
            val savedSalt = prefs[Keys.API_PASSWORD_SALT]
            prefs.clear()
            if (!savedHash.isNullOrBlank() && !savedSalt.isNullOrBlank()) {
                prefs[Keys.API_PASSWORD_HASH] = savedHash
                prefs[Keys.API_PASSWORD_SALT] = savedSalt
            }
        }
    }
}
