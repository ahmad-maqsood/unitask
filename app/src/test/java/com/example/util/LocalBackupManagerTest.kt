package com.example.util

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.Course
import com.example.data.local.LectureSlot
import com.example.data.local.Priority
import com.example.data.local.TaskItem
import com.example.data.local.TaskStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
class LocalBackupManagerTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private val createdFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.getDatabase(context)
        db.clearAllTables()
    }

    @After
    fun tearDown() {
        db.clearAllTables()
        for (f in createdFiles) {
            if (f.exists()) f.delete()
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
    fun testPlainBackupExportAndRestoreRoundTrip() = runBlocking {
        val courseDao = db.courseDao()
        val slotDao = db.lectureSlotDao()
        val taskDao = db.taskDao()

        val courseId = courseDao.insertCourse(
            Course(name = "Data Structures", code = "CS 201", colorHex = "#4F46E5", location = "Hall 1", instructor = "Prof. Knuth")
        )
        val slotId = slotDao.insertSlot(
            LectureSlot(courseId = courseId, dayOfWeek = 1, startHour = 9, startMinute = 0, endHour = 10, endMinute = 30, classroom = "Room 101")
        )
        val taskId = taskDao.insertTask(
            TaskItem(courseId = courseId, title = "Homework 1", description = "Trees and graphs", dueDateEpochMillis = 1772946000000L, dueTimeHour = 14, dueTimeMinute = 0, priority = Priority.HIGH, status = TaskStatus.PENDING)
        )

        val (_, uri) = createTempBackupFile("plain_backup.json")

        // 1. Export unencrypted backup
        val exportResult = LocalBackupManager.export(context, uri, password = null)
        assertTrue("Export must succeed: $exportResult", exportResult is BackupResult.Success)
        val exportCounts = (exportResult as BackupResult.Success).counts
        assertEquals(1, exportCounts.courses)
        assertEquals(1, exportCounts.slots)
        assertEquals(1, exportCounts.tasks)

        // 2. Inspect file
        val inspection = LocalBackupManager.inspect(context, uri)
        assertEquals(LocalBackupManager.Inspection.PLAIN, inspection)

        // 3. Clear database to verify restore repopulates it
        db.clearAllTables()
        assertEquals(0, courseDao.getAllCourses().first().size)
        assertEquals(0, slotDao.getAllSlotsSync().size)
        assertEquals(0, taskDao.getAllActiveTasksSync().size)

        // 4. Restore backup
        val restoreResult = LocalBackupManager.restore(context, uri, password = null)
        assertTrue("Restore must succeed: $restoreResult", restoreResult is BackupResult.Success)
        val restoreCounts = (restoreResult as BackupResult.Success).counts
        assertEquals(1, restoreCounts.courses)
        assertEquals(1, restoreCounts.slots)
        assertEquals(1, restoreCounts.tasks)

        // 5. Verify database contents
        val restoredCourses = courseDao.getAllCourses().first()
        assertEquals(1, restoredCourses.size)
        assertEquals("CS201", restoredCourses[0].code)
        assertEquals("Data Structures", restoredCourses[0].name)

        val restoredSlots = slotDao.getAllSlotsSync()
        assertEquals(1, restoredSlots.size)
        assertEquals("Room 101", restoredSlots[0].classroom)

        val restoredTasks = taskDao.getAllActiveTasksSync()
        assertEquals(1, restoredTasks.size)
        assertEquals("Homework 1", restoredTasks[0].title)
        assertEquals("Trees and graphs", restoredTasks[0].description)
        assertEquals(Priority.HIGH, restoredTasks[0].priority)
    }

    @Test
    fun testEncryptedBackupWithCorrectPasswordRoundTrip() = runBlocking {
        val courseDao = db.courseDao()
        val taskDao = db.taskDao()

        val courseId = courseDao.insertCourse(
            Course(name = "Operating Systems", code = "CS 350", colorHex = "#10B981")
        )
        taskDao.insertTask(
            TaskItem(courseId = courseId, title = "Thread Scheduler", dueDateEpochMillis = 1772946000000L)
        )

        val (_, uri) = createTempBackupFile("encrypted_backup.json")
        val password = "MyStrongPassword@2026"

        // 1. Export encrypted backup
        val exportResult = LocalBackupManager.export(context, uri, password = password)
        assertTrue(exportResult is BackupResult.Success)

        // 2. Inspect file identifies it as encrypted
        val inspection = LocalBackupManager.inspect(context, uri)
        assertEquals(LocalBackupManager.Inspection.ENCRYPTED, inspection)

        // 3. Clear database
        db.clearAllTables()

        // 4. Restore with correct password
        val restoreResult = LocalBackupManager.restore(context, uri, password = password)
        assertTrue("Restore must succeed: $restoreResult", restoreResult is BackupResult.Success)

        // 5. Verify restored data
        val courses = courseDao.getAllCourses().first()
        assertEquals(1, courses.size)
        assertEquals("CS350", courses[0].code)

        val tasks = taskDao.getAllActiveTasksSync()
        assertEquals(1, tasks.size)
        assertEquals("Thread Scheduler", tasks[0].title)
    }

    @Test
    fun testEncryptedBackupFailsWithWrongPassword() = runBlocking {
        val courseDao = db.courseDao()
        courseDao.insertCourse(Course(name = "Compilers", code = "CS 420", colorHex = "#6366F1"))

        val (_, uri) = createTempBackupFile("encrypted_wrong_pw.json")
        val exportResult = LocalBackupManager.export(context, uri, password = "RightPassword123")
        assertTrue(exportResult is BackupResult.Success)

        // Attempt restore with incorrect password
        val restoreResult = LocalBackupManager.restore(context, uri, password = "WrongPassword999")
        assertTrue("Restore must fail with wrong password", restoreResult is BackupResult.Failure)
        assertTrue("wrongPassword flag must be true", (restoreResult as BackupResult.Failure).wrongPassword)
    }

    @Test
    fun testMergeExistingCoursesWithoutDuplicates() = runBlocking {
        val courseDao = db.courseDao()
        val taskDao = db.taskDao()

        // Insert initial course
        val courseId = courseDao.insertCourse(
            Course(name = "Algorithms", code = "CS 101", colorHex = "#4F46E5")
        )
        taskDao.insertTask(
            TaskItem(courseId = courseId, title = "Pre-existing task", dueDateEpochMillis = 1000L)
        )

        // Create backup containing same course and an additional task
        val (_, uri) = createTempBackupFile("merge_backup.json")
        LocalBackupManager.export(context, uri, password = null)

        // Add a new local task before restoring
        taskDao.insertTask(
            TaskItem(courseId = courseId, title = "New local task", dueDateEpochMillis = 2000L)
        )

        // Restore backup into the existing database
        val restoreResult = LocalBackupManager.restore(context, uri, password = null)
        assertTrue(restoreResult is BackupResult.Success)

        // Course should NOT be duplicated
        val courses = courseDao.getAllCourses().first()
        assertEquals(1, courses.size)
        assertEquals("CS101", courses[0].code)

        // Both pre-existing and restored tasks exist
        val allTasks = taskDao.getAllActiveTasksSync()
        assertTrue(allTasks.size >= 2)
    }
}

