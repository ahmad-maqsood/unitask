package com.example.util.markdown

data class EditorText(
    val text: String,
    val selStart: Int,
    val selEnd: Int
)

fun EditorText.clamped(): EditorText {
    val len = text.length
    val s = selStart.coerceIn(0, len)
    val e = selEnd.coerceIn(0, len)
    return if (s <= e) {
        if (s == selStart && e == selEnd) this else copy(selStart = s, selEnd = e)
    } else {
        copy(selStart = e, selEnd = s)
    }
}

fun toggleWrap(t: EditorText, marker: String): EditorText {
    val clamped = t.clamped()
    val text = clamped.text
    val s = clamped.selStart
    val e = clamped.selEnd
    val mLen = marker.length

    // Empty selection: insert marker twice, place cursor between them
    if (s == e) {
        val newText = text.substring(0, s) + marker + marker + text.substring(e)
        val newPos = s + mLen
        return EditorText(newText, newPos, newPos)
    }

    // Case 1: Markers are immediately outside the selection (e.g. **|hello|**)
    if (s >= mLen && e + mLen <= text.length &&
        text.substring(s - mLen, s) == marker &&
        text.substring(e, e + mLen) == marker
    ) {
        val newText = text.substring(0, s - mLen) + text.substring(s, e) + text.substring(e + mLen)
        return EditorText(newText, s - mLen, e - mLen)
    }

    // Case 2: Markers are inside the selection boundaries (e.g. |**hello**|)
    if ((e - s) >= 2 * mLen &&
        text.substring(s, s + mLen) == marker &&
        text.substring(e - mLen, e) == marker
    ) {
        val newText = text.substring(0, s) + text.substring(s + mLen, e - mLen) + text.substring(e)
        return EditorText(newText, s, e - 2 * mLen)
    }

    // Otherwise: Wrap the selection in markers
    val newText = text.substring(0, s) + marker + text.substring(s, e) + marker + text.substring(e)
    return EditorText(newText, s + mLen, e + mLen)
}

fun toggleLinePrefix(t: EditorText, prefix: String): EditorText {
    val clamped = t.clamped()
    val text = clamped.text
    if (text.isEmpty()) {
        val newText = prefix
        val newPos = prefix.length
        return EditorText(newText, newPos, newPos)
    }

    val selStart = clamped.selStart
    val selEnd = clamped.selEnd

    val lines = text.split("\n")
    val lineOffsets = mutableListOf<Int>()
    var currOffset = 0
    for (line in lines) {
        lineOffsets.add(currOffset)
        currOffset += line.length + 1
    }

    val effectiveEnd = if (selEnd > selStart && selEnd > 0 && text[selEnd - 1] == '\n') selEnd - 1 else selEnd
    val touchedIndices = mutableListOf<Int>()
    for (i in lines.indices) {
        val lStart = lineOffsets[i]
        val lEnd = lStart + lines[i].length
        if (lStart <= effectiveEnd && lEnd >= selStart) {
            touchedIndices.add(i)
        }
    }
    if (touchedIndices.isEmpty()) {
        val idx = lineOffsets.indexOfLast { it <= selStart }.coerceAtLeast(0)
        touchedIndices.add(idx)
    }

    val isHeading = prefix.trimEnd().all { it == '#' } && prefix.endsWith(" ")
    val isNumbered = prefix == "1. "
    val isChecklist = prefix == "- [ ] "
    val isBullet = prefix == "- "

    val allHavePrefix = touchedIndices.all { i ->
        val line = lines[i]
        when {
            isNumbered -> line.matches(Regex("""^\d+\.\s+.*"""))
            isHeading -> line.startsWith(prefix)
            isChecklist -> line.startsWith("- [ ] ") || line.startsWith("- [x] ") || line.startsWith("- [X] ")
            else -> line.startsWith(prefix)
        }
    }

    val newLines = lines.toMutableList()
    var numCounter = 1
    for (i in touchedIndices) {
        val line = lines[i]
        val transformed = if (allHavePrefix) {
            when {
                isNumbered -> line.replaceFirst(Regex("""^\d+\.\s+"""), "")
                isHeading -> line.replaceFirst(Regex("""^#{1,6}\s+"""), "")
                isChecklist -> line.replaceFirst(Regex("""^-\s+\[[ xX]\]\s+"""), "")
                else -> if (line.startsWith(prefix)) line.substring(prefix.length) else line
            }
        } else {
            when {
                isNumbered -> {
                    val stripped = line.replaceFirst(Regex("""^(?:\d+\.|\-|\*|\+)\s+(?:\[[ xX]\]\s+)?|^(?:#{1,6}|>)\s+"""), "")
                    "${numCounter++}. $stripped"
                }
                isHeading -> {
                    val stripped = line.replaceFirst(Regex("""^#{1,6}\s+"""), "")
                    "$prefix$stripped"
                }
                isChecklist -> {
                    if (line.matches(Regex("""^-\s+\[[ xX]\]\s+.*"""))) {
                        line
                    } else if (line.matches(Regex("""^[-*+]\s+.*"""))) {
                        val stripped = line.replaceFirst(Regex("""^[-*+]\s+"""), "")
                        "- [ ] $stripped"
                    } else {
                        "- [ ] $line"
                    }
                }
                isBullet -> {
                    if (line.matches(Regex("""^-\s+\[[ xX]\]\s+.*"""))) {
                        val stripped = line.replaceFirst(Regex("""^-\s+\[[ xX]\]\s+"""), "")
                        "- $stripped"
                    } else if (line.startsWith("- ")) {
                        line
                    } else {
                        "- $line"
                    }
                }
                else -> {
                    if (line.startsWith(prefix)) line else "$prefix$line"
                }
            }
        }
        newLines[i] = transformed
    }

    val newText = newLines.joinToString("\n")

    if (selStart == selEnd) {
        val firstTouched = touchedIndices.first()
        val delta = newLines[firstTouched].length - lines[firstTouched].length
        val newCursor = (selStart + delta).coerceIn(0, newText.length)
        return EditorText(newText, newCursor, newCursor)
    } else {
        val firstTouched = touchedIndices.first()
        val firstDelta = newLines[firstTouched].length - lines[firstTouched].length
        val newSelStart = if (selStart > lineOffsets[firstTouched]) {
            (selStart + firstDelta).coerceAtLeast(lineOffsets[firstTouched])
        } else {
            selStart
        }

        var totalDelta = 0
        for (i in touchedIndices) {
            totalDelta += newLines[i].length - lines[i].length
        }
        val newSelEnd = (selEnd + totalDelta).coerceIn(newSelStart, newText.length)
        return EditorText(newText, newSelStart.coerceIn(0, newText.length), newSelEnd.coerceIn(0, newText.length))
    }
}

