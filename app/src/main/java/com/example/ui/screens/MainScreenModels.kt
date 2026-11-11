package com.example.ui.screens

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
enum class TaskSortOption(val label: String) {
    DUE_DATE_ASC("Due Date (Earliest)"),
    DUE_DATE_DESC("Due Date (Latest)"),
    ALARM_FIRST("Alarm tasks first"),
    TITLE_ASC("Title (A-Z)")
}
