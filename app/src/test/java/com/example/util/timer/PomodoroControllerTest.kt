package com.example.util.timer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
class PomodoroControllerTest {

    private class RecordingAlarmHook : CountdownAlarmHook {
        val scheduledEnds = mutableListOf<Long>()
        override fun schedule(endAtMillis: Long) {
            scheduledEnds.add(endAtMillis)
        }

        override fun cancel() {}
    }

    private class RecordingHistory : TimerHistoryHook {
        private val flow = MutableStateFlow<List<TimerSession>>(emptyList())
        override val sessions: StateFlow<List<TimerSession>> get() = flow

        override fun add(session: TimerSession) {
            flow.value = listOf(session) + flow.value
        }

        override fun addAll(sessions: List<TimerSession>): Int = 0
        override fun delete(startedAtMillis: Long) {}
        override fun clear() {
            flow.value = emptyList()
        }
    }

    private val minute = 60_000L

    private fun newController(
        clock: () -> Long,
        alarm: CountdownAlarmHook = NoCountdownAlarm,
        history: TimerHistoryHook = NoTimerHistory
    ): TimerController {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = TimerStore(context)
        store.save(TimerState())
        return TimerController(store = store, clock = clock, alarm = alarm, history = history)
    }

    @Test
    fun enablingPomodoroGivesAFocusCountdown() {
        val controller = newController(clock = { 0L })
        controller.enablePomodoro()
        val s = controller.state.value
        assertEquals(PomodoroPhase.FOCUS, s.pomodoroPhase)
        assertEquals(TimerMode.COUNTDOWN, s.mode)
        assertEquals(25 * minute, s.countdownTotalMillis)
    }

    @Test
    fun startingAfterAFinishedPhaseBeginsTheNextOneAndBreaksAreNotSavedAsStudy() {
        var now = 1_000L
        val alarm = RecordingAlarmHook()
        val history = RecordingHistory()
        val controller = newController(clock = { now }, alarm = alarm, history = history)

        controller.enablePomodoro()
        controller.start()
        assertEquals(listOf(1_000L + 25 * minute), alarm.scheduledEnds)

        // The focus phase runs out: one study session is saved.
        now = 1_000L + 25 * minute
        assertTrue(controller.markFinishedIfDue())
        assertEquals(1, history.sessions.value.size)
        assertTrue(history.sessions.value.single().completed)

        // Start moves on to the short break and schedules its alarm.
        controller.start()
        var s = controller.state.value
        assertEquals(PomodoroPhase.SHORT_BREAK, s.pomodoroPhase)
        assertEquals(5 * minute, s.countdownTotalMillis)
        assertTrue(TimerLogic.isRunning(s))
        assertEquals(now + 5 * minute, alarm.scheduledEnds.last())

        // The break ends: nothing new is saved to the study history.
        now += 5 * minute
        assertTrue(controller.markFinishedIfDue())
        assertEquals(1, history.sessions.value.size)

        // Start moves on to focus round 2.
        controller.start()
        s = controller.state.value
        assertEquals(PomodoroPhase.FOCUS, s.pomodoroPhase)
        assertEquals(2, s.pomodoroRound)
    }

    @Test
    fun theLongBreakComesAfterTheLastRoundAndTheCycleRestarts() {
        var now = 0L
        val controller = newController(clock = { now })
        controller.setPomodoroSettings(PomodoroSettings(1, 1, 2, 2))
        controller.enablePomodoro()

        fun finishPhaseAndStartNext() {
            controller.start()
            now += controller.state.value.countdownTotalMillis
            assertTrue(controller.markFinishedIfDue())
        }

        finishPhaseAndStartNext() // focus 1 done
        finishPhaseAndStartNext() // short break done (start() moved on to it)
        assertEquals(PomodoroPhase.SHORT_BREAK, controller.state.value.pomodoroPhase)
        finishPhaseAndStartNext() // focus 2 done
        assertEquals(PomodoroPhase.FOCUS, controller.state.value.pomodoroPhase)
        assertEquals(2, controller.state.value.pomodoroRound)
        finishPhaseAndStartNext() // long break done
        assertEquals(PomodoroPhase.LONG_BREAK, controller.state.value.pomodoroPhase)
        assertEquals(2 * minute, controller.state.value.countdownTotalMillis)
        controller.start()
        assertEquals(PomodoroPhase.FOCUS, controller.state.value.pomodoroPhase)
        assertEquals(1, controller.state.value.pomodoroRound)
    }

