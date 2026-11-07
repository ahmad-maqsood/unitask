package com.example.util.drive

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.Course
import com.example.data.local.TaskAttachment
import com.example.data.local.TaskItem
import com.example.util.BackupFileSource
import com.example.util.BackupResult
import com.example.util.LocalBackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DriveAttachmentTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var attachmentsDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = AppDatabase.getDatabase(context)
        runBlocking(Dispatchers.IO) { db.clearAllTables() }
        attachmentsDir = File(context.filesDir, "attachments")
        if (attachmentsDir.exists()) {
            attachmentsDir.deleteRecursively()
        }
        attachmentsDir.mkdirs()
        val prefs = context.getSharedPreferences("drive_backup_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @After
    fun tearDown() {
        db.close()
        AppDatabase::class.java.getDeclaredField("INSTANCE")
            .apply { isAccessible = true }
            .set(null, null)
        if (attachmentsDir.exists()) {
            attachmentsDir.deleteRecursively()
        }
        val prefs = context.getSharedPreferences("drive_backup_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun backupAndRestore_twoTasksThreeAttachments_restoredCorrectly() = runBlocking {
        val courseDao = db.courseDao()
        val taskDao = db.taskDao()
        val attDao = db.taskAttachmentDao()
        val fakeStore = FakeDriveStore()

        DrivePrefs(context).includeAttachments = true

        val cId = courseDao.insertCourse(Course(name = "Math", code = "MATH 101", colorHex = "#123456"))
        val tId1 = taskDao.insertTask(TaskItem(courseId = cId, title = "Homework 1", dueDateEpochMillis = 1000L))
        val tId2 = taskDao.insertTask(TaskItem(courseId = cId, title = "Homework 2", dueDateEpochMillis = 2000L))

        val file1 = File(attachmentsDir, "1001_doc1.pdf").apply { writeBytes("pdf data 1".toByteArray()) }
        val file2 = File(attachmentsDir, "1002_doc2.pdf").apply { writeBytes("pdf data 2".toByteArray()) }
        val file3 = File(attachmentsDir, "1003_image.png").apply { writeBytes("png data 3".toByteArray()) }

        attDao.insertAttachment(
            TaskAttachment(
                taskId = tId1,
                fileName = "doc1.pdf",
                relativePath = "attachments/1001_doc1.pdf",
                fileSize = file1.length(),
                mimeType = "application/pdf"
            )
        )
        attDao.insertAttachment(
            TaskAttachment(
                taskId = tId1,
                fileName = "doc2.pdf",
                relativePath = "attachments/1002_doc2.pdf",
                fileSize = file2.length(),
                mimeType = "application/pdf"
            )
        )
        attDao.insertAttachment(
            TaskAttachment(
                taskId = tId2,
                fileName = "image.png",
                relativePath = "attachments/1003_image.png",
                fileSize = file3.length(),
                mimeType = "image/png"
            )
        )

        val backupRes = DriveBackupManager.backUp(context, fakeStore, allowEmpty = true, force = true)
        assertTrue(backupRes is DriveBackupResult.Success)
        val success = backupRes as DriveBackupResult.Success
        assertEquals(3, success.info.attachments)
        assertEquals(4, success.uploadedFiles) // 3 attachments + 1 slot

        assertNotNull(fakeStore.files.values.firstOrNull { it.name == "att-1001_doc1.pdf" })
        assertNotNull(fakeStore.files.values.firstOrNull { it.name == "att-1002_doc2.pdf" })
        assertNotNull(fakeStore.files.values.firstOrNull { it.name == "att-1003_image.png" })

        // Fresh database and empty attachments folder
        db.clearAllTables()
        attachmentsDir.deleteRecursively()
        attachmentsDir.mkdirs()

        val restoreRes = DriveBackupManager.restore(context, fakeStore)
        assertTrue(restoreRes is BackupResult.Success)
        val restoredCounts = (restoreRes as BackupResult.Success).counts
        assertEquals(3, restoredCounts.attachments)
        assertEquals(0, restoredCounts.skippedAttachments)

        val restoredTasks = taskDao.getAllActiveTasksSync()
        assertEquals(2, restoredTasks.size)
        val task1 = restoredTasks.first { it.title == "Homework 1" }
        val task2 = restoredTasks.first { it.title == "Homework 2" }

        val atts1 = attDao.getAttachmentsForTaskSync(task1.id)
        val atts2 = attDao.getAttachmentsForTaskSync(task2.id)
        assertEquals(2, atts1.size)
        assertEquals(1, atts2.size)

        val restoredFile1 = File(context.filesDir, atts1.first { it.fileName == "doc1.pdf" }.relativePath)
        val restoredFile2 = File(context.filesDir, atts1.first { it.fileName == "doc2.pdf" }.relativePath)
        val restoredFile3 = File(context.filesDir, atts2.first { it.fileName == "image.png" }.relativePath)

        assertTrue(restoredFile1.exists())
        assertTrue(restoredFile2.exists())
        assertTrue(restoredFile3.exists())
        assertEquals("pdf data 1", restoredFile1.readText())
        assertEquals("pdf data 2", restoredFile2.readText())
        assertEquals("png data 3", restoredFile3.readText())

        // 2. Second restore adds nothing
        val secondRestore = DriveBackupManager.restore(context, fakeStore)
        assertTrue(secondRestore is BackupResult.Success)
        val secondCounts = (secondRestore as BackupResult.Success).counts
        assertEquals(0, secondCounts.attachments)
        assertEquals(0, secondCounts.skippedAttachments)
        assertEquals(2, attDao.getAttachmentsForTaskSync(task1.id).size)
        assertEquals(1, attDao.getAttachmentsForTaskSync(task2.id).size)

        // 3. Second backup uploads no attachment again
        val secondBackup = DriveBackupManager.backUp(context, fakeStore, force = true)
        assertTrue(secondBackup is DriveBackupResult.Success)
        val secondBackupSuccess = secondBackup as DriveBackupResult.Success
        assertEquals(1, secondBackupSuccess.uploadedFiles) // only slot file
        assertEquals(3, secondBackupSuccess.skippedFiles)

        // 4. Changed local file size uploads that file again
        val newContent = "longer pdf content for doc 1"
        restoredFile1.writeBytes(newContent.toByteArray())
        val att1ToUpdate = atts1.first { it.fileName == "doc1.pdf" }
        attDao.insertAttachment(att1ToUpdate.copy(fileSize = restoredFile1.length()))

        val thirdBackup = DriveBackupManager.backUp(context, fakeStore, force = true)
        assertTrue(thirdBackup is DriveBackupResult.Success)
        val thirdSuccess = thirdBackup as DriveBackupResult.Success
        assertEquals(2, thirdSuccess.uploadedFiles) // 1 attachment + 1 slot
        assertEquals(2, thirdSuccess.skippedFiles)

        val att1DriveFile = fakeStore.files.values.first { it.name == "att-" + File(att1ToUpdate.relativePath).name }
        assertEquals(newContent, att1DriveFile.bytes.toString(Charsets.UTF_8))
    }

    @Test
    fun includeAttachmentsFalse_givesNoAttachmentsKeyAndNoAttUpload() = runBlocking {
        val courseDao = db.courseDao()
        val taskDao = db.taskDao()
        val attDao = db.taskAttachmentDao()
        val fakeStore = FakeDriveStore()

        DrivePrefs(context).includeAttachments = false

        val cId = courseDao.insertCourse(Course(name = "Math", code = "MATH 101", colorHex = "#123456"))
        val tId = taskDao.insertTask(TaskItem(courseId = cId, title = "Task", dueDateEpochMillis = 1000L))

        val file = File(attachmentsDir, "1001_doc.pdf").apply { writeBytes("pdf data".toByteArray()) }
        attDao.insertAttachment(
            TaskAttachment(
                taskId = tId,
                fileName = "doc.pdf",
                relativePath = "attachments/1001_doc.pdf",
                fileSize = file.length(),
                mimeType = "application/pdf"
            )
        )

        val res = DriveBackupManager.backUp(context, fakeStore, force = true)
        assertTrue(res is DriveBackupResult.Success)
        val success = res as DriveBackupResult.Success
        assertEquals(0, success.info.attachments)

        val attDriveFiles = fakeStore.files.values.filter { it.name.startsWith("att-") }
        assertTrue(attDriveFiles.isEmpty())

        val slotFile = fakeStore.files.values.first { it.name.startsWith("unitask-backup-") }
        val root = JSONObject(slotFile.bytes.toString(Charsets.UTF_8))
        val data = root.getJSONObject("data")
        assertFalse(data.has("attachments"))
    }

    @Test
    fun missingLocalFile_isSkippedAndCounted() = runBlocking {
        val courseDao = db.courseDao()
        val taskDao = db.taskDao()
        val attDao = db.taskAttachmentDao()

        DrivePrefs(context).includeAttachments = true

        val cId = courseDao.insertCourse(Course(name = "Math", code = "MATH 101", colorHex = "#123456"))
        val tId = taskDao.insertTask(TaskItem(courseId = cId, title = "Task", dueDateEpochMillis = 1000L))

        // Do not create ghost.pdf on disk
        attDao.insertAttachment(
            TaskAttachment(
                taskId = tId,
                fileName = "ghost.pdf",
                relativePath = "attachments/ghost.pdf",
                fileSize = 100L,
                mimeType = "application/pdf"
            )
        )

        val built = LocalBackupManager.buildData(context, inlineImages = false, includeAttachments = true)
        assertEquals(0, built.counts.attachments)
        assertEquals(1, built.counts.skippedAttachments)
        assertFalse(built.referencedAttachments.contains("ghost.pdf"))
    }

    @Test
    fun traversalEntry_isIgnoredAndNothingWrittenOutsideAttachmentsFolder() = runBlocking {
        val courseDao = db.courseDao()
        val taskDao = db.taskDao()

        val cId = courseDao.insertCourse(Course(name = "Math", code = "MATH 101", colorHex = "#123456"))
        taskDao.insertTask(TaskItem(courseId = cId, title = "Task", dueDateEpochMillis = 1000L))

        val data = JSONObject().apply {
            put("courses", JSONArray().put(JSONObject().put("code", "MATH 101").put("name", "Math")))
            put("slots", JSONArray())
            put(
                "tasks",
                JSONArray().put(
                    JSONObject()
                        .put("courseCode", "MATH 101")
                        .put("title", "Task")
                        .put("dueDateEpochMillis", 1000L)
                )
            )
            put(
                "attachments",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("taskIndex", 0)
                            .put("fileName", "evil.txt")
                            .put("storedName", "../../evil.txt")
                            .put("fileSize", 10L)
                            .put("mimeType", "text/plain")
                    )
                    .put(
                        JSONObject()
                            .put("taskIndex", 0)
                            .put("fileName", "evil2.txt")
                            .put("storedName", "a/b.pdf")
                            .put("fileSize", 10L)
                            .put("mimeType", "application/pdf")
                    )
            )
        }

        val source = object : BackupFileSource {
            override suspend fun noteImage(name: String): ByteArray? = null
            override suspend fun downloadAttachment(storedName: String, target: File): Boolean {
                target.writeBytes("evil payload".toByteArray())
                return true
            }
        }

        val counts = LocalBackupManager.applyBackupData(context, data, source)
        assertEquals(0, counts.attachments)
        assertEquals(2, counts.skippedAttachments)

        assertFalse(File(context.filesDir, "evil.txt").exists())
        assertFalse(File(context.filesDir.parentFile, "evil.txt").exists())
        assertEquals(0, db.taskAttachmentDao().getAllRelativePaths().size)
    }

    @Test
    fun downloadedFileWrongSize_isDeletedAndSkippedAndNoRowInserted() = runBlocking {
        val courseDao = db.courseDao()
        val taskDao = db.taskDao()

        val cId = courseDao.insertCourse(Course(name = "Math", code = "MATH 101", colorHex = "#123456"))
        taskDao.insertTask(TaskItem(courseId = cId, title = "Task", dueDateEpochMillis = 1000L))

        val data = JSONObject().apply {
            put("courses", JSONArray().put(JSONObject().put("code", "MATH 101").put("name", "Math")))
            put("slots", JSONArray())
            put(
                "tasks",
                JSONArray().put(
                    JSONObject()
                        .put("courseCode", "MATH 101")
                        .put("title", "Task")
                        .put("dueDateEpochMillis", 1000L)
                )
            )
            put(
                "attachments",
                JSONArray().put(
                    JSONObject()
                        .put("taskIndex", 0)
                        .put("fileName", "wrong.pdf")
                        .put("storedName", "wrong.pdf")
                        .put("fileSize", 100L)
                        .put("mimeType", "application/pdf")
                )
            )
        }

        val source = object : BackupFileSource {
            override suspend fun noteImage(name: String): ByteArray? = null
            override suspend fun downloadAttachment(storedName: String, target: File): Boolean {
                target.writeBytes(ByteArray(40)) // only 40 bytes written instead of 100
                return true
            }
        }

        val counts = LocalBackupManager.applyBackupData(context, data, source)
        assertEquals(0, counts.attachments)
        assertEquals(1, counts.skippedAttachments)

        val targetFile = File(attachmentsDir, "wrong.pdf")
        assertFalse(targetFile.exists())
        assertEquals(0, db.taskAttachmentDao().getAllRelativePaths().size)
    }

    @Test
    fun nameCollision_getsRestoredPrefixAndOldFileUntouched() = runBlocking {
        val courseDao = db.courseDao()
        val taskDao = db.taskDao()
        val attDao = db.taskAttachmentDao()

        val existingFile = File(attachmentsDir, "1001_file.pdf").apply { writeBytes("local original".toByteArray()) }

        val cId = courseDao.insertCourse(Course(name = "Math", code = "MATH 101", colorHex = "#123456"))
        taskDao.insertTask(TaskItem(courseId = cId, title = "Task", dueDateEpochMillis = 1000L))

        val cloudContent = "cloud restored payload"
        val data = JSONObject().apply {
            put("courses", JSONArray().put(JSONObject().put("code", "MATH 101").put("name", "Math")))
            put("slots", JSONArray())
            put(
                "tasks",
                JSONArray().put(
                    JSONObject()
                        .put("courseCode", "MATH 101")
                        .put("title", "Task")
                        .put("dueDateEpochMillis", 1000L)
                )
            )
            put(
                "attachments",
                JSONArray().put(
                    JSONObject()
                        .put("taskIndex", 0)
                        .put("fileName", "other.pdf")
                        .put("storedName", "1001_file.pdf")
                        .put("fileSize", cloudContent.toByteArray().size.toLong())
                        .put("mimeType", "application/pdf")
                )
            )
        }

        val source = object : BackupFileSource {
            override suspend fun noteImage(name: String): ByteArray? = null
            override suspend fun downloadAttachment(storedName: String, target: File): Boolean {
                target.writeBytes(cloudContent.toByteArray())
                return true
            }
        }

        val counts = LocalBackupManager.applyBackupData(context, data, source)
        assertEquals(1, counts.attachments)
        assertEquals(0, counts.skippedAttachments)

        // Old file untouched
        assertEquals("local original", existingFile.readText())

        // Restored file created with prefix
        val rows = attDao.getAllRelativePaths()
        assertEquals(1, rows.size)
        assertTrue(rows.first().startsWith("attachments/restored_"))
        val restoredFile = File(context.filesDir, rows.first())
        assertTrue(restoredFile.exists())
        assertEquals(cloudContent, restoredFile.readText())
    }

    @Test
    fun failingDownloadForOneFile_stillRestoresOtherTwo() = runBlocking {
        val courseDao = db.courseDao()
        val taskDao = db.taskDao()
        val attDao = db.taskAttachmentDao()

        val cId = courseDao.insertCourse(Course(name = "Math", code = "MATH 101", colorHex = "#123456"))
        taskDao.insertTask(TaskItem(courseId = cId, title = "Task", dueDateEpochMillis = 1000L))

        val data = JSONObject().apply {
            put("courses", JSONArray().put(JSONObject().put("code", "MATH 101").put("name", "Math")))
            put("slots", JSONArray())
            put(
                "tasks",
                JSONArray().put(
                    JSONObject()
                        .put("courseCode", "MATH 101")
                        .put("title", "Task")
                        .put("dueDateEpochMillis", 1000L)
                )
            )
            put(
                "attachments",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("taskIndex", 0)
                            .put("fileName", "doc1.pdf")
                            .put("storedName", "doc1.pdf")
                            .put("fileSize", 4L)
                            .put("mimeType", "application/pdf")
                    )
                    .put(
                        JSONObject()
                            .put("taskIndex", 0)
                            .put("fileName", "doc2.pdf")
                            .put("storedName", "doc2.pdf")
                            .put("fileSize", 4L)
                            .put("mimeType", "application/pdf")
                    )
                    .put(
                        JSONObject()
                            .put("taskIndex", 0)
                            .put("fileName", "doc3.pdf")
                            .put("storedName", "doc3.pdf")
                            .put("fileSize", 4L)
                            .put("mimeType", "application/pdf")
                    )
            )
        }

        val source = object : BackupFileSource {
            override suspend fun noteImage(name: String): ByteArray? = null
            override suspend fun downloadAttachment(storedName: String, target: File): Boolean {
                if (storedName == "doc2.pdf") {
                    return false
                }
                target.writeBytes("1234".toByteArray())
                return true
            }
        }

        val counts = LocalBackupManager.applyBackupData(context, data, source)
        assertEquals(2, counts.attachments)
        assertEquals(1, counts.skippedAttachments)

        val paths = attDao.getAllRelativePaths()
        assertEquals(2, paths.size)
        assertTrue(paths.contains("attachments/doc1.pdf"))
        assertTrue(paths.contains("attachments/doc3.pdf"))
        assertFalse(File(attachmentsDir, "doc2.pdf").exists())
    }

    @Test
    fun taskDeletedBeforeRestore_skipsItsAttachments() = runBlocking {
        val data = JSONObject().apply {
            put("courses", JSONArray())
            put("slots", JSONArray())
            put("tasks", JSONArray()) // no tasks in backup
            put(
                "attachments",
                JSONArray().put(
                    JSONObject()
                        .put("taskIndex", 0)
                        .put("fileName", "doc.pdf")
                        .put("storedName", "doc.pdf")
                        .put("fileSize", 10L)
                        .put("mimeType", "application/pdf")
                )
            )
        }

        val source = object : BackupFileSource {
            override suspend fun noteImage(name: String): ByteArray? = null
            override suspend fun downloadAttachment(storedName: String, target: File): Boolean {
                target.writeBytes("content".toByteArray())
                return true
            }
        }

        val counts = LocalBackupManager.applyBackupData(context, data, source)
        assertEquals(0, counts.attachments)
        assertEquals(1, counts.skippedAttachments)
        assertFalse(File(attachmentsDir, "doc.pdf").exists())
    }

    @Test
    fun prune_removesOldUnreferencedAttFileAndKeepsYoungOne() = runBlocking {
        val courseDao = db.courseDao()
        val taskDao = db.taskDao()
        val fakeStore = FakeDriveStore()

        val cId = courseDao.insertCourse(Course(name = "Math", code = "MATH 101", colorHex = "#123456"))
        taskDao.insertTask(TaskItem(courseId = cId, title = "Task", dueDateEpochMillis = 1000L))

        val now = System.currentTimeMillis()
        val oldFile = fakeStore.create("att-old.pdf")
        val youngFile = fakeStore.create("att-young.pdf")

        // Set created time in fake store
        fakeStore.files[oldFile.id]!!.apply {
            val thirtyFiveDaysAgo = now - 35L * 24 * 60 * 60 * 1000L
            // Reconstruct stored file with old timestamp
            fakeStore.files[oldFile.id] = FakeDriveStore.StoredFile(
                id = id,
                name = name,
                bytes = bytes,
                createdTimeMillis = thirtyFiveDaysAgo,
                modifiedTimeMillis = thirtyFiveDaysAgo,
                props = props
            )
        }
        fakeStore.files[youngFile.id]!!.apply {
            val fiveDaysAgo = now - 5L * 24 * 60 * 60 * 1000L
            fakeStore.files[youngFile.id] = FakeDriveStore.StoredFile(
                id = id,
                name = name,
                bytes = bytes,
                createdTimeMillis = fiveDaysAgo,
                modifiedTimeMillis = fiveDaysAgo,
                props = props
            )
        }

        // Run backup without attachments (so neither file is referenced)
        DrivePrefs(context).includeAttachments = false
        val res = DriveBackupManager.backUp(context, fakeStore, force = true)
        assertTrue(res is DriveBackupResult.Success)

        // Old file must be pruned, young file must remain
        assertFalse(fakeStore.files.containsKey(oldFile.id))
        assertTrue(fakeStore.files.containsKey(youngFile.id))
    }
}
