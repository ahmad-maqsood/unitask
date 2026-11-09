package com.example.util.markdown

/**
 * Helpers that keep the Edit and Preview screens of a note at the same place in the text.
 * All line numbers are 0 based and count lines the same way the Markdown parser does.
 */

/** The source line that holds the character at [offset]. */
fun lineIndexAtOffset(text: String, offset: Int): Int {
    val end = offset.coerceIn(0, text.length)
    var line = 0
    for (i in 0 until end) {
        if (text[i] == '\n') line++
    }
    return line
}

/** The offset of the first character of source line [line] (the end of the text if the line is past the end). */
fun offsetOfLine(text: String, line: Int): Int {
    if (line <= 0) return 0
    var current = 0
    for (i in text.indices) {
        if (text[i] == '\n') {
            current++
            if (current == line) return i + 1
        }
    }
    return text.length
}

/** The block that contains source line [line], given the start line of every block in order. */
fun blockIndexForLine(startLines: List<Int>, line: Int): Int {
    if (startLines.isEmpty()) return 0
    var lo = 0
    var hi = startLines.size - 1
    var found = 0
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (startLines[mid] <= line) {
            found = mid
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    return found
}
