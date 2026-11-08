package com.example.util

import com.example.data.local.AppDatabase
import com.example.data.local.Course
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Summary of items that will be permanently lost if a course is deleted.
 */
data class CourseDeleteLossSummary(
    val taskCount: Int,
    val classCount: Int,
    val attachmentCount: Int
)

/**
 * Formats the warning explaining what items will be permanently removed when deleting a course.
 * For example: "This also permanently deletes 12 tasks, 3 classes and their attachments."
 * If no tasks, classes or attachments exist, returns an empty string.
 */
fun formatCourseDeleteWarning(taskCount: Int, classCount: Int, attachmentCount: Int): String {
    if (taskCount <= 0 && classCount <= 0 && attachmentCount <= 0) {
        return ""
    }
    val taskStr = if (taskCount == 1) "1 task" else "$taskCount tasks"
    val classStr = if (classCount == 1) "1 class" else "$classCount classes"

    return when {
        taskCount > 0 && classCount > 0 && attachmentCount > 0 ->
            "This also permanently deletes $taskStr, $classStr and their attachments."
        taskCount > 0 && classCount > 0 ->
            "This also permanently deletes $taskStr and $classStr."
        taskCount > 0 && attachmentCount > 0 ->
            "This also permanently deletes $taskStr and their attachments."
        taskCount > 0 ->
            "This also permanently deletes $taskStr."
        classCount > 0 ->
            "This also permanently deletes $classStr."
        attachmentCount > 0 ->
            "This also permanently deletes their attachments."
        else -> ""
    }
}

/**
 * Queries the database for all items belonging to a course (and any duplicate rows sharing
 * its normalized code) that will be lost: tasks (including trashed tasks), timetable slots,
 * and attachment files.
 */
suspend fun queryCourseDeleteLoss(database: AppDatabase, course: Course): CourseDeleteLossSummary =
    withContext(Dispatchers.IO) {
        val normCode = normalizeCourseCode(course.code)
        val matchingCourses = database.courseDao().getAllCoursesMatchingNormalizedCode(normCode)
        val allIds = (matchingCourses.map { it.id } + course.id).distinct()

        var tasks = 0
        var classes = 0
        var attachments = 0
        for (cId in allIds) {
            tasks += database.taskDao().countTasksForCourse(cId)
            classes += database.lectureSlotDao().countSlotsForCourse(cId)
            attachments += database.taskAttachmentDao().countAttachmentsForCourse(cId)
        }
        CourseDeleteLossSummary(
            taskCount = tasks,
            classCount = classes,
            attachmentCount = attachments
        )
    }
