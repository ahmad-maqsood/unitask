package com.example.util

data class ChecklistItem(
    val lineIndex: Int,
    val text: String,
    val checked: Boolean
)

fun parseChecklist(description: String): List<ChecklistItem> {
    if (description.isEmpty()) return emptyList()
    val lines = description.lines()
    val items = mutableListOf<ChecklistItem>()
    for ((index, line) in lines.withIndex()) {
        val trimmed = line.trimStart()
        if (trimmed.startsWith("- [ ]") || trimmed.startsWith("- [x]", ignoreCase = true)) {
            val checked = trimmed.startsWith("- [x]", ignoreCase = true)
            val text = trimmed.substring(5).trim()
            items.add(ChecklistItem(lineIndex = index, text = text, checked = checked))
        }
    }
    return items
}

fun toggleChecklistLine(description: String, lineIndex: Int): String {
    if (lineIndex < 0 || description.isEmpty()) return description
    val isCrlf = description.contains("\r\n")
    val delimiter = if (isCrlf) "\r\n" else "\n"
    val lines = description.split(delimiter)
    if (lineIndex >= lines.size) return description

    val line = lines[lineIndex]
    val trimmed = line.trimStart()
    val indent = line.substring(0, line.length - trimmed.length)

    val toggledLine = if (trimmed.startsWith("- [ ]")) {
        indent + "- [x]" + trimmed.substring(5)
    } else if (trimmed.startsWith("- [x]", ignoreCase = true)) {
        indent + "- [ ]" + trimmed.substring(5)
    } else {
        return description
    }

    val updatedLines = lines.toMutableList()
    updatedLines[lineIndex] = toggledLine
    return updatedLines.joinToString(delimiter)
}

/**
 * One row of the checklist editor in the Add/Edit task dialog.
 * [id] only exists so Compose can tell rows apart; it is never saved.
 */
data class DraftChecklistItem(
    val id: Long,
    val text: String,
    val checked: Boolean
)

/**
 * Splits a saved description into (plain notes, checklist rows).
 * Checklist lines ("- [ ] ..." / "- [x] ...") are removed from the notes.
 */
fun splitNotesAndChecklist(description: String): Pair<String, List<DraftChecklistItem>> {
    if (description.isEmpty()) return "" to emptyList()
    val items = parseChecklist(description)
    val checklistLineIndexes = items.map { it.lineIndex }.toSet()
    val notes = description.lines()
        .filterIndexed { index, _ -> index !in checklistLineIndexes }
        .joinToString("\n")
        .trimEnd()
    val drafts = items.mapIndexed { i, item ->
        DraftChecklistItem(id = i.toLong(), text = item.text, checked = item.checked)
    }
    return notes to drafts
}

/**
 * Joins the plain notes and the checklist rows back into one description string.
 * Empty rows are dropped. The checklist goes after the notes.
 */
fun buildDescriptionWithChecklist(notes: String, items: List<DraftChecklistItem>): String {
    val checklistText = items
        .filter { it.text.isNotBlank() }
        .joinToString("\n") { (if (it.checked) "- [x] " else "- [ ] ") + it.text.trim() }
    return listOf(notes.trimEnd(), checklistText)
        .filter { it.isNotBlank() }
        .joinToString("\n")
}


/**
 * Unticks every checklist item ("- [x]" becomes "- [ ]"). Used when a repeating task starts its
 * next round, so the checklist is fresh again. Other text is not touched.
 */
fun uncheckAllChecklistItems(description: String): String {
    if (description.isEmpty()) return description
    val delimiter = if (description.contains("\r\n")) "\r\n" else "\n"
    return description.split(delimiter).joinToString(delimiter) { line ->
        val trimmed = line.trimStart()
        if (trimmed.startsWith("- [x]", ignoreCase = true)) {
            line.substring(0, line.length - trimmed.length) + "- [ ]" + trimmed.substring(5)
        } else {
            line
        }
    }
}
