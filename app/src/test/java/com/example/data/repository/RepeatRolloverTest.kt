package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.Course
import com.example.data.local.TaskItem
import com.example.data.local.TaskStatus
import com.example.data.local.deadlineMillis
import com.example.data.local.startOfLocalDay
import com.example.reminder.ReminderScheduler
import kotlinx.coroutines.runBlocking
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
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RepeatRolloverTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: AppRepository
    private var courseId = 0L

    private val hour = 60 * 60 * 1000L
    private val day = 24 * hour

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
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

    private suspend fun addTask(
        rule: String,
        dueMillis: Long,
        status: TaskStatus,
        completedAt: Long
    ): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = dueMillis }
        return db.taskDao().insertTask(
            TaskItem(
                courseId = courseId,
                title = "Repeating task",
                dueDateEpochMillis = dueMillis,
                dueTimeHour = cal.get(Calendar.HOUR_OF_DAY),
                dueTimeMinute = cal.get(Calendar.MINUTE),
                status = status,
                repeatRule = rule,
                completedAtMillis = completedAt
            )
        )
    }

    @Test
    fun doneYesterday_movesToFutureDate_andBecomesPending() = runBlocking {
        val now = System.currentTimeMillis()
        val startOfToday = startOfLocalDay(now)
        val id = addTask("DAILY", startOfToday - 12 * hour, TaskStatus.COMPLETED, startOfToday - hour)

        assertTrue(repo.rolloverCompletedRepeatingTasks())

        val task = db.taskDao().getTaskById(id)
        assertNotNull(task)
        assertEquals(TaskStatus.PENDING, task!!.status)
        assertEquals(0L, task.completedAtMillis)
        assertTrue(task.deadlineMillis > now)
    }

    @Test
    fun doneToday_staysDone() = runBlocking {
        val now = System.currentTimeMillis()
        val id = addTask("DAILY", now, TaskStatus.COMPLETED, now)

        assertFalse(repo.rolloverCompletedRepeatingTasks())

        assertEquals(TaskStatus.COMPLETED, db.taskDao().getTaskById(id)!!.status)
    }

    @Test
    fun doneWithUnknownTime_isStampedNow_andStaysDone() = runBlocking {
        val now = System.currentTimeMillis()
        val id = addTask("WEEKLY", now, TaskStatus.COMPLETED, 0L)

        assertFalse(repo.rolloverCompletedRepeatingTasks())

        val task = db.taskDao().getTaskById(id)!!
        assertEquals(TaskStatus.COMPLETED, task.status)
        assertTrue(task.completedAtMillis > 0L)
    }

    @Test
    fun nonRepeatingTask_isNeverMoved() = runBlocking {
        val now = System.currentTimeMillis()
        val startOfToday = startOfLocalDay(now)
        val due = startOfToday - 12 * hour
        val id = addTask("NONE", due, TaskStatus.COMPLETED, startOfToday - hour)

        assertFalse(repo.rolloverCompletedRepeatingTasks())

        val task = db.taskDao().getTaskById(id)!!
        assertEquals(TaskStatus.COMPLETED, task.status)
        assertEquals(due, task.dueDateEpochMillis)
    }

    @Test
    fun missedDailyTask_threeDaysLate_jumpsToFutureDate() = runBlocking {
        val now = System.currentTimeMillis()
        val id = addTask("DAILY", now - 3 * day, TaskStatus.PENDING, 0L)

        assertTrue(repo.rolloverCompletedRepeatingTasks())

        val task = db.taskDao().getTaskById(id)!!
        assertEquals(TaskStatus.PENDING, task.status)
        assertTrue(task.deadlineMillis > now)
    }

    @Test
    fun missedDailyTask_onlyOneHourLate_staysOverdue() = runBlocking {
        val now = System.currentTimeMillis()
        val due = now - hour
        val id = addTask("DAILY", due, TaskStatus.PENDING, 0L)

        assertFalse(repo.rolloverCompletedRepeatingTasks())

        assertEquals(due, db.taskDao().getTaskById(id)!!.dueDateEpochMillis)
    }

    @Test
    fun rollover_unticksChecklistItems() = runBlocking {
        val now = System.currentTimeMillis()
        val startOfToday = startOfLocalDay(now)
        val id = addTask("DAILY", startOfToday - 12 * hour, TaskStatus.COMPLETED, startOfToday - hour)
        val task = db.taskDao().getTaskById(id)!!
        db.taskDao().updateTask(task.copy(description = "Notes\n- [x] Mulk\n- [x] Assajdah"))

        assertTrue(repo.rolloverCompletedRepeatingTasks())

        assertEquals(
            "Notes\n- [ ] Mulk\n- [ ] Assajdah",
            db.taskDao().getTaskById(id)!!.description
        )
    }
}
