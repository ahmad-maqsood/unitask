package com.example.util

import com.example.util.markdown.blockIndexForLine
import com.example.util.markdown.lineIndexAtOffset
import com.example.util.markdown.offsetOfLine
import com.example.util.markdown.parseMarkdownIndexed
import org.junit.Assert.assertEquals
import org.junit.Test

class ScrollSyncTest {

    private val doc = "# Title\n\nFirst paragraph\nsecond line\n\n---\n\n```\ncode\nmore\n```\n\n- a\n- b\n"

    @Test
    fun everyBlockKnowsTheLineItStartsOn() {
        val parsed = parseMarkdownIndexed(doc)
        assertEquals(parsed.blocks.size, parsed.startLines.size)
        // heading, paragraph, rule, code block, bullet a, bullet b
        assertEquals(listOf(0, 2, 5, 7, 11, 12), parsed.startLines)
    }

    @Test
    fun emptyTextHasNoBlocks() {
        val parsed = parseMarkdownIndexed("")
        assertEquals(0, parsed.blocks.size)
        assertEquals(0, parsed.startLines.size)
    }

    @Test
    fun lineAtOffsetCountsNewlinesBeforeIt() {
        val text = "ab\ncd\nef"
        assertEquals(0, lineIndexAtOffset(text, 0))
        assertEquals(0, lineIndexAtOffset(text, 2))
        assertEquals(1, lineIndexAtOffset(text, 3))
        assertEquals(2, lineIndexAtOffset(text, 8))
        assertEquals(2, lineIndexAtOffset(text, 999))
    }

    @Test
    fun offsetOfLineIsTheStartOfThatLine() {
        val text = "ab\ncd\nef"
        assertEquals(0, offsetOfLine(text, 0))
        assertEquals(3, offsetOfLine(text, 1))
        assertEquals(6, offsetOfLine(text, 2))
        assertEquals(text.length, offsetOfLine(text, 50))
    }

    @Test
    fun lineAndOffsetRoundTrip() {
        for (line in 0..4) {
            assertEquals(line, lineIndexAtOffset(doc, offsetOfLine(doc, line)))
        }
    }

    @Test
    fun blockForLinePicksTheBlockThatContainsIt() {
        val starts = listOf(0, 2, 5, 7, 11, 12)
        assertEquals(0, blockIndexForLine(starts, 0))
        assertEquals(1, blockIndexForLine(starts, 3)) // second line of the paragraph
        assertEquals(3, blockIndexForLine(starts, 9)) // inside the code block
        assertEquals(5, blockIndexForLine(starts, 400))
        assertEquals(0, blockIndexForLine(emptyList(), 4))
    }
}
