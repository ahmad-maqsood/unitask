package com.example.util.drive

import android.content.Context
import android.content.SharedPreferences

/**
 * Saved settings and status for Google Drive backup.
 *
 * Stored in SharedPreferences "drive_backup_prefs" so that settings survive
 * app restarts without requiring database tables or migrations.
 */
class DrivePrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Connected Google account email, used for display only. Null when not connected. */
    var connectedEmail: String?
        get() = prefs.getString(KEY_CONNECTED_EMAIL, null)
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_CONNECTED_EMAIL) else putString(KEY_CONNECTED_EMAIL, value)
            }.apply()
        }

    /** True if a Google Drive account is currently linked. */
    val isConnected: Boolean
        get() = connectedEmail != null

    /** Whether automatic daily backups are enabled by the user. */
    var autoEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_ENABLED, value).apply()

    /** Whether backups should only run over an unmetered Wi-Fi connection. */
    var wifiOnly: Boolean
        get() = prefs.getBoolean(KEY_WIFI_ONLY, true)
        set(value) = prefs.edit().putBoolean(KEY_WIFI_ONLY, value).apply()

    /** Whether note attachments (images) are included in the backup. */
    var includeAttachments: Boolean
        get() = prefs.getBoolean(KEY_INCLUDE_ATTACHMENTS, true)
        set(value) = prefs.edit().putBoolean(KEY_INCLUDE_ATTACHMENTS, value).apply()

    /** Timestamp in epoch milliseconds of the last successful backup. */
    var lastSuccessAt: Long
        get() = prefs.getLong(KEY_LAST_SUCCESS_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SUCCESS_AT, value).apply()

    /** Timestamp in epoch milliseconds of the last backup attempt. */
    var lastAttemptAt: Long
        get() = prefs.getLong(KEY_LAST_ATTEMPT_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_ATTEMPT_AT, value).apply()

    /** Plain English description of the last error encountered, if any. */
    var lastError: String?
        get() = prefs.getString(KEY_LAST_ERROR, null)
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_LAST_ERROR) else putString(KEY_LAST_ERROR, value)
            }.apply()
        }

    /** Content hash of the last successful backup, used to skip unchanged data. */
    var lastHash: String?
        get() = prefs.getString(KEY_LAST_HASH, null)
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_LAST_HASH) else putString(KEY_LAST_HASH, value)
            }.apply()
        }

    /** Number of consecutive failed backup attempts. */
    var consecutiveFailures: Int
        get() = prefs.getInt(KEY_CONSECUTIVE_FAILURES, 0)
        set(value) = prefs.edit().putInt(KEY_CONSECUTIVE_FAILURES, value).apply()

    /** Epoch millis of the first failure in the current failure streak. 0 means no streak. */
    var firstFailureAt: Long
        get() = prefs.getLong(KEY_FIRST_FAILURE_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_FIRST_FAILURE_AT, value).apply()
        
    /** Whether the user has been notified about persistent backup failures. */
    var failureNotified: Boolean
        get() = prefs.getBoolean(KEY_FAILURE_NOTIFIED, false)
        set(value) = prefs.edit().putBoolean(KEY_FAILURE_NOTIFIED, value).apply()

    /** Whether the user dismissed the offer to restore data on first connect. */
    var restoreOfferDismissed: Boolean
        get() = prefs.getBoolean(KEY_RESTORE_OFFER_DISMISSED, false)
        set(value) = prefs.edit().putBoolean(KEY_RESTORE_OFFER_DISMISSED, value).apply()

    /** How many times the user has seen the prompt to migrate to Drive. */
    var migrationPromptCount: Int
        get() = prefs.getInt(KEY_MIGRATION_PROMPT_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_MIGRATION_PROMPT_COUNT, value).apply()

    /** Epoch millis before which the migration prompt should not be shown again. */
    var migrationNextPromptAt: Long
        get() = prefs.getLong(KEY_MIGRATION_NEXT_PROMPT_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_MIGRATION_NEXT_PROMPT_AT, value).apply()

    /** Short summary of backed up entities (for example "3 courses, 12 tasks, 4 notes"). */
    var lastCounts: String?
        get() = prefs.getString(KEY_LAST_COUNTS, null)
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_LAST_COUNTS) else putString(KEY_LAST_COUNTS, value)
            }.apply()
        }

    /**
     * Clears account connection and transient sync state.
     * Retains user preferences (wifiOnly, includeAttachments) and prompt records.
     */
    fun forgetAccount() {
        prefs.edit()
            .remove(KEY_CONNECTED_EMAIL)
            .remove(KEY_AUTO_ENABLED)
            .remove(KEY_LAST_HASH)
            .remove(KEY_LAST_ERROR)
            .remove(KEY_CONSECUTIVE_FAILURES)
            .remove(KEY_FIRST_FAILURE_AT)
            .remove(KEY_FAILURE_NOTIFIED)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "drive_backup_prefs"
        private const val KEY_CONNECTED_EMAIL = "connected_email"
        private const val KEY_AUTO_ENABLED = "auto_enabled"
        private const val KEY_WIFI_ONLY = "wifi_only"
        private const val KEY_INCLUDE_ATTACHMENTS = "include_attachments"
        private const val KEY_LAST_SUCCESS_AT = "last_success_at"
        private const val KEY_LAST_ATTEMPT_AT = "last_attempt_at"
        private const val KEY_LAST_ERROR = "last_error"
        private const val KEY_LAST_HASH = "last_hash"
        private const val KEY_CONSECUTIVE_FAILURES = "consecutive_failures"
        private const val KEY_FIRST_FAILURE_AT = "first_failure_at"
        private const val KEY_FAILURE_NOTIFIED = "failure_notified"
        private const val KEY_RESTORE_OFFER_DISMISSED = "restore_offer_dismissed"
        private const val KEY_MIGRATION_PROMPT_COUNT = "migration_prompt_count"
        private const val KEY_MIGRATION_NEXT_PROMPT_AT = "migration_next_prompt_at"
        private const val KEY_LAST_COUNTS = "last_counts"
    }
}
