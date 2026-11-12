package com.example.util

import com.example.data.local.Course
import com.example.data.local.TaskItem
import com.example.data.local.TaskStatus
import com.example.data.local.TaskWithCourse
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressLogicTest {

    private val utc = TimeZone.getTimeZone("UTC")

    /** Noon UTC on the given day. */
    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(year, month - 1, day, hour, 0, 0)
        }.timeInMillis

    // Thursday 12 November 2026, with weeks starting on Monday (so this week starts Mon 9 Nov).
    private val now = at(2026, 11, 12)

    private val course = Course(id = 1, name = "Maths", code = "M101", colorHex = "#112233")
    private val other = Course(id = 2, name = "Physics", code = "P101")

    private fun done(completedAt: Long, c: Course = course) = TaskWithCourse(
        task = TaskItem(
            courseId = c.id, title = "t", dueDateEpochMillis = 0L,
            status = TaskStatus.COMPLETED, completedAtMillis = completedAt
        ),
        course = c
    )

    private fun pending(c: Course = course) = TaskWithCourse(
        task = TaskItem(courseId = c.id, title = "p", dueDateEpochMillis = 0L),
        course = c
    )

    private fun compute(
        tasks: List<TaskWithCourse>,
        weeks: Int = 8,
        log: List<CompletionEntry> = emptyList()
    ) = ProgressLogic.compute(tasks, now, weeks, Calendar.MONDAY, utc, log)

    @Test
    fun countsThisWeekAndLastWeek() {
        val result = compute(
            listOf(
                done(at(2026, 11, 9)),   // Mon, this week
                done(at(2026, 11, 12)),  // Thu, this week
                done(at(2026, 11, 8)),   // Sun, last week
                done(at(2026, 11, 2)),   // Mon, last week
                done(at(2026, 10, 20))   // earlier
            )
        )
        assertEquals(2, result.doneThisWeek)
        assertEquals(2, result.doneLastWeek)
    }

    @Test
    fun weeksAreOldestFirstAndTheLastIsThisWeek() {
        val result = compute(listOf(done(at(2026, 11, 10))), weeks = 4)
        assertEquals(4, result.weeks.size)
        assertEquals(at(2026, 11, 9, 0), result.weeks.last().weekStartMillis)
        assertEquals(at(2026, 10, 19, 0), result.weeks.first().weekStartMillis)
        assertEquals(listOf(0, 0, 0, 1), result.weeks.map { it.done })
    }

    @Test
    fun weekStartFollowsTheChosenFirstDay() {
        // With Sunday first, Thursday 12 Nov belongs to the week starting Sunday 8 Nov.
        val result = ProgressLogic.compute(listOf(done(at(2026, 11, 8))), now, 2, Calendar.SUNDAY, utc)
        assertEquals(1, result.doneThisWeek)
        assertEquals(0, result.doneLastWeek)
    }

    @Test
    fun streakCountsDaysInARowEndingToday() {
        val result = compute(listOf(done(at(2026, 11, 12)), done(at(2026, 11, 11)), done(at(2026, 11, 10))))
        assertEquals(3, result.streakDays)
    }

    @Test
    fun streakSurvivesUntilTheEndOfTodayIfYesterdayHadOne() {
        val result = compute(listOf(done(at(2026, 11, 11)), done(at(2026, 11, 10))))
        assertEquals(2, result.streakDays)
    }

    @Test
    fun aMissedDayBreaksTheStreak() {
        val result = compute(listOf(done(at(2026, 11, 12)), done(at(2026, 11, 10))))
        assertEquals(1, result.streakDays)
    }

    @Test
    fun noRecentCompletionMeansNoStreak() {
        assertEquals(0, compute(listOf(done(at(2026, 11, 1)))).streakDays)
        assertEquals(0, compute(emptyList()).streakDays)
    }

    @Test
    fun severalTasksOnOneDayCountAsOneStreakDay() {
        val result = compute(listOf(done(at(2026, 11, 12, 8)), done(at(2026, 11, 12, 20))))
        assertEquals(1, result.streakDays)
        assertEquals(2, result.doneThisWeek)
    }

    @Test
    fun doneTaskWithoutAKnownTimeIsOnlyInTheCourseTotals() {
        val result = compute(listOf(done(completedAt = 0L), pending()))
        assertEquals(0, result.doneThisWeek)
        assertEquals(0, result.streakDays)
        assertEquals(1, result.totalDone)
        assertEquals(2, result.totalTasks)
        assertEquals(1, result.courses.single().done)
        assertEquals(2, result.courses.single().total)
    }

    @Test
    fun coursesShowDoneOutOfTotalBiggestFirst() {
        val result = compute(
            listOf(
                done(at(2026, 11, 12), course), pending(course), pending(course),
                done(at(2026, 11, 12), other)
            )
        )
        assertEquals(listOf("Maths", "Physics"), result.courses.map { it.name })
        assertEquals(1, result.courses[0].done)
        assertEquals(3, result.courses[0].total)
        assertEquals(1f, result.courses[1].fraction, 0.0001f)
    }

    @Test
    fun noTasksGivesAllZeros() {
        val result = compute(emptyList())
        assertEquals(0, result.totalTasks)
        assertEquals(0, result.doneThisWeek)
        assertEquals(8, result.weeks.size)
    }

    private val myTasks = Course(id = 3, name = "My tasks", code = "MYTASKS")

    @Test
    fun myTasksIsNotShownAsACourseButStillCounts() {
        val result = compute(listOf(done(at(2026, 11, 12), myTasks), done(at(2026, 11, 12), course)))
        assertEquals(listOf("Maths"), result.courses.map { it.name })
        assertEquals(2, result.doneThisWeek)
        assertEquals(2, result.totalDone)
    }

    @Test
    fun savedHistoryKeepsTicksOfResetRepeatingTasks() {
        // The repeating task is Pending again today, but it was ticked on the 10th and the 11th.
        val log = listOf(CompletionEntry(5L, at(2026, 11, 10)), CompletionEntry(5L, at(2026, 11, 11)))
        val result = compute(listOf(pending()), log = log)
        assertEquals(2, result.streakDays)
        assertEquals(2, result.doneThisWeek)
    }

    @Test
    fun theSameTickInTasksAndHistoryCountsOnce() {
        val time = at(2026, 11, 12)
        val task = TaskWithCourse(
            task = TaskItem(
                id = 7L, courseId = course.id, title = "t", dueDateEpochMillis = 0L,
                status = TaskStatus.COMPLETED, completedAtMillis = time
            ),
            course = course
        )
        val result = compute(listOf(task), log = listOf(CompletionEntry(7L, time)))
        assertEquals(1, result.doneThisWeek)
    }

    @Test
    fun twoDifferentTasksDoneAtTheSameMomentBothCount() {
        // Regression: ticks were matched by time only, so two tasks finished at the same
        // moment (for example ticked together) were counted as one.
        val time = at(2026, 11, 12)
        val result = compute(listOf(done(time), done(time, other)))
        assertEquals(2, result.doneThisWeek)
        assertEquals(2, result.weeks.last().done)
    }

    @Test
    fun historyTickMatchingOneOfTwoSimultaneousTasksIsNotCountedAgain() {
        val time = at(2026, 11, 12)
        val earlier = at(2026, 11, 10)
        val log = listOf(CompletionEntry(1L, time), CompletionEntry(2L, earlier))
        // Two tasks are Done at `time`. The history repeats that tick and adds one older tick.
        val result = compute(listOf(done(time), done(time, other)), log = log)
        assertEquals(3, result.doneThisWeek)
    }

    @Test
    fun aRestoredTickWithNoKnownTaskIsNotCountedTwice() {
        // After a restore on another phone the history has the tick as task 0 while the task itself
        // has a new number. Same time, so it is one tick.
        val time = at(2026, 11, 12)
        val task = TaskWithCourse(
            task = TaskItem(
                id = 42L, courseId = course.id, title = "t", dueDateEpochMillis = 0L,
                status = TaskStatus.COMPLETED, completedAtMillis = time
            ),
            course = course
        )
        val result = compute(listOf(task), log = listOf(CompletionEntry(0L, time)))
        assertEquals(1, result.doneThisWeek)
        assertEquals(1, result.streakDays)
    }
}
