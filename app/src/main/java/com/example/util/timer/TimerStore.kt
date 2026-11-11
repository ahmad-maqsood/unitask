package com.example.util.timer

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists timer state in SharedPreferences so timers survive process death and phone reboots.
 */
class TimerStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): TimerState {
        return try {
            val modeStr = prefs.getString(KEY_MODE, null)
            val mode = if (modeStr != null) {
                try {
                    TimerMode.valueOf(modeStr)
                } catch (_: IllegalArgumentException) {
                    TimerMode.STOPWATCH
                }
            } else {
                TimerMode.STOPWATCH
            }

            val countdownTotal = prefs.getLong(KEY_COUNTDOWN_TOTAL_MILLIS, 25 * 60_000L)
            val accumulated = prefs.getLong(KEY_ACCUMULATED_MILLIS, 0L)
            val runningSince = if (prefs.contains(KEY_RUNNING_SINCE_MILLIS)) {
                prefs.getLong(KEY_RUNNING_SINCE_MILLIS, 0L)
            } else {
                null
            }

            val phase = prefs.getString(KEY_POMODORO_PHASE, null)?.let {
                try {
                    PomodoroPhase.valueOf(it)
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
            val round = prefs.getInt(KEY_POMODORO_ROUND, 1)

            TimerState(
                // Pomodoro is always a countdown.
                mode = if (phase != null) TimerMode.COUNTDOWN else mode,
                countdownTotalMillis = if (countdownTotal > 0L) countdownTotal else 25 * 60_000L,
                accumulatedMillis = if (accumulated >= 0L) accumulated else 0L,
                runningSinceMillis = runningSince,
                pomodoroPhase = phase,
                pomodoroRound = if (round >= 1) round else 1
            )
        } catch (_: Exception) {
            TimerState()
        }
    }

    fun save(state: TimerState) {
        try {
            val editor = prefs.edit()
                .putString(KEY_MODE, state.mode.name)
                .putLong(KEY_COUNTDOWN_TOTAL_MILLIS, state.countdownTotalMillis)
                .putLong(KEY_ACCUMULATED_MILLIS, state.accumulatedMillis)
                .putInt(KEY_POMODORO_ROUND, state.pomodoroRound)

            if (state.pomodoroPhase != null) {
                editor.putString(KEY_POMODORO_PHASE, state.pomodoroPhase.name)
            } else {
                editor.remove(KEY_POMODORO_PHASE)
            }

            if (state.runningSinceMillis != null) {
                editor.putLong(KEY_RUNNING_SINCE_MILLIS, state.runningSinceMillis)
            } else {
                editor.remove(KEY_RUNNING_SINCE_MILLIS)
            }
            editor.apply()
        } catch (_: Exception) {
            // Never throw on preference save failures.
        }
    }

    fun lastCountdownMinutes(): Int {
        return try {
            val minutes = prefs.getInt(KEY_LAST_COUNTDOWN_MINUTES, DEFAULT_COUNTDOWN_MINUTES)
            if (minutes in TimerLogic.MIN_COUNTDOWN_MINUTES..TimerLogic.MAX_COUNTDOWN_MINUTES) {
                minutes
            } else {
                DEFAULT_COUNTDOWN_MINUTES
            }
        } catch (_: Exception) {
            DEFAULT_COUNTDOWN_MINUTES
        }
    }

    fun setLastCountdownMinutes(minutes: Int) {
        try {
            val clamped = minutes.coerceIn(
                TimerLogic.MIN_COUNTDOWN_MINUTES,
                TimerLogic.MAX_COUNTDOWN_MINUTES
            )
            prefs.edit().putInt(KEY_LAST_COUNTDOWN_MINUTES, clamped).apply()
        } catch (_: Exception) {
            // Never throw on preference save failures.
        }
    }
    
    /** The Pomodoro lengths the user chose, or the usual 25 / 5 / 15 minutes and 4 rounds. */
    fun pomodoroSettings(): PomodoroSettings {
        return try {
            PomodoroSettings(
                focusMinutes = prefs.getInt(KEY_POMODORO_FOCUS, PomodoroSettings.DEFAULT_FOCUS_MINUTES),
                shortBreakMinutes = prefs.getInt(KEY_POMODORO_SHORT, PomodoroSettings.DEFAULT_SHORT_BREAK_MINUTES),
                longBreakMinutes = prefs.getInt(KEY_POMODORO_LONG, PomodoroSettings.DEFAULT_LONG_BREAK_MINUTES),
                roundsBeforeLongBreak = prefs.getInt(KEY_POMODORO_ROUNDS, PomodoroSettings.DEFAULT_ROUNDS)
            ).sanitized()
        } catch (_: Exception) {
            PomodoroSettings()
        }
    }

    fun setPomodoroSettings(settings: PomodoroSettings) {
        try {
            val s = settings.sanitized()
            prefs.edit()
                .putInt(KEY_POMODORO_FOCUS, s.focusMinutes)
                .putInt(KEY_POMODORO_SHORT, s.shortBreakMinutes)
                .putInt(KEY_POMODORO_LONG, s.longBreakMinutes)
                .putInt(KEY_POMODORO_ROUNDS, s.roundsBeforeLongBreak)
                .apply()
        } catch (_: Exception) {
            // Never throw on preference save failures.
        }
    }

    /** When the current run was first started, or null when no run is in progress. */
    fun sessionStartMillis(): Long? {
        return try {
            if (prefs.contains(KEY_SESSION_START_MILLIS)) {
                prefs.getLong(KEY_SESSION_START_MILLIS, 0L)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun setSessionStartMillis(value: Long?) {
        try {
            val editor = prefs.edit()
            if (value != null) {
                editor.putLong(KEY_SESSION_START_MILLIS, value)
            } else {
                editor.remove(KEY_SESSION_START_MILLIS)
            }
            editor.apply()
        } catch (_: Exception) {
            // Never throw on preference save failures.
        }
    }

    fun keepScreenOnInFullscreen(): Boolean {
        return try {
            prefs.getBoolean(KEY_KEEP_SCREEN_ON_FULLSCREEN, true)
        } catch (_: Exception) {
            true
        }
    }

    fun setKeepScreenOnInFullscreen(value: Boolean) {
        try {
            prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON_FULLSCREEN, value).apply()
        } catch (_: Exception) {
            // Never throw on preference save failures.
        }
    }

    companion object {
        private const val PREFS_NAME = "tools_timer_prefs"
        private const val KEY_MODE = "mode"
        private const val KEY_COUNTDOWN_TOTAL_MILLIS = "countdown_total_millis"
        private const val KEY_ACCUMULATED_MILLIS = "accumulated_millis"
        private const val KEY_RUNNING_SINCE_MILLIS = "running_since_millis"
        private const val KEY_LAST_COUNTDOWN_MINUTES = "last_countdown_minutes"
        private const val KEY_KEEP_SCREEN_ON_FULLSCREEN = "keep_screen_on_fullscreen"
        private const val KEY_SESSION_START_MILLIS = "session_start_millis"
        private const val KEY_POMODORO_PHASE = "pomodoro_phase"
        private const val KEY_POMODORO_ROUND = "pomodoro_round"
        private const val KEY_POMODORO_FOCUS = "pomodoro_focus_minutes"
        private const val KEY_POMODORO_SHORT = "pomodoro_short_break_minutes"
        private const val KEY_POMODORO_LONG = "pomodoro_long_break_minutes"
        private const val KEY_POMODORO_ROUNDS = "pomodoro_rounds_before_long_break"
        private const val DEFAULT_COUNTDOWN_MINUTES = 25
    }
}
