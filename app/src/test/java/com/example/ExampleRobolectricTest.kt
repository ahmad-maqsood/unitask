package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.Course
import com.example.data.local.CourseDao
import com.example.data.local.Converters
import com.example.data.local.Priority
import com.example.data.local.ReminderOption
import com.example.data.local.TaskDao
import com.example.data.local.TaskItem
import com.example.data.local.TaskStatus
import com.example.data.local.decodeReminderOptions
import com.example.data.local.encodeReminderOptions
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var db: AppDatabase
    private lateinit var courseDao: CourseDao
    private lateinit var taskDao: TaskDao
    private lateinit var lectureSlotDao: com.example.data.local.LectureSlotDao
    private lateinit var taskAttachmentDao: com.example.data.local.TaskAttachmentDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        courseDao = db.courseDao()
        taskDao = db.taskDao()
        lectureSlotDao = db.lectureSlotDao()
        taskAttachmentDao = db.taskAttachmentDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("UniTask", appName)
    }

    @Test
    fun `insert and retrieve courses and tasks in Room`() = runBlocking {
        val course = Course(
            name = "Algorithms",
            code = "CS 301",
            instructor = "Prof. Turing",
            colorHex = "#4F46E5",
            location = "Room 101"
        )
        val courseId = courseDao.insertCourse(course)
        assertTrue(courseId > 0)

        val retrievedCourses = courseDao.getAllCourses().first()
        assertEquals(1, retrievedCourses.size)
        assertEquals("CS 301", retrievedCourses[0].code)

        val task = TaskItem(
            courseId = courseId,
            title = "HW 1",
            description = "Graph algorithms",
            dueDateEpochMillis = System.currentTimeMillis() + 86400000L,
            priority = Priority.HIGH,
            status = TaskStatus.PENDING,
            reminderOptions = setOf(ReminderOption.AT_DEADLINE, ReminderOption.ONE_DAY_BEFORE)
        )
        val taskId = taskDao.insertTask(task)
        assertTrue(taskId > 0)

        val tasksWithCourse = taskDao.getAllTasksWithCourse().first()
        assertEquals(1, tasksWithCourse.size)
        assertEquals("HW 1", tasksWithCourse[0].task.title)
        assertNotNull(tasksWithCourse[0].course)
        assertEquals("CS 301", tasksWithCourse[0].course?.code)
        assertEquals(
            setOf(ReminderOption.AT_DEADLINE, ReminderOption.ONE_DAY_BEFORE),
            tasksWithCourse[0].task.reminderOptions
        )

        // Status update
        taskDao.updateStatus(taskId, TaskStatus.COMPLETED)
        val updated = taskDao.getTaskById(taskId)
        assertEquals(TaskStatus.COMPLETED, updated?.status)
    }

    @Test
    fun `reminder options encode and decode, including old single values`() {
        val both = setOf(ReminderOption.ONE_DAY_BEFORE, ReminderOption.AT_DEADLINE)
        assertEquals("AT_DEADLINE,ONE_DAY_BEFORE", encodeReminderOptions(both))
        assertEquals(both, decodeReminderOptions("AT_DEADLINE,ONE_DAY_BEFORE"))

        // Values written by earlier versions of the app
        assertEquals(setOf(ReminderOption.TWO_HOURS_BEFORE), decodeReminderOptions("TWO_HOURS_BEFORE"))
        assertEquals(emptySet<ReminderOption>(), decodeReminderOptions("NONE"))
        assertEquals(emptySet<ReminderOption>(), decodeReminderOptions(null))
        assertEquals(emptySet<ReminderOption>(), decodeReminderOptions("garbage"))
        assertEquals("NONE", encodeReminderOptions(emptySet()))
    }

    @Test
    fun `removed in-progress status is read as pending`() {
        assertEquals(TaskStatus.PENDING, Converters().toTaskStatus("IN_PROGRESS"))
        assertEquals(TaskStatus.COMPLETED, Converters().toTaskStatus("COMPLETED"))
    }

    @Test
    fun `test unsynced items and sync marking`() = runBlocking {
        val course = Course(
            name = "Calculus",
            code = "MATH 101",
            instructor = "Dr. Newton",
            colorHex = "#059669",
            isSynced = false
        )
        val courseId = courseDao.insertCourse(course)
        val unsynced = courseDao.getUnsyncedCourses()
        assertEquals(1, unsynced.size)

        courseDao.markCourseSynced(courseId, "cloud_c1")
        val remainingUnsynced = courseDao.getUnsyncedCourses()
        assertEquals(0, remainingUnsynced.size)
    }

    @Test
    fun `test lecture slot insertion and day retrieval`() = runBlocking {
        val course = Course(
            name = "Quantum Physics",
            code = "PHYS 300",
            instructor = "Dr. Feynman",
            colorHex = "#D97706"
        )
        val courseId = courseDao.insertCourse(course)

        val slotMon = com.example.data.local.LectureSlot(
            courseId = courseId,
            dayOfWeek = 1,
            startHour = 9,
            startMinute = 0,
            endHour = 10,
            endMinute = 30,
            classroom = "Hall B"
        )
        val slotId = lectureSlotDao.insertSlot(slotMon)
        assertTrue(slotId > 0)

        val mondaySlots = lectureSlotDao.getSlotsForDay(1).first()
        assertEquals(1, mondaySlots.size)
        assertEquals("PHYS 300", mondaySlots[0].course?.code)
        assertEquals("Hall B", mondaySlots[0].slot.classroom)
    }

    @Test
    fun `test task attachment linking and task query`() = runBlocking {
        val courseId = courseDao.insertCourse(
            Course(name = "Organic Chemistry", code = "CHEM 210", instructor = "Dr. Curie", colorHex = "#7C3AED")
        )
        val taskId = taskDao.insertTask(
            TaskItem(
                courseId = courseId,
                title = "Lab Report 3",
                dueDateEpochMillis = System.currentTimeMillis() + 1000000L
            )
        )

        val attachment = com.example.data.local.TaskAttachment(
            taskId = taskId,
            fileName = "syllabus.pdf",
            relativePath = "attachments/att_123.pdf",
            fileSize = 1048576L,
            mimeType = "application/pdf"
        )
        val attId = taskAttachmentDao.insertAttachment(attachment)
        assertTrue(attId > 0)

        val taskWithCourse = taskDao.getAllTasksWithCourse().first()
        assertEquals(1, taskWithCourse.size)
        assertEquals(1, taskWithCourse[0].attachments.size)
        assertEquals("syllabus.pdf", taskWithCourse[0].attachments[0].fileName)
    }

    @Test
    fun `test soft-delete course hides from UI queries and marks for sync`() = runBlocking {
        val course = Course(
            name = "Database Systems",
            code = "CS 450",
            instructor = "Prof. Stonebraker",
            colorHex = "#2563EB",
            isSynced = true,
            cloudId = "cloud_cs450"
        )
        val courseId = courseDao.insertCourse(course)
        val taskId = taskDao.insertTask(
            TaskItem(
                courseId = courseId,
                title = "SQL Project",
                dueDateEpochMillis = System.currentTimeMillis() + 86400000L,
                isSynced = true,
                cloudId = "cloud_t1"
            )
        )

        // Before soft-delete, items exist in active queries
        assertEquals(1, courseDao.getAllCourses().first().size)
        assertEquals(1, taskDao.getAllTasksWithCourse().first().size)

        // Soft-delete course & tasks
        courseDao.softDeleteCourse(courseId)
        taskDao.softDeleteTasksForCourse(courseId)

        // Active UI flows immediately return 0 items
        assertEquals(0, courseDao.getAllCourses().first().size)
        assertEquals(0, taskDao.getAllTasksWithCourse().first().size)

        // Deleted courses query identifies the soft-deleted item for cloud sync
        val deletedCourses = courseDao.getDeletedCourses()
        assertEquals(1, deletedCourses.size)
        assertEquals("CS 450", deletedCourses[0].code)
        assertTrue(deletedCourses[0].isDeleted)

        val deletedTasks = taskDao.getDeletedTasks()
        assertEquals(1, deletedTasks.size)
        assertEquals("SQL Project", deletedTasks[0].title)
        assertTrue(deletedTasks[0].isDeleted)

        // Guard query confirms code is marked deleted
        assertTrue(courseDao.isCourseDeletedByCode("CS 450"))

        // Deletion guard in FirestoreSyncManager
        com.example.sync.FirestoreSyncManager.recordPendingCourseDeletion(courseId, "CS 450", "cloud_cs450")
        assertTrue(com.example.sync.FirestoreSyncManager.isCourseDeletionPending("CS 450"))
        assertTrue(com.example.sync.FirestoreSyncManager.isCourseDeletionPending("cs 450"))
    }
}
