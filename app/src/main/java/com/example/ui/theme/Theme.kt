package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

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

private val DarkPhotonExtended = PhotonExtendedColors(
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

val LocalPhotonColors = staticCompositionLocalOf { DarkPhotonExtended }

private val DarkGalaxyColorScheme = darkColorScheme(
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
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkGalaxyColorScheme else LightAstralColorScheme
    val extendedColors = if (darkTheme) DarkPhotonExtended else LightPhotonExtended

    CompositionLocalProvider(LocalPhotonColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
