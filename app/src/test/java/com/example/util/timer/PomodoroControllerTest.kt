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

    private class RecordingAutoStartAlarm : AutoStartAlarmHook {
        val scheduled = mutableListOf<Long>()
        var cancels = 0
        override fun schedule(triggerAtMillis: Long) {
            scheduled.add(triggerAtMillis)
        }

        override fun cancel() {
            cancels++
        }
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
        history: TimerHistoryHook = NoTimerHistory,
        autoStartAlarm: AutoStartAlarmHook = NoAutoStartAlarm
    ): TimerController {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = TimerStore(context)
        store.save(TimerState())
        store.setAutoStartAtMillis(null)
        store.setPomodoroAlertSettings(PomodoroAlertSettings())
        return TimerController(
            store = store,
            clock = clock,
            alarm = alarm,
            history = history,
            autoStartAlarm = autoStartAlarm
        )
    }

    /** Runs a focus round to its end, ready for the auto-start tests. */
    private fun finishFocusRound(controller: TimerController, setNow: (Long) -> Unit, startAt: Long) {
        setNow(startAt)
        controller.enablePomodoro()
        controller.start()
        setNow(startAt + 25 * minute)
        assertTrue(controller.markFinishedIfDue())
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

    // ---- Auto-start of the next round and the alert options ----

    @Test
    fun alertSettingsAreSavedAndSurviveANewController() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = TimerStore(context)
        val first = TimerController(store = store, clock = { 0L })
        first.setPomodoroAlertSettings(
            PomodoroAlertSettings(ringLikeAlarm = false, autoStartNext = true, stopAfterLongBreak = true)
        )
        val second = TimerController(store = store, clock = { 0L })
        val a = second.pomodoroAlertSettings()
        assertFalse(a.ringLikeAlarm)
        assertTrue(a.autoStartNext)
        assertTrue(a.stopAfterLongBreak)
    }

    @Test
    fun nothingIsScheduledWhenAutoStartIsOff() {
        var now = 0L
        val autoAlarm = RecordingAutoStartAlarm()
        val controller = newController(clock = { now }, autoStartAlarm = autoAlarm)
        finishFocusRound(controller, { now = it }, startAt = 1_000L)

        assertTrue(autoAlarm.scheduled.isEmpty())
        assertNull(controller.autoStartAtMillis.value)
    }

    @Test
    fun aFinishedRoundSchedulesTheNextOneTenSecondsLater() {
        var now = 0L
        val autoAlarm = RecordingAutoStartAlarm()
        val controller = newController(clock = { now }, autoStartAlarm = autoAlarm)
        controller.setPomodoroAlertSettings(PomodoroAlertSettings(autoStartNext = true))
        finishFocusRound(controller, { now = it }, startAt = 1_000L)

        val due = now + 10_000L
        assertEquals(listOf(due), autoAlarm.scheduled)
        assertEquals(due, controller.autoStartAtMillis.value)
        // Still waiting: the finished round is on screen and nothing is running yet.
        assertFalse(TimerLogic.isRunning(controller.state.value))
        assertEquals(PomodoroPhase.FOCUS, controller.state.value.pomodoroPhase)
    }

    @Test
    fun theNextRoundStartsByItselfOnceTheWaitIsOver() {
        var now = 0L
        val alarm = RecordingAlarmHook()
        val autoAlarm = RecordingAutoStartAlarm()
        val controller = newController(clock = { now }, alarm = alarm, autoStartAlarm = autoAlarm)
        controller.setPomodoroAlertSettings(PomodoroAlertSettings(autoStartNext = true))
        finishFocusRound(controller, { now = it }, startAt = 1_000L)
        val due = controller.autoStartAtMillis.value!!

        // Too early: nothing happens and it keeps waiting.
        now = due - 5_000L
        assertFalse(controller.autoStartIfDue())
        assertEquals(due, controller.autoStartAtMillis.value)

        // Due: the short break starts and its own end alarm is scheduled.
        now = due
        assertTrue(controller.autoStartIfDue())
        val s = controller.state.value
        assertEquals(PomodoroPhase.SHORT_BREAK, s.pomodoroPhase)
        assertTrue(TimerLogic.isRunning(s))
        assertEquals(due + 5 * minute, alarm.scheduledEnds.last())
        assertNull(controller.autoStartAtMillis.value)

        // A second delivery (screen and alarm both fire) finds nothing left to do.
        assertFalse(controller.autoStartIfDue())
    }

    @Test
    fun anAlarmArrivingASliverEarlyStillStartsWithTheReceiversTolerance() {
        var now = 0L
        val controller = newController(clock = { now })
        controller.setPomodoroAlertSettings(PomodoroAlertSettings(autoStartNext = true))
        finishFocusRound(controller, { now = it }, startAt = 0L)
        val due = controller.autoStartAtMillis.value!!

        now = due - 1_500L
        assertTrue(controller.autoStartIfDue(toleranceMillis = 2_000L))
        assertEquals(PomodoroPhase.SHORT_BREAK, controller.state.value.pomodoroPhase)
    }

    @Test
    fun cancellingKeepsTheFinishedRoundForTheUserToStart() {
        var now = 0L
        val autoAlarm = RecordingAutoStartAlarm()
        val controller = newController(clock = { now }, autoStartAlarm = autoAlarm)
        controller.setPomodoroAlertSettings(PomodoroAlertSettings(autoStartNext = true))
        finishFocusRound(controller, { now = it }, startAt = 0L)
        val before = autoAlarm.cancels

        controller.cancelAutoStart()

        assertNull(controller.autoStartAtMillis.value)
        assertEquals(before + 1, autoAlarm.cancels)
        now += 30_000L
        assertFalse(controller.autoStartIfDue())
        // The user can still carry on by hand.
        assertTrue(TimerLogic.isFinished(controller.state.value, now))
        controller.start()
        assertEquals(PomodoroPhase.SHORT_BREAK, controller.state.value.pomodoroPhase)
    }

    @Test
    fun startingResettingOrSkippingByHandDropsAWaitingAutoStart() {
        listOf<(TimerController) -> Unit>(
            { it.start() },
            { it.reset() },
            { it.skipPomodoroPhase() }
        ).forEach { action ->
            var now = 0L
            val controller = newController(clock = { now })
            controller.setPomodoroAlertSettings(PomodoroAlertSettings(autoStartNext = true))
            finishFocusRound(controller, { now = it }, startAt = 0L)
            assertTrue(controller.autoStartAtMillis.value != null)

            action(controller)

            assertNull(controller.autoStartAtMillis.value)
            // The old wait must not start a second round on top of what the user did.
            now += 60_000L
            assertFalse(controller.autoStartIfDue())
        }
    }

    @Test
    fun turningAutoStartOffDropsAWaitingOne() {
        var now = 0L
        val controller = newController(clock = { now })
        controller.setPomodoroAlertSettings(PomodoroAlertSettings(autoStartNext = true))
        finishFocusRound(controller, { now = it }, startAt = 0L)

        controller.setPomodoroAlertSettings(PomodoroAlertSettings(autoStartNext = false))

        assertNull(controller.autoStartAtMillis.value)
    }

    @Test
    fun theLongBreakCanEndTheChainWhenAskedTo() {
        var now = 0L
        val autoAlarm = RecordingAutoStartAlarm()
        val controller = newController(clock = { now }, autoStartAlarm = autoAlarm)
        controller.setPomodoroSettings(PomodoroSettings(1, 1, 1, 1)) // one round, then a long break
        controller.setPomodoroAlertSettings(
            PomodoroAlertSettings(autoStartNext = true, stopAfterLongBreak = true)
        )
        controller.enablePomodoro()

        // Focus ends: the long break starts by itself.
        controller.start()
        now += minute
        assertTrue(controller.markFinishedIfDue())
        assertTrue(controller.autoStartAtMillis.value != null)
        now = controller.autoStartAtMillis.value!!
        assertTrue(controller.autoStartIfDue())
        assertEquals(PomodoroPhase.LONG_BREAK, controller.state.value.pomodoroPhase)

        // The long break ends: nothing is scheduled, the user starts the next cycle.
        val scheduledBefore = autoAlarm.scheduled.size
        now += minute
        assertTrue(controller.markFinishedIfDue())
        assertEquals(scheduledBefore, autoAlarm.scheduled.size)
        assertNull(controller.autoStartAtMillis.value)
    }

    @Test
    fun aRoundThatEndedLongAgoIsNotFollowedByAnAutoStart() {
        var now = 0L
        val controller = newController(clock = { now })
        controller.setPomodoroAlertSettings(PomodoroAlertSettings(autoStartNext = true))
        controller.enablePomodoro()
        controller.start()
        // The phone was off: the app only notices 10 minutes after the round ended.
        now = 25 * minute + 10 * minute
        assertTrue(controller.markFinishedIfDue())

        assertNull(controller.autoStartAtMillis.value)
    }

    @Test
    fun aWaitThatRanOutLongAgoIsDroppedInsteadOfStartingLate() {
        var now = 0L
        val controller = newController(clock = { now })
        controller.setPomodoroAlertSettings(PomodoroAlertSettings(autoStartNext = true))
        finishFocusRound(controller, { now = it }, startAt = 0L)
        val due = controller.autoStartAtMillis.value!!

        now = due + PomodoroLogic.AUTO_START_MAX_LATE_MILLIS + 1_000L
        assertFalse(controller.autoStartIfDue())

        assertNull(controller.autoStartAtMillis.value)
        assertFalse(TimerLogic.isRunning(controller.state.value))
        assertEquals(PomodoroPhase.FOCUS, controller.state.value.pomodoroPhase)
    }

    @Test
    fun aWaitingAutoStartSurvivesANewControllerAndIsRescheduledAfterReboot() {
        var now = 0L
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = TimerStore(context)
        store.save(TimerState())
        store.setAutoStartAtMillis(null)
        store.setPomodoroAlertSettings(PomodoroAlertSettings(autoStartNext = true))
        val first = TimerController(store = store, clock = { now })
        first.enablePomodoro()
        first.start()
        now = 25 * minute
        assertTrue(first.markFinishedIfDue())
        val due = first.autoStartAtMillis.value!!

        val autoAlarm = RecordingAutoStartAlarm()
        now = due - 4_000L
        val second = TimerController(store = store, clock = { now }, autoStartAlarm = autoAlarm)
        assertEquals(due, second.autoStartAtMillis.value)
        second.restoreAfterReboot()
        assertEquals(listOf(due), autoAlarm.scheduled)

        // After a reboot that took longer than the wait, it is dropped rather than started late.
        now = due + 1_000L
        val third = TimerController(store = store, clock = { now }, autoStartAlarm = autoAlarm)
        third.restoreAfterReboot()
        assertNull(third.autoStartAtMillis.value)
    }
}
