package com.example.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.example.MainActivity
import com.example.Repositories
import com.example.data.local.AppDatabase
import com.example.data.local.TaskStatus
import com.example.util.AuthStorage
import com.example.util.currentSupabaseUid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Snooze for reminder notifications. Tapping a snooze button removes the notification and sets one
 * alarm that shows the same reminder again later. The alarm carries a copy of the original
 * reminder. Snoozes started inside the app are remembered separately, in SnoozeStore.
 */
object ReminderSnooze {
    const val ACTION_SNOOZE = "com.example.ACTION_SNOOZE_REMINDER"
    const val EXTRA_SNOOZE_MINUTES = "extra_snooze_minutes"
    const val EXTRA_SNOOZED = "extra_snoozed"
    /** Set on a reminder that came from an in-app snooze. Holds the task card id (see SnoozeStore). */
    const val EXTRA_SNOOZE_KEY = "extra_snooze_key"
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

    /** Sets the alarm that brings the reminder back after [minutes]. [original] holds the reminder's details. */
    fun schedule(context: Context, notificationId: Int, original: Intent, minutes: Int) =
        scheduleAt(context, notificationId, original, System.currentTimeMillis() + minutes * 60_000L)

    /** The same, at an exact time. In-app snooze uses this (see SnoozeStore). */
    fun scheduleAt(context: Context, notificationId: Int, original: Intent, triggerAt: Long) {
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
        try {
            if (original.getStringExtra(TaskReminderReceiver.EXTRA_PRIORITY) == "HIGH") {
                // A snoozed high priority reminder must not be delayed by battery saving either.
                val showIntent = PendingIntent.getActivity(
                    context,
                    requestCode(notificationId),
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showIntent), pending)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
suspend fun isStillNeeded(context: Context, taskId: Long, groupId: String?, groupTaskId: String?): Boolean =
        withContext(Dispatchers.IO) {
            try {
                withTimeout(3000L) {
                    if (!groupId.isNullOrBlank() && !groupTaskId.isNullOrBlank()) {
                        val uid = currentSupabaseUid() ?: AuthStorage.getLastUid(context).orEmpty()
                        if (uid.isBlank()) true
                        else groupTaskId !in Repositories.group(context).getDoneTaskIdsOnce(uid, groupId)
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
