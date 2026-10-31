package com.example.util

import com.example.data.local.Course
import com.example.data.local.LectureSlot
import com.example.data.local.LectureSlotWithCourse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class IcsExportTest {

    private fun makeSlot(
        id: Long = 1L,
        dayOfWeek: Int = 1,
        startHour: Int = 9,
        startMinute: Int = 0,
        endHour: Int = 10,
        endMinute: Int = 0,
        classroom: String = "Room 101",
        courseCode: String = "CS101",
        courseName: String = "Intro to Computer Science",
        instructor: String = "Dr. Alan Turing"
    ): LectureSlotWithCourse {
        return LectureSlotWithCourse(
            slot = LectureSlot(
                id = id,
                courseId = 1L,
                dayOfWeek = dayOfWeek,
                startHour = startHour,
                startMinute = startMinute,
                endHour = endHour,
                endMinute = endMinute,
                classroom = classroom,
                instructor = instructor
            ),
            course = Course(
                id = 1L,
                code = courseCode,
                name = courseName,
                colorHex = "#4F46E5",
                location = "Campus Center",
                instructor = instructor
            )
        )
    }

    @Test
    fun testOutputUsesOnlyCrlf() {
        val slots = listOf(
            makeSlot(id = 1, classroom = "Room 101 [Week A]"),
            makeSlot(id = 2, classroom = "Room 102 [Week B]")
        )
        val ics = buildTimetableIcs(slots, zoneId = ZoneId.of("UTC"))
        assertTrue(ics.contains("\r\n"))
        // There must be no standalone '\n' (i.e. '\n' not preceded by '\r')
        val withoutCrlf = ics.replace("\r\n", "")
        assertFalse(withoutCrlf.contains("\n"))
        assertFalse(withoutCrlf.contains("\r"))
    }

    @Test
    fun testEveryVeventHasRequiredProperties() {
        val slots = listOf(
            makeSlot(id = 1, dayOfWeek = 1, startHour = 10, startMinute = 0, endHour = 11, endMinute = 0)
        )
        val ics = buildTimetableIcs(slots, zoneId = ZoneId.of("UTC"))
        assertTrue(ics.contains("UID:unitask_slot_1@unitask"))
        assertTrue(ics.contains("DTSTAMP:"))
        assertTrue(ics.contains("DTSTART:"))
        assertTrue(ics.contains("DTEND:"))
        // Check UTC format yyyyMMddTHHmmssZ
        val dtStampRegex = Regex("DTSTAMP:\\d{8}T\\d{6}Z")
        val dtStartRegex = Regex("DTSTART:\\d{8}T\\d{6}Z")
        val dtEndRegex = Regex("DTEND:\\d{8}T\\d{6}Z")
        assertTrue(dtStampRegex.containsMatchIn(ics))
        assertTrue(dtStartRegex.containsMatchIn(ics))
        assertTrue(dtEndRegex.containsMatchIn(ics))
    }

    @Test
    fun testAllGivesNoInterval() {
        val slots = listOf(
            makeSlot(id = 1, dayOfWeek = 2, classroom = "Room 101")
        )
        val ics = buildTimetableIcs(slots, zoneId = ZoneId.of("UTC"))
        assertTrue(ics.contains("RRULE:FREQ=WEEKLY;WKST=MO;BYDAY=TU"))
        assertFalse(ics.contains("INTERVAL="))
    }

    @Test
    fun testWeekAGivesInterval2() {
        val slots = listOf(
            makeSlot(id = 1, dayOfWeek = 3, classroom = "Lab A [Week A]")
        )
        val ics = buildTimetableIcs(slots, zoneId = ZoneId.of("UTC"))
        assertTrue(ics.contains("RRULE:FREQ=WEEKLY;INTERVAL=2;WKST=MO;BYDAY=WE"))
    }

    @Test
    fun testWeekBGivesInterval2() {
        val slots = listOf(
            makeSlot(id = 1, dayOfWeek = 4, classroom = "Lab B [Week B]")
        )
        val ics = buildTimetableIcs(slots, zoneId = ZoneId.of("UTC"))
        assertTrue(ics.contains("RRULE:FREQ=WEEKLY;INTERVAL=2;WKST=MO;BYDAY=TH"))
    }

    @Test
    fun testLocationContainsNoWeekTag() {
        val slots = listOf(
            makeSlot(id = 1, classroom = "Room 302 [Week A]"),
            makeSlot(id = 2, classroom = "[Week B]")
        )
        val ics = buildTimetableIcs(slots, zoneId = ZoneId.of("UTC"))
        assertFalse(ics.contains("[Week"))
        assertTrue(ics.contains("LOCATION:Room 302"))
    }

    @Test
    fun testTitleFoldedToLinesOf75OctetsOrFewer() {
        val longTitle = "A".repeat(200)
        val slots = listOf(
            makeSlot(id = 1, courseName = longTitle)
        )
        val ics = buildTimetableIcs(slots, zoneId = ZoneId.of("UTC"))
        val lines = ics.split("\r\n")
        for (line in lines) {
            val octets = line.toByteArray(Charsets.UTF_8).size
            assertTrue("Line '$line' exceeded 75 octets: $octets", octets <= 75)
        }
        // Verify continuation line begins with a space
        val summaryLines = lines.filter { it.startsWith("SUMMARY:") || it.startsWith(" ") }
        assertTrue(summaryLines.size > 1)
        assertTrue(summaryLines.drop(1).all { it.startsWith(" ") })
    }

    @Test
    fun testSlotWithEndLessOrEqualToStartIsSkipped() {
        val slots = listOf(
            // end == start (should be skipped)
            makeSlot(id = 1, startHour = 10, startMinute = 0, endHour = 10, endMinute = 0),
            // end < start (should be skipped)
            makeSlot(id = 2, startHour = 11, startMinute = 30, endHour = 11, endMinute = 15),
            // valid slot
            makeSlot(id = 3, startHour = 10, startMinute = 0, endHour = 11, endMinute = 0)
        )
        val ics = buildTimetableIcs(slots, zoneId = ZoneId.of("UTC"))
        assertFalse(ics.contains("UID:unitask_slot_1@unitask"))
        assertFalse(ics.contains("UID:unitask_slot_2@unitask"))
        assertTrue(ics.contains("UID:unitask_slot_3@unitask"))
    }

    @Test
    fun testWeekShiftCalculation() {
        // Monday 2026-10-19 00:00:00 UTC as Week A anchor
        val anchorMonday = 1792368000000L // 2026-10-19T00:00:00Z
        // Wednesday 2026-10-21 (Week A)
        val weekANow = anchorMonday + 2 * 86_400_000L
        // Wednesday 2026-10-28 (Week B)
        val weekBNow = anchorMonday + 9 * 86_400_000L

        val slotA = makeSlot(id = 1, dayOfWeek = 1, classroom = "Room [Week A]")
        val slotB = makeSlot(id = 2, dayOfWeek = 1, classroom = "Room [Week B]")

        // During Week A: Slot A starts on Monday of current week (20261019), Slot B starts on Monday of next week (20261026)
        val icsA = buildTimetableIcs(listOf(slotA, slotB), weekAAnchorMillis = anchorMonday, nowMillis = weekANow, zoneId = ZoneId.of("UTC"))
        assertTrue(icsA.contains("UID:unitask_slot_1@unitask\r\nDTSTAMP:20261021T000000Z\r\nDTSTART:20261019T090000Z"))
        assertTrue(icsA.contains("UID:unitask_slot_2@unitask\r\nDTSTAMP:20261021T000000Z\r\nDTSTART:20261026T090000Z"))

        // During Week B: Slot A starts on Monday of next week (20261102), Slot B starts on Monday of current week (20261026)
        val icsB = buildTimetableIcs(listOf(slotA, slotB), weekAAnchorMillis = anchorMonday, nowMillis = weekBNow, zoneId = ZoneId.of("UTC"))
        assertTrue(icsB.contains("UID:unitask_slot_1@unitask\r\nDTSTAMP:20261028T000000Z\r\nDTSTART:20261102T090000Z"))
        assertTrue(icsB.contains("UID:unitask_slot_2@unitask\r\nDTSTAMP:20261028T000000Z\r\nDTSTART:20261026T090000Z"))
    }
}

