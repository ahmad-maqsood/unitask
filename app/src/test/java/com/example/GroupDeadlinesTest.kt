package com.example

import com.example.data.local.Course
import com.example.data.local.TaskItem
import com.example.data.local.TaskStatus
import com.example.data.local.TaskWithCourse
import com.example.ui.screens.groupDeadlines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class GroupDeadlinesTest {

    private fun createCalendar(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 0,
        minute: Int = 0
    ): Calendar = Calendar.getInstance().apply {
        set(year, month, day, hour, minute, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun createTask(
        id: Long,
        title: String,
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 12,
        minute: Int = 0,
        status: TaskStatus = TaskStatus.PENDING
    ): TaskWithCourse {
        val dateCal = createCalendar(year, month, day, 0, 0)
        return TaskWithCourse(
            task = TaskItem(
                id = id,
                courseId = 1L,
                title = title,
                dueDateEpochMillis = dateCal.timeInMillis,
                dueTimeHour = hour,
                dueTimeMinute = minute,
                status = status
            ),
            course = Course(id = 1L, code = "CS101", name = "Intro to CS")
        )
    }

    @Test
    fun testGroupDeadlinesSectionsAndSorting() {
        // Fixed nowMillis: October 28, 2026 at 14:00 (2:00 PM)
        val nowCal = createCalendar(2026, Calendar.OCTOBER, 28, 14, 0)
        val nowMillis = nowCal.timeInMillis

        // 1. Overdue: Oct 27 18:00 (yesterday) and Oct 28 10:00 (earlier today)
        val overdueYesterday = createTask(1L, "Overdue Yesterday", 2026, Calendar.OCTOBER, 27, 18, 0)
        val overdueEarlierToday = createTask(2L, "Overdue Earlier Today", 2026, Calendar.OCTOBER, 28, 10, 0)

        // 2. Today: Oct 28 16:00 and Oct 28 20:00
        val todayEarly = createTask(3L, "Today 4PM", 2026, Calendar.OCTOBER, 28, 16, 0)
        val todayLate = createTask(4L, "Today 8PM", 2026, Calendar.OCTOBER, 28, 20, 0)

        // 3. Tomorrow: Oct 29 09:00 and Oct 29 17:00
        val tomorrowMorning = createTask(5L, "Tomorrow 9AM", 2026, Calendar.OCTOBER, 29, 9, 0)
        val tomorrowEvening = createTask(6L, "Tomorrow 5PM", 2026, Calendar.OCTOBER, 29, 17, 0)

        // 4. Later: Oct 31 12:00 and Nov 05 10:00
        val laterHalloween = createTask(7L, "Later Oct 31", 2026, Calendar.OCTOBER, 31, 12, 0)
        val laterNovember = createTask(8L, "Later Nov 5", 2026, Calendar.NOVEMBER, 5, 10, 0)

        // 5. Done: completed tasks (one earlier, one later)
        val doneEarlier = createTask(9L, "Done Earlier", 2026, Calendar.OCTOBER, 20, 12, 0, status = TaskStatus.COMPLETED)
        val doneLater = createTask(10L, "Done Later", 2026, Calendar.OCTOBER, 30, 12, 0, status = TaskStatus.COMPLETED)

        // Pass items in mixed order to verify sorting
        val allTasks = listOf(
            laterNovember,
            todayLate,
            doneEarlier,
            overdueEarlierToday,
            tomorrowEvening,
            todayEarly,
            doneLater,
            overdueYesterday,
            laterHalloween,
            tomorrowMorning
        )

        val sections = groupDeadlines(allTasks, nowMillis)

        // Check overdue (sorted ascending by deadline)
        assertEquals(2, sections.overdue.size)
        assertEquals(listOf("Overdue Yesterday", "Overdue Earlier Today"), sections.overdue.map { it.task.title })

        // Check today (sorted ascending by deadline)
        assertEquals(2, sections.today.size)
        assertEquals(listOf("Today 4PM", "Today 8PM"), sections.today.map { it.task.title })

        // Check tomorrow (sorted ascending by deadline)
        assertEquals(2, sections.tomorrow.size)
        assertEquals(listOf("Tomorrow 9AM", "Tomorrow 5PM"), sections.tomorrow.map { it.task.title })

        // Check later (sorted ascending by deadline)
        assertEquals(2, sections.later.size)
        assertEquals(listOf("Later Oct 31", "Later Nov 5"), sections.later.map { it.task.title })

        // Check done (sorted descending by deadline)
        assertEquals(2, sections.done.size)
        assertEquals(listOf("Done Later", "Done Earlier"), sections.done.map { it.task.title })
    }

    @Test
    fun testEmptyTaskListReturnsEmptySections() {
        val nowMillis = createCalendar(2026, Calendar.OCTOBER, 28, 12, 0).timeInMillis
        val sections = groupDeadlines(emptyList(), nowMillis)

        assertTrue(sections.overdue.isEmpty())
        assertTrue(sections.today.isEmpty())
        assertTrue(sections.tomorrow.isEmpty())
        assertTrue(sections.later.isEmpty())
        assertTrue(sections.done.isEmpty())
    }

    @Test
    fun testCompletedTaskWithPastDeadlineGoesToDone() {
        val nowMillis = createCalendar(2026, Calendar.OCTOBER, 28, 12, 0).timeInMillis
        val pastCompletedTask = createTask(1L, "Finished Task", 2026, Calendar.OCTOBER, 25, 10, 0, status = TaskStatus.COMPLETED)

        val sections = groupDeadlines(listOf(pastCompletedTask), nowMillis)

        assertTrue(sections.overdue.isEmpty())
        assertEquals(1, sections.done.size)
        assertEquals("Finished Task", sections.done.first().task.title)
    }
}

