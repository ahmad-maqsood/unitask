package com.example.util

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.Course
import com.example.data.local.Note
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
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
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalBackupNotesTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private val createdFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.getDatabase(context)
        runBlocking(Dispatchers.IO) { db.clearAllTables() }
    }

    @After
    fun tearDown() {
        db.close()
        // Forget the saved database so the next test builds a fresh one.
        AppDatabase::class.java.getDeclaredField("INSTANCE")
            .apply { isAccessible = true }
            .set(null, null)
        for (f in createdFiles) {
            if (f.exists()) f.delete()
        }
        val imageDir = File(context.filesDir, "note_images")
        if (imageDir.exists()) {
            imageDir.deleteRecursively()
        }
    }

    private fun createTempBackupFile(name: String): Pair<File, Uri> {
        val file = File(context.cacheDir, name)
        if (file.exists()) file.delete()
        file.createNewFile()
        createdFiles.add(file)
        return file to Uri.fromFile(file)
    }

    @Test
    fun testExportThenRestoreIntoEmptyDatabaseKeepsNoteProperties() = runBlocking {
        val courseDao = db.courseDao()
        val noteDao = db.noteDao()

        val courseId = courseDao.insertCourse(
            Course(
                name = "Data Structures",
                code = "CS 201",
                colorHex = "#4F46E5",
                location = "Hall 1",
                instructor = "Prof. Knuth"
            )
        )

        val noteId = noteDao.insert(
            Note(
                title = "Binary Search Trees",
                body = "# Notes\nBalanced trees are O(log n).",
                courseId = courseId,
                isPinned = true,
                createdAt = 1700000000000L,
                updatedAt = 1700001000000L
            )
        )
        assertTrue(noteId > 0L)

        val (_, uri) = createTempBackupFile("notes_backup.json")
        val exportResult = LocalBackupManager.export(context, uri, password = null)
        assertTrue("Export must succeed: $exportResult", exportResult is BackupResult.Success)
        val exportCounts = (exportResult as BackupResult.Success).counts
        assertEquals(1, exportCounts.notes)
        assertEquals(1, exportCounts.courses)

        // Wipe database to verify restore repopulates it
        withContext(Dispatchers.IO) { db.clearAllTables() }
        assertEquals(0, noteDao.getActiveNotesSync().size)
        assertEquals(0, courseDao.getAllCoursesList().size)

        // Restore backup
        val restoreResult = LocalBackupManager.restore(context, uri, password = null)
        assertTrue("Restore must succeed: $restoreResult", restoreResult is BackupResult.Success)
        val restoreCounts = (restoreResult as BackupResult.Success).counts
        assertEquals(1, restoreCounts.notes)

        val restoredNotes = noteDao.getActiveNotesSync()
        assertEquals(1, restoredNotes.size)
        val note = restoredNotes[0]
        assertEquals("Binary Search Trees", note.title)
        assertEquals("# Notes\nBalanced trees are O(log n).", note.body)
        assertTrue(note.isPinned)
        assertEquals(1700000000000L, note.createdAt)
        assertEquals(1700001000000L, note.updatedAt)
        assertFalse(note.isDeleted)

        // Verify course link restored
        assertNotNull(note.courseId)
        val restoredCourse = courseDao.getCourseById(note.courseId!!)
        assertNotNull(restoredCourse)
        assertEquals("CS 201", restoredCourse!!.code)
    }

    @Test
    fun testRestoringSameBackupTwiceDoesNotDuplicateNotes() = runBlocking {
        val noteDao = db.noteDao()
        noteDao.insert(
            Note(
                title = "Unique Note",
                body = "Body text",
                courseId = null,
                isPinned = false,
                createdAt = 1700000000000L
            )
        )

        val (_, uri) = createTempBackupFile("duplicate_test_backup.json")
        val exportResult = LocalBackupManager.export(context, uri, password = null)
        assertTrue(exportResult is BackupResult.Success)

        withContext(Dispatchers.IO) { db.clearAllTables() }

        // First restore: note should be added
        val firstRestore = LocalBackupManager.restore(context, uri, password = null)
        assertTrue(firstRestore is BackupResult.Success)
        assertEquals(1, (firstRestore as BackupResult.Success).counts.notes)
        assertEquals(1, noteDao.getActiveNotesSync().size)

        // Second restore: duplicate note should be skipped
        val secondRestore = LocalBackupManager.restore(context, uri, password = null)
        assertTrue(secondRestore is BackupResult.Success)
        assertEquals(0, (secondRestore as BackupResult.Success).counts.notes)
        assertEquals(1, noteDao.getActiveNotesSync().size)
    }

    @Test
    fun testRestoringOldBackupWithoutNotesArrayWorksAndGivesZeroNotes() = runBlocking {
        val noteDao = db.noteDao()

        // Create an old backup envelope without "notes" key in "data"
        val oldData = JSONObject()
            .put("courses", JSONArray())
            .put("slots", JSONArray())
            .put("tasks", JSONArray())

        val envelope = JSONObject()
            .put("format", "unitask-backup")
            .put("version", 1)
            .put("app", "UniTask")
            .put("exportedAt", System.currentTimeMillis())
            .put("encrypted", false)
            .put("data", oldData)

        val (file, uri) = createTempBackupFile("old_format_backup.json")
        file.writeText(envelope.toString(2))

        val restoreResult = LocalBackupManager.restore(context, uri, password = null)
        assertTrue("Restore of old format must succeed: $restoreResult", restoreResult is BackupResult.Success)
        val counts = (restoreResult as BackupResult.Success).counts
        assertEquals(0, counts.notes)
        assertEquals(0, noteDao.getActiveNotesSync().size)
    }

    @Test
    fun testNoteWhoseCourseDoesNotExistRestoresWithNoCourse() = runBlocking {
        val noteDao = db.noteDao()

        // Construct backup where note refers to nonexistent course code
        val noteObj = JSONObject()
            .put("title", "Orphan Note")
            .put("body", "Content of orphan note")
            .put("courseCode", "NONEXISTENT_COURSE")
            .put("isPinned", false)
            .put("createdAt", 1700000000000L)
            .put("updatedAt", 1700000000000L)

        val data = JSONObject()
            .put("courses", JSONArray())
            .put("slots", JSONArray())
            .put("tasks", JSONArray())
            .put("notes", JSONArray().put(noteObj))

        val envelope = JSONObject()
            .put("format", "unitask-backup")
            .put("version", 1)
            .put("app", "UniTask")
            .put("exportedAt", System.currentTimeMillis())
            .put("encrypted", false)
            .put("data", data)

        val (file, uri) = createTempBackupFile("orphan_note_backup.json")
        file.writeText(envelope.toString(2))

        val restoreResult = LocalBackupManager.restore(context, uri, password = null)
        assertTrue(restoreResult is BackupResult.Success)
        val counts = (restoreResult as BackupResult.Success).counts
        assertEquals(1, counts.notes)

        val restoredNotes = noteDao.getActiveNotesSync()
        assertEquals(1, restoredNotes.size)
        val note = restoredNotes[0]
        assertEquals("Orphan Note", note.title)
        assertNull("Note with nonexistent course must restore with null courseId", note.courseId)
    }

    @Test
    fun testTrashedNotesAreNotExported() = runBlocking {
        val noteDao = db.noteDao()

        noteDao.insert(
            Note(
                title = "Active Note",
                body = "Active note body",
                isDeleted = false
            )
        )
        noteDao.insert(
            Note(
                title = "Trashed Note",
                body = "Trashed note body",
                isDeleted = true,
                deletedAt = System.currentTimeMillis()
            )
        )

        assertEquals(1, noteDao.getActiveNotesSync().size)
        assertEquals(2, noteDao.getAllNotesSync().size)

        val (_, uri) = createTempBackupFile("trashed_notes_test.json")
        val exportResult = LocalBackupManager.export(context, uri, password = null)
        assertTrue(exportResult is BackupResult.Success)
        val exportCounts = (exportResult as BackupResult.Success).counts
        assertEquals(1, exportCounts.notes)

        withContext(Dispatchers.IO) { db.clearAllTables() }

        val restoreResult = LocalBackupManager.restore(context, uri, password = null)
        assertTrue(restoreResult is BackupResult.Success)
        assertEquals(1, (restoreResult as BackupResult.Success).counts.notes)

        val allRestored = noteDao.getAllNotesSync()
        assertEquals(1, allRestored.size)
        assertEquals("Active Note", allRestored[0].title)
    }

    @Test
    fun testNoteWithImageRoundTripsSuccessfully() = runBlocking {
        val noteDao = db.noteDao()
        val imageName = "roundtrip_photo_01.jpg"
        val imageDir = File(context.filesDir, "note_images")
        if (!imageDir.exists()) imageDir.mkdirs()
        val imageFile = File(imageDir, imageName)
        val fileBytes = byteArrayOf(11, 22, 33, 44, 55, 66, 77, 88)
        imageFile.writeBytes(fileBytes)
        createdFiles.add(imageFile)

        val noteId = noteDao.insert(
            Note(
                title = "Photo Note",
                body = "Here is an image: ![](unitask-image:$imageName)",
                isPinned = false,
                createdAt = 1700000000000L,
                updatedAt = 1700000000000L
            )
        )
        assertTrue(noteId > 0L)

        val (_, uri) = createTempBackupFile("image_roundtrip_backup.json")
        val exportResult = LocalBackupManager.export(context, uri, password = null)
        assertTrue(exportResult is BackupResult.Success)
        val exportCounts = (exportResult as BackupResult.Success).counts
        assertEquals(1, exportCounts.notes)
        assertEquals(1, exportCounts.images)
        assertEquals(0, exportCounts.skippedImages)

        // Delete the file on disk and wipe the database
        assertTrue(imageFile.delete())
        assertFalse(imageFile.exists())
        withContext(Dispatchers.IO) { db.clearAllTables() }
        assertEquals(0, noteDao.getActiveNotesSync().size)

        // Restore backup
        val restoreResult = LocalBackupManager.restore(context, uri, password = null)
        assertTrue(restoreResult is BackupResult.Success)
        val restoreCounts = (restoreResult as BackupResult.Success).counts
        assertEquals(1, restoreCounts.notes)
        assertEquals(1, restoreCounts.images)

        // The file exists again with the exact same bytes
        assertTrue("Restored image file must exist", imageFile.exists())
        val restoredBytes = imageFile.readBytes()
        assertTrue("Restored bytes must match original bytes", fileBytes.contentEquals(restoredBytes))
    }

    @Test
    fun testEntryWithInvalidNameIsIgnored() = runBlocking {
        val evilBytes = byteArrayOf(1, 2, 3)
        val base64Data = Base64.encodeToString(evilBytes, Base64.NO_WRAP)
        val evilData = JSONObject()
            .put("courses", JSONArray())
            .put("slots", JSONArray())
            .put("tasks", JSONArray())
            .put("notes", JSONArray())
            .put(
                "noteImages",
                JSONArray().put(
                    JSONObject()
                        .put("name", "../evil.jpg")
                        .put("data", base64Data)
                )
            )

        val envelope = JSONObject()
            .put("format", "unitask-backup")
            .put("version", 1)
            .put("app", "UniTask")
            .put("exportedAt", System.currentTimeMillis())
            .put("encrypted", false)
            .put("data", evilData)

        val (file, uri) = createTempBackupFile("evil_backup.json")
        file.writeText(envelope.toString(2))

        val restoreResult = LocalBackupManager.restore(context, uri, password = null)
        assertTrue(restoreResult is BackupResult.Success)
        val counts = (restoreResult as BackupResult.Success).counts
        assertEquals(0, counts.images)

        val evilFile = File(context.filesDir, "evil.jpg")
        assertFalse(evilFile.exists())
    }

    @Test
    fun testOversizedEntryIsIgnored() = runBlocking {
        // Limit is 5 MB (5 * 1024 * 1024). 5 MB + 10 bytes exceeds it.
        val oversizedBytes = ByteArray(5 * 1024 * 1024 + 10)
        val base64Data = Base64.encodeToString(oversizedBytes, Base64.NO_WRAP)
        val imageName = "oversized_image_12.jpg"

        val data = JSONObject()
            .put("courses", JSONArray())
            .put("slots", JSONArray())
            .put("tasks", JSONArray())
            .put("notes", JSONArray())
            .put(
                "noteImages",
                JSONArray().put(
                    JSONObject()
                        .put("name", imageName)
                        .put("data", base64Data)
                )
            )

        val envelope = JSONObject()
            .put("format", "unitask-backup")
            .put("version", 1)
            .put("app", "UniTask")
            .put("exportedAt", System.currentTimeMillis())
            .put("encrypted", false)
            .put("data", data)

        val (file, uri) = createTempBackupFile("oversized_backup.json")
        file.writeText(envelope.toString(2))

        val restoreResult = LocalBackupManager.restore(context, uri, password = null)
        assertTrue(restoreResult is BackupResult.Success)
        val counts = (restoreResult as BackupResult.Success).counts
        assertEquals(0, counts.images)

        val targetFile = File(File(context.filesDir, "note_images"), imageName)
        assertFalse(targetFile.exists())
    }

    @Test
    fun testExportWithLoweredMaxImageBytesSkipsImagesAndReportsSkipped() = runBlocking {
        val noteDao = db.noteDao()
        val imageName = "capped_image_123.jpg"
        val imageDir = File(context.filesDir, "note_images")
        if (!imageDir.exists()) imageDir.mkdirs()
        val imageFile = File(imageDir, imageName)
        val sampleBytes = ByteArray(1500) { 7 }
        imageFile.writeBytes(sampleBytes)
        createdFiles.add(imageFile)

        noteDao.insert(
            Note(
                title = "Note with Large Image",
                body = "![](unitask-image:$imageName)",
                isPinned = false,
                createdAt = 1700000000000L
            )
        )

        val (_, uri) = createTempBackupFile("capped_image_backup.json")
        val exportResult = LocalBackupManager.export(
            context,
            uri,
            password = null,
            maxImageBytes = 500L
        )
        assertTrue(exportResult is BackupResult.Success)
        val counts = (exportResult as BackupResult.Success).counts
        assertEquals(1, counts.notes)
        assertEquals(0, counts.images)
        assertEquals(1, counts.skippedImages)
    }

    @Test
    fun testOldBackupWithoutNoteImagesStillRestores() = runBlocking {
        val noteDao = db.noteDao()

        val noteObj = JSONObject()
            .put("title", "Old Note")
            .put("body", "Body without any images")
            .put("courseCode", "")
            .put("isPinned", false)
            .put("createdAt", 1700000000000L)
            .put("updatedAt", 1700000000000L)

        // data contains notes but NO "noteImages"
        val data = JSONObject()
            .put("courses", JSONArray())
            .put("slots", JSONArray())
            .put("tasks", JSONArray())
            .put("notes", JSONArray().put(noteObj))

        val envelope = JSONObject()
            .put("format", "unitask-backup")
            .put("version", 1)
            .put("app", "UniTask")
            .put("exportedAt", System.currentTimeMillis())
            .put("encrypted", false)
            .put("data", data)

        val (file, uri) = createTempBackupFile("old_no_images_backup.json")
        file.writeText(envelope.toString(2))

        val restoreResult = LocalBackupManager.restore(context, uri, password = null)
        assertTrue(restoreResult is BackupResult.Success)
        val counts = (restoreResult as BackupResult.Success).counts
        assertEquals(1, counts.notes)
        assertEquals(0, counts.images)
        assertEquals(0, counts.skippedImages)

        val activeNotes = noteDao.getActiveNotesSync()
        assertEquals(1, activeNotes.size)
        assertEquals("Old Note", activeNotes[0].title)
    }
}

