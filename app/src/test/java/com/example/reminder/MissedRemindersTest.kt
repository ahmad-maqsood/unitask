package com.example.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MissedRemindersTest {

    private val hour = 60 * 60 * 1000L
    private val now = 1_000_000_000_000L

    @Test
    fun pastReminderInsideWindow_isMissed() {
        val keys = setOf(MissedReminders.keyFor(7L, "ONE_HOUR_BEFORE", now - 2 * hour))
        val missed = MissedReminders.pickMissed(keys, now)
        assertEquals(1, missed.size)
        assertEquals(7L, missed[0].taskId)
    }

    @Test
    fun futureReminder_isNotMissed() {
        val keys = setOf(MissedReminders.keyFor(7L, "AT_DEADLINE", now + hour))
        assertTrue(MissedReminders.pickMissed(keys, now).isEmpty())
    }

    @Test
    fun reminderOlderThanWindow_isNotMissed() {
        val keys = setOf(MissedReminders.keyFor(7L, "AT_DEADLINE", now - 13 * hour))
        assertTrue(MissedReminders.pickMissed(keys, now).isEmpty())
    }

    @Test
    fun brokenKeys_areIgnored() {
        assertTrue(MissedReminders.pickMissed(setOf("garbage", "1|2", "x|A|5"), now).isEmpty())
    }

    @Test
    fun missedReminders_comeOldestFirst() {
        val keys = setOf(
            MissedReminders.keyFor(2L, "AT_DEADLINE", now - hour),
            MissedReminders.keyFor(1L, "ONE_DAY_BEFORE", now - 5 * hour)
        )
        assertEquals(listOf(1L, 2L), MissedReminders.pickMissed(keys, now).map { it.taskId })
    }
}
