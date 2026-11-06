package com.example.util

import android.content.Context
import com.example.reminder.SnoozeStore
import java.io.File

/**
 * Clears private local files and caches that belong to the previous user.
 * This is called wherever the database is wiped for a different person.
 */
object PrivateDataCleaner {

    fun clearPersonalFiles(context: Context) {
        val app = context.applicationContext

        // Delete every file inside filesDir/note_images, keeping the folder itself
        runCatching {
            val dir = File(app.filesDir, "note_images")
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        file.delete()
                    }
                }
            }
        }

        // Clear notification history
        runCatching {
            NotificationHistory.clear(app)
        }

        // Clear daily summary log
        runCatching {
            DailySummaryLog.clear(app)
        }

        // Clear all snooze entries and cancel their alarms
        runCatching {
            SnoozeStore.entries(app).forEach { entry ->
                SnoozeStore.clear(app, entry.key, cancelAlarm = true)
            }
        }
    }
}
