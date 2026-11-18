package com.example.util

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class DayNamesTest {
    @Test
    fun englishNamesMatchTheStoredDayNumbers() {
        assertEquals("Monday", DayNames.full(1, Locale.ENGLISH))
        assertEquals("Sunday", DayNames.full(7, Locale.ENGLISH))
        assertEquals("Wed", DayNames.short(3, Locale.ENGLISH))
    }

    @Test
    fun outOfRangeDayGivesEmptyText() {
        assertEquals("", DayNames.full(0, Locale.ENGLISH))
        assertEquals("", DayNames.short(8, Locale.ENGLISH))
    }

    @Test
    fun urduNamesAreNotTheEnglishOnes() {
        val ur = Locale.forLanguageTag("ur")
        assertEquals(false, DayNames.full(1, ur) == DayNames.full(1, Locale.ENGLISH))
    }
}
