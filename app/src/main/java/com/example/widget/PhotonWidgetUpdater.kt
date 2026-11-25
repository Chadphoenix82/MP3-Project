package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import androidx.glance.appwidget.updateAll
import com.example.MainActivity
import com.example.PhotonApplication
import com.example.R
import com.example.data.model.WidgetThemeMode
import com.example.data.prefs.AppSettings
import com.example.player.PhotonPlaybackBridge
import com.example.player.PlaybackUiState
import com.example.ui.theme.CosmicThemeSkin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

data class GlanceWidgetPalette(
    val bgStart: Int,
    val bgEnd: Int,
    val borderColor: Int,
    val accentPrimary: Int,
    val accentSecondary: Int,
    val onAccent: Int,
    val titleColor: Int,
    val subtitleColor: Int
)

/**
 * Debounced Jetpack Glance widget synchronizer & responsive RemoteViews fallback.
 * Debounces rapid track skipping (250ms) to prevent Binder transaction bottlenecks.
 */
object PhotonWidgetUpdater {

    const val ACTION_PLAY_PAUSE = "com.example.widget.ACTION_PLAY_PAUSE"
    const val ACTION_NEXT = "com.example.widget.ACTION_NEXT"
    const val ACTION_PREV = "com.example.widget.ACTION_PREV"
    const val ACTION_TOGGLE_FAVORITE = "com.example.widget.ACTION_TOGGLE_FAVORITE"
    const val ACTION_TOGGLE_SHUFFLE = "com.example.widget.ACTION_TOGGLE_SHUFFLE"

    private const val WIDGET_UPDATE_DEBOUNCE_MS = 250L

    private val updaterScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var pendingDebounceJob: Job? = null

    @Volatile
    private var cachedArtUri: String? = null

    @Volatile
    private var cachedRoundedArt: Bitmap? = null

    @Volatile
    private var cachedArtAccent: Int? = null

    @Volatile
    private var cachedArtSecondary: Int? = null

    private data class WidgetPalette(
        val bgStart: Int,
        val bgEnd: Int,
        val borderColor: Int,
        val accentPrimary: Int,
        val accentSecondary: Int,
        val onAccent: Int,
        val titleColor: Int,
        val subtitleColor: Int
    )

    fun loadRoundedArtworkForGlance(context: Context, artworkUri: String?): Bitmap? =
        loadRoundedArtwork(context, artworkUri)

    fun resolveGlancePalette(settings: AppSettings, hasArtwork: Boolean): GlanceWidgetPalette {
        val p = resolvePalette(settings, hasArtwork)
        return GlanceWidgetPalette(
            bgStart = p.bgStart,
            bgEnd = p.bgEnd,
            borderColor = p.borderColor,
            accentPrimary = p.accentPrimary,
            accentSecondary = p.accentSecondary,
            onAccent = p.onAccent,
            titleColor = p.titleColor,
            subtitleColor = p.subtitleColor
        )
    }

    fun buildCodecLabelForGlance(playback: PlaybackUiState): String = buildCodecLabel(playback)

    fun formatClockForGlance(ms: Long): String = formatClock(ms)

