package com.example.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

/** Remembers the Light / Dark / System choice and the accent colour on the phone. */
object ThemePreference {
    private const val PREFS = "unitask_settings"
    private const val KEY = "theme_mode"
    private const val ACCENT_KEY = "theme_accent"

    var mode by mutableStateOf(ThemeMode.SYSTEM)
        private set

    var accent by mutableStateOf(AccentTheme.INDIGO)
        private set

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val savedMode = prefs.getString(KEY, null)
        mode = ThemeMode.entries.firstOrNull { it.name == savedMode } ?: ThemeMode.SYSTEM
        val savedAccent = prefs.getString(ACCENT_KEY, null)
        accent = AccentTheme.entries.firstOrNull { it.name == savedAccent } ?: AccentTheme.INDIGO
    }

    fun set(context: Context, newMode: ThemeMode) {
        mode = newMode
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, newMode.name)
            .apply()
    }

    fun setAccent(context: Context, newAccent: AccentTheme) {
        accent = newAccent
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(ACCENT_KEY, newAccent.name)
            .apply()
    }
}
