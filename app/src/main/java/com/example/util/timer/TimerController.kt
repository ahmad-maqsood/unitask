package com.example.util.timer

import android.content.Context
import com.example.reminder.AutoStartAlarm
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

/** The system alarm that starts the next Pomodoro round, so it also works with the app closed. */
interface AutoStartAlarmHook {
    fun schedule(triggerAtMillis: Long)
    fun cancel()
}

object NoAutoStartAlarm : AutoStartAlarmHook {
    override fun schedule(triggerAtMillis: Long) {}
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
    private val ongoing: TimerOngoingHook = NoTimerOngoing,
    private val history: TimerHistoryHook = NoTimerHistory,
    private val autoStartAlarm: AutoStartAlarmHook = NoAutoStartAlarm
) {

    private val _state = MutableStateFlow(store.load())
    val state: StateFlow<TimerState> = _state.asStateFlow()

    private val _autoStartAt = MutableStateFlow(store.autoStartAtMillis())

    /** When the next Pomodoro round will start by itself, or null when none is waiting. */
    val autoStartAtMillis: StateFlow<Long?> = _autoStartAt.asStateFlow()

    /** Finished study sessions, newest first. */
    val sessions: StateFlow<List<TimerSession>> get() = history.sessions

    fun deleteSession(startedAtMillis: Long) = history.delete(startedAtMillis)

    fun clearHistory() = history.clear()

    fun now(): Long = clock()

    fun start() {
        // Pomodoro: make sure a phase that has just run out is closed (and saved) first.
        if (_state.value.pomodoroPhase != null) markFinishedIfDue()
        // Starting by hand takes over from a waiting auto-start.
        cancelAutoStart()
        val current = _state.value
        val currentTime = now()
        // Pomodoro: starting after a phase has finished begins the next phase (focus or break).
        val base = if (current.pomodoroPhase != null && TimerLogic.isFinished(current, currentTime)) {
            PomodoroLogic.advance(current, store.pomodoroSettings())
        } else {
            current
        }
        val updated = TimerLogic.start(base, currentTime)
        if (updated != current) {
            // Starting from a clean timer begins a new study session; resuming from a pause
            // continues the one already in progress.
            if (TimerLogic.isIdle(base)) {
                store.setSessionStartMillis(currentTime)
            }
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
            recordSession(current, completed = false)
            store.save(updated)
            _state.value = updated
            alarm.cancel()
            ongoing.cancel()
            cancelAutoStart()
        }
    }

    fun setMode(mode: TimerMode) {
        val current = _state.value
        if (!TimerLogic.isIdle(current)) return
        // Leaving Pomodoro puts the plain countdown back at the length the user last chose.
        val base = if (current.pomodoroPhase != null) {
            PomodoroLogic.disable(current, store.lastCountdownMinutes())
        } else {
            current
        }
        val updated = TimerLogic.withMode(base, mode)
        if (updated != current) {
            store.save(updated)
            _state.value = updated
        }
    }

    /** Turns on Pomodoro mode, starting at focus round 1. Only works while the timer is idle. */
    fun enablePomodoro() {
        val current = _state.value
        val updated = PomodoroLogic.enable(current, store.pomodoroSettings())
        if (updated != current) {
            store.save(updated)
            _state.value = updated
        }
    }

    fun pomodoroSettings(): PomodoroSettings = store.pomodoroSettings()

    /** Saves new Pomodoro lengths. An idle timer picks up the new length of its current phase now. */
    fun setPomodoroSettings(settings: PomodoroSettings) {
        val clean = settings.sanitized()
        store.setPomodoroSettings(clean)
        val current = _state.value
        val updated = PomodoroLogic.withSettings(current, clean)
        if (updated != current) {
            store.save(updated)
            _state.value = updated
        }
    }

    /**
     * Jumps to the next Pomodoro phase without waiting for this one to end, for example to cut a
     * break short. Study time already done in a focus phase is still saved.
     */
    fun skipPomodoroPhase() {
        markFinishedIfDue()
        cancelAutoStart()
        val current = _state.value
        if (current.pomodoroPhase == null) return
        recordSession(current, completed = false)
        val updated = PomodoroLogic.advance(current, store.pomodoroSettings())
        store.save(updated)
        _state.value = updated
        alarm.cancel()
        ongoing.cancel()
    }

    fun setCountdownMinutes(minutes: Int) {
        // In Pomodoro mode the lengths come from the Pomodoro settings instead.
        if (_state.value.pomodoroPhase != null) return
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
                // A round that ended long ago (phone was off) is not followed by an auto-start.
                val endedLongAgo = TimerLogic.isAlarmLate(current, now())
                recordSession(current, completed = true)
                val updated = TimerLogic.finish(current)
                store.save(updated)
                _state.value = updated
                alarm.cancel()
                ongoing.cancel()
                if (!endedLongAgo) scheduleAutoStart(updated)
                return true
            }
        }
        return false
    }

    fun pomodoroAlertSettings(): PomodoroAlertSettings = store.pomodoroAlertSettings()

    /** Saves how a Pomodoro round ends. Turning auto-start off also drops a start that is waiting. */
    fun setPomodoroAlertSettings(settings: PomodoroAlertSettings) {
        store.setPomodoroAlertSettings(settings)
        if (!settings.autoStartNext) cancelAutoStart()
    }

    /** Stops a waiting auto-start. The finished round stays as it is until the user starts it. */
    fun cancelAutoStart() {
        if (_autoStartAt.value == null) return
        _autoStartAt.value = null
        store.setAutoStartAtMillis(null)
        autoStartAlarm.cancel()
    }

    private fun scheduleAutoStart(finished: TimerState) {
        val phase = finished.pomodoroPhase ?: return
        if (!PomodoroLogic.shouldAutoStart(phase, store.pomodoroAlertSettings())) return
        val at = now() + PomodoroLogic.AUTO_START_DELAY_MILLIS
        store.setAutoStartAtMillis(at)
        _autoStartAt.value = at
        autoStartAlarm.schedule(at)
    }

    /**
     * Starts the next Pomodoro round if its wait is over. Safe to call from the alarm and from the
     * screen: whichever comes first starts it, the other finds nothing waiting.
     */
    fun autoStartIfDue(toleranceMillis: Long = 0L): Boolean {
        val at = _autoStartAt.value ?: return false
        val currentTime = now()
        if (currentTime < at - toleranceMillis) return false
        cancelAutoStart()
        if (currentTime - at > PomodoroLogic.AUTO_START_MAX_LATE_MILLIS) return false
        val current = _state.value
        if (current.pomodoroPhase == null || !TimerLogic.isFinished(current, currentTime)) return false
        start()
        return true
    }

    /**
     * Saves the run that is ending as a study session. The saved start time is cleared first,
     * so pressing Reset after a countdown has finished cannot save the same run twice.
     */
    private fun recordSession(state: TimerState, completed: Boolean) {
        val startedAt = store.sessionStartMillis() ?: return
        store.setSessionStartMillis(null)
        // A Pomodoro break is rest, not study, so it is never saved to the study history.
        if (state.pomodoroPhase?.isBreak == true) return
        val duration = if (completed) {
            state.countdownTotalMillis
        } else {
            TimerLogic.elapsedMillis(state, now())
        }
        if (!completed && duration < TimerHistoryLogic.MIN_SESSION_MILLIS) return
        history.add(
            TimerSession(
                startedAtMillis = startedAt,
                durationMillis = duration,
                mode = state.mode,
                plannedMillis = if (state.mode == TimerMode.COUNTDOWN) state.countdownTotalMillis else null,
                completed = completed
            )
        )
    }

    fun keepScreenOnInFullscreen(): Boolean = store.keepScreenOnInFullscreen()

    fun setKeepScreenOnInFullscreen(value: Boolean) = store.setKeepScreenOnInFullscreen(value)

    fun restoreAfterReboot(): Boolean {
        val current = _state.value
        // A reboot clears notifications, so show the running or paused timer again.
        ongoing.update(current, now())
        // A reboot also clears alarms: bring back a waiting auto-start, or drop it if it is past.
        _autoStartAt.value?.let { at ->
            if (now() < at) autoStartAlarm.schedule(at) else cancelAutoStart()
        }
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
                    ongoing = TimerOngoingNotifier(appContext),
                    history = TimerHistoryStore.get(appContext),
                    autoStartAlarm = AutoStartAlarm(appContext)
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
