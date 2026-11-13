package com.example.util.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PomodoroLogicTest {

    private val settings = PomodoroSettings(
        focusMinutes = 25,
        shortBreakMinutes = 5,
        longBreakMinutes = 15,
        roundsBeforeLongBreak = 4
    )

    @Test
    fun cycleRunsFocusShortBreakAndEndsWithALongBreak() {
        var phase = PomodoroPhase.FOCUS
        var round = 1
        val seen = mutableListOf<Pair<PomodoroPhase, Int>>()
        repeat(8) {
            val next = PomodoroLogic.nextPhase(phase, round, settings)
            phase = next.first
            round = next.second
            seen.add(next)
        }
        assertEquals(
            listOf(
                PomodoroPhase.SHORT_BREAK to 1,
                PomodoroPhase.FOCUS to 2,
                PomodoroPhase.SHORT_BREAK to 2,
                PomodoroPhase.FOCUS to 3,
                PomodoroPhase.SHORT_BREAK to 3,
                PomodoroPhase.FOCUS to 4,
                PomodoroPhase.LONG_BREAK to 4,
                PomodoroPhase.FOCUS to 1
            ),
            seen
        )
    }

    @Test
    fun oneRoundBeforeLongBreakMeansEveryFocusIsFollowedByALongBreak() {
        val s = settings.copy(roundsBeforeLongBreak = 1)
        assertEquals(PomodoroPhase.LONG_BREAK to 1, PomodoroLogic.nextPhase(PomodoroPhase.FOCUS, 1, s))
    }

    @Test
    fun phaseLengthsComeFromTheSettings() {
        assertEquals(25 * 60_000L, PomodoroLogic.phaseMillis(PomodoroPhase.FOCUS, settings))
        assertEquals(5 * 60_000L, PomodoroLogic.phaseMillis(PomodoroPhase.SHORT_BREAK, settings))
        assertEquals(15 * 60_000L, PomodoroLogic.phaseMillis(PomodoroPhase.LONG_BREAK, settings))
    }

    @Test
    fun enableStartsAtFocusRoundOneAsACountdown() {
        val s = PomodoroLogic.enable(TimerState(mode = TimerMode.STOPWATCH), settings)
        assertEquals(TimerMode.COUNTDOWN, s.mode)
        assertEquals(PomodoroPhase.FOCUS, s.pomodoroPhase)
        assertEquals(1, s.pomodoroRound)
        assertEquals(25 * 60_000L, s.countdownTotalMillis)
    }

    @Test
    fun enableAndDisableDoNothingWhileTheTimerIsInProgress() {
        val running = TimerState(mode = TimerMode.COUNTDOWN, runningSinceMillis = 1_000L)
        assertEquals(running, PomodoroLogic.enable(running, settings))

        val active = PomodoroLogic.enable(TimerState(), settings)
        val activeRunning = active.copy(runningSinceMillis = 1_000L)
        assertEquals(activeRunning, PomodoroLogic.disable(activeRunning, 45))
    }

    @Test
    fun disableRestoresThePlainCountdownLength() {
        val active = PomodoroLogic.enable(TimerState(), settings)
        val plain = PomodoroLogic.disable(active, 45)
        assertNull(plain.pomodoroPhase)
        assertEquals(45 * 60_000L, plain.countdownTotalMillis)
        assertFalse(PomodoroLogic.isActive(plain))
    }

    @Test
    fun advanceGivesAStoppedTimerWithTheNextPhaseLength() {
        val finished = PomodoroLogic.enable(TimerState(), settings)
            .copy(accumulatedMillis = 25 * 60_000L)
        val next = PomodoroLogic.advance(finished, settings)
        assertEquals(PomodoroPhase.SHORT_BREAK, next.pomodoroPhase)
        assertEquals(5 * 60_000L, next.countdownTotalMillis)
        assertTrue(TimerLogic.isIdle(next))
    }

    @Test
    fun advanceDoesNothingWhenPomodoroIsOff() {
        val s = TimerState(mode = TimerMode.COUNTDOWN, accumulatedMillis = 5_000L)
        assertEquals(s, PomodoroLogic.advance(s, settings))
    }

    @Test
    fun newSettingsChangeAnIdlePhaseButNotARunningOne() {
        val idle = PomodoroLogic.enable(TimerState(), settings)
        val longer = settings.copy(focusMinutes = 50)
        assertEquals(50 * 60_000L, PomodoroLogic.withSettings(idle, longer).countdownTotalMillis)

        val running = idle.copy(runningSinceMillis = 1_000L)
        assertEquals(running, PomodoroLogic.withSettings(running, longer))
    }

    @Test
    fun mainButtonLabelNamesWhatComesNext() {
        val focus = PomodoroLogic.enable(TimerState(), settings)
        assertEquals("Start break", PomodoroLogic.nextActionLabel(focus, settings))
        val shortBreak = focus.copy(pomodoroPhase = PomodoroPhase.SHORT_BREAK)
        assertEquals("Start focus", PomodoroLogic.nextActionLabel(shortBreak, settings))
        assertNull(PomodoroLogic.nextActionLabel(TimerState(), settings))
    }

    @Test
    fun statusLabelsDescribeThePhase() {
        assertEquals("Focus", PomodoroLogic.statusLabel(PomodoroPhase.FOCUS, false, false))
        assertEquals("Long break", PomodoroLogic.statusLabel(PomodoroPhase.LONG_BREAK, false, false))
        assertEquals("Paused", PomodoroLogic.statusLabel(PomodoroPhase.FOCUS, true, false))
        assertEquals("Focus done", PomodoroLogic.statusLabel(PomodoroPhase.FOCUS, false, true))
        assertEquals("Break over", PomodoroLogic.statusLabel(PomodoroPhase.SHORT_BREAK, false, true))
    }

    @Test
    fun settingsAreKeptInsideTheirAllowedRanges() {
        val s = PomodoroSettings(0, 999, -5, 99).sanitized()
        assertEquals(PomodoroSettings.MIN_MINUTES, s.focusMinutes)
        assertEquals(PomodoroSettings.MAX_BREAK_MINUTES, s.shortBreakMinutes)
        assertEquals(PomodoroSettings.MIN_MINUTES, s.longBreakMinutes)
        assertEquals(PomodoroSettings.MAX_ROUNDS, s.roundsBeforeLongBreak)
    }

    @Test
    fun alertSettingsDefaultToRingingWithNoAutoStart() {
        val a = PomodoroAlertSettings()
        assertTrue(a.ringLikeAlarm)
        assertFalse(a.autoStartNext)
        assertFalse(a.stopAfterLongBreak)
    }

    @Test
    fun autoStartFollowsEveryRoundOnlyWhenTurnedOn() {
        val off = PomodoroAlertSettings(autoStartNext = false)
        val on = PomodoroAlertSettings(autoStartNext = true)
        PomodoroPhase.values().forEach { phase ->
            assertFalse(PomodoroLogic.shouldAutoStart(phase, off))
            assertTrue(PomodoroLogic.shouldAutoStart(phase, on))
        }
    }

    @Test
    fun stopAfterLongBreakOnlyHoldsBackTheCycleRestart() {
        val a = PomodoroAlertSettings(autoStartNext = true, stopAfterLongBreak = true)
        assertTrue(PomodoroLogic.shouldAutoStart(PomodoroPhase.FOCUS, a))
        assertTrue(PomodoroLogic.shouldAutoStart(PomodoroPhase.SHORT_BREAK, a))
        assertFalse(PomodoroLogic.shouldAutoStart(PomodoroPhase.LONG_BREAK, a))
        // Without auto-start the extra option changes nothing.
        val offToo = PomodoroAlertSettings(autoStartNext = false, stopAfterLongBreak = true)
        assertFalse(PomodoroLogic.shouldAutoStart(PomodoroPhase.FOCUS, offToo))
    }

    @Test
    fun autoStartLabelNamesTheNextRound() {
        val focus = PomodoroLogic.enable(TimerState(), settings)
        assertEquals("Short break starts in 7s", PomodoroLogic.autoStartLabel(focus, settings, 7))

        val lastFocus = focus.copy(pomodoroRound = settings.roundsBeforeLongBreak)
        assertEquals("Long break starts in 10s", PomodoroLogic.autoStartLabel(lastFocus, settings, 10))

        val shortBreak = focus.copy(pomodoroPhase = PomodoroPhase.SHORT_BREAK)
        assertEquals("Focus starts in 1s", PomodoroLogic.autoStartLabel(shortBreak, settings, 1))
        // Never shows a negative number.
        assertEquals("Focus starts in 0s", PomodoroLogic.autoStartLabel(shortBreak, settings, -3))
        // Not a Pomodoro timer: nothing to say.
        assertNull(PomodoroLogic.autoStartLabel(TimerState(), settings, 5))
    }

    @Test
    fun autoStartWaitsTenSeconds() {
        assertEquals(10_000L, PomodoroLogic.AUTO_START_DELAY_MILLIS)
    }
}
