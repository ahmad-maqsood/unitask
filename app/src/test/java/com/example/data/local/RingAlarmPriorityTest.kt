package com.example.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Ring alarm" is saved as HIGH priority, so the database and cloud data do not change. */
class RingAlarmPriorityTest {

    private fun task(priority: Priority) = TaskItem(
        courseId = 1L,
        title = "Surah Yaseen",
        dueDateEpochMillis = 0L,
        priority = priority
    )

    @Test
    fun onlyHighPriorityTasksRingAnAlarm() {
        assertTrue(task(Priority.HIGH).ringsAlarm)
        assertFalse(task(Priority.MEDIUM).ringsAlarm)
        assertFalse(task(Priority.LOW).ringsAlarm)
    }

    @Test
    fun switchOnSavesHigh() {
        assertEquals(Priority.HIGH, priorityForRingAlarm(true, null))
        assertEquals(Priority.HIGH, priorityForRingAlarm(true, Priority.LOW))
        assertEquals(Priority.HIGH, priorityForRingAlarm(true, Priority.MEDIUM))
    }

    @Test
    fun switchOffSavesMediumForNewAndFormerAlarmTasks() {
        assertEquals(Priority.MEDIUM, priorityForRingAlarm(false, null))
        assertEquals(Priority.MEDIUM, priorityForRingAlarm(false, Priority.HIGH))
    }

    @Test
    fun switchOffKeepsAnUntouchedLowOrMedium() {
        assertEquals(Priority.LOW, priorityForRingAlarm(false, Priority.LOW))
        assertEquals(Priority.MEDIUM, priorityForRingAlarm(false, Priority.MEDIUM))
    }

    @Test
    fun groupTasksUseTheSameRuleWithTextNames() {
        assertEquals("HIGH", priorityNameForRingAlarm(true, "MEDIUM"))
        assertEquals("MEDIUM", priorityNameForRingAlarm(false, "HIGH"))
        assertEquals("LOW", priorityNameForRingAlarm(false, "LOW"))
        assertEquals("MEDIUM", priorityNameForRingAlarm(false, null))
        // A value this app does not know is treated like no value.
        assertEquals("MEDIUM", priorityNameForRingAlarm(false, "URGENT"))
    }
}
