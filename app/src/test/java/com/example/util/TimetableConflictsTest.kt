package com.example.util

import com.example.data.local.Course
import com.example.data.local.LectureSlot
import com.example.data.local.LectureSlotWithCourse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableConflictsTest {

    private fun makeSlot(
        id: Long,
        dayOfWeek: Int,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        courseCode: String = "CS101",
        courseName: String = "Computer Science",
        classroom: String = "Room 101",
        isDeleted: Boolean = false
    ): LectureSlotWithCourse {
        return LectureSlotWithCourse(
            slot = LectureSlot(
                id = id,
                courseId = id,
                dayOfWeek = dayOfWeek,
                startHour = startHour,
                startMinute = startMinute,
                endHour = endHour,
                endMinute = endMinute,
                classroom = classroom,
                isDeleted = isDeleted
            ),
            course = Course(
                id = id,
                code = courseCode,
                name = courseName,
                colorHex = "#4F46E5",
                instructor = "Instructor"
            )
        )
    }

    @Test
    fun testOverlapOnSameDay() {
        val slot1 = makeSlot(id = 1L, dayOfWeek = 1, startHour = 10, startMinute = 0, endHour = 11, endMinute = 30, courseCode = "CS101")
        val slot2 = makeSlot(id = 2L, dayOfWeek = 1, startHour = 11, startMinute = 0, endHour = 12, endMinute = 0, courseCode = "MATH101")

        val conflicts = findConflicts(listOf(slot1, slot2))

        assertEquals(listOf("MATH101"), conflicts[1L])
        assertEquals(listOf("CS101"), conflicts[2L])
    }

    @Test
    fun testBackToBackClassesAreNotAConflict() {
        // Slot 1 ends exactly when Slot 2 starts
        val slot1 = makeSlot(id = 1L, dayOfWeek = 1, startHour = 10, startMinute = 0, endHour = 11, endMinute = 0, courseCode = "CS101")
        val slot2 = makeSlot(id = 2L, dayOfWeek = 1, startHour = 11, startMinute = 0, endHour = 12, endMinute = 0, courseCode = "MATH101")

        val conflicts = findConflicts(listOf(slot1, slot2))

        assertTrue(conflicts.isEmpty())
        assertNull(conflicts[1L])
        assertNull(conflicts[2L])
    }

    @Test
    fun testWeekAVsWeekBDoNotConflict() {
        // Same day and time, but one is Week A and one is Week B
        val slotA = makeSlot(id = 1L, dayOfWeek = 1, startHour = 10, startMinute = 0, endHour = 11, endMinute = 0, courseCode = "CS101", classroom = "Room 1 [Week A]")
        val slotB = makeSlot(id = 2L, dayOfWeek = 1, startHour = 10, startMinute = 0, endHour = 11, endMinute = 0, courseCode = "MATH101", classroom = "Room 2 [Week B]")

        val conflicts = findConflicts(listOf(slotA, slotB))

        assertTrue(conflicts.isEmpty())
    }

    @Test
    fun testWeekAVsAllIsAConflict() {
        val slotA = makeSlot(id = 1L, dayOfWeek = 1, startHour = 10, startMinute = 0, endHour = 11, endMinute = 0, courseCode = "CS101", classroom = "Room 1 [Week A]")
        val slotAll = makeSlot(id = 2L, dayOfWeek = 1, startHour = 10, startMinute = 0, endHour = 11, endMinute = 0, courseCode = "MATH101", classroom = "Room 2")

        val conflicts = findConflicts(listOf(slotA, slotAll))

        assertEquals(listOf("MATH101"), conflicts[1L])
        assertEquals(listOf("CS101"), conflicts[2L])
    }

    @Test
    fun testDifferentDaysDoNotConflict() {
        // Same time, but Monday vs Tuesday
        val slotMon = makeSlot(id = 1L, dayOfWeek = 1, startHour = 10, startMinute = 0, endHour = 11, endMinute = 0, courseCode = "CS101")
        val slotTue = makeSlot(id = 2L, dayOfWeek = 2, startHour = 10, startMinute = 0, endHour = 11, endMinute = 0, courseCode = "MATH101")

        val conflicts = findConflicts(listOf(slotMon, slotTue))

        assertTrue(conflicts.isEmpty())
    }

    @Test
    fun testZeroLengthOrNegativeSlotIgnored() {
        // Slot 1: 10:00 - 10:00 (zero-length)
        val slotZero = makeSlot(id = 1L, dayOfWeek = 1, startHour = 10, startMinute = 0, endHour = 10, endMinute = 0, courseCode = "ZERO")
        // Slot 2: 09:30 - 10:30 (valid)
        val slotNormal = makeSlot(id = 2L, dayOfWeek = 1, startHour = 9, startMinute = 30, endHour = 10, endMinute = 30, courseCode = "NORMAL")
        // Slot 3: 11:00 - 10:00 (end < start)
        val slotNegative = makeSlot(id = 3L, dayOfWeek = 1, startHour = 11, startMinute = 0, endHour = 10, endMinute = 0, courseCode = "NEG")

        val conflicts = findConflicts(listOf(slotZero, slotNormal, slotNegative))

        assertTrue(conflicts.isEmpty())
    }

    @Test
    fun testThreeWayOverlap() {
        val slot1 = makeSlot(id = 1L, dayOfWeek = 1, startHour = 10, startMinute = 0, endHour = 12, endMinute = 0, courseCode = "CS101")
        val slot2 = makeSlot(id = 2L, dayOfWeek = 1, startHour = 10, startMinute = 30, endHour = 11, endMinute = 30, courseCode = "MATH101")
        val slot3 = makeSlot(id = 3L, dayOfWeek = 1, startHour = 11, startMinute = 0, endHour = 13, endMinute = 0, courseCode = "PHYS101")

        val conflicts = findConflicts(listOf(slot1, slot2, slot3))

        assertEquals(listOf("MATH101", "PHYS101"), conflicts[1L])
        assertEquals(listOf("CS101", "PHYS101"), conflicts[2L])
        assertEquals(listOf("CS101", "MATH101"), conflicts[3L])
    }

    @Test
    fun testDeletedSlotIgnored() {
        val slotDeleted = makeSlot(id = 1L, dayOfWeek = 1, startHour = 10, startMinute = 0, endHour = 11, endMinute = 0, courseCode = "CS101", isDeleted = true)
        val slotActive = makeSlot(id = 2L, dayOfWeek = 1, startHour = 10, startMinute = 0, endHour = 11, endMinute = 0, courseCode = "MATH101", isDeleted = false)

        val conflicts = findConflicts(listOf(slotDeleted, slotActive))

        assertTrue(conflicts.isEmpty())
    }

    @Test
    fun testCourseCodeBlankFallsBackToName() {
        val slot1 = makeSlot(id = 1L, dayOfWeek = 1, startHour = 10, startMinute = 0, endHour = 11, endMinute = 0, courseCode = "CS101")
        val slot2 = makeSlot(id = 2L, dayOfWeek = 1, startHour = 10, startMinute = 0, endHour = 11, endMinute = 0, courseCode = "", courseName = "Calculus I")

        val conflicts = findConflicts(listOf(slot1, slot2))

        assertEquals(listOf("Calculus I"), conflicts[1L])
        assertEquals(listOf("CS101"), conflicts[2L])
    }
}

