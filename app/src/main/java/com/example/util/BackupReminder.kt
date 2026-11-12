package com.example.util

import android.content.Context
import com.example.util.drive.DriveBackupManager
import com.example.util.drive.DrivePrefs

/**
 * Decides when to remind the person that their backup is old.
 *
 * Notes, tasks and courses live only on the phone, so a lost or reset phone means lost data. The
 * reminder counts the newest of two things: the last time the Google Drive copy was known to be
 * current, and the last time a backup file was saved from Settings.
 */
object BackupReminder {

    /** The reminder appears when the newest backup is this many days old. */
    const val STALE_AFTER_DAYS = 7

    /** After "Later", the reminder stays away this many days. */
    const val SNOOZE_DAYS = 3

    private const val DAY_MS = 24L * 60 * 60 * 1000

    data class Reminder(
        /** True when no backup was ever made. */
        val neverBackedUp: Boolean,
        /** Whole days since the newest backup. Only meaningful when [neverBackedUp] is false. */
        val daysAgo: Int
    ) {
        val message: String
            get() = if (neverBackedUp) {
                "Your courses, tasks and notes are only on this phone, and you have not made a backup yet."
            } else {
                "Your last backup was $daysAgo days ago. Your courses, tasks and notes are only on this phone."
            }
    }

    /**
     * Pure decision, easy to test.
     *
     * @param hasData false for an empty app: there is nothing to lose, so no reminder.
     * @param lastBackupAt newest known backup in epoch millis, 0 if there never was one.
     * @param trackingStartedAt when the app first noticed data without any backup, 0 if not yet.
     *        A person who never backed up gets [STALE_AFTER_DAYS] days from that moment, so a new
     *        install is not nagged on day one.
     * @param snoozedUntil epoch millis before which the reminder stays hidden.
     */
    fun evaluate(
        hasData: Boolean,
        lastBackupAt: Long,
        trackingStartedAt: Long,
        snoozedUntil: Long,
        now: Long
    ): Reminder? {
        if (!hasData) return null
        if (now < snoozedUntil) return null

        val hasBackup = lastBackupAt > 0L
        val baseline = if (hasBackup) lastBackupAt else trackingStartedAt
        if (baseline <= 0L) return null

        // A clock set back must not hide the reminder forever or show a negative age.
        val age = (now - baseline).coerceAtLeast(0L)
        if (age < STALE_AFTER_DAYS * DAY_MS) return null

        return Reminder(
            neverBackedUp = !hasBackup,
            daysAgo = (age / DAY_MS).toInt()
        )
    }

    /** Remembers that a backup file was just saved from Settings. */
    fun recordLocalExport(context: Context, now: Long = System.currentTimeMillis()) {
        BackupReminderPrefs(context).lastLocalExportAt = now
    }

    /** Hides the reminder for [SNOOZE_DAYS] days. */
    fun snooze(context: Context, now: Long = System.currentTimeMillis()) {
        BackupReminderPrefs(context).snoozedUntil = now + SNOOZE_DAYS * DAY_MS
    }

    /** Reads the saved state and returns the reminder to show, or null. Call off the main thread. */
    suspend fun check(context: Context, now: Long = System.currentTimeMillis()): Reminder? {
        val prefs = BackupReminderPrefs(context)
        val hasData = !DriveBackupManager.isDatabaseEmpty(context)
        if (!hasData) return null

        val drive = DrivePrefs(context)
        val driveAt = if (drive.isConnected) drive.lastSafeAt else 0L
        val lastBackupAt = maxOf(driveAt, prefs.lastLocalExportAt)

        if (lastBackupAt <= 0L && prefs.trackingStartedAt <= 0L) {
            prefs.trackingStartedAt = now
        }

        return evaluate(
            hasData = true,
            lastBackupAt = lastBackupAt,
            trackingStartedAt = prefs.trackingStartedAt,
            snoozedUntil = prefs.snoozedUntil,
            now = now
        )
    }
}

/** Saved state for [BackupReminder]. */
class BackupReminderPrefs(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences("backup_reminder_prefs", Context.MODE_PRIVATE)

    /** Last time a backup file was saved from Settings. */
    var lastLocalExportAt: Long
        get() = prefs.getLong("last_local_export_at", 0L)
        set(value) = prefs.edit().putLong("last_local_export_at", value).apply()

    /** When data without any backup was first noticed. */
    var trackingStartedAt: Long
        get() = prefs.getLong("tracking_started_at", 0L)
        set(value) = prefs.edit().putLong("tracking_started_at", value).apply()

    /** The reminder stays hidden before this time. */
    var snoozedUntil: Long
        get() = prefs.getLong("snoozed_until", 0L)
        set(value) = prefs.edit().putLong("snoozed_until", value).apply()
}
