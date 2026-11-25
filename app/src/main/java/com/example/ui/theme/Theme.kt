package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

enum class AppWallpaperPreset(
    val displayName: String,
    val subtitle: String
) {
    COSMIC_CANVAS("Cosmic Starfield", "Animated gradient & starlight"),
    GALAXY_NEBULA("Galaxy Nebula", "Deep blue cosmic nebula art"),
    PHOTON_PRISM("Photon Prism", "Crystalline neon prism art"),
    CUSTOM_PHOTO("Custom Photo", "Your chosen gallery wallpaper")
}

enum class CosmicThemeSkin(
    val displayName: String,
    val subtitle: String,
    val swatchPrimary: Color,
    val swatchSecondary: Color,
    val swatchBackdrop: Color
) {
    PHOTON_CYAN(
        displayName = "Photon Cyan",
        subtitle = "Deep space indigo & electric cyan",
        swatchPrimary = PhotonCyan,
        swatchSecondary = AstralViolet,
        swatchBackdrop = VoidIndigo
    ),
    ASTRAL_VIOLET(
        displayName = "Astral Violet",
        subtitle = "Cosmic nebula violet & plasma lilac",
        swatchPrimary = Color(0xFFB48CFF),
        swatchSecondary = Color(0xFF38E4FF),
        swatchBackdrop = Color(0xFF0A051D)
    ),
    SOLAR_GOLD(
        displayName = "Solar Gold",
        subtitle = "Obsidian night & gilded supernova",
        swatchPrimary = Color(0xFFFFCF5C),
        swatchSecondary = Color(0xFFFF8866),
        swatchBackdrop = Color(0xFF100A05)
    ),
    EMERALD_NEBULA(
        displayName = "Emerald Nebula",
        subtitle = "Abyssal teal & lossless emerald glow",
        swatchPrimary = Color(0xFF34E5A8),
        swatchSecondary = Color(0xFF38BDF8),
        swatchBackdrop = Color(0xFF041312)
    ),
    OLED_PURE_BLACK(
        displayName = "OLED Pure Black",
        subtitle = "True #000000 pitch black with neon cyan",
        swatchPrimary = PhotonCyan,
        swatchSecondary = Color(0xFF7C6BFF),
        swatchBackdrop = Color(0xFF000000)
    ),
    CUSTOM(
        displayName = "Custom Theme",
        subtitle = "Your custom accent colors & backdrop",
        swatchPrimary = Color(0xFFFF5C8A),
        swatchSecondary = Color(0xFF38E4FF),
        swatchBackdrop = Color(0xFF0B0718)
    )
}

@Immutable
data class PhotonExtendedColors(
    val photonGlow: Color,
    val nebulaAccent: Color,
    val goldBadge: Color,
    val losslessBadge: Color,
    val cardBorder: Color,
    val glassSurface: Color,
    val backdropTop: Color,
    val backdropMid: Color,
    val backdropBottom: Color,
    val starColor: Color
)

@Immutable
data class PhotonWallpaperConfig(
    val preset: AppWallpaperPreset = AppWallpaperPreset.COSMIC_CANVAS,
    val customPhotoPath: String? = null,
    val dimAlpha: Float = 0.62f
)

