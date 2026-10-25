package com.example.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.groups.GroupChatTracker
import com.example.data.groups.GroupMessage
import com.example.util.AppVisibility
import com.example.util.NotificationSound

class GroupChatNotifier(context: Context) {

    private val appContext: Context = context.applicationContext
    private val notifiedPrefs: SharedPreferences =
        appContext.getSharedPreferences("group_chat_notified", Context.MODE_PRIVATE)
    private val tracker: GroupChatTracker = GroupChatTracker(appContext)

    companion object {
        const val CHANNEL_BASE_ID = "unitask_group_chat_v1"
        private const val CHANNEL_NAME = "Group chat"
        private const val CHANNEL_DESCRIPTION = "Tells you when someone writes in one of your groups"

        // Single shared lock across all instances
        private val LOCK = Any()
    }

    /**
     * Returns the threshold (max of lastRead and notifiedUpTo) for this group,
     * or null if the group has no baseline yet.
     */
    fun thresholdFor(groupId: String): Long? {
        synchronized(LOCK) {
            val lastRead = tracker.lastRead(groupId) ?: return null
            val notified = if (notifiedPrefs.contains(groupId)) {
                notifiedPrefs.getLong(groupId, 0L)
            } else {
                lastRead
            }
            return maxOf(lastRead, notified)
        }
    }

    fun onMessages(
        myUid: String,
        groupId: String,
        groupName: String,
        messages: List<GroupMessage>,
        fromServer: Boolean
    ) {
        synchronized(LOCK) {
            // 1. Cache-only data is not trusted for decisions.
            if (!fromServer) return

            val newest = messages.maxOfOrNull { it.createdAtMillis } ?: 0L

            // 2. No baseline yet: set it silently, do not notify.
            val storedLastRead = tracker.lastRead(groupId)
            if (storedLastRead == null) {
                tracker.setBaseline(groupId, newest)
                notifiedPrefs.edit().putLong(groupId, newest).apply()
                return
            }

            // 3. If the notified mark is missing, treat it as equal to lastRead.
            val notifiedUpTo = if (notifiedPrefs.contains(groupId)) {
                notifiedPrefs.getLong(groupId, 0L)
            } else {
                storedLastRead
            }

            // 4. Only messages from other people, newer than both marks.
            val threshold = maxOf(storedLastRead, notifiedUpTo)
            val candidates = messages.filter {
                it.senderId != myUid && it.createdAtMillis > threshold
            }.sortedBy { it.createdAtMillis }

            if (candidates.isEmpty()) return

            // 5. Remember what we notify about BEFORE posting, so a crash cannot cause repeats.
            val lastMillis = candidates.last().createdAtMillis
            notifiedPrefs.edit().putLong(groupId, lastMillis).apply()

            // 6. In the foreground the in-app badges are enough.
            if (AppVisibility.isForeground) return

            // 7. Post the notification.
            postNotification(groupId, groupName, candidates)
        }
    }

    private fun postNotification(
        groupId: String,
        groupName: String,
        candidates: List<GroupMessage>
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        ensureChannel()

        val notificationId = ("chat|" + groupId).hashCode()
        val title = groupName.ifBlank { "Group" }

        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_group_id", groupId)
            putExtra("open_group_chat", true)
        }
        val requestCode = ("chat_pi|" + groupId).hashCode()
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = NotificationSound.channelId(appContext, CHANNEL_BASE_ID)
        val builder = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(R.drawable.ic_stat_unitask)
            .setContentTitle(title)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        if (candidates.size == 1) {
            val msg = candidates.first()
            val line = formatMessageLine(msg.senderName, msg.text)
            builder.setContentText(line)
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(line))
        } else {
            val count = candidates.size
            val collapsedText = "$count new messages"
            builder.setContentText(collapsedText)

            val inboxStyle = NotificationCompat.InboxStyle()
            val linesToShow = if (count > 5) candidates.takeLast(5) else candidates
            for (msg in linesToShow) {
                inboxStyle.addLine(formatMessageLine(msg.senderName, msg.text))
            }
            builder.setStyle(inboxStyle)
        }

        val customSound = NotificationSound.uri(appContext)
        if (customSound != null) {
            builder.setSound(customSound)
        } else {
            builder.setDefaults(NotificationCompat.DEFAULT_SOUND)
        }

        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(notificationId, builder.build())
    }

    private fun formatMessageLine(senderName: String, text: String): String {
        val sender = senderName.ifBlank { "Someone" }
        val cleanText = text.replace('\n', ' ').trim()
        val cappedText = if (cleanText.length > 100) cleanText.take(99) + "…" else cleanText
        return "$sender: $cappedText"
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val targetChannelId = NotificationSound.channelId(appContext, CHANNEL_BASE_ID)
            manager.deleteNotificationChannel(NotificationSound.otherChannelId(appContext, CHANNEL_BASE_ID))
            if (manager.getNotificationChannel(targetChannelId) == null) {
                val channel = NotificationChannel(
                    targetChannelId,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = CHANNEL_DESCRIPTION
                    NotificationSound.uri(appContext)?.let {
                        setSound(it, NotificationSound.audioAttributes())
                    }
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun cancel(groupId: String) = synchronized(LOCK) {
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.cancel(("chat|" + groupId).hashCode())
    }

    fun clearAll() = synchronized(LOCK) {
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        for (key in notifiedPrefs.all.keys) {
            manager?.cancel(("chat|" + key).hashCode())
        }
        notifiedPrefs.edit().clear().apply()
    }
}
