package com.example.ui.screens

import com.example.data.local.Course
import com.example.data.local.TaskItem
import com.example.data.local.TaskStatus
import com.example.data.local.TaskWithCourse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A task that is turned off is never overdue or due today. It sits in its own "Turned off" section. */
class TurnedOffSectionTest {

    private val now = 1_800_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    private fun task(
        id: Long,
        dueOffsetMillis: Long,
        enabled: Boolean = true,
        status: TaskStatus = TaskStatus.PENDING
    ) = TaskWithCourse(
        task = TaskItem(
            id = id,
            courseId = 1L,
            title = "Task $id",
            dueDateEpochMillis = now + dueOffsetMillis,
            status = status,
            isEnabled = enabled
        ),
        course = Course(id = 1L, name = "Maths", code = "M101")
    )

    @Test
    fun anOverdueTaskThatIsTurnedOffIsNotOverdue() {
        val sections = groupDeadlines(listOf(task(1, -3 * day, enabled = false)), now)
        assertTrue(sections.overdue.isEmpty())
        assertEquals(listOf(1L), sections.turnedOff.map { it.task.id })
    }

    @Test
    fun aTurnedOffTaskDoesNotCountAsDueToday() {
        val sections = groupDeadlines(listOf(task(1, 0L, enabled = false), task(2, 0L)), now)
        assertEquals(listOf(2L), sections.today.map { it.task.id })
        assertEquals(listOf(1L), sections.turnedOff.map { it.task.id })
    }

    @Test
    fun aFinishedTaskStaysInDoneEvenWhenTurnedOff() {
        val sections = groupDeadlines(listOf(task(1, -day, enabled = false, status = TaskStatus.COMPLETED)), now)
        assertEquals(listOf(1L), sections.done.map { it.task.id })
        assertTrue(sections.turnedOff.isEmpty())
    }

    @Test
    fun turnedOffTasksAreSortedByDate() {
        val sections = groupDeadlines(
            listOf(task(1, 5 * day, enabled = false), task(2, -2 * day, enabled = false)),
            now
        )
        assertEquals(listOf(2L, 1L), sections.turnedOff.map { it.task.id })
    }
}
