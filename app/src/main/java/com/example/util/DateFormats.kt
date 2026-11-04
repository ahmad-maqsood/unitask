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

    /** Mon, Nov 4, 2026 */
    fun fullDate(millis: Long) = format("EEE, MMM d, yyyy", millis)

    /** Mon, Nov 4 */
    fun shortDate(millis: Long) = format("EEE, MMM d", millis)

    /** Nov 4 */
    fun monthDay(millis: Long) = format("MMM d", millis)

    /** 9:30 AM */
    fun clock(millis: Long) = format("h:mm a", millis)

    /** 09:30 AM (chat bubbles) */
    fun chatClock(millis: Long) = format("hh:mm a", millis)
}