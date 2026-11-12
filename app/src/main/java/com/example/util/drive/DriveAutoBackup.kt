package com.example.util.drive

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.sync.DriveBackupWorker
import com.example.util.NotificationHistory
import java.util.concurrent.TimeUnit

/**
 * Coordinates automatic backups of user data to Google Drive.
 *
 * A backup is started shortly after something changes (see [DriveChangeWatcher] and
 * [onDataChanged]). The daily background job and the check when the app opens stay as a safety
 * net, for example for a change made while the phone had no connection.
 * Runs in the background via WorkManager when network conditions are met.
 */
object DriveAutoBackup {

    const val WORK_NAME = "unitask_drive_backup"
    const val PERIODIC_WORK_NAME = "unitask_drive_backup_periodic"
    const val INTERVAL_HOURS = 24L
    const val RETRY_INTERVAL_HOURS = 6L

    /**
     * How long to wait after a change before backing up. Every new change restarts the wait, so a
     * burst of edits (typing a note, ticking several tasks) is uploaded once, after it settles.
     */
    const val CHANGE_DEBOUNCE_SECONDS = 30L

    private const val ONE_HOUR_MS = 60 * 60 * 1000L
    private const val ONE_MINUTE_MS = 60 * 1000L
    private const val THREE_DAYS_MS = 3L * 24 * 60 * 60 * 1000L
    
    enum class Outcome {
        Done,
        Unchanged,
        NeedsReconnect,
        Retry,
        Failed
    }

    /**
     * Determines whether an automatic backup should be scheduled.
     * Pure function for simple and reliable testing.
     *
     * [dirty] means something changed since the last run. A backup is then due right away, unless
     * the last runs failed: after a failure it waits for the retry window, so a broken connection
     * is not hit again on every edit.
     */
    fun shouldRun(
        connected: Boolean,
        autoEnabled: Boolean,
        lastAttemptAt: Long,
        lastSuccessAt: Long,
        consecutiveFailures: Int,
        now: Long,
        dirty: Boolean = false
    ): Boolean {
        if (!connected || !autoEnabled) return false
        if (dirty && consecutiveFailures == 0) return true
        val effectiveAttemptAt = if (lastAttemptAt - now > ONE_MINUTE_MS) now else lastAttemptAt
        val thresholdHours = if (consecutiveFailures > 0) RETRY_INTERVAL_HOURS else INTERVAL_HOURS
        val thresholdMs = thresholdHours * ONE_HOUR_MS
        return now - effectiveAttemptAt >= thresholdMs
    }

    /**
     * Builds network constraints based on user Wi-Fi preference.
     */
    fun buildConstraints(wifiOnly: Boolean): Constraints {
        val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        return Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .build()
    }

    /**
     * Called when the app opens or a backup setting changes. Makes sure the daily background
     * job is registered, and enqueues a one-time backup straight away if one is due.
     */
    fun onAppOpen(context: Context) {
        try {
            val prefs = DrivePrefs(context)
            if (!prefs.isConnected || !prefs.autoEnabled) return

            DriveChangeWatcher.start(context)
            schedulePeriodic(context, prefs.wifiOnly)

            val now = System.currentTimeMillis()
            if (!shouldRun(
                    connected = prefs.isConnected,
                    autoEnabled = prefs.autoEnabled,
                    lastAttemptAt = prefs.lastAttemptAt,
                    lastSuccessAt = prefs.lastSuccessAt,
                    consecutiveFailures = prefs.consecutiveFailures,
                    now = now,
                    dirty = prefs.dirty
                )
            ) {
                return
            }

            enqueueBackup(context, prefs, delaySeconds = 10L, policy = ExistingWorkPolicy.KEEP)
        } catch (e: Exception) {
            // Never throws, but leave a trace for debugging.
            android.util.Log.w("UniTask", "Could not schedule the Drive backup", e)
        }
    }

    /**
     * Called by [DriveChangeWatcher] when courses, tasks, notes, timetable or other backed up data
     * changed. Marks the data as changed and plans a backup after a short quiet period. Each new
     * change replaces the planned run, so the wait restarts and a burst of edits is uploaded once.
     */
    fun onDataChanged(context: Context) {
        try {
            val prefs = DrivePrefs(context)
            if (!prefs.isConnected || !prefs.autoEnabled) return

            prefs.dirty = true

            // After a failed run the retry window decides, so a change does not hit a broken
            // connection again right away. The data stays marked as changed for that later run.
            if (!shouldRun(
                    connected = prefs.isConnected,
                    autoEnabled = prefs.autoEnabled,
                    lastAttemptAt = prefs.lastAttemptAt,
                    lastSuccessAt = prefs.lastSuccessAt,
                    consecutiveFailures = prefs.consecutiveFailures,
                    now = System.currentTimeMillis(),
                    dirty = true
                )
            ) {
                return
            }

            enqueueBackup(
                context,
                prefs,
                delaySeconds = CHANGE_DEBOUNCE_SECONDS,
                policy = ExistingWorkPolicy.REPLACE
            )
        } catch (e: Exception) {
            android.util.Log.w("UniTask", "Could not schedule the Drive backup after a change", e)
        }
    }

    private fun enqueueBackup(
        context: Context,
        prefs: DrivePrefs,
        delaySeconds: Long,
        policy: ExistingWorkPolicy
    ) {
        val request = OneTimeWorkRequestBuilder<DriveBackupWorker>()
            .setConstraints(buildConstraints(prefs.wifiOnly))
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(WORK_NAME, policy, request)
    }

