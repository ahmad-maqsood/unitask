package com.example

import com.example.util.UpdateChecker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun testIsNewer() {
        assertTrue(UpdateChecker.isNewer("v3.0.0", "3.0.0-beta-2"))
        assertTrue(UpdateChecker.isNewer("v3.0.0-beta-3", "3.0.0-beta-2"))
        assertFalse(UpdateChecker.isNewer("v3.0.0-beta-2", "3.0.0-beta-2"))
        assertFalse(UpdateChecker.isNewer("v3.0.0-beta2", "3.0.0-beta-2"))
        assertFalse(UpdateChecker.isNewer("v3.0.0-beta-2", "3.0.0"))
        assertTrue(UpdateChecker.isNewer("v3.0.1", "3.0.0"))
        assertFalse(UpdateChecker.isNewer("v2.9.9", "3.0.0-beta-2"))
        assertTrue(UpdateChecker.isNewer("v1.2.0", "1.1"))
    }
}