private fun darkExtendedForSkin(
    skin: CosmicThemeSkin,
    customPrimary: Color,
    customSecondary: Color,
    customOledBlack: Boolean
): PhotonExtendedColors = when (skin) {
    CosmicThemeSkin.PHOTON_CYAN -> PhotonExtendedColors(
        photonGlow = PhotonCyan,
        nebulaAccent = AstralViolet,
        goldBadge = SolarGold,
        losslessBadge = LosslessEmerald,
        cardBorder = Color(0xFF233C82),
        glassSurface = Color(0xCC0D1A42),
        backdropTop = Color(0xFF040818),
        backdropMid = Color(0xFF0A163D),
        backdropBottom = Color(0xFF050B20),
        starColor = Color(0xFFBCEFFF)
    )
    CosmicThemeSkin.ASTRAL_VIOLET -> PhotonExtendedColors(
        photonGlow = Color(0xFFB48CFF),
        nebulaAccent = Color(0xFF38E4FF),
        goldBadge = SolarGold,
        losslessBadge = LosslessEmerald,
        cardBorder = Color(0xFF3D2578),
        glassSurface = Color(0xCC180E38),
        backdropTop = Color(0xFF0A051D),
        backdropMid = Color(0xFF170B3B),
        backdropBottom = Color(0xFF090418),
        starColor = Color(0xFFE0D1FF)
    )
    CosmicThemeSkin.SOLAR_GOLD -> PhotonExtendedColors(
        photonGlow = Color(0xFFFFCF5C),
        nebulaAccent = Color(0xFFFF8866),
        goldBadge = Color(0xFFFFDF80),
        losslessBadge = LosslessEmerald,
        cardBorder = Color(0xFF5B3F14),
        glassSurface = Color(0xCC211609),
        backdropTop = Color(0xFF100A05),
        backdropMid = Color(0xFF1F140A),
        backdropBottom = Color(0xFF0D0804),
        starColor = Color(0xFFFFECC2)
    )
    CosmicThemeSkin.EMERALD_NEBULA -> PhotonExtendedColors(
        photonGlow = Color(0xFF34E5A8),
        nebulaAccent = Color(0xFF38BDF8),
        goldBadge = SolarGold,
        losslessBadge = Color(0xFF52F5BD),
        cardBorder = Color(0xFF15544B),
        glassSurface = Color(0xCC082623),
        backdropTop = Color(0xFF041312),
        backdropMid = Color(0xFF092826),
        backdropBottom = Color(0xFF031010),
        starColor = Color(0xFFC2FFEE)
    )
    CosmicThemeSkin.OLED_PURE_BLACK -> PhotonExtendedColors(
        photonGlow = PhotonCyan,
        nebulaAccent = Color(0xFF8B7AFF),
        goldBadge = SolarGold,
        losslessBadge = LosslessEmerald,
        cardBorder = Color(0xFF1E293B),
        glassSurface = Color(0xEB090C14),
        backdropTop = Color(0xFF000000),
        backdropMid = Color(0xFF000000),
        backdropBottom = Color(0xFF000000),
        starColor = Color(0xFF9DEEFF)
    )
    CosmicThemeSkin.CUSTOM -> {
        val topBg = if (customOledBlack) Color.Black else lerp(Color(0xFF050814), customPrimary, 0.08f)
        val midBg = if (customOledBlack) Color.Black else lerp(Color(0xFF0B1228), customSecondary, 0.14f)
        val bottomBg = if (customOledBlack) Color.Black else lerp(Color(0xFF040712), customPrimary, 0.06f)
        PhotonExtendedColors(
            photonGlow = customPrimary,
            nebulaAccent = customSecondary,
            goldBadge = SolarGold,
            losslessBadge = LosslessEmerald,
            cardBorder = lerp(Color(0xFF1E293B), customPrimary, 0.36f),
            glassSurface = if (customOledBlack) Color(0xEB090C14) else lerp(Color(0xCC0C142B), customPrimary, 0.09f),
            backdropTop = topBg,
            backdropMid = midBg,
            backdropBottom = bottomBg,
            starColor = lerp(Color.White, customPrimary, 0.35f)
        )
    }
}

private val LightPhotonExtended = PhotonExtendedColors(
    photonGlow = DeepPhotonTeal,
    nebulaAccent = DeepAstralViolet,
    goldBadge = Color(0xFFB57A00),
    losslessBadge = Color(0xFF0F8B5F),
    cardBorder = AstralBorder,
    glassSurface = Color(0xE6FFFFFF),
    backdropTop = Color(0xFFEEF4FF),
    backdropMid = Color(0xFFE0ECFF),
    backdropBottom = Color(0xFFF6F9FF),
    starColor = Color(0xFF2B60E0)
)