    /**
     * Registers the daily background backup. Android picks the exact time, usually when the
     * phone is idle and the network rule is met. UPDATE keeps the existing schedule and only
     * applies a changed Wi-Fi setting, so opening the app does not restart the 24-hour wait.
     */
    private fun schedulePeriodic(context: Context, wifiOnly: Boolean) {
        val request = PeriodicWorkRequestBuilder<DriveBackupWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(buildConstraints(wifiOnly))
            .build()

        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
    }

    /**
     * Cancels any scheduled background backup work.
     */
    fun cancel(context: Context) {
        try {
            val workManager = WorkManager.getInstance(context.applicationContext)
            workManager.cancelUniqueWork(WORK_NAME)
            workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
        } catch (e: Exception) {
            android.util.Log.w("UniTask", "Could not cancel the scheduled Drive backup work", e)
        }
    }

    /**
     * Executes a single backup pass silently.
     *
     * The "changed" mark is cleared before the data is read, so a change made while this run is
     * uploading marks it again and gets its own run. If this run does not finish (failure or
     * cancel), the mark is put back.
     */
    suspend fun runOnce(
        context: Context,
        auth: DriveAuthApi,
        storeFactory: ((suspend (Boolean) -> String) -> DriveStore),
        now: Long
    ): Outcome {
        val prefs = DrivePrefs(context)
        prefs.dirty = false
        val outcome = try {
            runBackupPass(context, prefs, auth, storeFactory, now)
        } catch (e: kotlinx.coroutines.CancellationException) {
            prefs.dirty = true
            throw e
        }
        if (outcome == Outcome.Retry || outcome == Outcome.NeedsReconnect || outcome == Outcome.Failed) {
            prefs.dirty = true
        }
        return outcome
    }

    private suspend fun runBackupPass(
        context: Context,
        prefs: DrivePrefs,
        auth: DriveAuthApi,
        storeFactory: ((suspend (Boolean) -> String) -> DriveStore),
        now: Long
    ): Outcome {
        prefs.lastAttemptAt = now

        val localHash = DriveBackupManager.computeLocalDataHash(context)
        if (localHash == prefs.lastHash) {
            // The phone matches the last upload, so the Drive copy is current.
            prefs.lastSafeAt = now
            return Outcome.Unchanged
        }

        var token: String
        when (val authRes = auth.authorize()) {
            is DriveAuthResult.Granted -> {
                token = authRes.accessToken
                if (authRes.accountEmail != null) {
                    prefs.connectedEmail = authRes.accountEmail
                }
            }
            is DriveAuthResult.NeedsConsent -> {
                prefs.lastError = "Google Drive access was lost. Reconnect in Settings."
                prefs.consecutiveFailures += 1
                checkNotification(context, prefs, now)
                return Outcome.NeedsReconnect
            }
            is DriveAuthResult.Failed -> {
                prefs.lastError = authRes.message
                prefs.consecutiveFailures += 1
                checkNotification(context, prefs, now)
                return if (authRes.retryable) Outcome.Retry else Outcome.Failed
            }
        }

        val tokenProvider: suspend (Boolean) -> String = { forceRefresh ->
            if (forceRefresh) {
                auth.clearToken(token)
                when (val refreshed = auth.authorize()) {
                    is DriveAuthResult.Granted -> {
                        token = refreshed.accessToken
                        if (refreshed.accountEmail != null) {
                            prefs.connectedEmail = refreshed.accountEmail
                        }
                        token
                    }
                    is DriveAuthResult.NeedsConsent -> {
                        throw DriveException(DriveException.Kind.AUTH, "Google Drive access was lost. Reconnect in Settings.")
                    }
                    is DriveAuthResult.Failed -> {
                        throw DriveException(DriveException.Kind.AUTH, refreshed.message)
                    }
                }
            } else {
                token
            }
        }

        val store = storeFactory(tokenProvider)
        val backupResult = DriveBackupManager.backUp(context, store, allowEmpty = false)
        return when (backupResult) {
            is DriveBackupResult.Success -> {
                Outcome.Done
            }
            is DriveBackupResult.Unchanged -> {
                Outcome.Unchanged
            }
            is DriveBackupResult.EmptyBlocked -> {
                Outcome.Unchanged
            }
            is DriveBackupResult.Failure -> {
                checkNotification(context, prefs, now)
                when (backupResult.kind) {
                    DriveException.Kind.NETWORK, DriveException.Kind.SERVER -> Outcome.Retry
                    DriveException.Kind.AUTH -> {
                        prefs.lastError = "Google Drive access was lost. Reconnect in Settings."
                        Outcome.NeedsReconnect
                    }
                    DriveException.Kind.QUOTA, DriveException.Kind.OTHER -> Outcome.Failed
                }
            }
        }
    }

    private fun checkNotification(context: Context, prefs: DrivePrefs, now: Long) {
        // Start the failure clock on the first failure of a streak.
        if (prefs.firstFailureAt == 0L || prefs.firstFailureAt > now) {
            prefs.firstFailureAt = now
        }
        val failingForMs = now - prefs.firstFailureAt
        if (prefs.consecutiveFailures >= 3 && failingForMs >= THREE_DAYS_MS && !prefs.failureNotified) {
            NotificationHistory.add(
                context = context,
                kind = "Backup",
                title = "Google Drive backup",
                body = "Google Drive backup has not worked for 3 days. Open Settings, Backup & data."
            )
            prefs.failureNotified = true
        }
    }
}
