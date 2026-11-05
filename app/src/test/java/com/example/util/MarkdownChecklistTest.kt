package com.example.util

import com.example.util.markdown.toggleCheckboxAtLine
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownChecklistTest {

    @Test
    fun toggleUncheckedToCheckedAndBackWithDash() {
        val original = "- [ ] Task item"
        val checked = toggleCheckboxAtLine(original, 0)
        assertEquals("- [x] Task item", checked)

        val unchecked = toggleCheckboxAtLine(checked, 0)
        assertEquals("- [ ] Task item", unchecked)
    }

    @Test
    fun toggleUncheckedToCheckedAndBackWithAsterisk() {
        val original = "* [ ] Star task"
        val checked = toggleCheckboxAtLine(original, 0)
        assertEquals("* [x] Star task", checked)

        val unchecked = toggleCheckboxAtLine(checked, 0)
        assertEquals("* [ ] Star task", unchecked)
    }

    @Test
    fun toggleUncheckedToCheckedAndBackWithPlus() {
        val original = "+ [ ] Plus task"
        val checked = toggleCheckboxAtLine(original, 0)
        assertEquals("+ [x] Plus task", checked)

        val unchecked = toggleCheckboxAtLine(checked, 0)
        assertEquals("+ [ ] Plus task", unchecked)
    }

    @Test
    fun toggleCapitalXBecomesUnchecked() {
        val original = "- [X] Done with capital X"
        val toggled = toggleCheckboxAtLine(original, 0)
        assertEquals("- [ ] Done with capital X", toggled)
    }

    @Test
    fun onlyTargetLineChangesInMultiLineDocument() {
        val doc = listOf(
            "# Heading",
            "",
            "- [ ] First item",
            "- [x] Second item",
            "- [ ] Third item",
            "",
            "Paragraph footer"
        ).joinToString("\n")

        val toggled = toggleCheckboxAtLine(doc, 3)

        val expected = listOf(
            "# Heading",
            "",
            "- [ ] First item",
            "- [ ] Second item",
            "- [ ] Third item",
            "",
            "Paragraph footer"
        ).joinToString("\n")

        assertEquals(expected, toggled)
    }

    @Test
    fun preservesIndentationOfTargetLine() {
        val doc = "    - [ ] Indented 4 spaces"
        val toggled = toggleCheckboxAtLine(doc, 0)
        assertEquals("    - [x] Indented 4 spaces", toggled)
    }

    @Test
    fun preservesTabIndentation() {
        val doc = "\t* [ ] Tab indented"
        val toggled = toggleCheckboxAtLine(doc, 0)
        assertEquals("\t* [x] Tab indented", toggled)
    }

    @Test
    fun outOfRangeNegativeIndexChangesNothing() {
        val doc = "- [ ] Task"
        val result = toggleCheckboxAtLine(doc, -1)
        assertEquals(doc, result)
    }

    @Test
    fun outOfRangePositiveIndexChangesNothing() {
        val doc = "- [ ] Task"
        val result = toggleCheckboxAtLine(doc, 5)
        assertEquals(doc, result)
    }

    @Test
    fun nonChecklistLineChangesNothing() {
        val doc = listOf(
            "# Heading",
            "Plain paragraph text",
            "- Bullet item without checkbox"
        ).joinToString("\n")

        val result = toggleCheckboxAtLine(doc, 1)
        assertEquals(doc, result)
    }

    @Test
    fun emptyStringReturnsEmptyString() {
        val result = toggleCheckboxAtLine("", 0)
        assertEquals("", result)
    }

    @Test
    fun preservesCrlfLineEndings() {
        val doc = "Line 1\r\n- [ ] Task\r\nLine 3"
        val toggled = toggleCheckboxAtLine(doc, 1)
        assertEquals("Line 1\r\n- [x] Task\r\nLine 3", toggled)
    }
}

