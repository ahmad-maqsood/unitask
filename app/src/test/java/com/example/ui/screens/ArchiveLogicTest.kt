package com.example.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveLogicTest {

    @Test
    fun validateSemesterNameTrimsWhitespace() {
        val result = validateSemesterName("   Fall 2026   ")
        assertTrue(result.isValid)
        assertEquals("Fall 2026", result.trimmedName)
        assertNull(result.errorMessage)
    }

    @Test
    fun validateSemesterNameRejectsBlankOrEmpty() {
        val emptyResult = validateSemesterName("")
        assertFalse(emptyResult.isValid)
        assertEquals("", emptyResult.trimmedName)

        val whitespaceResult = validateSemesterName("     ")
        assertFalse(whitespaceResult.isValid)
        assertEquals("", whitespaceResult.trimmedName)
    }

    @Test
    fun validateSemesterNameEnforcesMax40Characters() {
        val exact40 = "A".repeat(40)
        val validResult = validateSemesterName(exact40)
        assertTrue(validResult.isValid)
        assertEquals(40, validResult.trimmedName.length)

        val over40 = "A".repeat(41)
        val invalidResult = validateSemesterName(over40)
        assertFalse(invalidResult.isValid)
    }

    @Test
    fun findExistingSemesterMatchesIgnoringCaseAndPreservesOriginalSpelling() {
        val existing = listOf("Fall 2026", "Spring 2027")

        // Exact match
        assertEquals("Fall 2026", findExistingSemester("Fall 2026", existing))

        // Lowercase match
        assertEquals("Fall 2026", findExistingSemester("fall 2026", existing))

        // Uppercase match with leading and trailing whitespace
        assertEquals("Spring 2027", findExistingSemester("  SPRING 2027  ", existing))

        // No match
        assertNull(findExistingSemester("Summer 2026", existing))

        // Empty or blank input
        assertNull(findExistingSemester("", existing))
        assertNull(findExistingSemester("   ", existing))
    }

    @Test
    fun archiveSummaryTextWithoutExistingSemester() {
        val summary = archiveSummaryText(taskCount = 3, classCount = 2, noteCount = 5, existingName = null)
        assertEquals("3 tasks, 2 classes and 5 notes will be hidden. Their reminders stop.", summary)

        val blankSummary = archiveSummaryText(taskCount = 0, classCount = 0, noteCount = 0, existingName = "   ")
        assertEquals("0 tasks, 0 classes and 0 notes will be hidden. Their reminders stop.", blankSummary)
    }

    @Test
    fun archiveSummaryTextWithExistingSemester() {
        val summary = archiveSummaryText(taskCount = 4, classCount = 1, noteCount = 2, existingName = "Fall 2026")
        assertEquals("4 tasks, 1 classes and 2 notes will be hidden. Their reminders stop. Adds to the existing Fall 2026.", summary)

        val trimmedSummary = archiveSummaryText(taskCount = 1, classCount = 0, noteCount = 0, existingName = "  Spring 2026  ")
        assertEquals("1 tasks, 0 classes and 0 notes will be hidden. Their reminders stop. Adds to the existing Spring 2026.", trimmedSummary)
    }
}
