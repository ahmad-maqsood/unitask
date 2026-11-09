package com.example.ui.screens

import com.example.data.local.TaskWithCourse
import com.example.data.local.isMyTasksCourse
import com.example.util.AppFeature

/**
 * Target actions when a search result row is tapped.
 */
sealed class SearchNavigationTarget {
    /** Opens task edit dialog for a personal or My tasks task. */
    data class EditPersonalTask(val taskWithCourse: TaskWithCourse, val isMyTask: Boolean) : SearchNavigationTarget()

    /** Navigates to Deadlines tab with Deadlines search query set to the group task title. */
    data class DeadlinesGroupTask(val taskTitle: String) : SearchNavigationTarget()

    /** Opens a note in the note editor. */
    data class OpenNote(val noteId: Long) : SearchNavigationTarget()

    /** Opens a course detail sheet. */
    data class OpenCourse(val courseId: Long) : SearchNavigationTarget()

    /** Opens the Settings sheet on the page that holds the setting, or the sign in sheet for the account row. */
    data class OpenSetting(val page: SettingsPage?, val opensAccount: Boolean) : SearchNavigationTarget()

    /** Opens "Learn to use the app" on the guide with this title. */
    data class OpenGuide(val featureTitle: String) : SearchNavigationTarget()

    /** Opens an alert dialog notifying that the item belongs to an archived semester. */
    data class ShowArchivedDialog(val semesterName: String) : SearchNavigationTarget()
}

/**
 * Pure function determining the navigation action when a task search result is clicked.
 */
fun resolveTaskClickTarget(result: SearchTaskResult): SearchNavigationTarget {
    if (result.isArchived) {
        return SearchNavigationTarget.ShowArchivedDialog(result.archivedSemester ?: "")
    }
    val task = result.task
    if (isGroupTaskItem(task)) {
        return SearchNavigationTarget.DeadlinesGroupTask(task.title)
    }
    val isMyTask = result.course?.isMyTasksCourse() == true
    return SearchNavigationTarget.EditPersonalTask(result.taskWithCourse, isMyTask)
}

/**
 * Pure function determining the navigation action when a note search result is clicked.
 */
fun resolveNoteClickTarget(result: SearchNoteResult): SearchNavigationTarget {
    if (result.isArchived) {
        return SearchNavigationTarget.ShowArchivedDialog(result.archivedSemester ?: "")
    }
    return SearchNavigationTarget.OpenNote(result.noteItem.note.id)
}

/**
 * Pure function determining the navigation action when a course search result is clicked.
 */
fun resolveCourseClickTarget(result: SearchCourseResult): SearchNavigationTarget {
    if (result.isArchived) {
        return SearchNavigationTarget.ShowArchivedDialog(result.archivedSemester ?: "")
    }
    return SearchNavigationTarget.OpenCourse(result.course.id)
}

/**
 * Pure function determining the navigation action when a settings search result is clicked.
 */
fun resolveSettingClickTarget(entry: SettingsSearchEntry): SearchNavigationTarget {
    return SearchNavigationTarget.OpenSetting(entry.page, entry.opensAccount)
}

/**
 * Pure function determining the navigation action when a guide search result is clicked.
 */
fun resolveGuideClickTarget(feature: AppFeature): SearchNavigationTarget {
    return SearchNavigationTarget.OpenGuide(feature.title)
}

/**
 * Pure function returning the list of items visible in a section.
 * Shows at most [limit] items unless [isExpanded] is true.
 */
fun <T> visibleSectionItems(
    items: List<T>,
    isExpanded: Boolean,
    limit: Int = 5
): List<T> {
    return if (isExpanded || items.size <= limit) {
        items
    } else {
        items.take(limit)
    }
}

/**
 * Pure function determining whether the "Show all n" button should be shown.
 */
fun shouldShowExpandButton(
    totalCount: Int,
    isExpanded: Boolean,
    limit: Int = 5
): Boolean {
    return totalCount > limit && !isExpanded
}

/**
 * Returns the label for the expand button of a section.
 */
fun formatShowAllButtonText(totalCount: Int): String = "Show all $totalCount"
