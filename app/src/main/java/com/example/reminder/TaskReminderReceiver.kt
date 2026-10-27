package com.example.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.util.NotificationSound
import android.app.Notification
import android.media.AudioManager
import com.example.util.HighPriorityAlarm
class TaskReminderReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "unitask_assignment_reminders_v2"
        const val CHANNEL_NAME = "Assignment Deadlines & Reminders"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_COURSE_CODE = "extra_course_code"
        const val EXTRA_DUE_INFO = "extra_due_info"
        const val EXTRA_REMINDER_LABEL = "extra_reminder_label"
        const val EXTRA_REMINDER_OPTION = "extra_reminder_option"
        const val EXTRA_PRIORITY = "extra_priority"
    }

    override fun onReceive(context: Context, intent: Intent) {
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
        createNotificationChannel(context)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("opened_task_id", taskId)
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

        val contentText = if (!reminderLabel.isNullOrBlank()) {
            "[$courseCode] $reminderLabel • $dueInfo"
        } else {
            "[$courseCode] $dueInfo"
        }

        // High priority task and the Settings switch is on: ring like an alarm.
        val isAlarm = priority == "HIGH" && HighPriorityAlarm.isEnabled(context)
        if (isAlarm) HighPriorityAlarm.ensureChannel(context)

        val builder = NotificationCompat.Builder(
            context,
            if (isAlarm) HighPriorityAlarm.CHANNEL_ID else NotificationSound.channelId(context, CHANNEL_ID)
        )
            .setSmallIcon(R.drawable.ic_stat_unitask)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Assignment for $courseCode: $taskTitle\n$dueInfo${if (!reminderLabel.isNullOrBlank()) " ($reminderLabel)" else ""}")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (isAlarm) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (isAlarm) {
            // Android 8+ takes sound and vibration from the channel; older versions from here.
            builder.setSound(HighPriorityAlarm.soundUri(), AudioManager.STREAM_ALARM)
                .setVibrate(HighPriorityAlarm.VIBRATION_PATTERN)
                // Stops by itself if you do not react.
                .setTimeoutAfter(HighPriorityAlarm.RING_MILLIS)
        } else {
            val customSound = NotificationSound.uri(context)
            if (customSound != null) builder.setSound(customSound) else builder.setDefaults(NotificationCompat.DEFAULT_SOUND)
        }

        val notification = builder.build()
        // Keep repeating the sound until the notification is opened, swiped away or times out.
        if (isAlarm) notification.flags = notification.flags or Notification.FLAG_INSISTENT


        val notificationId = if (!reminderOption.isNullOrBlank()) {
            ((taskId and 0x7FFFFFFF) * 31 + reminderOption.hashCode()).toInt()
        } else {
            taskId.toInt()
        }

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
                NotificationSound.uri(context)?.let { setSound(it, NotificationSound.audioAttributes()) }
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
