package com.example.ui.screens

import com.example.data.local.Note
import com.example.data.local.NoteListItem
import com.example.data.repository.NoteRepository

sealed interface NotesFilter {
    object All : NotesFilter
    object NoCourse : NotesFilter
    data class Course(val courseId: Long) : NotesFilter
}

enum class NotesSort {
    UPDATED,
    CREATED,
    TITLE
}

fun filterNotes(
    items: List<NoteListItem>,
    query: String,
    filter: NotesFilter
): List<NoteListItem> {
    val trimmed = query.trim()
    return items.filter { item ->
        val matchesCourse = when (filter) {
            is NotesFilter.All -> true
            is NotesFilter.NoCourse -> item.note.courseId == null
            is NotesFilter.Course -> item.note.courseId == filter.courseId
        }
        val matchesQuery = if (trimmed.isEmpty()) {
            true
        } else {
            item.note.title.contains(trimmed, ignoreCase = true) ||
                item.note.body.contains(trimmed, ignoreCase = true)
        }
        matchesCourse && matchesQuery
    }
}

fun sortNotes(items: List<NoteListItem>, sort: NotesSort): List<NoteListItem> {
    return items.sortedWith(
        compareByDescending<NoteListItem> { it.note.isPinned }
            .then(
                when (sort) {
                    NotesSort.UPDATED -> compareByDescending { it.note.updatedAt }
                    NotesSort.CREATED -> compareByDescending { it.note.createdAt }
                    NotesSort.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { noteDisplayTitle(it.note) }
                }
            )
    )
}

fun daysLeftInTrash(deletedAt: Long, now: Long): Int {
    val retentionDays = NoteRepository.TRASH_DAYS
    if (now <= deletedAt) return retentionDays
    val millisPerDay = 24L * 60 * 60 * 1000L
    val elapsedDays = (now - deletedAt) / millisPerDay
    val remaining = retentionDays - elapsedDays
    return if (remaining < 0) 0 else remaining.toInt()
}

fun wordCount(text: String): Int {
    var count = 0
    var inWord = false
    for (i in 0 until text.length) {
        val c = text[i]
        if (!c.isWhitespace()) {
            if (!inWord) {
                count++
                inWord = true
            }
        } else {
            inWord = false
        }
    }
    return count
}

fun characterCount(text: String): Int {
    return text.length
}

// Strips markdown markers from a single line:
// Leading markers like #, >, -, *, 1., [ ], [x]
// Inline markers like ** and `
private fun stripMarkdownLine(line: String): String {
    var s = line.trim()
    s = s.replace("**", "").replace("`", "")
    val markerPattern = Regex("""^(?:#{1,6}\s*|>\s*|(?:[-*+]|\d+\.)\s*(?:\[[ xX]\]\s*)?|\[[ xX]\]\s*)""")
    while (true) {
        val match = markerPattern.find(s)
        if (match != null && match.range.first == 0 && match.value.isNotEmpty()) {
            s = s.substring(match.range.last + 1).trim()
        } else {
            break
        }
    }
    return s
}

fun noteDisplayTitle(note: Note): String {
    if (note.title.isNotBlank()) {
        return note.title.trim()
    }
    for (line in note.body.lineSequence()) {
        if (line.isNotBlank()) {
            val stripped = stripMarkdownLine(line)
            if (stripped.isNotBlank()) {
                return stripped
            }
        }
    }
    return "Untitled note"
}

fun notePreviewText(body: String, maxLines: Int = 2): String {
    if (body.isBlank() || maxLines <= 0) return ""
    val lines = mutableListOf<String>()
    for (rawLine in body.lineSequence()) {
        if (rawLine.isNotBlank()) {
            val stripped = stripMarkdownLine(rawLine)
            if (stripped.isNotBlank()) {
                lines.add(stripped)
                if (lines.size == maxLines) break
            }
        }
    }
    return lines.joinToString(" ")
}
