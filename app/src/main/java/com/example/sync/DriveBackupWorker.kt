package com.example.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.util.drive.DriveAutoBackup
import com.example.util.drive.DrivePrefs
import com.example.util.drive.DriveRestClient
import com.example.util.drive.GoogleDriveAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Background worker for automatic daily Google Drive backups.
 * Runs only when network constraints are met.
 */
class DriveBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = DrivePrefs(applicationContext)
        if (!prefs.isConnected || !prefs.autoEnabled) return@withContext Result.success()
        try {
            val auth = GoogleDriveAuth(applicationContext)
            val outcome = DriveAutoBackup.runOnce(
                context = applicationContext,
                auth = auth,
                storeFactory = { tokenProvider -> DriveRestClient(tokenProvider = tokenProvider) },
                now = System.currentTimeMillis()
            )
            when (outcome) {
                DriveAutoBackup.Outcome.Done,
                DriveAutoBackup.Outcome.Unchanged,
                DriveAutoBackup.Outcome.NeedsReconnect,
                DriveAutoBackup.Outcome.Failed -> Result.success()
                DriveAutoBackup.Outcome.Retry -> {
                    if (runAttemptCount < 3) {
                        Result.retry()
                    } else {
                        Result.success()
                    }
                }
            }
        } finally {
            cleanPartFiles(applicationContext)
        }
    }

    private fun cleanPartFiles(context: Context) {
        try {
            val dirs = listOf(
                context.filesDir,
                context.cacheDir,
                File(context.filesDir, "attachments"),
                File(context.filesDir, "note_images")
            )
            for (dir in dirs) {
                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles { _, name -> name.endsWith(".part") }?.forEach { it.delete() }
                }
            }
        } catch (_: Exception) {
        }
    }
}
