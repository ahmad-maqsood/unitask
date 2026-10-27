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
import com.example.data.groups.GroupSnapshot
import com.example.data.groups.GroupTask
import com.example.data.local.computeDeadlineMillis
import com.example.data.groups.GroupsState
import com.example.util.NotificationSound
import com.example.util.TimeFormatter

/** A pending group task as saved for the daily summary. */
data class SummaryTask(
    val deadlineMillis: Long,
    val groupName: String,
    val label: String,
    val title: String
)
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
    private val stateStore = appContext.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
    private val summaryStore = appContext.getSharedPreferences(SUMMARY_PREFS, Context.MODE_PRIVATE)
    companion object {
        const val CHANNEL_ID = "unitask_group_updates_v2"
        private const val CHANNEL_NAME = "Group updates"
        private const val BASELINE = "__baseline__"
        private const val MAX_INDIVIDUAL_NOTICES = 3
        private const val STATE_PREFS = "group_task_state"
        private const val SUMMARY_PREFS = "group_summary_tasks"
        private const val SEP = "\u001F"

        // One lock for every instance: the app-level listener and the background worker can
        // run at the same moment, and must not both announce the same task.
        private val LOCK = Any()

        /**
         * Call right before THIS user edits a group task, so their own phone does not announce
         * their own change back to them.
         */
        fun rememberOwnEdit(
            context: Context,
            groupId: String,
            taskId: String,
            deadlineMillis: Long,
            title: String
        ) {
            synchronized(LOCK) {
                val prefs = context.applicationContext.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
                val set = prefs.getStringSet(groupId, null) ?: return
                val updated = HashSet(set.filterNot { it.startsWith(taskId + SEP) })
                updated.add("$taskId$SEP$deadlineMillis$SEP$title")
                prefs.edit().putStringSet(groupId, updated).apply()
            }
        }

        /** Call right before THIS user deletes a group task, or when their edit failed. */
        fun forgetOwnTask(context: Context, groupId: String, taskId: String) {
            synchronized(LOCK) {
                val prefs = context.applicationContext.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
                val set = prefs.getStringSet(groupId, null) ?: return
                val updated = HashSet(set.filterNot { it.startsWith(taskId + SEP) })
                prefs.edit().putStringSet(groupId, updated).apply()
            }
        }

        /** Pending group tasks saved by [process], for the daily summary. */
        fun pendingSummaryTasks(context: Context): List<SummaryTask> {
            val prefs = context.applicationContext.getSharedPreferences(SUMMARY_PREFS, Context.MODE_PRIVATE)
            val result = ArrayList<SummaryTask>()
            for (value in prefs.all.values) {
                val entries = value as? Set<*> ?: continue
                for (entry in entries) {
                    val parts = (entry as? String)?.split(SEP, limit = 4) ?: continue
                    if (parts.size != 4) continue
                    val deadline = parts[0].toLongOrNull() ?: continue
                    result.add(SummaryTask(deadline, parts[1], parts[2], parts[3]))
                }
            }
            return result
        }
    }

    fun process(myUid: String, state: GroupsState) = synchronized(LOCK) {
        scheduler.cancelGroupsNotIn(state.groupIds)
        for (key in seenPrefs.all.keys.toList()) {
            if (key !in state.groupIds) seenPrefs.edit().remove(key).apply()
        }
        for (key in stateStore.all.keys.toList()) {
            if (key !in state.groupIds) stateStore.edit().remove(key).apply()
        }
        for (key in summaryStore.all.keys.toList()) {
            if (key !in state.groupIds) summaryStore.edit().remove(key).apply()
        }
        for (snapshot in state.loaded) {
            scheduler.sync(snapshot.groupId, snapshot.groupName, snapshot.tasks, snapshot.done)
            announceNewTasks(myUid, snapshot)
            announceChangedTasks(snapshot)
            storeSummary(snapshot)
        }
    }

    /** Signed out: no alarms and no memory of seen tasks. */
    fun clearAll() = synchronized(LOCK) {
        scheduler.cancelGroupsNotIn(emptySet())
        seenPrefs.edit().clear().apply()
        stateStore.edit().clear().apply()
        summaryStore.edit().clear().apply()
    }
    /**
     * Remembers the pending group tasks that show on this user's Deadlines tab (tagged to them and
     * not marked done), so the daily summary can list them even when the app is closed.
     */
    private fun storeSummary(snapshot: GroupSnapshot) {
        val pending = snapshot.tasks
            .filter { it.id !in snapshot.done }
            .map { t ->
                listOf(
                    computeDeadlineMillis(t.dueDateEpochMillis, t.dueTimeHour, t.dueTimeMinute).toString(),
                    snapshot.groupName,
                    t.courseLabel.orEmpty().trim(),
                    t.title
                ).joinToString(SEP)
            }
            .toHashSet()
        summaryStore.edit().putStringSet(snapshot.groupId, pending).apply()
    }
    private fun announceNewTasks(myUid: String, snapshot: GroupSnapshot) {
        val currentIds = snapshot.allTasks.map { it.id }
        val seen = seenPrefs.getStringSet(snapshot.groupId, null)

        if (seen == null) {
            // First time we see this group on this phone (just joined, or fresh install):
            // remember what is already there without announcing it.
            seenPrefs.edit()
                .putStringSet(snapshot.groupId, HashSet(currentIds).apply { add(BASELINE) })
                .apply()
            return
        }

        val fresh = snapshot.allTasks.filter { it.id !in seen && it.createdBy != myUid }
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
                text = "Open the Deadlines tab to see them.",
                groupId = snapshot.groupId
            )
            return
        }
        for (task in fresh) {
            val who = task.createdByName.ifBlank { "A member" }
            val due = "Due at ${TimeFormatter.format12Hour(task.dueTimeHour, task.dueTimeMinute)}"
            postNotification(
                id = ("new|" + snapshot.groupId + "|" + task.id).hashCode(),
                title = "New task in ${snapshot.groupName}",
                text = "$who added: ${task.title} • $due",
                groupId = snapshot.groupId
            )
        }
    }
    private fun encodeTask(t: GroupTask): String =
        "${t.id}$SEP${computeDeadlineMillis(t.dueDateEpochMillis, t.dueTimeHour, t.dueTimeMinute)}$SEP${t.title}"

    private fun formatDue(millis: Long): String =
        java.text.SimpleDateFormat("EEE, d MMM h:mm a", java.util.Locale.getDefault())
            .format(java.util.Date(millis))

    /** "Deadline moved" and "Task deleted" notices, by comparing with what this phone saw last time. */
    private fun announceChangedTasks(snapshot: GroupSnapshot) {
        // An empty answer can mean "no access" or "not loaded": never read it as "all deleted".
        if (snapshot.allTasks.isEmpty()) return

        val mine = snapshot.tasks
        val stored = stateStore.getStringSet(snapshot.groupId, null)
        stateStore.edit()
            .putStringSet(snapshot.groupId, mine.map { encodeTask(it) }.toHashSet())
            .apply()
        // First time this group is seen on this phone: remember it, announce nothing.
        if (stored == null) return

        val old = HashMap<String, Pair<Long, String>>()
        for (entry in stored) {
            val p = entry.split(SEP, limit = 3)
            if (p.size != 3) continue
            val deadline = p[1].toLongOrNull() ?: continue
            old[p[0]] = deadline to p[2]
        }

        val notices = ArrayList<Triple<String, String, String>>() // task id, title, text
        for (task in mine) {
            val before = old[task.id] ?: continue
            val now = computeDeadlineMillis(task.dueDateEpochMillis, task.dueTimeHour, task.dueTimeMinute)
            if (now != before.first && task.id !in snapshot.done) {
                notices.add(
                    Triple(
                        task.id,
                        "Deadline moved in ${snapshot.groupName}",
                        "${task.title} is now due ${formatDue(now)}"
                    )
                )
            }
        }
        // Gone from the group entirely. A task only re-assigned away from you is still in allTasks.
        val allIds = snapshot.allTasks.map { it.id }.toSet()
        for ((id, v) in old) {
            if (id !in allIds) {
                notices.add(Triple(id, "Task deleted in ${snapshot.groupName}", "${v.second} was removed"))
            }
        }

        if (notices.isEmpty()) return
        ensureChannel()
        if (notices.size > MAX_INDIVIDUAL_NOTICES) {
            postNotification(
                id = ("changed|" + snapshot.groupId).hashCode(),
                title = "${notices.size} tasks changed in ${snapshot.groupName}",
                text = "Open the Deadlines tab to see what changed.",
                groupId = snapshot.groupId
            )
            return
        }
        for ((id, title, text) in notices) {
            postNotification(
                id = ("change|" + snapshot.groupId + "|" + id).hashCode(),
                title = title,
                text = text,
                groupId = snapshot.groupId
            )
        }
    }
    private fun postNotification(id: Int, title: String, text: String, groupId: String? = null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val openApp = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!groupId.isNullOrBlank()) {
                putExtra("open_group_id", groupId)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            id,
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(appContext, NotificationSound.channelId(appContext, CHANNEL_ID))
            .setSmallIcon(R.drawable.ic_stat_unitask)
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
                NotificationSound.channelId(appContext, CHANNEL_ID),
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Tells you when someone adds a task to one of your groups"
                NotificationSound.uri(appContext)?.let { setSound(it, NotificationSound.audioAttributes()) }

            }
            val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.deleteNotificationChannel("unitask_group_updates")
            manager?.let { NotificationSound.deleteOtherChannels(appContext, it, CHANNEL_ID) }
            manager?.createNotificationChannel(channel)
        }
    }
}
