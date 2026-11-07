package com.example.util.drive

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DrivePrefsTest {

    private lateinit var context: Context
    private lateinit var prefs: DrivePrefs

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("drive_backup_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        prefs = DrivePrefs(context)
    }

    @Test
    fun testDefaults() {
        assertNull(prefs.connectedEmail)
        assertFalse(prefs.isConnected)
        assertFalse(prefs.autoEnabled)
        assertTrue(prefs.wifiOnly)
        assertTrue(prefs.includeAttachments)
        assertEquals(0L, prefs.lastSuccessAt)
        assertEquals(0L, prefs.lastAttemptAt)
        assertNull(prefs.lastError)
        assertNull(prefs.lastHash)
        assertEquals(0, prefs.consecutiveFailures)
        assertFalse(prefs.failureNotified)
        assertFalse(prefs.restoreOfferDismissed)
        assertEquals(0, prefs.migrationPromptCount)
        assertEquals(0L, prefs.migrationNextPromptAt)
        assertNull(prefs.lastCounts)
    }

    @Test
    fun testForgetAccount() {
        // Set values across all fields
        prefs.connectedEmail = "student@example.com"
        prefs.autoEnabled = true
        prefs.wifiOnly = false
        prefs.includeAttachments = false
        prefs.lastSuccessAt = 1000L
        prefs.lastAttemptAt = 2000L
        prefs.lastError = "Temporary network error"
        prefs.lastHash = "hash123"
        prefs.consecutiveFailures = 2
        prefs.failureNotified = true
        prefs.firstFailureAt = 5000L
        prefs.restoreOfferDismissed = true
        prefs.migrationPromptCount = 3
        prefs.migrationNextPromptAt = 5000L
        prefs.lastCounts = "2 courses, 5 tasks, 3 notes"

        assertTrue(prefs.isConnected)

        // Clear account
        prefs.forgetAccount()

        // Account-related and transient fields must be cleared
        assertNull(prefs.connectedEmail)
        assertFalse(prefs.isConnected)
        assertFalse(prefs.autoEnabled)
        assertNull(prefs.lastHash)
        assertNull(prefs.lastError)
        assertEquals(0, prefs.consecutiveFailures)
        assertFalse(prefs.failureNotified)
        assertEquals(0L, prefs.firstFailureAt)

        // General settings and prompt records must be preserved
        assertFalse(prefs.wifiOnly)
        assertFalse(prefs.includeAttachments)
        assertTrue(prefs.restoreOfferDismissed)
        assertEquals(3, prefs.migrationPromptCount)
        assertEquals(5000L, prefs.migrationNextPromptAt)
        assertEquals(1000L, prefs.lastSuccessAt)
        assertEquals(2000L, prefs.lastAttemptAt)
        assertEquals("2 courses, 5 tasks, 3 notes", prefs.lastCounts)
    }
}
