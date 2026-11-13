package com.example.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.groups.GroupCacheRepository
import com.example.Repositories
import com.example.reminder.AnnouncementNotifier
import com.example.reminder.GroupTaskProcessor
import com.example.util.Announcements
import com.example.util.AppVisibility
import com.example.util.AuthStorage
import com.example.util.SupabaseProvider
import com.example.util.awaitAppUser
import com.example.util.currentSupabaseUid
import com.example.util.runLogged
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs about every 15 minutes, even when the app is closed.
 * Sequentially executes:
 * 1. Group sync logic (updates group state, sets deadline alarms).
 * 2. Announcement check (refreshes announcements and shows notifications for new ones).
 *
 * Each step runs in its own try/catch so a failure in one does not skip the other.
 */
class PeriodicSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "PeriodicSyncWorker"

        suspend fun syncGroups(context: Context): Result = withContext(Dispatchers.IO) {
            val user = awaitAppUser() ?: return@withContext Result.success()

            try {
                val repo = Repositories.group(context)
                runLogged("waiting for the saved sign-in") { SupabaseProvider.client.auth.awaitInitialization() }
                val sbUid = currentSupabaseUid() ?: AuthStorage.getLastUid(context) ?: user.uid
                val state = repo.fetchGroupsStateOnce(sbUid)
                if (state == null) {
                    Log.w(TAG, "Could not read groups; keeping existing alarms")
                    return@withContext Result.retry()
                }
                GroupCacheRepository(context).saveGroupsState(sbUid, state)
                GroupTaskProcessor(context).process(sbUid, state)

                Result.success()
            } catch (e: Exception) {
                Log.e(TAG, "Group sync error: ${e.message}", e)
                Result.retry()
            }
        }

        suspend fun syncAnnouncements(context: Context): Result = withContext(Dispatchers.IO) {
            try {
                // Wait for the saved sign-in to load, so messages meant for one person are included.
                runLogged("waiting for the saved sign-in") { SupabaseProvider.client.auth.awaitInitialization() }

                val reached = Announcements.refresh(context)
                if (!reached) {
                    Log.w(TAG, "Could not reach the server; will try again at the next check")
                    return@withContext Result.success()
                }

                // While the app is open, the pop-up and the bell are enough. The next check
                // (after the person leaves the app) will notify about anything still unseen.
                if (!AppVisibility.isForeground) {
                    val fresh = Announcements.toNotify(context)
                    if (fresh.isNotEmpty()) {
                        // Remember BEFORE posting, so a crash can never cause the same notification twice.
                        Announcements.markNotified(context, fresh)
                        AnnouncementNotifier.post(context, fresh)
                    }
                }
                Result.success()
            } catch (e: Exception) {
                Log.e(TAG, "Announcement sync error: ${e.message}", e)
                Result.retry()
            }
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val groupResult = try {
            syncGroups(applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Group sync unexpected error: ${e.message}", e)
            Result.retry()
        }

        val announcementResult = try {
            syncAnnouncements(applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Announcement sync unexpected error: ${e.message}", e)
            Result.retry()
        }

        // Neither helper ever returns failure(), so only retry or success are possible.
        if (groupResult == Result.retry() || announcementResult == Result.retry()) {
            Result.retry()
        } else {
            Result.success()
        }
    }
}


