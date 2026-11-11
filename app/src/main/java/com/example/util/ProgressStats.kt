package com.example.util

import com.example.data.local.TaskStatus
import com.example.data.local.TaskWithCourse
import java.util.Calendar
import java.util.TimeZone

/** Tasks finished in one week. [weekStartMillis] is the first moment of the week. */
data class WeekBar(val weekStartMillis: Long, val done: Int)

/** How many of a course's tasks are done. */
data class CourseProgress(val name: String, val colorHex: String, val done: Int, val total: Int) {
    val fraction: Float get() = if (total > 0) done.toFloat() / total else 0f
}

/** Everything the Progress screen shows. */
data class ProgressSummary(
    val doneThisWeek: Int,
    val doneLastWeek: Int,
    /** Days in a row, up to today, with at least one task finished. */
    val streakDays: Int,
    /** Oldest week first, the current week last. */
    val weeks: List<WeekBar>,
    val courses: List<CourseProgress>,
    val totalDone: Int,
    val totalTasks: Int
)

object ProgressLogic {

    /**
     * Works out the Progress numbers from the tasks on the phone.
     *
     * Only tasks that were ticked Done with a known time count towards the weekly bars and the
     * streak. A task finished long ago whose time was never saved still counts in its course total.
     */
    fun compute(
        tasks: List<TaskWithCourse>,
        now: Long,
        weekCount: Int = 8,
        firstDayOfWeek: Int = Calendar.getInstance().firstDayOfWeek,
        zone: TimeZone = TimeZone.getDefault()
    ): ProgressSummary {
        val weeksShown = weekCount.coerceAtLeast(1)

        fun dayStart(millis: Long): Calendar = Calendar.getInstance(zone).apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        fun weekStart(millis: Long): Long {
            val c = dayStart(millis)
            val offset = (c.get(Calendar.DAY_OF_WEEK) - firstDayOfWeek + 7) % 7
            c.add(Calendar.DAY_OF_YEAR, -offset)
            return c.timeInMillis
        }

        val doneTimes = tasks
            .map { it.task }
            .filter { it.status == TaskStatus.COMPLETED && it.completedAtMillis > 0L }
            .map { it.completedAtMillis }

        val perWeek = doneTimes.groupingBy { weekStart(it) }.eachCount()
        val thisWeekStart = weekStart(now)
        val weeks = (weeksShown - 1 downTo 0).map { weeksAgo ->
            val start = Calendar.getInstance(zone).apply {
                timeInMillis = thisWeekStart
                add(Calendar.WEEK_OF_YEAR, -weeksAgo)
            }.timeInMillis
            WeekBar(start, perWeek[start] ?: 0)
        }

        // Streak: today counts if something was finished today; if not, the streak can still be
        // alive from yesterday, so it does not drop to 0 first thing in the morning.
        val days = doneTimes.map { dayStart(it).timeInMillis }.toSet()
        val cursor = dayStart(now)
        if (cursor.timeInMillis !in days) cursor.add(Calendar.DAY_OF_YEAR, -1)
        var streak = 0
        while (cursor.timeInMillis in days) {
            streak++
            cursor.add(Calendar.DAY_OF_YEAR, -1)
        }

        val courses = tasks
            .mapNotNull { item -> item.course?.let { it to item.task } }
            .groupBy { it.first.id }
            .map { (_, pairs) ->
                val course = pairs.first().first
                CourseProgress(
                    name = course.name,
                    colorHex = course.colorHex,
                    done = pairs.count { it.second.status == TaskStatus.COMPLETED },
                    total = pairs.size
                )
            }
            .sortedByDescending { it.total }

        return ProgressSummary(
            doneThisWeek = weeks.last().done,
            doneLastWeek = if (weeks.size >= 2) weeks[weeks.size - 2].done else 0,
            streakDays = streak,
            weeks = weeks,
            courses = courses,
            totalDone = tasks.count { it.task.status == TaskStatus.COMPLETED },
            totalTasks = tasks.size
        )
    }
}
