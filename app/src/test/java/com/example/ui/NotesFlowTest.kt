package com.example.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.runtime.mutableStateOf
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.Note
import com.example.ui.screens.NotesHost
import com.example.ui.theme.MyApplicationTheme
import com.example.util.NoteExportFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotesFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var db: AppDatabase
    private val notesVisible = mutableStateOf(true)
    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.getDatabase(context)

        runBlocking(Dispatchers.IO) {
            db.clearAllTables()
            val imagesDir = File(context.filesDir, "note_images")
            if (imagesDir.exists()) {
                imagesDir.deleteRecursively()
            }
        }

        composeRule.setContent {
            MyApplicationTheme {
                if (notesVisible.value) {
                    NotesHost(onClose = {})
                }
            }
        }
    }

    private fun tagExists(tag: String): Boolean =
        composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    // Closing the editor saves on a background thread first and only then switches screen,
    // and waitForIdle() does not wait for that thread. So wait for the list explicitly.
    private fun waitForNotesList() {
        composeRule.waitUntil(timeoutMillis = 5_000) { tagExists("notes_fab_new_note") }
    }

    private fun waitForCard(id: Long) {
        composeRule.waitUntil(timeoutMillis = 5_000) { tagExists("note_card_$id") }
    }

    private fun openNote(id: Long) {
        waitForCard(id)
        composeRule.onNodeWithTag("note_card_$id").performClick()
        composeRule.waitForIdle()
    }

    private fun waitForPreviewTitle(title: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("note_preview_title").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openNewNote() {
        waitForNotesList()
        composeRule.onNodeWithTag("notes_fab_new_note").performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 5_000) { tagExists("note_title_input") }
    }

    private fun typeTitle(text: String) {
        composeRule.onNodeWithTag("note_title_input").performTextInput(text)
        composeRule.waitForIdle()
    }

    private fun typeBody(text: String) {
        composeRule.onNodeWithTag("note_body_input").performTextInput(text)
        composeRule.waitForIdle()
    }

    private fun goBackFromEditor() {
        composeRule.onNodeWithTag("note_editor_back_button").performClick()
        composeRule.waitForIdle()
        waitForNotesList()
    }

    private fun waitForNoteInDb(predicate: (List<Note>) -> Boolean) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            val notes = runBlocking(Dispatchers.IO) {
                db.noteDao().getAllNotesSync()
            }
            predicate(notes)
        }
    }

    private fun insertNote(title: String, body: String): Long {
        val id = runBlocking(Dispatchers.IO) {
            db.noteDao().insert(Note(title = title, body = body))
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("note_card_$id").fetchSemanticsNodes().isNotEmpty()
        }
        return id
    }

    private fun assertInputEmpty(tag: String) {
        val node = composeRule.onNodeWithTag(tag)
        val text = node.fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text ?: ""
        assertTrue("Expected input $tag to be empty, but was '$text'", text.isEmpty())
    }

    @Test
    fun `new note after another note starts empty`() {
        val noteId = insertNote("Alpha", "alpha body")

        // Open note A
        openNote(noteId)

        // Verify note A is open
        waitForPreviewTitle("Alpha")

        // Go back to the notes list
        goBackFromEditor()

        // Tap new note FAB
        openNewNote()

        // Assert the title and body inputs contain no "Alpha" or "alpha body" text
        assertInputEmpty("note_title_input")
        assertInputEmpty("note_body_input")
        composeRule.onNodeWithText("Alpha").assertDoesNotExist()
        composeRule.onNodeWithText("alpha body").assertDoesNotExist()
    }

    @Test
    fun `opening note B after note A shows B not A`() {
        val idA = insertNote("Alpha", "alpha body")
        val idB = insertNote("Beta", "beta body")

        // Open note A
        openNote(idA)
        waitForPreviewTitle("Alpha")
        composeRule.onNodeWithTag("note_preview_title").assertTextEquals("Alpha")

        // Go back
        goBackFromEditor()

        // Open note B
        openNote(idB)
        waitForPreviewTitle("Beta")

        // Preview title shows B's title and never A's
        composeRule.onNodeWithTag("note_preview_title").assertTextEquals("Beta")
        composeRule.onNodeWithText("Alpha").assertDoesNotExist()
    }

    @Test
    fun `typed text is saved when leaving the editor`() {
        openNewNote()
        typeTitle("Title C")
        typeBody("Body C")
        goBackFromEditor()

        waitForNoteInDb { notes ->
            notes.any { it.title == "Title C" && it.body == "Body C" && !it.isDeleted }
        }

        val notes = runBlocking(Dispatchers.IO) { db.noteDao().getActiveNotesSync() }
        assertEquals(1, notes.size)
        assertEquals("Title C", notes[0].title)
        assertEquals("Body C", notes[0].body)

        // The list shows it
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Title C").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Title C").assertIsDisplayed()
    }

    @Test
    fun `blank new note is not saved`() {
        openNewNote()
        goBackFromEditor()

        composeRule.waitForIdle()
        val notes = runBlocking(Dispatchers.IO) { db.noteDao().getAllNotesSync() }
        assertEquals(0, notes.size)
    }

    @Test
    fun `edited existing note keeps its changes after reopening`() {
        val idA = insertNote("Title E", "Body E")

        // Open note A
        openNote(idA)

        // Existing notes open in preview mode; switch to edit mode
        composeRule.waitUntil(timeoutMillis = 5_000) { tagExists("note_mode_edit_button") }
        composeRule.onNodeWithTag("note_mode_edit_button").performClick()
        composeRule.waitForIdle()

        // Append text to the body
        composeRule.waitUntil(timeoutMillis = 5_000) { tagExists("note_body_input") }
        composeRule.onNodeWithTag("note_body_input").performTextInput(" and extra text")
        composeRule.waitForIdle()

        // Go back (saves changes)
        goBackFromEditor()

        // Open note A again
        openNote(idA)

        // The preview contains the appended text
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Body E and extra text", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Body E and extra text", substring = true).assertIsDisplayed()
    }

    @Test
    fun `delete from editor moves the note to the trash and Undo brings it back`() {
        val idA = insertNote("Note F", "Body F")

        // Open note A
        openNote(idA)

        // Open overflow menu
        composeRule.waitUntil(timeoutMillis = 5_000) { tagExists("note_editor_overflow_button") }
        composeRule.onNodeWithTag("note_editor_overflow_button").performClick()
        composeRule.waitForIdle()

        // Tap Delete
        composeRule.onNodeWithText("Delete").performClick()
        composeRule.waitForIdle()

        // List no longer shows it (the move to trash happens on a background thread first)
        waitForNotesList()
        composeRule.waitUntil(timeoutMillis = 5_000) { !tagExists("note_card_$idA") }

        // DAO says isDeleted = true
        waitForNoteInDb { notes ->
            notes.any { it.id == idA && it.isDeleted }
        }
        val deletedNote = runBlocking(Dispatchers.IO) { db.noteDao().getNoteById(idA) }
        assertNotNull(deletedNote)
        assertTrue(deletedNote?.isDeleted == true)

        // Tap Undo snackbar action
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Undo").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Undo").performClick()
        composeRule.waitForIdle()

        // DAO says isDeleted = false
        waitForNoteInDb { notes ->
            notes.any { it.id == idA && !it.isDeleted }
        }
        val restoredNote = runBlocking(Dispatchers.IO) { db.noteDao().getNoteById(idA) }
        assertNotNull(restoredNote)
        assertTrue(restoredNote?.isDeleted == false)

        // List shows it again
        waitForCard(idA)
        composeRule.onNodeWithTag("note_card_$idA").assertIsDisplayed()
    }
    @Test
    fun `share from editor opens format dialog with format rows and cancel closes it`() {
        val id = insertNote("Share Title", "Share Body")
        openNote(id)

        // Open overflow menu
        composeRule.waitUntil(timeoutMillis = 5_000) { tagExists("note_editor_overflow_button") }
        composeRule.onNodeWithTag("note_editor_overflow_button").performClick()
        composeRule.waitForIdle()

        // Tap Share menu item
        composeRule.onNodeWithTag("note_menu_share").performClick()
        composeRule.waitForIdle()

        // Export dialog is displayed with a row per format
        composeRule.onNodeWithTag("note_export_dialog").assertIsDisplayed()
        for (format in NoteExportFormat.entries) {
            composeRule.onNodeWithTag("note_export_format_" + format.extension).assertIsDisplayed()
        }

        // Cancel closes the dialog
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("note_export_dialog").assertDoesNotExist()
    }

    @Test
    fun `reopening Notes after closing it shows the right note`() {
        val idA = insertNote("Alpha", "alpha body")
        val idB = insertNote("Beta", "beta body")

        openNote(idA)
        waitForPreviewTitle("Alpha")
        goBackFromEditor()

        // Close the whole Notes screen and open it again, like leaving Notes and returning.
        composeRule.runOnUiThread { notesVisible.value = false }
        composeRule.waitForIdle()
        composeRule.runOnUiThread { notesVisible.value = true }
        composeRule.waitForIdle()

        openNote(idB)
        waitForPreviewTitle("Beta")
        composeRule.onNodeWithText("Alpha").assertDoesNotExist()
    }

    @Test
    fun `save to device in note card menu shows format dialog and cancel closes it`() {
        val id = insertNote("Card Save Note", "Some content")
        waitForCard(id)

        // Open card actions menu
        composeRule.onNodeWithContentDescription("Note actions").performClick()
        composeRule.waitForIdle()

        // Card menu shows "Save to device"
        composeRule.onNodeWithTag("note_card_menu_save").assertIsDisplayed()
        composeRule.onNodeWithTag("note_card_menu_save").performClick()
        composeRule.waitForIdle()

        // Export dialog is displayed with format options
        composeRule.onNodeWithTag("note_export_dialog").assertIsDisplayed()
        for (format in NoteExportFormat.entries) {
            composeRule.onNodeWithTag("note_export_format_" + format.extension).assertIsDisplayed()
        }

        // Cancel closes the dialog
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("note_export_dialog").assertDoesNotExist()
    }
}
