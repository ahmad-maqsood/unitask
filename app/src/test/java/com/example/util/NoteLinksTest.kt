package com.example.util

import com.example.util.markdown.EditorText
import com.example.util.markdown.Inline
import com.example.util.markdown.NOTE_LINK_URL_PREFIX
import com.example.util.markdown.activeNoteLinkQuery
import com.example.util.markdown.applyNoteLinkChoice
import com.example.util.markdown.noteLinkKey
import com.example.util.markdown.noteLinkTitles
import com.example.util.markdown.parseInlines
import com.example.util.markdown.sanitizeNoteLinkTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoteLinksTest {

    // ---- reading links

    @Test
    fun noteLinkTitlesFindsEveryLinkInOrder() {
        val body = "See [[Graphs]] and [[ Trees ]].\nAlso [[Graphs]] again, but not [single] or [[]] or [[a\nb]]."
        assertEquals(listOf("Graphs", " Trees ", "Graphs"), noteLinkTitles(body))
    }

    @Test
    fun noteLinkTitlesIsEmptyWithoutDoubleBrackets() {
        assertEquals(emptyList<String>(), noteLinkTitles("plain [link](https://example.com) text"))
    }

    @Test
    fun noteLinkKeyIgnoresCapitalsSpacesAndBrackets() {
        assertEquals("lecture 1", noteLinkKey("  Lecture   1 "))
        assertEquals(noteLinkKey("LECTURE 1"), noteLinkKey("lecture 1"))
        assertEquals("a b", sanitizeNoteLinkTitle("a [b]"))
    }

    // ---- preview parsing

    @Test
    fun parseInlinesTurnsDoubleBracketsIntoANoteLink() {
        val inlines = parseInlines("Read [[Graphs]] first")
        assertEquals(3, inlines.size)
        assertEquals(Inline.Text("Read "), inlines[0])
        val link = inlines[1] as Inline.Link
        assertEquals(NOTE_LINK_URL_PREFIX + "Graphs", link.url)
        assertEquals(listOf<Inline>(Inline.Text("Graphs")), link.children)
        assertEquals(Inline.Text(" first"), inlines[2])
    }

    @Test
    fun parseInlinesLeavesOrdinaryLinksAndBrokenNoteLinksAlone() {
        val ordinary = parseInlines("[text](https://example.com)")
        assertEquals("https://example.com", (ordinary.single() as Inline.Link).url)

        val unclosed = parseInlines("[[never closed")
        assertEquals(listOf<Inline>(Inline.Text("[[never closed")), unclosed)

        val empty = parseInlines("[[ ]]")
        assertEquals(false, empty.any { it is Inline.Link && it.url.startsWith(NOTE_LINK_URL_PREFIX) })
    }

    @Test
    fun noteLinkInsideInlineCodeStaysCode() {
        val inlines = parseInlines("`[[Graphs]]`")
        assertEquals(listOf<Inline>(Inline.Code("[[Graphs]]")), inlines)
    }

    // ---- typing "[["

    @Test
    fun activeQueryIsFoundWhileTypingAfterDoubleBrackets() {
        val q = activeNoteLinkQuery("See [[Gra", 9)!!
        assertEquals(6, q.start)
        assertEquals(9, q.end)
        assertEquals("Gra", q.query)

        assertEquals("", activeNoteLinkQuery("[[", 2)!!.query)
    }

    @Test
    fun activeQueryIsNullWhenNotTypingALink() {
        assertNull(activeNoteLinkQuery("plain text", 10))
        assertNull(activeNoteLinkQuery("one [bracket", 12))
        assertNull(activeNoteLinkQuery("[[done]] and more", 17))
        assertNull(activeNoteLinkQuery("[[line one\nline two", 19))
        assertNull(activeNoteLinkQuery("[[" + "x".repeat(200), 202))
        assertNull(activeNoteLinkQuery("abc", 0))
    }

    @Test
    fun choosingANoteClosesTheLinkAndMovesTheCursor() {
        val text = "See [[Gra and more"
        val query = activeNoteLinkQuery(text, 9)!!
        val result = applyNoteLinkChoice(EditorText(text, 9, 9), query, "Graph theory")
        assertEquals("See [[Graph theory]] and more", result.text)
        assertEquals(20, result.selStart)
        assertEquals(20, result.selEnd)
    }

    @Test
    fun choosingANoteKeepsAnExistingClosingBracketsInsteadOfDoublingThem() {
        val text = "See [[Gra]] now"
        val query = activeNoteLinkQuery(text, 9)!!
        val result = applyNoteLinkChoice(EditorText(text, 9, 9), query, "Graphs")
        assertEquals("See [[Graphs]] now", result.text)
    }

    @Test
    fun choosingANoteWithBracketsInItsTitleStillMakesAValidLink() {
        val text = "[["
        val query = activeNoteLinkQuery(text, 2)!!
        val result = applyNoteLinkChoice(EditorText(text, 2, 2), query, "Week [3] notes")
        assertEquals("[[Week 3 notes]]", result.text)
    }
}
