package com.example.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One place for the date and time patterns shown on screen.
 * SimpleDateFormat is not thread-safe, so each call makes a fresh one. That is cheap for
 * a few labels, and it also follows the phone's language if it changes.
 */
object DateFormats {
    private fun format(pattern: String, millis: Long): String =
        SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis))

    /** Same as [format] but always with English digits and names, for keys and file names. */
    private fun formatUs(pattern: String, millis: Long): String =
        SimpleDateFormat(pattern, Locale.US).format(Date(millis))

    /** Mon, Nov 4, 2026 */
    fun fullDate(millis: Long) = format("EEE, MMM d, yyyy", millis)

    /** Mon, Nov 4 */
    fun shortDate(millis: Long) = format("EEE, MMM d", millis)

    /** Nov 4 */
    fun monthDay(millis: Long) = format("MMM d", millis)

    /** 9:30 AM */
    fun clock(millis: Long) = format("h:mm a", millis)


    /** Mon, Nov 4 at 9:30 AM */
    fun shortDateAtClock(millis: Long) = format("EEE, MMM d 'at' h:mm a", millis)

    /** Nov 4, 9:30 AM */
    fun monthDayClock(millis: Long) = format("MMM d, h:mm a", millis)

    /** November 2026 */
    fun monthYear(millis: Long) = format("MMMM yyyy", millis)

    /** Monday, November 4 */
    fun longWeekdayMonthDay(millis: Long) = format("EEEE, MMMM d", millis)

    /** 21:30 (24-hour, used for backup times) */
    fun clock24(millis: Long) = format("HH:mm", millis)

    /** Nov 4, 21:30 (24-hour, used for backup times) */
    fun monthDayClock24(millis: Long) = format("MMM d, HH:mm", millis)

    /** Mon, 4 Nov (day first) */
    fun dayFirstDate(millis: Long) = format("EEE, d MMM", millis)

    /** 4 Nov (day first, no weekday) */
    fun dayMonth(millis: Long) = format("d MMM", millis)

    /** 2026-11-04. Always digits and the same in every language, so it is safe as a lookup key. */
    fun dayKey(millis: Long) = formatUs("yyyy-MM-dd", millis)

    /** 20261104. For file names. */
    fun fileDate(millis: Long) = formatUs("yyyyMMdd", millis)

    /** 20261104-2130. For file names. */
    fun fileDateTime(millis: Long) = formatUs("yyyyMMdd-HHmm", millis)

    /** Mon, 4 Nov 9:30 AM (day first) */
    fun dayFirstDateClock(millis: Long) = format("EEE, d MMM h:mm a", millis)
}