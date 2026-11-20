package com.example.ui.screens

import com.example.util.DateFormats
import com.example.util.TimeFormatter
import java.util.Calendar

/**
 * Returns formatted days left in trash text.
 * For example: "1 day left" or "28 days left".
 */
fun formatTrashDaysLeft(
    daysLeft: Int,
    // Screens pass the translated plural text; the default is English for plain unit tests.
    format: (Int) -> String = { if (it == 1) "1 day left" else "$it days left" }
): String = format(daysLeft)

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
fun formatTrashTaskDueDate(
    epochMillis: Long,
    hour: Int,
    minute: Int,
    // Builds the line from the date and the time. Screens pass the translated "Due %1$s at %2$s".
    format: (date: String, time: String) -> String = { date, time -> "Due $date at $time" }
): String {
    if (epochMillis <= 0L) return ""
    val cal = Calendar.getInstance().apply {
        timeInMillis = epochMillis
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    }
    val dateStr = DateFormats.shortDate(epochMillis)
    val timeStr = DateFormats.clock(cal.timeInMillis)
    return format(dateStr, timeStr)
}

/**
 * Returns the full name of the day of week, in the app language (1 = Monday ... 7 = Sunday).
 */
fun dayOfWeekName(dayOfWeek: Int): String = com.example.util.DayNames.full(dayOfWeek)

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
