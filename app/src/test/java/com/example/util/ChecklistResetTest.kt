package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ChecklistResetTest {

    @Test
    fun untickAll_resetsEveryTickedItem() {
        val before = "Notes\n- [x] Mulk\n- [X] Assajdah\n- [ ] Third"
        val after = "Notes\n- [ ] Mulk\n- [ ] Assajdah\n- [ ] Third"
        assertEquals(after, uncheckAllChecklistItems(before))
    }

    @Test
    fun untickAll_leavesTextWithoutChecklistAlone() {
        val text = "Just a note\nwith two lines"
        assertEquals(text, uncheckAllChecklistItems(text))
        assertEquals("", uncheckAllChecklistItems(""))
    }

    @Test
    fun untickAll_keepsIndentAndWindowsLineEndings() {
        val before = "Notes\r\n  - [x] Indented\r\n- [x] Plain"
        val after = "Notes\r\n  - [ ] Indented\r\n- [ ] Plain"
        assertEquals(after, uncheckAllChecklistItems(before))
    }
}
