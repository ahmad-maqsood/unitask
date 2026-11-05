package com.example.util

import com.example.util.markdown.EditorText
import com.example.util.markdown.continueListOnEnter
import com.example.util.markdown.insertLink
import com.example.util.markdown.insertTable
import com.example.util.markdown.toggleLinePrefix
import com.example.util.markdown.toggleWrap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownEditingTest {

    // -------------------------------------------------------------------------
    // toggleWrap tests
    // -------------------------------------------------------------------------

    @Test
    fun toggleWrapAddsMarkersAroundSelection() {
        val t = EditorText("hello world", 0, 5)
        val res = toggleWrap(t, "**")
        assertEquals("**hello** world", res.text)
        assertEquals(2, res.selStart)
        assertEquals(7, res.selEnd)
        assertEquals("hello", res.text.substring(res.selStart, res.selEnd))
    }

    @Test
    fun toggleWrapRemovesMarkersWhenAlreadyWrappedOutsideSelection() {
        val t = EditorText("**hello** world", 2, 7)
        val res = toggleWrap(t, "**")
        assertEquals("hello world", res.text)
        assertEquals(0, res.selStart)
        assertEquals(5, res.selEnd)
        assertEquals("hello", res.text.substring(res.selStart, res.selEnd))
    }

    @Test
    fun toggleWrapRemovesMarkersWhenMarkersIncludedInSelection() {
        val t = EditorText("**hello** world", 0, 9)
        val res = toggleWrap(t, "**")
        assertEquals("hello world", res.text)
        assertEquals(0, res.selStart)
        assertEquals(5, res.selEnd)
        assertEquals("hello", res.text.substring(res.selStart, res.selEnd))
    }

    @Test
    fun toggleWrapWithEmptySelectionInsertsMarkersTwiceAndPlacesCursorBetween() {
        val t = EditorText("hello", 2, 2)
        val res = toggleWrap(t, "**")
        assertEquals("he****llo", res.text)
        assertEquals(4, res.selStart)
        assertEquals(4, res.selEnd)
    }

    @Test
    fun toggleWrapWorksWithVariousMarkers() {
        val tItalic = EditorText("note", 0, 4)
        assertEquals("*note*", toggleWrap(tItalic, "*").text)

        val tStrike = EditorText("note", 0, 4)
        assertEquals("~~note~~", toggleWrap(tStrike, "~~").text)

        val tCode = EditorText("note", 0, 4)
        assertEquals("`note`", toggleWrap(tCode, "`").text)
    }

    // -------------------------------------------------------------------------
    // toggleLinePrefix tests
    // -------------------------------------------------------------------------

    @Test
    fun toggleLinePrefixOnSingleLineAddsPrefix() {
        val t = EditorText("First line", 0, 5)
        val res = toggleLinePrefix(t, "- ")
        assertEquals("- First line", res.text)
    }

    @Test
    fun toggleLinePrefixOnSeveralLinesAddsPrefixToAllTouchedLines() {
        val t = EditorText("Apple\nBanana\nCherry", 0, 18)
        val res = toggleLinePrefix(t, "- ")
        assertEquals("- Apple\n- Banana\n- Cherry", res.text)
    }

    @Test
    fun toggleLinePrefixRemovesPrefixWhenAllTouchedLinesHaveIt() {
        val t = EditorText("- Apple\n- Banana", 0, 16)
        val res = toggleLinePrefix(t, "- ")
        assertEquals("Apple\nBanana", res.text)
    }

    @Test
    fun toggleLinePrefixReplacesDifferentHeadingPrefixInsteadOfStacking() {
        val t = EditorText("# Main Heading", 0, 14)
        val res = toggleLinePrefix(t, "## ")
        assertEquals("## Main Heading", res.text)

        val res3 = toggleLinePrefix(res, "### ")
        assertEquals("### Main Heading", res3.text)

        // Toggling same heading removes it
        val removed = toggleLinePrefix(res3, "### ")
        assertEquals("Main Heading", removed.text)
    }

    @Test
    fun toggleLinePrefixNumberedListsNumbersInOrderAndRemovesWhenAllNumbered() {
        val t = EditorText("One\nTwo\nThree", 0, 13)
        val res = toggleLinePrefix(t, "1. ")
        assertEquals("1. One\n2. Two\n3. Three", res.text)

        val removed = toggleLinePrefix(res, "1. ")
        assertEquals("One\nTwo\nThree", removed.text)
    }

    // -------------------------------------------------------------------------
    // insertLink tests
    // -------------------------------------------------------------------------

    @Test
    fun insertLinkWithSelectionWrapsInLinkAndPlacesCursorInParentheses() {
        val t = EditorText("Visit Google today", 6, 12)
        val res = insertLink(t)
        assertEquals("Visit [Google]() today", res.text)
        assertEquals(15, res.selStart)
        assertEquals(15, res.selEnd)
    }

    @Test
    fun insertLinkWithoutSelectionInsertsTemplateAndSelectsTextWord() {
        val t = EditorText("Start ", 6, 6)
        val res = insertLink(t)
        assertEquals("Start [text]()", res.text)
        assertEquals(7, res.selStart)
        assertEquals(11, res.selEnd)
        assertEquals("text", res.text.substring(res.selStart, res.selEnd))
    }

    // -------------------------------------------------------------------------
    // insertTable tests
    // -------------------------------------------------------------------------

    @Test
    fun insertTablePlacesTableOnOwnLines() {
        val expected = "| Column 1 | Column 2 |\n| --- | --- |\n|  |  |\n"

        // Empty document
        val resEmpty = insertTable(EditorText("", 0, 0))
        assertEquals(expected, resEmpty.text)

        // In between lines
        val resMiddle = insertTable(EditorText("Before\nAfter", 6, 6))
        assertTrue(resMiddle.text.startsWith("Before\n" + expected))
        assertTrue(resMiddle.text.endsWith("After"))
    }

    // -------------------------------------------------------------------------
    // selection clamping tests
    // -------------------------------------------------------------------------

    @Test
    fun selectionsAreClampedForOutOfRangeValues() {
        val outOfBounds = EditorText("Hello", -50, 100)

        val wrapRes = toggleWrap(outOfBounds, "**")
        assertEquals("**Hello**", wrapRes.text)
        assertTrue(wrapRes.selStart in 0..wrapRes.text.length)
        assertTrue(wrapRes.selEnd in 0..wrapRes.text.length)
        assertTrue(wrapRes.selStart <= wrapRes.selEnd)

        val inverted = EditorText("Hello", 4, 1)
        val prefixRes = toggleLinePrefix(inverted, "- ")
        assertEquals("- Hello", prefixRes.text)
        assertTrue(prefixRes.selStart in 0..prefixRes.text.length)
        assertTrue(prefixRes.selEnd in 0..prefixRes.text.length)
        assertTrue(prefixRes.selStart <= prefixRes.selEnd)

        val linkRes = insertLink(EditorText("Hello", 100, 200))
        assertTrue(linkRes.selStart in 0..linkRes.text.length)
        assertTrue(linkRes.selEnd in 0..linkRes.text.length)
        assertTrue(linkRes.selStart <= linkRes.selEnd)

        val tableRes = insertTable(EditorText("Hello", -10, -5))
        assertTrue(tableRes.selStart in 0..tableRes.text.length)
        assertTrue(tableRes.selEnd in 0..tableRes.text.length)
        assertTrue(tableRes.selStart <= tableRes.selEnd)
    }

    // -------------------------------------------------------------------------
    // continueListOnEnter tests
    // -------------------------------------------------------------------------

    @Test
    fun continueListOnEnterBulletContinuesSameBullet() {
        val before = EditorText("- Item one", 10, 10)
        val after = EditorText("- Item one\n", 11, 11)
        val res = continueListOnEnter(before, after)
        assertEquals("- Item one\n- ", res?.text)
        assertEquals(13, res?.selStart)
        assertEquals(13, res?.selEnd)
    }

    @Test
    fun continueListOnEnterNestedBulletKeepsIndentation() {
        val before = EditorText("    * Nested item", 17, 17)
        val after = EditorText("    * Nested item\n", 18, 18)
        val res = continueListOnEnter(before, after)
        assertEquals("    * Nested item\n    * ", res?.text)
        assertEquals(24, res?.selStart)
        assertEquals(24, res?.selEnd)
    }

    @Test
    fun continueListOnEnterNumberedIncrements() {
        val beforeDot = EditorText("1. Step one", 11, 11)
        val afterDot = EditorText("1. Step one\n", 12, 12)
        val resDot = continueListOnEnter(beforeDot, afterDot)
        assertEquals("1. Step one\n2. ", resDot?.text)
        assertEquals(15, resDot?.selStart)
        assertEquals(15, resDot?.selEnd)

        val beforeParen = EditorText("  9) Ninth item", 15, 15)
        val afterParen = EditorText("  9) Ninth item\n", 16, 16)
        val resParen = continueListOnEnter(beforeParen, afterParen)
        assertEquals("  9) Ninth item\n  10) ", resParen?.text)
        assertEquals(22, resParen?.selStart)
        assertEquals(22, resParen?.selEnd)
    }

    @Test
    fun continueListOnEnterChecklistContinuesUnchecked() {
        val before = EditorText("- [x] Done item", 15, 15)
        val after = EditorText("- [x] Done item\n", 16, 16)
        val res = continueListOnEnter(before, after)
        assertEquals("- [x] Done item\n- [ ] ", res?.text)
        assertEquals(22, res?.selStart)
        assertEquals(22, res?.selEnd)

        val beforeStar = EditorText("  * [X] Checked with star", 25, 25)
        val afterStar = EditorText("  * [X] Checked with star\n", 26, 26)
        val resStar = continueListOnEnter(beforeStar, afterStar)
        assertEquals("  * [X] Checked with star\n  - [ ] ", resStar?.text)
        assertEquals(34, resStar?.selStart)
        assertEquals(34, resStar?.selEnd)
    }

    @Test
    fun continueListOnEnterEmptyItemExitsTheList() {
        val before = EditorText("- Item\n- ", 9, 9)
        val after = EditorText("- Item\n- \n", 10, 10)
        val res = continueListOnEnter(before, after)
        assertEquals("- Item\n", res?.text)
        assertEquals(7, res?.selStart)
        assertEquals(7, res?.selEnd)

        val beforeNested = EditorText("  1. First\n  2. ", 16, 16)
        val afterNested = EditorText("  1. First\n  2. \n", 17, 17)
        val resNested = continueListOnEnter(beforeNested, afterNested)
        assertEquals("  1. First\n", resNested?.text)
        assertEquals(11, resNested?.selStart)
        assertEquals(11, resNested?.selEnd)
    }

    @Test
    fun continueListOnEnterOnPlainLineReturnsNull() {
        val before = EditorText("Plain text line", 15, 15)
        val after = EditorText("Plain text line\n", 16, 16)
        assertNull(continueListOnEnter(before, after))
    }

    @Test
    fun continueListOnEnterSelectionReplacedByEnterReturnsNull() {
        val before = EditorText("- List item", 2, 6)
        val after = EditorText("- item\n", 3, 3)
        assertNull(continueListOnEnter(before, after))
    }

    @Test
    fun continueListOnEnterPasteOfSeveralCharactersReturnsNull() {
        val before = EditorText("- List item", 11, 11)
        val after = EditorText("- List item\n\nMore text", 22, 22)
        assertNull(continueListOnEnter(before, after))
    }
}

