package com.example.util

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NoteShareTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
    }

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

    // -------------------------------------------------------------------------
    // exportText tests
    // -------------------------------------------------------------------------

    @Test
    fun exportTextMarkdownFormatsCorrectly() {
        // title and body
        assertEquals("# Title\n\nBody", NoteShare.exportText("Title", "Body", NoteExportFormat.MD))
        // blank title
        assertEquals("Body", NoteShare.exportText("", "Body", NoteExportFormat.MD))
        assertEquals("Body", NoteShare.exportText("   ", "Body", NoteExportFormat.MD))
        // blank body
        assertEquals("# Title", NoteShare.exportText("Title", "", NoteExportFormat.MD))
        assertEquals("# Title", NoteShare.exportText("Title", "   ", NoteExportFormat.MD))
        // both blank
        assertEquals("", NoteShare.exportText("", "", NoteExportFormat.MD))
        assertEquals("   ", NoteShare.exportText("   ", "   ", NoteExportFormat.MD))
    }

    @Test
    fun exportTextPlainTextFormatsCorrectly() {
        // title and body (without '# ')
        assertEquals("Title\n\nBody", NoteShare.exportText("Title", "Body", NoteExportFormat.TXT))
        // blank title
        assertEquals("Body", NoteShare.exportText("", "Body", NoteExportFormat.TXT))
        assertEquals("Body", NoteShare.exportText("   ", "Body", NoteExportFormat.TXT))
        // blank body
        assertEquals("Title", NoteShare.exportText("Title", "", NoteExportFormat.TXT))
        assertEquals("Title", NoteShare.exportText("Title", "   ", NoteExportFormat.TXT))
        // both blank
        assertEquals("", NoteShare.exportText("", "", NoteExportFormat.TXT))
        assertEquals("   ", NoteShare.exportText("   ", "   ", NoteExportFormat.TXT))
    }

    // -------------------------------------------------------------------------
    // fileNameFor tests
    // -------------------------------------------------------------------------

    @Test
    fun fileNameForBothFormatsIncludesExtension() {
        assertEquals("Weekly Note.txt", NoteShare.fileNameFor("Weekly Note", NoteExportFormat.TXT))
        assertEquals("Weekly Note.md", NoteShare.fileNameFor("Weekly Note", NoteExportFormat.MD))
    }

    @Test
    fun fileNameForSanitizesIllegalCharacters() {
        val input = "Meeting: 2026/11/05? [Draft]*"
        assertEquals("Meeting_ 2026_11_05_ _Draft__.txt", NoteShare.fileNameFor(input, NoteExportFormat.TXT))
        assertEquals("Meeting_ 2026_11_05_ _Draft__.md", NoteShare.fileNameFor(input, NoteExportFormat.MD))
    }

    @Test
    fun fileNameForWithEmptyTitleUsesDefaultNoteName() {
        assertEquals("note.txt", NoteShare.fileNameFor("", NoteExportFormat.TXT))
        assertEquals("note.txt", NoteShare.fileNameFor("   ", NoteExportFormat.TXT))
        assertEquals("note.md", NoteShare.fileNameFor("", NoteExportFormat.MD))
        assertEquals("note.md", NoteShare.fileNameFor("   ", NoteExportFormat.MD))
    }

    // -------------------------------------------------------------------------
    // saveToUri tests
    // -------------------------------------------------------------------------

    @Test
    fun saveToUriWritesExactTextForTxt() = runBlocking {
        val tempFile = File.createTempFile("save_txt", ".txt")
        tempFile.deleteOnExit()
        val uri = Uri.fromFile(tempFile)

        val result = NoteShare.saveToUri(context, uri, "My Note", "Body content", NoteExportFormat.TXT)

        assertTrue(result)
        assertEquals("My Note\n\nBody content", tempFile.readText(Charsets.UTF_8))
        tempFile.delete()
    }

    @Test
    fun saveToUriWritesExactTextForMd() = runBlocking {
        val tempFile = File.createTempFile("save_md", ".md")
        tempFile.deleteOnExit()
        val uri = Uri.fromFile(tempFile)

        val result = NoteShare.saveToUri(context, uri, "My Note", "Body content", NoteExportFormat.MD)

        assertTrue(result)
        assertEquals("# My Note\n\nBody content", tempFile.readText(Charsets.UTF_8))
        tempFile.delete()
    }

    @Test
    fun saveToUriOverwritesLongerOldFileCompletely() = runBlocking {
        val tempFile = File.createTempFile("save_truncate", ".txt")
        tempFile.deleteOnExit()
        tempFile.writeText("A very long previous content that should be completely truncated and replaced.")
        val uri = Uri.fromFile(tempFile)

        val result = NoteShare.saveToUri(context, uri, "Short", "New", NoteExportFormat.TXT)

        assertTrue(result)
        assertEquals("Short\n\nNew", tempFile.readText(Charsets.UTF_8))
        tempFile.delete()
    }

    @Test
    fun saveToUriReturnsFalseForUriThatCannotBeOpened() = runBlocking {
        val invalidUri = Uri.parse("content://nonexistent.authority/invalid/path")

        val result = NoteShare.saveToUri(context, invalidUri, "Title", "Body", NoteExportFormat.TXT)

        assertFalse(result)
    }
}
