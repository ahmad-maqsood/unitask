package com.example

import com.example.data.local.LectureSlot
import com.example.util.WeekParity
import com.example.util.WeekType
import com.example.util.cleanClassroom
import com.example.util.currentWeekType
import com.example.util.isSlotActive
import com.example.util.weekTypeOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class WeekParityTest {

    private val tz: TimeZone = TimeZone.getTimeZone("UTC")

    private fun dateToMillis(year: Int, month1Based: Int, day: Int, hour: Int = 12, minute: Int = 0, second: Int = 0, ms: Int = 0): Long {
        return Calendar.getInstance(tz).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month1Based - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, second)
            set(Calendar.MILLISECOND, ms)
        }.timeInMillis
    }

    private fun makeSlot(classroom: String): LectureSlot {
        return LectureSlot(
            id = 1L,
            courseId = 10L,
            dayOfWeek = 1,
            startHour = 10,
            startMinute = 0,
            endHour = 11,
            endMinute = 0,
            classroom = classroom
        )
    }

    @Test
    fun testAnchorUnset() {
        val slotA = makeSlot("Room 101 [Week A]")
        val slotB = makeSlot("Room 102 [Week B]")
        val slotAll = makeSlot("Room 103")

        val now = dateToMillis(2026, 10, 28)
        assertNull(currentWeekType(atMillis = now, anchorMillis = null, timeZone = tz))
        assertTrue(isSlotActive(slot = slotA, atMillis = now, anchorMillis = null, timeZone = tz))
        assertTrue(isSlotActive(slot = slotB, atMillis = now, anchorMillis = null, timeZone = tz))
        assertTrue(isSlotActive(slot = slotAll, atMillis = now, anchorMillis = null, timeZone = tz))
    }

    @Test
    fun testSameWeek() {
        // Wednesday Oct 28, 2026 as Week A anchor
        val anchor = dateToMillis(2026, 10, 28, 14, 30)

        val mon = dateToMillis(2026, 10, 26, 0, 0)
        val wed = dateToMillis(2026, 10, 28, 10, 0)
        val sun = dateToMillis(2026, 10, 31, 23, 59)
        val sunEnd = dateToMillis(2026, 11, 1, 23, 59)

        assertEquals(WeekType.A, currentWeekType(atMillis = mon, anchorMillis = anchor, timeZone = tz))
        assertEquals(WeekType.A, currentWeekType(atMillis = wed, anchorMillis = anchor, timeZone = tz))
        assertEquals(WeekType.A, currentWeekType(atMillis = sun, anchorMillis = anchor, timeZone = tz))
        assertEquals(WeekType.A, currentWeekType(atMillis = sunEnd, anchorMillis = anchor, timeZone = tz))

        val slotA = makeSlot("Hall [Week A]")
        val slotB = makeSlot("Hall [Week B]")
        val slotAll = makeSlot("Hall")

        assertTrue(isSlotActive(slot = slotA, atMillis = mon, anchorMillis = anchor, timeZone = tz))
        assertFalse(isSlotActive(slot = slotB, atMillis = mon, anchorMillis = anchor, timeZone = tz))
        assertTrue(isSlotActive(slot = slotAll, atMillis = mon, anchorMillis = anchor, timeZone = tz))
    }

    @Test
    fun testNextWeek() {
        val anchor = dateToMillis(2026, 10, 28, 14, 30)

        // Monday Nov 2, 2026 through Sunday Nov 8, 2026 are Week B
        val nextMon = dateToMillis(2026, 11, 2, 0, 1)
        val nextFri = dateToMillis(2026, 11, 6, 15, 0)
        val nextSun = dateToMillis(2026, 11, 8, 23, 59)

        assertEquals(WeekType.B, currentWeekType(atMillis = nextMon, anchorMillis = anchor, timeZone = tz))
        assertEquals(WeekType.B, currentWeekType(atMillis = nextFri, anchorMillis = anchor, timeZone = tz))
        assertEquals(WeekType.B, currentWeekType(atMillis = nextSun, anchorMillis = anchor, timeZone = tz))

        // Week after next (Monday Nov 9) is Week A again
        val twoWeeksLater = dateToMillis(2026, 11, 9, 8, 0)
        assertEquals(WeekType.A, currentWeekType(atMillis = twoWeeksLater, anchorMillis = anchor, timeZone = tz))

        val slotA = makeSlot("Hall [Week A]")
        val slotB = makeSlot("Hall [Week B]")
        assertFalse(isSlotActive(slot = slotA, atMillis = nextMon, anchorMillis = anchor, timeZone = tz))
        assertTrue(isSlotActive(slot = slotB, atMillis = nextMon, anchorMillis = anchor, timeZone = tz))
    }

    @Test
    fun testWeekBeforeAnchor() {
        val anchor = dateToMillis(2026, 10, 28, 14, 30) // Week A

        // Previous week: Monday Oct 19 to Sunday Oct 25 should be Week B
        val prevSun = dateToMillis(2026, 10, 25, 23, 59)
        val prevMon = dateToMillis(2026, 10, 19, 0, 0)

        assertEquals(WeekType.B, currentWeekType(atMillis = prevSun, anchorMillis = anchor, timeZone = tz))
        assertEquals(WeekType.B, currentWeekType(atMillis = prevMon, anchorMillis = anchor, timeZone = tz))

        // Two weeks before anchor: Monday Oct 12 should be Week A
        val twoWeeksBefore = dateToMillis(2026, 10, 12, 10, 0)
        assertEquals(WeekType.A, currentWeekType(atMillis = twoWeeksBefore, anchorMillis = anchor, timeZone = tz))
    }

    @Test
    fun testSundayVsMondayBoundary() {
        val anchor = dateToMillis(2026, 10, 28) // Week A

        val sundayEnd = dateToMillis(2026, 11, 1, 23, 59, 59, 999)
        val mondayStart = dateToMillis(2026, 11, 2, 0, 0, 0, 0)

        assertEquals(WeekType.A, currentWeekType(atMillis = sundayEnd, anchorMillis = anchor, timeZone = tz))
        assertEquals(WeekType.B, currentWeekType(atMillis = mondayStart, anchorMillis = anchor, timeZone = tz))
    }

    @Test
    fun testYearBoundary() {
        // Anchor in October 2026
        val anchor = dateToMillis(2026, 10, 28) // Week A

        // 2026-12-31 is Thursday
        // 2027-01-01 is Friday
        // 2027-01-03 is Sunday
        // All three are in the same Monday-based week (Monday 2026-12-28)
        val thu = dateToMillis(2026, 12, 31, 20, 0)
        val fri = dateToMillis(2027, 1, 1, 8, 0)
        val sun = dateToMillis(2027, 1, 3, 23, 59)

        val thuType = currentWeekType(atMillis = thu, anchorMillis = anchor, timeZone = tz)
        val friType = currentWeekType(atMillis = fri, anchorMillis = anchor, timeZone = tz)
        val sunType = currentWeekType(atMillis = sun, anchorMillis = anchor, timeZone = tz)

        assertEquals(thuType, friType)
        assertEquals(friType, sunType)

        // Monday 2027-01-04 is the next week and must flip parity
        val nextMon = dateToMillis(2027, 1, 4, 9, 0)
        val nextMonType = currentWeekType(atMillis = nextMon, anchorMillis = anchor, timeZone = tz)

        val expectedOpposite = if (thuType == WeekType.A) WeekType.B else WeekType.A
        assertEquals(expectedOpposite, nextMonType)
    }

    @Test
    fun testCleanClassroomAndWeekTypeOf() {
        assertEquals("Faraday Hall", cleanClassroom("Faraday Hall [Week A]"))
        assertEquals("Faraday Hall", cleanClassroom("Faraday Hall [Week B]"))
        assertEquals("", cleanClassroom("[Week A]"))
        assertEquals("", cleanClassroom("[Week B]"))
        assertEquals("Room 204", cleanClassroom("Room 204"))

        assertEquals(WeekType.A, weekTypeOf("Lab [Week A]"))
        assertEquals(WeekType.B, weekTypeOf("Lab [Week B]"))
        assertEquals(WeekType.ALL, weekTypeOf("Lab"))
        assertEquals(WeekType.ALL, weekTypeOf(""))
    }
}

