package com.example.ui.screens

import com.example.util.DateFormats
import com.example.util.TimeFormatter
import java.util.Calendar

/**
 * Returns formatted days left in trash text.
 * For example: "1 day left" or "28 days left".
 */
fun formatTrashDaysLeft(daysLeft: Int): String {
    return if (daysLeft == 1) "1 day left" else "$daysLeft days left"
}

/**
 * Formats the tab label with its item count.
 * For example: "Tasks (3)" or "Notes (0)".
 */
fun formatTabTitle(name: String, count: Int): String {
    return "$name ($count)"
}

/**
 * Formats the original due date of a task in the trash.
 * For example: "Due Mon, Nov 4 at 9:30 AM" or empty string if no due date.
 */
fun formatTrashTaskDueDate(epochMillis: Long, hour: Int, minute: Int): String {
    if (epochMillis <= 0L) return ""
    val cal = Calendar.getInstance().apply {
        timeInMillis = epochMillis
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    }
    val dateStr = DateFormats.shortDate(epochMillis)
    val timeStr = DateFormats.clock(cal.timeInMillis)
    return "Due $dateStr at $timeStr"
}

/**
 * Returns full English name for day of week (1 = Monday ... 7 = Sunday).
 */
fun dayOfWeekName(dayOfWeek: Int): String {
    return when (dayOfWeek) {
        1 -> "Monday"
        2 -> "Tuesday"
        3 -> "Wednesday"
        4 -> "Thursday"
        5 -> "Friday"
        6 -> "Saturday"
        7 -> "Sunday"
        else -> ""
    }
}

/**
 * Formats day and time for a lecture slot in the trash.
 * For example: "Thursday, 10:00 AM to 11:30 AM" or "Monday, 09:00 AM to 10:30 AM".
 */
fun formatTrashClassTime(dayOfWeek: Int, startHour: Int, startMinute: Int, endHour: Int, endMinute: Int): String {
    val day = dayOfWeekName(dayOfWeek)
    val startTime = TimeFormatter.format(startHour, startMinute)
    val endTime = TimeFormatter.format(endHour, endMinute)
    return if (day.isNotEmpty()) "$day, $startTime to $endTime" else "$startTime to $endTime"
}
