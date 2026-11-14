package com.example.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * One place for the accent colour used OUTSIDE the app screens: notifications and the home screen widget.
 * It reads the accent saved in Settings straight from the phone's storage, so it also works when a
 * notification or the widget wakes up before the app screens are open.
 */
object AccentColors {
    private const val PREFS = "unitask_settings"
    private const val ACCENT_KEY = "theme_accent"

    /** The accent the person picked in Settings. Indigo if nothing was saved yet. */
    fun current(context: Context): AccentTheme {
        val saved = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(ACCENT_KEY, null)
        return AccentTheme.entries.firstOrNull { it.name == saved } ?: AccentTheme.INDIGO
    }

    /** Colour for notifications (the small icon and app name). Same as the app's main colour in light mode. */
    fun notificationColor(context: Context): Int =
        lightSchemeFor(current(context)).primary.toArgb()

    /** Colours for the widget: [lightColor] for day mode, [darkColor] for night mode. */
    fun widgetLightColor(context: Context): Color =
        lightSchemeFor(current(context)).primary

    fun widgetDarkColor(context: Context): Color =
        darkSchemeFor(current(context)).primary
}
