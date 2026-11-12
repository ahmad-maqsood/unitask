package com.example.ui.screens

import com.example.data.local.Note
import com.example.data.local.NoteListItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoteLinksLogicTest {

    private fun item(id: Long, title: String, body: String = "", updatedAt: Long = 0L) =
        NoteListItem(
            note = Note(id = id, title = title, body = body, updatedAt = updatedAt),
            courseCode = null, courseName = null, courseColorHex = null
        )

    @Test
    fun findNoteForLinkIgnoresCapitalsAndPrefersTheNewestNote() {
        val notes = listOf(
            item(1, "Graphs", updatedAt = 10),
            item(2, "graphs", updatedAt = 20),
            item(3, "Trees", updatedAt = 30)
        )
        assertEquals(2L, findNoteForLink(notes, "GRAPHS")!!.id)
        assertEquals(3L, findNoteForLink(notes, "  Trees ")!!.id)
        assertNull(findNoteForLink(notes, "Missing"))
        assertNull(findNoteForLink(notes, "  "))
    }

    @Test
    fun findNoteForLinkUsesTheFirstLineWhenANoteHasNoTitle() {
        val notes = listOf(item(1, "", body = "# Shopping list\n- milk"))
        assertEquals(1L, findNoteForLink(notes, "shopping list")!!.id)
    }

    @Test
    fun findBacklinksListsOtherNotesThatLinkHere() {
        val notes = listOf(
            item(1, "Graphs"),
            item(2, "Course overview", body = "Start with [[graphs]] then [[Trees]]"),
            item(3, "Unrelated", body = "no links here"),
            item(4, "Self", body = "I mention [[Graphs]] too"),
            item(5, "Trees", body = "Back to [[Graphs]]")
        )
        assertEquals(listOf(2L, 4L, 5L), findBacklinks(notes, currentId = 1, currentTitle = "Graphs").map { it.id })
        assertEquals(listOf(2L), findBacklinks(notes, currentId = 5, currentTitle = "Trees").map { it.id })
        assertEquals(emptyList<Long>(), findBacklinks(notes, currentId = 1, currentTitle = ""))
    }

    @Test
    fun findBacklinksNeverIncludesTheNoteItself() {
        val notes = listOf(item(1, "Graphs", body = "see [[Graphs]]"))
        assertEquals(emptyList<Long>(), findBacklinks(notes, currentId = 1, currentTitle = "Graphs").map { it.id })
    }

    @Test
    fun suggestionsPutTitlesStartingWithTheTextFirstAndHideTheCurrentNote() {
        val notes = listOf(
            item(1, "Intro to graphs", updatedAt = 50),
            item(2, "Graph theory", updatedAt = 10),
            item(3, "Trees", updatedAt = 40),
            item(4, "Graphs cheat sheet", updatedAt = 30)
        )
        val result = noteLinkSuggestions(notes, currentId = 4, typed = "graph")
        assertEquals(listOf(2L, 1L), result.map { it.id })
    }

    @Test
    fun suggestionsOfferAnEmptyQueryEverythingAndRespectTheLimit() {
        val notes = (1L..8L).map { item(it, "Note $it", updatedAt = it) }
        val result = noteLinkSuggestions(notes, currentId = 0, typed = "", limit = 5)
        assertEquals(listOf(8L, 7L, 6L, 5L, 4L), result.map { it.id })
    }

    @Test
    fun suggestionsOfferATitleSharedByManyNotesOnlyOnce() {
        val notes = listOf(item(1, "Notes", updatedAt = 1), item(2, "notes", updatedAt = 2))
        assertEquals(listOf(2L), noteLinkSuggestions(notes, currentId = 0, typed = "no").map { it.id })
    }
}
