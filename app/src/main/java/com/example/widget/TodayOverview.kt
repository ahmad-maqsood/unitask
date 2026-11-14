package com.example.widget

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.TaskStatus
import com.example.data.local.computeDeadlineMillis
import com.example.data.local.isMyTasksCourse
import com.example.data.local.startOfLocalDay
import com.example.reminder.GroupTaskProcessor
import com.example.util.TimeFormatter
import com.example.util.isSlotActive
import com.example.util.DateFormats
import java.util.Calendar

enum class ClassState { PAST, NOW, UPCOMING }

data class WidgetClass(
    val timeText: String,
    val code: String,
    val place: String,
    val state: ClassState
)

data class WidgetTask(
    val timeText: String,
    val label: String,
    val title: String,
    /** Id of a personal task (used to open the right tab), or null for a group task. */
    val personalTaskId: Long?,
    val isMyTask: Boolean
)

data class TodayOverview(
    val dateText: String,
    val classes: List<WidgetClass>,
    val tasks: List<WidgetTask>,
    val overdueCount: Int
)

/** Reads today's classes and tasks from the phone. Same rules as the daily summary notification. */
object TodayOverviewLoader {

    private data class DueItem(
        val deadlineMillis: Long,
        val label: String,
        val title: String,
        val personalTaskId: Long?,
        val isMyTask: Boolean
    )

    suspend fun load(context: Context, nowMillis: Long = System.currentTimeMillis()): TodayOverview {
        val appContext = context.applicationContext
        val db = AppDatabase.getDatabase(appContext)
        val courses = db.courseDao().getAllCoursesList().associateBy { it.id }

        val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        // 1 = Monday ... 7 = Sunday, same as the timetable.
        val today = (nowCal.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1
        val nowMinutes = nowCal.get(Calendar.HOUR_OF_DAY) * 60 + nowCal.get(Calendar.MINUTE)

        val classes = db.lectureSlotDao().getAllSlotsSync()
            .filter {
                !it.isDeleted &&
                    it.courseId in courses &&
                    it.dayOfWeek == today &&
                    isSlotActive(appContext, it, nowMillis)
            }
            .sortedBy { it.startHour * 60 + it.startMinute }
            .map { slot ->
                val start = slot.startHour * 60 + slot.startMinute
                val end = slot.endHour * 60 + slot.endMinute
                val course = courses[slot.courseId]
                WidgetClass(
                    timeText = TimeFormatter.format(slot.startHour, slot.startMinute),
                    code = course?.code.orEmpty(),
                    place = slot.classroom.ifBlank { course?.location.orEmpty() },
                    state = when {
                        nowMinutes >= end -> ClassState.PAST
                        nowMinutes >= start -> ClassState.NOW
                        else -> ClassState.UPCOMING
                    }
                )
            }

        val startOfToday = startOfLocalDay(nowMillis)
        val startOfTomorrow = Calendar.getInstance().apply {
            timeInMillis = startOfToday
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis

        val personal = db.taskDao().getAllActiveTasksSync()
            .filter { it.courseId in courses && it.status != TaskStatus.COMPLETED }
            .map {
                val course = courses[it.courseId]
                val isMy = course?.isMyTasksCourse() == true
                DueItem(
                    deadlineMillis = computeDeadlineMillis(it.dueDateEpochMillis, it.dueTimeHour, it.dueTimeMinute),
                    label = if (isMy) "My task" else course?.code.orEmpty(),
                    title = it.title,
                    personalTaskId = it.id,
                    isMyTask = isMy
                )
            }
        // Group tasks that show on your Deadlines tab and that you have not marked done.
        val group = GroupTaskProcessor.pendingSummaryTasks(appContext).map {
            DueItem(it.deadlineMillis, it.label.ifBlank { it.groupName }, it.title, null, false)
        }
        val pending = personal + group

        val dueToday = pending
            .filter { it.deadlineMillis >= startOfToday && it.deadlineMillis < startOfTomorrow }
            .sortedBy { it.deadlineMillis }
            .map { item ->
                val cal = Calendar.getInstance().apply { timeInMillis = item.deadlineMillis }
                WidgetTask(
                    timeText = TimeFormatter.format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)),
                    label = item.label,
                    title = item.title,
                    personalTaskId = item.personalTaskId,
                    isMyTask = item.isMyTask
                )
            }
        val overdue = pending.count { it.deadlineMillis < startOfToday }

        return TodayOverview(
            dateText = DateFormats.dayFirstDate(nowMillis),
            classes = classes,
            tasks = dueToday,
            overdueCount = overdue
        )
    }
}

