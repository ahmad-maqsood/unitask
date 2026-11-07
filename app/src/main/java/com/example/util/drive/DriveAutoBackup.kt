package com.example.util.drive

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.sync.DriveBackupWorker
import com.example.util.NotificationHistory
import java.util.concurrent.TimeUnit

/**
 * Coordinates automatic daily backups of user data to Google Drive.
 * Runs in the background via WorkManager when network conditions are met.
 */
object DriveAutoBackup {

    const val WORK_NAME = "unitask_drive_backup"
    const val INTERVAL_HOURS = 24L
    const val RETRY_INTERVAL_HOURS = 6L

    private const val ONE_HOUR_MS = 60 * 60 * 1000L
    private const val ONE_MINUTE_MS = 60 * 1000L

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
     */
    fun shouldRun(
        connected: Boolean,
        autoEnabled: Boolean,
        lastAttemptAt: Long,
        lastSuccessAt: Long,
        consecutiveFailures: Int,
        now: Long
    ): Boolean {
        if (!connected || !autoEnabled) return false
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
     * Called when the app opens. Enqueues unique one-time backup work if due.
     */
    fun onAppOpen(context: Context) {
        try {
            val prefs = DrivePrefs(context)
            val now = System.currentTimeMillis()
            if (!shouldRun(
                    connected = prefs.isConnected,
                    autoEnabled = prefs.autoEnabled,
                    lastAttemptAt = prefs.lastAttemptAt,
                    lastSuccessAt = prefs.lastSuccessAt,
                    consecutiveFailures = prefs.consecutiveFailures,
                    now = now
                )
            ) {
                return
            }

            val request = OneTimeWorkRequestBuilder<DriveBackupWorker>()
                .setConstraints(buildConstraints(prefs.wifiOnly))
                .setInitialDelay(10, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(
                    WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    request
                )
        } catch (_: Exception) {
            // Never throws
        }
    }

    /**
     * Cancels any scheduled background backup work.
     */
    fun cancel(context: Context) {
        try {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
        } catch (_: Exception) {
        }
    }

    /**
     * Executes a single backup pass silently.
     */
    suspend fun runOnce(
        context: Context,
        auth: DriveAuthApi,
        storeFactory: ((suspend (Boolean) -> String) -> DriveStore),
        now: Long
    ): Outcome {
        val prefs = DrivePrefs(context)
        prefs.lastAttemptAt = now

        val localHash = DriveBackupManager.computeLocalDataHash(context)
        if (localHash == prefs.lastHash) {
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
                checkNotification(context, prefs)
                return Outcome.NeedsReconnect
            }
            is DriveAuthResult.Failed -> {
                prefs.lastError = authRes.message
                prefs.consecutiveFailures += 1
                checkNotification(context, prefs)
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
                checkNotification(context, prefs)
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

    private fun checkNotification(context: Context, prefs: DrivePrefs) {
        if (prefs.consecutiveFailures >= 3 && !prefs.failureNotified) {
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