    @Test
    fun skippingASessionInProgressSavesTheTimeStudiedAndMovesToTheBreak() {
        var now = 0L
        val history = RecordingHistory()
        val controller = newController(clock = { now }, history = history)
        controller.enablePomodoro()
        controller.start()
        now = 30_000L

        controller.skipPomodoroPhase()

        val s = controller.state.value
        assertEquals(PomodoroPhase.SHORT_BREAK, s.pomodoroPhase)
        assertTrue(TimerLogic.isIdle(s))
        val saved = history.sessions.value.single()
        assertEquals(30_000L, saved.durationMillis)
        assertFalse(saved.completed)
    }

    @Test
    fun skippingABreakIsNotSavedAsStudy() {
        var now = 0L
        val history = RecordingHistory()
        val controller = newController(clock = { now }, history = history)
        controller.enablePomodoro()
        controller.skipPomodoroPhase() // idle focus -> short break
        controller.start()
        now = 60_000L

        controller.skipPomodoroPhase() // break -> focus round 2

        assertEquals(PomodoroPhase.FOCUS, controller.state.value.pomodoroPhase)
        assertEquals(2, controller.state.value.pomodoroRound)
        assertTrue(history.sessions.value.isEmpty())
    }

    @Test
    fun leavingPomodoroRestoresTheCountdownLengthTheUserChoseBefore() {
        val controller = newController(clock = { 0L })
        controller.setMode(TimerMode.COUNTDOWN)
        controller.setCountdownMinutes(45)
        controller.enablePomodoro()
        assertEquals(25 * minute, controller.state.value.countdownTotalMillis)

        controller.setMode(TimerMode.COUNTDOWN)

        val s = controller.state.value
        assertNull(s.pomodoroPhase)
        assertEquals(TimerMode.COUNTDOWN, s.mode)
        assertEquals(45 * minute, s.countdownTotalMillis)
    }

    @Test
    fun leavingPomodoroForTheStopwatchTurnsItOff() {
        val controller = newController(clock = { 0L })
        controller.enablePomodoro()
        controller.setMode(TimerMode.STOPWATCH)
        assertNull(controller.state.value.pomodoroPhase)
        assertEquals(TimerMode.STOPWATCH, controller.state.value.mode)
    }

    @Test
    fun presetLengthsAreIgnoredWhilePomodoroIsOn() {
        val controller = newController(clock = { 0L })
        controller.enablePomodoro()
        controller.setCountdownMinutes(10)
        assertEquals(25 * minute, controller.state.value.countdownTotalMillis)
    }

    @Test
    fun pomodoroPhaseAndRoundSurviveANewControllerOverTheSameStore() {
        var now = 0L
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = TimerStore(context)
        val first = TimerController(store = store, clock = { now })
        first.enablePomodoro()
        first.skipPomodoroPhase() // short break, round 1
        first.skipPomodoroPhase() // focus, round 2

        val second = TimerController(store = store, clock = { now })
        val s = second.state.value
        assertEquals(PomodoroPhase.FOCUS, s.pomodoroPhase)
        assertEquals(2, s.pomodoroRound)
    }

    @Test
    fun newLengthsApplyToAnIdleTimerRightAway() {
        val controller = newController(clock = { 0L })
        controller.enablePomodoro()
        controller.setPomodoroSettings(PomodoroSettings(50, 10, 20, 3))
        assertEquals(50 * minute, controller.state.value.countdownTotalMillis)
        assertEquals(3, controller.pomodoroSettings().roundsBeforeLongBreak)
    }
}
