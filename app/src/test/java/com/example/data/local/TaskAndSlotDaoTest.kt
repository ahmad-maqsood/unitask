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
class TaskAndSlotDaoTest {

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

    // -------------------------------------------------------------------------
    // TaskDao Tests
    // -------------------------------------------------------------------------

    @Test
    fun testTaskDaoInsertAndRetrieve() = runBlocking {
        val courseId = courseDao.insertCourse(
            Course(name = "Data Structures", code = "CS 201", colorHex = "#4F46E5")
        )
        val task = TaskItem(
            courseId = courseId,
            title = "Binary Trees Assignment",
            description = "Implement red-black tree insertion",
            dueDateEpochMillis = 1772946000000L,
            dueTimeHour = 14,
            dueTimeMinute = 30,
            priority = Priority.HIGH,
            status = TaskStatus.PENDING,
            reminderOptions = setOf(ReminderOption.AT_DEADLINE)
        )

        val taskId = taskDao.insertTask(task)
        assertTrue(taskId > 0)

        val retrieved = taskDao.getTaskById(taskId)
        assertNotNull(retrieved)
        assertEquals("Binary Trees Assignment", retrieved?.title)
        assertEquals(courseId, retrieved?.courseId)
        assertEquals(14, retrieved?.dueTimeHour)
        assertEquals(30, retrieved?.dueTimeMinute)
        assertEquals(Priority.HIGH, retrieved?.priority)
    }

    @Test
    fun testTaskDaoReassignTasksToCourse() = runBlocking {
        val course1Id = courseDao.insertCourse(Course(name = "Old Course", code = "CS 101", colorHex = "#111111"))
        val course2Id = courseDao.insertCourse(Course(name = "New Course", code = "CS 102", colorHex = "#222222"))

        val t1 = taskDao.insertTask(TaskItem(courseId = course1Id, title = "Task 1", dueDateEpochMillis = 1000L))
        val t2 = taskDao.insertTask(TaskItem(courseId = course1Id, title = "Task 2", dueDateEpochMillis = 2000L))

        assertEquals(2, taskDao.getTasksForCourseSync(course1Id).size)
        assertEquals(0, taskDao.getTasksForCourseSync(course2Id).size)

        // Reassign all tasks from Course 1 to Course 2
        taskDao.reassignTasksToCourse(oldCourseId = course1Id, newCourseId = course2Id)

        assertEquals(0, taskDao.getTasksForCourseSync(course1Id).size)
        val reassigned = taskDao.getTasksForCourseSync(course2Id)
        assertEquals(2, reassigned.size)
        assertTrue(reassigned.any { it.id == t1 && it.courseId == course2Id })
        assertTrue(reassigned.any { it.id == t2 && it.courseId == course2Id })
    }

    @Test
    fun testTaskDaoDeleteOrphanedTasks() = runBlocking {
        val course1Id = courseDao.insertCourse(Course(name = "Active Course", code = "ACT 100", colorHex = "#111111"))
        val course2Id = courseDao.insertCourse(Course(name = "Deleted Course", code = "DEL 200", colorHex = "#222222"))

        val taskUnderActive = taskDao.insertTask(TaskItem(courseId = course1Id, title = "Keep Me", dueDateEpochMillis = 1000L))
        val taskUnderDeleted = taskDao.insertTask(TaskItem(courseId = course2Id, title = "Remove Me", dueDateEpochMillis = 2000L))
        val taskUnderNonExistent = taskDao.insertTask(TaskItem(courseId = 99999L, title = "Orphaned", dueDateEpochMillis = 3000L))

        // Soft-delete Course 2
        courseDao.softDeleteCourse(course2Id)

        // Delete orphaned tasks
        taskDao.deleteOrphanedTasks()

        assertNotNull(taskDao.getAnyTaskById(taskUnderActive))
        assertNull(taskDao.getAnyTaskById(taskUnderDeleted))
        assertNull(taskDao.getAnyTaskById(taskUnderNonExistent))
    }

