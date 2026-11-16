package com.example.ui

import androidx.compose.runtime.mutableStateOf
import com.example.ui.screens.SectionCollapse
import com.example.ui.screens.TaskSectionKey
import com.example.ui.screens.shownUnless
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SectionCollapseTest {

    private fun newCollapse(vararg collapsed: String) =
        SectionCollapse(mutableStateOf(collapsed.toList()))

    @Test
    fun everySectionIsOpenUnlessItStartsCollapsed() {
        val collapse = newCollapse(TaskSectionKey.DONE)
        assertTrue(collapse.isCollapsed(TaskSectionKey.DONE))
        assertFalse(collapse.isCollapsed(TaskSectionKey.LATER))
        assertFalse(collapse.isCollapsed(TaskSectionKey.TURNED_OFF))
    }

    @Test
    fun toggleCollapsesAndExpandsOnlyThatSection() {
        val collapse = newCollapse()

        collapse.toggle(TaskSectionKey.LATER)
        assertTrue(collapse.isCollapsed(TaskSectionKey.LATER))
        assertFalse(collapse.isCollapsed(TaskSectionKey.TURNED_OFF))

        collapse.toggle(TaskSectionKey.TURNED_OFF)
        assertTrue(collapse.isCollapsed(TaskSectionKey.LATER))
        assertTrue(collapse.isCollapsed(TaskSectionKey.TURNED_OFF))

        collapse.toggle(TaskSectionKey.LATER)
        assertFalse(collapse.isCollapsed(TaskSectionKey.LATER))
        assertTrue(collapse.isCollapsed(TaskSectionKey.TURNED_OFF))
        assertEquals(listOf(TaskSectionKey.TURNED_OFF), collapse.keys)
    }

    @Test
    fun collapsedSectionShowsNoTasks() {
        val tasks = listOf(1, 2, 3)
        assertEquals(emptyList<Int>(), tasks.shownUnless(collapsed = true))
        assertEquals(tasks, tasks.shownUnless(collapsed = false))
    }
}
