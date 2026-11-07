package com.example.util.timer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TimerControllerTest {

    private class RecordingAlarmHook : CountdownAlarmHook {
        val scheduledEnds = mutableListOf<Long>()
        var cancelCount = 0

        override fun schedule(endAtMillis: Long) {
            scheduledEnds.add(endAtMillis)
        }

        override fun cancel() {
            cancelCount++
        }
    }

    @Test
    fun startPauseAndResumeAreSavedAndPreservedAcrossKilledAppInstances() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = TimerStore(context)
        var currentTime = 10_000L
        val hook1 = RecordingAlarmHook()
        val controller1 = TimerController(store = store, clock = { currentTime }, alarm = hook1)

        controller1.setMode(TimerMode.STOPWATCH)
        controller1.start() // runningSince = 10_000L

        currentTime = 20_000L // 10s passed
        controller1.pause() // accumulated = 10_000L

        // Simulate app process termination and new controller creation over the same store
        currentTime = 80_000L
        val hook2 = RecordingAlarmHook()
        val controller2 = TimerController(store = store, clock = { currentTime }, alarm = hook2)

        // While paused, elapsed remains 10_000L even after time advanced
        assertEquals(10_000L, TimerLogic.elapsedMillis(controller2.state.value, controller2.now()))

        // Resume in new controller instance
        controller2.start()
        currentTime = 85_000L // 5s more passed
        assertEquals(15_000L, TimerLogic.elapsedMillis(controller2.state.value, controller2.now()))
    }

    @Test
    fun alarmHookReceivesEndTimeOnStartAndResumeAndCancelOnPauseAndReset() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = TimerStore(context)
        var currentTime = 1_000L
        val hook = RecordingAlarmHook()
        val controller = TimerController(store = store, clock = { currentTime }, alarm = hook)

        controller.setMode(TimerMode.COUNTDOWN)
        controller.setCountdownMinutes(10) // 10 minutes = 600_000 ms

        controller.start() // started at 1_000L, end should be 601_000L
        assertEquals(listOf(601_000L), hook.scheduledEnds)
        assertEquals(0, hook.cancelCount)

        // Advance time by 100s and pause
        currentTime = 101_000L
        controller.pause()
        assertEquals(1, hook.cancelCount)

        // Resume at 200_000L (500s remaining, so end should be 700_000L)
        currentTime = 200_000L
        controller.start()
        assertEquals(listOf(601_000L, 700_000L), hook.scheduledEnds)

        // Reset cancels alarm
        controller.reset()
        assertEquals(2, hook.cancelCount)
    }

    @Test
    fun markFinishedIfDueIsIdempotentAndCancelsAlarmOnce() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = TimerStore(context)
        var currentTime = 1_000L
        val hook = RecordingAlarmHook()
        val controller = TimerController(store = store, clock = { currentTime }, alarm = hook)

        controller.setMode(TimerMode.COUNTDOWN)
        controller.setCountdownMinutes(1) // 60_000 ms
        controller.start()

        // At 60_000L, remaining is 1000 ms
        currentTime = 60_000L
        val notYet = controller.markFinishedIfDue(toleranceMillis = 500L)
        assertFalse(notYet)
        assertEquals(0, hook.cancelCount)

        // At 60_600L, remaining is 400 ms <= 500 ms tolerance
        currentTime = 60_600L
        val firstMark = controller.markFinishedIfDue(toleranceMillis = 500L)
        assertTrue(firstMark)
        assertEquals(1, hook.cancelCount)
        assertFalse(TimerLogic.isRunning(controller.state.value))

        // Second call is idempotent: returns false and doesn't cancel again
        val secondMark = controller.markFinishedIfDue(toleranceMillis = 500L)
        assertFalse(secondMark)
        assertEquals(1, hook.cancelCount)
    }

    @Test
    fun restoreAfterRebootSchedulesWhenTimeLeftAndFinishesWhenNot() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Case A: Running countdown with time left
        val storeA = TimerStore(context)
        var currentTimeA = 10_000L
        val hookA = RecordingAlarmHook()
        val controllerA1 = TimerController(store = storeA, clock = { currentTimeA }, alarm = hookA)
        controllerA1.setMode(TimerMode.COUNTDOWN)
        controllerA1.setCountdownMinutes(2) // 120_000 ms -> end is 130_000L
        controllerA1.start()

        // Reboot happens at 50_000L (80s left)
        currentTimeA = 50_000L
        val hookA2 = RecordingAlarmHook()
        val controllerA2 = TimerController(store = storeA, clock = { currentTimeA }, alarm = hookA2)
        controllerA2.restoreAfterReboot()
        assertEquals(listOf(130_000L), hookA2.scheduledEnds)
        assertTrue(TimerLogic.isRunning(controllerA2.state.value))

        // Case B: Running countdown whose time expired while device was off
        val storeB = TimerStore(context)
        var currentTimeB = 10_000L
        val hookB = RecordingAlarmHook()
        val controllerB1 = TimerController(store = storeB, clock = { currentTimeB }, alarm = hookB)
        controllerB1.setMode(TimerMode.COUNTDOWN)
        controllerB1.setCountdownMinutes(1) // 60_000 ms -> end was 70_000L
        controllerB1.start()

        // Reboot happens at 80_000L (10s overdue)
        currentTimeB = 80_000L
        val hookB2 = RecordingAlarmHook()
        val controllerB2 = TimerController(store = storeB, clock = { currentTimeB }, alarm = hookB2)
        controllerB2.restoreAfterReboot()
        assertEquals(emptyList<Long>(), hookB2.scheduledEnds)
        assertFalse(TimerLogic.isRunning(controllerB2.state.value))
        assertEquals(60_000L, controllerB2.state.value.accumulatedMillis)
    }
}
