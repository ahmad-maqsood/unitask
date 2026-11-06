package com.example.ui

import android.app.Application
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.Note
import com.example.ui.screens.NoteEditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NoteEditorViewModelTest {

    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private lateinit var vm: NoteEditorViewModel

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext<Application>()
        db = AppDatabase.getDatabase(app)
        runBlocking(Dispatchers.IO) {
            db.clearAllTables()
        }
        vm = NoteEditorViewModel(app)
    }

    @After
    fun tearDown() {
        runBlocking(Dispatchers.IO) {
            db.clearAllTables()
        }
    }

    private fun waitForLoaded(viewModel: NoteEditorViewModel) {
        val start = System.currentTimeMillis()
        while (!viewModel.isLoaded && System.currentTimeMillis() - start < 3000) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(10)
        }
    }

    @Test
    fun loadNoteAThenLoadNullWithNewSessionClearsState() = runBlocking {
        val noteId = db.noteDao().insert(
            Note(
                title = "Note A Title",
                body = "Note A Body",
                courseId = null,
                isPinned = false,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )

        vm.loadNote(noteId, session = 1)
        waitForLoaded(vm)

        assertEquals("Note A Title", vm.title)
        assertEquals("Note A Body", vm.bodyValue.text)
        assertEquals(noteId, vm.currentId)

        vm.loadNote(null, session = 2)

        assertEquals("", vm.title)
        assertEquals("", vm.bodyValue.text)
        assertEquals(0L, vm.currentId)
        assertTrue(vm.isLoaded)
    }

    @Test
    fun loadNoteWithSameSessionTwiceDoesNotReload() {
        vm.loadNote(null, session = 1)
        assertTrue(vm.isLoaded)

        vm.updateTitle("Typed Title")
        vm.updateBody(TextFieldValue("Typed Body"))

        // Re-call with same session (rotation case)
        vm.loadNote(null, session = 1)

        assertEquals("Typed Title", vm.title)
        assertEquals("Typed Body", vm.bodyValue.text)
    }

    @Test
    fun switchingSessionsClearsUndoHistory() {
        vm.loadNote(null, session = 1)
        assertTrue(vm.isLoaded)

        vm.updateBody(TextFieldValue("Step 1"))
        vm.checkpointBeforeToolbar()
        vm.updateBody(TextFieldValue("Step 2"))

        assertTrue(vm.canUndo)

        // Switch to a new session
        vm.loadNote(null, session = 2)

        assertFalse(vm.canUndo)
        assertFalse(vm.canRedo)
        assertFalse(vm.editHistory.canUndo)
        assertFalse(vm.editHistory.canRedo)
    }
}
