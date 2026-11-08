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

    @Test
    fun combineHoursAndMinutesCalculatesTotalMinutes() {
        assertEquals(0, TimerLogic.combineHoursAndMinutes(0, 0))
        assertEquals(45, TimerLogic.combineHoursAndMinutes(0, 45))
        assertEquals(60, TimerLogic.combineHoursAndMinutes(1, 0))
        assertEquals(90, TimerLogic.combineHoursAndMinutes(1, 30))
        assertEquals(600, TimerLogic.combineHoursAndMinutes(10, 0))
    }

    @Test
    fun splitHoursAndMinutesSplitsIntoHoursAndMinutes() {
        assertEquals(Pair(0, 0), TimerLogic.splitHoursAndMinutes(0))
        assertEquals(Pair(0, 45), TimerLogic.splitHoursAndMinutes(45))
        assertEquals(Pair(1, 0), TimerLogic.splitHoursAndMinutes(60))
        assertEquals(Pair(1, 30), TimerLogic.splitHoursAndMinutes(90))
        assertEquals(Pair(10, 0), TimerLogic.splitHoursAndMinutes(600))
    }

    @Test
    fun addMinutesClampsAtBothEnds() {
        // Upper clamp at MAX_COUNTDOWN_MINUTES (600)
        assertEquals(600, TimerLogic.addMinutes(590, 20))
        assertEquals(600, TimerLogic.addMinutes(600, 10))

        // Lower clamp at MIN_COUNTDOWN_MINUTES (1)
        assertEquals(1, TimerLogic.addMinutes(5, -10))
        assertEquals(1, TimerLogic.addMinutes(1, -5))

        // Normal addition within range
        assertEquals(30, TimerLogic.addMinutes(25, 5))
        assertEquals(75, TimerLogic.addMinutes(45, 30))
    }

    @Test
    fun formatDurationProducesAllThreeShapes() {
        // Minutes only: "45 min"
        assertEquals("45 min", TimerLogic.formatDuration(45))

        // Hours only: "1 h"
        assertEquals("1 h", TimerLogic.formatDuration(60))
        assertEquals("2 h", TimerLogic.formatDuration(120))

        // Both hours and minutes: "1 h 30 min"
        assertEquals("1 h 30 min", TimerLogic.formatDuration(90))
        assertEquals("2 h 15 min", TimerLogic.formatDuration(135))
    }

    @Test
    fun isAlarmLateDetectsLateAlarmsPastTwoMinutes() {
        // Running 10-minute countdown started at 10_000L, scheduled to end at 610_000L
        val runningState = TimerState(
            mode = TimerMode.COUNTDOWN,
            countdownTotalMillis = 10 * 60_000L,
            runningSinceMillis = 10_000L,
            accumulatedMillis = 0L
        )

        // Still running before end: not late
        assertFalse(TimerLogic.isAlarmLate(runningState, now = 300_000L))

        // Arrives exactly at end time: not late
        assertFalse(TimerLogic.isAlarmLate(runningState, now = 610_000L))

        // Arrives 1 minute after end time (within 2 minutes threshold): not late
        assertFalse(TimerLogic.isAlarmLate(runningState, now = 670_000L))

        // Arrives exactly 2 minutes after end time: not late (not > 2 minutes)
        assertFalse(TimerLogic.isAlarmLate(runningState, now = 730_000L))

        // Arrives 2 minutes and 1 second after end time (exceeds 2 minutes threshold): late
        assertTrue(TimerLogic.isAlarmLate(runningState, now = 730_001L))

        // Not a running countdown: false
        val idleState = TimerState(mode = TimerMode.COUNTDOWN)
        assertFalse(TimerLogic.isAlarmLate(idleState, now = 1_000_000L))
    }
}
