package com.example.ui.screens

import com.example.data.local.TaskStatus
import com.example.data.local.TaskWithCourse
import com.example.data.local.deadlineMillis
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.filter
import com.example.data.local.LectureSlotWithCourse
import java.util.Calendar

// Classes that started more than this many minutes ago are hidden from the schedule.
private const val STALE_CLASS_MINUTES = 20

internal fun MainViewModel.buildClassSchedule(
    slots: List<LectureSlotWithCourse>,
    tasks: List<TaskWithCourse>
): List<HighlightedClassInfo> {
    if (slots.isEmpty()) return emptyList()

    val dayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    val cal = Calendar.getInstance()
    val todayDay = when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> 1
        Calendar.TUESDAY -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4
        Calendar.FRIDAY -> 5
        Calendar.SATURDAY -> 6
        Calendar.SUNDAY -> 7
        else -> 1
    }
    val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

    fun startOf(s: LectureSlotWithCourse) = s.slot.startHour * 60 + s.slot.startMinute
    fun endOf(s: LectureSlotWithCourse) = s.slot.endHour * 60 + s.slot.endMinute
    // Your own tasks of the course, plus pending group tasks tagged with the same course
    // (matched by course code or exact course name).
    fun pendingFor(s: LectureSlotWithCourse): List<TaskWithCourse> {
        val slotCode = s.course?.code?.let { normalizeCourseKey(it) }.orEmpty()
        val slotName = s.course?.name?.trim().orEmpty()
        return tasks
            .filter { twc ->
                if (twc.task.status == TaskStatus.COMPLETED) return@filter false
                if (twc.task.courseId == s.slot.courseId) return@filter true
                if (!isGroupTaskItem(twc.task)) return@filter false
                val taskCode = twc.course?.code?.let { normalizeCourseKey(it) }.orEmpty()
                val taskName = twc.course?.name?.trim().orEmpty()
                (slotCode.isNotEmpty() && taskCode == slotCode) ||
                    (slotName.isNotEmpty() && taskName.equals(slotName, ignoreCase = true))
            }
            .sortedBy { it.task.deadlineMillis }
    }
    fun slotsOn(day: Int) = slots.filter { it.slot.dayOfWeek == day }.sortedBy { startOf(it) }
    fun startTimeText(s: LectureSlotWithCourse) =
        com.example.util.TimeFormatter.format(s.slot.startHour, s.slot.startMinute)

    val nowMillis = cal.timeInMillis
    val visibleToday = slotsOn(todayDay).filter {
        currentMinutes < endOf(it) &&
            currentMinutes - startOf(it) <= STALE_CLASS_MINUTES &&
            com.example.util.isSlotActive(getApplication(), it.slot, nowMillis)
    }
    if (visibleToday.isNotEmpty()) {
        return visibleToday.map { s ->
            if (currentMinutes >= startOf(s)) {
                HighlightedClassInfo(
                    slotWithCourse = s,
                    isActiveNow = true,
                    timeLabel = "Ends in ${endOf(s) - currentMinutes} min",
                    pendingTasks = pendingFor(s)
                )
            } else {
                val minUntil = startOf(s) - currentMinutes
                HighlightedClassInfo(
                    slotWithCourse = s,
                    isActiveNow = false,
                    timeLabel = if (minUntil < 60) "Starts in $minUntil min" else "Starts at ${startTimeText(s)}",
                    pendingTasks = pendingFor(s)
                )
            }
        }
    }

    // Nothing left today: show the next day that has classes (may be in a later week).
    for (dayOffset in 1..14) {
        val targetCal = Calendar.getInstance().apply {
            timeInMillis = cal.timeInMillis
            add(Calendar.DAY_OF_YEAR, dayOffset)
        }
        val day = when (targetCal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }
        val daySlots = slotsOn(day).filter {
            com.example.util.isSlotActive(getApplication(), it.slot, targetCal.timeInMillis)
        }
        if (daySlots.isNotEmpty()) {
            val prefix = if (dayOffset > 7) "Next " else ""
            return daySlots.map { s ->
                HighlightedClassInfo(
                    slotWithCourse = s,
                    isActiveNow = false,
                    timeLabel = "$prefix${dayNames[day - 1]} at ${startTimeText(s)}",
                    pendingTasks = pendingFor(s)
                )
            }
        }
    }
    return emptyList()
}
