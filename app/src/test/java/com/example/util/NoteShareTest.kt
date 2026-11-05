package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteShareTest {

    // -------------------------------------------------------------------------
    // safeFileName tests
    // -------------------------------------------------------------------------

    @Test
    fun safeFileNameKeepsValidCharacters() {
        val input = "Notes - Week 1_Math"
        assertEquals("Notes - Week 1_Math", NoteShare.safeFileName(input))
    }

    @Test
    fun safeFileNameReplacesInvalidCharactersWithUnderscore() {
        val input = "Meeting: 2026/11/05? [Draft]*"
        assertEquals("Meeting_ 2026_11_05_ _Draft__", NoteShare.safeFileName(input))
    }

    @Test
    fun safeFileNameTrimsWhitespace() {
        val input = "   Trim Me   "
        assertEquals("Trim Me", NoteShare.safeFileName(input))
    }

    @Test
    fun safeFileNameTruncatesTo60Characters() {
        val input = "a".repeat(100)
        val result = NoteShare.safeFileName(input)
        assertEquals(60, result.length)
        assertEquals("a".repeat(60), result)
    }

    @Test
    fun safeFileNameReturnsNoteWhenEmptyOrBlank() {
        assertEquals("note", NoteShare.safeFileName(""))
        assertEquals("note", NoteShare.safeFileName("   "))
        assertEquals("note", NoteShare.safeFileName("\t\n"))
    }

    // -------------------------------------------------------------------------
    // noteFromImportedText tests
    // -------------------------------------------------------------------------

    @Test
    fun noteFromImportedTextWithHeadingTitle() {
        val text = "# My Great Note\n\nFirst line of body\nSecond line"
        val (title, body) = NoteShare.noteFromImportedText("sample.md", text)
        assertEquals("My Great Note", title)
        assertEquals("First line of body\nSecond line", body)
    }

    @Test
    fun noteFromImportedTextWithNoHeadingUsesFileNameWithoutExtension() {
        val text = "- Milk\n- Bread\n- Eggs"
        val (title, body) = NoteShare.noteFromImportedText("shopping_list.txt", text)
        assertEquals("shopping_list", title)
        assertEquals("- Milk\n- Bread\n- Eggs", body)
    }

    @Test
    fun noteFromImportedTextWithLeadingBlankLinesBeforeHeading() {
        val text = "\n  \n\n# Chapter One\n\n\nContent paragraph"
        val (title, body) = NoteShare.noteFromImportedText("book.md", text)
        assertEquals("Chapter One", title)
        assertEquals("Content paragraph", body)
    }

    @Test
    fun noteFromImportedTextWithEmptyText() {
        val (title, body) = NoteShare.noteFromImportedText("empty_note.txt", "")
        assertEquals("empty_note", title)
        assertEquals("", body)
    }

    @Test
    fun noteFromImportedTextWithMultipleDotsInFileName() {
        val text = "Some raw text content"
        val (title, body) = NoteShare.noteFromImportedText("my.special.notes.archive.txt", text)
        assertEquals("my.special.notes.archive", title)
        assertEquals("Some raw text content", body)
    }

    @Test
    fun noteFromImportedTextWithFileNameHavingNoExtension() {
        val text = "Unformatted file content"
        val (title, body) = NoteShare.noteFromImportedText("README", text)
        assertEquals("README", title)
        assertEquals("Unformatted file content", body)
    }

    @Test
    fun noteFromImportedTextWithOnlyHeadingAndNoBody() {
        val text = "# Only Title"
        val (title, body) = NoteShare.noteFromImportedText("solo.md", text)
        assertEquals("Only Title", title)
        assertEquals("", body)
    }
}

