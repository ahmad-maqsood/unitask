package com.example.util

import android.app.NotificationManager
import android.content.Context

/**
 * Group chat was removed from UniTask. Phones that had an older version still hold its leftovers:
 * the "Group chat" notification channel and two small files with chat read marks. This tidies them
 * up once, so the channel no longer shows in the phone's notification settings.
 */
object LegacyChatCleanup {

    private const val PREFS = "legacy_chat_cleanup"
    private const val KEY_DONE = "done"
    private const val CHANNEL_PREFIX = "unitask_group_chat_v1"
    private const val SUMMARY_ID = 9901

    fun runOnce(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_DONE, false)) return

        runCatching {
            val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.cancel(SUMMARY_ID)
            manager?.notificationChannels
                ?.filter { it.id.startsWith(CHANNEL_PREFIX) }
                ?.forEach { manager.deleteNotificationChannel(it.id) }
        }
        runCatching {
            appContext.getSharedPreferences("group_chat_read", Context.MODE_PRIVATE).edit().clear().apply()
            appContext.getSharedPreferences("group_chat_notified", Context.MODE_PRIVATE).edit().clear().apply()
        }
        prefs.edit().putBoolean(KEY_DONE, true).apply()
    }
}
