package com.example.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.MainActivity
import com.example.PhotonApplication
import com.example.R
import com.example.data.prefs.AppSettings
import com.example.player.PhotonPlaybackBridge
import com.example.player.PlaybackUiState
import com.example.ui.navigation.PhotonDeepLinks
import kotlinx.coroutines.flow.first

/**
 * Jetpack Glance home screen widget supporting Compact (2x1), Standard (4x2), and Studio (4x3+) sizes.
 * - Tapping album artwork routes directly to the expanded Now Playing Deck via deep link.
 * - Tapping the Library icon routes directly to LibraryKey via deep link.
 * - Playback controls dispatch cleanly via Glance ActionCallbacks with debounced state sync.
 */
class PhotonGlanceWidget : GlanceAppWidget() {

    companion object {
        private val COMPACT_SIZE = DpSize(110.dp, 56.dp)
        private val STANDARD_SIZE = DpSize(240.dp, 100.dp)
        private val STUDIO_SIZE = DpSize(240.dp, 190.dp)
    }

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(COMPACT_SIZE, STANDARD_SIZE, STUDIO_SIZE)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as? PhotonApplication
        val settings = runCatching {
            app?.settingsRepository?.settingsFlow?.first()
        }.getOrNull() ?: AppSettings()

        val playback = PhotonPlaybackBridge.state.value
        val artworkBitmap = PhotonWidgetUpdater.loadRoundedArtworkForGlance(
            context.applicationContext,
            playback.currentTrack?.artworkUri
        )
        val palette = PhotonWidgetUpdater.resolveGlancePalette(settings, artworkBitmap != null)

        provideContent {
            val size = LocalSize.current
            when {
                size.height >= 175.dp -> StudioGlanceContent(
                    context = context,
                    playback = playback,
                    artwork = artworkBitmap,
                    palette = palette,
                    glassOpacity = settings.widgetGlassOpacity
                )
                size.width >= 210.dp -> StandardGlanceContent(
                    context = context,
                    playback = playback,
                    artwork = artworkBitmap,
                    palette = palette,
                    glassOpacity = settings.widgetGlassOpacity
                )
                else -> CompactGlanceContent(
                    context = context,
                    playback = playback,
                    artwork = artworkBitmap,
                    palette = palette,
                    glassOpacity = settings.widgetGlassOpacity
                )
            }
        }
    }
}

