package com.example.util.timer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TimerControllerHistoryTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private var now = 1_000_000L

    private fun controller(
        store: TimerStore = TimerStore(context),
        history: TimerHistoryStore = TimerHistoryStore(context)
    ) = TimerController(store = store, clock = { now }, history = history)

    @Test
    fun stopwatchRunIsSavedOnResetAndPausesAreNotCounted() {
        val c = controller()
        c.setMode(TimerMode.STOPWATCH)

        c.start()               // t = 1_000_000
        now += 30_000L
        c.pause()               // 30 s counted
        now += 70_000L          // 70 s paused, must not count
        c.start()               // resume, still the same session
        now += 20_000L
        c.reset()               // 20 s more

        val session = c.sessions.value.single()
        assertEquals(1_000_000L, session.startedAtMillis)
        assertEquals(50_000L, session.durationMillis)
        assertEquals(TimerMode.STOPWATCH, session.mode)
        assertNull(session.plannedMillis)
        assertFalse(session.completed)
    }

    @Test
    fun pausingAloneDoesNotSaveAnything() {
        val c = controller()
        c.start()
        now += 60_000L
        c.pause()

        assertTrue(c.sessions.value.isEmpty())
    }

    @Test
    fun veryShortRunsAreIgnored() {
        val c = controller()
        c.start()
        now += 5_000L
        c.reset()

        assertTrue(c.sessions.value.isEmpty())
    }

    @Test
    fun countdownThatFinishesIsSavedOnceAsCompleted() {
        val c = controller()
        c.setMode(TimerMode.COUNTDOWN)
        c.setCountdownMinutes(1)
        c.start()
        now += 60_000L

        assertTrue(c.markFinishedIfDue())
        // Pressing Reset afterwards must not save the same run a second time.
        c.reset()

        val session = c.sessions.value.single()
        assertEquals(60_000L, session.durationMillis)
        assertEquals(60_000L, session.plannedMillis)
        assertEquals(TimerMode.COUNTDOWN, session.mode)
        assertTrue(session.completed)
    }

    @Test
    fun countdownStoppedEarlyIsSavedWithTimeActuallyStudied() {
        val c = controller()
        c.setMode(TimerMode.COUNTDOWN)
        c.setCountdownMinutes(5)
        c.start()
        now += 90_000L
        c.reset()

        val session = c.sessions.value.single()
        assertEquals(90_000L, session.durationMillis)
        assertEquals(5 * 60_000L, session.plannedMillis)
        assertFalse(session.completed)
    }

    @Test
    fun runStillCountsAsOneSessionAfterTheAppIsKilledAndReopened() {
        val store = TimerStore(context)
        val first = controller(store = store)
        first.start()                       // session starts at 1_000_000
        now += 40_000L
        first.pause()

        // The app process dies; a new controller picks up from the saved state.
        now += 3_600_000L
        val second = controller(store = TimerStore(context), history = TimerHistoryStore(context))
        second.start()
        now += 20_000L
        second.reset()

        val session = second.sessions.value.single()
        assertEquals(1_000_000L, session.startedAtMillis)
        assertEquals(60_000L, session.durationMillis)
    }

    @Test
    fun twoSeparateRunsAreTwoSessions() {
        val c = controller()
        c.start()
        now += 30_000L
        c.reset()

        now += 10_000L
        c.start()
        now += 45_000L
        c.reset()

        val sessions = c.sessions.value
        assertEquals(2, sessions.size)
        assertEquals(listOf(45_000L, 30_000L), sessions.map { it.durationMillis })
    }

    @Test
    fun deleteAndClearGoThroughTheController() {
        val c = controller()
        c.start()
        now += 30_000L
        c.reset()
        now += 1_000L
        c.start()
        now += 30_000L
        c.reset()
        assertEquals(2, c.sessions.value.size)

        c.deleteSession(c.sessions.value.first().startedAtMillis)
        assertEquals(1, c.sessions.value.size)

        c.clearHistory()
        assertTrue(c.sessions.value.isEmpty())
    }
}
