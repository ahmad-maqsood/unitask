package com.example.util.markdown

/**
 * Toggles a markdown checklist item at [lineIndex] between checked and unchecked.
 * Preserves the original bullet character (-, *, or +), indentation, and line endings.
 * If [lineIndex] is out of range or the target line is not a checklist item, returns [text] unchanged.
 */
fun toggleCheckboxAtLine(text: String, lineIndex: Int): String {
    if (text.isEmpty() || lineIndex < 0) return text

    val isCrlf = text.contains("\r\n")
    val delimiter = if (isCrlf) "\r\n" else "\n"
    val lines = text.split(delimiter)
    if (lineIndex >= lines.size) return text

    val line = lines[lineIndex]

    var charIdx = 0
    while (charIdx < line.length) {
        when (line[charIdx]) {
            ' ' -> charIdx++
            '\t' -> charIdx++
            else -> break
        }
    }

    val indent = line.substring(0, charIdx)
    val rest = line.substring(charIdx)

    if (rest.length < 5) return text
    val bullet = rest[0]
    if (bullet != '-' && bullet != '*' && bullet != '+') return text
    if (rest[1] != ' ' || rest[2] != '[' || rest[4] != ']') return text

    val stateChar = rest[3]
    val toggledRest = when (stateChar) {
        ' ' -> bullet + " [x]" + rest.substring(5)
        'x', 'X' -> bullet + " [ ]" + rest.substring(5)
        else -> return text
    }

    val updatedLines = lines.toMutableList()
    updatedLines[lineIndex] = indent + toggledRest
    return updatedLines.joinToString(delimiter)
}

