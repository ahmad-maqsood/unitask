package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingLogicTest {

    @Test
    fun autostartIsShownOnlyOnPhonesThatHaveIt() {
        assertEquals(
            listOf(OnboardingSetting.NOTIFICATIONS, OnboardingSetting.BATTERY, OnboardingSetting.AUTOSTART),
            OnboardingLogic.settingsFor(needsAutostart = true)
        )
        assertEquals(
            listOf(OnboardingSetting.NOTIFICATIONS, OnboardingSetting.BATTERY),
            OnboardingLogic.settingsFor(needsAutostart = false)
        )
    }

    @Test
    fun brandNewInstallShowsTheWelcomeScreens() {
        assertTrue(OnboardingLogic.shouldShow(alreadyDone = false, usedAppBefore = false))
    }

    @Test
    fun finishedOrSkippedWelcomeIsNotShownAgain() {
        assertFalse(OnboardingLogic.shouldShow(alreadyDone = true, usedAppBefore = false))
    }

    @Test
    fun peopleWhoUsedTheAppBeforeSkipTheWelcomeScreens() {
        assertFalse(OnboardingLogic.shouldShow(alreadyDone = false, usedAppBefore = true))
        assertFalse(OnboardingLogic.shouldShow(alreadyDone = true, usedAppBefore = true))
    }

    @Test
    fun allOnNeedsEverySettingThatIsShown() {
        val shown = OnboardingLogic.settingsFor(needsAutostart = true)
        assertFalse(OnboardingLogic.allOn(emptySet(), shown))
        assertFalse(
            OnboardingLogic.allOn(setOf(OnboardingSetting.NOTIFICATIONS, OnboardingSetting.BATTERY), shown)
        )
        assertTrue(OnboardingLogic.allOn(OnboardingSetting.entries.toSet(), shown))
    }

    @Test
    fun autostartDoesNotCountWhenThePhoneHasNone() {
        val shown = OnboardingLogic.settingsFor(needsAutostart = false)
        assertTrue(
            OnboardingLogic.allOn(setOf(OnboardingSetting.NOTIFICATIONS, OnboardingSetting.BATTERY), shown)
        )
    }
}