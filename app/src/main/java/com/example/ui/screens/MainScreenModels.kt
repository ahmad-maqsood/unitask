package com.example.ui.screens

import androidx.annotation.StringRes
import com.example.R

sealed interface MarkdownViewerState {
    object Loading : MarkdownViewerState
    data class Content(val displayName: String, val text: String) : MarkdownViewerState
    data class Error(val message: String) : MarkdownViewerState
}

enum class MainTab {
    DEADLINES,
    TIMETABLE,
    COURSES,
    GROUPS,
    MYTASKS
}

/** Deadlines filter: only group tasks, or only your own tasks. null = both. */
enum class TaskSourceFilter { GROUP, PERSONAL }
enum class TaskSortOption(val label: String, @StringRes val labelRes: Int) {
    DUE_DATE_ASC("Due Date (Earliest)", R.string.sort_due_asc),
    DUE_DATE_DESC("Due Date (Latest)", R.string.sort_due_desc),
    ALARM_FIRST("Alarm tasks first", R.string.sort_alarm_first),
    TITLE_ASC("Title (A-Z)", R.string.sort_title_asc)
}
