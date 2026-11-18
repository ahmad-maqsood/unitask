package com.example.util

import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * Day names that follow the app language (1 = Monday ... 7 = Sunday, the numbers the timetable stores).
 * They come from java.time, which Android supports on every phone this app runs on, so Urdu phones
 * and the per-app language show the right names without a string for each day.
 */
object DayNames {
    fun full(dayOfWeek: Int, locale: Locale = Locale.getDefault()): String =
        name(dayOfWeek, TextStyle.FULL, locale)

    fun short(dayOfWeek: Int, locale: Locale = Locale.getDefault()): String =
        name(dayOfWeek, TextStyle.SHORT, locale)

    private fun name(dayOfWeek: Int, style: TextStyle, locale: Locale): String =
        if (dayOfWeek in 1..7) DayOfWeek.of(dayOfWeek).getDisplayName(style, locale) else ""
}
