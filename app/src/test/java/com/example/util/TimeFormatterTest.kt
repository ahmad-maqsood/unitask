package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [TimeFormatter] follows the phone's 12-hour / 24-hour setting, and falls back to 12-hour when the
 * app context was never set (as in these tests). So these checks cover the default wording:
 * AM / PM and no leading zero on the hour.
 */
class TimeFormatterTest {

    @Test
    fun morningAndAfternoonTimesUseAmAndPmWithoutALeadingZero() {
        assertEquals("9:30 AM", TimeFormatter.format(9, 30))
        assertEquals("5:00 PM", TimeFormatter.format(17, 0))
        assertEquals("11:59 PM", TimeFormatter.format(23, 59))
    }

    @Test
    fun midnightAndNoonShowAs12() {
        assertEquals("12:05 AM", TimeFormatter.format(0, 5))
        assertEquals("12:00 PM", TimeFormatter.format(12, 0))
    }

    @Test
    fun hoursOutsideTheDayWrapAround() {
        assertEquals("12:00 AM", TimeFormatter.format(24, 0))
        assertEquals("1:15 AM", TimeFormatter.format(25, 15))
        assertEquals("11:00 PM", TimeFormatter.format(-1, 0))
    }

    @Test
    fun aTimeRangeJoinsTheTwoTimesWithADash() {
        assertEquals("9:30 AM - 11:00 AM", TimeFormatter.formatTimeRange(9, 30, 11, 0))
    }
}
