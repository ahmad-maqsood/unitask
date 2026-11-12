package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupReminderTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 1_800_000_000_000L

    private fun eval(
        hasData: Boolean = true,
        lastBackupAt: Long = 0L,
        trackingStartedAt: Long = 0L,
        snoozedUntil: Long = 0L
    ) = BackupReminder.evaluate(hasData, lastBackupAt, trackingStartedAt, snoozedUntil, now)

    @Test
    fun emptyApp_neverReminds() {
        assertNull(eval(hasData = false, lastBackupAt = now - 30 * day))
    }

    @Test
    fun recentBackup_noReminder() {
        assertNull(eval(lastBackupAt = now - 6 * day))
    }

    @Test
    fun oldBackup_reminds_withDaysAgo() {
        val r = eval(lastBackupAt = now - 9 * day - 5_000L)
        assertNotNull(r)
        assertFalse(r!!.neverBackedUp)
        assertEquals(9, r.daysAgo)
        assertTrue(r.message.contains("9 days ago"))
    }

    @Test
    fun exactlySevenDays_reminds() {
        assertNotNull(eval(lastBackupAt = now - 7 * day))
    }

    @Test
    fun neverBackedUp_waitsSevenDaysFromFirstNotice() {
        assertNull(eval(trackingStartedAt = now - 2 * day))
        val r = eval(trackingStartedAt = now - 8 * day)
        assertNotNull(r)
        assertTrue(r!!.neverBackedUp)
        assertTrue(r.message.contains("not made a backup"))
    }

    @Test
    fun notTrackedYet_noReminder() {
        assertNull(eval(lastBackupAt = 0L, trackingStartedAt = 0L))
    }

    @Test
    fun snoozed_hidesUntilItEnds() {
        assertNull(eval(lastBackupAt = now - 20 * day, snoozedUntil = now + day))
        assertNotNull(eval(lastBackupAt = now - 20 * day, snoozedUntil = now - 1))
    }

    @Test
    fun clockSetBack_doesNotCrashOrRemind() {
        assertNull(eval(lastBackupAt = now + 5 * day))
    }

    @Test
    fun backupBeatsTrackingStart() {
        // A backup made after tracking began is what counts.
        assertNull(eval(lastBackupAt = now - day, trackingStartedAt = now - 30 * day))
    }
}
