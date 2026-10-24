package com.example.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.groups.GroupRepository
import com.example.reminder.GroupTaskProcessor
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs about every 15 minutes, even when the app is closed. It reads the user's groups once and
 * hands them to [GroupTaskProcessor], which sets deadline alarms and announces new group tasks.
 * While the app is open the live listener in MainViewModel does the same thing instantly.
 */
class GroupSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "GroupSyncWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val user = try {
            Firebase.auth.currentUser
        } catch (_: Exception) {
            null
        } ?: return@withContext Result.success()

        try {
            val state = GroupRepository(applicationContext).fetchGroupsStateOnce(user.uid)
            if (state == null) {
                Log.w(TAG, "Could not read groups; keeping existing alarms")
                return@withContext Result.retry()
            }
            GroupTaskProcessor(applicationContext).process(user.uid, state)
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "GroupSyncWorker error: ${e.message}", e)
            Result.retry()
        }
    }
}
