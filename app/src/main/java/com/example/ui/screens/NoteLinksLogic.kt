package com.example.ui.screens

import com.example.data.local.Note
import com.example.data.local.NoteListItem
import com.example.util.markdown.noteLinkKey
import com.example.util.markdown.noteLinkTitles

/**
 * The note a [[title]] points at. Capitals do not matter. When several notes share the title,
 * the one edited most recently wins.
 */
fun findNoteForLink(notes: List<NoteListItem>, linkTitle: String): Note? {
    val key = noteLinkKey(linkTitle)
    if (key.isEmpty()) return null
    return notes.asSequence()
        .map { it.note }
        .filter { noteLinkKey(noteDisplayTitle(it)) == key }
        .maxByOrNull { it.updatedAt }
}

/** Notes (other than the one with currentId) that contain a [[link]] to the note titled currentTitle. */
fun findBacklinks(notes: List<NoteListItem>, currentId: Long, currentTitle: String): List<Note> {
    val key = noteLinkKey(currentTitle)
    if (key.isEmpty()) return emptyList()
    return notes.asSequence()
        .map { it.note }
        .filter { it.id != currentId && noteLinkTitles(it.body).any { link -> noteLinkKey(link) == key } }
        .toList()
}

/**
 * Notes to offer while a "[[" is being typed. Titles starting with what was typed come first,
 * then titles containing it. A title used by several notes is offered once.
 */
fun noteLinkSuggestions(
    notes: List<NoteListItem>,
    currentId: Long,
    typed: String,
    limit: Int = 5
): List<Note> {
    val needle = typed.trim().lowercase()
    return notes.asSequence()
        .map { it.note }
        .filter { it.id != currentId }
        .filter { noteLinkKey(noteDisplayTitle(it)).isNotEmpty() }
        .groupBy { noteLinkKey(noteDisplayTitle(it)) }
        .mapNotNull { (key, sameTitle) ->
            if (needle.isNotEmpty() && !key.contains(needle)) null
            else sameTitle.maxByOrNull { it.updatedAt }
        }
        .sortedWith(
            compareByDescending<Note> { noteLinkKey(noteDisplayTitle(it)).startsWith(needle) }
                .thenByDescending { it.updatedAt }
        )
        .take(limit)
}
