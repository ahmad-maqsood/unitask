package com.example.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.sp
import com.example.util.markdown.CodeHighlighter
import com.example.util.markdown.CodeTokenType

/**
 * Light styling for the note editor so the structure of a note is easy to see while writing:
 * headings are bold and bigger, the marker characters (#, >, ```) are dimmed, and fenced code is
 * monospace and coloured by language, like the preview and the PDF. When Find is open, the matches get
 * a background too. The text itself is never changed, so cursor positions, undo and saving are unaffected.
 */
class MarkdownEditorTransformation(
    private val markerColor: Color,
    private val darkCode: Boolean = false,
    private val findQuery: String = "",
    private val findCurrent: Int = -1
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val source = text.text
        val styled = buildAnnotatedString {
            append(source)
            var lineStart = 0
            var insideFence = false
            var codeStart = 0
            var language = ""
            while (lineStart <= source.length) {
                var lineEnd = source.indexOf('\n', lineStart)
                if (lineEnd < 0) lineEnd = source.length
                val wasInside = insideFence
                styleLine(source, lineStart, lineEnd, insideFence)?.let { insideFence = it }
                if (!wasInside && insideFence) {
                    // Opening fence: the label after the backticks names the language
                    language = fenceLanguage(source, lineStart, lineEnd)
                    codeStart = lineEnd + 1
                } else if (wasInside && !insideFence) {
                    // Closing fence: colour what was between the two fences
                    addCodeColors(source, codeStart, lineStart - 1, language)
                }
                lineStart = lineEnd + 1
            }
            // A fence that is still open (being typed) is coloured to the end
            if (insideFence) addCodeColors(source, codeStart, source.length, language)

            NoteFind.matches(source, findQuery).forEachIndexed { index, at ->
                val color = if (index == findCurrent) FindCurrentMatchColor else FindMatchColor
                addStyle(SpanStyle(background = color), at, at + findQuery.length)
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }

    private fun fenceLanguage(source: String, start: Int, end: Int): String {
        var i = start
        while (i < end && (source[i] == ' ' || source[i] == '\t')) i++
        return source.substring(minOf(i + 3, end), end).trim()
    }

    private fun AnnotatedString.Builder.addCodeColors(source: String, from: Int, to: Int, language: String) {
        val start = from.coerceIn(0, source.length)
        val end = to.coerceIn(start, source.length)
        if (start >= end) return
        CodeHighlighter.tokenize(source.substring(start, end), language).forEach { token ->
            addStyle(
                SpanStyle(
                    color = codeTokenColor(token.type, darkCode),
                    fontStyle = if (token.type == CodeTokenType.COMMENT) FontStyle.Italic else null
                ),
                start + token.start,
                start + token.end
            )
        }
    }

    /** Styles one line. Returns the new "inside a code fence" state when this line changes it, else null. */
    private fun AnnotatedString.Builder.styleLine(
        source: String,
        start: Int,
        end: Int,
        insideFence: Boolean
    ): Boolean? {
        if (start >= end) return null

        var firstChar = start
        while (firstChar < end && (source[firstChar] == ' ' || source[firstChar] == '\t')) firstChar++
        if (firstChar >= end) return null

        // Code fence line: dim it, and flip the state
        if (source.startsWith("```", firstChar)) {
            addStyle(SpanStyle(color = markerColor, fontFamily = FontFamily.Monospace), start, end)
            return !insideFence
        }
        if (insideFence) {
            addStyle(SpanStyle(fontFamily = FontFamily.Monospace), start, end)
            return null
        }

        // Heading: up to 3 spaces, 1 to 6 # characters, then a space
        if (firstChar - start <= 3 && source[firstChar] == '#') {
            var hashEnd = firstChar
            while (hashEnd < end && source[hashEnd] == '#') hashEnd++
            val level = hashEnd - firstChar
            if (level in 1..6 && hashEnd < end && source[hashEnd] == ' ') {
                val size = when (level) {
                    1 -> 22.sp
                    2 -> 20.sp
                    3 -> 18.sp
                    else -> 16.sp
                }
                addStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = size), start, end)
                addStyle(SpanStyle(color = markerColor), firstChar, hashEnd)
                return null
            }
        }

        // Quote marker
        if (source[firstChar] == '>') {
            addStyle(SpanStyle(color = markerColor, fontWeight = FontWeight.Bold), firstChar, firstChar + 1)
        }
        return null
    }
}
