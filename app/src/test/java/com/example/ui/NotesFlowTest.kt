package com.example.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.Note
import com.example.ui.screens.NotesHost
import com.example.ui.theme.MyApplicationTheme
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
                NotesHost(onClose = {})
            }
        }
    }

    private fun openNewNote() {
        composeRule.onNode(hasTestTag("notes_fab_new_note") or hasTestTag("notes_new_button")).performClick()
        composeRule.waitForIdle()
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
        composeRule.onNodeWithTag("note_card_$noteId").performClick()
        composeRule.waitForIdle()

        // Verify note A is open
        composeRule.onNodeWithTag("note_preview_title").assertIsDisplayed()

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
        composeRule.onNodeWithTag("note_card_$idA").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("note_preview_title").assertTextEquals("Alpha")

        // Go back
        goBackFromEditor()

        // Open note B
        composeRule.onNodeWithTag("note_card_$idB").performClick()
        composeRule.waitForIdle()

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
        composeRule.onNodeWithTag("note_card_$idA").performClick()
        composeRule.waitForIdle()

        // Existing notes open in preview mode; switch to edit mode
        composeRule.onNodeWithTag("note_mode_edit_button").performClick()
        composeRule.waitForIdle()

        // Append text to the body
        composeRule.onNodeWithTag("note_body_input").performTextInput(" and extra text")
        composeRule.waitForIdle()

        // Go back (saves changes)
        goBackFromEditor()

        // Open note A again
        composeRule.onNodeWithTag("note_card_$idA").performClick()
        composeRule.waitForIdle()

        // The preview contains the appended text
        composeRule.onNodeWithText("Body E and extra text", substring = true).assertIsDisplayed()
    }

    @Test
    fun `delete from editor moves the note to the trash and Undo brings it back`() {
        val idA = insertNote("Note F", "Body F")

        // Open note A
        composeRule.onNodeWithTag("note_card_$idA").performClick()
        composeRule.waitForIdle()

        // Open overflow menu
        composeRule.onNodeWithTag("note_editor_overflow_button").performClick()
        composeRule.waitForIdle()

        // Tap Delete
        composeRule.onNodeWithText("Delete").performClick()
        composeRule.waitForIdle()

        // List no longer shows it
        composeRule.onNodeWithTag("note_card_$idA").assertDoesNotExist()

        // DAO says isDeleted = true
        waitForNoteInDb { notes ->
            notes.any { it.id == idA && it.isDeleted }
        }
        val deletedNote = runBlocking(Dispatchers.IO) { db.noteDao().getNoteById(idA) }
        assertNotNull(deletedNote)
        assertTrue(deletedNote?.isDeleted == true)

        // Tap Undo snackbar action
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
        composeRule.onNodeWithTag("note_card_$idA").assertIsDisplayed()
    }
}
