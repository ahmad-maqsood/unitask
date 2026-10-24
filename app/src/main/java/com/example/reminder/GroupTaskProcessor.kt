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
import com.example.data.groups.GroupSnapshot
import com.example.data.groups.GroupsState
import com.example.util.NotificationSound
import com.example.util.TimeFormatter

/**
 * Turns the latest state of the user's groups into phone behaviour:
 *  1. deadline alarms for every group task that is not done ([GroupReminderScheduler]);
 *  2. a "new task" notification for tasks other members added since the last run.
 *
 * It is called from two places that share the same SharedPreferences, so a task is announced
 * only once: the app-level listener (instant while the app is running) and GroupSyncWorker
 * (periodic, works when the app is closed).
 */
class GroupTaskProcessor(context: Context) {

    private val appContext = context.applicationContext
    private val scheduler = GroupReminderScheduler(appContext)
    private val seenPrefs = appContext.getSharedPreferences("group_seen_tasks", Context.MODE_PRIVATE)

    companion object {
        const val CHANNEL_ID = "unitask_group_updates_v2"
        private const val CHANNEL_NAME = "Group updates"
        private const val BASELINE = "__baseline__"
        private const val MAX_INDIVIDUAL_NOTICES = 3

        // One lock for every instance: the app-level listener and the background worker can
        // run at the same moment, and must not both announce the same task.
        private val LOCK = Any()
    }

    fun process(myUid: String, state: GroupsState) = synchronized(LOCK) {
        scheduler.cancelGroupsNotIn(state.groupIds)
        for (key in seenPrefs.all.keys.toList()) {
            if (key !in state.groupIds) seenPrefs.edit().remove(key).apply()
        }
        for (snapshot in state.loaded) {
            scheduler.sync(snapshot.groupId, snapshot.groupName, snapshot.tasks, snapshot.done)
            announceNewTasks(myUid, snapshot)
        }
    }

    /** Signed out: no alarms and no memory of seen tasks. */
    fun clearAll() = synchronized(LOCK) {
        scheduler.cancelGroupsNotIn(emptySet())
        seenPrefs.edit().clear().apply()
    }

    private fun announceNewTasks(myUid: String, snapshot: GroupSnapshot) {
        val currentIds = snapshot.tasks.map { it.id }
        val seen = seenPrefs.getStringSet(snapshot.groupId, null)

        if (seen == null) {
            // First time we see this group on this phone (just joined, or fresh install):
            // remember what is already there without announcing it.
            seenPrefs.edit()
                .putStringSet(snapshot.groupId, HashSet(currentIds).apply { add(BASELINE) })
                .apply()
            return
        }

        val fresh = snapshot.tasks.filter { it.id !in seen && it.createdBy != myUid }
        // Keep only ids that still exist, so the set cannot grow forever.
        val updated = HashSet<String>().apply {
            add(BASELINE)
            addAll(currentIds)
        }
        seenPrefs.edit().putStringSet(snapshot.groupId, updated).apply()

        if (fresh.isEmpty()) return
        ensureChannel()

        if (fresh.size > MAX_INDIVIDUAL_NOTICES) {
            postNotification(
                id = ("summary|" + snapshot.groupId).hashCode(),
                title = "${fresh.size} new tasks in ${snapshot.groupName}",
                text = "Open the Deadlines tab to see them."
            )
            return
        }
        for (task in fresh) {
            val who = task.createdByName.ifBlank { "A member" }
            val due = "Due at ${TimeFormatter.format12Hour(task.dueTimeHour, task.dueTimeMinute)}"
            postNotification(
                id = ("new|" + snapshot.groupId + "|" + task.id).hashCode(),
                title = "New task in ${snapshot.groupName}",
                text = "$who added: ${task.title} • $due"
            )
        }
    }

    private fun postNotification(id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val openApp = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            id,
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .apply {
                val customSound = NotificationSound.uri(appContext)
                if (customSound != null) setSound(customSound) else setDefaults(NotificationCompat.DEFAULT_SOUND)
            }
            .build()

        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(id, notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Tells you when someone adds a task to one of your groups"
                NotificationSound.uri(appContext)?.let { setSound(it, NotificationSound.audioAttributes()) }

            }
            val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.deleteNotificationChannel("unitask_group_updates")
            manager?.createNotificationChannel(channel)
        }
    }
}
