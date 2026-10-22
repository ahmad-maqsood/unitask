package com.example.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.example.data.local.ReminderOption
import com.example.data.local.TaskItem
import java.util.Calendar

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
            val intent = reminderIntent(task.id, option).apply {
                putExtra(TaskReminderReceiver.EXTRA_TASK_ID, task.id)
                putExtra(TaskReminderReceiver.EXTRA_TASK_TITLE, task.title)
                putExtra(TaskReminderReceiver.EXTRA_COURSE_CODE, courseCode)
                putExtra(
                    TaskReminderReceiver.EXTRA_DUE_INFO,
                    "Due at ${com.example.util.TimeFormatter.format12Hour(task.dueTimeHour, task.dueTimeMinute)}"
                )
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                task.id.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager?.let { am ->
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
    }

    fun cancelReminder(taskId: Long) {
        // Alarm set by earlier app versions: one per task, no data URI.
        cancelAlarm(taskId, reminderIntent(taskId, null))
        // One alarm per selected reminder.
        ReminderOption.entries.forEach { option ->
            cancelAlarm(taskId, reminderIntent(taskId, option))
        }
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

    private fun cancelAlarm(taskId: Long, intent: Intent) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager?.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}
