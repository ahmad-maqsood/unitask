package com.example.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.example.data.groups.GroupTask
import com.example.util.TimeFormatter
import java.util.Calendar

/**
 * Deadline alarms for group tasks. Every phone sets its own alarms, so no server is needed.
 *
 * Each alarm is remembered in SharedPreferences so it can be put back after a reboot
 * (see BootReceiver) and cancelled later (task done, deleted, or you left the group).
 */
class GroupReminderScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    private val prefs = context.getSharedPreferences("group_reminders", Context.MODE_PRIVATE)

    companion object {
        private const val SEP = "\u001F"
        private const val ACTION = "com.example.ACTION_GROUP_TASK_REMINDER"
    }

    private fun keyOf(groupId: String, taskId: String) = "$groupId|$taskId"

    fun deadlineMillis(task: GroupTask): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = task.dueDateEpochMillis
        cal.set(Calendar.HOUR_OF_DAY, task.dueTimeHour)
        cal.set(Calendar.MINUTE, task.dueTimeMinute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * Makes the alarms of one group match [tasks]: sets an alarm for every task that is not
     * done and still in the future, and cancels alarms of tasks that are gone, done or past.
     */
    fun sync(groupId: String, groupLabel: String, tasks: List<GroupTask>, doneIds: Set<String>) {
        val now = System.currentTimeMillis()
        val wanted = HashSet<String>()

        for (task in tasks) {
            if (task.id in doneIds) continue
            val trigger = deadlineMillis(task)
            if (trigger <= now) continue

            wanted.add(task.id)
            val label = task.courseLabel?.takeIf { it.isNotBlank() } ?: groupLabel
            val dueInfo = "Due at ${TimeFormatter.format12Hour(task.dueTimeHour, task.dueTimeMinute)}"
            val key = keyOf(groupId, task.id)
            prefs.edit()
                .putString(key, listOf(trigger.toString(), task.title, label, dueInfo).joinToString(SEP))
                .apply()
            setAlarm(key, trigger, task.title, label, dueInfo)
        }

        for (key in prefs.all.keys.toList()) {
            if (key.startsWith("$groupId|") && key.substringAfter('|') !in wanted) {
                cancelKey(key)
            }
        }
    }

    fun cancel(groupId: String, taskId: String) = cancelKey(keyOf(groupId, taskId))

    fun cancelGroup(groupId: String) {
        for (key in prefs.all.keys.toList()) {
            if (key.startsWith("$groupId|")) cancelKey(key)
        }
    }

    /** Cancels alarms of every group that is not in [keepGroupIds]. */
    fun cancelGroupsNotIn(keepGroupIds: Set<String>) {
        for (key in prefs.all.keys.toList()) {
            if (key.substringBefore('|') !in keepGroupIds) cancelKey(key)
        }
    }

    /** Alarms are wiped by a reboot or an app update; this puts the remembered ones back. */
    fun rescheduleAll() {
        val now = System.currentTimeMillis()
        for (key in prefs.all.keys.toList()) {
            val parts = (prefs.getString(key, null) ?: continue).split(SEP)
            val trigger = parts.getOrNull(0)?.toLongOrNull()
            if (parts.size < 4 || trigger == null || trigger <= now) {
                prefs.edit().remove(key).apply()
                continue
            }
            setAlarm(key, trigger, parts[1], parts[2], parts[3])
        }
    }

    private fun intentFor(key: String): Intent =
        Intent(context, TaskReminderReceiver::class.java).apply {
            action = ACTION
            data = Uri.parse("unitask://group-reminder/" + Uri.encode(key))
        }

    private fun setAlarm(key: String, trigger: Long, title: String, label: String, dueInfo: String) {
        val intent = intentFor(key).apply {
            putExtra(TaskReminderReceiver.EXTRA_TASK_TITLE, title)
            putExtra(TaskReminderReceiver.EXTRA_COURSE_CODE, label)
            putExtra(TaskReminderReceiver.EXTRA_DUE_INFO, dueInfo)
            putExtra(TaskReminderReceiver.EXTRA_REMINDER_LABEL, "Group deadline")
            // The receiver derives its notification id from this, so every task gets its own.
            putExtra(TaskReminderReceiver.EXTRA_REMINDER_OPTION, "GROUP_$key")
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            key.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = alarmManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pendingIntent)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pendingIntent)
                }
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pendingIntent)
            }
        } catch (_: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, trigger, pendingIntent)
        }
    }

    private fun cancelKey(key: String) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            key.hashCode(),
            intentFor(key),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager?.cancel(pendingIntent)
            pendingIntent.cancel()
        }
        prefs.edit().remove(key).apply()
    }
}
