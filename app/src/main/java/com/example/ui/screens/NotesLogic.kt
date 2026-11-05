package com.example.ui.screens

import com.example.data.local.Note
import com.example.data.local.NoteListItem

sealed interface NotesFilter {
    object All : NotesFilter
    object NoCourse : NotesFilter
    data class Course(val courseId: Long) : NotesFilter
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

