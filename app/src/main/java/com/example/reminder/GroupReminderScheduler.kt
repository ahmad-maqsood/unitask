package com.example.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.example.data.groups.GroupTask
import com.example.data.local.ReminderOption
import com.example.data.local.decodeReminderOptions
import com.example.util.TimeFormatter
import java.util.Calendar

/**
 * Deadline alarms for group tasks. Every phone sets its own alarms, so no server is needed.
 *
 * Group tasks get the same kind of reminders as personal tasks: one alarm per offset in
 * [DEFAULT_REMINDERS]. Each alarm is remembered in SharedPreferences so it can be put back after
 * a reboot (see BootReceiver) and cancelled later (task done, deleted, or you left the group).
 */
class GroupReminderScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    private val prefs = context.getSharedPreferences("group_reminders", Context.MODE_PRIVATE)

    companion object {
        private const val SEP = "\u001F"
        private const val ACTION = "com.example.ACTION_GROUP_TASK_REMINDER"

        /** Change this list to change when group-task reminders fire. */
        val DEFAULT_REMINDERS: Set<ReminderOption> = linkedSetOf(
            ReminderOption.ONE_DAY_BEFORE,
            ReminderOption.TWO_HOURS_BEFORE,
            ReminderOption.AT_DEADLINE
        )

        /**
         * The reminders chosen for this task. Tasks made before this feature have nothing saved,
         * so they keep [DEFAULT_REMINDERS]. A saved "NONE" means no reminders at all.
         */
        fun optionsFor(task: GroupTask): Set<ReminderOption> =
            task.reminderOptions?.let { decodeReminderOptions(it) } ?: DEFAULT_REMINDERS
    }

    // groupId|taskId|OPTION_NAME
    private fun keyOf(groupId: String, taskId: String, option: ReminderOption) =
        "$groupId|$taskId|${option.name}"

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
     * Makes the alarms of one group match [tasks]: sets the reminders of every task that is not
     * done and still in the future, and cancels alarms of tasks that are gone, done or past.
     */
    fun sync(groupId: String, groupLabel: String, tasks: List<GroupTask>, doneIds: Set<String>) {
        val now = System.currentTimeMillis()
        val wanted = HashSet<String>()

        for (task in tasks) {
            if (task.id in doneIds) continue
            val deadline = deadlineMillis(task)
            val dueInfo = "Due at ${TimeFormatter.format12Hour(task.dueTimeHour, task.dueTimeMinute)}"

            for (option in optionsFor(task)) {
                val trigger = deadline - option.offsetMinutes * 60_000L
                if (trigger <= now) continue

                val key = keyOf(groupId, task.id, option)
                wanted.add(key)
                val value = listOf(trigger.toString(), task.title, groupLabel, dueInfo, option.label)
                    .joinToString(SEP)
                if (prefs.getString(key, null) != value) {
                    prefs.edit().putString(key, value).apply()
                }
                // Always (re)set: it is idempotent, and it repairs alarms the system dropped.
                setAlarm(key, trigger, task.title, groupLabel, dueInfo, option.label)
            }
        }

        for (key in prefs.all.keys.toList()) {
            if (key.startsWith("$groupId|") && key !in wanted) cancelKey(key)
        }
    }

    fun cancel(groupId: String, taskId: String) {
        for (key in prefs.all.keys.toList()) {
            if (key.startsWith("$groupId|$taskId|")) cancelKey(key)
        }
    }

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
            // Entries written by older versions have 4 parts; drop them, the next sync re-creates them.
            if (parts.size < 5 || trigger == null || trigger <= now) {
                prefs.edit().remove(key).apply()
                continue
            }
            setAlarm(key, trigger, parts[1], parts[2], parts[3], parts[4])
        }
    }

    private fun intentFor(key: String): Intent =
        Intent(context, TaskReminderReceiver::class.java).apply {
            action = ACTION
            data = Uri.parse("unitask://group-reminder/" + Uri.encode(key))
        }

    private fun setAlarm(
        key: String,
        trigger: Long,
        title: String,
        label: String,
        dueInfo: String,
        optionLabel: String
    ) {
        val intent = intentFor(key).apply {
            putExtra(TaskReminderReceiver.EXTRA_TASK_TITLE, title)
            putExtra(TaskReminderReceiver.EXTRA_COURSE_CODE, label)
            putExtra(TaskReminderReceiver.EXTRA_DUE_INFO, dueInfo)
            putExtra(TaskReminderReceiver.EXTRA_REMINDER_LABEL, optionLabel)
            // The receiver derives its notification id from this, so every alarm gets its own.
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
