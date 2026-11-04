package com.example.reminder

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.screens.snoozeChoices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SnoozeTest {

    // A getter, so the app context is read when a test runs, not when the class is created.
    private val context: Context
        get() = ApplicationProvider.getApplicationContext<Context>()
    private val hour = 60 * 60_000L

    private fun entry(key: Long, until: Long, groupId: String = "", groupTaskId: String = "") =
        SnoozeStore.Entry(
            key = key,
            until = until,
            taskId = if (key > 0) key else 0L,
            title = "Lab report",
            courseCode = "CS201",
            dueInfo = "Due Fri, Nov 7 at 5:00 PM",
            priority = "MEDIUM",
            groupId = groupId,
            groupTaskId = groupTaskId
        )

    private fun savedKeys() = SnoozeStore.entries(context).map { it.key }.toSet()

    @Test
    fun aSnoozeIsRememberedAndShownToTheCards() {
        val until = System.currentTimeMillis() + hour
        SnoozeStore.set(context, entry(5L, until))
        assertEquals(until, SnoozeStore.untilByTask(context).value[5L])
        assertEquals(setOf(5L), savedKeys())
    }

    @Test
    fun clearRemovesIt() {
        SnoozeStore.set(context, entry(5L, System.currentTimeMillis() + hour))
        SnoozeStore.clear(context, 5L, cancelAlarm = true)
        assertTrue(savedKeys().isEmpty())
        assertFalse(SnoozeStore.untilByTask(context).value.containsKey(5L))
    }

    @Test
    fun clearIfDueKeepsASnoozeThatIsStillInTheFuture() {
        val now = System.currentTimeMillis()
        SnoozeStore.set(context, entry(5L, now + hour))
        SnoozeStore.set(context, entry(6L, now - 1_000L))
        SnoozeStore.clearIfDue(context, 5L)
        SnoozeStore.clearIfDue(context, 6L)
        assertEquals(setOf(5L), savedKeys())
    }

    @Test
    fun groupSnoozesGoWithTheirGroupTaskOrGroup() {
        val later = System.currentTimeMillis() + hour
        SnoozeStore.set(context, entry(-1L, later, "g1", "t1"))
        SnoozeStore.set(context, entry(-2L, later, "g1", "t2"))
        SnoozeStore.set(context, entry(-3L, later, "g2", "t1"))
        SnoozeStore.set(context, entry(7L, later))

        SnoozeStore.clearGroupTask(context, "g1", "t1")
        assertEquals(setOf(-2L, -3L, 7L), savedKeys())

        SnoozeStore.clearGroup(context, "g1")
        assertEquals(setOf(-3L, 7L), savedKeys())

        // Signing out: every group snooze goes, a personal one stays.
        SnoozeStore.clearGroupsNotIn(context, emptySet())
        assertEquals(setOf(7L), savedKeys())
    }

    @Test
    fun restoreDropsVeryOldSnoozesAndKeepsTheRest() {
        val now = System.currentTimeMillis()
        SnoozeStore.set(context, entry(5L, now - 13 * hour))
        SnoozeStore.set(context, entry(6L, now + hour))
        SnoozeStore.restoreAll(context)
        assertEquals(setOf(6L), savedKeys())
        assertEquals(setOf(6L), SnoozeStore.untilByTask(context).value.keys)
    }

    @Test
    fun choicesStopBeforeTheDeadline() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 3, 14, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // Due in 2 hours: only 30 minutes and 1 hour fit before it.
        assertEquals(listOf("30 minutes", "1 hour"), snoozeChoices(now, now + 2 * hour).map { it.label })
        // Due in 10 minutes: nothing fits.
        assertTrue(snoozeChoices(now, now + 10 * 60_000L).isEmpty())
        // Already overdue: every choice is offered.
        assertEquals(4, snoozeChoices(now, now - hour).size)
    }

    @Test
    fun tomorrowMorningIsTheNextDayAtEight() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 3, 14, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val tomorrow = Calendar.getInstance().apply {
            timeInMillis = snoozeChoices(now, now - hour).last().atMillis
        }
        assertEquals(4, tomorrow.get(Calendar.DAY_OF_MONTH))
        assertEquals(8, tomorrow.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, tomorrow.get(Calendar.MINUTE))
    }
}
