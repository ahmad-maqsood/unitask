package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.Course
import com.example.data.local.CourseDao
import com.example.data.local.LectureSlot
import com.example.data.local.LectureSlotDao
import com.example.data.local.Priority
import com.example.data.local.TaskDao
import com.example.data.local.TaskItem
import com.example.data.local.TaskStatus
import com.example.sync.SupabaseSyncManager
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
class CourseDedupTest {

    private lateinit var db: AppDatabase
    private lateinit var courseDao: CourseDao
    private lateinit var taskDao: TaskDao
    private lateinit var lectureSlotDao: LectureSlotDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        courseDao = db.courseDao()
        taskDao = db.taskDao()
        lectureSlotDao = db.lectureSlotDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testDedupReassignsTasksAndSlotsUnderKeptCourse() = runBlocking {
        // Two courses with the same normalized code
        val course1 = Course(
            name = "Computer Science 1",
            code = "CS 101",
            colorHex = "#4F46E5"
        )
        val course1Id = courseDao.insertCourse(course1)

        val course2 = Course(
            name = "CS Duplicate",
            code = "cs101",
            colorHex = "#4F46E5"
        )
        val course2Id = courseDao.insertCourse(course2)

        // Task 1 under Course 1
        val task1 = TaskItem(
            courseId = course1Id,
            title = "Task from Course 1",
            dueDateEpochMillis = 1000L,
            priority = Priority.MEDIUM,
            status = TaskStatus.PENDING,
            cloudId = "cloud_task_1"
        )
        taskDao.insertTask(task1)

        // Task 2 under Course 2 (unsynced or distinct)
        val task2 = TaskItem(
            courseId = course2Id,
            title = "Task from Course 2",
            dueDateEpochMillis = 2000L,
            priority = Priority.HIGH,
            status = TaskStatus.PENDING,
            cloudId = ""
        )
        taskDao.insertTask(task2)

        // Non-colliding slots under Course 1 and Course 2
        val slot1 = LectureSlot(
            courseId = course1Id,
            dayOfWeek = 1,
            startHour = 9,
            startMinute = 0,
            endHour = 10,
            endMinute = 0
        )
        lectureSlotDao.insertSlot(slot1)

        val slot2 = LectureSlot(
            courseId = course2Id,
            dayOfWeek = 2,
            startHour = 14,
            startMinute = 0,
            endHour = 15,
            endMinute = 0
        )
        lectureSlotDao.insertSlot(slot2)

        // Run the course dedup path for the normalized code "CS101"
        SupabaseSyncManager.deduplicateCourse(db, course1Id, "CS101")

        // Assert duplicate course 2 was deleted and course 1 survived
        assertNull(courseDao.getAnyCourseByIdSync(course2Id))
        assertNotNull(courseDao.getAnyCourseByIdSync(course1Id))

        // Assert both tasks survived under the kept course
        val tasksUnderCourse1 = taskDao.getAnyTasksForCourseSync(course1Id)
        assertEquals(2, tasksUnderCourse1.size)
        assertTrue(tasksUnderCourse1.any { it.title == "Task from Course 1" && it.courseId == course1Id })
        assertTrue(tasksUnderCourse1.any { it.title == "Task from Course 2" && it.courseId == course1Id })

        // Assert no tasks remain under old course ID
        val tasksUnderCourse2 = taskDao.getAnyTasksForCourseSync(course2Id)
        assertTrue(tasksUnderCourse2.isEmpty())

        // Assert both slots survived under the kept course
        val slotsUnderCourse1 = lectureSlotDao.getSlotsForCourseSync(course1Id)
        assertEquals(2, slotsUnderCourse1.size)
        assertTrue(slotsUnderCourse1.all { it.courseId == course1Id })
    }

    @Test
    fun testDedupRemovesExactCloudIdDuplicatesAndCollidingSlots() = runBlocking {
        val course1 = Course(name = "Math A", code = "MATH 201")
        val course1Id = courseDao.insertCourse(course1)

        val course2 = Course(name = "Math B", code = "MATH201")
        val course2Id = courseDao.insertCourse(course2)

        // Two tasks with the same non-blank cloudId
        val task1 = TaskItem(
            courseId = course1Id,
            title = "HW 1",
            dueDateEpochMillis = 1000L,
            cloudId = "same_cloud_task_id"
        )
        taskDao.insertTask(task1)

        val task2 = TaskItem(
            courseId = course2Id,
            title = "HW 1 Duplicate",
            dueDateEpochMillis = 1000L,
            cloudId = "same_cloud_task_id"
        )
        taskDao.insertTask(task2)

        // Two slots with the same dayOfWeek and startTime (colliding)
        val slot1 = LectureSlot(
            courseId = course1Id,
            dayOfWeek = 3,
            startHour = 10,
            startMinute = 0,
            endHour = 11,
            endMinute = 0
        )
        lectureSlotDao.insertSlot(slot1)

        val slot2 = LectureSlot(
            courseId = course2Id,
            dayOfWeek = 3,
            startHour = 10,
            startMinute = 0,
            endHour = 11,
            endMinute = 0
        )
        lectureSlotDao.insertSlot(slot2)

        SupabaseSyncManager.deduplicateCourse(db, course1Id, "MATH201")

        // Exact cloudId duplicate task is deduplicated to 1 task
        val tasks = taskDao.getAnyTasksForCourseSync(course1Id)
        assertEquals(1, tasks.size)
        assertEquals("same_cloud_task_id", tasks[0].cloudId)

        // Colliding slot is deduplicated to 1 slot
        val slots = lectureSlotDao.getSlotsForCourseSync(course1Id)
        assertEquals(1, slots.size)
        assertEquals(3, slots[0].dayOfWeek)
        assertEquals(10, slots[0].startHour)
        assertEquals(0, slots[0].startMinute)
    }
}