    fun requestUpdate(context: Context, specificWidgetIds: IntArray? = null) {
        val appContext = context.applicationContext
        pendingDebounceJob?.cancel()
        pendingDebounceJob = updaterScope.launch {
            delay(WIDGET_UPDATE_DEBOUNCE_MS)
            // 1. Update Jetpack Glance Widgets
            runCatching {
                PhotonGlanceWidget().updateAll(appContext)
            }
            // 2. Keep legacy RemoteViews provider updated if any pinned instances remain
            runCatching {
                val manager = AppWidgetManager.getInstance(appContext)
                val component = ComponentName(appContext, PhotonAudioWidgetProvider::class.java)
                val widgetIds = specificWidgetIds ?: manager.getAppWidgetIds(component)
                if (widgetIds.isEmpty()) return@launch

                val app = appContext as? PhotonApplication
                val settings = runCatching {
                    app?.settingsRepository?.settingsFlow?.first()
                }.getOrNull() ?: AppSettings()

                val playback = PhotonPlaybackBridge.state.value
                val artworkBitmap = loadRoundedArtwork(appContext, playback.currentTrack?.artworkUri)
                val palette = resolvePalette(settings, artworkBitmap != null)

                val compactBg = renderGlassCardBitmap(palette, settings.widgetGlassOpacity, 360, 140)
                val standardBg = renderGlassCardBitmap(palette, settings.widgetGlassOpacity, 600, 240)
                val studioBg = renderGlassCardBitmap(palette, settings.widgetGlassOpacity, 600, 440)

                val compactViews = buildCompactViews(appContext, playback, palette, artworkBitmap, compactBg)
                val standardViews = buildStandardViews(appContext, playback, palette, artworkBitmap, standardBg)
                val studioViews = buildStudioViews(appContext, playback, palette, artworkBitmap, studioBg)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val sizeMapping = mapOf(
                        SizeF(110f, 56f) to compactViews,
                        SizeF(230f, 56f) to standardViews,
                        SizeF(230f, 185f) to studioViews
                    )
                    val responsiveViews = RemoteViews(sizeMapping)
                    for (id in widgetIds) {
                        manager.updateAppWidget(id, responsiveViews)
                    }
                } else {
                    for (id in widgetIds) {
                        val options: Bundle = manager.getAppWidgetOptions(id)
                        val minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
                        val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
                        val chosen = when {
                            minW < 220 && minH < 140 -> compactViews
                            minH >= 180 -> studioViews
                            else -> standardViews
                        }
                        manager.updateAppWidget(id, chosen)
                    }
                }
            }
        }
    }

    private fun buildCompactViews(
        context: Context,
        playback: PlaybackUiState,
        palette: WidgetPalette,
        artwork: Bitmap?,
        bgBitmap: Bitmap
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_photon_compact)
        bindCommonViews(context, views, playback, palette, artwork, bgBitmap)
        return views
    }

    private fun buildStandardViews(
        context: Context,
        playback: PlaybackUiState,
        palette: WidgetPalette,
        artwork: Bitmap?,
        bgBitmap: Bitmap
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_photon_standard)
        bindCommonViews(context, views, playback, palette, artwork, bgBitmap)
        views.setTextViewText(R.id.widget_codec_badge, buildCodecLabel(playback))
        views.setTextColor(R.id.widget_codec_badge, palette.accentPrimary)

        val progress = computeProgressPermille(playback)
        views.setProgressBar(R.id.widget_progress, 1000, progress, false)

        views.setInt(R.id.widget_btn_prev, "setColorFilter", palette.titleColor)
        views.setInt(R.id.widget_btn_next, "setColorFilter", palette.titleColor)
        views.setOnClickPendingIntent(R.id.widget_btn_prev, actionPendingIntent(context, ACTION_PREV, 202))
        views.setOnClickPendingIntent(R.id.widget_btn_next, actionPendingIntent(context, ACTION_NEXT, 203))
        return views
    }

    private fun buildStudioViews(
        context: Context,
        playback: PlaybackUiState,
        palette: WidgetPalette,
        artwork: Bitmap?,
        bgBitmap: Bitmap
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_photon_studio)
        bindCommonViews(context, views, playback, palette, artwork, bgBitmap)

        val track = playback.currentTrack
        views.setTextColor(R.id.widget_brand_label, palette.accentPrimary)
        views.setTextViewText(R.id.widget_codec_badge, buildCodecLabel(playback))
        views.setTextColor(R.id.widget_codec_badge, palette.accentPrimary)

        val albumText = track?.album?.takeIf { it.isNotBlank() && !it.equals("Unknown Album", ignoreCase = true) }.orEmpty()
        views.setTextViewText(R.id.widget_album, albumText)
        views.setViewVisibility(R.id.widget_album, if (albumText.isNotEmpty()) View.VISIBLE else View.GONE)
        views.setTextColor(R.id.widget_album, palette.subtitleColor)

        val progress = computeProgressPermille(playback)
        views.setProgressBar(R.id.widget_progress, 1000, progress, false)
        views.setTextViewText(R.id.widget_elapsed, formatClock(playback.positionMs))
        views.setTextViewText(R.id.widget_duration, formatClock(playback.durationMs))
        views.setTextColor(R.id.widget_elapsed, palette.subtitleColor)
        views.setTextColor(R.id.widget_duration, palette.subtitleColor)

        views.setInt(R.id.widget_btn_prev, "setColorFilter", palette.titleColor)
        views.setInt(R.id.widget_btn_next, "setColorFilter", palette.titleColor)
        views.setOnClickPendingIntent(R.id.widget_btn_prev, actionPendingIntent(context, ACTION_PREV, 302))
        views.setOnClickPendingIntent(R.id.widget_btn_next, actionPendingIntent(context, ACTION_NEXT, 303))

        val isFav = track?.isFavorite == true
        views.setImageViewResource(
            R.id.widget_btn_favorite,
            if (isFav) R.drawable.ic_widget_fav_filled else R.drawable.ic_widget_fav_outline
        )
        views.setInt(
            R.id.widget_btn_favorite,
            "setColorFilter",
            if (isFav) Color.parseColor("#FFFF5C8A") else palette.subtitleColor
        )
        views.setOnClickPendingIntent(R.id.widget_btn_favorite, actionPendingIntent(context, ACTION_TOGGLE_FAVORITE, 304))

        views.setInt(
            R.id.widget_btn_shuffle,
            "setColorFilter",
            if (playback.shuffleEnabled) palette.accentPrimary else palette.subtitleColor
        )
        views.setOnClickPendingIntent(R.id.widget_btn_shuffle, actionPendingIntent(context, ACTION_TOGGLE_SHUFFLE, 305))
        return views
    }

    private fun bindCommonViews(
        context: Context,
        views: RemoteViews,
        playback: PlaybackUiState,
        palette: WidgetPalette,
        artwork: Bitmap?,
        bgBitmap: Bitmap
    ) {
        val track = playback.currentTrack
        views.setImageViewBitmap(R.id.widget_bg_image, bgBitmap)

        if (artwork != null) {
            views.setImageViewBitmap(R.id.widget_artwork, artwork)
            views.setInt(R.id.widget_artwork, "setColorFilter", 0)
        } else {
            views.setImageViewResource(R.id.widget_artwork, R.drawable.ic_widget_music_note)
            views.setInt(R.id.widget_artwork, "setColorFilter", palette.accentPrimary)
        }

        views.setTextViewText(R.id.widget_title, track?.title ?: context.getString(R.string.widget_empty_title))
        views.setTextColor(R.id.widget_title, palette.titleColor)
        views.setTextViewText(R.id.widget_artist, track?.artist ?: context.getString(R.string.widget_empty_subtitle))
        views.setTextColor(R.id.widget_artist, palette.subtitleColor)

        views.setInt(R.id.widget_play_bg, "setColorFilter", palette.accentPrimary)
        views.setImageViewResource(
            R.id.widget_play_icon,
            if (playback.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
        )
        views.setInt(R.id.widget_play_icon, "setColorFilter", palette.onAccent)

        val openAppIntent = openAppPendingIntent(context)
        views.setOnClickPendingIntent(R.id.widget_artwork, openAppIntent)
        views.setOnClickPendingIntent(R.id.widget_info_container, openAppIntent)
        views.setOnClickPendingIntent(R.id.widget_btn_play_pause, actionPendingIntent(context, ACTION_PLAY_PAUSE, 101))
    }

    private fun openAppPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            900,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun actionPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, PhotonAudioWidgetProvider::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun resolvePalette(settings: AppSettings, hasArtwork: Boolean): WidgetPalette {
        val (themePrimary, themeSecondary, themeBgTop, themeBgBottom) = when {
            !settings.darkMode -> QuadColors(
                Color.parseColor("#007BA7"),
                Color.parseColor("#5B42D6"),
                Color.parseColor("#F1F6FF"),
                Color.parseColor("#DCEAFF")
            )
            settings.cosmicThemeSkin == CosmicThemeSkin.CUSTOM -> {
                val p = settings.customAccentPrimaryArgb.toInt()
                val s = settings.customAccentSecondaryArgb.toInt()
                val top = if (settings.customOledBlackBg) Color.BLACK else blendColors(Color.parseColor("#060A18"), p, 0.16f)
                val bot = if (settings.customOledBlackBg) Color.parseColor("#0A0D16") else blendColors(Color.parseColor("#0B142C"), s, 0.20f)
                QuadColors(p, s, top, bot)
            }
            settings.cosmicThemeSkin == CosmicThemeSkin.OLED_PURE_BLACK -> QuadColors(
                settings.cosmicThemeSkin.swatchPrimary.toArgb(),
                settings.cosmicThemeSkin.swatchSecondary.toArgb(),
                Color.BLACK,
                Color.parseColor("#0B0F1A")
            )
            else -> {
                val p = settings.cosmicThemeSkin.swatchPrimary.toArgb()
                val s = settings.cosmicThemeSkin.swatchSecondary.toArgb()
                val base = settings.cosmicThemeSkin.swatchBackdrop.toArgb()
                QuadColors(p, s, base, blendColors(base, p, 0.18f))
            }
        }

        val useArt = settings.widgetThemeMode == WidgetThemeMode.ADAPTIVE_ARTWORK &&
            hasArtwork && cachedArtAccent != null

        val primary = if (useArt) cachedArtAccent!! else themePrimary
        val secondary = if (useArt) (cachedArtSecondary ?: themeSecondary) else themeSecondary
        val isLight = !settings.darkMode && !useArt

        val bgStart = if (useArt) blendColors(Color.parseColor("#070B16"), primary, 0.22f) else themeBgTop
        val bgEnd = if (useArt) blendColors(Color.parseColor("#0A1024"), secondary, 0.24f) else themeBgBottom

        val titleColor = if (isLight) Color.parseColor("#0E1A33") else Color.parseColor("#F5F9FF")
        val subtitleColor = if (isLight) Color.parseColor("#4B5F86") else Color.parseColor("#C2D8F2")
        val onAccent = if (colorLuminance(primary) > 0.56f) Color.parseColor("#050A18") else Color.WHITE
        val borderColor = setColorAlpha(primary, if (isLight) 95 else 115)

        return WidgetPalette(
            bgStart = bgStart,
            bgEnd = bgEnd,
            borderColor = borderColor,
            accentPrimary = primary,
            accentSecondary = secondary,
            onAccent = onAccent,
            titleColor = titleColor,
            subtitleColor = subtitleColor
        )
    }

    private data class QuadColors(val a: Int, val b: Int, val c: Int, val d: Int)

    private fun renderGlassCardBitmap(
        palette: WidgetPalette,
        opacity: Float,
        width: Int,
        height: Int
    ): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val rect = RectF(2f, 2f, width - 2f, height - 2f)
        val corner = 38f
        val alphaInt = (opacity.coerceIn(0.45f, 1.0f) * 255f).roundToInt().coerceIn(115, 255)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(
                    setColorAlpha(palette.bgStart, alphaInt),
                    setColorAlpha(palette.bgEnd, alphaInt)
                ),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(rect, corner, corner, bgPaint)

        // Subtle luminous accent glow wash at top-right
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                width * 0.45f, 0f, width.toFloat(), height * 0.6f,
                intArrayOf(
                    Color.TRANSPARENT,
                    setColorAlpha(palette.accentPrimary, 46)
                ),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(rect, corner, corner, glowPaint)

        // Crisp border stroke
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.6f
            color = palette.borderColor
        }
        canvas.drawRoundRect(rect, corner, corner, borderPaint)
        return bmp
    }

    private fun loadRoundedArtwork(context: Context, artworkUri: String?): Bitmap? {
        if (artworkUri.isNullOrBlank()) {
            cachedArtUri = null
            cachedRoundedArt = null
            cachedArtAccent = null
            cachedArtSecondary = null
            return null
        }
        if (artworkUri == cachedArtUri && cachedRoundedArt != null) {
            return cachedRoundedArt
        }
        val raw = runCatching {
            val optsBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openArtworkStream(context, artworkUri)?.use {
                BitmapFactory.decodeStream(it, null, optsBounds)
            }
            val maxDim = maxOf(optsBounds.outWidth, optsBounds.outHeight).coerceAtLeast(1)
            var sampleSize = 1
            while (maxDim / sampleSize > 220) sampleSize *= 2

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            openArtworkStream(context, artworkUri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            }
        }.getOrNull() ?: return null

        val targetSize = 160
        val scaled = Bitmap.createScaledBitmap(raw, targetSize, targetSize, true)
        if (scaled !== raw) raw.recycle()

        // Extract dominant vibrant color for adaptive artwork widget theme
        val (accent1, accent2) = extractVibrantColors(scaled)
        cachedArtAccent = accent1
        cachedArtSecondary = accent2

        val rounded = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(rounded)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(RectF(0f, 0f, targetSize.toFloat(), targetSize.toFloat()), 26f, 26f, paint)
        scaled.recycle()

        cachedArtUri = artworkUri
        cachedRoundedArt = rounded
        return rounded
    }

    private fun openArtworkStream(context: Context, rawUri: String) = when {
        rawUri.startsWith("content://") || rawUri.startsWith("file://") ->
            context.contentResolver.openInputStream(Uri.parse(rawUri))
        rawUri.startsWith("/") -> File(rawUri).takeIf { it.exists() }?.inputStream()
        else -> null
    }

    private fun extractVibrantColors(bmp: Bitmap): Pair<Int, Int> {
        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        var count = 0L
        var bestScore = -1f
        var bestColor = Color.parseColor("#38E4FF")
        val hsv = FloatArray(3)

        val step = (bmp.width / 12).coerceAtLeast(1)
        var y = 0
        while (y < bmp.height) {
            var x = 0
            while (x < bmp.width) {
                val pixel = bmp.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                rSum += r
                gSum += g
                bSum += b
                count++

                Color.colorToHSV(pixel, hsv)
                val sat = hsv[1]
                val value = hsv[2]
                val score = sat * 1.6f + (1f - kotlin.math.abs(value - 0.65f))
                if (sat > 0.25f && value > 0.25f && score > bestScore) {
                    bestScore = score
                    bestColor = pixel
                }
                x += step
            }
            y += step
        }

        // Lift brightness of accent so it remains vivid on dark widget glass
        Color.colorToHSV(bestColor, hsv)
        hsv[1] = hsv[1].coerceIn(0.45f, 0.92f)
        hsv[2] = hsv[2].coerceIn(0.68f, 0.98f)
        val primary = Color.HSVToColor(hsv)

        val avgColor = if (count > 0) {
            Color.rgb((rSum / count).toInt(), (gSum / count).toInt(), (bSum / count).toInt())
        } else {
            Color.parseColor("#9D7BFF")
        }
        Color.colorToHSV(avgColor, hsv)
        hsv[0] = (hsv[0] + 45f) % 360f
        hsv[1] = hsv[1].coerceIn(0.40f, 0.85f)
        hsv[2] = hsv[2].coerceIn(0.60f, 0.92f)
        val secondary = Color.HSVToColor(hsv)
        return primary to secondary
    }

    private fun buildCodecLabel(playback: PlaybackUiState): String {
        val track = playback.currentTrack ?: return "PHOTON DECK"
        val fmt = track.format.uppercase().ifBlank { "AUDIO" }
        val khz = if (track.sampleRateHz >= 1000) {
            val k = track.sampleRateHz / 1000.0
            if (k % 1.0 == 0.0) "${k.toInt()}kHz" else "%.1fkHz".format(k)
        } else ""
        return if (khz.isNotEmpty()) "$fmt · $khz" else fmt
    }

    private fun computeProgressPermille(playback: PlaybackUiState): Int {
        val dur = playback.durationMs
        if (dur <= 0L) return 0
        return ((playback.positionMs.toDouble() / dur.toDouble()) * 1000.0).roundToInt().coerceIn(0, 1000)
    }

    private fun formatClock(ms: Long): String {
        val totalSec = (ms / 1000L).coerceAtLeast(0L)
        val mins = totalSec / 60L
        val secs = totalSec % 60L
        return "%d:%02d".format(mins, secs)
    }

    private fun setColorAlpha(color: Int, alpha: Int): Int {
        return Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun blendColors(c1: Int, c2: Int, ratio: Float): Int {
        val t = ratio.coerceIn(0f, 1f)
        val inv = 1f - t
        val r = (Color.red(c1) * inv + Color.red(c2) * t).roundToInt().coerceIn(0, 255)
        val g = (Color.green(c1) * inv + Color.green(c2) * t).roundToInt().coerceIn(0, 255)
        val b = (Color.blue(c1) * inv + Color.blue(c2) * t).roundToInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun colorLuminance(color: Int): Float {
        return (0.299f * Color.red(color) + 0.587f * Color.green(color) + 0.114f * Color.blue(color)) / 255f
    }
}
