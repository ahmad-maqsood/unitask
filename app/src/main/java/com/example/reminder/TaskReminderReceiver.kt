package com.example.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity

class TaskReminderReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "unitask_assignment_reminders"
        const val CHANNEL_NAME = "Assignment Deadlines & Reminders"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_COURSE_CODE = "extra_course_code"
        const val EXTRA_DUE_INFO = "extra_due_info"
        const val EXTRA_REMINDER_LABEL = "extra_reminder_label"
        const val EXTRA_REMINDER_OPTION = "extra_reminder_option"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, 0L)
        val taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE) ?: "Upcoming Assignment"
        val courseCode = intent.getStringExtra(EXTRA_COURSE_CODE) ?: "Course"
        val dueInfo = intent.getStringExtra(EXTRA_DUE_INFO) ?: "Due soon"
        val reminderLabel = intent.getStringExtra(EXTRA_REMINDER_LABEL)
        val reminderOption = intent.getStringExtra(EXTRA_REMINDER_OPTION)

        createNotificationChannel(context)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
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

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Assignment for $courseCode: $taskTitle\n$dueInfo${if (!reminderLabel.isNullOrBlank()) " ($reminderLabel)" else ""}")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationId = if (!reminderOption.isNullOrBlank()) {
            ((taskId and 0x7FFFFFFF) * 31 + reminderOption.hashCode()).toInt()
        } else {
            taskId.toInt()
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.notify(notificationId, notification)
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies students about upcoming assignments and homework deadlines"
                enableVibration(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }
}
