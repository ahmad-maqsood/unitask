package com.example.reminder

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.groups.GroupRepository
import com.example.data.local.AppDatabase
import com.example.data.local.TaskStatus
import com.example.util.AuthStorage
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

        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_COURSE_CODE = "extra_course_code"
        const val EXTRA_DUE_INFO = "extra_due_info"
        const val EXTRA_REMINDER_LABEL = "extra_reminder_label"
        const val EXTRA_REMINDER_OPTION = "extra_reminder_option"
        const val EXTRA_PRIORITY = "extra_priority"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_IS_MY_TASK = "extra_is_my_task"
        const val EXTRA_GROUP_ID = "extra_group_id"
        const val EXTRA_GROUP_TASK_ID = "extra_group_task_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        // Handle "Mark as Done" action for personal tasks & MyTasks
        if (intent.action == ACTION_MARK_TASK_DONE) {
            val taskId = intent.getLongExtra(EXTRA_TASK_ID, 0L)
            val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
            if (notifId != 0) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.cancel(notifId)
            }
            if (taskId > 0L) {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val db = AppDatabase.getDatabase(context)
                        db.taskDao().updateStatusAndCompletedAt(taskId, TaskStatus.COMPLETED, System.currentTimeMillis())
                        ReminderScheduler(context).cancelReminder(taskId)
                        if (currentSupabaseUid() != null) {
                            com.example.sync.SupabaseSyncManager.syncAllDataNow(context, db)
                        }
                    } catch (_: Exception) {
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
            }
            if (groupId.isNotBlank() && taskId.isNotBlank()) {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val uid = currentSupabaseUid() ?: AuthStorage.getLastUid(context).orEmpty()
                        if (uid.isNotBlank()) {
                            GroupRepository(context).setTaskDone(uid, groupId, taskId, isDone = true)
                        }
                        GroupReminderScheduler(context).cancel(groupId, taskId)
                    } catch (_: Exception) {
                    } finally {
                        pending.finish()
                    }
                }
            }
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

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

        val title = if (!reminderLabel.isNullOrBlank()) {
            "⏰ Reminder ($reminderLabel): $taskTitle"
        } else {
            "⏰ Reminder: $taskTitle"
        }

        val contentText = when {
            isGroupTask -> {
                if (!reminderLabel.isNullOrBlank()) "[$courseCode] $reminderLabel • $dueInfo" else "[$courseCode] $dueInfo"
            }
            isMyTask -> {
                if (!reminderLabel.isNullOrBlank()) "$reminderLabel • $dueInfo" else dueInfo
            }
            else -> {
                if (!reminderLabel.isNullOrBlank()) "[$courseCode] $reminderLabel • $dueInfo" else "[$courseCode] $dueInfo"
            }
        }

        val isAlarm = priority == "HIGH" && HighPriorityAlarm.isEnabled(context)
        if (isAlarm) HighPriorityAlarm.ensureChannel(context)

        val bigSummary = if (isMyTask) taskTitle else "Assignment for $courseCode: $taskTitle"
        val notificationId = if (!reminderOption.isNullOrBlank()) {
            ((taskId and 0x7FFFFFFF) * 31 + reminderOption.hashCode()).toInt()
        } else {
            taskId.toInt()
        }

        val builder = NotificationCompat.Builder(
            context,
            if (isAlarm) HighPriorityAlarm.CHANNEL_ID else NotificationSound.channelId(context, CHANNEL_ID)
        )
            .setSmallIcon(R.drawable.ic_stat_unitask)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$bigSummary\n$dueInfo${if (!reminderLabel.isNullOrBlank()) " ($reminderLabel)" else ""}")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (isAlarm) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
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

        if (isAlarm) {
            builder.setSound(HighPriorityAlarm.soundUri(), AudioManager.STREAM_ALARM)
                .setVibrate(HighPriorityAlarm.VIBRATION_PATTERN)
                .setTimeoutAfter(HighPriorityAlarm.RING_MILLIS)
        } else {
            val customSound = NotificationSound.uri(context)
            if (customSound != null) builder.setSound(customSound) else builder.setDefaults(NotificationCompat.DEFAULT_SOUND)
        }

        val notification = builder.build()
        if (isAlarm) notification.flags = notification.flags or Notification.FLAG_INSISTENT

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.notify(notificationId, notification)
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
                } catch (_: Exception) {
                }
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