    @Test
    fun testTaskDaoOrderingQueries() = runBlocking {
        val courseId = courseDao.insertCourse(Course(name = "Math", code = "MATH 200", colorHex = "#333333"))

        // Insert tasks out of chronological order
        // Task 1: Day 2 at 10:00
        val tDay2Hour10 = taskDao.insertTask(
            TaskItem(courseId = courseId, title = "T1", dueDateEpochMillis = 200000L, dueTimeHour = 10, dueTimeMinute = 0)
        )
        // Task 2: Day 1 at 15:30
        val tDay1Hour15Min30 = taskDao.insertTask(
            TaskItem(courseId = courseId, title = "T2", dueDateEpochMillis = 100000L, dueTimeHour = 15, dueTimeMinute = 30)
        )
        // Task 3: Day 1 at 09:00
        val tDay1Hour9Min0 = taskDao.insertTask(
            TaskItem(courseId = courseId, title = "T3", dueDateEpochMillis = 100000L, dueTimeHour = 9, dueTimeMinute = 0)
        )
        // Task 4: Day 1 at 15:00
        val tDay1Hour15Min0 = taskDao.insertTask(
            TaskItem(courseId = courseId, title = "T4", dueDateEpochMillis = 100000L, dueTimeHour = 15, dueTimeMinute = 0)
        )

        // getAllTasksWithCourse() orders by dueDateEpochMillis ASC, dueTimeHour ASC, dueTimeMinute ASC
        val sortedList = taskDao.getAllTasksWithCourse().first()
        assertEquals(4, sortedList.size)
        assertEquals(tDay1Hour9Min0, sortedList[0].task.id)
        assertEquals(tDay1Hour15Min0, sortedList[1].task.id)
        assertEquals(tDay1Hour15Min30, sortedList[2].task.id)
        assertEquals(tDay2Hour10, sortedList[3].task.id)

        // getTasksForCourse() verifies same ordering for course-specific flow
        val courseTasks = taskDao.getTasksForCourse(courseId).first()
        assertEquals(4, courseTasks.size)
        assertEquals(tDay1Hour9Min0, courseTasks[0].task.id)
        assertEquals(tDay1Hour15Min0, courseTasks[1].task.id)
        assertEquals(tDay1Hour15Min30, courseTasks[2].task.id)
        assertEquals(tDay2Hour10, courseTasks[3].task.id)
    }

    // -------------------------------------------------------------------------
    // LectureSlotDao Tests
    // -------------------------------------------------------------------------

    @Test
    fun testLectureSlotDaoInsertAndRetrieve() = runBlocking {
        val courseId = courseDao.insertCourse(Course(name = "Physics", code = "PHYS 101", colorHex = "#0EA5E9"))
        val slot = LectureSlot(
            courseId = courseId,
            dayOfWeek = 1,
            startHour = 10,
            startMinute = 0,
            endHour = 11,
            endMinute = 30,
            classroom = "Lecture Hall A",
            instructor = "Dr. Maxwell"
        )

        val slotId = lectureSlotDao.insertSlot(slot)
        assertTrue(slotId > 0)

        val retrieved = lectureSlotDao.getSlotById(slotId)
        assertNotNull(retrieved)
        assertEquals("Lecture Hall A", retrieved?.classroom)
        assertEquals("Dr. Maxwell", retrieved?.instructor)
        assertEquals(1, retrieved?.dayOfWeek)
    }

    @Test
    fun testLectureSlotDaoReassignSlotsToCourse() = runBlocking {
        val course1Id = courseDao.insertCourse(Course(name = "Bio 1", code = "BIO 101", colorHex = "#10B981"))
        val course2Id = courseDao.insertCourse(Course(name = "Bio 2", code = "BIO 102", colorHex = "#059669"))

        val s1 = lectureSlotDao.insertSlot(LectureSlot(courseId = course1Id, dayOfWeek = 1, startHour = 9, startMinute = 0, endHour = 10, endMinute = 0))
        val s2 = lectureSlotDao.insertSlot(LectureSlot(courseId = course1Id, dayOfWeek = 3, startHour = 9, startMinute = 0, endHour = 10, endMinute = 0))

        assertEquals(2, lectureSlotDao.getSlotsForCourseSync(course1Id).size)
        assertEquals(0, lectureSlotDao.getSlotsForCourseSync(course2Id).size)

        // Reassign slots from Course 1 to Course 2
        lectureSlotDao.reassignSlotsToCourse(oldCourseId = course1Id, newCourseId = course2Id)

        assertEquals(0, lectureSlotDao.getSlotsForCourseSync(course1Id).size)
        val reassigned = lectureSlotDao.getSlotsForCourseSync(course2Id)
        assertEquals(2, reassigned.size)
        assertTrue(reassigned.any { it.id == s1 && it.courseId == course2Id })
        assertTrue(reassigned.any { it.id == s2 && it.courseId == course2Id })
    }

