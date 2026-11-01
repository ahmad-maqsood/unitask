package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The accent colour choices in Settings. Each one has a light and a dark colour scheme.
 * Error red, priority chips and course colours are NOT themed on purpose, so their meaning never changes.
 * `swatch` is the circle shown in the Settings picker.
 * Every text/background pair was checked for WCAG AA contrast (4.5:1 for text, 3:1 for outlines).
 */
enum class AccentTheme(val label: String, val swatch: Color) {
    INDIGO("Indigo", Color(0xFF4F46E5)),
    OCEAN("Ocean teal", Color(0xFF157984)),
    FOREST("Forest green", Color(0xFF157F46)),
    ROSE("Rose", Color(0xFFD32263)),
    SUNSET("Sunset orange", Color(0xFFB2531D)),
    VIOLET("Violet", Color(0xFF8F47E1))
}

fun lightSchemeFor(accent: AccentTheme): ColorScheme = when (accent) {
    AccentTheme.INDIGO -> IndigoLightScheme
    AccentTheme.OCEAN -> OceanLightScheme
    AccentTheme.FOREST -> ForestLightScheme
    AccentTheme.ROSE -> RoseLightScheme
    AccentTheme.SUNSET -> SunsetLightScheme
    AccentTheme.VIOLET -> VioletLightScheme
}

fun darkSchemeFor(accent: AccentTheme): ColorScheme = when (accent) {
    AccentTheme.INDIGO -> IndigoDarkScheme
    AccentTheme.OCEAN -> OceanDarkScheme
    AccentTheme.FOREST -> ForestDarkScheme
    AccentTheme.ROSE -> RoseDarkScheme
    AccentTheme.SUNSET -> SunsetDarkScheme
    AccentTheme.VIOLET -> VioletDarkScheme
}

// Indigo is the original look. It uses the colours already defined in Color.kt.
private val IndigoDarkScheme = darkColorScheme(
    primary = IndigoPrimaryDark, onPrimary = IndigoOnPrimaryDark,
    primaryContainer = IndigoPrimaryContainerDark, onPrimaryContainer = IndigoOnPrimaryContainerDark,
    secondary = SkySecondaryDark, onSecondary = SkyOnSecondaryDark,
    secondaryContainer = SkySecondaryContainerDark, onSecondaryContainer = SkyOnSecondaryContainerDark,
    tertiary = AmberTertiaryDark, onTertiary = AmberOnTertiaryDark,
    tertiaryContainer = AmberTertiaryContainerDark, onTertiaryContainer = AmberOnTertiaryContainerDark,
    error = ErrorDark, onError = OnErrorDark, errorContainer = ErrorContainerDark, onErrorContainer = OnErrorContainerDark,
    background = BackgroundDark, onBackground = OnBackgroundDark,
    surface = SurfaceDark, onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark, onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark, outlineVariant = OutlineVariantDark,
    surfaceContainerLowest = SurfaceContainerLowestDark, surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainer = SurfaceContainerDark, surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = SurfaceContainerHighestDark
)

private val IndigoLightScheme = lightColorScheme(
    primary = IndigoPrimaryLight, onPrimary = IndigoOnPrimaryLight,
    primaryContainer = IndigoPrimaryContainerLight, onPrimaryContainer = IndigoOnPrimaryContainerLight,
    secondary = SkySecondaryLight, onSecondary = SkyOnSecondaryLight,
    secondaryContainer = SkySecondaryContainerLight, onSecondaryContainer = SkyOnSecondaryContainerLight,
    tertiary = AmberTertiaryLight, onTertiary = AmberOnTertiaryLight,
    tertiaryContainer = AmberTertiaryContainerLight, onTertiaryContainer = AmberOnTertiaryContainerLight,
    error = ErrorLight, onError = OnErrorLight, errorContainer = ErrorContainerLight, onErrorContainer = OnErrorContainerLight,
    background = BackgroundLight, onBackground = OnBackgroundLight,
    surface = SurfaceLight, onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight, onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight, outlineVariant = OutlineVariantLight,
    surfaceContainerLowest = SurfaceContainerLowestLight, surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainer = SurfaceContainerLight, surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighestLight
)

