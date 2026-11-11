package com.example.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import com.example.ui.screens.FindCurrentMatchColor
import com.example.ui.screens.FindMatchColor
import com.example.ui.screens.MarkdownEditorTransformation
import com.example.ui.screens.NoteFind
import com.example.ui.screens.withFindHighlights
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteFindTest {

    @Test
    fun blankQueryFindsNothing() {
        assertTrue(NoteFind.matches("some text", "").isEmpty())
        assertTrue(NoteFind.matches("some text", "   ").isEmpty())
    }

    @Test
    fun ignoresCaseAndReturnsEveryStart() {
        assertEquals(listOf(0, 11), NoteFind.matches("Apple pie, apple tart", "apple"))
        assertEquals(2, NoteFind.matches("Apple pie, apple tart", "APPLE").size)
    }

    @Test
    fun matchesDoNotOverlap() {
        assertEquals(listOf(0, 2), NoteFind.matches("aaaa", "aa"))
    }

    @Test
    fun noMatchGivesEmptyList() {
        assertTrue(NoteFind.matches("hello", "xyz").isEmpty())
    }

    @Test
    fun stepGoesRoundInBothDirections() {
        assertEquals(1, NoteFind.step(0, 3, forward = true))
        assertEquals(0, NoteFind.step(2, 3, forward = true))
        assertEquals(2, NoteFind.step(0, 3, forward = false))
        assertEquals(0, NoteFind.step(0, 0, forward = true))
    }

    @Test
    fun highlightsAddABackgroundOverEachMatch() {
        val result = AnnotatedString("one two one").withFindHighlights("one")
        val spans = result.spanStyles.filter { it.item.background == FindMatchColor }
        assertEquals(listOf(0 to 3, 8 to 11), spans.map { it.start to it.end })
        assertEquals("one two one", result.text)
    }

    @Test
    fun editorColoursCodeInsideAFenceByLanguage() {
        val text = "intro\n```kotlin\nval x = 1\n```\nafter"
        val result = MarkdownEditorTransformation(Color.Gray).filter(AnnotatedString(text)).text
        val codeStart = text.indexOf("val")
        assertTrue(result.spanStyles.any { it.item.color != Color.Unspecified && it.item.color != Color.Gray && it.start == codeStart })
        // Nothing outside the fence is coloured as code
        assertTrue(result.spanStyles.none { it.start < text.indexOf("```kotlin") && it.item.color != Color.Unspecified })
    }

    @Test
    fun editorColoursAFenceThatIsStillOpen() {
        val text = "```python\ndef f(): pass"
        val result = MarkdownEditorTransformation(Color.Gray).filter(AnnotatedString(text)).text
        assertTrue(result.spanStyles.any { it.start >= text.indexOf("def") && it.item.color != Color.Gray && it.item.color != Color.Unspecified })
    }

    @Test
    fun fenceWithoutLanguageIsNotColoured() {
        val text = "```\nval x = 1\n```"
        val result = MarkdownEditorTransformation(Color.Gray).filter(AnnotatedString(text)).text
        assertTrue(result.spanStyles.none { it.item.color != Color.Unspecified && it.item.color != Color.Gray })
    }

    @Test
    fun editorMarksFindMatchesAndTheCurrentOne() {
        val result = MarkdownEditorTransformation(Color.Gray, findQuery = "cat", findCurrent = 1)
            .filter(AnnotatedString("cat dog cat")).text
        assertEquals(listOf(0 to 3), result.spanStyles.filter { it.item.background == FindMatchColor }.map { it.start to it.end })
        assertEquals(listOf(8 to 11), result.spanStyles.filter { it.item.background == FindCurrentMatchColor }.map { it.start to it.end })
    }
}