fun insertLink(t: EditorText): EditorText {
    val clamped = t.clamped()
    val text = clamped.text
    val s = clamped.selStart
    val e = clamped.selEnd

    return if (s == e) {
        val linkTemplate = "[text]()"
        val newText = text.substring(0, s) + linkTemplate + text.substring(e)
        // Select the word "text" (indices s + 1 to s + 5)
        EditorText(newText, s + 1, s + 5)
    } else {
        val selected = text.substring(s, e)
        val replacement = "[$selected]()"
        val newText = text.substring(0, s) + replacement + text.substring(e)
        // Cursor placed between the parentheses: right before the closing ')'
        val cursor = s + replacement.length - 1
        EditorText(newText, cursor, cursor)
    }
}

fun insertTable(t: EditorText): EditorText {
    val clamped = t.clamped()
    val text = clamped.text
    val s = clamped.selStart
    val e = clamped.selEnd

    val needsLeadingNewline = s > 0 && text[s - 1] != '\n'
    val needsTrailingNewline = e < text.length && text[e] != '\n'

    val tableContent = "| Column 1 | Column 2 |\n| --- | --- |\n|  |  |\n"
    val toInsert = buildString {
        if (needsLeadingNewline) append("\n")
        append(tableContent)
        if (needsTrailingNewline) append("\n")
    }

    val newText = text.substring(0, s) + toInsert + text.substring(e)
    val newPos = s + toInsert.length
    return EditorText(newText, newPos, newPos)
}

fun continueListOnEnter(before: EditorText, after: EditorText): EditorText? {
    if (before.selStart != before.selEnd) return null
    if (after.text.length != before.text.length + 1) return null
    if (before.selStart !in 0 until after.text.length) return null
    if (after.text[before.selStart] != '\n') return null
    if (after.selStart != before.selStart + 1) return null

    val pos = before.selStart
    val lineStart = before.text.lastIndexOf('\n', pos - 1).let { if (it == -1) 0 else it + 1 }
    val lineEnd = before.text.indexOf('\n', pos).let { if (it == -1) before.text.length else it }
    val line = before.text.substring(lineStart, lineEnd)

    // List marker regex:
    // Group 1: Leading spaces or tabs
    // Group 2: Checklist marker (- [ ], - [x], * [ ], etc.)
    // Group 3: Number N
    // Group 4: Number delimiter (. or ))
    // Group 5: Bullet marker (- , * , + )
    val markerRegex = Regex("""^(\s*)(?:([-*+]\s+\[[ xX]\]\s+)|(\d+)([.)])\s+|([-*+]\s+))""")
    val match = markerRegex.find(line) ?: return null
    if (match.range.first != 0) return null

    val fullMarkerLen = match.value.length
    val remainder = line.substring(fullMarkerLen)

    // If the line contains ONLY the marker (an empty item):
    // remove the marker and the Enter, leaving an empty line, cursor at its start.
    if (remainder.isBlank()) {
        val newText = before.text.substring(0, lineStart) + before.text.substring(lineEnd)
        return EditorText(newText, lineStart, lineStart)
    }

    // Otherwise, insert on the new line the same leading spaces and marker.
    val indent = match.groupValues[1]
    val continuationPrefix = when {
        match.groupValues[2].isNotEmpty() -> {
            "$indent- [ ] "
        }
        match.groupValues[3].isNotEmpty() -> {
            val n = match.groupValues[3].toLongOrNull() ?: 1L
            val delim = match.groupValues[4]
            "$indent${n + 1}$delim "
        }
        else -> {
            "$indent${match.groupValues[5]}"
        }
    }

    val insertPos = after.selStart
    val newText = after.text.substring(0, insertPos) + continuationPrefix + after.text.substring(insertPos)
    val newCursor = insertPos + continuationPrefix.length
    return EditorText(newText, newCursor, newCursor)
}

fun insertImageLine(t: EditorText, imageMarkdown: String): EditorText {
    val clamped = t.clamped()
    val text = clamped.text
    val s = clamped.selStart
    val e = clamped.selEnd

    val needsNewlineBefore = s > 0 && text[s - 1] != '\n'
    val toInsert = buildString {
        if (needsNewlineBefore) append("\n")
        append(imageMarkdown)
        append("\n")
    }

    val newText = text.substring(0, s) + toInsert + text.substring(e)
    val newCursor = s + toInsert.length
    return EditorText(newText, newCursor, newCursor)
}



