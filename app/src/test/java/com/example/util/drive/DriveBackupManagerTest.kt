package com.example.util.drive

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.Course
import com.example.data.local.MY_TASKS_COURSE_CODE
import com.example.data.local.Note
import com.example.data.local.TaskAttachment
import com.example.data.local.TaskItem
import com.example.ui.screens.DriveBackupViewModel
import com.example.util.BackupResult
import com.example.util.NoteImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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
class DriveBackupManagerTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = AppDatabase.getDatabase(context)
        runBlocking(Dispatchers.IO) { db.clearAllTables() }
        context.getSharedPreferences("drive_backup_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val attDirSetup = File(context.filesDir, "attachments")
        if (attDirSetup.exists()) {
            attDirSetup.deleteRecursively()
        }
    }

    @After
    fun tearDown() {
        db.close()
        AppDatabase::class.java.getDeclaredField("INSTANCE")
            .apply { isAccessible = true }
            .set(null, null)
        val imageDir = File(context.filesDir, "note_images")
        if (imageDir.exists()) {
            imageDir.deleteRecursively()
        }
        val attDir = File(context.filesDir, "attachments")
        if (attDir.exists()) {
            attDir.deleteRecursively()
        }
        context.getSharedPreferences("drive_backup_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun slotRotation_threeBackupsCycleSlotsWithIncrementingSeq() = runBlocking {
        val courseDao = db.courseDao()
        val taskDao = db.taskDao()
        val fakeStore = FakeDriveStore()

        val cId = courseDao.insertCourse(Course(name = "Math", code = "MATH 101", colorHex = "#123456"))
        taskDao.insertTask(TaskItem(courseId = cId, title = "HW 1", dueDateEpochMillis = 1000L))

        // First backup: creates slot 1 with seq 1
        val res1 = DriveBackupManager.backUp(context, fakeStore)
        assertTrue(res1 is DriveBackupResult.Success)
        assertEquals(1L, (res1 as DriveBackupResult.Success).info.seq)
        val slot1 = fakeStore.files.values.firstOrNull { it.name == "unitask-backup-1.json" }
        assertNotNull(slot1)
        assertEquals("1", slot1?.props?.get("seq"))
        assertNull(fakeStore.files.values.firstOrNull { it.name == "unitask-backup-2.json" })

        // Second backup after data change: creates slot 2 with seq 2
        taskDao.insertTask(TaskItem(courseId = cId, title = "HW 2", dueDateEpochMillis = 2000L))
        val res2 = DriveBackupManager.backUp(context, fakeStore)
        assertTrue(res2 is DriveBackupResult.Success)
        assertEquals(2L, (res2 as DriveBackupResult.Success).info.seq)
        val slot2 = fakeStore.files.values.firstOrNull { it.name == "unitask-backup-2.json" }
        assertNotNull(slot2)
        assertEquals("2", slot2?.props?.get("seq"))
        assertEquals("1", slot1?.props?.get("seq"))

        // Third backup: writes into older slot 1 with seq 3, slot 2 untouched
        val slot2BytesBefore = slot2!!.bytes.copyOf()
        taskDao.insertTask(TaskItem(courseId = cId, title = "HW 3", dueDateEpochMillis = 3000L))
        // rotateAfterMs = 0 means the history slot is always due to swap, as before the daily rule
        val res3 = DriveBackupManager.backUp(context, fakeStore, rotateAfterMs = 0L)
        assertTrue(res3 is DriveBackupResult.Success)
        assertEquals(3L, (res3 as DriveBackupResult.Success).info.seq)
        assertEquals("3", slot1?.props?.get("seq"))
        assertEquals("2", slot2.props["seq"])
        assertTrue(slot2BytesBefore.contentEquals(slot2.bytes))
    }

    @Test
    fun quickBackups_overwriteNewestSlot_andKeepOlderSlotAsHistory() = runBlocking {
        val courseDao = db.courseDao()
        val taskDao = db.taskDao()
        val fakeStore = FakeDriveStore()

        val cId = courseDao.insertCourse(Course(name = "Math", code = "MATH 101", colorHex = "#123456"))
        taskDao.insertTask(TaskItem(courseId = cId, title = "HW 1", dueDateEpochMillis = 1000L))

        DriveBackupManager.backUp(context, fakeStore) // slot 1, seq 1
        taskDao.insertTask(TaskItem(courseId = cId, title = "HW 2", dueDateEpochMillis = 2000L))
        DriveBackupManager.backUp(context, fakeStore) // slot 2, seq 2 (history is slot 1)

        val slot1 = fakeStore.files.values.first { it.name == "unitask-backup-1.json" }
        val slot1Before = slot1.bytes.copyOf()

        // Several quick backups: all go into slot 2, slot 1 stays as the history copy
        for (i in 3..5) {
            taskDao.insertTask(TaskItem(courseId = cId, title = "HW $i", dueDateEpochMillis = i * 1000L))
            val res = DriveBackupManager.backUp(context, fakeStore)
            assertTrue(res is DriveBackupResult.Success)
            assertEquals(i.toLong(), (res as DriveBackupResult.Success).info.seq)
        }
        val slot2 = fakeStore.files.values.first { it.name == "unitask-backup-2.json" }
        assertEquals("5", slot2.props["seq"])
        assertEquals("1", slot1.props["seq"])
        assertTrue("History slot must be untouched", slot1Before.contentEquals(slot1.bytes))

        // The newest copy is still the one that restores
        withContext(Dispatchers.IO) { db.clearAllTables() }
        assertTrue(DriveBackupManager.restore(context, fakeStore) is BackupResult.Success)
        assertEquals(5, taskDao.getAllActiveTasksSync().size)
    }

    @Test
    fun restoreOlderBackup_restoresOnlyThatSlot_noFallbackToNewer() = runBlocking {
        val courseDao = db.courseDao()
        val taskDao = db.taskDao()
        val fakeStore = FakeDriveStore()

        val cId = courseDao.insertCourse(Course(name = "Math", code = "MATH 101", colorHex = "#123456"))
        taskDao.insertTask(TaskItem(courseId = cId, title = "HW 1", dueDateEpochMillis = 1000L))
        DriveBackupManager.backUp(context, fakeStore) // slot 1, seq 1, one task
        taskDao.insertTask(TaskItem(courseId = cId, title = "HW 2", dueDateEpochMillis = 2000L))
        DriveBackupManager.backUp(context, fakeStore) // slot 2, seq 2, two tasks

        val backups = DriveBackupManager.listBackups(fakeStore)
        assertEquals(listOf(2L, 1L), backups.map { it.seq })
        assertEquals(listOf(2, 1), backups.map { it.tasks })
        val older = backups[1]

        // Restoring the older backup brings back one task, not two
        withContext(Dispatchers.IO) { db.clearAllTables() }
        val res = DriveBackupManager.restore(context, fakeStore, slotFileId = older.slotFileId)
        assertTrue(res is BackupResult.Success)
        assertEquals(1, taskDao.getAllActiveTasksSync().size)

        // A damaged older backup fails and does not fall back to the newer one
        withContext(Dispatchers.IO) { db.clearAllTables() }
        fakeStore.files[older.slotFileId]!!.bytes = "corrupted".toByteArray()
        val damaged = DriveBackupManager.restore(context, fakeStore, slotFileId = older.slotFileId)
        assertTrue(damaged is BackupResult.Failure)
        assertEquals(0, taskDao.getAllActiveTasksSync().size)

        // An id that is not on Drive fails with a message
        val missing = DriveBackupManager.restore(context, fakeStore, slotFileId = "does-not-exist")
        assertTrue(missing is BackupResult.Failure)
    }

    @Test
    fun listBackups_withOneSlot_returnsOneEntry() = runBlocking {
        val fakeStore = FakeDriveStore()
        val cId = db.courseDao().insertCourse(Course(name = "Math", code = "MATH 101", colorHex = "#123456"))
        db.taskDao().insertTask(TaskItem(courseId = cId, title = "HW 1", dueDateEpochMillis = 1000L))
        assertEquals(0, DriveBackupManager.listBackups(fakeStore).size)
        DriveBackupManager.backUp(context, fakeStore)
        assertEquals(1, DriveBackupManager.listBackups(fakeStore).size)
    }

    @Test
    fun afterADay_slotsSwapRoles_soHistoryMovesForward() = runBlocking {
        val courseDao = db.courseDao()
        val taskDao = db.taskDao()
        val fakeStore = FakeDriveStore()

        val cId = courseDao.insertCourse(Course(name = "Math", code = "MATH 101", colorHex = "#123456"))
        taskDao.insertTask(TaskItem(courseId = cId, title = "HW 1", dueDateEpochMillis = 1000L))
        DriveBackupManager.backUp(context, fakeStore) // slot 1, seq 1
        taskDao.insertTask(TaskItem(courseId = cId, title = "HW 2", dueDateEpochMillis = 2000L))
        DriveBackupManager.backUp(context, fakeStore) // slot 2, seq 2

        // Pretend slot 2 became the newest copy two days ago
        val slot1 = fakeStore.files.values.first { it.name == "unitask-backup-1.json" }
        val slot2 = fakeStore.files.values.first { it.name == "unitask-backup-2.json" }
        val twoDaysAgo = System.currentTimeMillis() - 2L * 24 * 60 * 60 * 1000L
        slot2.props["rotatedAt"] = twoDaysAgo.toString()

        taskDao.insertTask(TaskItem(courseId = cId, title = "HW 3", dueDateEpochMillis = 3000L))
        val res = DriveBackupManager.backUp(context, fakeStore)
        assertEquals(3L, (res as DriveBackupResult.Success).info.seq)
        // The older slot (1) now holds the newest data and slot 2 becomes the history copy
        assertEquals("3", slot1.props["seq"])
        assertEquals("2", slot2.props["seq"])
    }

    @Test
    fun slotWithoutRotatedAtMark_rotatesOnce() = runBlocking {
        val courseDao = db.courseDao()
        val taskDao = db.taskDao()
        val fakeStore = FakeDriveStore()

        val cId = courseDao.insertCourse(Course(name = "Math", code = "MATH 101", colorHex = "#123456"))
        taskDao.insertTask(TaskItem(courseId = cId, title = "HW 1", dueDateEpochMillis = 1000L))
        DriveBackupManager.backUp(context, fakeStore)
        taskDao.insertTask(TaskItem(courseId = cId, title = "HW 2", dueDateEpochMillis = 2000L))
        DriveBackupManager.backUp(context, fakeStore)

        // Backups made by an older app version have no rotatedAt mark
        fakeStore.files.values.forEach { it.props.remove("rotatedAt") }
        taskDao.insertTask(TaskItem(courseId = cId, title = "HW 3", dueDateEpochMillis = 3000L))
        DriveBackupManager.backUp(context, fakeStore)

        val slot1 = fakeStore.files.values.first { it.name == "unitask-backup-1.json" }
        assertEquals("3", slot1.props["seq"])
        assertNotNull(slot1.props["rotatedAt"])
    }

    @Test
    fun unchangedData_returnsUnchangedWithoutUpload_forceUploadsAnyway() = runBlocking {
        val courseDao = db.courseDao()
        val taskDao = db.taskDao()
        val fakeStore = FakeDriveStore()

        val cId = courseDao.insertCourse(Course(name = "Physics", code = "PHYS 101", colorHex = "#123456"))
        taskDao.insertTask(TaskItem(courseId = cId, title = "Lab 1", dueDateEpochMillis = 1000L))

        val res1 = DriveBackupManager.backUp(context, fakeStore)
        assertTrue(res1 is DriveBackupResult.Success)

        fakeStore.callLog.clear()
        val res2 = DriveBackupManager.backUp(context, fakeStore, force = false)
        assertTrue("Expected Unchanged but was $res2", res2 is DriveBackupResult.Unchanged)
        assertTrue("No uploads should occur on unchanged", fakeStore.callLog.none { it.startsWith("upload") })

        fakeStore.callLog.clear()
        val res3 = DriveBackupManager.backUp(context, fakeStore, force = true)
        assertTrue("Expected Success on force", res3 is DriveBackupResult.Success)
        assertTrue("Upload must occur on force", fakeStore.callLog.any { it.startsWith("upload") })
    }

    @Test
    fun emptyDatabase_blocksUnlessAllowEmpty() = runBlocking {
        val fakeStore = FakeDriveStore()

        val resBlocked = DriveBackupManager.backUp(context, fakeStore, allowEmpty = false)
        assertEquals(DriveBackupResult.EmptyBlocked, resBlocked)
        assertTrue("No store calls should be made when empty", fakeStore.callLog.isEmpty())

        val resAllowed = DriveBackupManager.backUp(context, fakeStore, allowEmpty = true)
        assertTrue(resAllowed is DriveBackupResult.Success)
        val slot1 = fakeStore.files.values.firstOrNull { it.name == "unitask-backup-1.json" }
        assertNotNull(slot1)
        assertEquals("1", slot1?.props?.get("seq"))
    }

    @Test
    fun failedUploadIntoOlderSlot_leavesLatestSlotRestorable() = runBlocking {
        val courseDao = db.courseDao()
        val taskDao = db.taskDao()
        val fakeStore = FakeDriveStore()

        val cId = courseDao.insertCourse(Course(name = "Chemistry", code = "CHEM 101", colorHex = "#123456"))
        taskDao.insertTask(TaskItem(courseId = cId, title = "Quiz 1", dueDateEpochMillis = 1000L))

        // Backup 1 writes slot 1 (seq 1)
        DriveBackupManager.backUp(context, fakeStore)
        // Backup 2 writes slot 2 (seq 2)
        taskDao.insertTask(TaskItem(courseId = cId, title = "Quiz 2", dueDateEpochMillis = 2000L))
        DriveBackupManager.backUp(context, fakeStore)

        val latestBefore = DriveBackupManager.latestInfo(fakeStore)
        assertEquals(2L, latestBefore?.seq)

        // Make older slot (slot 1) fail content upload
        val slot1Id = fakeStore.files.values.first { it.name == "unitask-backup-1.json" }.id
        fakeStore.onUpload = { fileId ->
            if (fileId == slot1Id) {
                throw DriveException(DriveException.Kind.SERVER, "Interrupted upload")
            }
        }

        taskDao.insertTask(TaskItem(courseId = cId, title = "Quiz 3", dueDateEpochMillis = 3000L))
        val failedRes = DriveBackupManager.backUp(context, fakeStore, rotateAfterMs = 0L)
        assertTrue(failedRes is DriveBackupResult.Failure)

        // Latest slot is still slot 2 with seq 2
        val latestAfter = DriveBackupManager.latestInfo(fakeStore)
        assertEquals(2L, latestAfter?.seq)

        // Restore restores successfully from slot 2
        withContext(Dispatchers.IO) { db.clearAllTables() }
        val restoreRes = DriveBackupManager.restore(context, fakeStore)
        assertTrue(restoreRes is BackupResult.Success)
        val tasks = taskDao.getAllActiveTasksSync()
        assertEquals(2, tasks.size)
    }

    @Test
    fun imageUploadedOnce_pruneDeletesOldUnreferenced_keepsReferencedAndYoung() = runBlocking {
        val noteDao = db.noteDao()
        val fakeStore = FakeDriveStore()

        val imgName = "note_img_test.jpg"
        val imgFile = NoteImages.imageFile(context, imgName)!!
        imgFile.writeBytes(byteArrayOf(1, 2, 3, 4, 5))

        val noteId = noteDao.insert(
            Note(title = "Title", body = "Photo: ![test](unitask-image:$imgName)", createdAt = 1000L, updatedAt = 1000L)
        )
        assertTrue(noteId > 0)

        // Backup 1: uploads image and slot file
        val res1 = DriveBackupManager.backUp(context, fakeStore)
        assertTrue(res1 is DriveBackupResult.Success)
        assertEquals(2, (res1 as DriveBackupResult.Success).uploadedFiles)
        assertEquals(0, res1.skippedFiles)

        // Backup 2 after modifying note title: image is skipped, only slot is uploaded
        noteDao.update(noteDao.getNoteById(noteId)!!.copy(title = "Updated Title"))
        fakeStore.callLog.clear()
        val res2 = DriveBackupManager.backUp(context, fakeStore)
        assertTrue(res2 is DriveBackupResult.Success)
        assertEquals(1, (res2 as DriveBackupResult.Success).uploadedFiles)
        assertEquals(1, res2.skippedFiles)
        assertEquals(0, fakeStore.callLog.count { it == "uploadFile" })

        // Pruning checks:
        // Add old unreferenced image (> 30 days)
        val now = System.currentTimeMillis()
        val oldUnref = fakeStore.create("img-old_unref.jpg")
        fakeStore.files[oldUnref.id]?.let {
            val field = FakeDriveStore.StoredFile::class.java.getDeclaredField("createdTimeMillis")
            field.isAccessible = true
            field.set(it, now - 35L * 24 * 60 * 60 * 1000L)
        }

        // Add young unreferenced image (< 30 days)
        val youngUnref = fakeStore.create("img-young_unref.jpg")
        fakeStore.files[youngUnref.id]?.let {
            val field = FakeDriveStore.StoredFile::class.java.getDeclaredField("createdTimeMillis")
            field.isAccessible = true
            field.set(it, now - 5L * 24 * 60 * 60 * 1000L)
        }

        // Make referenced image old (> 30 days)
        val refImg = fakeStore.files.values.first { it.name == "img-$imgName" }
        val refField = FakeDriveStore.StoredFile::class.java.getDeclaredField("createdTimeMillis")
        refField.isAccessible = true
        refField.set(refImg, now - 40L * 24 * 60 * 60 * 1000L)

        // Backup 3 triggers prune
        noteDao.update(noteDao.getNoteById(noteId)!!.copy(title = "Third Title"))
        val res3 = DriveBackupManager.backUp(context, fakeStore)
        assertTrue(res3 is DriveBackupResult.Success)

        // Verify: old unreferenced deleted, young kept, old referenced kept
        assertTrue(fakeStore.files.values.none { it.name == "img-old_unref.jpg" })
        assertTrue(fakeStore.files.values.any { it.name == "img-young_unref.jpg" })
        assertTrue(fakeStore.files.values.any { it.name == "img-$imgName" })

        // Failing prune still returns Success
        val oldUnref2 = fakeStore.create("img-old_unref2.jpg")
        fakeStore.files[oldUnref2.id]?.let {
            val field = FakeDriveStore.StoredFile::class.java.getDeclaredField("createdTimeMillis")
            field.isAccessible = true
            field.set(it, now - 35L * 24 * 60 * 60 * 1000L)
        }
        fakeStore.onDelete = { throw DriveException(DriveException.Kind.SERVER, "Delete failed") }
        noteDao.update(noteDao.getNoteById(noteId)!!.copy(title = "Fourth Title"))
        val res4 = DriveBackupManager.backUp(context, fakeStore)
        assertTrue("Failing prune must still succeed: $res4", res4 is DriveBackupResult.Success)
    }

    @Test
    fun restoreRoundTrip_recreatesSameCountsAndImageFiles() = runBlocking {
        val courseDao = db.courseDao()
        val taskDao = db.taskDao()
        val noteDao = db.noteDao()
        val fakeStore = FakeDriveStore()

        val cId = courseDao.insertCourse(Course(name = "Biology", code = "BIO 101", colorHex = "#10B981"))
        taskDao.insertTask(TaskItem(courseId = cId, title = "Cell lab", dueDateEpochMillis = 5000L))

        val imgName = "bio_cell.jpg"
        val imgFile = NoteImages.imageFile(context, imgName)!!
        imgFile.writeBytes(byteArrayOf(9, 8, 7, 6))

        noteDao.insert(Note(title = "Cells", body = "Look: ![cell](unitask-image:$imgName)", createdAt = 1000L, updatedAt = 1000L))

        val backupRes = DriveBackupManager.backUp(context, fakeStore)
        assertTrue(backupRes is DriveBackupResult.Success)

        // Clear local database and local image file
        withContext(Dispatchers.IO) { db.clearAllTables() }
        imgFile.delete()
        assertFalse(imgFile.exists())

        // Restore
        val restoreRes = DriveBackupManager.restore(context, fakeStore)
        assertTrue(restoreRes is BackupResult.Success)
        val counts = (restoreRes as BackupResult.Success).counts
        assertEquals(1, counts.courses)
        assertEquals(1, counts.tasks)
        assertEquals(1, counts.notes)
        assertEquals(1, counts.images)

        // Verify image was recreated on disk with exact bytes
        assertTrue(imgFile.exists())
        assertTrue(byteArrayOf(9, 8, 7, 6).contentEquals(imgFile.readBytes()))
    }

    @Test
    fun damagedLatestSlot_fallsBackToOtherSlot_bothDamagedFails_newerVersionRefused() = runBlocking {
        val courseDao = db.courseDao()
        val taskDao = db.taskDao()
        val fakeStore = FakeDriveStore()

        val cId = courseDao.insertCourse(Course(name = "English", code = "ENG 101", colorHex = "#4F46E5"))
        taskDao.insertTask(TaskItem(courseId = cId, title = "Essay 1", dueDateEpochMillis = 1000L))
        DriveBackupManager.backUp(context, fakeStore) // slot 1 (seq 1)

        taskDao.insertTask(TaskItem(courseId = cId, title = "Essay 2", dueDateEpochMillis = 2000L))
        DriveBackupManager.backUp(context, fakeStore) // slot 2 (seq 2)

        // Case A: Corrupt slot 2 content -> falls back to slot 1
        val slot2 = fakeStore.files.values.first { it.name == "unitask-backup-2.json" }
        slot2.bytes = "corrupted json content".toByteArray()

        withContext(Dispatchers.IO) { db.clearAllTables() }
        val fallbackRes = DriveBackupManager.restore(context, fakeStore)
        assertTrue("Fallback must succeed", fallbackRes is BackupResult.Success)
        val tasksFallback = taskDao.getAllActiveTasksSync()
        assertEquals(1, tasksFallback.size)

        // Case B: Both damaged -> clear failure
        val slot1 = fakeStore.files.values.first { it.name == "unitask-backup-1.json" }
        slot1.bytes = "corrupted json content 2".toByteArray()

        val bothDamagedRes = DriveBackupManager.restore(context, fakeStore)
        assertTrue(bothDamagedRes is BackupResult.Failure)
        assertEquals(
            "The backup on Google Drive is damaged and could not be restored.",
            (bothDamagedRes as BackupResult.Failure).message
        )

        // Case C: Newer version refused
        val newerEnvelope = JSONObject().apply {
            put("format", "unitask-backup")
            put("version", 999)
            put("app", "UniTask")
            put("data", JSONObject())
        }
        slot1.bytes = newerEnvelope.toString().toByteArray()
        slot1.props.remove("dataHash")
        val newerRes = DriveBackupManager.restore(context, fakeStore)
        assertTrue(newerRes is BackupResult.Failure)
        assertEquals(
            "This backup was made by a newer version of UniTask. Update the app and try again.",
            (newerRes as BackupResult.Failure).message
        )
    }

    @Test
    fun missingImageOnDrive_skippedAndRestRestores() = runBlocking {
        val noteDao = db.noteDao()
        val fakeStore = FakeDriveStore()

        val imgName = "missing_image.jpg"
        val imgFile = NoteImages.imageFile(context, imgName)!!
        imgFile.writeBytes(byteArrayOf(1, 2, 3))
        noteDao.insert(Note(title = "Note", body = "![missing](unitask-image:$imgName)", createdAt = 1000L, updatedAt = 1000L))

        DriveBackupManager.backUp(context, fakeStore)

        // Delete the image from Drive fake store
        val driveImg = fakeStore.files.values.first { it.name == "img-$imgName" }
        fakeStore.delete(driveImg.id)

        // Clear local DB and local file
        withContext(Dispatchers.IO) { db.clearAllTables() }
        imgFile.delete()

        val restoreRes = DriveBackupManager.restore(context, fakeStore)
        assertTrue(restoreRes is BackupResult.Success)
        val counts = (restoreRes as BackupResult.Success).counts
        assertEquals(1, counts.notes)
        assertEquals(0, counts.images)
        assertEquals(1, counts.skippedImages)
        assertFalse(imgFile.exists())
    }

    @Test
    fun restoreTwice_mergesWithoutDuplicateRows() = runBlocking {
        val courseDao = db.courseDao()
        val taskDao = db.taskDao()
        val fakeStore = FakeDriveStore()

        val cId = courseDao.insertCourse(Course(name = "Art", code = "ART 101", colorHex = "#EF4444"))
        taskDao.insertTask(TaskItem(courseId = cId, title = "Sketch", dueDateEpochMillis = 1000L))
        DriveBackupManager.backUp(context, fakeStore)

        withContext(Dispatchers.IO) { db.clearAllTables() }

        val res1 = DriveBackupManager.restore(context, fakeStore)
        assertTrue(res1 is BackupResult.Success)
        assertEquals(1, courseDao.getAllCourses().first().size)
        assertEquals(1, taskDao.getAllActiveTasksSync().size)

        val res2 = DriveBackupManager.restore(context, fakeStore)
        assertTrue(res2 is BackupResult.Success)
        assertEquals(1, courseDao.getAllCourses().first().size)
        assertEquals(1, taskDao.getAllActiveTasksSync().size)
    }

    @Test
    fun backupWithMyTasks_summaryCountsMyTasksCorrectly() = runBlocking {
        val courseDao = db.courseDao()
        val taskDao = db.taskDao()
        val noteDao = db.noteDao()
        val attDao = db.taskAttachmentDao()
        val fakeStore = FakeDriveStore()

        DrivePrefs(context).includeAttachments = true

        // 8 courses
        val courseIds = (1..8).map { i ->
            courseDao.insertCourse(Course(name = "Course $i", code = "CRS$i", colorHex = "#123456"))
        }

        // 2 course tasks
        val tId1 = taskDao.insertTask(TaskItem(courseId = courseIds[0], title = "Course Task 1", dueDateEpochMillis = 1000L))
        val tId2 = taskDao.insertTask(TaskItem(courseId = courseIds[1], title = "Course Task 2", dueDateEpochMillis = 2000L))

        // 3 My tasks
        val myTasksCourse = Course(name = "My tasks", code = MY_TASKS_COURSE_CODE, colorHex = "#888888")
        val myTasksCourseId = courseDao.insertCourse(myTasksCourse)
        taskDao.insertTask(TaskItem(courseId = myTasksCourseId, title = "My Task 1", dueDateEpochMillis = 3000L))
        taskDao.insertTask(TaskItem(courseId = myTasksCourseId, title = "My Task 2", dueDateEpochMillis = 4000L))
        taskDao.insertTask(TaskItem(courseId = myTasksCourseId, title = "My Task 3", dueDateEpochMillis = 5000L))

        // 2 notes
        noteDao.insert(Note(title = "Note 1", body = "Body 1", createdAt = 1000L, updatedAt = 1000L))
        noteDao.insert(Note(title = "Note 2", body = "Body 2", createdAt = 2000L, updatedAt = 2000L))

        // 4 attachments
        val attachmentsDir = File(context.filesDir, "attachments").apply { mkdirs() }
        for (i in 1..4) {
            val file = File(attachmentsDir, "att_$i.txt").apply { writeBytes("att $i".toByteArray()) }
            attDao.insertAttachment(
                TaskAttachment(
                    taskId = if (i <= 2) tId1 else tId2,
                    fileName = "att_$i.txt",
                    relativePath = "attachments/att_$i.txt",
                    fileSize = file.length(),
                    mimeType = "text/plain"
                )
            )
        }

        val result = DriveBackupManager.backUp(context, fakeStore)
        assertTrue(result is DriveBackupResult.Success)
        val info = (result as DriveBackupResult.Success).info
        assertEquals(8, info.courses)
        assertEquals(2, info.tasks)
        assertEquals(3, info.myTasks)
        assertEquals(2, info.notes)
        assertEquals(4, info.attachments)

        val expectedSummary = "8 courses, 2 tasks, 3 My tasks, 2 notes, 4 attachments"
        assertEquals(expectedSummary, DriveBackupManager.buildCountsText(info))
        assertEquals(expectedSummary, DriveBackupViewModel.buildCountsText(info))
        assertEquals(expectedSummary, DrivePrefs(context).lastCounts)

        val zeroMyTasksInfo = info.copy(myTasks = 0)
        assertEquals("8 courses, 2 tasks, 2 notes, 4 attachments", DriveBackupManager.buildCountsText(zeroMyTasksInfo))

        val zeroAttachmentsInfo = info.copy(attachments = 0)
        assertEquals("8 courses, 2 tasks, 3 My tasks, 2 notes", DriveBackupManager.buildCountsText(zeroAttachmentsInfo))
    }

    @Test
    fun oldSlotWithoutMyTasksProp_reuploadedOnceThenUnchanged() = runBlocking {
        val courseDao = db.courseDao()
        val taskDao = db.taskDao()
        val fakeStore = FakeDriveStore()

        val cId = courseDao.insertCourse(Course(name = "Math", code = "MATH 101", colorHex = "#123456"))
        taskDao.insertTask(TaskItem(courseId = cId, title = "HW 1", dueDateEpochMillis = 1000L))

        // Initial backup writes slot 1 with myTasks prop
        val initialRes = DriveBackupManager.backUp(context, fakeStore)
        assertTrue(initialRes is DriveBackupResult.Success)

        // Simulate an older slot by stripping the myTasks prop
        val slot1 = fakeStore.files.values.first { it.name == "unitask-backup-1.json" }
        slot1.props.remove("myTasks")
        assertFalse(slot1.props.containsKey("myTasks"))

        // Next backup without force: missing prop triggers re-upload
        fakeStore.callLog.clear()
        val reuploadRes = DriveBackupManager.backUp(context, fakeStore, force = false)
        assertTrue("Expected Success (re-upload) for slot lacking myTasks prop", reuploadRes is DriveBackupResult.Success)
        assertTrue("Slot must be re-uploaded", fakeStore.callLog.any { it == "upload" })

        // Check that slot now has myTasks prop
        val updatedSlot = fakeStore.files.values.first { it.id == (reuploadRes as DriveBackupResult.Success).info.slotFileId }
        assertTrue(updatedSlot.props.containsKey("myTasks"))

        // Second run without force: slot now has myTasks prop and data is unchanged -> Unchanged
        fakeStore.callLog.clear()
        val unchangedRes = DriveBackupManager.backUp(context, fakeStore, force = false)
        assertTrue("Second run must be Unchanged", unchangedRes is DriveBackupResult.Unchanged)
        assertTrue("No uploads on second run", fakeStore.callLog.none { it.startsWith("upload") })
    }

    @Test
    fun infoFromSlot_readsMyTasksPropertyOrDefaultZero() {
        val fileWithMyTasks = DriveFile(
            id = "file_1",
            name = "unitask-backup-1.json",
            size = 100L,
            createdTimeMillis = 1000L,
            modifiedTimeMillis = 1000L,
            props = mapOf(
                "seq" to "1",
                "exportedAt" to "1000",
                "courses" to "8",
                "tasks" to "2",
                "myTasks" to "3",
                "notes" to "2",
                "images" to "0",
                "attachments" to "4",
                "appVersion" to "1.0",
                "formatVersion" to "1"
            )
        )
        val info = DriveBackupManager.infoFromSlot(fileWithMyTasks)
        assertNotNull(info)
        assertEquals(3, info!!.myTasks)
        assertEquals(8, info.courses)
        assertEquals(2, info.tasks)
        assertEquals(2, info.notes)
        assertEquals(4, info.attachments)

        // Old slot without myTasks prop defaults to 0
        val fileWithoutMyTasks = fileWithMyTasks.copy(
            props = fileWithMyTasks.props - "myTasks"
        )
        val oldInfo = DriveBackupManager.infoFromSlot(fileWithoutMyTasks)
        assertNotNull(oldInfo)
        assertEquals(0, oldInfo!!.myTasks)
    }
}
