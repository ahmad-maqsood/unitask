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

