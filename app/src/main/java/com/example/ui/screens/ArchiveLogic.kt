package com.example.ui.screens

import com.example.data.local.Course

/**
 * Details of a course code conflict where a code is already used in an archived semester.
 */
data class ArchivedCourseConflict(
    val code: String,
    val semesterName: String
)

/**
 * Result of validating a semester name for archive.
 */
data class SemesterNameValidation(
    val isValid: Boolean,
    val trimmedName: String,
    val errorMessage: String? = null
) {
    val isSuccess: Boolean get() = isValid
}

/**
 * Validates a candidate semester name:
 * - Trims leading and trailing whitespace
 * - Rejects empty or whitespace-only names
 * - Rejects names longer than 40 characters
 */
fun validateSemesterName(name: String): SemesterNameValidation {
    val trimmed = name.trim()
    return when {
        trimmed.isEmpty() -> SemesterNameValidation(
            isValid = false,
            trimmedName = trimmed,
            errorMessage = "Semester name cannot be blank"
        )
        trimmed.length > 40 -> SemesterNameValidation(
            isValid = false,
            trimmedName = trimmed,
            errorMessage = "Semester name cannot exceed 40 characters"
        )
        else -> SemesterNameValidation(
            isValid = true,
            trimmedName = trimmed,
            errorMessage = null
        )
    }
}

/**
 * Searches the list of existing archived semester names for a match ignoring case.
 * Returns the existing semester name with its original spelling and casing, or null if no match.
 */
fun findExistingSemester(name: String, existing: Collection<String>): String? {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return null
    return existing.firstOrNull { it.trim().equals(trimmed, ignoreCase = true) }
}

/**
 * Builds the live summary text shown to the user before archiving.
 * When the semester name matches an existing archived semester, adds a note that
 * the courses will be added to that semester.
 */
fun archiveSummaryText(
    taskCount: Int,
    classCount: Int,
    noteCount: Int,
    existingName: String? = null
): String {
    val base = "$taskCount tasks, $classCount classes and $noteCount notes will be hidden. Their reminders stop."
    return if (existingName.isNullOrBlank()) {
        base
    } else {
        "$base Adds to the existing ${existingName.trim()}."
    }
}

/**
 * Formats counts of courses, tasks, classes, and notes for an archived semester card.
 */
fun formatSemesterCounts(
    courseCount: Int,
    taskCount: Int,
    classCount: Int,
    noteCount: Int
): String {
    val coursesStr = if (courseCount == 1) "1 course" else "$courseCount courses"
    val tasksStr = if (taskCount == 1) "1 task" else "$taskCount tasks"
    val classesStr = if (classCount == 1) "1 class" else "$classCount classes"
    val notesStr = if (noteCount == 1) "1 note" else "$noteCount notes"
    return "$coursesStr, $tasksStr, $classesStr, $notesStr"
}

/**
 * Confirmation dialog message before deleting an archived semester forever.
 */
fun deleteSemesterConfirmationMessage(
    semesterName: String,
    courseCount: Int,
    taskCount: Int,
    classCount: Int,
    noteCount: Int
): String {
    val counts = formatSemesterCounts(courseCount, taskCount, classCount, noteCount)
    return "Permanently delete $semesterName including $counts? This cannot be undone."
}

/**
 * Groups archived courses by semester name ignoring case, preserving original spelling.
 */
fun groupCoursesBySemester(courses: List<Course>): Map<String, List<Course>> {
    return courses
        .filter { !it.isDeleted && it.archivedSemester.isNotBlank() }
        .groupBy { it.archivedSemester.trim().uppercase() }
        .mapKeys { (_, list) -> list.first().archivedSemester.trim() }
}
