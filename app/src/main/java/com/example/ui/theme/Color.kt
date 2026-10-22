package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Palette: indigo primary, sky secondary, amber tertiary, on cool neutral surfaces.
// Every text/background pair below meets WCAG AA (4.5:1 for text, 3:1 for outlines).

// ---------- Light ----------
val IndigoPrimaryLight = Color(0xFF4F46E5)
val IndigoOnPrimaryLight = Color(0xFFFFFFFF)
val IndigoPrimaryContainerLight = Color(0xFFE3E6FF)
val IndigoOnPrimaryContainerLight = Color(0xFF1E1B4B)

val SkySecondaryLight = Color(0xFF0369A1)
val SkyOnSecondaryLight = Color(0xFFFFFFFF)
val SkySecondaryContainerLight = Color(0xFFDDF1FD)
val SkyOnSecondaryContainerLight = Color(0xFF0C3A57)

val AmberTertiaryLight = Color(0xFFB45309)
val AmberOnTertiaryLight = Color(0xFFFFFFFF)
val AmberTertiaryContainerLight = Color(0xFFFDF0D2)
val AmberOnTertiaryContainerLight = Color(0xFF5A3305)

val ErrorLight = Color(0xFFDC2626)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFDE4E4)
val OnErrorContainerLight = Color(0xFF7F1D1D)

val BackgroundLight = Color(0xFFF6F7FB)
val OnBackgroundLight = Color(0xFF131728)
val SurfaceLight = Color(0xFFFFFFFF)
val OnSurfaceLight = Color(0xFF131728)
val SurfaceVariantLight = Color(0xFFE9ECF5)
val OnSurfaceVariantLight = Color(0xFF4A5170)
val OutlineLight = Color(0xFF7A82A1)
val OutlineVariantLight = Color(0xFFD5D9E8)
val SurfaceContainerLowestLight = Color(0xFFFFFFFF)
val SurfaceContainerLowLight = Color(0xFFF9FAFD)
val SurfaceContainerLight = Color(0xFFF1F3F9)
val SurfaceContainerHighLight = Color(0xFFEBEEF6)
val SurfaceContainerHighestLight = Color(0xFFE4E8F2)

// ---------- Dark ----------
val IndigoPrimaryDark = Color(0xFF9AA8FF)
val IndigoOnPrimaryDark = Color(0xFF12163D)
val IndigoPrimaryContainerDark = Color(0xFF2A2F63)
val IndigoOnPrimaryContainerDark = Color(0xFFDDE2FF)

val SkySecondaryDark = Color(0xFF7DD3FC)
val SkyOnSecondaryDark = Color(0xFF082F49)
val SkySecondaryContainerDark = Color(0xFF123C57)
val SkyOnSecondaryContainerDark = Color(0xFFD6F1FF)

val AmberTertiaryDark = Color(0xFFFCD34D)
val AmberOnTertiaryDark = Color(0xFF451A03)
val AmberTertiaryContainerDark = Color(0xFF473512)
val AmberOnTertiaryContainerDark = Color(0xFFFDEFC6)

val ErrorDark = Color(0xFFF87171)
val OnErrorDark = Color(0xFF450A0A)
val ErrorContainerDark = Color(0xFF4C1D1D)
val OnErrorContainerDark = Color(0xFFFFDAD6)

val BackgroundDark = Color(0xFF0F1220)
val OnBackgroundDark = Color(0xFFE8EAF6)
val SurfaceDark = Color(0xFF151A2C)
val OnSurfaceDark = Color(0xFFE8EAF6)
val SurfaceVariantDark = Color(0xFF232A41)
val OnSurfaceVariantDark = Color(0xFFA9B0CC)
val OutlineDark = Color(0xFF6A7396)
val OutlineVariantDark = Color(0xFF2C3452)
val SurfaceContainerLowestDark = Color(0xFF0B0E1A)
val SurfaceContainerLowDark = Color(0xFF131828)
val SurfaceContainerDark = Color(0xFF191F33)
val SurfaceContainerHighDark = Color(0xFF20273D)
val SurfaceContainerHighestDark = Color(0xFF272F48)

// ---------- Priority and status chips ----------
// Soft tinted background with a readable foreground, tuned separately for light and dark
// so chips are not bright pastel blocks on a dark screen.
val PriorityHighBgLight = Color(0xFFFEE2E2)
val PriorityHighFgLight = Color(0xFFB91C1C)
val PriorityMediumBgLight = Color(0xFFFEF3C7)
val PriorityMediumFgLight = Color(0xFF92400E)
val PriorityLowBgLight = Color(0xFFD1FAE5)
val PriorityLowFgLight = Color(0xFF047857)

val PriorityHighBgDark = Color(0xFF3A1D26)
val PriorityHighFgDark = Color(0xFFFCA5A5)
val PriorityMediumBgDark = Color(0xFF3A2F14)
val PriorityMediumFgDark = Color(0xFFFCD34D)
val PriorityLowBgDark = Color(0xFF12372E)
val PriorityLowFgDark = Color(0xFF6EE7B7)

// "Done" and "class in progress" reuse the green pair.
val StatusDoneBgLight = PriorityLowBgLight
val StatusDoneFgLight = PriorityLowFgLight
val StatusDoneBgDark = PriorityLowBgDark
val StatusDoneFgDark = PriorityLowFgDark

// Solid green with white text (5.5:1) for the "class in progress" banner.
val ActiveClassGreen = Color(0xFF047857)

// Palette of course color choices
val CourseColors = listOf(
    "#4F46E5", // Indigo
    "#059669", // Emerald
    "#D97706", // Amber
    "#DC2626", // Red
    "#9333EA", // Purple
    "#0891B2", // Cyan
    "#2563EB", // Blue
    "#DB2777"  // Pink
)