val LocalPhotonColors = staticCompositionLocalOf {
    darkExtendedForSkin(CosmicThemeSkin.PHOTON_CYAN, PhotonCyan, AstralViolet, false)
}

val LocalPhotonWallpaper = staticCompositionLocalOf { PhotonWallpaperConfig() }

private fun darkColorSchemeForSkin(
    skin: CosmicThemeSkin,
    customPrimary: Color,
    customSecondary: Color,
    customOledBlack: Boolean
): ColorScheme = when (skin) {
    CosmicThemeSkin.PHOTON_CYAN -> darkColorScheme(
        primary = PhotonCyan,
        onPrimary = VoidIndigo,
        primaryContainer = Color(0xFF0B3A5C),
        onPrimaryContainer = Color(0xFFC2F5FF),
        secondary = HyperCobalt,
        onSecondary = StarlightWhite,
        secondaryContainer = Color(0xFF162C6E),
        onSecondaryContainer = Color(0xFFD6E4FF),
        tertiary = AstralViolet,
        onTertiary = VoidIndigo,
        tertiaryContainer = Color(0xFF2D1E63),
        onTertiaryContainer = Color(0xFFE5DBFF),
        background = VoidIndigo,
        onBackground = StarlightWhite,
        surface = DeepSpaceNavy,
        onSurface = StarlightWhite,
        surfaceVariant = ConstellationSurface,
        onSurfaceVariant = NebulaSilver,
        surfaceContainerLowest = VoidIndigo,
        surfaceContainerLow = ObsidianNebula,
        surfaceContainer = ConstellationSurface,
        surfaceContainerHigh = GalaxyElevated,
        surfaceContainerHighest = Color(0xFF213780),
        outline = Color(0xFF2E4994),
        outlineVariant = Color(0xFF1A2D63),
        error = NovaCoral,
        onError = VoidIndigo
    )
    CosmicThemeSkin.ASTRAL_VIOLET -> darkColorScheme(
        primary = Color(0xFFB48CFF),
        onPrimary = Color(0xFF12062B),
        primaryContainer = Color(0xFF341E6B),
        onPrimaryContainer = Color(0xFFECE0FF),
        secondary = PhotonCyan,
        onSecondary = Color(0xFF081426),
        secondaryContainer = Color(0xFF1F225C),
        onSecondaryContainer = Color(0xFFDCEBFF),
        tertiary = Color(0xFFFF79C6),
        onTertiary = Color(0xFF24071B),
        tertiaryContainer = Color(0xFF4A1842),
        onTertiaryContainer = Color(0xFFFFD8EE),
        background = Color(0xFF0A051D),
        onBackground = Color(0xFFF3EEFF),
        surface = Color(0xFF120A2E),
        onSurface = Color(0xFFF3EEFF),
        surfaceVariant = Color(0xFF20144C),
        onSurfaceVariant = Color(0xFFC4B5E8),
        surfaceContainerLowest = Color(0xFF0A051D),
        surfaceContainerLow = Color(0xFF160D38),
        surfaceContainer = Color(0xFF21154D),
        surfaceContainerHigh = Color(0xFF2C1D63),
        surfaceContainerHighest = Color(0xFF39277A),
        outline = Color(0xFF4F3794),
        outlineVariant = Color(0xFF2E1E63),
        error = NovaCoral,
        onError = Color(0xFF12062B)
    )
    CosmicThemeSkin.SOLAR_GOLD -> darkColorScheme(
        primary = Color(0xFFFFCF5C),
        onPrimary = Color(0xFF1F1303),
        primaryContainer = Color(0xFF52380C),
        onPrimaryContainer = Color(0xFFFFEAB3),
        secondary = Color(0xFFFF8866),
        onSecondary = Color(0xFF240C04),
        secondaryContainer = Color(0xFF4A2516),
        onSecondaryContainer = Color(0xFFFFDDD0),
        tertiary = PhotonCyan,
        onTertiary = VoidIndigo,
        tertiaryContainer = Color(0xFF18364A),
        onTertiaryContainer = Color(0xFFD2F3FF),
        background = Color(0xFF100A05),
        onBackground = Color(0xFFFFF6E5),
        surface = Color(0xFF1A1108),
        onSurface = Color(0xFFFFF6E5),
        surfaceVariant = Color(0xFF2B1D0F),
        onSurfaceVariant = Color(0xFFD6C0A1),
        surfaceContainerLowest = Color(0xFF100A05),
        surfaceContainerLow = Color(0xFF1F150A),
        surfaceContainer = Color(0xFF2B1E10),
        surfaceContainerHigh = Color(0xFF382714),
        surfaceContainerHighest = Color(0xFF47331B),
        outline = Color(0xFF6E5026),
        outlineVariant = Color(0xFF3F2D15),
        error = NovaCoral,
        onError = Color(0xFF1F1303)
    )
    CosmicThemeSkin.EMERALD_NEBULA -> darkColorScheme(
        primary = Color(0xFF34E5A8),
        onPrimary = Color(0xFF031F16),
        primaryContainer = Color(0xFF0B4A37),
        onPrimaryContainer = Color(0xFFC2FFE8),
        secondary = Color(0xFF38BDF8),
        onSecondary = Color(0xFF041C2B),
        secondaryContainer = Color(0xFF10374F),
        onSecondaryContainer = Color(0xFFD0F0FF),
        tertiary = AstralViolet,
        onTertiary = VoidIndigo,
        tertiaryContainer = Color(0xFF2B2057),
        onTertiaryContainer = Color(0xFFE6DCFF),
        background = Color(0xFF041312),
        onBackground = Color(0xFFE8FFF8),
        surface = Color(0xFF071E1D),
        onSurface = Color(0xFFE8FFF8),
        surfaceVariant = Color(0xFF0E302E),
        onSurfaceVariant = Color(0xFFA2D6CA),
        surfaceContainerLowest = Color(0xFF041312),
        surfaceContainerLow = Color(0xFF082220),
        surfaceContainer = Color(0xFF0F312F),
        surfaceContainerHigh = Color(0xFF16403E),
        surfaceContainerHighest = Color(0xFF1E524E),
        outline = Color(0xFF266E67),
        outlineVariant = Color(0xFF133B38),
        error = NovaCoral,
        onError = Color(0xFF031F16)
    )
    CosmicThemeSkin.OLED_PURE_BLACK -> darkColorScheme(
        primary = PhotonCyan,
        onPrimary = Color(0xFF000000),
        primaryContainer = Color(0xFF082A3D),
        onPrimaryContainer = Color(0xFFC8F6FF),
        secondary = Color(0xFF8B7AFF),
        onSecondary = Color(0xFF000000),
        secondaryContainer = Color(0xFF141826),
        onSecondaryContainer = Color(0xFFDCE4FF),
        tertiary = LosslessEmerald,
        onTertiary = Color(0xFF000000),
        tertiaryContainer = Color(0xFF0B2E24),
        onTertiaryContainer = Color(0xFFC9FFE9),
        background = Color(0xFF000000),
        onBackground = Color(0xFFF4F8FF),
        surface = Color(0xFF000000),
        onSurface = Color(0xFFF4F8FF),
        surfaceVariant = Color(0xFF0D111A),
        onSurfaceVariant = Color(0xFFA0AEC6),
        surfaceContainerLowest = Color(0xFF000000),
        surfaceContainerLow = Color(0xFF07090F),
        surfaceContainer = Color(0xFF0D111A),
        surfaceContainerHigh = Color(0xFF131926),
        surfaceContainerHighest = Color(0xFF1B2334),
        outline = Color(0xFF27344D),
        outlineVariant = Color(0xFF141C2B),
        error = NovaCoral,
        onError = Color(0xFF000000)
    )
    CosmicThemeSkin.CUSTOM -> {
        val bg = if (customOledBlack) Color.Black else lerp(Color(0xFF060916), customPrimary, 0.06f)
        val surf = if (customOledBlack) Color.Black else lerp(Color(0xFF0B1126), customPrimary, 0.08f)
        val container = if (customOledBlack) Color(0xFF0D111A) else lerp(Color(0xFF121C3A), customPrimary, 0.12f)
        darkColorScheme(
            primary = customPrimary,
            onPrimary = Color(0xFF050814),
            primaryContainer = lerp(Color(0xFF101930), customPrimary, 0.28f),
            onPrimaryContainer = Color(0xFFF4F8FF),
            secondary = customSecondary,
            onSecondary = Color(0xFF050814),
            secondaryContainer = lerp(Color(0xFF151C36), customSecondary, 0.25f),
            onSecondaryContainer = Color(0xFFF0F4FF),
            tertiary = LosslessEmerald,
            onTertiary = Color(0xFF031F16),
            tertiaryContainer = Color(0xFF0E382C),
            onTertiaryContainer = Color(0xFFD0FFEC),
            background = bg,
            onBackground = Color(0xFFF4F8FF),
            surface = surf,
            onSurface = Color(0xFFF4F8FF),
            surfaceVariant = container,
            onSurfaceVariant = Color(0xFFB4C3DE),
            surfaceContainerLowest = bg,
            surfaceContainerLow = if (customOledBlack) Color(0xFF080A12) else lerp(bg, container, 0.45f),
            surfaceContainer = container,
            surfaceContainerHigh = lerp(container, Color.White, 0.06f),
            surfaceContainerHighest = lerp(container, Color.White, 0.12f),
            outline = lerp(Color(0xFF2A3B63), customPrimary, 0.35f),
            outlineVariant = lerp(Color(0xFF18223D), customPrimary, 0.18f),
            error = NovaCoral,
            onError = Color(0xFF050814)
        )
    }
}

