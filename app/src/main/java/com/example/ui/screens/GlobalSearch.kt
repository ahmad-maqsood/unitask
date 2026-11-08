package com.example.ui.screens

import com.example.data.local.Course
import com.example.data.local.NoteListItem
import com.example.data.local.TaskItem
import com.example.data.local.TaskWithCourse
import com.example.data.local.isMyTasksCourse

/**
 * Wrapper for an item that belongs to an archived semester.
 */
data class ArchivedItem<T>(
    val item: T,
    val semesterName: String
)

/**
 * Result of a global search matching a task.
 */
data class SearchTaskResult(
    val taskWithCourse: TaskWithCourse,
    val archivedSemester: String? = null
) {
    val task: TaskItem get() = taskWithCourse.task
    val course: Course? get() = taskWithCourse.course
    val isArchived: Boolean get() = !archivedSemester.isNullOrBlank()
}

/**
 * Result of a global search matching a note, with body snippet and match range.
 */
data class SearchNoteResult(
    val noteItem: NoteListItem,
    val snippet: String,
    val matchRange: IntRange?,
    val archivedSemester: String? = null
) {
    val highlightRange: IntRange? get() = matchRange
    val isArchived: Boolean get() = !archivedSemester.isNullOrBlank()
}

/**
 * Result of a global search matching a course.
 */
data class SearchCourseResult(
    val course: Course,
    val archivedSemester: String? = null
) {
    val isArchived: Boolean get() = !archivedSemester.isNullOrBlank()
}

/**
 * Aggregated search results across tasks, notes, and courses.
 */
data class SearchResults(
    val tasks: List<SearchTaskResult>,
    val notes: List<SearchNoteResult>,
    val courses: List<SearchCourseResult>
) {
    val isEmpty: Boolean get() = tasks.isEmpty() && notes.isEmpty() && courses.isEmpty()
    val totalCount: Int get() = tasks.size + notes.size + courses.size
}

/**
 * Extracts a snippet of around 80 characters surrounding the first match in [body].
 * If [body] contains none of the query words (e.g. match was in title or course code),
 * returns up to the first 80 characters of [body] with null matchRange.
 * Uses region matching / indexOf to avoid lowercasing large bodies (up to 200,000 characters).
 */
fun buildNoteSnippet(
    body: String,
    words: List<String>,
    targetLength: Int = 80
): Pair<String, IntRange?> {
    if (body.isEmpty()) {
        return Pair("", null)
    }

    // Find the earliest match of any query word in the body without copying the body
    var earliestMatchIndex = -1
    var matchedWordLength = 0
    for (word in words) {
        val idx = body.indexOf(word, ignoreCase = true)
        if (idx >= 0 && (earliestMatchIndex == -1 || idx < earliestMatchIndex)) {
            earliestMatchIndex = idx
            matchedWordLength = word.length
        }
    }

    // Match not found in body: match occurred in title or course code
    if (earliestMatchIndex == -1) {
        val end = minOf(body.length, targetLength)
        val hasSuffix = end < body.length
        val snippet = body.substring(0, end) + if (hasSuffix) "..." else ""
        return Pair(snippet, null)
    }

    val matchStart = earliestMatchIndex
    val matchEnd = matchStart + matchedWordLength
    val remainingContext = maxOf(0, targetLength - matchedWordLength)
    val contextBefore = remainingContext / 2

    var start = maxOf(0, matchStart - contextBefore)
    var end = minOf(body.length, start + targetLength)
    if (end - start < targetLength) {
        start = maxOf(0, end - targetLength)
    }

    val hasPrefix = start > 0
    val hasSuffix = end < body.length

    val prefix = if (hasPrefix) "..." else ""
    val suffix = if (hasSuffix) "..." else ""

    val slice = body.substring(start, end)
    val snippet = prefix + slice + suffix

    val highlightStart = prefix.length + (matchStart - start)
    val highlightEnd = highlightStart + matchedWordLength
    val highlightRange = highlightStart until highlightEnd

    return Pair(snippet, highlightRange)
}

/**
 * Checks if a task matches all query words across title, description, and course code/name.
 */
