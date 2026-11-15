package com.example.reminder

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.Course
import com.example.data.local.TaskItem
import com.example.data.local.TaskStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A snoozed reminder comes back later, and a task may have been finished, deleted or archived in
 * the meantime. ReminderSnooze.isStillNeeded decides whether the notification is still shown.
 * These tests cover the personal task rules (groups need the server, so they are not covered here).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReminderStillNeededTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = AppDatabase.getDatabase(context)
        runBlocking { db.clearAllTables() }
    }

    @After
    fun tearDown() {
        db.close()
        AppDatabase::class.java.getDeclaredField("INSTANCE")
            .apply { isAccessible = true }
            .set(null, null)
    }

    private fun addCourse(archivedSemester: String = ""): Long = runBlocking {
        db.courseDao().insertCourse(Course(name = "Maths", code = "M101", archivedSemester = archivedSemester))
    }

    private fun addTask(courseId: Long, status: TaskStatus = TaskStatus.PENDING): Long = runBlocking {
        db.taskDao().insertTask(
            TaskItem(courseId = courseId, title = "Essay", dueDateEpochMillis = 1_800_000_000_000L, status = status)
        )
    }

    private fun stillNeeded(taskId: Long): Boolean = runBlocking {
        ReminderSnooze.isStillNeeded(context, taskId, groupId = null, groupTaskId = null)
    }

    @Test
    fun anOpenTaskIsStillNeeded() {
        val id = addTask(addCourse())
        assertTrue(stillNeeded(id))
    }

    @Test
    fun aFinishedTaskIsNotNeeded() {
        val id = addTask(addCourse(), TaskStatus.COMPLETED)
        assertFalse(stillNeeded(id))
    }

    @Test
    fun aTaskThatWasMarkedDoneAfterwardsIsNotNeeded() {
        val id = addTask(addCourse())
        runBlocking { db.taskDao().updateStatusAndCompletedAt(id, TaskStatus.COMPLETED, 1L) }
        assertFalse(stillNeeded(id))
    }

    @Test
    fun aTaskInRecentlyDeletedIsNotNeeded() {
        val id = addTask(addCourse())
        runBlocking { db.taskDao().softDeleteTask(id, deletedAt = 1L) }
        assertFalse(stillNeeded(id))
    }

    @Test
    fun aTaskThatNoLongerExistsIsNotNeeded() {
        assertFalse(stillNeeded(9_999L))
    }

    @Test
    fun aTaskOfAnArchivedCourseIsNotNeeded() {
        val id = addTask(addCourse(archivedSemester = "Spring 2026"))
        assertFalse(stillNeeded(id))
    }

    @Test
    fun aTurnedOffTaskIsNotNeeded() {
        val id = addTask(addCourse())
        runBlocking { db.taskDao().updateEnabled(id, enabled = false, reminderEpochMillis = null) }
        assertFalse(stillNeeded(id))
    }

    @Test
    fun aTaskThatIsTurnedBackOnIsNeededAgain() {
        val id = addTask(addCourse())
        runBlocking {
            db.taskDao().updateEnabled(id, enabled = false, reminderEpochMillis = null)
            db.taskDao().updateEnabled(id, enabled = true, reminderEpochMillis = null)
        }
        assertTrue(stillNeeded(id))
    }

    @Test
    fun withoutATaskOrGroupTheReminderIsShown() {
        // Nothing to check against, so the reminder is never lost.
        assertTrue(stillNeeded(0L))
    }
}