// ---------- Ocean teal ----------
private val OceanLightScheme = lightColorScheme(
        primary = Color(0xFF157984),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFDEF9FC),
        onPrimaryContainer = Color(0xFF104146),
        secondary = Color(0xFF2E6FC4),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFDEEBFC),
        onSecondaryContainer = Color(0xFF102846),
        tertiary = Color(0xFFBF4824),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFCE5DE),
        onTertiaryContainer = Color(0xFF461D10),
        error = ErrorLight,
        onError = OnErrorLight,
        errorContainer = ErrorContainerLight,
        onErrorContainer = OnErrorContainerLight,
        background = Color(0xFFF7FAFA),
        onBackground = Color(0xFF162225),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF162225),
        surfaceVariant = Color(0xFFEAF2F4),
        onSurfaceVariant = Color(0xFF4F666B),
        outline = Color(0xFF789096),
        outlineVariant = Color(0xFFD7E3E6),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFFAFCFC),
        surfaceContainer = Color(0xFFF2F7F8),
        surfaceContainerHigh = Color(0xFFECF3F5),
        surfaceContainerHighest = Color(0xFFE6EEF0)
    )

private val OceanDarkScheme = darkColorScheme(
        primary = Color(0xFF24C9DB),
        onPrimary = Color(0xFF0E3539),
        primaryContainer = Color(0xFF224E53),
        onPrimaryContainer = Color(0xFFD6FBFF),
        secondary = Color(0xFF7CA7DE),
        onSecondary = Color(0xFF0E2139),
        secondaryContainer = Color(0xFF223753),
        onSecondaryContainer = Color(0xFFD6E8FF),
        tertiary = Color(0xFFE29178),
        onTertiary = Color(0xFF39180E),
        tertiaryContainer = Color(0xFF532E22),
        onTertiaryContainer = Color(0xFFFFE0D6),
        error = ErrorDark,
        onError = OnErrorDark,
        errorContainer = ErrorContainerDark,
        onErrorContainer = OnErrorContainerDark,
        background = Color(0xFF111B1E),
        onBackground = Color(0xFFEAF2F4),
        surface = Color(0xFF182629),
        onSurface = Color(0xFFEAF2F4),
        surfaceVariant = Color(0xFF27393D),
        onSurfaceVariant = Color(0xFFADC2C8),
        outline = Color(0xFF708A90),
        outlineVariant = Color(0xFF31484D),
        surfaceContainerLowest = Color(0xFF0D1618),
        surfaceContainerLow = Color(0xFF162225),
        surfaceContainer = Color(0xFF1C2C30),
        surfaceContainerHigh = Color(0xFF243539),
        surfaceContainerHighest = Color(0xFF2B3F44)
    )

// ---------- Forest green ----------
private val ForestLightScheme = lightColorScheme(
        primary = Color(0xFF157F46),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFDEFCEC),
        onPrimaryContainer = Color(0xFF10462A),
        secondary = Color(0xFF4E7A1D),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFEEFCDE),
        onSecondaryContainer = Color(0xFF2D4610),
        tertiary = Color(0xFF9F5E1E),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFCEDDE),
        onTertiaryContainer = Color(0xFF462B10),
        error = ErrorLight,
        onError = OnErrorLight,
        errorContainer = ErrorContainerLight,
        onErrorContainer = OnErrorContainerLight,
        background = Color(0xFFF7FAF8),
        onBackground = Color(0xFF18231E),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF18231E),
        surfaceVariant = Color(0xFFECF2EF),
        onSurfaceVariant = Color(0xFF53675D),
        outline = Color(0xFF7B9186),
        outlineVariant = Color(0xFFD9E4DE),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFFAFCFB),
        surfaceContainer = Color(0xFFF3F7F5),
        surfaceContainerHigh = Color(0xFFEDF4F0),
        surfaceContainerHighest = Color(0xFFE7EFEB)
    )

private val ForestDarkScheme = darkColorScheme(
        primary = Color(0xFF22D375),
        onPrimary = Color(0xFF0E3922),
        primaryContainer = Color(0xFF225339),
        onPrimaryContainer = Color(0xFFD6FFE9),
        secondary = Color(0xFF88CE39),
        onSecondary = Color(0xFF25390E),
        secondaryContainer = Color(0xFF3C5322),
        onSecondaryContainer = Color(0xFFECFFD6),
        tertiary = Color(0xFFDEA163),
        onTertiary = Color(0xFF39240E),
        tertiaryContainer = Color(0xFF533B22),
        onTertiaryContainer = Color(0xFFFFEBD6),
        error = ErrorDark,
        onError = OnErrorDark,
        errorContainer = ErrorContainerDark,
        onErrorContainer = OnErrorContainerDark,
        background = Color(0xFF131C18),
        onBackground = Color(0xFFEBF3EF),
        surface = Color(0xFF1A2720),
        onSurface = Color(0xFFEBF3EF),
        surfaceVariant = Color(0xFF2A3A32),
        onSurfaceVariant = Color(0xFFB1C4BB),
        outline = Color(0xFF748C80),
        outlineVariant = Color(0xFF35493F),
        surfaceContainerLowest = Color(0xFF0E1712),
        surfaceContainerLow = Color(0xFF18231E),
        surfaceContainer = Color(0xFF1F2D26),
        surfaceContainerHigh = Color(0xFF27362F),
        surfaceContainerHighest = Color(0xFF2E4138)
    )