private val LightAstralColorScheme = lightColorScheme(
    primary = RoyalSapphire,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E4FF),
    onPrimaryContainer = Color(0xFF071D6B),
    secondary = DeepPhotonTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFF2FF),
    onSecondaryContainer = Color(0xFF00354A),
    tertiary = DeepAstralViolet,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE8DEFF),
    onTertiaryContainer = Color(0xFF230C69),
    background = AstralIce,
    onBackground = AstralInk,
    surface = AstralSurface,
    onSurface = AstralInk,
    surfaceVariant = AstralElevated,
    onSurfaceVariant = AstralSlate,
    surfaceContainerLowest = AstralCard,
    surfaceContainerLow = Color(0xFFF7FAFF),
    surfaceContainer = AstralSurface,
    surfaceContainerHigh = AstralElevated,
    surfaceContainerHighest = Color(0xFFCDE0FF),
    outline = Color(0xFF8AA4DB),
    outlineVariant = AstralBorder,
    error = Color(0xFFCC2854),
    onError = Color.White
)

@Composable
fun PhotonAudioTheme(
    darkTheme: Boolean = true,
    cosmicSkin: CosmicThemeSkin = CosmicThemeSkin.PHOTON_CYAN,
    customPrimaryArgb: Long = 0xFF38E4FF,
    customSecondaryArgb: Long = 0xFF9D7BFF,
    customOledBlack: Boolean = false,
    fontChoice: AppFontChoice = AppFontChoice.PUBLIC_SANS,
    wallpaperConfig: PhotonWallpaperConfig = PhotonWallpaperConfig(),
    content: @Composable () -> Unit
) {
    val customPrimary = Color(customPrimaryArgb)
    val customSecondary = Color(customSecondaryArgb)
    val colorScheme = if (darkTheme) {
        darkColorSchemeForSkin(cosmicSkin, customPrimary, customSecondary, customOledBlack)
    } else {
        LightAstralColorScheme
    }
    val extendedColors = if (darkTheme) {
        darkExtendedForSkin(cosmicSkin, customPrimary, customSecondary, customOledBlack)
    } else {
        LightPhotonExtended
    }

    CompositionLocalProvider(
        LocalPhotonColors provides extendedColors,
        LocalPhotonWallpaper provides wallpaperConfig
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = photonTypographyFor(fontChoice),
            content = content
        )
    }
}
