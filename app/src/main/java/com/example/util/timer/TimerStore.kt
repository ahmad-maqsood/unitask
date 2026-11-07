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

            TimerState(
                mode = mode,
                countdownTotalMillis = if (countdownTotal > 0L) countdownTotal else 25 * 60_000L,
                accumulatedMillis = if (accumulated >= 0L) accumulated else 0L,
                runningSinceMillis = runningSince
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
        private const val DEFAULT_COUNTDOWN_MINUTES = 25
    }
}
