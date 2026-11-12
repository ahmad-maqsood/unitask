package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Test

class MinuteClockTest {

    @Test
    fun waitsUntilTheNextWholeMinute() {
        assertEquals(60_000L, millisUntilNextMinute(0L))        // exactly on a minute: a full minute
        assertEquals(1_000L, millisUntilNextMinute(59_000L))
        assertEquals(30_000L, millisUntilNextMinute(90_000L))   // 1 min 30 s
        assertEquals(1L, millisUntilNextMinute(119_999L))
    }

    @Test
    fun neverWaitsZeroOrMoreThanAMinute() {
        for (t in listOf(0L, 1L, 59_999L, 60_000L, 1_700_000_000_123L, -1L, -60_000L)) {
            val wait = millisUntilNextMinute(t)
            assertEquals("wait for $t was $wait", true, wait in 1L..60_000L)
        }
    }
}
