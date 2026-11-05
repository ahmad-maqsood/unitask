package com.example.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
class NoteDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var noteDao: NoteDao
    private lateinit var courseDao: CourseDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        noteDao = db.noteDao()
        courseDao = db.courseDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testPinnedNotesSortFirstThenNewestFirst() = runBlocking {
        // Insert unpinned notes
        val unpinnedOld = noteDao.insert(
            Note(title = "Unpinned Old", isPinned = false, updatedAt = 1000L)
        )
        val unpinnedNew = noteDao.insert(
            Note(title = "Unpinned New", isPinned = false, updatedAt = 3000L)
        )
        // Insert pinned notes
        val pinnedOld = noteDao.insert(
            Note(title = "Pinned Old", isPinned = true, updatedAt = 2000L)
        )
        val pinnedNew = noteDao.insert(
            Note(title = "Pinned New", isPinned = true, updatedAt = 4000L)
        )

        val notes = noteDao.observeNotes().first()
        assertEquals(4, notes.size)
        // Pinned notes come first, ordered by updatedAt DESC
        assertEquals(pinnedNew, notes[0].note.id)
        assertEquals(pinnedOld, notes[1].note.id)
        // Then unpinned notes, ordered by updatedAt DESC
        assertEquals(unpinnedNew, notes[2].note.id)
        assertEquals(unpinnedOld, notes[3].note.id)
    }

    @Test
    fun testSoftDeleteHidesNoteFromActiveAndShowsInDeleted() = runBlocking {
        val noteId = noteDao.insert(Note(title = "Active Note", updatedAt = 1000L))

        assertEquals(1, noteDao.observeNotes().first().size)
        assertTrue(noteDao.observeDeletedNotes().first().isEmpty())

        val deleteTimestamp = 5000L
        noteDao.softDelete(id = noteId, deletedAt = deleteTimestamp)

        val activeNotes = noteDao.observeNotes().first()
        assertTrue(activeNotes.isEmpty())

        val deletedNotes = noteDao.observeDeletedNotes().first()
        assertEquals(1, deletedNotes.size)
        val deletedItem = deletedNotes.first().note
        assertEquals(noteId, deletedItem.id)
        assertTrue(deletedItem.isDeleted)
        assertEquals(deleteTimestamp, deletedItem.deletedAt)
        assertEquals(false, deletedItem.isSynced)
    }

    @Test
    fun testRestoreBringsDeletedNoteBack() = runBlocking {
        val noteId = noteDao.insert(Note(title = "To be restored", updatedAt = 1000L))
        noteDao.softDelete(id = noteId, deletedAt = 5000L)

        assertTrue(noteDao.observeNotes().first().isEmpty())
        assertEquals(1, noteDao.observeDeletedNotes().first().size)

        noteDao.restore(id = noteId)

        val activeNotes = noteDao.observeNotes().first()
        assertEquals(1, activeNotes.size)
        val restored = activeNotes.first().note
        assertEquals(noteId, restored.id)
        assertEquals(false, restored.isDeleted)
        assertEquals(0L, restored.deletedAt)
        assertEquals(false, restored.isSynced)

        assertTrue(noteDao.observeDeletedNotes().first().isEmpty())
    }

    @Test
    fun testPurgeDeletedBeforeRemovesOnlyOldDeletedNotesAndReturnsCount() = runBlocking {
        // Active note (should not be purged)
        val activeId = noteDao.insert(Note(title = "Active", isDeleted = false, deletedAt = 0L))

        // Deleted notes with deletedAt before cutoff (cutoff = 2000L)
        val oldDeleted1 = noteDao.insert(Note(title = "Old Deleted 1", isDeleted = true, deletedAt = 500L))
        val oldDeleted2 = noteDao.insert(Note(title = "Old Deleted 2", isDeleted = true, deletedAt = 1500L))

        // Deleted note at or after cutoff (should not be purged)
        val recentDeleted = noteDao.insert(Note(title = "Recent Deleted", isDeleted = true, deletedAt = 2500L))

        // Deleted note with deletedAt = 0 (should not be purged because deletedAt must be > 0)
        val zeroDeletedAt = noteDao.insert(Note(title = "Zero Deleted At", isDeleted = true, deletedAt = 0L))

        val purgedCount = noteDao.purgeDeletedBefore(cutoff = 2000L)
        assertEquals(2, purgedCount)

        // The two old deleted notes were purged
        assertNull(noteDao.getNoteById(oldDeleted1))
        assertNull(noteDao.getNoteById(oldDeleted2))

        // Others still exist in the database
        assertNotNull(noteDao.getNoteById(activeId))
        assertNotNull(noteDao.getNoteById(recentDeleted))
        assertNotNull(noteDao.getNoteById(zeroDeletedAt))
    }

    @Test
    fun testDetachCoursesSetsCourseIdToNullForMatchingNotesOnly() = runBlocking {
        val course1Id = courseDao.insertCourse(Course(name = "Course 1", code = "C1", colorHex = "#111111"))
        val course2Id = courseDao.insertCourse(Course(name = "Course 2", code = "C2", colorHex = "#222222"))
        val course3Id = courseDao.insertCourse(Course(name = "Course 3", code = "C3", colorHex = "#333333"))

        val n1 = noteDao.insert(Note(title = "N1", courseId = course1Id, isSynced = true))
        val n2 = noteDao.insert(Note(title = "N2", courseId = course2Id, isSynced = true))
        val n3 = noteDao.insert(Note(title = "N3", courseId = course3Id, isSynced = true))

        noteDao.detachCourses(listOf(course1Id, course2Id))

        val retrieved1 = noteDao.getNoteById(n1)
        val retrieved2 = noteDao.getNoteById(n2)
        val retrieved3 = noteDao.getNoteById(n3)

        assertNotNull(retrieved1)
        assertNull(retrieved1?.courseId)
        assertEquals(false, retrieved1?.isSynced)

        assertNotNull(retrieved2)
        assertNull(retrieved2?.courseId)
        assertEquals(false, retrieved2?.isSynced)

        assertNotNull(retrieved3)
        assertEquals(course3Id, retrieved3?.courseId)
        assertEquals(true, retrieved3?.isSynced)
    }

    @Test
    fun testNoteWithSoftDeletedCourseShowsNullCourseInJoin() = runBlocking {
        val courseId = courseDao.insertCourse(
            Course(name = "Operating Systems", code = "CS 301", colorHex = "#123456")
        )
        val noteId = noteDao.insert(
            Note(title = "Processes", courseId = courseId)
        )

        // Before soft deleting the course, join includes course details
        val initialList = noteDao.observeNotes().first()
        assertEquals(1, initialList.size)
        assertEquals("CS 301", initialList[0].courseCode)
        assertEquals("Operating Systems", initialList[0].courseName)
        assertEquals("#123456", initialList[0].courseColorHex)

        // Soft delete the course
        courseDao.softDeleteCourse(courseId)

        // Now observeNotes still returns the note, but course fields are null
        val afterCourseDelete = noteDao.observeNotes().first()
        assertEquals(1, afterCourseDelete.size)
        assertEquals(noteId, afterCourseDelete[0].note.id)
        assertNull(afterCourseDelete[0].courseCode)
        assertNull(afterCourseDelete[0].courseName)
        assertNull(afterCourseDelete[0].courseColorHex)
        // The foreign courseId on the note itself is untouched
        assertEquals(courseId, afterCourseDelete[0].note.courseId)
    }

    @Test
    fun testReassignCourseMovesNotes() = runBlocking {
        val oldCourseId = courseDao.insertCourse(Course(name = "Old Course", code = "OLD 100", colorHex = "#111111"))
        val newCourseId = courseDao.insertCourse(Course(name = "New Course", code = "NEW 200", colorHex = "#222222"))
        val untouchedCourseId = courseDao.insertCourse(Course(name = "Other Course", code = "OTH 300", colorHex = "#333333"))

        val n1 = noteDao.insert(Note(title = "N1", courseId = oldCourseId, isSynced = true))
        val n2 = noteDao.insert(Note(title = "N2", courseId = oldCourseId, isSynced = true))
        val n3 = noteDao.insert(Note(title = "N3", courseId = untouchedCourseId, isSynced = true))

        noteDao.reassignCourse(oldCourseId = oldCourseId, newCourseId = newCourseId)

        val retrieved1 = noteDao.getNoteById(n1)
        val retrieved2 = noteDao.getNoteById(n2)
        val retrieved3 = noteDao.getNoteById(n3)

        assertEquals(newCourseId, retrieved1?.courseId)
        assertEquals(false, retrieved1?.isSynced)

        assertEquals(newCourseId, retrieved2?.courseId)
        assertEquals(false, retrieved2?.isSynced)

        assertEquals(untouchedCourseId, retrieved3?.courseId)
        assertEquals(true, retrieved3?.isSynced)
    }
}

