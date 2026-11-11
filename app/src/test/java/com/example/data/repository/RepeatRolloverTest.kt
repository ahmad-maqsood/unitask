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
import com.example.util.CompletionEntry
import com.example.util.CompletionLog
import com.example.util.NotificationHistory
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
    fun doneYesterday_isKeptInTheProgressHistory_afterTheReset() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        CompletionLog.clear(context)
        val now = System.currentTimeMillis()
        val startOfToday = startOfLocalDay(now)
        val tickedAt = startOfToday - hour
        val id = addTask("DAILY", startOfToday - 12 * hour, TaskStatus.COMPLETED, tickedAt)

        repo.rolloverCompletedRepeatingTasks()

        assertEquals(0L, db.taskDao().getTaskById(id)!!.completedAtMillis)
        assertEquals(listOf(CompletionEntry(id, tickedAt)), CompletionLog.entries(context))
    }

    @Test
    fun tickingAndUntickingUpdatesTheProgressHistory() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        CompletionLog.clear(context)
        val now = System.currentTimeMillis()
        val id = addTask("NONE", now, TaskStatus.PENDING, 0L)

        repo.updateTaskStatus(id, TaskStatus.COMPLETED)
        assertEquals(listOf(id), CompletionLog.entries(context).map { it.taskId })

        repo.updateTaskStatus(id, TaskStatus.PENDING)
        assertEquals(emptyList<CompletionEntry>(), CompletionLog.entries(context))
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
    fun missedDailyTask_dueEarlierToday_staysOverdueUntilMidnight() = runBlocking {
        val now = System.currentTimeMillis()
        val startOfToday = startOfLocalDay(now)
        // Due at the start of today, so it is late but still today.
        val due = startOfToday
        val id = addTask("DAILY", due, TaskStatus.PENDING, 0L)

        assertFalse(repo.rolloverCompletedRepeatingTasks())

        assertEquals(due, db.taskDao().getTaskById(id)!!.dueDateEpochMillis)
    }

    @Test
    fun missedDailyTask_dueYesterday_isReplacedByTodayOrLater_andLogged() = runBlocking {
        val now = System.currentTimeMillis()
        val startOfToday = startOfLocalDay(now)
        val due = startOfToday - 15 * hour // yesterday 09:00
        val id = addTask("DAILY", due, TaskStatus.PENDING, 0L)

        assertTrue(repo.rolloverCompletedRepeatingTasks())

        val task = db.taskDao().getTaskById(id)!!
        assertEquals(TaskStatus.PENDING, task.status)
        assertTrue(task.deadlineMillis > now)
        // Never skips a whole day: the new date is today or tomorrow, not later.
        assertTrue(task.deadlineMillis <= startOfToday + 2 * day)
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(
            NotificationHistory.items(context).any { it.kind == "Missed task" }
        )
    }

    @Test
    fun missedWeeklyTask_oneDayLate_staysOverdue() = runBlocking {
        val now = System.currentTimeMillis()
        val due = startOfLocalDay(now) - 15 * hour
        val id = addTask("WEEKLY", due, TaskStatus.PENDING, 0L)

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

    @Test
    fun rollover_deletesFinishedOneTimeTask_whenSwitchIsOn() = runBlocking {
        val startOfToday = startOfLocalDay(System.currentTimeMillis())
        val id = addTask("NONE", startOfToday - 12 * hour, TaskStatus.COMPLETED, startOfToday - hour)
        db.taskDao().updateTask(db.taskDao().getTaskById(id)!!.copy(autoDeleteWhenDone = true))

        assertTrue(repo.rolloverCompletedRepeatingTasks())

        assertNull(db.taskDao().getTaskById(id))
    }

    @Test
    fun rollover_keepsFinishedOneTimeTask_whenSwitchIsOff() = runBlocking {
        val startOfToday = startOfLocalDay(System.currentTimeMillis())
        val id = addTask("NONE", startOfToday - 12 * hour, TaskStatus.COMPLETED, startOfToday - hour)

        repo.rolloverCompletedRepeatingTasks()

        assertNotNull(db.taskDao().getTaskById(id))
    }

    @Test
    fun rollover_keepsFinishedOneTimeTask_untilTheDayIsOver() = runBlocking {
        val now = System.currentTimeMillis()
        val id = addTask("NONE", now - hour, TaskStatus.COMPLETED, now)
        db.taskDao().updateTask(db.taskDao().getTaskById(id)!!.copy(autoDeleteWhenDone = true))

        repo.rolloverCompletedRepeatingTasks()

        assertNotNull(db.taskDao().getTaskById(id))
    }
}
