package com.example.data.groups

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MergePendingTasksTest {

    private val now = 1_000_000L
    private val maxAge = 60_000L

    private fun task(id: String, title: String = "Essay", group: String = "g1", createdAt: Long = now) = GroupTask(
        id = id,
        groupId = group,
        title = title,
        dueDateEpochMillis = 5_000L,
        createdBy = "me",
        createdAt = Timestamp(createdAt)
    )

    @Test
    fun newTaskShowsAtOnceBeforeTheServerHasIt() {
        val result = mergePendingTasks("g1", emptyList(), listOf(task("a")), now, maxAge)
        assertEquals(listOf("a"), result.map { it.id })
    }

    @Test
    fun taskIsNotShownTwiceOnceTheServerHasIt() {
        val result = mergePendingTasks("g1", listOf(task("a")), listOf(task("a")), now, maxAge)
        assertEquals(listOf("a"), result.map { it.id })
    }

    @Test
    fun twoTasksWithTheSameTitleAndDateAreBothKept() {
        // The old matching by title and due date hid the second one as if it were the first.
        val server = listOf(task("a"))
        val pending = listOf(task("b"))
        val result = mergePendingTasks("g1", server, pending, now, maxAge)
        assertEquals(listOf("a", "b"), result.map { it.id })
    }

    @Test
    fun tasksOfOtherGroupsAreIgnored() {
        val result = mergePendingTasks("g1", emptyList(), listOf(task("a", group = "g2")), now, maxAge)
        assertTrue(result.isEmpty())
    }

    @Test
    fun aTaskTheServerNeverConfirmedStopsShowingAfterTheMaxAge() {
        val old = task("a", createdAt = now - maxAge - 1)
        val result = mergePendingTasks("g1", emptyList(), listOf(old), now, maxAge)
        assertTrue(result.isEmpty())
    }

    @Test
    fun deletedTaskDoesNotComeBackFromThePendingList() {
        // Delete removes the task from the pending list at once, so only the server copy can remain.
        val result = mergePendingTasks("g1", emptyList(), emptyList(), now, maxAge)
        assertTrue(result.isEmpty())
    }
}
