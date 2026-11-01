package com.example.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.example.data.groups.GroupRepository
import com.example.data.local.AppDatabase
import com.example.data.local.TaskStatus
import com.example.util.AuthStorage
import com.example.util.currentSupabaseUid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * Snooze for reminder notifications. Tapping a snooze button removes the notification and sets one
 * alarm that shows the same reminder again later. Nothing is stored: the alarm carries a copy of
 * the original reminder.
 */
object ReminderSnooze {
    const val ACTION_SNOOZE = "com.example.ACTION_SNOOZE_REMINDER"
    const val EXTRA_SNOOZE_MINUTES = "extra_snooze_minutes"
    const val EXTRA_SNOOZED = "extra_snoozed"

    // Same action that ReminderScheduler uses for normal reminders.
    private const val ACTION_TASK_REMINDER = "com.example.ACTION_TASK_REMINDER"

    /** The snooze buttons: minutes and the text on the button. Change this list to change them. */
    val CHOICES: List<Pair<Int, String>> = listOf(
        30 to "Snooze 30m"
    )

    private fun requestCode(notificationId: Int) = notificationId * 37 + 55

    private fun firedIntent(context: Context, notificationId: Int): Intent =
        Intent(context, TaskReminderReceiver::class.java).apply {
            action = ACTION_TASK_REMINDER
            data = Uri.parse("unitask://snooze/$notificationId")
        }

    /** Sets the alarm that brings the reminder back. [original] holds the reminder's details. */
    fun schedule(context: Context, notificationId: Int, original: Intent, minutes: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = firedIntent(context, notificationId).apply {
            putExtras(original)
            putExtra(TaskReminderReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(EXTRA_SNOOZED, true)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode(notificationId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = System.currentTimeMillis() + minutes * 60_000L
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                }
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        } catch (_: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    /** Cancels a snoozed reminder that has not come back yet. */
    fun cancel(context: Context, notificationId: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode(notificationId),
            firedIntent(context, notificationId),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pending != null) {
            am.cancel(pending)
            pending.cancel()
        }
    }

    /**
     * Asked when a snoozed reminder comes back: is the task still open?
     * Answers true ("show it") whenever it cannot find out, so a reminder is never lost.
     */
    fun isStillNeeded(context: Context, taskId: Long, groupId: String?, groupTaskId: String?): Boolean =
        runBlocking(Dispatchers.IO) {
            try {
                withTimeout(3000L) {
                    if (!groupId.isNullOrBlank() && !groupTaskId.isNullOrBlank()) {
                        val uid = currentSupabaseUid() ?: AuthStorage.getLastUid(context).orEmpty()
                        if (uid.isBlank()) true
                        else groupTaskId !in GroupRepository(context).getDoneTaskIdsOnce(uid, groupId)
                    } else if (taskId > 0L) {
                        val task = AppDatabase.getDatabase(context).taskDao().getTaskById(taskId)
                        task != null && task.status != TaskStatus.COMPLETED
                    } else {
                        true
                    }
                }
            } catch (_: Exception) {
                true
            }
        }
}