package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.prefs.AppSettings
import com.example.ui.theme.AppFontChoice
import com.example.ui.theme.AppWallpaperPreset
import com.example.ui.theme.CosmicThemeSkin
import com.example.ui.theme.DeckStylePreset
import com.example.ui.theme.LocalPhotonColors
import kotlin.math.roundToInt

private val CUSTOM_ACCENT_SWATCHES: List<Pair<String, Long>> = listOf(
    "Cyan" to 0xFF38E4FF,
    "Nova Rose" to 0xFFFF5C8A,
    "Violet" to 0xFFB48CFF,
    "Gold" to 0xFFFFCF5C,
    "Emerald" to 0xFF34E5A8,
    "Cobalt" to 0xFF4D8BFF,
    "Sunset" to 0xFFFF7A45,
    "Crimson" to 0xFFFF4D5A,
    "Lime" to 0xFFA3E635,
    "Ice" to 0xFFC7E8FF
)

/**
 * Rich Appearance Studio card inside Settings enabling:
 * - Cosmic Theme Skins (including OLED Pure Black & Custom Accent Builder)
 * - Dynamic Album-Art Adaptive Player Colors toggle
 * - Custom App Font selection (5 bundled Google Fonts with live preview)
 * - App & Drawer Wallpaper presets + zero-permission Gallery Photo Picker + Dimming slider
 */
