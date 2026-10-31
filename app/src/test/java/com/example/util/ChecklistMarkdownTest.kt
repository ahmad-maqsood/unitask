package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistMarkdownTest {

    @Test
    fun testParseChecklist_descriptionWithNoChecklist() {
        val desc = "This is a regular description.\nNo checklists here!\nJust plain text."
        val items = parseChecklist(desc)
        assertTrue(items.isEmpty())
    }

    @Test
    fun testParseChecklist_emptyDescription() {
        assertTrue(parseChecklist("").isEmpty())
    }

    @Test
    fun testParseChecklist_mixedLinesAndIndentationAndUppercaseX() {
        val desc = """
            Header note
            - [ ] First uncompleted
              - [x] Indented completed
            Random paragraph
            	- [X] Tab-indented uppercase completed
            - [ ] Another item
            Trailing note
        """.trimIndent()

        val items = parseChecklist(desc)
        assertEquals(4, items.size)

        assertEquals(1, items[0].lineIndex)
        assertEquals("First uncompleted", items[0].text)
        assertFalse(items[0].checked)

        assertEquals(2, items[1].lineIndex)
        assertEquals("Indented completed", items[1].text)
        assertTrue(items[1].checked)

        assertEquals(4, items[2].lineIndex)
        assertEquals("Tab-indented uppercase completed", items[2].text)
        assertTrue(items[2].checked)

        assertEquals(5, items[3].lineIndex)
        assertEquals("Another item", items[3].text)
        assertFalse(items[3].checked)
    }

    @Test
    fun testParseChecklist_crlfText() {
        val desc = "Line 1\r\n- [ ] Task A\r\n- [x] Task B\r\nLine 4"
        val items = parseChecklist(desc)
        assertEquals(2, items.size)
        assertEquals(1, items[0].lineIndex)
        assertEquals("Task A", items[0].text)
        assertFalse(items[0].checked)
        assertEquals(2, items[1].lineIndex)
        assertEquals("Task B", items[1].text)
        assertTrue(items[1].checked)
    }

    @Test
    fun testToggleChecklistLine_toggleUncheckedToChecked() {
        val desc = "Intro\n- [ ] Buy groceries\nEnd"
        val toggled = toggleChecklistLine(desc, 1)
        val expected = "Intro\n- [x] Buy groceries\nEnd"
        assertEquals(expected, toggled)
    }

    @Test
    fun testToggleChecklistLine_toggleCheckedToUnchecked() {
        val desc = "Intro\n- [x] Buy groceries\nEnd"
        val toggled = toggleChecklistLine(desc, 1)
        val expected = "Intro\n- [ ] Buy groceries\nEnd"
        assertEquals(expected, toggled)
    }

    @Test
    fun testToggleChecklistLine_uppercaseXPreservesToggle() {
        val desc = "Intro\n- [X] Do homework\nEnd"
        val toggled = toggleChecklistLine(desc, 1)
        val expected = "Intro\n- [ ] Do homework\nEnd"
        assertEquals(expected, toggled)
    }

    @Test
    fun testToggleChecklistLine_preservesIndentationAndText() {
        val desc = "  - [ ]   Nested with spaces\n\t- [x] Tab indented"
        val toggled0 = toggleChecklistLine(desc, 0)
        assertEquals("  - [x]   Nested with spaces\n\t- [x] Tab indented", toggled0)

        val toggled1 = toggleChecklistLine(desc, 1)
        assertEquals("  - [ ]   Nested with spaces\n\t- [ ] Tab indented", toggled1)
    }

    @Test
    fun testToggleChecklistLine_crlfTextPreserved() {
        val desc = "Line 1\r\n- [ ] Task A\r\n- [x] Task B\r\nLine 4"
        val toggled = toggleChecklistLine(desc, 1)
        val expected = "Line 1\r\n- [x] Task A\r\n- [x] Task B\r\nLine 4"
        assertEquals(expected, toggled)
        assertTrue(toggled.contains("\r\n"))

        val toggled2 = toggleChecklistLine(toggled, 2)
        val expected2 = "Line 1\r\n- [x] Task A\r\n- [ ] Task B\r\nLine 4"
        assertEquals(expected2, toggled2)
    }

    @Test
    fun testToggleChecklistLine_invalidIndex() {
        val desc = "- [ ] Item 1\n- [ ] Item 2"
        assertEquals(desc, toggleChecklistLine(desc, -1))
        assertEquals(desc, toggleChecklistLine(desc, 5))
        assertEquals(desc, toggleChecklistLine(desc, 2))
    }

    @Test
    fun testToggleChecklistLine_nonChecklistLineUnchanged() {
        val desc = "Regular line 0\n- [ ] Checklist line 1\nRegular line 2"
        assertEquals(desc, toggleChecklistLine(desc, 0))
        assertEquals(desc, toggleChecklistLine(desc, 2))
    }

    @Test
    fun testToggleChecklistLine_emptyDescription() {
        assertEquals("", toggleChecklistLine("", 0))
    }
}

