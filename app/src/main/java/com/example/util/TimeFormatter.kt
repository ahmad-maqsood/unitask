package com.example.util

import android.content.Context
import android.text.format.DateFormat
import java.util.Locale

/**
 * The one place that turns a time of day into text for the screen.
 * It follows the phone's 12-hour / 24-hour setting, the same way the time picker does.
 *
 * 12-hour phone: "9:30 AM", "5:00 PM"
 * 24-hour phone: "09:30", "17:00"
 *
 * It needs the app context once (set in UniTaskApp) so the time can be formatted from anywhere,
 * including notifications and the widget. If it was never set, it falls back to 12-hour.
 */
object TimeFormatter {

    @Volatile
    private var appContext: Context? = null

    /** Call once when the app starts. */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** True when the phone is set to show 24-hour time. Read each time, so a change in the phone's settings applies at once. */
    fun is24Hour(): Boolean {
        val context = appContext ?: return false
        return DateFormat.is24HourFormat(context)
    }

    /** The time pattern for SimpleDateFormat: "HH:mm" or "h:mm a". Used by [DateFormats]. */
    fun clockPattern(): String = if (is24Hour()) "HH:mm" else "h:mm a"

    /**
     * Formats hour (0-23) and minute (0-59) the way the phone is set.
     * Examples: (9, 30) -> "9:30 AM" or "09:30", (17, 0) -> "5:00 PM" or "17:00", (0, 5) -> "12:05 AM" or "00:05"
     */
    fun format(hour: Int, minute: Int): String {
        val h = ((hour % 24) + 24) % 24
        if (is24Hour()) {
            return String.format(Locale.US, "%02d:%02d", h, minute)
        }
        val h12 = if (h % 12 == 0) 12 else h % 12
        val suffix = if (h < 12) "AM" else "PM"
        return String.format(Locale.US, "%d:%02d %s", h12, minute, suffix)
    }

    /**
     * Formats a start and end time range the way the phone is set.
     * Example: "9:30 AM - 11:00 AM" or "09:30 - 11:00"
     */
    fun formatTimeRange(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int): String {
        return "${format(startHour, startMinute)} - ${format(endHour, endMinute)}"
    }
}
