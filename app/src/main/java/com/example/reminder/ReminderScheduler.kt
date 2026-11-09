package com.example.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.example.data.local.AppDatabase
import com.example.data.local.ReminderOption
import com.example.data.local.TaskItem
import com.example.MainActivity
import com.example.data.local.Priority
import com.example.util.HighPriorityAlarm
import com.example.data.local.TaskStatus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ReminderScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    fun calculateDeadlineMillis(task: TaskItem): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = task.dueDateEpochMillis
        cal.set(Calendar.HOUR_OF_DAY, task.dueTimeHour)
        cal.set(Calendar.MINUTE, task.dueTimeMinute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** Trigger time for each selected reminder that is still in the future. */
    private fun upcomingTriggers(task: TaskItem): Map<ReminderOption, Long> {
        val deadlineMillis = calculateDeadlineMillis(task)
        val now = System.currentTimeMillis()
        return task.reminderOptions
            .associateWith { deadlineMillis - it.offsetMinutes * 60 * 1000L }
            .filterValues { it > now }
    }

    /** The earliest upcoming reminder, or null if none is left. */
    fun calculateReminderMillis(task: TaskItem): Long? =
        upcomingTriggers(task).values.minOrNull()

    fun scheduleReminder(task: TaskItem, courseCode: String) {
        // Clear everything first so reminders the user removed do not still fire.
        cancelReminder(task.id)

        for ((option, triggerTime) in upcomingTriggers(task)) {
            armReminder(task, courseCode, option, triggerTime)
        }
    }

    /** Sets the alarm of ONE reminder. The task's other alarms are not touched. */
    private fun armReminder(task: TaskItem, courseCode: String, option: ReminderOption, triggerTime: Long) {
        val intent = buildReminderIntent(task, courseCode, option, triggerTime)
        // Remember that this alarm is waiting, so we can tell if it never rang.
        MissedReminders.recordArmed(context, task.id, option, triggerTime)
        val requestCode = requestCodeFor(task.id, option)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // High priority tasks use an alarm-clock alarm, which phones do not delay or block
        // with battery saving. Everything else keeps the normal alarm.
        val useAlarmClock = task.priority == Priority.HIGH
        alarmManager?.let { am ->
            try {
                if (useAlarmClock) {
                    val showIntent = PendingIntent.getActivity(
                        context,
                        requestCode,
                        Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerTime, showIntent), pendingIntent)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (am.canScheduleExactAlarms()) {
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                    } else {
                        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                    am.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            } catch (_: SecurityException) {
                // Fallback to standard inexact alarm
                am.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        }
    }

        /**
     * Re-creates the alarms of every unfinished task. Alarms are lost on reboot and on app
     * update, and tasks pulled from the cloud have never had one, so this runs after both.
     */
    suspend fun rescheduleAll(database: AppDatabase) {
        for (task in database.taskDao().getAllActiveTasksSync()) {
            if (task.status == TaskStatus.COMPLETED) {
                cancelReminder(task.id)
                continue
            }
            val course = database.courseDao().getCourseByIdSync(task.courseId) ?: continue
            scheduleReminder(task, course.code)
        }
    }

    /** Cancels alarms of all active tasks. */
    suspend fun cancelAll(database: AppDatabase) {
        for (task in database.taskDao().getAllActiveTasksSync()) {
            cancelReminder(task.id)
        }
    }

    /** The intent (with every extra the notification needs) for one reminder of one task. */
    private fun buildReminderIntent(
        task: TaskItem,
        courseCode: String,
        option: ReminderOption,
        triggerTime: Long
    ): Intent = reminderIntent(task.id, option).apply {
        putExtra(TaskReminderReceiver.EXTRA_TASK_ID, task.id)
        putExtra(TaskReminderReceiver.EXTRA_TASK_TITLE, task.title)
        putExtra(TaskReminderReceiver.EXTRA_COURSE_CODE, courseCode)
        putExtra(TaskReminderReceiver.EXTRA_REMINDER_LABEL, option.label)
        putExtra(TaskReminderReceiver.EXTRA_REMINDER_OPTION, option.name)
        putExtra(TaskReminderReceiver.EXTRA_TRIGGER_AT, triggerTime)
        putExtra(TaskReminderReceiver.EXTRA_PRIORITY, task.priority.name)
        putExtra(
            TaskReminderReceiver.EXTRA_DUE_INFO,
            "Due at ${com.example.util.TimeFormatter.format12Hour(task.dueTimeHour, task.dueTimeMinute)}"
        )
    }

    /** True while the system still holds the alarm of this reminder (it is gone after a reboot or a force stop). */
    private fun isAlarmSet(taskId: Long, option: ReminderOption): Boolean =
        PendingIntent.getBroadcast(
            context,
            requestCodeFor(taskId, option),
            reminderIntent(taskId, option),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) != null

    /**
     * Sets the missing alarms again: every upcoming reminder of an unfinished task that has none.
     * Alarms that are in place are left alone. Returns how many tasks were fixed.
     */
    suspend fun rearmMissing(database: AppDatabase): Int {
        var fixed = 0
        for (task in database.taskDao().getAllActiveTasksSync()) {
            if (task.status == TaskStatus.COMPLETED) continue
            val missing = upcomingTriggers(task).filterKeys { !isAlarmSet(task.id, it) }
            if (missing.isEmpty()) continue
            val course = database.courseDao().getCourseByIdSync(task.courseId) ?: continue
            // Only the missing alarms are set. Setting the whole task again would also cancel an alarm
            // that is a few minutes late but still on its way.
            for ((option, triggerTime) in missing) armReminder(task, course.code, option, triggerTime)
            fixed++
        }
        return fixed
    }

    /**
     * Shows a reminder whose alarm never rang, right now, through the normal receiver (so the
     * "still open" check, the notification and the bell history all work as usual).
     */
    fun deliverLate(task: TaskItem, courseCode: String, option: ReminderOption, triggerTime: Long) {
        // Take the alarm away first, so a stuck copy of it can never ring a second time.
        cancelAlarm(requestCodeFor(task.id, option), reminderIntent(task.id, option))
        val due = SimpleDateFormat("EEE, MMM d 'at' h:mm a", Locale.getDefault())
            .format(Date(calculateDeadlineMillis(task)))
        val intent = buildReminderIntent(task, courseCode, option, triggerTime).apply {
            // "1 hour before" is no longer true. Without a label the title is just "Due: <task>".
            removeExtra(TaskReminderReceiver.EXTRA_REMINDER_LABEL)
            putExtra(TaskReminderReceiver.EXTRA_DUE_INFO, "Due $due")
        }
        context.sendBroadcast(intent)
    }

    fun cancelReminder(taskId: Long) {
        MissedReminders.forgetTask(context, taskId)
        // Alarm set by earlier app versions: one per task, no data URI.
        cancelAlarm(taskId.toInt(), reminderIntent(taskId, null))
        // Cancel all reminder options for this task with both requestCode and legacy taskId code.
        ReminderOption.entries.forEach { option ->
            cancelAlarm(requestCodeFor(taskId, option), reminderIntent(taskId, option))
            cancelAlarm(taskId.toInt(), reminderIntent(taskId, option))
        }
    }

    private fun requestCodeFor(taskId: Long, option: ReminderOption?): Int {
        val optIndex = option?.ordinal?.plus(1) ?: 0
        return ((taskId and 0x7FFFFFFF) * 31 + optIndex).toInt()
    }

    /**
     * Each (task, reminder) pair gets its own data URI. PendingIntents are matched on
     * action, data and request code, so this keeps every alarm separate.
     */
    private fun reminderIntent(taskId: Long, option: ReminderOption?): Intent =
        Intent(context, TaskReminderReceiver::class.java).apply {
            action = "com.example.ACTION_TASK_REMINDER"
            if (option != null) {
                data = Uri.parse("unitask://reminder/$taskId/${option.name}")
            }
        }

    private fun cancelAlarm(requestCode: Int, intent: Intent) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager?.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}
