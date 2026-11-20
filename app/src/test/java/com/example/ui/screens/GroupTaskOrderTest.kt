package com.example.ui.screens

import com.example.data.groups.GroupTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class GroupTaskOrderTest {

    private fun task(id: String) = GroupTask(id = id, title = id)

    /** Already sorted soonest first, as GroupDetailScreen passes them. */
    private val byDeadline = listOf(task("a"), task("b"), task("c"), task("d"))

    private fun ids(list: List<GroupTask>) = list.map { it.id }

    @Test
    fun nothingDoneKeepsTheDeadlineOrder() {
        assertSame(byDeadline, orderGroupTasks(byDeadline, emptySet()))
    }

    @Test
    fun finishedTasksGoToTheBottom() {
        assertEquals(listOf("a", "c", "d", "b"), ids(orderGroupTasks(byDeadline, setOf("b"))))
    }

    @Test
    fun openTasksKeepTheirOrderAndFinishedOnesAreNewestDeadlineFirst() {
        assertEquals(listOf("b", "d", "c", "a"), ids(orderGroupTasks(byDeadline, setOf("a", "c"))))
    }

    @Test
    fun everythingDoneIsNewestDeadlineFirst() {
        assertEquals(listOf("d", "c", "b", "a"), ids(orderGroupTasks(byDeadline, setOf("a", "b", "c", "d"))))
    }

    @Test
    fun doneIdsOfOtherTasksAreIgnored() {
        assertEquals(ids(byDeadline), ids(orderGroupTasks(byDeadline, setOf("zzz"))))
    }
}
