package com.example.reminder

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CancelledClassStoreTest {
    private val tz = TimeZone.getTimeZone("Asia/Karachi")

    private fun millis(y: Int, m: Int, d: Int, h: Int = 9): Long =
        Calendar.getInstance(tz).apply { clear(); set(y, m - 1, d, h, 0, 0) }.timeInMillis

    @Test
    fun keyIsSlotAndLocalDate() {
        assertEquals("7|2026-11-23", CancelledClassStore.keyOf(7, millis(2026, 11, 23), tz))
    }

    @Test
    fun sameDayDifferentTimeGivesSameKey() {
        assertEquals(
            CancelledClassStore.keyOf(3, millis(2026, 11, 23, 8), tz),
            CancelledClassStore.keyOf(3, millis(2026, 11, 23, 22), tz)
        )
    }

    @Test
    fun anotherWeekIsNotCancelled() {
        assertFalse(
            CancelledClassStore.keyOf(3, millis(2026, 11, 23), tz) == CancelledClassStore.keyOf(3, millis(2026, 11, 30), tz)
        )
    }

    @Test
    fun oldDatesAreDroppedAndRecentOnesKept() {
        val now = millis(2026, 11, 18)
        val keys = setOf("1|2026-01-05", "1|2026-10-30", "2|2026-11-25")
        val kept = CancelledClassStore.prune(keys, now, tz)
        assertEquals(setOf("1|2026-10-30", "2|2026-11-25"), kept)
        assertTrue("1|2026-01-05" !in kept)
    }
}
