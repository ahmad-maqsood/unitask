package com.example.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.reminder.AnnouncementNotifier
import com.example.util.Announcements
import com.example.util.AppVisibility
import com.example.util.SupabaseProvider
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs about every 15 minutes, even when the app is closed. It downloads the current announcements
 * (so the bell is up to date) and shows a notification for each one that is new to this phone.
 * Works for signed-out people too, because public announcements do not need a login.
 */
class AnnouncementWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "AnnouncementWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Wait for the saved sign-in to load, so messages meant for one person are included.
            try { SupabaseProvider.client.auth.awaitInitialization() } catch (_: Exception) { }

            val reached = Announcements.refresh(applicationContext)
            if (!reached) {
                Log.w(TAG, "Could not reach the server; will try again at the next check")
                return@withContext Result.success()
            }

            // While the app is open, the pop-up and the bell are enough. The next check
            // (after the person leaves the app) will notify about anything still unseen.
            if (!AppVisibility.isForeground) {
                val fresh = Announcements.toNotify(applicationContext)
                if (fresh.isNotEmpty()) {
                    // Remember BEFORE posting, so a crash can never cause the same notification twice.
                    Announcements.markNotified(applicationContext, fresh)
                    AnnouncementNotifier.post(applicationContext, fresh)
                }
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "AnnouncementWorker error: ${e.message}", e)
            Result.retry()
        }
    }
}