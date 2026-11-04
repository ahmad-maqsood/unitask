package com.example.reminder

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/**
 * In-app snooze: "remind me about this task again at ...".
 *
 * A snooze adds ONE extra reminder at the chosen time. The task's normal reminders are not touched
 * and still ring, so a snooze can never make you miss a deadline.
 *
 * Snoozes live in SharedPreferences, not in the database: group tasks are not database rows, and a
 * snooze belongs to this phone only (it is never synced or backed up). Each one is keyed by the id
 * of the task card (TaskItem.id, negative for group tasks) and carries a copy of what the reminder
 * should say, so it can be put back after a reboot without touching the database or the network.
 */
object SnoozeStore {

    private const val PREFS = "task_snoozes"

    /** After a reboot, a snooze older than this is dropped instead of ringing very late. */
    private const val STALE_AFTER_MS = 12L * 60 * 60 * 1000

    /** An alarm counts as "due" this long before its time. */
    private const val DUE_SLACK_MS = 60_000L

    data class Entry(
        val key: Long,
        val until: Long,
        /** Room id for personal and My tasks, 0 for group tasks. */
        val taskId: Long,
        val title: String,
        val courseCode: String,
        val dueInfo: String,
        val priority: String,
        val groupId: String = "",
        val groupTaskId: String = ""
    ) {
        val notificationId: Int get() = "SNOOZE_$key".hashCode()

        /** What the reminder notification needs. Same extras the normal reminders carry. */
        fun toIntent(): Intent = Intent().apply {
            putExtra(TaskReminderReceiver.EXTRA_TASK_ID, taskId)
            putExtra(TaskReminderReceiver.EXTRA_TASK_TITLE, title)
            putExtra(TaskReminderReceiver.EXTRA_COURSE_CODE, courseCode)
            putExtra(TaskReminderReceiver.EXTRA_DUE_INFO, dueInfo)
            putExtra(TaskReminderReceiver.EXTRA_PRIORITY, priority)
            // Gives the notification its own tap target (the receiver builds a request code from this).
            putExtra(TaskReminderReceiver.EXTRA_REMINDER_OPTION, "SNOOZE_$key")
            putExtra(ReminderSnooze.EXTRA_SNOOZE_KEY, key)
            if (groupId.isNotBlank()) putExtra(TaskReminderReceiver.EXTRA_GROUP_ID, groupId)
            if (groupTaskId.isNotBlank()) putExtra(TaskReminderReceiver.EXTRA_GROUP_TASK_ID, groupTaskId)
        }
    }

    private val untilFlow = MutableStateFlow<Map<Long, Long>>(emptyMap())

    @Volatile
    private var loaded = false

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun toJson(e: Entry): String = JSONObject()
        .put("key", e.key)
        .put("until", e.until)
        .put("taskId", e.taskId)
        .put("title", e.title)
        .put("courseCode", e.courseCode)
        .put("dueInfo", e.dueInfo)
        .put("priority", e.priority)
        .put("groupId", e.groupId)
        .put("groupTaskId", e.groupTaskId)
        .toString()

    private fun entryFrom(json: String): Entry? = try {
        val o = JSONObject(json)
        Entry(
            key = o.getLong("key"),
            until = o.getLong("until"),
            taskId = o.optLong("taskId", 0L),
            title = o.optString("title"),
            courseCode = o.optString("courseCode"),
            dueInfo = o.optString("dueInfo"),
            priority = o.optString("priority"),
            groupId = o.optString("groupId"),
            groupTaskId = o.optString("groupTaskId")
        )
    } catch (_: Exception) {
        null
    }

    /** Every snooze that is saved, whether or not its time has passed. */
    fun entries(context: Context): List<Entry> =
        prefs(context).all.values.mapNotNull { value -> (value as? String)?.let { entryFrom(it) } }

    private fun publish(context: Context) {
        untilFlow.value = entries(context).associate { it.key to it.until }
        loaded = true
    }

    /** Task card id to snooze end time. Collect this in the UI so cards update at once. */
    fun untilByTask(context: Context): StateFlow<Map<Long, Long>> {
        if (!loaded) publish(context)
        return untilFlow
    }

    /** Saves the snooze and sets the alarm that brings the reminder back. */
    fun set(context: Context, entry: Entry) {
        val app = context.applicationContext
        prefs(app).edit().putString(entry.key.toString(), toJson(entry)).apply()
        ReminderSnooze.scheduleAt(app, entry.notificationId, entry.toIntent(), entry.until)
        publish(app)
    }

    /** Forgets a snooze. [cancelAlarm] also stops the alarm that would bring it back. */
    fun clear(context: Context, key: Long, cancelAlarm: Boolean) {
        val app = context.applicationContext
        val saved = prefs(app).getString(key.toString(), null)?.let { entryFrom(it) }
        prefs(app).edit().remove(key.toString()).apply()
        if (cancelAlarm && saved != null) ReminderSnooze.cancel(app, saved.notificationId)
        publish(app)
    }

    /**
     * Called when a snoozed reminder fires. Forgets the snooze only if its time has come, so an old
     * notification snooze cannot wipe a newer in-app snooze of the same task.
     */
    fun clearIfDue(context: Context, key: Long) {
        val saved = entries(context).firstOrNull { it.key == key } ?: return
        if (saved.until <= System.currentTimeMillis() + DUE_SLACK_MS) {
            clear(context, key, cancelAlarm = false)
        }
    }

    fun clearGroupTask(context: Context, groupId: String, groupTaskId: String) {
        entries(context)
            .filter { it.groupId == groupId && it.groupTaskId == groupTaskId }
            .forEach { clear(context, it.key, cancelAlarm = true) }
    }

    fun clearGroup(context: Context, groupId: String) {
        entries(context)
            .filter { it.groupId == groupId }
            .forEach { clear(context, it.key, cancelAlarm = true) }
    }

    fun clearGroupsNotIn(context: Context, keepGroupIds: Set<String>) {
        entries(context)
            .filter { it.groupId.isNotBlank() && it.groupId !in keepGroupIds }
            .forEach { clear(context, it.key, cancelAlarm = true) }
    }

    /** Alarms are wiped by a reboot or an app update; this puts the saved snoozes back. */
    fun restoreAll(context: Context) {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        for (saved in entries(app)) {
            if (saved.until < now - STALE_AFTER_MS) {
                prefs(app).edit().remove(saved.key.toString()).apply()
            } else {
                ReminderSnooze.scheduleAt(app, saved.notificationId, saved.toIntent(), saved.until)
            }
        }
        publish(app)
    }
}
