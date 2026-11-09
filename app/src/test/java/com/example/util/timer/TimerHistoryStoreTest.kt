package com.example.util.timer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TimerHistoryStoreTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun session(start: Long, minutes: Long = 25, completed: Boolean = false) = TimerSession(
        startedAtMillis = start,
        durationMillis = minutes * 60_000L,
        mode = TimerMode.COUNTDOWN,
        plannedMillis = 25 * 60_000L,
        completed = completed
    )

    @Test
    fun startsEmpty() {
        assertTrue(TimerHistoryStore(context).sessions.value.isEmpty())
    }

    @Test
    fun savedSessionsComeBackNewestFirstAfterTheAppRestarts() {
        val store = TimerHistoryStore(context)
        store.add(session(1_000L))
        store.add(session(3_000L, completed = true))
        store.add(session(2_000L))

        val reopened = TimerHistoryStore(context).sessions.value
        assertEquals(listOf(3_000L, 2_000L, 1_000L), reopened.map { it.startedAtMillis })
        assertEquals(true, reopened.first().completed)
        assertEquals(TimerMode.COUNTDOWN, reopened.first().mode)
        assertEquals(25 * 60_000L, reopened.first().plannedMillis)
    }

    @Test
    fun stopwatchSessionKeepsNoGoal() {
        val store = TimerHistoryStore(context)
        store.add(
            TimerSession(
                startedAtMillis = 5_000L,
                durationMillis = 40_000L,
                mode = TimerMode.STOPWATCH,
                plannedMillis = null,
                completed = false
            )
        )
        assertNull(TimerHistoryStore(context).sessions.value.single().plannedMillis)
    }

    @Test
    fun addingTheSameStartTimeReplacesInsteadOfDuplicating() {
        val store = TimerHistoryStore(context)
        store.add(session(1_000L, minutes = 10))
        store.add(session(1_000L, minutes = 20))

        val sessions = store.sessions.value
        assertEquals(1, sessions.size)
        assertEquals(20 * 60_000L, sessions.single().durationMillis)
    }

    @Test
    fun onlyTheNewestSessionsAreKept() {
        val store = TimerHistoryStore(context)
        val total = TimerHistoryLogic.MAX_STORED_SESSIONS + 5
        for (i in 1..total) store.add(session(i * 1_000L))

        val sessions = TimerHistoryStore(context).sessions.value
        assertEquals(TimerHistoryLogic.MAX_STORED_SESSIONS, sessions.size)
        assertEquals(total * 1_000L, sessions.first().startedAtMillis)
        assertEquals(6_000L, sessions.last().startedAtMillis)
    }

    @Test
    fun deleteRemovesOnlyThatSession() {
        val store = TimerHistoryStore(context)
        store.add(session(1_000L))
        store.add(session(2_000L))
        store.delete(1_000L)

        assertEquals(listOf(2_000L), TimerHistoryStore(context).sessions.value.map { it.startedAtMillis })
    }

    @Test
    fun clearRemovesEverything() {
        val store = TimerHistoryStore(context)
        store.add(session(1_000L))
        store.add(session(2_000L))
        store.clear()

        assertTrue(store.sessions.value.isEmpty())
        assertTrue(TimerHistoryStore(context).sessions.value.isEmpty())
    }

    @Test
    fun damagedSavedDataIsIgnoredInsteadOfCrashing() {
        val prefs = context.getSharedPreferences("tools_timer_history", Context.MODE_PRIVATE)

        prefs.edit().putString("sessions", "this is not json").commit()
        assertTrue(TimerHistoryStore(context).sessions.value.isEmpty())

        // One good entry and one broken entry: the good one survives.
        prefs.edit().putString(
            "sessions",
            """[{"s":5000,"d":120000,"m":"STOPWATCH","c":false},{"oops":1}]"""
        ).commit()
        val sessions = TimerHistoryStore(context).sessions.value
        assertEquals(1, sessions.size)
        assertEquals(5_000L, sessions.single().startedAtMillis)
    }
}
