package com.example.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.edit
import androidx.core.net.toUri
import com.example.MainActivity
import com.example.R
import com.example.data.groups.GroupCacheRepository
import com.example.data.groups.GroupTask
import com.example.util.AuthStorage
import com.example.util.DateFormats
import com.example.data.local.ReminderOption
import com.example.data.local.decodeReminderOptions
import com.example.data.local.computeDeadlineMillis

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

        /** A waiting reminder is treated as lost once it is this late (same rule as personal reminders). */
        const val LATE_GRACE_MILLIS = MissedReminders.LATE_GRACE_MILLIS

        /** Reminders older than this are no longer useful and are forgotten. */
        const val LATE_WINDOW_MILLIS = MissedReminders.WINDOW_MILLIS

        private fun triggerOf(value: String): Long = value.substringBefore(SEP).toLongOrNull() ?: 0L

        /**
         * Which waiting reminders are overdue by more than [graceMillis] but still inside [windowMillis].
         * These alarms never rang although the phone was on. [waiting] maps a key to its saved value.
         * Oldest first.
         */
        fun pickLate(
            waiting: Map<String, String>,
            now: Long,
            graceMillis: Long = LATE_GRACE_MILLIS,
            windowMillis: Long = LATE_WINDOW_MILLIS
        ): List<String> =
            waiting.entries
                .filter { triggerOf(it.value) <= now - graceMillis && triggerOf(it.value) > now - windowMillis }
                .sortedBy { triggerOf(it.value) }
                .map { it.key }
    }

    /**
     * A copy of every alarm that is waiting to ring, with everything needed to show it. An entry leaves
     * when the alarm rings ([markHandled]) or is cancelled. After a reboot or a cleaner wiped the alarms,
     * an entry that is still here and past its time never rang, so [deliverLate] shows it.
     */
    private val waitingPrefs = context.getSharedPreferences("group_reminders_waiting", Context.MODE_PRIVATE)

    private fun waitingEntries(): Map<String, String> =
        waitingPrefs.all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()

    private fun taskIdOf(key: String): String = key.split("|").getOrNull(1).orEmpty()

    private fun forgetWaiting(matches: (String) -> Boolean) {
        val keys = waitingPrefs.all.keys.filter(matches)
        if (keys.isNotEmpty()) waitingPrefs.edit { keys.forEach { remove(it) } }
    }

    /** The alarm rang: it is not waiting any more. [key] is the extra the receiver got, without "GROUP_". */
    fun markHandled(key: String) {
        if (waitingPrefs.contains(key)) waitingPrefs.edit { remove(key) }
    }

    /**
     * For the "missed reminders" notification after the phone started: one line for every group task
     * whose reminder should have rung in the last 12 hours while the phone was off. Needs no internet:
     * the saved copy of the groups tells whether the task is still open. A task that is done, deleted,
     * turned off on this phone, or not in the saved copy gets no line. Everything that was waiting and
     * is past is forgotten here, so the guard does not show the same reminders again one by one.
     */
    suspend fun takeMissedLines(now: Long = System.currentTimeMillis()): List<String> {
        val missed = pickLate(waitingEntries(), now, graceMillis = 0L)
        if (missed.isEmpty()) return emptyList()
        forgetWaiting { it in missed }

        val uid = AuthStorage.getLastUid(context).orEmpty()
        val state = GroupCacheRepository(context).getGroupsState(uid) ?: return emptyList()
        val turnedOff = GroupTaskOffStore.keys(context)
        val lines = LinkedHashMap<String, String>()
        for (key in missed) {
            val groupId = key.substringBefore('|')
            val taskId = taskIdOf(key)
            val itemKey = "$groupId|$taskId"
            if (itemKey in lines || GroupTaskOffStore.keyOf(groupId, taskId) in turnedOff) continue
            val snapshot = state.loaded.firstOrNull { it.groupId == groupId } ?: continue
            val task = snapshot.tasks.firstOrNull { it.id == taskId } ?: continue
            if (task.id in snapshot.done) continue
            lines[itemKey] = context.getString(
                R.string.missed_reminder_line,
                "${task.title} (${snapshot.groupName})",
                DateFormats.shortDateAtClock(context, deadlineMillis(task))
            )
        }
        return lines.values.toList()
    }

    /**
     * Shows the group reminders whose alarm never rang (an app cleaner removed it, or the phone was
     * off), through the normal receiver. Returns how many were sent. Step 1 of ReminderGuard.
     */
    fun deliverLate(now: Long = System.currentTimeMillis()): Int {
        // Reminders past the window can never be shown, so the list must not keep them.
        forgetWaiting { key -> waitingPrefs.getString(key, null)?.let { triggerOf(it) <= now - LATE_WINDOW_MILLIS } == true }
        val waiting = waitingEntries()
        var sent = 0
        for (key in pickLate(waiting, now)) {
            val parts = waiting[key]?.split(SEP) ?: continue
            // Forget it first: a stuck copy of the alarm must never ring a second time, and a failure
            // below must not send the same reminder again and again.
            cancelKey(key)
            val trigger = parts.getOrNull(0)?.toLongOrNull() ?: continue
            if (parts.size < 5) continue
            // No label: "1 day before" is no longer true. The snoozed flag makes the receiver check first
            // that the task is still open and not turned off on this phone.
            val intent = buildIntent(key, trigger, parts[1], parts[2], parts[3], "", parts.getOrNull(5).orEmpty())
                .apply { putExtra(ReminderSnooze.EXTRA_SNOOZED, true) }
            context.sendBroadcast(intent)
            sent++
        }
        return sent
    }

    // groupId|taskId|OPTION_NAME
    private fun keyOf(groupId: String, taskId: String, option: ReminderOption) =
        "$groupId|$taskId|${option.name}"

    fun deadlineMillis(task: GroupTask): Long =
        computeDeadlineMillis(task.dueDateEpochMillis, task.dueTimeHour, task.dueTimeMinute)

    /**
     * Makes the alarms of one group match [tasks]: sets the reminders of every task that is not
     * done and still in the future, and cancels alarms of tasks that are gone, done or past.
     */
    fun sync(groupId: String, groupLabel: String, tasks: List<GroupTask>, doneIds: Set<String>) {
        val now = System.currentTimeMillis()
        val wanted = HashSet<String>()
        // Tasks the person turned off on this phone get no alarms, like a task that is done.
        val turnedOff = GroupTaskOffStore.keys(context)

        for (task in tasks) {
            if (task.id in doneIds) continue
            if (GroupTaskOffStore.keyOf(groupId, task.id) in turnedOff) continue
            val deadline = deadlineMillis(task)
            // With the date, like personal reminders: "1 day before" fires on another day.
            val dueInfo = com.example.util.DueText.dueAt(context, deadline)

            for (option in optionsFor(task)) {
                val trigger = deadline - option.offsetMinutes * 60_000L
                if (trigger <= now) continue

                val key = keyOf(groupId, task.id, option)
                wanted.add(key)
                val value = listOf(trigger.toString(), task.title, groupLabel, dueInfo, option.label, task.priority)
                    .joinToString(SEP)
                if (prefs.getString(key, null) != value) {
                    prefs.edit { putString(key, value) }
                }
                // The waiting copy lets a reminder that never rang be shown late (see deliverLate).
                if (waitingPrefs.getString(key, null) != value) {
                    waitingPrefs.edit { putString(key, value) }
                }
                // Always (re)set: it is idempotent, and it repairs alarms the system dropped.
                setAlarm(key, trigger, task.title, groupLabel, dueInfo, option.label, task.priority)
            }
        }

        val openTaskIds = tasks
            .filter { it.id !in doneIds && GroupTaskOffStore.keyOf(groupId, it.id) !in turnedOff }
            .map { it.id }
            .toSet()

        for (key in prefs.all.keys.toList()) {
            // keepWaiting: a reminder that is past but never rang is decided just below.
            if (key.startsWith("$groupId|") && key !in wanted) cancelKey(key, keepWaiting = true)
        }
        // Keep the waiting copy of a past reminder only while its task is still open and the reminder is
        // still inside the window. Without this, this clean-up would delete a lost reminder before the
        // guard could show it.
        forgetWaiting { key ->
            key.startsWith("$groupId|") && key !in wanted && run {
                val trigger = waitingPrefs.getString(key, null)?.let { triggerOf(it) } ?: 0L
                !(trigger <= now && trigger > now - LATE_WINDOW_MILLIS && taskIdOf(key) in openTaskIds)
            }
        }

        // A snoozed reminder must not outlive its task (deleted, or done on another phone).
        SnoozeStore.entries(context)
            .filter { it.groupId == groupId && it.groupTaskId !in openTaskIds }
            .forEach { SnoozeStore.clear(context, it.key, cancelAlarm = true) }
    }

    fun cancel(groupId: String, taskId: String) {
        for (key in prefs.all.keys.toList()) {
            if (key.startsWith("$groupId|$taskId|")) {
                // Also cancel a snoozed copy of this reminder. Same id the receiver uses for group reminders.
                ReminderSnooze.cancel(context, "GROUP_$key".hashCode())
                cancelKey(key)
            }
        }
        forgetWaiting { it.startsWith("$groupId|$taskId|") }
        SnoozeStore.clearGroupTask(context, groupId, taskId)
    }

    fun cancelGroup(groupId: String) {
        for (key in prefs.all.keys.toList()) {
            if (key.startsWith("$groupId|")) {
                ReminderSnooze.cancel(context, "GROUP_$key".hashCode())
                cancelKey(key)
            }
        }
        forgetWaiting { it.startsWith("$groupId|") }
        SnoozeStore.clearGroup(context, groupId)
    }

    /** Cancels alarms of every group that is not in [keepGroupIds]. */
    fun cancelGroupsNotIn(keepGroupIds: Set<String>) {
        for (key in prefs.all.keys.toList()) {
            if (key.substringBefore('|') !in keepGroupIds) cancelKey(key)
        }
        forgetWaiting { it.substringBefore('|') !in keepGroupIds }
        SnoozeStore.clearGroupsNotIn(context, keepGroupIds)
    }

    /** Alarms are wiped by a reboot or an app update; this puts the remembered ones back. */
    fun rescheduleAll() {
        val now = System.currentTimeMillis()
        for (key in prefs.all.keys.toList()) {
            val parts = (prefs.getString(key, null) ?: continue).split(SEP)
            val trigger = parts.getOrNull(0)?.toLongOrNull()
            // Entries written by older versions have 4 parts; drop them, the next sync re-creates them.
            if (parts.size < 5 || trigger == null || trigger <= now) {
                prefs.edit { remove(key) }
                continue
            }
            setAlarm(key, trigger, parts[1], parts[2], parts[3], parts[4], parts.getOrNull(5).orEmpty())
        }
    }

    private fun intentFor(key: String): Intent =
        Intent(context, TaskReminderReceiver::class.java).apply {
            action = ACTION
            data = ("unitask://group-reminder/" + Uri.encode(key)).toUri()
        }

    /** The intent (with every extra the notification needs) for one group reminder. */
    private fun buildIntent(
        key: String,
        trigger: Long,
        title: String,
        label: String,
        dueInfo: String,
        optionLabel: String,
        priority: String
    ): Intent {
        val parts = key.split("|")
        val groupId = parts.getOrNull(0).orEmpty()
        val taskId = parts.getOrNull(1).orEmpty()
        return intentFor(key).apply {
            putExtra(TaskReminderReceiver.EXTRA_TASK_TITLE, title)
            putExtra(TaskReminderReceiver.EXTRA_COURSE_CODE, label)
            putExtra(TaskReminderReceiver.EXTRA_DUE_INFO, dueInfo)
            putExtra(TaskReminderReceiver.EXTRA_REMINDER_LABEL, optionLabel)
            // The receiver derives its notification id from this, so every alarm gets its own.
            putExtra(TaskReminderReceiver.EXTRA_REMINDER_OPTION, "GROUP_$key")
            putExtra(TaskReminderReceiver.EXTRA_PRIORITY, priority)
            putExtra(TaskReminderReceiver.EXTRA_TRIGGER_AT, trigger)
            if (groupId.isNotBlank()) putExtra(TaskReminderReceiver.EXTRA_GROUP_ID, groupId)
            if (taskId.isNotBlank()) putExtra(TaskReminderReceiver.EXTRA_GROUP_TASK_ID, taskId)
        }
    }

    private fun setAlarm(
        key: String,
        trigger: Long,
        title: String,
        label: String,
        dueInfo: String,
        optionLabel: String,
        priority: String = ""
    ) {
        val intent = buildIntent(key, trigger, title, label, dueInfo, optionLabel, priority)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            key.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = alarmManager ?: return
        try {
            if (priority == "HIGH") {
                // Alarm-clock alarms are not delayed or blocked by battery saving.
                val showIntent = PendingIntent.getActivity(
                    context,
                    key.hashCode(),
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                am.setAlarmClock(AlarmManager.AlarmClockInfo(trigger, showIntent), pendingIntent)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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

    /** Cancels the alarm and forgets the key. The waiting copy goes too, unless [keepWaiting] is set. */
    private fun cancelKey(key: String, keepWaiting: Boolean = false) {
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
        prefs.edit { remove(key) }
        if (!keepWaiting) forgetWaiting { it == key }
    }
}