    @Test
    fun testLectureSlotDaoDeleteOrphanedSlots() = runBlocking {
        val course1Id = courseDao.insertCourse(Course(name = "History", code = "HIST 100", colorHex = "#F59E0B"))
        val course2Id = courseDao.insertCourse(Course(name = "Latin", code = "LAT 200", colorHex = "#D97706"))

        val slotValid = lectureSlotDao.insertSlot(LectureSlot(courseId = course1Id, dayOfWeek = 2, startHour = 10, startMinute = 0, endHour = 11, endMinute = 0))
        val slotSoftDeletedCourse = lectureSlotDao.insertSlot(LectureSlot(courseId = course2Id, dayOfWeek = 4, startHour = 10, startMinute = 0, endHour = 11, endMinute = 0))
        val slotNonExistent = lectureSlotDao.insertSlot(LectureSlot(courseId = 88888L, dayOfWeek = 5, startHour = 10, startMinute = 0, endHour = 11, endMinute = 0))

        courseDao.softDeleteCourse(course2Id)

        // Delete orphaned slots
        lectureSlotDao.deleteOrphanedSlots()

        assertNotNull(lectureSlotDao.getSlotById(slotValid))
        assertNull(lectureSlotDao.getSlotById(slotSoftDeletedCourse))
        assertNull(lectureSlotDao.getSlotById(slotNonExistent))
    }

    @Test
    fun testLectureSlotDaoOrderingQueries() = runBlocking {
        val courseId = courseDao.insertCourse(Course(name = "Chemistry", code = "CHEM 101", colorHex = "#EC4899"))

        // Insert slots out of order
        // Tuesday 14:00
        val sTue14 = lectureSlotDao.insertSlot(
            LectureSlot(courseId = courseId, dayOfWeek = 2, startHour = 14, startMinute = 0, endHour = 15, endMinute = 0)
        )
        // Monday 11:30
        val sMon1130 = lectureSlotDao.insertSlot(
            LectureSlot(courseId = courseId, dayOfWeek = 1, startHour = 11, startMinute = 30, endHour = 12, endMinute = 30)
        )
        // Monday 08:00
        val sMon08 = lectureSlotDao.insertSlot(
            LectureSlot(courseId = courseId, dayOfWeek = 1, startHour = 8, startMinute = 0, endHour = 9, endMinute = 0)
        )
        // Monday 11:00
        val sMon1100 = lectureSlotDao.insertSlot(
            LectureSlot(courseId = courseId, dayOfWeek = 1, startHour = 11, startMinute = 0, endHour = 12, endMinute = 0)
        )

        // getAllSlotsWithCourse orders by dayOfWeek ASC, startHour ASC, startMinute ASC
        val allSlots = lectureSlotDao.getAllSlotsWithCourse().first()
        assertEquals(4, allSlots.size)
        assertEquals(sMon08, allSlots[0].slot.id)
        assertEquals(sMon1100, allSlots[1].slot.id)
        assertEquals(sMon1130, allSlots[2].slot.id)
        assertEquals(sTue14, allSlots[3].slot.id)

        // getSlotsForDay(1) orders Monday slots by startHour ASC, startMinute ASC
        val mondaySlots = lectureSlotDao.getSlotsForDay(1).first()
        assertEquals(3, mondaySlots.size)
        assertEquals(sMon08, mondaySlots[0].slot.id)
        assertEquals(sMon1100, mondaySlots[1].slot.id)
        assertEquals(sMon1130, mondaySlots[2].slot.id)
    }
}

