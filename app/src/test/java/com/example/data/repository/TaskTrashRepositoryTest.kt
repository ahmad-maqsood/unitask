package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.Course
import com.example.data.local.Priority
import com.example.data.local.ReminderOption
import com.example.data.local.TaskAttachment
import com.example.data.local.TaskItem
import com.example.data.local.TaskStatus
import com.example.reminder.ReminderScheduler
import com.example.util.LocalBackupManager
import com.example.util.LocalTombstonePurge
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
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TaskTrashRepositoryTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var scheduler: ReminderScheduler
    private lateinit var repo: AppRepository
    private var courseId: Long = 0L
    private lateinit var attachmentsDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        scheduler = ReminderScheduler(context)
        repo = AppRepository(
            context = context,
            courseDao = db.courseDao(),
            taskDao = db.taskDao(),
            taskAttachmentDao = db.taskAttachmentDao(),
            lectureSlotDao = db.lectureSlotDao(),
            reminderScheduler = scheduler,
            noteDao = db.noteDao(),
            database = db
        )
        courseId = runBlocking {
            db.courseDao().insertCourse(Course(name = "Math", code = "MATH 101", colorHex = "#4F46E5"))
        }
        attachmentsDir = File(context.filesDir, "attachments").apply { mkdirs() }
        context.getSharedPreferences("local_tombstone_purge", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @After
    fun tearDown() {
        db.close()
        if (attachmentsDir.exists()) {
            attachmentsDir.deleteRecursively()
        }
        context.getSharedPreferences("local_tombstone_purge", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun deleteMovesTaskToTrashAndCancelsReminder() = runBlocking {
        val futureDue = System.currentTimeMillis() + 86400_000L
        val task = TaskItem(
            courseId = courseId,
            title = "Assignment 1",
            dueDateEpochMillis = futureDue,
            dueTimeHour = 14,
            dueTimeMinute = 0,
            reminderOptions = setOf(ReminderOption.AT_DEADLINE)
        )
        val id = repo.insertTask(task)
        val inserted = db.taskDao().getTaskById(id)!!
        assertNotNull(inserted.reminderEpochMillis)

        repo.deleteTask(inserted)

        // Active task query excludes it
        assertTrue(db.taskDao().getAllActiveTasksSync().none { it.id == id })
        assertNull(db.taskDao().getTaskById(id))

        // Trashed task query includes it
        val deletedList = db.taskDao().getDeletedTasksSync()
        val trashed = deletedList.firstOrNull { it.id == id }
        assertNotNull(trashed)
        assertTrue(trashed!!.isDeleted)
        assertTrue(trashed.deletedAt > 0L)
    }

    @Test
    fun restoreBringsItBackWithReminderAndDoesNotScheduleInPast() = runBlocking {
        val now = System.currentTimeMillis()

        // 1. Task with future deadline: restore recomputes upcoming reminder
        val futureDue = now + 86400_000L
        val futureTask = TaskItem(
            courseId = courseId,
            title = "Future Task",
            dueDateEpochMillis = futureDue,
            dueTimeHour = 14,
            dueTimeMinute = 0,
            reminderOptions = setOf(ReminderOption.AT_DEADLINE)
        )
        val futureId = repo.insertTask(futureTask)
        val savedFuture = db.taskDao().getTaskById(futureId)!!
        repo.deleteTask(savedFuture)
        assertEquals(1, db.taskDao().getDeletedTasksSync().size)

        repo.restoreTask(futureId)
        val restoredFuture = db.taskDao().getTaskById(futureId)
        assertNotNull(restoredFuture)
        assertFalse(restoredFuture!!.isDeleted)
        assertEquals(0L, restoredFuture.deletedAt)
        assertNotNull(restoredFuture.reminderEpochMillis)

        // 2. Task with past deadline: restore clears reminderEpochMillis
        val pastDue = now - 86400_000L
        val pastTask = TaskItem(
            courseId = courseId,
            title = "Past Task",
            dueDateEpochMillis = pastDue,
            dueTimeHour = 10,
            dueTimeMinute = 0,
            reminderOptions = setOf(ReminderOption.AT_DEADLINE)
        )
        val pastId = db.taskDao().insertTask(pastTask)
        val savedPast = db.taskDao().getTaskById(pastId)!!
        repo.deleteTask(savedPast)

        repo.restoreTask(pastId)
        val restoredPast = db.taskDao().getTaskById(pastId)
        assertNotNull(restoredPast)
        assertFalse(restoredPast!!.isDeleted)
        assertEquals(0L, restoredPast.deletedAt)
        assertNull(restoredPast.reminderEpochMillis)

        // 3. undoDeleteTask delegates to restoreTask
        repo.deleteTask(restoredFuture)
        assertTrue(db.taskDao().getTaskById(futureId) == null)
        repo.undoDeleteTask(restoredFuture)
        val undone = db.taskDao().getTaskById(futureId)
        assertNotNull(undone)
        assertFalse(undone!!.isDeleted)
        assertEquals(0L, undone.deletedAt)
    }

    @Test
    fun attachmentsStayUntilDeleteForeverAndAreRemovedThen() = runBlocking {
        val taskId = repo.insertTask(
            TaskItem(courseId = courseId, title = "Task with attachment", dueDateEpochMillis = 1000L)
        )
        val relPath = "attachments/att_stay_test.txt"
        val attFile = File(context.filesDir, relPath).apply {
            parentFile?.mkdirs()
            writeBytes("test attachment content".toByteArray())
        }
        db.taskAttachmentDao().insertAttachment(
            TaskAttachment(
                taskId = taskId,
                fileName = "att_stay_test.txt",
                relativePath = relPath,
                fileSize = attFile.length(),
                mimeType = "text/plain"
            )
        )

        // Soft delete keeps attachment row and physical file
        val task = db.taskDao().getTaskById(taskId)!!
        repo.deleteTask(task)

        assertTrue(attFile.exists())
        assertEquals(1, db.taskAttachmentDao().getAttachmentsForTaskSync(taskId).size)

        // Delete forever removes attachment row, physical file, and task row
        repo.deleteTaskForever(taskId)

        assertFalse(attFile.exists())
        assertTrue(db.taskAttachmentDao().getAttachmentsForTaskSync(taskId).isEmpty())
        assertNull(db.taskDao().getAnyTaskById(taskId))
    }

    @Test
    fun purgeRemovesOnlyTasksOlderThan30DaysAndTheirFiles() = runBlocking {
        val now = 100_000_000_000L
        val thirtyDaysMs = NoteRepository.TRASH_DAYS * 24L * 60L * 60L * 1000L

        // Old trashed task (35 days old)
        val oldTaskId = db.taskDao().insertTask(
            TaskItem(
                courseId = courseId,
                title = "Old Trashed",
                dueDateEpochMillis = 1000L,
                isDeleted = true,
                deletedAt = now - thirtyDaysMs - 5 * 86400_000L
            )
        )
        val oldFile = File(attachmentsDir, "old_file.txt").apply { writeBytes("old".toByteArray()) }
        db.taskAttachmentDao().insertAttachment(
            TaskAttachment(taskId = oldTaskId, fileName = "old.txt", relativePath = "attachments/old_file.txt", fileSize = 3, mimeType = "text/plain")
        )

        // Recent trashed task (10 days old)
        val recentTaskId = db.taskDao().insertTask(
            TaskItem(
                courseId = courseId,
                title = "Recent Trashed",
                dueDateEpochMillis = 1000L,
                isDeleted = true,
                deletedAt = now - 10 * 86400_000L
            )
        )
        val recentFile = File(attachmentsDir, "recent_file.txt").apply { writeBytes("recent".toByteArray()) }
        db.taskAttachmentDao().insertAttachment(
            TaskAttachment(taskId = recentTaskId, fileName = "recent.txt", relativePath = "attachments/recent_file.txt", fileSize = 6, mimeType = "text/plain")
        )

        // Active task
        val activeTaskId = db.taskDao().insertTask(
            TaskItem(courseId = courseId, title = "Active Task", dueDateEpochMillis = 1000L, isDeleted = false, deletedAt = 0L)
        )
        val activeFile = File(attachmentsDir, "active_file.txt").apply { writeBytes("active".toByteArray()) }
        db.taskAttachmentDao().insertAttachment(
            TaskAttachment(taskId = activeTaskId, fileName = "active.txt", relativePath = "attachments/active_file.txt", fileSize = 6, mimeType = "text/plain")
        )

        val purgedCount = repo.purgeOldTaskTrash(now)
        assertEquals(1, purgedCount)

        // Old trashed task and file deleted
        assertNull(db.taskDao().getAnyTaskById(oldTaskId))
        assertFalse(oldFile.exists())
        assertTrue(db.taskAttachmentDao().getAttachmentsForTaskSync(oldTaskId).isEmpty())

        // Recent trashed task and active task preserved
        assertNotNull(db.taskDao().getAnyTaskById(recentTaskId))
        assertTrue(recentFile.exists())
        assertNotNull(db.taskDao().getAnyTaskById(activeTaskId))
        assertTrue(activeFile.exists())
    }

    @Test
    fun deleteWhenDoneLandsInTrash() = runBlocking {
        val now = System.currentTimeMillis()
        val twoDaysAgo = now - 2 * 86400_000L

        val task = TaskItem(
            courseId = courseId,
            title = "Delete When Done Task",
            dueDateEpochMillis = twoDaysAgo,
            status = TaskStatus.COMPLETED,
            autoDeleteWhenDone = true,
            completedAtMillis = twoDaysAgo
        )
        val id = repo.insertTask(task)

        val rolled = repo.rolloverCompletedRepeatingTasks()
        assertTrue(rolled)

        // Active tasks no longer contain it
        assertNull(db.taskDao().getTaskById(id))

        // Lands in trash with deletedAt set
        val trashed = db.taskDao().getDeletedTasksSync().firstOrNull { it.id == id }
        assertNotNull(trashed)
        assertTrue(trashed!!.isDeleted)
        assertTrue(trashed.deletedAt > 0L)
    }

    @Test
    fun backupExportExcludesTrashedTasks() = runBlocking {
        // Active task
        repo.insertTask(TaskItem(courseId = courseId, title = "Active Backup Task", dueDateEpochMillis = 1000L))

        // Trashed task
        val trashedId = repo.insertTask(TaskItem(courseId = courseId, title = "Trashed Backup Task", dueDateEpochMillis = 1000L))
        val trashedTask = db.taskDao().getTaskById(trashedId)!!
        repo.deleteTask(trashedTask)

        val built = LocalBackupManager.buildData(context, inlineImages = false)
        val tasksArray = built.data.getJSONArray("tasks")
        assertEquals(1, tasksArray.length())
        assertEquals("Active Backup Task", tasksArray.getJSONObject(0).getString("title"))
        assertEquals(1, built.counts.tasks)
    }

    @Test
    fun purgeOnceKeepsTrashedTasks() = runBlocking {
        // Old sync-deleted task (isDeleted = true, deletedAt = 0)
        val syncDeletedId = db.taskDao().insertTask(
            TaskItem(courseId = courseId, title = "Sync Deleted Task", dueDateEpochMillis = 1000L, isDeleted = true, deletedAt = 0L)
        )

        // User-trashed task (isDeleted = true, deletedAt > 0)
        val trashedId = db.taskDao().insertTask(
            TaskItem(courseId = courseId, title = "User Trashed Task", dueDateEpochMillis = 1000L, isDeleted = true, deletedAt = System.currentTimeMillis())
        )

        LocalTombstonePurge.purgeOnce(context, db)

        // Sync-deleted task was purged
        assertNull(db.taskDao().getAnyTaskById(syncDeletedId))

        // User-trashed task was preserved
        assertNotNull(db.taskDao().getAnyTaskById(trashedId))
    }
}
