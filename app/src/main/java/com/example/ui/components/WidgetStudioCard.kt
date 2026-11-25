package com.example.ui.components

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.TrackEntity
import com.example.data.model.WidgetThemeMode
import com.example.data.prefs.AppSettings
import com.example.ui.theme.LocalPhotonColors
import com.example.widget.PhotonGlanceWidgetReceiver
import kotlin.math.roundToInt

private enum class WidgetPreviewSize(val label: String, val cells: String) {
    COMPACT("Compact", "2×1"),
    STANDARD("Standard", "4×2"),
    STUDIO("Studio Deck", "4×3+")
}

@Composable
fun WidgetStudioCard(
    settings: AppSettings,
    currentTrack: TrackEntity?,
    isPlaying: Boolean,
    shuffleEnabled: Boolean,
    onSetWidgetThemeMode: (WidgetThemeMode) -> Unit,
    onSetWidgetGlassOpacity: (Float) -> Unit,
    onWidgetPinnedFeedback: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val photonColors = LocalPhotonColors.current
    var selectedPreview by remember { mutableStateOf(WidgetPreviewSize.STANDARD) }

    val previewAccent = if (settings.widgetThemeMode == WidgetThemeMode.ADAPTIVE_ARTWORK && currentTrack != null) {
        photonColors.nebulaAccent
    } else {
        photonColors.photonGlow
    }
    val previewSecondary = photonColors.nebulaAccent
    val previewBgStart = lerp(photonColors.backdropTop, previewAccent, 0.14f).copy(alpha = settings.widgetGlassOpacity)
    val previewBgEnd = lerp(photonColors.backdropMid, previewSecondary, 0.20f).copy(alpha = settings.widgetGlassOpacity)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("widget_studio_card"),
        shape = RoundedCornerShape(22.dp),
        color = photonColors.glassSurface,
        border = BorderStroke(1.dp, photonColors.photonGlow.copy(alpha = 0.55f))
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
                    imageVector = Icons.Rounded.Widgets,
                    contentDescription = null,
                    tint = photonColors.photonGlow
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Home Screen Widget Studio",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Resizes seamlessly across 2×1, 4×1/4×2, and 4×3+ decks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Size Preview Tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WidgetPreviewSize.entries.forEach { sizeOption ->
                    val selected = selectedPreview == sizeOption
                    FilterChip(
                        selected = selected,
                        onClick = { selectedPreview = sizeOption },
                        label = {
                            Text(
                                text = "${sizeOption.label} ${sizeOption.cells}",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("widget_preview_tab_${sizeOption.name.lowercase()}"),
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

            // Live Interactive Widget Mockup Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                photonColors.backdropTop.copy(alpha = 0.9f),
                                photonColors.backdropMid.copy(alpha = 0.85f)
                            )
                        )
                    )
                    .border(0.8.dp, photonColors.cardBorder.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                val trackTitle = currentTrack?.title ?: "Cosmic Horizon"
                val trackArtist = currentTrack?.artist ?: "Photon Audio Studio"
                val trackFormat = currentTrack?.format?.uppercase()?.ifBlank { "FLAC" } ?: "FLAC · 48kHz"
                val isFavorite = currentTrack?.isFavorite == true

                val widgetCardModifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(previewBgStart, previewBgEnd)))
                    .border(1.1.dp, previewAccent.copy(alpha = 0.55f), RoundedCornerShape(20.dp))

                when (selectedPreview) {
                    WidgetPreviewSize.COMPACT -> {
                        Row(
                            modifier = widgetCardModifier
                                .width(210.dp)
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PhotonAlbumArt(
                                artworkUri = currentTrack?.artworkUri,
                                contentDescription = "Compact widget artwork preview",
                                size = 40.dp,
                                cornerRadius = 10.dp,
                                isPlaying = isPlaying
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = trackTitle,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = trackArtist,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFC2D8F2),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(previewAccent),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = Color(0xFF040818),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    WidgetPreviewSize.STANDARD -> {
                        Column(
                            modifier = widgetCardModifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                PhotonAlbumArt(
                                    artworkUri = currentTrack?.artworkUri,
                                    contentDescription = "Standard widget artwork preview",
                                    size = 50.dp,
                                    cornerRadius = 12.dp,
                                    isPlaying = isPlaying
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = trackFormat,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = previewAccent
                                    )
                                    Text(
                                        text = trackTitle,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = trackArtist,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFC2D8F2),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.SkipPrevious,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(previewAccent),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                            contentDescription = null,
                                            tint = Color(0xFF040818),
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Icon(
                                        Icons.Rounded.SkipNext,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            LinearProgressIndicator(
                                progress = { 0.42f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = previewAccent,
                                trackColor = Color.White.copy(alpha = 0.22f)
                            )
                        }
                    }

                    WidgetPreviewSize.STUDIO -> {
                        Column(
                            modifier = widgetCardModifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "PHOTON DECK",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.2.sp
                                    ),
                                    color = previewAccent
                                )
                                Surface(
                                    shape = RoundedCornerShape(99.dp),
                                    color = Color.White.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = trackFormat,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = previewAccent,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                PhotonAlbumArt(
                                    artworkUri = currentTrack?.artworkUri,
                                    contentDescription = "Studio widget artwork preview",
                                    size = 72.dp,
                                    cornerRadius = 14.dp,
                                    isPlaying = isPlaying
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = trackTitle,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = trackArtist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFD0DCEB),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            LinearProgressIndicator(
                                progress = { 0.42f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = previewAccent,
                                trackColor = Color.White.copy(alpha = 0.22f)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.Shuffle,
                                    contentDescription = null,
                                    tint = if (shuffleEnabled) previewAccent else Color(0xFFA0B8D8),
                                    modifier = Modifier.size(20.dp)
                                )
                                Icon(
                                    Icons.Rounded.SkipPrevious,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(previewAccent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = null,
                                        tint = Color(0xFF040818),
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                Icon(
                                    Icons.Rounded.SkipNext,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                                Icon(
                                    imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    contentDescription = null,
                                    tint = if (isFavorite) Color(0xFFFF5C8A) else Color(0xFFA0B8D8),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Color Sync Selector
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "WIDGET COLOR SYNC",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = photonColors.photonGlow
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WidgetThemeMode.entries.forEach { mode ->
                        val selected = settings.widgetThemeMode == mode
                        FilterChip(
                            selected = selected,
                            onClick = { onSetWidgetThemeMode(mode) },
                            label = {
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("widget_theme_mode_${mode.name.lowercase()}"),
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

            // Glass Opacity Slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Widget Glass Opacity",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${(settings.widgetGlassOpacity * 100f).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = photonColors.photonGlow
                    )
                }
                Slider(
                    value = settings.widgetGlassOpacity,
                    onValueChange = onSetWidgetGlassOpacity,
                    valueRange = 0.45f..1.0f,
                    modifier = Modifier.testTag("widget_glass_opacity_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = photonColors.photonGlow,
                        activeTrackColor = photonColors.photonGlow
                    )
                )
            }

            // Pin to Home Screen Button
            Button(
                onClick = {
                    val manager = AppWidgetManager.getInstance(context)
                    val provider = ComponentName(context, PhotonGlanceWidgetReceiver::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.isRequestPinAppWidgetSupported) {
                        val requested = manager.requestPinAppWidget(provider, null, null)
                        if (requested) {
                            onWidgetPinnedFeedback("Confirm on your home screen to place the Photon Deck widget")
                        } else {
                            onWidgetPinnedFeedback("Long-press your home screen → Widgets → Photon Audio Deck")
                        }
                    } else {
                        onWidgetPinnedFeedback("Long-press your home screen → Widgets → Photon Audio Deck")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("pin_widget_home_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = photonColors.photonGlow,
                    contentColor = Color(0xFF040818)
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.AddToHomeScreen,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Pin Widget to Home Screen",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}
