package com.example.ui.screens

import java.util.Calendar
import com.example.data.local.TaskStatus
import com.example.data.local.TaskWithCourse
import com.example.data.local.deadlineMillis

internal data class DeadlineSections(
    val overdue: List<TaskWithCourse>,
    val today: List<TaskWithCourse>,
    val tomorrow: List<TaskWithCourse>,
    val later: List<TaskWithCourse>,
    val done: List<TaskWithCourse>
)

internal fun groupDeadlines(tasks: List<TaskWithCourse>, nowMillis: Long = System.currentTimeMillis()): DeadlineSections {
    val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val currentYear = cal.get(Calendar.YEAR)
    val currentDayOfYear = cal.get(Calendar.DAY_OF_YEAR)

    cal.add(Calendar.DAY_OF_YEAR, 1)
    val tomorrowYear = cal.get(Calendar.YEAR)
    val tomorrowDayOfYear = cal.get(Calendar.DAY_OF_YEAR)

    val overdue = mutableListOf<Pair<TaskWithCourse, Long>>()
    val today = mutableListOf<Pair<TaskWithCourse, Long>>()
    val tomorrow = mutableListOf<Pair<TaskWithCourse, Long>>()
    val later = mutableListOf<Pair<TaskWithCourse, Long>>()
    val done = mutableListOf<Pair<TaskWithCourse, Long>>()

    for (item in tasks) {
        val task = item.task
        val deadline = task.deadlineMillis
        val paired = item to deadline

        if (task.status == TaskStatus.COMPLETED) {
            done.add(paired)
            continue
        }

        if (deadline < nowMillis) {
            overdue.add(paired)
        } else {
            cal.timeInMillis = task.dueDateEpochMillis
            val taskYear = cal.get(Calendar.YEAR)
            val taskDayOfYear = cal.get(Calendar.DAY_OF_YEAR)

            if (taskYear == currentYear && taskDayOfYear == currentDayOfYear) {
                today.add(paired)
            } else if (taskYear == tomorrowYear && taskDayOfYear == tomorrowDayOfYear) {
                tomorrow.add(paired)
            } else {
                later.add(paired)
            }
        }
    }

    return DeadlineSections(
        overdue = overdue.sortedBy { it.second }.map { it.first },
        today = today.sortedBy { it.second }.map { it.first },
        tomorrow = tomorrow.sortedBy { it.second }.map { it.first },
        later = later.sortedBy { it.second }.map { it.first },
        done = done.sortedByDescending { it.second }.map { it.first }
    )
}
