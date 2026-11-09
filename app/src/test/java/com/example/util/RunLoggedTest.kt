package com.example.util

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RunLoggedTest {

    @Test
    fun returnsTheResultWhenTheBlockWorks() {
        assertEquals(42, runLogged("test") { 42 })
    }

    @Test
    fun returnsNullInsteadOfThrowingWhenTheBlockFails() {
        assertNull(runLogged("test") { error("boom") })
    }

    @Test
    fun passesCancellationOn() {
        try {
            runLogged("test") { throw CancellationException("stop") }
            fail("CancellationException must not be swallowed")
        } catch (_: CancellationException) {
            // expected
        }
    }
}
