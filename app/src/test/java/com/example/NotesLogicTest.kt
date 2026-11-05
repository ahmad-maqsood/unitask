package com.example

import com.example.data.local.Note
import com.example.data.local.NoteListItem
import com.example.ui.screens.NotesFilter
import com.example.ui.screens.filterNotes
import com.example.ui.screens.noteDisplayTitle
import com.example.ui.screens.notePreviewText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesLogicTest {

    private fun makeItem(
        id: Long,
        title: String = "",
        body: String = "",
        courseId: Long? = null,
        courseCode: String? = null
    ): NoteListItem {
        return NoteListItem(
            note = Note(
                id = id,
                title = title,
                body = body,
                courseId = courseId
            ),
            courseCode = courseCode,
            courseName = null,
            courseColorHex = null
        )
    }

    // -------------------------------------------------------------------------
    // filterNotes tests
    // -------------------------------------------------------------------------

    @Test
    fun filterNotesMatchesQueryCaseInsensitivelyOnTitleAndBody() {
        val items = listOf(
            makeItem(1, title = "Calculus Notes", body = "Limits and derivatives"),
            makeItem(2, title = "Physics Lab", body = "MEASUREMENT OF ACCELERATION"),
            makeItem(3, title = "History Essay", body = "Ancient civilisations")
        )

        // Query matching title
        val titleMatch = filterNotes(items, "calculus", NotesFilter.All)
        assertEquals(1, titleMatch.size)
        assertEquals(1L, titleMatch[0].note.id)

        // Query matching body case-insensitively
        val bodyMatch = filterNotes(items, "acceleration", NotesFilter.All)
        assertEquals(1, bodyMatch.size)
        assertEquals(2L, bodyMatch[0].note.id)

        // Query matching nothing
        val noMatch = filterNotes(items, "Chemistry", NotesFilter.All)
        assertTrue(noMatch.isEmpty())

        // Blank query matches all
        val blankMatch = filterNotes(items, "   ", NotesFilter.All)
        assertEquals(3, blankMatch.size)
    }

    @Test
    fun filterNotesAppliesEachFilterKind() {
        val items = listOf(
            makeItem(1, title = "Note 1", courseId = null),
            makeItem(2, title = "Note 2", courseId = 101L),
            makeItem(3, title = "Note 3", courseId = 102L),
            makeItem(4, title = "Note 4", courseId = 101L)
        )

        // All filter
        val allFiltered = filterNotes(items, "", NotesFilter.All)
        assertEquals(4, allFiltered.size)

        // No course filter
        val noCourseFiltered = filterNotes(items, "", NotesFilter.NoCourse)
        assertEquals(1, noCourseFiltered.size)
        assertEquals(1L, noCourseFiltered[0].note.id)

        // Specific course filter
        val course101Filtered = filterNotes(items, "", NotesFilter.Course(101L))
        assertEquals(2, course101Filtered.size)
        assertEquals(2L, course101Filtered[0].note.id)
        assertEquals(4L, course101Filtered[1].note.id)

        val course102Filtered = filterNotes(items, "", NotesFilter.Course(102L))
        assertEquals(1, course102Filtered.size)
        assertEquals(3L, course102Filtered[0].note.id)
    }

    @Test
    fun filterNotesPreservesIncomingOrder() {
        val items = listOf(
            makeItem(5, title = "Alpha Note", courseId = 10L),
            makeItem(2, title = "Beta Note", courseId = 10L),
            makeItem(9, title = "Gamma Note", courseId = 10L),
            makeItem(1, title = "Delta Note", courseId = 10L)
        )

        val filtered = filterNotes(items, "Note", NotesFilter.Course(10L))
        assertEquals(4, filtered.size)
        assertEquals(5L, filtered[0].note.id)
        assertEquals(2L, filtered[1].note.id)
        assertEquals(9L, filtered[2].note.id)
        assertEquals(1L, filtered[3].note.id)
    }

    // -------------------------------------------------------------------------
    // noteDisplayTitle tests
    // -------------------------------------------------------------------------

    @Test
    fun noteDisplayTitleReturnsTitleWhenPresent() {
        val note = Note(title = "Meeting Minutes", body = "Some body content")
        assertEquals("Meeting Minutes", noteDisplayTitle(note))

        val spacedNote = Note(title = "  Trimmed Title  ", body = "Some body")
        assertEquals("Trimmed Title", noteDisplayTitle(spacedNote))
    }

    @Test
    fun noteDisplayTitleFallsBackToFirstNonBlankLineOfBodyWithMarkdownStripped() {
        val headingNote = Note(title = "", body = "# Project Overview\nDetails here")
        assertEquals("Project Overview", noteDisplayTitle(headingNote))

        val checkboxNote = Note(title = "   ", body = "- [ ] Finish homework\nNext line")
        assertEquals("Finish homework", noteDisplayTitle(checkboxNote))

        val quoteNote = Note(title = "", body = "\n\n  > Deep thoughts\nBy someone")
        assertEquals("Deep thoughts", noteDisplayTitle(quoteNote))

        val numberedNote = Note(title = "", body = "1. First step\n2. Second step")
        assertEquals("First step", noteDisplayTitle(numberedNote))

        val boldNote = Note(title = "", body = "**Important announcement**")
        assertEquals("Important announcement", noteDisplayTitle(boldNote))
    }

    @Test
    fun noteDisplayTitleReturnsUntitledWhenBothTitleAndBodyAreBlank() {
        val emptyNote = Note(title = "", body = "")
        assertEquals("Untitled note", noteDisplayTitle(emptyNote))

        val whitespaceNote = Note(title = "   ", body = "   \n\n  \t ")
        assertEquals("Untitled note", noteDisplayTitle(whitespaceNote))

        val markersOnlyNote = Note(title = "", body = "## \n\n- ")
        assertEquals("Untitled note", noteDisplayTitle(markersOnlyNote))
    }

    // -------------------------------------------------------------------------
    // notePreviewText tests
    // -------------------------------------------------------------------------

    @Test
    fun notePreviewTextStripsLeadingAndInlineMarkdownMarkers() {
        val body = """
            # Heading 1
            > Blockquote line
            - Bullet item
            * Star bullet
            1. Numbered line
            [ ] Unchecked box
            [x] Checked box
            - [ ] Nested checkbox
            **Bold words** and `inline code`
        """.trimIndent()

        val preview = notePreviewText(body, maxLines = 10)
        assertEquals(
            "Heading 1 Blockquote line Bullet item Star bullet Numbered line Unchecked box Checked box Nested checkbox Bold words and inline code",
            preview
        )
    }

    @Test
    fun notePreviewTextRespectsMaxLinesLimit() {
        val body = """
            First non-blank line
            
            Second non-blank line
            Third non-blank line
            Fourth non-blank line
        """.trimIndent()

        // Default maxLines = 2
        assertEquals("First non-blank line Second non-blank line", notePreviewText(body))

        // Custom maxLines = 1
        assertEquals("First non-blank line", notePreviewText(body, maxLines = 1))

        // Custom maxLines = 3
        assertEquals("First non-blank line Second non-blank line Third non-blank line", notePreviewText(body, maxLines = 3))
    }

    @Test
    fun notePreviewTextReturnsEmptyStringForEmptyBody() {
        assertEquals("", notePreviewText(""))
        assertEquals("", notePreviewText("   \n\n  \t "))
        assertEquals("", notePreviewText("", maxLines = 2))
    }
}

