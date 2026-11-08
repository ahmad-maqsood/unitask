package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.Course
import com.example.data.local.LectureSlot
import com.example.data.local.TaskAttachment
import com.example.data.local.TaskItem
import com.example.data.local.TaskStatus
import com.example.reminder.ReminderScheduler
import com.example.util.formatCourseDeleteWarning
import com.example.util.queryCourseDeleteLoss
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class TaskTrashAuditTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var scheduler: ReminderScheduler
    private lateinit var repo: AppRepository
    private var courseId: Long = 0L

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
        val course = Course(name = "Operating Systems", code = "CS 201", colorHex = "#4F46E5")
        courseId = runBlocking { db.courseDao().insertCourse(course) }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testRescheduleAllOnBootIgnoresTrashedTasks() = runBlocking {
        val now = System.currentTimeMillis()
        val activeId = db.taskDao().insertTask(
            TaskItem(
                courseId = courseId,
                title = "Active Assignment",
                dueDateEpochMillis = now + 86400000L,
                dueTimeHour = 10,
                dueTimeMinute = 0,
                isDeleted = false,
                deletedAt = 0L
            )
        )
        val trashedId = db.taskDao().insertTask(
            TaskItem(
                courseId = courseId,
                title = "Trashed Assignment",
                dueDateEpochMillis = now + 86400000L,
                dueTimeHour = 10,
                dueTimeMinute = 0,
                isDeleted = true,
                deletedAt = now
            )
        )

        // Reschedule all simulates boot receiver logic: ReminderScheduler(context).rescheduleAll(db)
        scheduler.rescheduleAll(db)

        // Only active tasks are returned by getAllActiveTasksSync
        val activeTasks = db.taskDao().getAllActiveTasksSync()
        assertEquals(1, activeTasks.size)
        assertEquals(activeId, activeTasks.first().id)
        assertTrue(activeTasks.none { it.id == trashedId })
    }

    @Test
    fun testDailySummaryIgnoresTrashedTasks() = runBlocking {
        val now = System.currentTimeMillis()
        // Active task due today
        val activeId = db.taskDao().insertTask(
            TaskItem(
                courseId = courseId,
                title = "Daily Summary Active",
                dueDateEpochMillis = now,
                dueTimeHour = 14,
                dueTimeMinute = 0,
                isDeleted = false,
                deletedAt = 0L
            )
        )
        // Trashed task also due today
        val trashedId = db.taskDao().insertTask(
            TaskItem(
                courseId = courseId,
                title = "Daily Summary Trashed",
                dueDateEpochMillis = now,
                dueTimeHour = 14,
                dueTimeMinute = 0,
                isDeleted = true,
                deletedAt = now
            )
        )

        // Readers like DailySummaryReceiver query getAllActiveTasksSync()
        val tasks = db.taskDao().getAllActiveTasksSync()
        assertEquals(1, tasks.size)
        assertEquals(activeId, tasks.first().id)
        assertFalse(tasks.any { it.id == trashedId })
    }

    @Test
    fun testCourseCountsIgnoreTrashedTasks() = runBlocking {
        val now = System.currentTimeMillis()
        db.taskDao().insertTask(
            TaskItem(
                courseId = courseId,
                title = "Pending Active",
                dueDateEpochMillis = now + 10000,
                status = TaskStatus.PENDING,
                isDeleted = false
            )
        )
        db.taskDao().insertTask(
            TaskItem(
                courseId = courseId,
                title = "Completed Active",
                dueDateEpochMillis = now + 20000,
                status = TaskStatus.COMPLETED,
                isDeleted = false
            )
        )
        db.taskDao().insertTask(
            TaskItem(
                courseId = courseId,
                title = "Trashed Task",
                dueDateEpochMillis = now + 30000,
                status = TaskStatus.PENDING,
                isDeleted = true,
                deletedAt = now
            )
        )

        val counts = repo.coursesWithTaskCounts.first()
        val courseWithCount = counts.first { it.course.id == courseId }

        assertEquals(1, courseWithCount.pendingTasksCount)
        assertEquals(1, courseWithCount.completedTasksCount)
        assertEquals(2, courseWithCount.totalTasksCount)
    }

    @Test
    fun testDeleteCourseRemovesTrashedTasksAndAttachments() = runBlocking {
        val now = System.currentTimeMillis()
        val course = db.courseDao().getCourseByIdSync(courseId)!!

        val activeTaskId = db.taskDao().insertTask(
            TaskItem(
                courseId = courseId,
                title = "Active Assignment",
                dueDateEpochMillis = now + 86400000L,
                isDeleted = false
            )
        )
        val trashedTaskId = db.taskDao().insertTask(
            TaskItem(
                courseId = courseId,
                title = "Trashed Assignment",
                dueDateEpochMillis = now + 86400000L,
                isDeleted = true,
                deletedAt = now
            )
        )

        // Create attachment file and DB row for trashed task
        val attachmentsDir = File(context.filesDir, "task_attachments").apply { mkdirs() }
        val testFile = File(attachmentsDir, "trashed_att.pdf").apply { writeText("dummy content") }
        val attId = db.taskAttachmentDao().insertAttachment(
            TaskAttachment(
                taskId = trashedTaskId,
                fileName = "trashed_att.pdf",
                relativePath = "task_attachments/trashed_att.pdf",
                fileSize = 13L,
                mimeType = "application/pdf"
            )
        )

        assertTrue(testFile.exists())
        assertEquals(1, db.taskAttachmentDao().countAttachmentsForCourse(courseId))
        assertEquals(2, db.taskDao().countTasksForCourse(courseId))

        // Delete course through repository
        repo.deleteCourse(course)

        // Both active and trashed tasks must be removed from Room
        assertNull(db.taskDao().getAnyTaskById(activeTaskId))
        assertNull(db.taskDao().getAnyTaskById(trashedTaskId))
        assertTrue(db.taskDao().getAnyTasksForCourseSync(courseId).isEmpty())

        // Attachment DB row and physical file must be removed
        assertNull(db.taskAttachmentDao().getAttachmentById(attId))
        assertFalse(testFile.exists())
    }

    @Test
    fun testCountTextForCourseDialog() = runBlocking {
        // Test helper function with various combinations
        assertEquals(
            "This also permanently deletes 12 tasks, 3 classes and their attachments.",
            formatCourseDeleteWarning(taskCount = 12, classCount = 3, attachmentCount = 1)
        )
        assertEquals(
            "This also permanently deletes 1 task, 1 class and their attachments.",
            formatCourseDeleteWarning(taskCount = 1, classCount = 1, attachmentCount = 5)
        )
        assertEquals(
            "This also permanently deletes 5 tasks and 2 classes.",
            formatCourseDeleteWarning(taskCount = 5, classCount = 2, attachmentCount = 0)
        )
        assertEquals(
            "This also permanently deletes 1 task.",
            formatCourseDeleteWarning(taskCount = 1, classCount = 0, attachmentCount = 0)
        )
        assertEquals(
            "This also permanently deletes 3 classes.",
            formatCourseDeleteWarning(taskCount = 0, classCount = 3, attachmentCount = 0)
        )
        assertEquals(
            "",
            formatCourseDeleteWarning(taskCount = 0, classCount = 0, attachmentCount = 0)
        )

        // Test querying actual database counts including trashed tasks
        val course = db.courseDao().getCourseByIdSync(courseId)!!
        val now = System.currentTimeMillis()

        // 1 active task, 1 trashed task
        db.taskDao().insertTask(TaskItem(courseId = courseId, title = "Active", dueDateEpochMillis = now, isDeleted = false))
        val trashedTaskId = db.taskDao().insertTask(TaskItem(courseId = courseId, title = "Trashed", dueDateEpochMillis = now, isDeleted = true, deletedAt = now))

        // 1 slot
        db.lectureSlotDao().insertSlot(
            LectureSlot(
                courseId = courseId,
                dayOfWeek = 1,
                startHour = 9,
                startMinute = 0,
                endHour = 10,
                endMinute = 0,
                classroom = "Room 101"
            )
        )

        // 1 attachment
        db.taskAttachmentDao().insertAttachment(
            TaskAttachment(
                taskId = trashedTaskId,
                fileName = "test.pdf",
                relativePath = "test.pdf",
                fileSize = 100L,
                mimeType = "application/pdf"
            )
        )

        val loss = queryCourseDeleteLoss(db, course)
        assertEquals(2, loss.taskCount)
        assertEquals(1, loss.classCount)
        assertEquals(1, loss.attachmentCount)

        val warning = formatCourseDeleteWarning(loss.taskCount, loss.classCount, loss.attachmentCount)
        assertEquals("This also permanently deletes 2 tasks, 1 class and their attachments.", warning)
    }
}
