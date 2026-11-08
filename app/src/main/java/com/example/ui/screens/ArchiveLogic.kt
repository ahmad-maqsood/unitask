package com.example.ui.screens

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
