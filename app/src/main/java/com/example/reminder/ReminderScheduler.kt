package com.example.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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

    fun calculateReminderMillis(task: TaskItem): Long? {
        if (task.reminderOption == ReminderOption.NONE) return null
        val deadlineMillis = calculateDeadlineMillis(task)
        val offsetMillis = task.reminderOption.offsetMinutes * 60 * 1000L
        val triggerTime = deadlineMillis - offsetMillis
        return if (triggerTime > System.currentTimeMillis()) triggerTime else null
    }

    fun scheduleReminder(task: TaskItem, courseCode: String) {
        if (task.reminderOption == ReminderOption.NONE) {
            cancelReminder(task.id)
            return
        }

        val triggerTime = calculateReminderMillis(task) ?: return

        val intent = Intent(context, TaskReminderReceiver::class.java).apply {
            action = "com.example.ACTION_TASK_REMINDER"
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

    fun cancelReminder(taskId: Long) {
        val intent = Intent(context, TaskReminderReceiver::class.java).apply {
            action = "com.example.ACTION_TASK_REMINDER"
        }
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
