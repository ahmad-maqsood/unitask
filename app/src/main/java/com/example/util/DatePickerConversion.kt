package com.example.util

import java.util.Calendar
import java.util.TimeZone

/**
 * Converts dates between the app's local millisecond timestamps and Material 3
 * DatePicker's UTC midnight timestamps.
 *
 * Material 3 DatePickerState works in UTC midnight of the chosen day, whereas
 * task due dates in UniTask are stored as local epoch millis (keeping the user's
 * chosen hour and minute in their local time zone).
 */

/**
 * Takes the year, month and day of month from the picked UTC midnight timestamp
 * and applies them to currentMillis in the specified time zone, preserving the
 * existing time of day (hour, minute, second, millisecond).
 */
fun applyPickedDate(
    currentMillis: Long,
    pickedUtcMillis: Long,
    zone: TimeZone = TimeZone.getDefault()
): Long {
    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = pickedUtcMillis
    }
    val year = utcCal.get(Calendar.YEAR)
    val month = utcCal.get(Calendar.MONTH)
    val day = utcCal.get(Calendar.DAY_OF_MONTH)

    val localCal = Calendar.getInstance(zone).apply {
        timeInMillis = currentMillis
        // Set day to 1 before changing year and month so month-end dates (like Jan 31)
        // do not overflow into the next month when switching to a shorter month (like Feb).
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
        set(Calendar.DAY_OF_MONTH, day)
    }
    return localCal.timeInMillis
}

/**
 * Converts a local epoch millisecond timestamp to the UTC midnight timestamp of
 * the same calendar day in the specified time zone. This is used as the initial
 * selection for Material 3 DatePicker.
 */
fun localMillisToPickerUtcMillis(
    localMillis: Long,
    zone: TimeZone = TimeZone.getDefault()
): Long {
    val localCal = Calendar.getInstance(zone).apply {
        timeInMillis = localMillis
    }
    val year = localCal.get(Calendar.YEAR)
    val month = localCal.get(Calendar.MONTH)
    val day = localCal.get(Calendar.DAY_OF_MONTH)

    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
        set(Calendar.DAY_OF_MONTH, day)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return utcCal.timeInMillis
}
