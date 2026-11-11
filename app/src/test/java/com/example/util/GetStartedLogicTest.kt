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
    }

    @Test
    fun allFiveStepsFinishTheChecklist() {
        val done = GetStartedLogic.detect(true, true, true, true, true)
        assertEquals(5, done.size)
        assertTrue(GetStartedLogic.isFinished(done))
    }

    @Test
    fun fourStepsAreNotFinished() {
        assertFalse(GetStartedLogic.isFinished(GetStartedLogic.detect(true, true, true, true, false)))
    }
}
