package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

/**
 * Plus Jakarta Sans (SIL Open Font License, see docs/licenses). The four weights below are the
 * only ones bundled, so any other weight asked for in the UI is shown as the nearest of these.
 */
val AppFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold)
)

private fun TextStyle.inAppFont(letterSpacing: Float? = null): TextStyle =
    copy(
        fontFamily = AppFontFamily,
        letterSpacing = if (letterSpacing != null) letterSpacing.sp else this.letterSpacing
    )

/**
 * The standard Material 3 text styles, set in the app font. Sizes and line heights are the
 * Material defaults. Only the letter spacing of the body styles is tightened a little, because
 * the Material defaults are tuned for Roboto and look loose in this wider font.
 */
private val DefaultTypography = Typography()

val Typography = Typography(
    displayLarge = DefaultTypography.displayLarge.inAppFont(),
    displayMedium = DefaultTypography.displayMedium.inAppFont(),
    displaySmall = DefaultTypography.displaySmall.inAppFont(),
    headlineLarge = DefaultTypography.headlineLarge.inAppFont(),
    headlineMedium = DefaultTypography.headlineMedium.inAppFont(),
    headlineSmall = DefaultTypography.headlineSmall.inAppFont(),
    titleLarge = DefaultTypography.titleLarge.inAppFont(),
    titleMedium = DefaultTypography.titleMedium.inAppFont(),
    titleSmall = DefaultTypography.titleSmall.inAppFont(),
    bodyLarge = DefaultTypography.bodyLarge.inAppFont(letterSpacing = 0.2f),
    bodyMedium = DefaultTypography.bodyMedium.inAppFont(letterSpacing = 0.1f),
    bodySmall = DefaultTypography.bodySmall.inAppFont(letterSpacing = 0.2f),
    labelLarge = DefaultTypography.labelLarge.inAppFont(),
    labelMedium = DefaultTypography.labelMedium.inAppFont(),
    labelSmall = DefaultTypography.labelSmall.inAppFont()
)
