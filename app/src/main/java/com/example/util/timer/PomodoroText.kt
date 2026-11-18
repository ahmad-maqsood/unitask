package com.example.util.timer

import android.content.Context
import com.example.R

/**
 * Pomodoro and timer wording in the language the app is using. The rules in [PomodoroLogic] stay
 * language free (and unit tested); screens and notifications use these to show text.
 */
object PomodoroText {
    fun phase(context: Context, phase: PomodoroPhase): String = context.getString(
        when (phase) {
            PomodoroPhase.FOCUS -> R.string.pomodoro_phase_focus
            PomodoroPhase.SHORT_BREAK -> R.string.pomodoro_phase_short_break
            PomodoroPhase.LONG_BREAK -> R.string.pomodoro_phase_long_break
        }
    )

    fun status(context: Context, phase: PomodoroPhase, isPaused: Boolean, isFinished: Boolean): String = when {
        isFinished -> context.getString(
            if (phase.isBreak) R.string.pomodoro_status_break_over else R.string.pomodoro_status_focus_done
        )
        isPaused -> context.getString(R.string.timer_status_paused)
        else -> phase(context, phase)
    }

    fun nextAction(context: Context, s: TimerState, settings: PomodoroSettings): String? {
        val phase = s.pomodoroPhase ?: return null
        val (next, _) = PomodoroLogic.nextPhase(phase, s.pomodoroRound, settings)
        return context.getString(
            if (next.isBreak) R.string.timer_fullscreen_start_break else R.string.timer_fullscreen_start_focus
        )
    }

    fun autoStart(context: Context, s: TimerState, settings: PomodoroSettings, secondsLeft: Int): String? {
        val phase = s.pomodoroPhase ?: return null
        val (next, _) = PomodoroLogic.nextPhase(phase, s.pomodoroRound, settings)
        return context.getString(R.string.pomodoro_starts_in, phase(context, next), secondsLeft.coerceAtLeast(0))
    }
}
