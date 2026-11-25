package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val PublicSansFontFamily = FontFamily(
    Font(R.font.public_sans, FontWeight.Normal),
    Font(R.font.public_sans, FontWeight.Medium),
    Font(R.font.public_sans, FontWeight.SemiBold),
    Font(R.font.public_sans, FontWeight.Bold)
)

val SpaceGroteskFontFamily = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Normal),
    Font(R.font.space_grotesk, FontWeight.Medium),
    Font(R.font.space_grotesk, FontWeight.SemiBold),
    Font(R.font.space_grotesk, FontWeight.Bold)
)

val OutfitFontFamily = FontFamily(
    Font(R.font.outfit, FontWeight.Normal),
    Font(R.font.outfit, FontWeight.Medium),
    Font(R.font.outfit, FontWeight.SemiBold),
    Font(R.font.outfit, FontWeight.Bold)
)

val PlayfairDisplayFontFamily = FontFamily(
    Font(R.font.playfair_display, FontWeight.Normal),
    Font(R.font.playfair_display, FontWeight.Medium),
    Font(R.font.playfair_display, FontWeight.SemiBold),
    Font(R.font.playfair_display, FontWeight.Bold)
)

val JetBrainsMonoFontFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium),
    Font(R.font.jetbrains_mono, FontWeight.SemiBold),
    Font(R.font.jetbrains_mono, FontWeight.Bold)
)

val OrbitronFontFamily = FontFamily(
    Font(R.font.orbitron, FontWeight.Normal),
    Font(R.font.orbitron, FontWeight.Medium),
    Font(R.font.orbitron, FontWeight.SemiBold),
    Font(R.font.orbitron, FontWeight.Bold)
)

val PlusJakartaSansFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold)
)

enum class AppFontChoice(
    val displayName: String,
    val subtitle: String
) {
    PUBLIC_SANS("Public Sans", "Clean balanced studio sans"),
    ORBITRON("Orbitron", "Cyber deck geometric display"),
    SPACE_GROTESK("Space Grotesk", "Futuristic geometric tech"),
    JETBRAINS_MONO("JetBrains Mono", "Precision DSP monospace"),
    PLUS_JAKARTA_SANS("Plus Jakarta Sans", "Refined modern editorial sans"),
    OUTFIT("Outfit", "Sculpted minimalist hi-fi"),
    PLAYFAIR_DISPLAY("Playfair Display", "Editorial audiophile serif");

    val fontFamily: FontFamily
        get() = when (this) {
            PUBLIC_SANS -> PublicSansFontFamily
            ORBITRON -> OrbitronFontFamily
            SPACE_GROTESK -> SpaceGroteskFontFamily
            JETBRAINS_MONO -> JetBrainsMonoFontFamily
            PLUS_JAKARTA_SANS -> PlusJakartaSansFontFamily
            OUTFIT -> OutfitFontFamily
            PLAYFAIR_DISPLAY -> PlayfairDisplayFontFamily
        }
}

enum class DeckStylePreset(
    val displayName: String,
    val subtitle: String,
    val displayFont: AppFontChoice,
    val bodyFont: AppFontChoice,
    val recommendedSkin: CosmicThemeSkin,
    val darkMode: Boolean = true
) {
    CYBER_STUDIO(
        displayName = "Cyber Studio",
        subtitle = "Orbitron telemetry + JetBrains Mono · Photon Cyan",
        displayFont = AppFontChoice.ORBITRON,
        bodyFont = AppFontChoice.JETBRAINS_MONO,
        recommendedSkin = CosmicThemeSkin.PHOTON_CYAN
    ),
    MINIMALIST_HIFI(
        displayName = "Minimalist Hi-Fi",
        subtitle = "Space Grotesk + Outfit · AMOLED Pure Black",
        displayFont = AppFontChoice.SPACE_GROTESK,
        bodyFont = AppFontChoice.OUTFIT,
        recommendedSkin = CosmicThemeSkin.OLED_PURE_BLACK
    ),
    EDITORIAL(
        displayName = "Editorial",
        subtitle = "Playfair Display + Plus Jakarta Sans · Solar Gold",
        displayFont = AppFontChoice.PLAYFAIR_DISPLAY,
        bodyFont = AppFontChoice.PLUS_JAKARTA_SANS,
        recommendedSkin = CosmicThemeSkin.SOLAR_GOLD
    )
}

fun photonTypographyFor(
    fontChoice: AppFontChoice,
    deckPreset: DeckStylePreset? = null
): Typography {
    val displayFamily = deckPreset?.displayFont?.fontFamily ?: fontChoice.fontFamily
    val bodyFamily = deckPreset?.bodyFont?.fontFamily ?: fontChoice.fontFamily
    val monoLabelFamily = if (deckPreset == DeckStylePreset.CYBER_STUDIO) {
        JetBrainsMonoFontFamily
    } else {
        bodyFamily
    }
    return Typography(
        displaySmall = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 30.sp,
            lineHeight = 36.sp,
            letterSpacing = (-0.25).sp
        ),
        headlineMedium = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            lineHeight = 30.sp,
            letterSpacing = (-0.15).sp
        ),
        headlineSmall = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = 26.sp,
            letterSpacing = 0.sp
        ),
        titleLarge = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            lineHeight = 26.sp,
            letterSpacing = 0.sp
        ),
        titleMedium = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.1.sp
        ),
        titleSmall = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp
        ),
        bodyLarge = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.15.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.15.sp
        ),
        bodySmall = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.2.sp
        ),
        labelLarge = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp
        ),
        labelMedium = TextStyle(
            fontFamily = monoLabelFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.25.sp
        ),
        labelSmall = TextStyle(
            fontFamily = monoLabelFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            letterSpacing = 0.3.sp
        )
    )
}

val Typography: Typography by lazy { photonTypographyFor(AppFontChoice.PUBLIC_SANS) }
