package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object TimeFormatter {

    /**
     * Formats hour (0-23) and minute (0-59) into 12-hour AM/PM format "hh:mm a"
     * Examples: (9, 30) -> "09:30 AM", (14, 15) -> "02:15 PM", (0, 5) -> "12:05 AM"
     */
    fun format12Hour(hour: Int, minute: Int): String {
         val h12 = if (hour % 12 == 0) 12 else hour % 12
        val suffix = if (hour % 24 < 12) "AM" else "PM"
        return String.format(java.util.Locale.US, "%02d:%02d %s", h12, minute, suffix)
    }

    /**
     * Formats a start and end time range in 12-hour AM/PM format.
     * Example: "09:30 AM - 11:00 AM"
     */
    fun formatTimeRange(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int): String {
        return "${format12Hour(startHour, startMinute)} - ${format12Hour(endHour, endMinute)}"
    }
}
