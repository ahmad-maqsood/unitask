package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingLogicTest {

    @Test
    fun onlyNotificationsAndBatteryAreAskedFor() {
        assertEquals(
            listOf(OnboardingSetting.NOTIFICATIONS, OnboardingSetting.BATTERY),
            OnboardingSetting.entries
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
    fun allOnNeedsEverySetting() {
        assertFalse(OnboardingLogic.allOn(emptySet()))
        assertFalse(OnboardingLogic.allOn(setOf(OnboardingSetting.NOTIFICATIONS)))
        assertFalse(OnboardingLogic.allOn(setOf(OnboardingSetting.BATTERY)))
        assertTrue(OnboardingLogic.allOn(setOf(OnboardingSetting.NOTIFICATIONS, OnboardingSetting.BATTERY)))
    }
}