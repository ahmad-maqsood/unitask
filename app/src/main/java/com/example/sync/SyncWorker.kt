package com.example.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.local.AppDatabase
import com.example.util.awaitAppUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "SyncWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val user = awaitAppUser() ?: run {
            Log.d(TAG, "No Google user logged in; skipping cloud sync")
            return@withContext Result.success()
        }

        try {
            val localDb = AppDatabase.getDatabase(applicationContext)
            val success = FirestoreSyncManager.syncAllDataNow(applicationContext, localDb)
            if (success) {
                Log.d(TAG, "SyncWorker completed successfully for user ${user.uid}")
                Result.success()
            } else {
                Log.w(TAG, "SyncWorker finished with errors; retrying later")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "SyncWorker error: ${e.message}", e)
            Result.retry()
        }
    }
}
