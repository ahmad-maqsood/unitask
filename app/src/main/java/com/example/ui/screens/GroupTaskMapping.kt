package com.example.ui.screens

import com.example.data.groups.GroupTaskEntry
import com.example.data.local.Course
import com.example.data.local.Priority
import com.example.data.local.TaskItem
import com.example.data.local.TaskStatus
import com.example.data.local.TaskWithCourse
import com.example.reminder.GroupReminderScheduler
import java.util.UUID

/** Colour of the badge and left strip of group tasks on the Deadlines tab. */
const val GROUP_TASK_COLOR_HEX = "#0D9488"

/**
 * Group tasks are shown as personal tasks without being copied into Room, so they never touch
 * cloud backup. They get negative ids; Room only hands out positive ones, so the two can never clash.
 */
private fun negativeId(key: String): Long =
    UUID.nameUUIDFromBytes(key.toByteArray()).mostSignificantBits or Long.MIN_VALUE

fun groupTaskItemId(groupId: String, taskId: String): Long = negativeId("task|$groupId|$taskId")

fun isGroupTaskItem(task: TaskItem): Boolean = task.id < 0

/**
 * Turns a course code into a comparable key: "CS-503", "cs 503" and "CS503" all become "CS503".
 * Used wherever a group task's course label is compared with a course code.
 */
fun normalizeCourseKey(value: String): String =
    value.filter { it.isLetterOrDigit() }.uppercase()
fun GroupTaskEntry.toTaskWithCourse(): TaskWithCourse {
    val t = task
    val item = TaskItem(
        id = groupTaskItemId(t.groupId, t.id),
        courseId = negativeId("group|${t.groupId}"),
        title = t.title,
        description = t.description.orEmpty(),
        dueDateEpochMillis = t.dueDateEpochMillis,
        dueTimeHour = t.dueTimeHour,
        dueTimeMinute = t.dueTimeMinute,
        priority = try { Priority.valueOf(t.priority) } catch (_: Exception) { Priority.MEDIUM },
        status = if (isDone) TaskStatus.COMPLETED else TaskStatus.PENDING,
        reminderOptions = GroupReminderScheduler.optionsFor(t),
        isSynced = true,
        createdAt = t.createdAt?.toDate()?.time ?: 0L
    )
    val cleanLabel = t.courseLabel?.trim()
    val matchedCourse = if (!cleanLabel.isNullOrBlank()) {
        val labelKey = normalizeCourseKey(cleanLabel)
        groupCourses.find { labelKey.isNotEmpty() && normalizeCourseKey(it.code) == labelKey }
            ?: groupCourses.find { it.name.trim().equals(cleanLabel, ignoreCase = true) }
    } else null

    val courseCode = matchedCourse?.code ?: cleanLabel?.ifBlank { null } ?: groupName
    val courseName = matchedCourse?.name ?: cleanLabel?.ifBlank { null } ?: groupName
    val courseColor = matchedCourse?.colorHex ?: GROUP_TASK_COLOR_HEX

    val course = Course(
        id = item.courseId,
        name = courseName,
        code = courseCode,
        instructor = matchedCourse?.instructor.orEmpty(),
        colorHex = courseColor,
        isSynced = true
    )
    return TaskWithCourse(task = item, course = course, attachments = emptyList())
}