private fun nowPlayingDeepLinkIntent(context: Context): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse(PhotonDeepLinks.URI_NOW_PLAYING), context, MainActivity::class.java).apply {
        putExtra(PhotonDeepLinks.EXTRA_TARGET, PhotonDeepLinks.TARGET_NOW_PLAYING)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

private fun libraryDeepLinkIntent(context: Context): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse(PhotonDeepLinks.URI_LIBRARY), context, MainActivity::class.java).apply {
        putExtra(PhotonDeepLinks.EXTRA_TARGET, PhotonDeepLinks.TARGET_LIBRARY)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

@Composable
private fun CompactGlanceContent(
    context: Context,
    playback: PlaybackUiState,
    artwork: Bitmap?,
    palette: GlanceWidgetPalette,
    glassOpacity: Float
) {
    val track = playback.currentTrack
    val bg = Color(palette.bgStart).copy(alpha = glassOpacity.coerceIn(0.62f, 0.98f))
    val accent = Color(palette.accentPrimary)
    val titleColor = Color(palette.titleColor)
    val subtitleColor = Color(palette.subtitleColor)
    val onAccent = Color(palette.onAccent)

    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(20.dp)
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WidgetArtworkBox(
            artwork = artwork,
            accent = accent,
            sizeDp = 42,
            onClickIntent = nowPlayingDeepLinkIntent(context)
        )
        Spacer(modifier = GlanceModifier.width(8.dp))
        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .clickable(actionStartActivity(nowPlayingDeepLinkIntent(context)))
        ) {
            Text(
                text = track?.title ?: context.getString(R.string.widget_empty_title),
                style = TextStyle(
                    color = ColorProvider(titleColor),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            Text(
                text = track?.artist ?: context.getString(R.string.widget_empty_subtitle),
                style = TextStyle(
                    color = ColorProvider(subtitleColor),
                    fontSize = 11.sp
                ),
                maxLines = 1
            )
        }
        Spacer(modifier = GlanceModifier.width(6.dp))
        Box(
            modifier = GlanceModifier
                .size(36.dp)
                .cornerRadius(18.dp)
                .background(accent)
                .clickable(actionRunCallback<PlayPauseWidgetAction>()),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(
                    if (playback.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
                ),
                contentDescription = if (playback.isPlaying) "Pause" else "Play",
                colorFilter = ColorFilter.tint(ColorProvider(onAccent)),
                modifier = GlanceModifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun StandardGlanceContent(
    context: Context,
    playback: PlaybackUiState,
    artwork: Bitmap?,
    palette: GlanceWidgetPalette,
    glassOpacity: Float
) {
    val track = playback.currentTrack
    val bg = Color(palette.bgStart).copy(alpha = glassOpacity.coerceIn(0.62f, 0.98f))
    val accent = Color(palette.accentPrimary)
    val titleColor = Color(palette.titleColor)
    val subtitleColor = Color(palette.subtitleColor)
    val onAccent = Color(palette.onAccent)
    val progress = if (playback.durationMs > 0L) {
        (playback.positionMs.toFloat() / playback.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(22.dp)
            .background(bg)
            .padding(12.dp)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WidgetArtworkBox(
                artwork = artwork,
                accent = accent,
                sizeDp = 52,
                onClickIntent = nowPlayingDeepLinkIntent(context)
            )
            Spacer(modifier = GlanceModifier.width(10.dp))
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .clickable(actionStartActivity(nowPlayingDeepLinkIntent(context)))
            ) {
                Text(
                    text = PhotonWidgetUpdater.buildCodecLabelForGlance(playback),
                    style = TextStyle(
                        color = ColorProvider(accent),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                Text(
                    text = track?.title ?: context.getString(R.string.widget_empty_title),
                    style = TextStyle(
                        color = ColorProvider(titleColor),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                Text(
                    text = track?.artist ?: context.getString(R.string.widget_empty_subtitle),
                    style = TextStyle(
                        color = ColorProvider(subtitleColor),
                        fontSize = 12.sp
                    ),
                    maxLines = 1
                )
            }
            Spacer(modifier = GlanceModifier.width(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = GlanceModifier
                        .size(34.dp)
                        .cornerRadius(17.dp)
                        .clickable(actionRunCallback<SkipPrevWidgetAction>()),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_prev),
                        contentDescription = "Previous",
                        colorFilter = ColorFilter.tint(ColorProvider(titleColor)),
                        modifier = GlanceModifier.size(20.dp)
                    )
                }
                Box(
                    modifier = GlanceModifier
                        .size(40.dp)
                        .cornerRadius(20.dp)
                        .background(accent)
                        .clickable(actionRunCallback<PlayPauseWidgetAction>()),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(
                            if (playback.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
                        ),
                        contentDescription = if (playback.isPlaying) "Pause" else "Play",
                        colorFilter = ColorFilter.tint(ColorProvider(onAccent)),
                        modifier = GlanceModifier.size(22.dp)
                    )
                }
                Box(
                    modifier = GlanceModifier
                        .size(34.dp)
                        .cornerRadius(17.dp)
                        .clickable(actionRunCallback<SkipNextWidgetAction>()),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_next),
                        contentDescription = "Next",
                        colorFilter = ColorFilter.tint(ColorProvider(titleColor)),
                        modifier = GlanceModifier.size(20.dp)
                    )
                }
                Box(
                    modifier = GlanceModifier
                        .size(32.dp)
                        .cornerRadius(16.dp)
                        .clickable(actionStartActivity(libraryDeepLinkIntent(context))),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_library),
                        contentDescription = "Open Library",
                        colorFilter = ColorFilter.tint(ColorProvider(subtitleColor)),
                        modifier = GlanceModifier.size(18.dp)
                    )
                }
            }
        }
        Spacer(modifier = GlanceModifier.height(6.dp))
        LinearProgressIndicator(
            progress = progress,
            modifier = GlanceModifier.fillMaxWidth().height(4.dp).cornerRadius(2.dp),
            color = ColorProvider(accent),
            backgroundColor = ColorProvider(Color.White.copy(alpha = 0.2f))
        )
    }
}

@Composable
private fun StudioGlanceContent(
    context: Context,
    playback: PlaybackUiState,
    artwork: Bitmap?,
    palette: GlanceWidgetPalette,
    glassOpacity: Float
) {
    val track = playback.currentTrack
    val bg = Color(palette.bgStart).copy(alpha = glassOpacity.coerceIn(0.62f, 0.98f))
    val accent = Color(palette.accentPrimary)
    val titleColor = Color(palette.titleColor)
    val subtitleColor = Color(palette.subtitleColor)
    val onAccent = Color(palette.onAccent)
    val progress = if (playback.durationMs > 0L) {
        (playback.positionMs.toFloat() / playback.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val isFav = track?.isFavorite == true

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(24.dp)
            .background(bg)
            .padding(14.dp)
    ) {
        // Header row: Brand + Codec + Library deep-link icon
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PHOTON DECK",
                style = TextStyle(
                    color = ColorProvider(accent),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier.defaultWeight()
            )
            Text(
                text = PhotonWidgetUpdater.buildCodecLabelForGlance(playback),
                style = TextStyle(
                    color = ColorProvider(subtitleColor),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            Box(
                modifier = GlanceModifier
                    .size(30.dp)
                    .cornerRadius(15.dp)
                    .background(Color.White.copy(alpha = 0.1f))
                    .clickable(actionStartActivity(libraryDeepLinkIntent(context))),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_library),
                    contentDescription = "Open Library",
                    colorFilter = ColorFilter.tint(ColorProvider(accent)),
                    modifier = GlanceModifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(10.dp))

        // Main Artwork + Metadata
        Row(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WidgetArtworkBox(
                artwork = artwork,
                accent = accent,
                sizeDp = 74,
                onClickIntent = nowPlayingDeepLinkIntent(context)
            )
            Spacer(modifier = GlanceModifier.width(12.dp))
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .clickable(actionStartActivity(nowPlayingDeepLinkIntent(context)))
            ) {
                Text(
                    text = track?.title ?: context.getString(R.string.widget_empty_title),
                    style = TextStyle(
                        color = ColorProvider(titleColor),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 2
                )
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = track?.artist ?: context.getString(R.string.widget_empty_subtitle),
                    style = TextStyle(
                        color = ColorProvider(subtitleColor),
                        fontSize = 12.sp
                    ),
                    maxLines = 1
                )
                val album = track?.album?.takeIf { it.isNotBlank() && !it.equals("Unknown Album", ignoreCase = true) }
                if (album != null) {
                    Text(
                        text = album,
                        style = TextStyle(
                            color = ColorProvider(subtitleColor.copy(alpha = 0.8f)),
                            fontSize = 11.sp
                        ),
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        // Progress + Elapsed / Total
        LinearProgressIndicator(
            progress = progress,
            modifier = GlanceModifier.fillMaxWidth().height(4.dp).cornerRadius(2.dp),
            color = ColorProvider(accent),
            backgroundColor = ColorProvider(Color.White.copy(alpha = 0.2f))
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                text = PhotonWidgetUpdater.formatClockForGlance(playback.positionMs),
                style = TextStyle(color = ColorProvider(subtitleColor), fontSize = 10.sp),
                modifier = GlanceModifier.defaultWeight()
            )
            Text(
                text = PhotonWidgetUpdater.formatClockForGlance(playback.durationMs),
                style = TextStyle(color = ColorProvider(subtitleColor), fontSize = 10.sp)
            )
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        // Full Transport Controls
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .height(40.dp)
                    .clickable(actionRunCallback<ToggleShuffleWidgetAction>()),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_shuffle),
                    contentDescription = "Shuffle",
                    colorFilter = ColorFilter.tint(
                        ColorProvider(if (playback.shuffleEnabled) accent else subtitleColor)
                    ),
                    modifier = GlanceModifier.size(20.dp)
                )
            }
            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .height(40.dp)
                    .clickable(actionRunCallback<SkipPrevWidgetAction>()),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_prev),
                    contentDescription = "Previous",
                    colorFilter = ColorFilter.tint(ColorProvider(titleColor)),
                    modifier = GlanceModifier.size(24.dp)
                )
            }
            Box(
                modifier = GlanceModifier
                    .size(46.dp)
                    .cornerRadius(23.dp)
                    .background(accent)
                    .clickable(actionRunCallback<PlayPauseWidgetAction>()),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(
                        if (playback.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
                    ),
                    contentDescription = if (playback.isPlaying) "Pause" else "Play",
                    colorFilter = ColorFilter.tint(ColorProvider(onAccent)),
                    modifier = GlanceModifier.size(24.dp)
                )
            }
            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .height(40.dp)
                    .clickable(actionRunCallback<SkipNextWidgetAction>()),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_next),
                    contentDescription = "Next",
                    colorFilter = ColorFilter.tint(ColorProvider(titleColor)),
                    modifier = GlanceModifier.size(24.dp)
                )
            }
            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .height(40.dp)
                    .clickable(actionRunCallback<ToggleFavoriteWidgetAction>()),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(
                        if (isFav) R.drawable.ic_widget_fav_filled else R.drawable.ic_widget_fav_outline
                    ),
                    contentDescription = if (isFav) "Remove Favorite" else "Favorite",
                    colorFilter = ColorFilter.tint(
                        ColorProvider(if (isFav) Color(0xFFFF5C8A) else subtitleColor)
                    ),
                    modifier = GlanceModifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun WidgetArtworkBox(
    artwork: Bitmap?,
    accent: Color,
    sizeDp: Int,
    onClickIntent: Intent
) {
    Box(
        modifier = GlanceModifier
            .size(sizeDp.dp)
            .cornerRadius(14.dp)
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(actionStartActivity(onClickIntent)),
        contentAlignment = Alignment.Center
    ) {
        if (artwork != null) {
            Image(
                provider = ImageProvider(artwork),
                contentDescription = "Open Now Playing Deck",
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier.fillMaxSize().cornerRadius(14.dp)
            )
        } else {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_music_note),
                contentDescription = "Open Now Playing Deck",
                colorFilter = ColorFilter.tint(ColorProvider(accent)),
                modifier = GlanceModifier.size((sizeDp * 0.56f).dp)
            )
        }
    }
}

class PlayPauseWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val app = context.applicationContext as? PhotonApplication ?: return
        app.playerController.togglePlayPause()
        PhotonWidgetUpdater.requestUpdate(context)
    }
}

class SkipNextWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val app = context.applicationContext as? PhotonApplication ?: return
        app.playerController.skipNext()
        PhotonWidgetUpdater.requestUpdate(context)
    }
}

class SkipPrevWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val app = context.applicationContext as? PhotonApplication ?: return
        app.playerController.skipPrevious()
        PhotonWidgetUpdater.requestUpdate(context)
    }
}

class ToggleShuffleWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val app = context.applicationContext as? PhotonApplication ?: return
        app.playerController.toggleShuffle()
        PhotonWidgetUpdater.requestUpdate(context)
    }
}

class ToggleFavoriteWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val app = context.applicationContext as? PhotonApplication ?: return
        val track = PhotonPlaybackBridge.state.value.currentTrack ?: return
        val toggled = !track.isFavorite
        app.libraryRepository.setTrackFavorite(track.id, toggled)
        app.playerController.updateTrackInQueue(track.copy(isFavorite = toggled))
        PhotonWidgetUpdater.requestUpdate(context)
    }
}
