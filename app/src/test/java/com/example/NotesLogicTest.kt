package com.example

import com.example.data.local.Note
import com.example.data.local.NoteListItem
import com.example.ui.screens.NotesFilter
import com.example.ui.screens.NotesFolderFilter
import com.example.ui.screens.NotesSort
import com.example.ui.screens.characterCount
import com.example.ui.screens.daysLeftInTrash
import com.example.ui.screens.filterNotes
import com.example.ui.screens.noteDisplayTitle
import com.example.ui.screens.notePreviewText
import com.example.ui.screens.sortNotes
import com.example.ui.screens.wordCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesLogicTest {

    private fun makeItem(
        id: Long,
        title: String = "",
        body: String = "",
        courseId: Long? = null,
        courseCode: String? = null,
        folderId: Long? = null,
        isPinned: Boolean = false,
        createdAt: Long = 0L,
        updatedAt: Long = 0L
    ): NoteListItem {
        return NoteListItem(
            note = Note(
                id = id,
                title = title,
                body = body,
                courseId = courseId,
                folderId = folderId,
                isPinned = isPinned,
                createdAt = createdAt,
                updatedAt = updatedAt
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
    fun filterNotesAppliesFolderFilterAndCombinesWithCourseAndSearch() {
        val items = listOf(
            makeItem(1, title = "Loose note", folderId = null),
            makeItem(2, title = "Algebra", courseId = 101L, folderId = 7L),
            makeItem(3, title = "Geometry", courseId = 102L, folderId = 7L),
            makeItem(4, title = "Chemistry", folderId = 8L)
        )

        assertEquals(4, filterNotes(items, "", NotesFilter.All).size)
        assertEquals(4, filterNotes(items, "", NotesFilter.All, NotesFolderFilter.AllFolders).size)

        val unfiled = filterNotes(items, "", NotesFilter.All, NotesFolderFilter.Unfiled)
        assertEquals(listOf(1L), unfiled.map { it.note.id })

        val folder7 = filterNotes(items, "", NotesFilter.All, NotesFolderFilter.Folder(7L))
        assertEquals(listOf(2L, 3L), folder7.map { it.note.id })

        val folder7Course101 = filterNotes(items, "", NotesFilter.Course(101L), NotesFolderFilter.Folder(7L))
        assertEquals(listOf(2L), folder7Course101.map { it.note.id })

        val folder7Search = filterNotes(items, "geo", NotesFilter.All, NotesFolderFilter.Folder(7L))
        assertEquals(listOf(3L), folder7Search.map { it.note.id })

        val emptyFolder = filterNotes(items, "", NotesFilter.All, NotesFolderFilter.Folder(99L))
        assertTrue(emptyFolder.isEmpty())
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

    // -------------------------------------------------------------------------
    // sortNotes tests
    // -------------------------------------------------------------------------

    @Test
    fun sortNotesUpdatedPlacesPinnedFirstThenNewestUpdated() {
        val items = listOf(
            makeItem(1, title = "Note 1", isPinned = false, updatedAt = 1000L),
            makeItem(2, title = "Note 2", isPinned = false, updatedAt = 4000L),
            makeItem(3, title = "Note 3", isPinned = true, updatedAt = 2000L),
            makeItem(4, title = "Note 4", isPinned = true, updatedAt = 3000L)
        )

        val sorted = sortNotes(items, NotesSort.UPDATED)
        val sortedIds = sorted.map { it.note.id }
        // Pinned first (4, 3 sorted by updatedAt desc), then unpinned (2, 1 sorted by updatedAt desc)
        assertEquals(listOf(4L, 3L, 2L, 1L), sortedIds)
    }

    @Test
    fun sortNotesCreatedPlacesPinnedFirstThenNewestCreated() {
        val items = listOf(
            makeItem(1, title = "Note 1", isPinned = false, createdAt = 1000L),
            makeItem(2, title = "Note 2", isPinned = false, createdAt = 4000L),
            makeItem(3, title = "Note 3", isPinned = true, createdAt = 2000L),
            makeItem(4, title = "Note 4", isPinned = true, createdAt = 3000L)
        )

        val sorted = sortNotes(items, NotesSort.CREATED)
        val sortedIds = sorted.map { it.note.id }
        // Pinned first (4, 3 sorted by createdAt desc), then unpinned (2, 1 sorted by createdAt desc)
        assertEquals(listOf(4L, 3L, 2L, 1L), sortedIds)
    }

    @Test
    fun sortNotesTitlePlacesPinnedFirstThenCaseInsensitiveAlphabetical() {
        val items = listOf(
            makeItem(1, title = "banana", isPinned = false),
            makeItem(2, title = "Apple", isPinned = false),
            makeItem(3, title = "zebra", isPinned = true),
            makeItem(4, title = "Cat", isPinned = true)
        )

        val sorted = sortNotes(items, NotesSort.TITLE)
        val sortedIds = sorted.map { it.note.id }
        // Pinned first alphabetically: "Cat", "zebra". Then unpinned: "Apple", "banana"
        assertEquals(listOf(4L, 3L, 2L, 1L), sortedIds)
    }

    @Test
    fun sortNotesTitleUsesNoteDisplayTitleFallbackWhenTitleIsBlank() {
        val items = listOf(
            makeItem(1, title = "", body = "# Zoo notes", isPinned = false),
            makeItem(2, title = "Aardvark", isPinned = false)
        )

        val sorted = sortNotes(items, NotesSort.TITLE)
        val sortedIds = sorted.map { it.note.id }
        // "Aardvark" comes before "Zoo notes"
        assertEquals(listOf(2L, 1L), sortedIds)
    }

    // -------------------------------------------------------------------------
    // daysLeftInTrash tests
    // -------------------------------------------------------------------------

    @Test
    fun daysLeftInTrashFreshNoteReturnsThirtyDays() {
        val now = 1_000_000_000L
        assertEquals(30, daysLeftInTrash(deletedAt = now, now = now))
        // 1 hour after deletion is still 30 days left
        assertEquals(30, daysLeftInTrash(deletedAt = now - 3600_000L, now = now))
    }

    @Test
    fun daysLeftInTrashIntermediateDays() {
        val now = 1_000_000_000L
        val oneDayMillis = 24L * 60 * 60 * 1000L
        // 1 day elapsed
        assertEquals(29, daysLeftInTrash(deletedAt = now - 1 * oneDayMillis, now = now))
        // 18 days elapsed -> 12 days left
        assertEquals(12, daysLeftInTrash(deletedAt = now - 18 * oneDayMillis, now = now))
        // 29 days elapsed -> 1 day left
        assertEquals(1, daysLeftInTrash(deletedAt = now - 29 * oneDayMillis, now = now))
    }

    @Test
    fun daysLeftInTrashOldNoteReturnsZero() {
        val now = 1_000_000_000L
        val oneDayMillis = 24L * 60 * 60 * 1000L
        // Exactly 30 days elapsed
        assertEquals(0, daysLeftInTrash(deletedAt = now - 30 * oneDayMillis, now = now))
        // 35 days elapsed
        assertEquals(0, daysLeftInTrash(deletedAt = now - 35 * oneDayMillis, now = now))
    }

    @Test
    fun daysLeftInTrashNeverNegative() {
        val now = 1_000_000_000L
        val oneDayMillis = 24L * 60 * 60 * 1000L
        // Very old note (365 days ago)
        assertEquals(0, daysLeftInTrash(deletedAt = now - 365 * oneDayMillis, now = now))
        // Future deletedAt due to clock anomaly
        val futureDays = daysLeftInTrash(deletedAt = now + 10 * oneDayMillis, now = now)
        assertTrue(futureDays >= 0)
        assertEquals(30, futureDays)
    }

    // -------------------------------------------------------------------------
    // wordCount and characterCount tests
    // -------------------------------------------------------------------------

    @Test
    fun wordCountEmptyStringReturnsZero() {
        assertEquals(0, wordCount(""))
    }

    @Test
    fun wordCountSpacesOnlyReturnsZero() {
        assertEquals(0, wordCount("   "))
        assertEquals(0, wordCount(" \t \t  "))
    }

    @Test
    fun wordCountNewlinesReturnsZero() {
        assertEquals(0, wordCount("\n\n\r\n\t"))
    }

    @Test
    fun wordCountOneWordReturnsOne() {
        assertEquals(1, wordCount("word"))
        assertEquals(1, wordCount("   word   "))
        assertEquals(1, wordCount("\nword\t"))
    }

    @Test
    fun wordCountMultipleWordsAndPunctuation() {
        assertEquals(5, wordCount("128 words  |  742 characters"))
        assertEquals(3, wordCount("One, two, three!"))
        assertEquals(6, wordCount("The quick\nbrown\tfox   jumps over"))
    }

    @Test
    fun characterCountReturnsLength() {
        assertEquals(0, characterCount(""))
        assertEquals(5, characterCount("hello"))
        assertEquals(4, characterCount("a b\n"))
        assertEquals(28, characterCount("128 words  |  742 characters"))
    }
}
