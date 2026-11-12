package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GetStartedLogicTest {

    @Test
    fun nothingDoneGivesNoSteps() {
        val done = GetStartedLogic.detect(false, false, false, false, false)
        assertTrue(done.isEmpty())
        assertFalse(GetStartedLogic.isFinished(done))
    }

    @Test
    fun eachFlagTicksItsOwnStep() {
        assertEquals(setOf(GetStartedStep.COURSE), GetStartedLogic.detect(true, false, false, false, false))
        assertEquals(setOf(GetStartedStep.DEADLINE), GetStartedLogic.detect(false, true, false, false, false))
        assertEquals(setOf(GetStartedStep.REMINDER), GetStartedLogic.detect(false, false, true, false, false))
        assertEquals(setOf(GetStartedStep.CLASS), GetStartedLogic.detect(false, false, false, true, false))
        assertEquals(setOf(GetStartedStep.TIMER), GetStartedLogic.detect(false, false, false, false, true))
        assertEquals(setOf(GetStartedStep.BACKUP), GetStartedLogic.detect(false, false, false, false, false, driveConnected = true))
    }

    @Test
    fun phoneSettingFlagsTickTheirOwnSteps() {
        assertEquals(setOf(GetStartedStep.NOTIFICATIONS), GetStartedLogic.detect(false, false, false, false, false, notificationsOn = true))
        assertEquals(setOf(GetStartedStep.BATTERY), GetStartedLogic.detect(false, false, false, false, false, batteryOptimizationOff = true))
        assertEquals(setOf(GetStartedStep.AUTOSTART), GetStartedLogic.detect(false, false, false, false, false, autostartDone = true))
    }

    @Test
    fun allNineStepsFinishTheChecklist() {
        val done = GetStartedLogic.detect(true, true, true, true, true, true, true, true, true)
        assertEquals(9, done.size)
        assertTrue(GetStartedLogic.isFinished(done))
    }

    @Test
    fun fourStepsAreNotFinished() {
        assertFalse(GetStartedLogic.isFinished(GetStartedLogic.detect(true, true, true, true, false)))
    }

    @Test
    fun backupStepIsRequiredEvenWithoutAutostart() {
        val required = GetStartedLogic.requiredSteps(needsAutostart = false)
        assertTrue(GetStartedStep.BACKUP in required)
        val withoutBackup = GetStartedLogic.detect(true, true, true, true, true, true, true, false)
        assertFalse(GetStartedLogic.isFinished(withoutBackup, required))
    }

    @Test
    fun autostartIsNotRequiredOnPhonesWithoutIt() {
        val required = GetStartedLogic.requiredSteps(needsAutostart = false)
        assertFalse(GetStartedStep.AUTOSTART in required)
        val done = GetStartedLogic.detect(true, true, true, true, true, true, true, false, true)
        assertTrue(GetStartedLogic.isFinished(done, required))
        assertFalse(GetStartedLogic.isFinished(done, GetStartedLogic.requiredSteps(needsAutostart = true)))
    }
}