// ---------- Rose ----------
private val RoseLightScheme = lightColorScheme(
        primary = Color(0xFFD32263),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFCDEE9),
        onPrimaryContainer = Color(0xFF461024),
        secondary = Color(0xFFA53DD1),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFF3DEFC),
        onSecondaryContainer = Color(0xFF361046),
        tertiary = Color(0xFFAB5821),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFCEADE),
        onTertiaryContainer = Color(0xFF462610),
        error = ErrorLight,
        onError = OnErrorLight,
        errorContainer = ErrorContainerLight,
        onErrorContainer = OnErrorContainerLight,
        background = Color(0xFFFBF6F8),
        onBackground = Color(0xFF26151B),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF26151B),
        surfaceVariant = Color(0xFFF4EAED),
        onSurfaceVariant = Color(0xFF6C4E58),
        outline = Color(0xFF9D7E88),
        outlineVariant = Color(0xFFE6D7DC),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFFDF9FA),
        surfaceContainer = Color(0xFFF8F2F4),
        surfaceContainerHigh = Color(0xFFF5ECEF),
        surfaceContainerHighest = Color(0xFFF1E5E9)
    )

private val RoseDarkScheme = darkColorScheme(
        primary = Color(0xFFEB82A8),
        onPrimary = Color(0xFF390E1E),
        primaryContainer = Color(0xFF532234),
        onPrimaryContainer = Color(0xFFFFD6E5),
        secondary = Color(0xFFC789E1),
        onSecondary = Color(0xFF2C0E39),
        secondaryContainer = Color(0xFF452253),
        onSecondaryContainer = Color(0xFFF3D6FF),
        tertiary = Color(0xFFE09A6C),
        onTertiary = Color(0xFF391F0E),
        tertiaryContainer = Color(0xFF533622),
        onTertiaryContainer = Color(0xFFFFE7D6),
        error = ErrorDark,
        onError = OnErrorDark,
        errorContainer = ErrorContainerDark,
        onErrorContainer = OnErrorContainerDark,
        background = Color(0xFF1E1115),
        onBackground = Color(0xFFF5E9ED),
        surface = Color(0xFF2A171D),
        onSurface = Color(0xFFF5E9ED),
        surfaceVariant = Color(0xFF3E262E),
        onSurfaceVariant = Color(0xFFC9ACB6),
        outline = Color(0xFF926E7A),
        outlineVariant = Color(0xFF4E303A),
        surfaceContainerLowest = Color(0xFF180C10),
        surfaceContainerLow = Color(0xFF26151B),
        surfaceContainer = Color(0xFF301C23),
        surfaceContainerHigh = Color(0xFF3A232B),
        surfaceContainerHighest = Color(0xFF452A33)
    )

// ---------- Sunset orange ----------
private val SunsetLightScheme = lightColorScheme(
        primary = Color(0xFFB2531D),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFCE9DE),
        onPrimaryContainer = Color(0xFF462410),
        secondary = Color(0xFFCC3064),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFFCDEE8),
        onSecondaryContainer = Color(0xFF461022),
        tertiary = Color(0xFF8E49DC),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFECDEFC),
        onTertiaryContainer = Color(0xFF2A1046),
        error = ErrorLight,
        onError = OnErrorLight,
        errorContainer = ErrorContainerLight,
        onErrorContainer = OnErrorContainerLight,
        background = Color(0xFFFAF8F7),
        onBackground = Color(0xFF231C18),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF231C18),
        surfaceVariant = Color(0xFFF2EEEC),
        onSurfaceVariant = Color(0xFF665B54),
        outline = Color(0xFF958981),
        outlineVariant = Color(0xFFE3DEDA),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFFCFBFA),
        surfaceContainer = Color(0xFFF7F5F3),
        surfaceContainerHigh = Color(0xFFF3F0EE),
        surfaceContainerHighest = Color(0xFFEFEAE8)
    )

