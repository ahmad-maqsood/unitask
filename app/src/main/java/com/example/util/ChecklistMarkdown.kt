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

