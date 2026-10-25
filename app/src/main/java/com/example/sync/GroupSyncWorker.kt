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
            val repo = GroupRepository(applicationContext)
            val state = repo.fetchGroupsStateOnce(user.uid)
            if (state == null) {
                Log.w(TAG, "Could not read groups; keeping existing alarms")
                return@withContext Result.retry()
            }
            GroupTaskProcessor(applicationContext).process(user.uid, state)

            val notifier = com.example.reminder.GroupChatNotifier(applicationContext)
            for (snapshot in state.loaded) {
                try {
                    val threshold = notifier.thresholdFor(snapshot.groupId)
                    // -1 means "no baseline yet": the repository then fetches only the newest message.
                    val tail = repo.getNewMessagesOnce(snapshot.groupId, threshold ?: -1L)
                    if (tail != null) {
                        notifier.onMessages(
                            myUid = user.uid,
                            groupId = snapshot.groupId,
                            groupName = snapshot.groupName,
                            messages = tail.messages,
                            fromServer = tail.fromServer
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Chat check failed for group ${snapshot.groupId}: ${e.message}")
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "GroupSyncWorker error: ${e.message}", e)
            Result.retry()
        }
    }
}
