package com.example.reminder

import org.junit.Assert.assertEquals
import org.junit.Test

class GroupLateRemindersTest {

    private val now = 1_000_000_000L
    private val minute = 60_000L
    private val hour = 60 * minute

    /** Same shape the scheduler saves: trigger, title, group, due text, label, priority. */
    private fun value(trigger: Long) = listOf(trigger.toString(), "Task", "Group", "Due", "", "").joinToString("\u001F")

    @Test
    fun aReminderOverdueByMoreThanTheGraceIsPicked() {
        val waiting = mapOf("g|t|TWO_HOURS_BEFORE" to value(now - 10 * minute))
        assertEquals(listOf("g|t|TWO_HOURS_BEFORE"), GroupReminderScheduler.pickLate(waiting, now))
    }

    @Test
    fun aReminderOnlyJustPastIsLeftToItsRealAlarm() {
        val waiting = mapOf("g|t|AT_DEADLINE" to value(now - 2 * minute))
        assertEquals(emptyList<String>(), GroupReminderScheduler.pickLate(waiting, now))
    }

    @Test
    fun aFutureReminderIsNotPicked() {
        val waiting = mapOf("g|t|AT_DEADLINE" to value(now + hour))
        assertEquals(emptyList<String>(), GroupReminderScheduler.pickLate(waiting, now))
    }

    @Test
    fun aReminderOlderThanTheWindowIsNotPicked() {
        val waiting = mapOf("g|t|ONE_DAY_BEFORE" to value(now - 13 * hour))
        assertEquals(emptyList<String>(), GroupReminderScheduler.pickLate(waiting, now))
    }

    @Test
    fun lateRemindersComeOldestFirstAndBrokenValuesAreIgnored() {
        val waiting = mapOf(
            "g|b|AT_DEADLINE" to value(now - 20 * minute),
            "g|a|AT_DEADLINE" to value(now - 3 * hour),
            "g|c|AT_DEADLINE" to "not a number"
        )
        assertEquals(listOf("g|a|AT_DEADLINE", "g|b|AT_DEADLINE"), GroupReminderScheduler.pickLate(waiting, now))
    }
}
