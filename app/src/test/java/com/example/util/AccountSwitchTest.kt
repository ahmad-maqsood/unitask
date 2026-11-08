package com.example.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountSwitchTest {

    @Test
    fun shouldResetGroupState_returnsFalseWhenEitherUidIsNullOrBlank() {
        assertFalse(shouldResetGroupState(null, null))
        assertFalse(shouldResetGroupState(null, "user2"))
        assertFalse(shouldResetGroupState("user1", null))
        assertFalse(shouldResetGroupState("", "user2"))
        assertFalse(shouldResetGroupState("user1", ""))
        assertFalse(shouldResetGroupState("   ", "user2"))
        assertFalse(shouldResetGroupState("user1", "   "))
    }

    @Test
    fun shouldResetGroupState_returnsFalseWhenUidsAreEqual() {
        assertFalse(shouldResetGroupState("user1", "user1"))
    }

    @Test
    fun shouldResetGroupState_returnsTrueWhenBothAreNonBlankAndDifferent() {
        assertTrue(shouldResetGroupState("user1", "user2"))
        assertTrue(shouldResetGroupState("user-a", "user-b"))
    }
}
