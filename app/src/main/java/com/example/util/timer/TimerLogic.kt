package com.example.util.timer

import java.util.Locale

enum class TimerMode {
    STOPWATCH,
    COUNTDOWN
}

data class TimerState(
    val mode: TimerMode = TimerMode.STOPWATCH,
    val countdownTotalMillis: Long = 25 * 60_000L,
    val accumulatedMillis: Long = 0L,
    val runningSinceMillis: Long? = null
)

/**
 * Pure timer arithmetic based on wall-clock timestamps.
 *
 * Timers in this app are not ticking counters, because background throttling and screen-off
 * states freeze app execution on Android. Instead, elapsed time is computed on demand from
 * accumulated paused time plus the delta since the timer last started.
 */
object TimerLogic {

    // Maximum stopwatch time: 99 hours, 59 minutes, 59 seconds.
    const val MAX_STOPWATCH_MILLIS = 99 * 3_600_000L + 59 * 60_000L + 59_000L
    const val MIN_COUNTDOWN_MINUTES = 1
    const val MAX_COUNTDOWN_MINUTES = 600
    const val LATE_ALARM_THRESHOLD_MILLIS = 2 * 60_000L

    fun elapsedMillis(s: TimerState, now: Long): Long {
        val runningElapsed = if (s.runningSinceMillis != null) {
            maxOf(0L, now - s.runningSinceMillis)
        } else {
            0L
        }
        val total = s.accumulatedMillis + runningElapsed
        return when (s.mode) {
            TimerMode.COUNTDOWN -> minOf(total, s.countdownTotalMillis)
            TimerMode.STOPWATCH -> minOf(total, MAX_STOPWATCH_MILLIS)
        }
    }

    fun remainingMillis(s: TimerState, now: Long): Long {
        return maxOf(0L, s.countdownTotalMillis - elapsedMillis(s, now))
    }

    fun isRunning(s: TimerState): Boolean {
        return s.runningSinceMillis != null
    }

    fun isIdle(s: TimerState): Boolean {
        return !isRunning(s) && s.accumulatedMillis == 0L
    }

    fun isFinished(s: TimerState, now: Long): Boolean {
        return s.mode == TimerMode.COUNTDOWN &&
            remainingMillis(s, now) == 0L &&
            (s.accumulatedMillis > 0L || isRunning(s))
    }

    fun isPaused(s: TimerState): Boolean {
        if (isRunning(s) || s.accumulatedMillis == 0L) return false
        return if (s.mode == TimerMode.COUNTDOWN) {
            s.accumulatedMillis < s.countdownTotalMillis
        } else {
            true
        }
    }

    fun start(s: TimerState, now: Long): TimerState {
        if (isRunning(s) || isFinished(s, now)) {
            return s
        }
        return s.copy(runningSinceMillis = now)
    }

    fun pause(s: TimerState, now: Long): TimerState {
        if (!isRunning(s)) {
            return s
        }
        return s.copy(
            accumulatedMillis = elapsedMillis(s, now),
            runningSinceMillis = null
        )
    }

    fun finish(s: TimerState): TimerState {
        return if (s.mode == TimerMode.COUNTDOWN) {
            s.copy(
                accumulatedMillis = s.countdownTotalMillis,
                runningSinceMillis = null
            )
        } else {
            s
        }
    }

    fun reset(s: TimerState): TimerState {
        return s.copy(
            accumulatedMillis = 0L,
            runningSinceMillis = null
        )
    }

    fun withMode(s: TimerState, mode: TimerMode): TimerState {
        if (!isIdle(s)) return s
        return s.copy(mode = mode)
    }

    fun withCountdownMinutes(s: TimerState, minutes: Int): TimerState {
        if (!isIdle(s)) return s
        val clamped = minutes.coerceIn(MIN_COUNTDOWN_MINUTES, MAX_COUNTDOWN_MINUTES)
        return s.copy(countdownTotalMillis = clamped * 60_000L)
    }

    fun endTimeMillis(s: TimerState, now: Long): Long? {
        if (s.mode != TimerMode.COUNTDOWN || !isRunning(s)) return null
        return now + remainingMillis(s, now)
    }

    fun formatClock(millis: Long, roundUp: Boolean): String {
        val nonNegative = maxOf(0L, millis)
        val rawSeconds = if (roundUp) {
            if (nonNegative == 0L) 0L else (nonNegative + 999L) / 1000L
        } else {
            nonNegative / 1000L
        }
        val maxSeconds = 99L * 3600L + 59L * 60L + 59L
        val seconds = minOf(rawSeconds, maxSeconds)

        val h = seconds / 3600L
        val m = (seconds % 3600L) / 60L
        val sec = seconds % 60L

        return if (h > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
        } else {
            String.format(Locale.US, "%02d:%02d", m, sec)
        }
    }

    fun combineHoursAndMinutes(hours: Int, minutes: Int): Int {
        return maxOf(0, hours) * 60 + maxOf(0, minutes)
    }

    fun splitHoursAndMinutes(totalMinutes: Int): Pair<Int, Int> {
        val nonNegative = maxOf(0, totalMinutes)
        return Pair(nonNegative / 60, nonNegative % 60)
    }

    fun addMinutes(currentTotal: Int, delta: Int): Int {
        return (currentTotal + delta).coerceIn(MIN_COUNTDOWN_MINUTES, MAX_COUNTDOWN_MINUTES)
    }

    fun formatDuration(totalMinutes: Int): String {
        val (h, m) = splitHoursAndMinutes(totalMinutes)
        return when {
            h > 0 && m > 0 -> "$h h $m min"
            h > 0 -> "$h h"
            else -> "$m min"
        }
    }

    fun scheduledEndTimeMillis(s: TimerState): Long? {
        if (s.mode != TimerMode.COUNTDOWN || s.runningSinceMillis == null) return null
        val remainingWhenStarted = maxOf(0L, s.countdownTotalMillis - s.accumulatedMillis)
        return s.runningSinceMillis + remainingWhenStarted
    }

    fun isAlarmLate(
        s: TimerState,
        now: Long,
        lateThresholdMillis: Long = LATE_ALARM_THRESHOLD_MILLIS
    ): Boolean {
        val end = scheduledEndTimeMillis(s) ?: return false
        return (now - end) > lateThresholdMillis
    }
}
