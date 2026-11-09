package com.example.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.screens.MarkdownEditorTransformation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownEditorTransformationTest {

    private val marker = Color.Gray
    private val transformation = MarkdownEditorTransformation(marker)

    private fun run(text: String) = transformation.filter(AnnotatedString(text)).text

    @Test
    fun textIsNeverChanged() {
        val text = "# Title\n\nbody\n```\ncode\n```\n> quote"
        assertEquals(text, run(text).text)
    }

    @Test
    fun headingLineIsBoldAndItsHashesAreDimmed() {
        val result = run("## Section\nplain")
        val bold = result.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }
        assertEquals(1, bold.size)
        assertEquals(0, bold[0].start)
        assertEquals(10, bold[0].end)
        val dim = result.spanStyles.filter { it.item.color == marker }
        assertEquals(1, dim.size)
        assertEquals(0, dim[0].start)
        assertEquals(2, dim[0].end)
    }

    @Test
    fun plainTextAndHashWithoutSpaceAreLeftAlone() {
        assertTrue(run("just text\n#hashtag\n####### seven").spanStyles.isEmpty())
    }

    @Test
    fun hashesInsideACodeFenceAreNotHeadings() {
        val result = run("```\n# not a heading\n```\nafter")
        assertFalse(result.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        assertTrue(result.spanStyles.any { it.item.fontFamily == FontFamily.Monospace })
    }

    @Test
    fun emptyTextIsFine() {
        assertEquals("", run("").text)
    }
}