private val SunsetDarkScheme = darkColorScheme(
        primary = Color(0xFFE69668),
        onPrimary = Color(0xFF391E0E),
        primaryContainer = Color(0xFF533422),
        onPrimaryContainer = Color(0xFFFFE5D6),
        secondary = Color(0xFFE189A6),
        onSecondary = Color(0xFF390E1D),
        secondaryContainer = Color(0xFF532232),
        onSecondaryContainer = Color(0xFFFFD6E4),
        tertiary = Color(0xFFBA92E8),
        onTertiary = Color(0xFF220E39),
        tertiaryContainer = Color(0xFF392253),
        onTertiaryContainer = Color(0xFFE9D6FF),
        error = ErrorDark,
        onError = OnErrorDark,
        errorContainer = ErrorContainerDark,
        onErrorContainer = OnErrorContainerDark,
        background = Color(0xFF1C1713),
        onBackground = Color(0xFFF2EEEC),
        surface = Color(0xFF261F1B),
        onSurface = Color(0xFFF2EEEC),
        surfaceVariant = Color(0xFF39302B),
        onSurfaceVariant = Color(0xFFC3B9B2),
        outline = Color(0xFF8B7E75),
        outlineVariant = Color(0xFF483D36),
        surfaceContainerLowest = Color(0xFF16120F),
        surfaceContainerLow = Color(0xFF231C18),
        surfaceContainer = Color(0xFF2C2520),
        surfaceContainerHigh = Color(0xFF362D27),
        surfaceContainerHighest = Color(0xFF40362F)
    )

// ---------- Violet ----------
private val VioletLightScheme = lightColorScheme(
        primary = Color(0xFF8F47E1),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFECDEFC),
        onPrimaryContainer = Color(0xFF2A1046),
        secondary = Color(0xFFC42E92),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFFCDEF2),
        onSecondaryContainer = Color(0xFF461034),
        tertiary = Color(0xFF187C6F),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFDEFCF8),
        onTertiaryContainer = Color(0xFF10463F),
        error = ErrorLight,
        onError = OnErrorLight,
        errorContainer = ErrorContainerLight,
        onErrorContainer = OnErrorContainerLight,
        background = Color(0xFFF8F6FB),
        onBackground = Color(0xFF1C1427),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF1C1427),
        surfaceVariant = Color(0xFFEEEAF4),
        onSurfaceVariant = Color(0xFF5B4C6E),
        outline = Color(0xFF8B7C9F),
        outlineVariant = Color(0xFFDDD6E7),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFFBF9FD),
        surfaceContainer = Color(0xFFF5F1F9),
        surfaceContainerHigh = Color(0xFFF0ECF5),
        surfaceContainerHighest = Color(0xFFEAE5F1)
    )

private val VioletDarkScheme = darkColorScheme(
        primary = Color(0xFFB88BEC),
        onPrimary = Color(0xFF220E39),
        primaryContainer = Color(0xFF392253),
        onPrimaryContainer = Color(0xFFE9D6FF),
        secondary = Color(0xFFE085C2),
        onSecondary = Color(0xFF390E2B),
        secondaryContainer = Color(0xFF532243),
        onSecondaryContainer = Color(0xFFFFD6F1),
        tertiary = Color(0xFF2CD0BA),
        onTertiary = Color(0xFF0E3933),
        tertiaryContainer = Color(0xFF22534D),
        onTertiaryContainer = Color(0xFFD6FFFA),
        error = ErrorDark,
        onError = OnErrorDark,
        errorContainer = ErrorContainerDark,
        onErrorContainer = OnErrorContainerDark,
        background = Color(0xFF16101F),
        onBackground = Color(0xFFEEE9F5),
        surface = Color(0xFF1F162B),
        onSurface = Color(0xFFEEE9F5),
        surfaceVariant = Color(0xFF302440),
        onSurfaceVariant = Color(0xFFB8ABCA),
        outline = Color(0xFF7D6C94),
        outlineVariant = Color(0xFF3D2E50),
        surfaceContainerLowest = Color(0xFF120C19),
        surfaceContainerLow = Color(0xFF1C1427),
        surfaceContainer = Color(0xFF241A32),
        surfaceContainerHigh = Color(0xFF2D213C),
        surfaceContainerHighest = Color(0xFF362946)
    )
