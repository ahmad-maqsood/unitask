package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.groups.GroupTask
import com.example.data.local.AppDatabase
import com.example.data.local.Course
import com.example.data.local.TaskItem
import com.example.data.local.TaskStatus
import com.example.reminder.GroupReminderScheduler
import com.example.reminder.ReminderScheduler
import com.example.reminder.SnoozeStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SnoozeCleanupTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repo: AppRepository
    private var courseId = 0L

    private val hour = 60 * 60 * 1000L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = AppRepository(
            context = context,
            courseDao = db.courseDao(),
            taskDao = db.taskDao(),
            taskAttachmentDao = db.taskAttachmentDao(),
            lectureSlotDao = db.lectureSlotDao(),
            reminderScheduler = ReminderScheduler(context)
        )
        courseId = runBlocking {
            db.courseDao().insertCourse(Course(name = "Test", code = "T 100", colorHex = "#4F46E5"))
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun snooze(key: Long, groupId: String = "", groupTaskId: String = "") {
        SnoozeStore.set(
            context,
            SnoozeStore.Entry(
                key = key,
                until = System.currentTimeMillis() + hour,
                taskId = if (key > 0) key else 0L,
                title = "Lab report",
                courseCode = "T 100",
                dueInfo = "Due soon",
                priority = "MEDIUM",
                groupId = groupId,
                groupTaskId = groupTaskId
            )
        )
    }

    private fun savedKeys() = SnoozeStore.entries(context).map { it.key }.toSet()

    private suspend fun addTask(): Long = db.taskDao().insertTask(
        TaskItem(
            courseId = courseId,
            title = "Lab report",
            dueDateEpochMillis = System.currentTimeMillis() + 48 * hour
        )
    )

    private fun groupTask(id: String) = GroupTask(
        id = id,
        groupId = "g1",
        title = "Quiz",
        dueDateEpochMillis = System.currentTimeMillis() + 72 * hour
    )

    @Test
    fun markingATaskDoneClearsItsSnooze() = runBlocking {
        val id = addTask()
        snooze(id)
        snooze(-9L, "g9", "t9")

        repo.updateTaskStatus(id, TaskStatus.COMPLETED)

        assertEquals(setOf(-9L), savedKeys())
    }

    @Test
    fun deletingATaskClearsItsSnooze() = runBlocking {
        val id = addTask()
        snooze(id)
        val task = db.taskDao().getTaskById(id)!!

        repo.deleteTask(task)

        assertEquals(emptySet<Long>(), savedKeys())
    }

    @Test
    fun groupSyncClearsSnoozesOfGroupTasksThatAreGoneOrDone() {
        snooze(-1L, "g1", "open")
        snooze(-2L, "g1", "done")
        snooze(-3L, "g1", "deleted")
        snooze(-4L, "g2", "other")
        snooze(7L)

        GroupReminderScheduler(context).sync(
            "g1",
            "Study group",
            listOf(groupTask("open"), groupTask("done")),
            setOf("done")
        )

        // Kept: the open task, another group, a personal snooze.
        assertEquals(setOf(-1L, -4L, 7L), savedKeys())
    }
}
