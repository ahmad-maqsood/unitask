package com.example.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object SyncScheduler {

    private const val GROUP_PERIODIC_WORK_NAME = "unitask_group_sync_periodic"
    private const val ANNOUNCEMENT_PERIODIC_WORK_NAME = "unitask_announcement_check_periodic"
    private const val PERIODIC_SYNC_WORK_NAME = "unitask_periodic_sync"

    /**
     * Checks user's groups and announcements sequentially every 15 minutes (the minimum WorkManager allows).
     * Also cancels the legacy separate group and announcement periodic workers.
     */
    fun schedulePeriodicSyncWorker(context: Context) {
        val workManager = WorkManager.getInstance(context.applicationContext)

        // Migration: cancel legacy separate periodic workers so existing installs do not run both
        workManager.cancelUniqueWork(GROUP_PERIODIC_WORK_NAME)
        workManager.cancelUniqueWork(ANNOUNCEMENT_PERIODIC_WORK_NAME)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<PeriodicSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
