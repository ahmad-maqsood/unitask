package com.example.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.groups.GroupRepository
import com.example.data.local.AppDatabase
import com.example.data.local.TaskStatus
import com.example.ui.screens.groupTaskItemId
import com.example.util.AuthStorage
import com.example.util.CompletionLog
import com.example.util.HighPriorityAlarm
import com.example.util.NotificationSound
import com.example.util.currentSupabaseUid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TaskReminderReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "unitask_assignment_reminders_v2"
        const val CHANNEL_NAME = "Assignment Deadlines & Reminders"
        const val ACTION_MARK_TASK_DONE = "com.example.ACTION_MARK_TASK_DONE"
        const val ACTION_MARK_GROUP_TASK_DONE = "com.example.ACTION_MARK_GROUP_TASK_DONE"
        const val ACTION_SILENCE_ALARM = "com.example.ACTION_SILENCE_ALARM"

        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_COURSE_CODE = "extra_course_code"
        const val EXTRA_DUE_INFO = "extra_due_info"
        const val EXTRA_REMINDER_LABEL = "extra_reminder_label"
        const val EXTRA_REMINDER_OPTION = "extra_reminder_option"
        const val EXTRA_PRIORITY = "extra_priority"
        const val EXTRA_TRIGGER_AT = "extra_trigger_at"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_IS_MY_TASK = "extra_is_my_task"
        const val EXTRA_GROUP_ID = "extra_group_id"
        const val EXTRA_GROUP_TASK_ID = "extra_group_task_id"
        const val GROUP_KEY_DEADLINES = "com.example.DEADLINE_REMINDERS"
        private const val SUMMARY_ID = 9903
        private const val PRIMARY_COLOR = 0xFF4F46E5.toInt()

        /**
         * Android does not remove a group summary when its last reminder is removed, so the empty
         * "Deadlines and reminders" bar would stay behind after Snooze or Mark as Done.
         * Call this right after cancelling a reminder: it removes the summary when no reminder is left.
         * [removedId] is the reminder that was just cancelled (ignored even if it still shows as active).
         */
        fun removeSummaryIfEmpty(context: Context, removedId: Int) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            try {
                val anyReminderLeft = nm.activeNotifications.any {
                    it.id != SUMMARY_ID &&
                        it.id != removedId &&
                        it.notification.group == GROUP_KEY_DEADLINES
                }
                if (!anyReminderLeft) nm.cancel(SUMMARY_ID)
            } catch (_: Exception) {
                // If we cannot check, leave the summary alone.
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        // Handle "Mark as Done" action for personal tasks & MyTasks
        if (intent.action == ACTION_MARK_TASK_DONE) {
            val taskId = intent.getLongExtra(EXTRA_TASK_ID, 0L)
            val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
            if (notifId != 0) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.cancel(notifId)
                cancelSilenceAlarm(context, notifId)
                removeSummaryIfEmpty(context, notifId)
            }
            if (taskId > 0L) {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val db = AppDatabase.getDatabase(context)
                        val doneAt = System.currentTimeMillis()
                        // Progress history: log the tick once, before the database write.
                        if (db.taskDao().getTaskById(taskId)?.status != TaskStatus.COMPLETED) {
                            CompletionLog.record(context, taskId, doneAt)
                        }
                        db.taskDao().updateStatusAndCompletedAt(taskId, TaskStatus.COMPLETED, doneAt)
                        com.example.widget.UniTaskWidget.refresh(context)
                        ReminderScheduler(context).cancelReminder(taskId)
                        SnoozeStore.clear(context, taskId, cancelAlarm = true)
                    } catch (e: Exception) {
                        android.util.Log.w("UniTask", "Mark as done from notification failed", e)
                        toastOnMain(context, "Could not mark the task as done")
                    } finally {
                        pending.finish()
                    }
                }
            }
            return
        }

        // Handle "Mark as Done" action for group tasks
        if (intent.action == ACTION_MARK_GROUP_TASK_DONE) {
            val groupId = intent.getStringExtra(EXTRA_GROUP_ID).orEmpty()
            val taskId = intent.getStringExtra(EXTRA_GROUP_TASK_ID).orEmpty()
            val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
            if (notifId != 0) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.cancel(notifId)
                cancelSilenceAlarm(context, notifId)
                removeSummaryIfEmpty(context, notifId)
            }
            if (groupId.isNotBlank() && taskId.isNotBlank()) {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val uid = currentSupabaseUid() ?: AuthStorage.getLastUid(context).orEmpty()
                        if (uid.isNotBlank()) {
                            val repo = GroupRepository(context)
                            repo.setTaskDone(uid, groupId, taskId, isDone = true)
                            // setTaskDone sends in the background. Wait here, or the phone may stop
                            // the app before the tick reaches the server.
                            repo.flushPendingDone()
                        }
                        GroupReminderScheduler(context).cancel(groupId, taskId)
                    } catch (e: Exception) {
                        android.util.Log.w("UniTask", "Mark as done from notification failed", e)
                        toastOnMain(context, "Could not mark the task as done")
                    } finally {
                        pending.finish()
                    }
                }
            }
            return
        }

        // Handle the "Snooze" buttons: remove the notification and bring the reminder back later.
        if (intent.action == ReminderSnooze.ACTION_SNOOZE) {
            val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
            val minutes = intent.getIntExtra(ReminderSnooze.EXTRA_SNOOZE_MINUTES, 30)
            if (notifId != 0) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.cancel(notifId)
                cancelSilenceAlarm(context, notifId)
                removeSummaryIfEmpty(context, notifId)
                // Keep it in SnoozeStore, so the task card shows "Snoozed until ..." and a reboot keeps it.
                val snoozeGroupId = intent.getStringExtra(EXTRA_GROUP_ID).orEmpty()
                val snoozeGroupTaskId = intent.getStringExtra(EXTRA_GROUP_TASK_ID).orEmpty()
                val snoozeRowId = intent.getLongExtra(EXTRA_TASK_ID, 0L)
                val snoozeKey = when {
                    snoozeGroupId.isNotBlank() && snoozeGroupTaskId.isNotBlank() ->
                        groupTaskItemId(snoozeGroupId, snoozeGroupTaskId)
                    snoozeRowId > 0L -> snoozeRowId
                    else -> null
                }
                if (snoozeKey != null) {
                    SnoozeStore.set(
                        context,
                        SnoozeStore.Entry(
                            key = snoozeKey,
                            until = System.currentTimeMillis() + minutes * 60_000L,
                            taskId = if (snoozeGroupId.isBlank()) snoozeRowId else 0L,
                            title = intent.getStringExtra(EXTRA_TASK_TITLE).orEmpty(),
                            courseCode = intent.getStringExtra(EXTRA_COURSE_CODE).orEmpty(),
                            dueInfo = intent.getStringExtra(EXTRA_DUE_INFO).orEmpty(),
                            priority = intent.getStringExtra(EXTRA_PRIORITY).orEmpty(),
                            groupId = snoozeGroupId,
                            groupTaskId = snoozeGroupTaskId
                        )
                    )
                } else {
                    ReminderSnooze.schedule(context, notifId, intent, minutes)
                }
            }
            return
        }
        if (intent.action == ACTION_SILENCE_ALARM) {
            val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val isStillActive = try {
                nm?.activeNotifications?.any { it.id == notifId } == true
            } catch (_: Exception) {
                false
            }
            if (!isStillActive) return
        }

        // An in-app snooze is over once its alarm fires: forget it so the card stops saying "Snoozed".
        if (intent.action != ACTION_SILENCE_ALARM && intent.hasExtra(ReminderSnooze.EXTRA_SNOOZE_KEY)) {
            SnoozeStore.clearIfDue(context, intent.getLongExtra(ReminderSnooze.EXTRA_SNOOZE_KEY, 0L))
        }
        // This alarm has gone off, so it is no longer "waiting". See MissedReminders.
        MissedReminders.markHandled(
            context,
            intent.getLongExtra(EXTRA_TASK_ID, 0L),
            intent.getStringExtra(EXTRA_REMINDER_OPTION),
            intent.getLongExtra(EXTRA_TRIGGER_AT, 0L)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val taskId = intent.getLongExtra(EXTRA_TASK_ID, 0L)
        val groupId = intent.getStringExtra(EXTRA_GROUP_ID)
        val groupTaskId = intent.getStringExtra(EXTRA_GROUP_TASK_ID)
        val isGroupTask = !groupId.isNullOrBlank() && !groupTaskId.isNullOrBlank()

        // Only show the reminder if the task is still open. This stops reminders for tasks that were
        // finished on another phone or deleted after the alarm was set. Group tasks are skipped here
        // (they are checked when they sync), unless it is a snoozed reminder.
        val checkStillOpen = intent.getBooleanExtra(ReminderSnooze.EXTRA_SNOOZED, false) ||
            (!isGroupTask && taskId > 0L)
        if (checkStillOpen) {
            // A snoozed group reminder asks the server, so wait off the main thread.
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // If the check fails, show the reminder: never lose one.
                    val needed = try {
                        ReminderSnooze.isStillNeeded(context, taskId, groupId, groupTaskId)
                    } catch (_: Exception) {
                        true
                    }
                    if (needed) showReminder(context, intent)
                } finally {
                    pending.finish()
                }
            }
            return
        }
        showReminder(context, intent)
    }

    /** Shows a short message from a background thread (the receiver runs its work off the main thread). */
    private fun toastOnMain(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    /** Builds and shows one reminder notification. Safe to call off the main thread. */
    private fun showReminder(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, 0L)
        val taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE) ?: "Upcoming Assignment"
        val courseCode = intent.getStringExtra(EXTRA_COURSE_CODE) ?: "Course"
        val dueInfo = intent.getStringExtra(EXTRA_DUE_INFO) ?: "Due soon"
        val reminderLabel = intent.getStringExtra(EXTRA_REMINDER_LABEL)
        val reminderOption = intent.getStringExtra(EXTRA_REMINDER_OPTION)
        val priority = intent.getStringExtra(EXTRA_PRIORITY)
        val groupId = intent.getStringExtra(EXTRA_GROUP_ID)
        val groupTaskId = intent.getStringExtra(EXTRA_GROUP_TASK_ID)
        val isGroupTask = !groupId.isNullOrBlank() && !groupTaskId.isNullOrBlank()
        createNotificationChannel(context)

        val isMyTask = !isGroupTask && courseCode.trim().replace("\\s+".toRegex(), "").uppercase() == "MYTASKS"

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (isGroupTask) {
                putExtra("open_group_id", groupId)
            } else {
                putExtra("opened_task_id", taskId)
                putExtra(EXTRA_IS_MY_TASK, isMyTask)
            }
        }

        val activityRequestCode = if (!reminderOption.isNullOrBlank()) {
            ((taskId and 0x7FFFFFFF) * 31 + reminderOption.hashCode()).toInt()
        } else {
            taskId.toInt()
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            activityRequestCode,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = when {
            reminderLabel.isNullOrBlank() -> "Due: $taskTitle"
            reminderLabel.equals("At deadline", ignoreCase = true) -> "Due now: $taskTitle"
            reminderLabel.lowercase().endsWith(" before") -> {
                val span = reminderLabel.substring(0, reminderLabel.length - 7).trim()
                "Due in $span: $taskTitle"
            }
            else -> "Due ($reminderLabel): $taskTitle"
        }

        val contentText = when {
            isMyTask -> dueInfo
            courseCode.isNotBlank() -> "[$courseCode] $dueInfo"
            else -> dueInfo
        }

        // "Ring alarm" on a task is saved as HIGH priority (see TaskItem.ringsAlarm).
        val isAlarm = priority == "HIGH"
        val isSilence = intent.action == ACTION_SILENCE_ALARM
        if (isAlarm || isSilence) HighPriorityAlarm.ensureChannel(context)

        val bigSummary = if (isMyTask) taskTitle else "Assignment for $courseCode: $taskTitle"
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0).takeIf { it != 0 }
            ?: if (!reminderOption.isNullOrBlank()) {
                ((taskId and 0x7FFFFFFF) * 31 + reminderOption.hashCode()).toInt()
            } else {
                taskId.toInt()
            }

        val channelId = when {
            isSilence -> HighPriorityAlarm.CHANNEL_ID_SILENCED
            isAlarm -> HighPriorityAlarm.CHANNEL_ID
            else -> NotificationSound.channelId(context, CHANNEL_ID)
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_unitask)
            .setColor(PRIMARY_COLOR)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$bigSummary\n$dueInfo")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (isAlarm) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setGroup(GROUP_KEY_DEADLINES)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        // Add interactive "Mark as Done" action button
        if (isGroupTask) {
            val markDoneIntent = Intent(context, TaskReminderReceiver::class.java).apply {
                action = ACTION_MARK_GROUP_TASK_DONE
                putExtra(EXTRA_GROUP_ID, groupId)
                putExtra(EXTRA_GROUP_TASK_ID, groupTaskId)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
            val markDonePending = PendingIntent.getBroadcast(
                context,
                (notificationId * 37 + 7),
                markDoneIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(R.drawable.ic_stat_unitask, "Mark as Done", markDonePending)
        } else if (taskId > 0L) {
            val markDoneIntent = Intent(context, TaskReminderReceiver::class.java).apply {
                action = ACTION_MARK_TASK_DONE
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
            val markDonePending = PendingIntent.getBroadcast(
                context,
                (notificationId * 37 + 7),
                markDoneIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(R.drawable.ic_stat_unitask, "Mark as Done", markDonePending)
        }

        // Snooze button: bring the same reminder back after 30 minutes.
        for ((minutes, buttonText) in ReminderSnooze.CHOICES) {
            val snoozeIntent = Intent(context, TaskReminderReceiver::class.java).apply {
                action = ReminderSnooze.ACTION_SNOOZE
                // Data makes each button's PendingIntent different from the others.
                data = Uri.parse("unitask://snooze-action/$notificationId/$minutes")
                putExtras(intent)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(ReminderSnooze.EXTRA_SNOOZE_MINUTES, minutes)
            }
            val snoozePending = PendingIntent.getBroadcast(
                context,
                notificationId * 37 + 20 + minutes,
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(R.drawable.ic_stat_unitask, buttonText, snoozePending)
        }
        if (isAlarm && !isSilence) {
            builder.setSound(HighPriorityAlarm.soundUri(), AudioManager.STREAM_ALARM)
                .setVibrate(HighPriorityAlarm.VIBRATION_PATTERN)
        } else if (isSilence) {
            builder.setSound(null)
                .setVibrate(null)
                .setOnlyAlertOnce(true)
        } else {
            val customSound = NotificationSound.uri(context)
            if (customSound != null) builder.setSound(customSound) else builder.setDefaults(NotificationCompat.DEFAULT_SOUND)
        }

        val notification = builder.build()
        if (isAlarm && !isSilence) {
            notification.flags = notification.flags or Notification.FLAG_INSISTENT
        }

        if (isAlarm && !isSilence) {
            scheduleSilenceAlarm(context, notificationId, intent)
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        // Keep only the newest reminder of a task in the tray: remove its earlier ones.
        // "Earlier" means a bigger offset (1 day before is earlier than 1 hour before), so a snoozed
        // earlier reminder never removes a later one.
        val currentOption = com.example.data.local.ReminderOption.entries.firstOrNull { it.name == reminderOption }
        if (!isSilence && taskId > 0L && currentOption != null) {
            for (other in com.example.data.local.ReminderOption.entries) {
                if (other.offsetMinutes <= currentOption.offsetMinutes) continue
                val olderId = ((taskId and 0x7FFFFFFF) * 31 + other.name.hashCode()).toInt()
                notificationManager?.cancel(olderId)
                cancelSilenceAlarm(context, olderId)
            }
        }
        notificationManager?.notify(notificationId, notification)

        // The test reminder from "Check reminders" is not saved in the bell history.
        if (!isSilence && intent.data?.host != "reminder-test") {
            com.example.util.NotificationHistory.add(
                context,
                kind = if (isGroupTask) "Group deadline" else "Deadline",
                title = title,
                body = "$bigSummary\n$dueInfo",
                target = when {
                    isGroupTask -> "group:$groupId"
                    isMyTask -> "tab:MYTASKS"
                    else -> "tab:DEADLINES"
                },
                key = "n|$notificationId"
            )
        }

        val summaryNotification = NotificationCompat.Builder(
            context,
            when {
                isSilence -> HighPriorityAlarm.CHANNEL_ID_SILENCED
                isAlarm -> HighPriorityAlarm.CHANNEL_ID
                else -> NotificationSound.channelId(context, CHANNEL_ID)
            }
        )
            .setSmallIcon(R.drawable.ic_stat_unitask)
            .setColor(PRIMARY_COLOR)
            .setStyle(NotificationCompat.InboxStyle().setSummaryText("Deadlines and reminders"))
            .setGroup(GROUP_KEY_DEADLINES)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        notificationManager?.notify(SUMMARY_ID, summaryNotification)
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val targetId = NotificationSound.channelId(context, CHANNEL_ID)
        notificationManager.deleteNotificationChannel("unitask_assignment_reminders")
        NotificationSound.deleteOtherChannels(context, notificationManager, CHANNEL_ID)
        if (notificationManager.getNotificationChannel(targetId) == null) {
            val channel = NotificationChannel(
                targetId,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies students about upcoming assignments and homework deadlines"
                enableVibration(true)
                try {
                    NotificationSound.uri(context)?.let { setSound(it, NotificationSound.audioAttributes()) }
                } catch (e: Exception) {
                    android.util.Log.w("UniTask", "Could not set the reminder channel sound, using the default", e)
                }
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun scheduleSilenceAlarm(context: Context, notificationId: Int, originalIntent: Intent) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val silenceIntent = Intent(context, TaskReminderReceiver::class.java).apply {
            action = ACTION_SILENCE_ALARM
            putExtras(originalIntent)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            data = Uri.parse("unitask://silence-alarm/$notificationId")
        }
        val silencePending = PendingIntent.getBroadcast(
            context,
            notificationId * 37 + 99,
            silenceIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = System.currentTimeMillis() + HighPriorityAlarm.RING_MILLIS
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, silencePending)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, silencePending)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, silencePending)
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, silencePending)
            }
        } catch (_: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, triggerAt, silencePending)
        }
    }

    private fun cancelSilenceAlarm(context: Context, notificationId: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val silenceIntent = Intent(context, TaskReminderReceiver::class.java).apply {
            action = ACTION_SILENCE_ALARM
            data = Uri.parse("unitask://silence-alarm/$notificationId")
        }
        val pending = PendingIntent.getBroadcast(
            context,
            notificationId * 37 + 99,
            silenceIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pending != null) {
            am.cancel(pending)
            pending.cancel()
        }
    }
}
