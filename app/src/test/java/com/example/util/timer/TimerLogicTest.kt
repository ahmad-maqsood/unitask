package com.example.util.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerLogicTest {

    @Test
    fun elapsedWithSeveralPausesCalculatesCorrectly() {
        // Start at 0, pause at 10 s, resume at 100 s, pause at 105 s gives 15 s
        var s = TimerState(mode = TimerMode.STOPWATCH)
        s = TimerLogic.start(s, now = 0L)
        s = TimerLogic.pause(s, now = 10_000L)
        assertEquals(10_000L, TimerLogic.elapsedMillis(s, now = 50_000L))

        s = TimerLogic.start(s, now = 100_000L)
        s = TimerLogic.pause(s, now = 105_000L)
        assertEquals(15_000L, TimerLogic.elapsedMillis(s, now = 200_000L))
    }

    @Test
    fun pausedStopwatchStopsCounting() {
        var s = TimerState(mode = TimerMode.STOPWATCH)
        s = TimerLogic.start(s, now = 1_000L)
        s = TimerLogic.pause(s, now = 5_000L)

        assertTrue(TimerLogic.isPaused(s))
        assertFalse(TimerLogic.isRunning(s))
        assertFalse(TimerLogic.isIdle(s))

        // Advancing time must not advance elapsedMillis while paused
        assertEquals(4_000L, TimerLogic.elapsedMillis(s, now = 5_000L))
        assertEquals(4_000L, TimerLogic.elapsedMillis(s, now = 50_000L))
    }

    @Test
    fun countdownRemainingAndFinishedAtTotalAndNeverNegative() {
        val total = 60_000L
        var s = TimerState(mode = TimerMode.COUNTDOWN, countdownTotalMillis = total)
        s = TimerLogic.start(s, now = 1_000L)

        // Mid-way (30s elapsed)
        assertEquals(30_000L, TimerLogic.remainingMillis(s, now = 31_000L))
        assertFalse(TimerLogic.isFinished(s, now = 31_000L))

        // Exactly at total (60s elapsed)
        assertEquals(0L, TimerLogic.remainingMillis(s, now = 61_000L))
        assertTrue(TimerLogic.isFinished(s, now = 61_000L))
        assertEquals(60_000L, TimerLogic.elapsedMillis(s, now = 61_000L))

        // Past total (120s elapsed): remaining is never negative, elapsed capped at total
        assertEquals(0L, TimerLogic.remainingMillis(s, now = 121_000L))
        assertEquals(60_000L, TimerLogic.elapsedMillis(s, now = 121_000L))
        assertTrue(TimerLogic.isFinished(s, now = 121_000L))
    }

    @Test
    fun clockMovedBackwardsGivesNoNegativeTime() {
        var s = TimerState(mode = TimerMode.STOPWATCH)
        s = TimerLogic.start(s, now = 10_000L)

        // Clock moves backwards from 10_000 to 5_000
        assertEquals(0L, TimerLogic.elapsedMillis(s, now = 5_000L))

        var countdown = TimerState(mode = TimerMode.COUNTDOWN, countdownTotalMillis = 60_000L)
        countdown = TimerLogic.start(countdown, now = 10_000L)
        assertEquals(0L, TimerLogic.elapsedMillis(countdown, now = 5_000L))
        assertEquals(60_000L, TimerLogic.remainingMillis(countdown, now = 5_000L))
    }

    @Test
    fun withModeAndWithCountdownMinutesIgnoredWhenNotIdleAndClampWhenIdle() {
        val idle = TimerState(mode = TimerMode.STOPWATCH)
        assertTrue(TimerLogic.isIdle(idle))

        // When idle: mode changes work
        val countdown = TimerLogic.withMode(idle, TimerMode.COUNTDOWN)
        assertEquals(TimerMode.COUNTDOWN, countdown.mode)

        // When idle: minutes clamped to MIN..MAX
        val underMin = TimerLogic.withCountdownMinutes(countdown, 0)
        assertEquals(TimerLogic.MIN_COUNTDOWN_MINUTES * 60_000L, underMin.countdownTotalMillis)

        val overMax = TimerLogic.withCountdownMinutes(countdown, 1000)
        assertEquals(TimerLogic.MAX_COUNTDOWN_MINUTES * 60_000L, overMax.countdownTotalMillis)

        val normal = TimerLogic.withCountdownMinutes(countdown, 15)
        assertEquals(15 * 60_000L, normal.countdownTotalMillis)

        // When running: ignored
        val running = TimerLogic.start(normal, now = 1000L)
        assertEquals(running, TimerLogic.withMode(running, TimerMode.STOPWATCH))
        assertEquals(running, TimerLogic.withCountdownMinutes(running, 30))

        // When paused: ignored
        val paused = TimerLogic.pause(running, now = 2000L)
        assertEquals(paused, TimerLogic.withMode(paused, TimerMode.STOPWATCH))
        assertEquals(paused, TimerLogic.withCountdownMinutes(paused, 30))
    }

    @Test
    fun resetKeepsModeAndLength() {
        var s = TimerState(mode = TimerMode.COUNTDOWN, countdownTotalMillis = 10 * 60_000L)
        s = TimerLogic.start(s, now = 1000L)
        s = TimerLogic.pause(s, now = 5000L)
        assertEquals(4000L, s.accumulatedMillis)

        val resetState = TimerLogic.reset(s)
        assertEquals(TimerMode.COUNTDOWN, resetState.mode)
        assertEquals(10 * 60_000L, resetState.countdownTotalMillis)
        assertEquals(0L, resetState.accumulatedMillis)
        assertNull(resetState.runningSinceMillis)
        assertTrue(TimerLogic.isIdle(resetState))
    }

    @Test
    fun startOnRunningOrFinishedTimerChangesNothing() {
        var s = TimerState(mode = TimerMode.COUNTDOWN, countdownTotalMillis = 60_000L)
        s = TimerLogic.start(s, now = 1000L)
        assertEquals(s, TimerLogic.start(s, now = 5000L))

        val finished = TimerLogic.finish(s)
        assertEquals(finished, TimerLogic.start(finished, now = 10_000L))
    }

    @Test
    fun formatClockCases() {
        // 0
        assertEquals("00:00", TimerLogic.formatClock(0L, roundUp = false))
        assertEquals("00:00", TimerLogic.formatClock(0L, roundUp = true))

        // 999 ms floor versus ceil
        assertEquals("00:00", TimerLogic.formatClock(999L, roundUp = false))
        assertEquals("00:01", TimerLogic.formatClock(999L, roundUp = true))

        // 60 s
        assertEquals("01:00", TimerLogic.formatClock(60_000L, roundUp = false))

        // 3599 s
        assertEquals("59:59", TimerLogic.formatClock(3599_000L, roundUp = false))

        // 3600 s
        assertEquals("1:00:00", TimerLogic.formatClock(3600_000L, roundUp = false))

        // 100 hours capped as 99:59:59 for the stopwatch cap value
        assertEquals("99:59:59", TimerLogic.formatClock(TimerLogic.MAX_STOPWATCH_MILLIS, roundUp = false))
        assertEquals("99:59:59", TimerLogic.formatClock(100L * 3600_000L, roundUp = false))

        // negative
        assertEquals("00:00", TimerLogic.formatClock(-5000L, roundUp = false))
        assertEquals("00:00", TimerLogic.formatClock(-5000L, roundUp = true))
    }
}