fun matchesTask(taskWithCourse: TaskWithCourse, words: List<String>): Boolean {
    val task = taskWithCourse.task
    val course = taskWithCourse.course
    val courseCode = if (course?.isMyTasksCourse() == true) "My tasks" else course?.code.orEmpty()
    val courseName = if (course?.isMyTasksCourse() == true) "My tasks" else course?.name.orEmpty()

    return words.all { word ->
        task.title.contains(word, ignoreCase = true) ||
        task.description.contains(word, ignoreCase = true) ||
        courseCode.contains(word, ignoreCase = true) ||
        courseName.contains(word, ignoreCase = true)
    }
}

/**
 * Checks if a note matches all query words across title, body, and course code.
 * Searches the body using indexOf(ignoreCase = true) without copying or lowercasing.
 */
fun matchesNote(noteItem: NoteListItem, words: List<String>): Boolean {
    val note = noteItem.note
    val courseCode = noteItem.courseCode.orEmpty()

    return words.all { word ->
        note.title.contains(word, ignoreCase = true) ||
        note.body.indexOf(word, ignoreCase = true) >= 0 ||
        courseCode.contains(word, ignoreCase = true)
    }
}

/**
 * Checks if a course matches all query words across code, name, instructor, and location.
 */
fun matchesCourse(course: Course, words: List<String>): Boolean {
    val code = if (course.isMyTasksCourse()) "My tasks" else course.code
    val name = if (course.isMyTasksCourse()) "My tasks" else course.name
    val instructor = course.instructor
    val location = course.location

    return words.all { word ->
        code.contains(word, ignoreCase = true) ||
        name.contains(word, ignoreCase = true) ||
        instructor.contains(word, ignoreCase = true) ||
        location.contains(word, ignoreCase = true)
    }
}

/**
 * Performs global multi-word search over tasks, notes, and courses.
 * An item matches when EVERY word in the query is found in any of its searchable fields.
 * Empty or blank query returns empty results.
 * Archived items are included only when provided via the archived parameters.
 */
@JvmOverloads
fun searchEverything(
    query: String,
    tasks: List<TaskWithCourse>,
    notes: List<NoteListItem>,
    courses: List<Course>,
    archivedTasks: List<ArchivedItem<TaskWithCourse>> = emptyList(),
    archivedNotes: List<ArchivedItem<NoteListItem>> = emptyList(),
    archivedCourses: List<ArchivedItem<Course>> = emptyList()
): SearchResults {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) {
        return SearchResults(emptyList(), emptyList(), emptyList())
    }

    val words = trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) {
        return SearchResults(emptyList(), emptyList(), emptyList())
    }

    val taskResults = ArrayList<SearchTaskResult>()
    for (t in tasks) {
        if (matchesTask(t, words)) {
            taskResults.add(SearchTaskResult(taskWithCourse = t, archivedSemester = null))
        }
    }
    for (a in archivedTasks) {
        if (matchesTask(a.item, words)) {
            taskResults.add(SearchTaskResult(taskWithCourse = a.item, archivedSemester = a.semesterName))
        }
    }

    val noteResults = ArrayList<SearchNoteResult>()
    for (n in notes) {
        if (matchesNote(n, words)) {
            val (snippet, matchRange) = buildNoteSnippet(n.note.body, words)
            noteResults.add(
                SearchNoteResult(
                    noteItem = n,
                    snippet = snippet,
                    matchRange = matchRange,
                    archivedSemester = null
                )
            )
        }
    }
    for (a in archivedNotes) {
        if (matchesNote(a.item, words)) {
            val (snippet, matchRange) = buildNoteSnippet(a.item.note.body, words)
            noteResults.add(
                SearchNoteResult(
                    noteItem = a.item,
                    snippet = snippet,
                    matchRange = matchRange,
                    archivedSemester = a.semesterName
                )
            )
        }
    }

    val courseResults = ArrayList<SearchCourseResult>()
    for (c in courses) {
        if (matchesCourse(c, words)) {
            courseResults.add(SearchCourseResult(course = c, archivedSemester = null))
        }
    }
    for (a in archivedCourses) {
        if (matchesCourse(a.item, words)) {
            courseResults.add(SearchCourseResult(course = a.item, archivedSemester = a.semesterName))
        }
    }

    return SearchResults(
        tasks = taskResults,
        notes = noteResults,
        courses = courseResults
    )
}
