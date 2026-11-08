package com.example.ui.screens

import com.example.util.DateFormats
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
