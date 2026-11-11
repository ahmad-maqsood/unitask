package com.example.util.timer

/** The three kinds of stretch in a Pomodoro cycle. */
enum class PomodoroPhase(val label: String) {
    FOCUS("Focus"),
    SHORT_BREAK("Short break"),
    LONG_BREAK("Long break");

    val isBreak: Boolean get() = this != FOCUS
}

/** How long each stretch lasts, and how many focus rounds come before the long break. */
data class PomodoroSettings(
    val focusMinutes: Int = DEFAULT_FOCUS_MINUTES,
    val shortBreakMinutes: Int = DEFAULT_SHORT_BREAK_MINUTES,
    val longBreakMinutes: Int = DEFAULT_LONG_BREAK_MINUTES,
    val roundsBeforeLongBreak: Int = DEFAULT_ROUNDS
) {
    /** The same settings with every value pulled back into its allowed range. */
    fun sanitized(): PomodoroSettings = PomodoroSettings(
        focusMinutes = focusMinutes.coerceIn(MIN_MINUTES, MAX_FOCUS_MINUTES),
        shortBreakMinutes = shortBreakMinutes.coerceIn(MIN_MINUTES, MAX_BREAK_MINUTES),
        longBreakMinutes = longBreakMinutes.coerceIn(MIN_MINUTES, MAX_BREAK_MINUTES),
        roundsBeforeLongBreak = roundsBeforeLongBreak.coerceIn(MIN_ROUNDS, MAX_ROUNDS)
    )

    companion object {
        const val DEFAULT_FOCUS_MINUTES = 25
        const val DEFAULT_SHORT_BREAK_MINUTES = 5
        const val DEFAULT_LONG_BREAK_MINUTES = 15
        const val DEFAULT_ROUNDS = 4
        const val MIN_MINUTES = 1
        const val MAX_FOCUS_MINUTES = 120
        const val MAX_BREAK_MINUTES = 60
        const val MIN_ROUNDS = 1
        const val MAX_ROUNDS = 10
    }
}

/**
 * Pure Pomodoro rules. A Pomodoro timer is an ordinary countdown whose length and label come from
 * the current phase, so the alarm, the notifications and the restart recovery all work unchanged.
 *
 * The cycle is: focus, short break, focus, short break, ... and after the last focus round of the
 * cycle a long break, then it starts again at round 1.
 */
object PomodoroLogic {

    fun isActive(s: TimerState): Boolean = s.pomodoroPhase != null

    fun phaseMillis(phase: PomodoroPhase, settings: PomodoroSettings): Long {
        val minutes = when (phase) {
            PomodoroPhase.FOCUS -> settings.focusMinutes
            PomodoroPhase.SHORT_BREAK -> settings.shortBreakMinutes
            PomodoroPhase.LONG_BREAK -> settings.longBreakMinutes
        }
        return minutes * 60_000L
    }

    /** The phase after [phase] and the focus round it belongs to. */
    fun nextPhase(
        phase: PomodoroPhase,
        round: Int,
        settings: PomodoroSettings
    ): Pair<PomodoroPhase, Int> = when (phase) {
        PomodoroPhase.FOCUS ->
            if (round >= settings.roundsBeforeLongBreak) {
                PomodoroPhase.LONG_BREAK to round
            } else {
                PomodoroPhase.SHORT_BREAK to round
            }
        PomodoroPhase.SHORT_BREAK -> PomodoroPhase.FOCUS to round + 1
        PomodoroPhase.LONG_BREAK -> PomodoroPhase.FOCUS to 1
    }

    /** Turns Pomodoro on, at focus round 1. Only an idle timer can change mode. */
    fun enable(s: TimerState, settings: PomodoroSettings): TimerState {
        if (!TimerLogic.isIdle(s) || isActive(s)) return s
        return s.copy(
            mode = TimerMode.COUNTDOWN,
            countdownTotalMillis = phaseMillis(PomodoroPhase.FOCUS, settings),
            pomodoroPhase = PomodoroPhase.FOCUS,
            pomodoroRound = 1
        )
    }

    /** Turns Pomodoro off and puts the plain countdown back at [countdownMinutes]. */
    fun disable(s: TimerState, countdownMinutes: Int): TimerState {
        if (!TimerLogic.isIdle(s) || !isActive(s)) return s
        val minutes = countdownMinutes.coerceIn(
            TimerLogic.MIN_COUNTDOWN_MINUTES,
            TimerLogic.MAX_COUNTDOWN_MINUTES
        )
        return s.copy(
            countdownTotalMillis = minutes * 60_000L,
            pomodoroPhase = null,
            pomodoroRound = 1
        )
    }

    /** Moves to the next phase, stopped and ready to start. Does nothing when Pomodoro is off. */
    fun advance(s: TimerState, settings: PomodoroSettings): TimerState {
        val phase = s.pomodoroPhase ?: return s
        val (next, round) = nextPhase(phase, s.pomodoroRound, settings)
        return s.copy(
            mode = TimerMode.COUNTDOWN,
            countdownTotalMillis = phaseMillis(next, settings),
            accumulatedMillis = 0L,
            runningSinceMillis = null,
            pomodoroPhase = next,
            pomodoroRound = round
        )
    }

    /** After a settings change: an idle timer picks up the new length of its current phase. */
    fun withSettings(s: TimerState, settings: PomodoroSettings): TimerState {
        val phase = s.pomodoroPhase ?: return s
        if (!TimerLogic.isIdle(s)) return s
        return s.copy(countdownTotalMillis = phaseMillis(phase, settings))
    }

    /** The text of the main button once a phase has finished: "Start break" or "Start focus". */
    fun nextActionLabel(s: TimerState, settings: PomodoroSettings): String? {
        val phase = s.pomodoroPhase ?: return null
        val (next, _) = nextPhase(phase, s.pomodoroRound, settings)
        return if (next.isBreak) "Start break" else "Start focus"
    }

    /** The short line under the clock: what the current phase is, or that it just ended. */
    fun statusLabel(
        phase: PomodoroPhase,
        isPaused: Boolean,
        isFinished: Boolean
    ): String = when {
        isFinished -> if (phase.isBreak) "Break over" else "Focus done"
        isPaused -> "Paused"
        else -> phase.label
    }
}
