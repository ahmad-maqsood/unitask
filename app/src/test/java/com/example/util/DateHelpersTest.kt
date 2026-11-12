package com.example.util

import com.example.data.local.startOfLocalDay
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class DateHelpersTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val auckland = TimeZone.getTimeZone("Pacific/Auckland")

    private fun utcMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis

    @Test
    fun startOfLocalDayGivesMidnightOfThatDayInTheGivenZone() {
        val afternoon = utcMillis(2026, 11, 12, 15, 45)
        assertEquals(utcMillis(2026, 11, 12, 0), startOfLocalDay(afternoon, utc))
    }

    @Test
    fun startOfLocalDayFollowsTheZoneNotUtc() {
        // 20:00 UTC on 12 Nov is already 9:00 on 13 Nov in Auckland (UTC+13 in November).
        val time = utcMillis(2026, 11, 12, 20)
        val start = startOfLocalDay(time, auckland)
        val cal = Calendar.getInstance(auckland).apply { timeInMillis = start }
        assertEquals(13, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.MILLISECOND))
    }

    @Test
    fun startOfLocalDayIsUnchangedWhenAlreadyMidnight() {
        val midnight = utcMillis(2026, 11, 12, 0)
        assertEquals(midnight, startOfLocalDay(midnight, utc))
    }

    @Test
    fun keyAndFileNameFormatsUseEnglishDigitsInOrder() {
        // Noon UTC is the same calendar day in every time zone the tests could run in.
        val noon = utcMillis(2026, 11, 4, 12)
        assertEquals("2026-11-04", DateFormats.dayKey(noon))
        assertEquals("20261104", DateFormats.fileDate(noon))
        assertEquals(8, DateFormats.fileDateTime(noon).substringBefore('-').length)
        assertEquals(4, DateFormats.fileDateTime(noon).substringAfter('-').length)
    }
}
