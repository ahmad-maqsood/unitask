package com.example.util.timer

import android.content.Context
import com.example.reminder.CountdownAlarm
import com.example.reminder.TimerOngoingNotifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface CountdownAlarmHook {
    fun schedule(endAtMillis: Long)
    fun cancel()
}

object NoCountdownAlarm : CountdownAlarmHook {
    override fun schedule(endAtMillis: Long) {}
    override fun cancel() {}
}

/**
 * Manages timer state, persistence and system alarm hooks.
 *
 * The StateFlow only publishes structural state changes (start, pause, reset, mode, duration,
 * or finish) and never emits ticking intervals.
 */
class TimerController(
    private val store: TimerStore,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val alarm: CountdownAlarmHook = NoCountdownAlarm,
    private val ongoing: TimerOngoingHook = NoTimerOngoing
) {

    private val _state = MutableStateFlow(store.load())
    val state: StateFlow<TimerState> = _state.asStateFlow()

    fun now(): Long = clock()

    fun start() {
        val current = _state.value
        val currentTime = now()
        val updated = TimerLogic.start(current, currentTime)
        if (updated != current) {
            store.save(updated)
            _state.value = updated
            if (updated.mode == TimerMode.COUNTDOWN) {
                val end = TimerLogic.endTimeMillis(updated, currentTime)
                if (end != null) {
                    alarm.schedule(end)
                }
            }
            ongoing.update(updated, currentTime)
        }
    }

    fun pause() {
        val current = _state.value
        val currentTime = now()
        val updated = TimerLogic.pause(current, currentTime)
        if (updated != current) {
            store.save(updated)
            _state.value = updated
            alarm.cancel()
            ongoing.update(updated, currentTime)
        }
    }

    fun reset() {
        val current = _state.value
        val updated = TimerLogic.reset(current)
        if (updated != current) {
            store.save(updated)
            _state.value = updated
            alarm.cancel()
            ongoing.cancel()
        }
    }

    fun setMode(mode: TimerMode) {
        val current = _state.value
        val updated = TimerLogic.withMode(current, mode)
        if (updated != current) {
            store.save(updated)
            _state.value = updated
        }
    }

    fun setCountdownMinutes(minutes: Int) {
        store.setLastCountdownMinutes(minutes)
        val current = _state.value
        val updated = TimerLogic.withCountdownMinutes(current, minutes)
        if (updated != current) {
            store.save(updated)
            _state.value = updated
        }
    }

    fun markFinishedIfDue(toleranceMillis: Long = 0L): Boolean {
        val current = _state.value
        if (current.mode == TimerMode.COUNTDOWN && TimerLogic.isRunning(current)) {
            val remaining = TimerLogic.remainingMillis(current, now())
            if (remaining <= toleranceMillis) {
                val updated = TimerLogic.finish(current)
                store.save(updated)
                _state.value = updated
                alarm.cancel()
                ongoing.cancel()
                return true
            }
        }
        return false
    }
    
    fun keepScreenOnInFullscreen(): Boolean = store.keepScreenOnInFullscreen()

    fun setKeepScreenOnInFullscreen(value: Boolean) = store.setKeepScreenOnInFullscreen(value)

    fun restoreAfterReboot(): Boolean {
        val current = _state.value
        // A reboot clears notifications, so show the running or paused timer again.
        ongoing.update(current, now())
        if (current.mode == TimerMode.COUNTDOWN && TimerLogic.isRunning(current)) {
            val currentTime = now()
            val remaining = TimerLogic.remainingMillis(current, currentTime)
            if (remaining > 0L) {
                val end = TimerLogic.endTimeMillis(current, currentTime)
                if (end != null) {
                    alarm.schedule(end)
                }
                return false
            } else {
                return markFinishedIfDue()
            }
        }
        return false
    }

    companion object {
        @Volatile
        private var INSTANCE: TimerController? = null

        fun get(context: Context): TimerController {
            val appContext = context.applicationContext
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TimerController(
                    store = TimerStore(appContext),
                    alarm = CountdownAlarm(appContext),
                    ongoing = TimerOngoingNotifier(appContext)
                ).also { INSTANCE = it }
            }
        }

        internal fun setInstanceForTests(c: TimerController?) {
            synchronized(this) {
                INSTANCE = c
            }
        }
    }
}