@Composable
fun AppearanceCustomizationCard(
    settings: AppSettings,
    onApplyDeckStylePreset: (DeckStylePreset) -> Unit = {},
    onSelectCosmicSkin: (CosmicThemeSkin) -> Unit,
    onSetCustomThemeColors: (primaryArgb: Long, secondaryArgb: Long, oledBlack: Boolean) -> Unit,
    onSetDynamicAlbumColors: (Boolean) -> Unit,
    onSelectAppFont: (AppFontChoice) -> Unit,
    onSelectWallpaperPreset: (AppWallpaperPreset) -> Unit,
    onPickCustomWallpaper: () -> Unit,
    onSetWallpaperDimAlpha: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val photonColors = LocalPhotonColors.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("appearance_customization_card"),
        shape = RoundedCornerShape(22.dp),
        color = photonColors.glassSurface,
        border = BorderStroke(1.1.dp, photonColors.photonGlow.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Section Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Rounded.Palette, contentDescription = null, tint = photonColors.photonGlow)
                Column {
                    Text(
                        text = "Theme, Font & Wallpaper Studio",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Cosmic skins, OLED Pure Black, custom colors, typography & wallpaper",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 0. Deck Typography & Theme Presets (Cyber Studio, Minimalist Hi-Fi, Editorial)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "DECK THEME PRESET (${settings.deckStylePreset?.displayName?.uppercase() ?: "CUSTOM"})",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = photonColors.photonGlow
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DeckStylePreset.entries.forEach { preset ->
                        val selected = settings.deckStylePreset == preset
                        Surface(
                            onClick = { onApplyDeckStylePreset(preset) },
                            shape = RoundedCornerShape(16.dp),
                            color = if (selected) photonColors.photonGlow.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(
                                width = if (selected) 1.6.dp else 0.9.dp,
                                color = if (selected) photonColors.photonGlow else photonColors.cardBorder
                            ),
                            modifier = Modifier
                                .width(156.dp)
                                .testTag("deck_preset_${preset.name.lowercase()}")
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = preset.displayName,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontFamily = preset.displayFont.fontFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = if (selected) photonColors.photonGlow else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = preset.subtitle,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = preset.bodyFont.fontFamily
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = photonColors.cardBorder.copy(alpha = 0.5f))

            // 1. Cosmic Theme Skins (Horizontal Swatch Cards)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "THEME SKIN (${settings.cosmicThemeSkin.displayName.uppercase()})",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = photonColors.photonGlow
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CosmicThemeSkin.entries.forEach { skin ->
                        val selected = settings.darkMode && settings.cosmicThemeSkin == skin
                        val primarySwatch = if (skin == CosmicThemeSkin.CUSTOM) {
                            Color(settings.customAccentPrimaryArgb)
                        } else {
                            skin.swatchPrimary
                        }
                        val secondarySwatch = if (skin == CosmicThemeSkin.CUSTOM) {
                            Color(settings.customAccentSecondaryArgb)
                        } else {
                            skin.swatchSecondary
                        }
                        val bgSwatch = if (skin == CosmicThemeSkin.CUSTOM && settings.customOledBlackBg) {
                            Color.Black
                        } else {
                            skin.swatchBackdrop
                        }

                        Surface(
                            onClick = { onSelectCosmicSkin(skin) },
                            shape = RoundedCornerShape(16.dp),
                            color = bgSwatch,
                            border = BorderStroke(
                                width = if (selected) 1.8.dp else 1.dp,
                                color = if (selected) primarySwatch else photonColors.cardBorder
                            ),
                            modifier = Modifier
                                .width(126.dp)
                                .testTag("theme_skin_${skin.name.lowercase()}")
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(primarySwatch)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(secondarySwatch)
                                        )
                                    }
                                    if (selected) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = "Selected skin",
                                            tint = primarySwatch,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = skin.displayName,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = skin.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFB4C6E6),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Custom Theme Accent Builder (shown when Custom Theme is selected)
            if (settings.cosmicThemeSkin == CosmicThemeSkin.CUSTOM) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(0.9.dp, Color(settings.customAccentPrimaryArgb).copy(alpha = 0.6f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "CUSTOM PRIMARY ACCENT",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(settings.customAccentPrimaryArgb)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CUSTOM_ACCENT_SWATCHES.forEach { (name, argb) ->
                                val isPicked = settings.customAccentPrimaryArgb == argb
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(argb))
                                        .border(
                                            width = if (isPicked) 2.5.dp else 1.dp,
                                            color = if (isPicked) Color.White else Color.White.copy(alpha = 0.25f),
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            onSetCustomThemeColors(
                                                argb,
                                                settings.customAccentSecondaryArgb,
                                                settings.customOledBlackBg
                                            )
                                        }
                                        .testTag("custom_primary_${name.lowercase().replace(" ", "_")}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isPicked) {
                                        Icon(
                                            Icons.Rounded.Check,
                                            contentDescription = name,
                                            tint = Color(0xFF040818),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = "CUSTOM SECONDARY GLOW",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(settings.customAccentSecondaryArgb)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CUSTOM_ACCENT_SWATCHES.forEach { (name, argb) ->
                                val isPicked = settings.customAccentSecondaryArgb == argb
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Color(argb))
                                        .border(
                                            width = if (isPicked) 2.5.dp else 1.dp,
                                            color = if (isPicked) Color.White else Color.White.copy(alpha = 0.25f),
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            onSetCustomThemeColors(
                                                settings.customAccentPrimaryArgb,
                                                argb,
                                                settings.customOledBlackBg
                                            )
                                        }
                                        .testTag("custom_secondary_${name.lowercase().replace(" ", "_")}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isPicked) {
                                        Icon(
                                            Icons.Rounded.Check,
                                            contentDescription = name,
                                            tint = Color(0xFF040818),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "OLED Pure Black Background",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "Use true #000000 backdrop with your custom glow colors",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.customOledBlackBg,
                                onCheckedChange = { oled ->
                                    onSetCustomThemeColors(
                                        settings.customAccentPrimaryArgb,
                                        settings.customAccentSecondaryArgb,
                                        oled
                                    )
                                },
                                modifier = Modifier.testTag("custom_theme_oled_switch")
                            )
                        }
                    }
                }
            }

            // Dynamic Album-Art Adaptive Colors Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = photonColors.photonGlow,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Dynamic Album-Art Glow",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "Adapts Now Playing halo & accents to each song's cover art",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = settings.dynamicAlbumColorsEnabled,
                    onCheckedChange = onSetDynamicAlbumColors,
                    modifier = Modifier.testTag("dynamic_album_colors_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = photonColors.photonGlow
                    )
                )
            }

            HorizontalDivider(color = photonColors.cardBorder.copy(alpha = 0.5f))

            // 2. Custom App Font Picker (All 7 bundled local Google Fonts with live preview)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Rounded.FontDownload, contentDescription = null, tint = photonColors.photonGlow, modifier = Modifier.size(16.dp))
                    Text(
                        text = "BUNDLED FONTS (${settings.appFont.displayName.uppercase()})",
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
                    AppFontChoice.entries.forEach { font ->
                        val selected = settings.appFont == font
                        Surface(
                            onClick = { onSelectAppFont(font) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) photonColors.photonGlow.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(
                                width = if (selected) 1.4.dp else 0.8.dp,
                                color = if (selected) photonColors.photonGlow else photonColors.cardBorder
                            ),
                            modifier = Modifier
                                .width(136.dp)
                                .testTag("font_choice_${font.name.lowercase()}")
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = font.displayName,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontFamily = font.fontFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = if (selected) photonColors.photonGlow else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Aa Bb 123 · ${font.subtitle}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = font.fontFamily),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = photonColors.cardBorder.copy(alpha = 0.5f))

            // 3. App & Drawer Wallpaper Picker + Custom Photo + Dimming Slider
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Rounded.Image, contentDescription = null, tint = photonColors.photonGlow, modifier = Modifier.size(16.dp))
                    Text(
                        text = "APP & DRAWER WALLPAPER (${settings.wallpaperPreset.displayName.uppercase()})",
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
                    AppWallpaperPreset.entries.forEach { preset ->
                        val selected = settings.wallpaperPreset == preset
                        val isCustomWithoutPhoto = preset == AppWallpaperPreset.CUSTOM_PHOTO && settings.customWallpaperPath == null
                        Surface(
                            onClick = {
                                if (isCustomWithoutPhoto) {
                                    onPickCustomWallpaper()
                                } else {
                                    onSelectWallpaperPreset(preset)
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) photonColors.photonGlow.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(
                                width = if (selected) 1.4.dp else 0.8.dp,
                                color = if (selected) photonColors.photonGlow else photonColors.cardBorder
                            ),
                            modifier = Modifier
                                .width(126.dp)
                                .testTag("wallpaper_preset_${preset.name.lowercase()}")
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(
                                            when (preset) {
                                                AppWallpaperPreset.COSMIC_CANVAS -> Brush.horizontalGradient(
                                                    listOf(photonColors.backdropTop, photonColors.photonGlow)
                                                )
                                                AppWallpaperPreset.GALAXY_NEBULA -> Brush.horizontalGradient(
                                                    listOf(Color(0xFF0A2463), Color(0xFF38E4FF))
                                                )
                                                AppWallpaperPreset.PHOTON_PRISM -> Brush.horizontalGradient(
                                                    listOf(Color(0xFF9D7BFF), Color(0xFF38E4FF))
                                                )
                                                AppWallpaperPreset.CUSTOM_PHOTO -> Brush.horizontalGradient(
                                                    listOf(Color(0xFFFF5C8A), Color(0xFFFFCF5C))
                                                )
                                            }
                                        )
                                )
                                Text(
                                    text = preset.displayName,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (selected) photonColors.photonGlow else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = preset.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = onPickCustomWallpaper,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("pick_custom_wallpaper_btn"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (settings.customWallpaperPath != null) {
                            "Change Custom Wallpaper Photo"
                        } else {
                            "Pick Custom Wallpaper from Gallery"
                        },
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                if (settings.wallpaperPreset != AppWallpaperPreset.COSMIC_CANVAS) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Wallpaper Dimming (Readability)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${(settings.wallpaperDimAlpha * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = photonColors.photonGlow
                            )
                        }
                        Slider(
                            value = settings.wallpaperDimAlpha,
                            onValueChange = onSetWallpaperDimAlpha,
                            valueRange = 0.25f..0.88f,
                            modifier = Modifier.testTag("wallpaper_dim_slider"),
                            colors = SliderDefaults.colors(
                                thumbColor = photonColors.photonGlow,
                                activeTrackColor = photonColors.photonGlow,
                                inactiveTrackColor = photonColors.cardBorder
                            )
                        )
                    }
                }
            }
        }
    }
}
