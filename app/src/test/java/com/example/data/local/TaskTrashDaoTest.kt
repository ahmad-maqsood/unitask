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
class TaskTrashDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var taskDao: TaskDao
    private lateinit var courseDao: CourseDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        taskDao = db.taskDao()
        courseDao = db.courseDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testSoftDeleteHidesTaskFromNormalQueriesAndShowsInDeleted() = runBlocking {
        val courseId = courseDao.insertCourse(Course(name = "Math", code = "MATH101", colorHex = "#123456"))
        val taskId = taskDao.insertTask(
            TaskItem(courseId = courseId, title = "Homework 1", dueDateEpochMillis = 1000L)
        )

        // Initially active
        assertEquals(1, taskDao.getAllTasksWithCourse().first().size)
        assertEquals(1, taskDao.getAllActiveTasksSync().size)
        assertNotNull(taskDao.getTaskById(taskId))
        assertTrue(taskDao.observeDeletedTasks().first().isEmpty())
        assertTrue(taskDao.getDeletedTasksSync().isEmpty())

        val deleteTimestamp = 5000L
        taskDao.softDeleteTask(id = taskId, deletedAt = deleteTimestamp)

        // Hidden from normal queries
        assertTrue(taskDao.getAllTasksWithCourse().first().isEmpty())
        assertTrue(taskDao.getAllActiveTasksSync().isEmpty())
        assertNull(taskDao.getTaskById(taskId))

        // Shown in deleted queries
        val deletedWithCourse = taskDao.observeDeletedTasks().first()
        assertEquals(1, deletedWithCourse.size)
        val deletedItem = deletedWithCourse.first()
        assertEquals(taskId, deletedItem.task.id)
        assertTrue(deletedItem.task.isDeleted)
        assertEquals(deleteTimestamp, deletedItem.task.deletedAt)
        assertEquals(false, deletedItem.task.isSynced)
        assertEquals("Math", deletedItem.course?.name)

        val deletedSync = taskDao.getDeletedTasksSync()
        assertEquals(1, deletedSync.size)
        assertEquals(taskId, deletedSync.first().id)
        assertEquals(deleteTimestamp, deletedSync.first().deletedAt)
    }

    @Test
    fun testRestoreBringsDeletedTaskBackAndClearsDeletedAt() = runBlocking {
        val courseId = courseDao.insertCourse(Course(name = "Math", code = "MATH101", colorHex = "#123456"))
        val taskId = taskDao.insertTask(
            TaskItem(courseId = courseId, title = "Homework 1", dueDateEpochMillis = 1000L)
        )
        taskDao.softDeleteTask(id = taskId, deletedAt = 5000L)

        assertTrue(taskDao.getAllTasksWithCourse().first().isEmpty())
        assertEquals(1, taskDao.observeDeletedTasks().first().size)

        taskDao.restoreTask(id = taskId)

        // Brought back to active queries
        val activeTasks = taskDao.getAllTasksWithCourse().first()
        assertEquals(1, activeTasks.size)
        val restored = activeTasks.first().task
        assertEquals(taskId, restored.id)
        assertEquals(false, restored.isDeleted)
        assertEquals(0L, restored.deletedAt)
        assertEquals(false, restored.isSynced)

        assertNotNull(taskDao.getTaskById(taskId))
        assertEquals(0L, taskDao.getTaskById(taskId)!!.deletedAt)

        // Gone from deleted queries
        assertTrue(taskDao.observeDeletedTasks().first().isEmpty())
        assertTrue(taskDao.getDeletedTasksSync().isEmpty())
    }

    @Test
    fun testPurgeDeletedBeforeRemovesOnlyOldTrashedRowsAndReturnsCount() = runBlocking {
        val courseId = courseDao.insertCourse(Course(name = "Math", code = "MATH101", colorHex = "#123456"))

        // Active task (isDeleted = false, deletedAt = 0)
        val activeId = taskDao.insertTask(
            TaskItem(courseId = courseId, title = "Active", dueDateEpochMillis = 1000L, isDeleted = false, deletedAt = 0L)
        )

        // Old deleted tasks before cutoff 2000L
        val oldDeleted1 = taskDao.insertTask(
            TaskItem(courseId = courseId, title = "Old 1", dueDateEpochMillis = 1000L, isDeleted = true, deletedAt = 500L)
        )
        val oldDeleted2 = taskDao.insertTask(
            TaskItem(courseId = courseId, title = "Old 2", dueDateEpochMillis = 1000L, isDeleted = true, deletedAt = 1500L)
        )

        // Recent deleted task at or after cutoff
        val recentDeleted = taskDao.insertTask(
            TaskItem(courseId = courseId, title = "Recent", dueDateEpochMillis = 1000L, isDeleted = true, deletedAt = 2500L)
        )

        // Deleted task with deletedAt = 0 (must not be purged because deletedAt must be > 0)
        val zeroDeletedAt = taskDao.insertTask(
            TaskItem(courseId = courseId, title = "Zero DeletedAt", dueDateEpochMillis = 1000L, isDeleted = true, deletedAt = 0L)
        )

        val purgedCount = taskDao.purgeDeletedBefore(cutoff = 2000L)
        assertEquals(2, purgedCount)

        // Old deleted tasks are permanently gone
        assertNull(taskDao.getAnyTaskById(oldDeleted1))
        assertNull(taskDao.getAnyTaskById(oldDeleted2))

        // Others survive
        assertNotNull(taskDao.getAnyTaskById(activeId))
        assertNotNull(taskDao.getAnyTaskById(recentDeleted))
        assertNotNull(taskDao.getAnyTaskById(zeroDeletedAt))
    }

    @Test
    fun testObserveDeletedTasksSortsNewestDeletedFirst() = runBlocking {
        val courseId = courseDao.insertCourse(Course(name = "Math", code = "MATH101", colorHex = "#123456"))
        val id1 = taskDao.insertTask(
            TaskItem(courseId = courseId, title = "Task 1", dueDateEpochMillis = 1000L, isDeleted = true, deletedAt = 1000L)
        )
        val id2 = taskDao.insertTask(
            TaskItem(courseId = courseId, title = "Task 2", dueDateEpochMillis = 2000L, isDeleted = true, deletedAt = 3000L)
        )
        val id3 = taskDao.insertTask(
            TaskItem(courseId = courseId, title = "Task 3", dueDateEpochMillis = 3000L, isDeleted = true, deletedAt = 2000L)
        )

        val deleted = taskDao.observeDeletedTasks().first()
        assertEquals(3, deleted.size)
        // Ordered by deletedAt DESC: id2 (3000), id3 (2000), id1 (1000)
        assertEquals(id2, deleted[0].task.id)
        assertEquals(id3, deleted[1].task.id)
        assertEquals(id1, deleted[2].task.id)
    }
}
