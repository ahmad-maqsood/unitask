package com.example.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.util.Announcement
import com.example.util.NotificationSound

/** Shows a phone notification for new announcements. Uses the same sound the person chose in Settings. */
object AnnouncementNotifier {

    private const val CHANNEL_BASE_ID = "unitask_announcements_v1"
    private const val CHANNEL_NAME = "Announcements"
    private const val CHANNEL_DESCRIPTION = "Messages from the UniTask team"

    /** If many are waiting at once (for example on a fresh install), only the newest few are shown. */
    private const val MAX_AT_ONCE = 3
    const val GROUP_KEY_ANNOUNCEMENTS = "com.example.ANNOUNCEMENTS"
    private const val SUMMARY_ID = 9905
    private const val PRIMARY_COLOR = 0xFF4F46E5.toInt()

    private fun notificationId(announcementId: Long): Int = ("announcement|" + announcementId).hashCode()

    /** [items] must be newest first. Does nothing if notifications are not allowed. */
    fun post(context: Context, items: List<Announcement>) {
        val appContext = context.applicationContext
        if (items.isEmpty()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        ensureChannel(appContext, manager)
        val channelId = NotificationSound.channelId(appContext, CHANNEL_BASE_ID)

        for (item in items.take(MAX_AT_ONCE)) {
            try {
                val id = notificationId(item.id)
                val intent = Intent(appContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                val pendingIntent = PendingIntent.getActivity(
                    appContext,
                    id,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val builder = NotificationCompat.Builder(appContext, channelId)
                    .setSmallIcon(R.drawable.ic_stat_unitask)
                    .setColor(PRIMARY_COLOR)
                    .setContentTitle(item.title.ifBlank { "UniTask Announcement" })
                    .setContentText(item.body.replace('\n', ' ').trim())
                    .setStyle(NotificationCompat.BigTextStyle().bigText(item.body.trim()))
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setGroup(GROUP_KEY_ANNOUNCEMENTS)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pendingIntent)

                val customSound = NotificationSound.uri(appContext)
                if (customSound != null) {
                    builder.setSound(customSound)
                } else {
                    builder.setDefaults(NotificationCompat.DEFAULT_SOUND)
                }

                manager.notify(id, builder.build())
            } catch (_: Exception) {
            }
        }

        try {
            val summary = NotificationCompat.Builder(appContext, channelId)
                .setSmallIcon(R.drawable.ic_stat_unitask)
                .setColor(PRIMARY_COLOR)
                .setStyle(NotificationCompat.InboxStyle().setSummaryText("Announcements"))
                .setGroup(GROUP_KEY_ANNOUNCEMENTS)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build()
            manager.notify(SUMMARY_ID, summary)
        } catch (_: Exception) {
        }
    }

    /** Removes the notifications of these announcements (used when the person has seen or removed them). */
    fun cancel(context: Context, items: List<Announcement>) {
        val manager = context.applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        for (item in items) {
            try {
                manager.cancel(notificationId(item.id))
            } catch (_: Exception) {
            }
        }
        try {
            manager.cancel(SUMMARY_ID)
        } catch (_: Exception) {
        }
    }

    private fun ensureChannel(context: Context, manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val targetChannelId = NotificationSound.channelId(context, CHANNEL_BASE_ID)
        NotificationSound.deleteOtherChannels(context, manager, CHANNEL_BASE_ID)
        if (manager.getNotificationChannel(targetChannelId) == null) {
            val channel = NotificationChannel(
                targetChannelId,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
                NotificationSound.uri(context)?.let {
                    setSound(it, NotificationSound.audioAttributes())
                }
            }
            manager.createNotificationChannel(channel)
        }
    }
}