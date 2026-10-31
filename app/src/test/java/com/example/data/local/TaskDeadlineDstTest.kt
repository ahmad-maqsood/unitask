package com.example.data.local

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class TaskDeadlineDstTest {

    private lateinit var originalDefaultTimeZone: TimeZone

    @Before
    fun setUp() {
        originalDefaultTimeZone = TimeZone.getDefault()
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalDefaultTimeZone)
    }

    // -------------------------------------------------------------------------
    // Timezone 1: America/New_York (UTC-5 EST / UTC-4 EDT)
    // -------------------------------------------------------------------------

    @Test
    fun testStartOfLocalDayOnStandardDayNewYork() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))

        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.JANUARY, 15, 14, 35, 42)
            set(Calendar.MILLISECOND, 500)
        }
        val inputMillis = cal.timeInMillis

        val startOfDayMillis = startOfLocalDay(inputMillis)

        val resultCal = Calendar.getInstance().apply {
            timeInMillis = startOfDayMillis
        }

        assertEquals(2026, resultCal.get(Calendar.YEAR))
        assertEquals(Calendar.JANUARY, resultCal.get(Calendar.MONTH))
        assertEquals(15, resultCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, resultCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, resultCal.get(Calendar.MINUTE))
        assertEquals(0, resultCal.get(Calendar.SECOND))
        assertEquals(0, resultCal.get(Calendar.MILLISECOND))
    }

    @Test
    fun testStartOfLocalDayAcrossSpringForwardDstNewYork() {
        // America/New_York transitions to DST on March 8, 2026 at 2:00 AM -> 3:00 AM
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))

        // Time before the 2 AM transition (1:30 AM EST)
        val beforeDstJump = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 8, 1, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // Time after the transition (2:30 PM EDT)
        val afterDstJump = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 8, 14, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // Late evening on same day (11:59 PM EDT)
        val lateEvening = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 8, 23, 59, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        val start1 = startOfLocalDay(beforeDstJump)
        val start2 = startOfLocalDay(afterDstJump)
        val start3 = startOfLocalDay(lateEvening)

        // All times on the DST transition day resolve to the exact same start-of-day
        assertEquals(start1, start2)
        assertEquals(start2, start3)

        val resultCal = Calendar.getInstance().apply {
            timeInMillis = start1
        }
        assertEquals(2026, resultCal.get(Calendar.YEAR))
        assertEquals(Calendar.MARCH, resultCal.get(Calendar.MONTH))
        assertEquals(8, resultCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, resultCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, resultCal.get(Calendar.MINUTE))
        assertEquals(0, resultCal.get(Calendar.SECOND))
        assertEquals(0, resultCal.get(Calendar.MILLISECOND))
    }

    @Test
    fun testStartOfLocalDayAcrossFallBackDstNewYork() {
        // America/New_York transitions back from DST on November 1, 2026 at 2:00 AM -> 1:00 AM
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))

        val earlyMorning = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 1, 0, 45, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val afternoon = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 1, 15, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val start1 = startOfLocalDay(earlyMorning)
        val start2 = startOfLocalDay(afternoon)

        assertEquals(start1, start2)

        val resultCal = Calendar.getInstance().apply {
            timeInMillis = start1
        }
        assertEquals(2026, resultCal.get(Calendar.YEAR))
        assertEquals(Calendar.NOVEMBER, resultCal.get(Calendar.MONTH))
        assertEquals(1, resultCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, resultCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, resultCal.get(Calendar.MINUTE))
        assertEquals(0, resultCal.get(Calendar.SECOND))
        assertEquals(0, resultCal.get(Calendar.MILLISECOND))
    }

    @Test
    fun testComputeDeadlineMillisAcrossDstChangeNewYork() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))

        // Day before DST (March 7, 2026)
        val march7Raw = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 7, 16, 45, 12)
        }.timeInMillis

        // DST transition day (March 8, 2026)
        val march8Raw = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 8, 19, 20, 0)
        }.timeInMillis

        // Day after DST (March 9, 2026)
        val march9Raw = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 9, 8, 10, 0)
        }.timeInMillis

        val march7Deadline = computeDeadlineMillis(startOfLocalDay(march7Raw), 12, 0)
        val march8Deadline = computeDeadlineMillis(startOfLocalDay(march8Raw), 12, 0)
        val march9Deadline = computeDeadlineMillis(startOfLocalDay(march9Raw), 12, 0)

        // Raw timestamp computation yields identical deadline as normalized startOfLocalDay
        val march8DeadlineFromRaw = computeDeadlineMillis(march8Raw, 12, 0)
        assertEquals(march8Deadline, march8DeadlineFromRaw)

        // Deadlines across days must follow strict chronological order
        assertTrue(march7Deadline < march8Deadline)
        assertTrue(march8Deadline < march9Deadline)

        // Verify deadline on DST day has expected calendar components
        val dCal = Calendar.getInstance().apply {
            timeInMillis = march8Deadline
        }
        assertEquals(2026, dCal.get(Calendar.YEAR))
        assertEquals(Calendar.MARCH, dCal.get(Calendar.MONTH))
        assertEquals(8, dCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(12, dCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, dCal.get(Calendar.MINUTE))
        assertEquals(0, dCal.get(Calendar.SECOND))
        assertEquals(0, dCal.get(Calendar.MILLISECOND))

        // Same-day tasks on DST transition day sort correctly by deadline
        val morningDeadline = computeDeadlineMillis(march8Raw, 9, 0)
        val afternoonDeadline = computeDeadlineMillis(march8Raw, 14, 0)
        val eveningDeadline = computeDeadlineMillis(march8Raw, 20, 0)

        assertTrue(morningDeadline < afternoonDeadline)
        assertTrue(afternoonDeadline < eveningDeadline)
    }

    // -------------------------------------------------------------------------
    // Timezone 2: Europe/London (UTC+0 GMT / UTC+1 BST)
    // -------------------------------------------------------------------------

    @Test
    fun testStartOfLocalDayAcrossSpringForwardDstLondon() {
        // Europe/London transitions to BST on March 29, 2026 at 1:00 AM -> 2:00 AM
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"))

        val earlyGmt = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 29, 0, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val afternoonBst = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 29, 15, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val start1 = startOfLocalDay(earlyGmt)
        val start2 = startOfLocalDay(afternoonBst)

        assertEquals(start1, start2)

        val resultCal = Calendar.getInstance().apply {
            timeInMillis = start1
        }
        assertEquals(2026, resultCal.get(Calendar.YEAR))
        assertEquals(Calendar.MARCH, resultCal.get(Calendar.MONTH))
        assertEquals(29, resultCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, resultCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, resultCal.get(Calendar.MINUTE))
        assertEquals(0, resultCal.get(Calendar.SECOND))
        assertEquals(0, resultCal.get(Calendar.MILLISECOND))
    }

    @Test
    fun testComputeDeadlineMillisAcrossDstChangeLondon() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"))

        val march28Raw = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 28, 18, 0, 0)
        }.timeInMillis

        val march29DstRaw = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 29, 14, 0, 0)
        }.timeInMillis

        val march30Raw = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 30, 9, 0, 0)
        }.timeInMillis

        val d28 = computeDeadlineMillis(startOfLocalDay(march28Raw), 11, 0)
        val d29 = computeDeadlineMillis(startOfLocalDay(march29DstRaw), 11, 0)
        val d30 = computeDeadlineMillis(startOfLocalDay(march30Raw), 11, 0)

        // Raw timestamp matches normalized timestamp
        assertEquals(d29, computeDeadlineMillis(march29DstRaw, 11, 0))

        // Strict chronological progression across transition boundary
        assertTrue(d28 < d29)
        assertTrue(d29 < d30)

        val dCal = Calendar.getInstance().apply {
            timeInMillis = d29
        }
        assertEquals(2026, dCal.get(Calendar.YEAR))
        assertEquals(Calendar.MARCH, dCal.get(Calendar.MONTH))
        assertEquals(29, dCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(11, dCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, dCal.get(Calendar.MINUTE))
    }
}
