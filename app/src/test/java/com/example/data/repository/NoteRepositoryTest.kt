package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.Course
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NoteRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: NoteRepository
    private var courseId = 0L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = NoteRepository(db.noteDao())
        courseId = runBlocking {
            db.courseDao().insertCourse(Course(name = "Test", code = "T 100", colorHex = "#4F46E5"))
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun blankNewNoteIsNotSaved() = runBlocking {
        val id1 = repo.saveNote(id = 0, title = "", body = "", courseId = null)
        assertEquals(0L, id1)

        val id2 = repo.saveNote(id = 0, title = "   ", body = "   \n\t ", courseId = courseId)
        assertEquals(0L, id2)

        assertTrue(repo.observeNotes().first().isEmpty())
        assertTrue(repo.observeDeletedNotes().first().isEmpty())
    }

    @Test
    fun savingNewNoteReturnsIdAndStoresIt() = runBlocking {
        val id = repo.saveNote(id = 0, title = "Lecture 1", body = "Graph algorithms", courseId = courseId)
        assertTrue(id > 0)

        val note = repo.getNote(id)
        assertNotNull(note)
        assertEquals("Lecture 1", note?.title)
        assertEquals("Graph algorithms", note?.body)
        assertEquals(courseId, note?.courseId)
        assertFalse(note!!.isDeleted)
        assertFalse(note.isSynced)
        assertTrue(note.createdAt > 0)
        assertEquals(note.createdAt, note.updatedAt)

        val activeList = repo.observeNotes().first()
        assertEquals(1, activeList.size)
        assertEquals(id, activeList[0].note.id)
    }

    @Test
    fun unchangedSaveDoesNotChangeUpdatedAt() = runBlocking {
        val id = repo.saveNote(id = 0, title = "Original Title", body = "Original Body", courseId = courseId)
        val original = repo.getNote(id)!!
        val originalUpdatedAt = original.updatedAt

        val returnedId = repo.saveNote(id = id, title = "Original Title", body = "Original Body", courseId = courseId)
        assertEquals(id, returnedId)

        val afterSave = repo.getNote(id)!!
        assertEquals(originalUpdatedAt, afterSave.updatedAt)
    }

    @Test
    fun changedSaveBumpsUpdatedAtAndSetsIsSyncedFalse() = runBlocking {
        val id = repo.saveNote(id = 0, title = "Old Title", body = "Body", courseId = courseId)
        val original = repo.getNote(id)!!

        db.noteDao().update(original.copy(updatedAt = 1000L, isSynced = true))
        val beforeUpdate = repo.getNote(id)!!
        assertEquals(1000L, beforeUpdate.updatedAt)
        assertTrue(beforeUpdate.isSynced)

        val returnedId = repo.saveNote(id = id, title = "New Title", body = "Body", courseId = courseId)
        assertEquals(id, returnedId)

        val afterUpdate = repo.getNote(id)!!
        assertEquals("New Title", afterUpdate.title)
        assertTrue(afterUpdate.updatedAt > 1000L)
        assertFalse(afterUpdate.isSynced)
    }

    @Test
    fun clearingExistingNoteMovesItToTrashInsteadOfDeleting() = runBlocking {
        val id = repo.saveNote(id = 0, title = "Important Note", body = "Do not lose", courseId = courseId)
        val initialNote = repo.getNote(id)
        assertNotNull(initialNote)
        assertFalse(initialNote!!.isDeleted)

        val returnedId = repo.saveNote(id = id, title = "   ", body = "", courseId = courseId)
        assertEquals(id, returnedId)

        val softDeletedNote = repo.getNote(id)
        assertNotNull(softDeletedNote)
        assertTrue(softDeletedNote!!.isDeleted)
        assertTrue(softDeletedNote.deletedAt > 0)

        assertTrue(repo.observeNotes().first().isEmpty())
        val deletedList = repo.observeDeletedNotes().first()
        assertEquals(1, deletedList.size)
        assertEquals(id, deletedList[0].note.id)
    }

    @Test
    fun lengthLimitsAreEnforced() = runBlocking {
        val longTitle = "A".repeat(NoteRepository.MAX_TITLE + 50)
        val longBody = "B".repeat(NoteRepository.MAX_BODY + 50)

        val id = repo.saveNote(id = 0, title = longTitle, body = longBody, courseId = null)
        val note = repo.getNote(id)!!

        assertEquals(NoteRepository.MAX_TITLE, note.title.length)
        assertEquals("A".repeat(NoteRepository.MAX_TITLE), note.title)
        assertEquals(NoteRepository.MAX_BODY, note.body.length)
        assertEquals("B".repeat(NoteRepository.MAX_BODY), note.body)
    }

    @Test
    fun moveToTrashAndRestoreFromTrash() = runBlocking {
        val id = repo.saveNote(id = 0, title = "Note to Trash", body = "Body", courseId = null)

        repo.moveToTrash(id)
        val trashed = repo.getNote(id)!!
        assertTrue(trashed.isDeleted)
        assertTrue(trashed.deletedAt > 0)
        assertTrue(repo.observeNotes().first().isEmpty())
        assertEquals(1, repo.observeDeletedNotes().first().size)

        repo.restoreFromTrash(id)
        val restored = repo.getNote(id)!!
        assertFalse(restored.isDeleted)
        assertEquals(0L, restored.deletedAt)
        assertEquals(1, repo.observeNotes().first().size)
        assertTrue(repo.observeDeletedNotes().first().isEmpty())
    }

    @Test
    fun purgeOldTrashRemovesOnlyNotesDeletedMoreThan30DaysAgo() = runBlocking {
        val now = 100_000_000_000L
        val thirtyDaysMillis = NoteRepository.TRASH_DAYS * 24L * 60L * 60L * 1000L

        // Active note (must survive)
        val activeId = repo.saveNote(id = 0, title = "Active", body = "Active note", courseId = null)

        // Note deleted 31 days ago (older than 30 days: must be purged)
        val oldDeletedId = repo.saveNote(id = 0, title = "Old Deleted", body = "Old deleted note", courseId = null)
        db.noteDao().softDelete(oldDeletedId, deletedAt = now - thirtyDaysMillis - 1000L)

        // Note deleted 10 days ago (recent: must survive)
        val recentDeletedId = repo.saveNote(id = 0, title = "Recent Deleted", body = "Recent deleted note", courseId = null)
        db.noteDao().softDelete(recentDeletedId, deletedAt = now - (10L * 24 * 60 * 60 * 1000L))

        val purgedCount = repo.purgeOldTrash(now = now)
        assertEquals(1, purgedCount)

        assertNotNull(repo.getNote(activeId))
        assertNull(repo.getNote(oldDeletedId))
        assertNotNull(repo.getNote(recentDeletedId))
    }

    @Test
    fun setPinnedUpdatesPinnedStatusAndBumpsUpdatedAt() = runBlocking {
        val id = repo.saveNote(id = 0, title = "Pinned Test", body = "Body", courseId = null)
        assertFalse(repo.getNote(id)!!.isPinned)

        db.noteDao().update(repo.getNote(id)!!.copy(updatedAt = 1000L, isSynced = true))

        repo.setPinned(id, true)
        val pinned = repo.getNote(id)!!
        assertTrue(pinned.isPinned)
        assertTrue(pinned.updatedAt > 1000L)
        assertFalse(pinned.isSynced)

        repo.setPinned(id, false)
        val unpinned = repo.getNote(id)!!
        assertFalse(unpinned.isPinned)
    }

    @Test
    fun deleteForeverRemovesNoteCompletely() = runBlocking {
        val id = repo.saveNote(id = 0, title = "Forever", body = "Body", courseId = null)
        assertNotNull(repo.getNote(id))

        repo.deleteForever(id)
        assertNull(repo.getNote(id))
    }
}

