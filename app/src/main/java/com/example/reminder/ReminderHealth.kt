package com.example.reminder

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.example.util.BackgroundAccess
import com.example.util.NotificationSound

enum class HealthCheck { NOTIFICATIONS, CHANNEL, EXACT_ALARMS, BATTERY }

data class HealthItem(
    val check: HealthCheck,
    val title: String,
    val ok: Boolean,
    val hint: String
)

/** Checks that reminders can reach the user, and sends a test reminder. */
object ReminderHealth {
    private const val TEST_REQUEST_CODE = 9921
    const val TEST_NOTIFICATION_ID = 9920

    fun check(context: Context): List<HealthItem> {
        val appContext = context.applicationContext

        val notificationsOn = NotificationManagerCompat.from(appContext).areNotificationsEnabled()

        var channelOk = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val channel = nm?.getNotificationChannel(
                NotificationSound.channelId(appContext, TaskReminderReceiver.CHANNEL_ID)
            )
            // A missing channel is fine: it is created when the first reminder rings.
            if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) channelOk = false
        }

        val exactOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)?.canScheduleExactAlarms() == true
        } else {
            true
        }

        return listOf(
            HealthItem(
                HealthCheck.NOTIFICATIONS,
                "Notifications are allowed",
                notificationsOn,
                "UniTask cannot show anything while notifications are off."
            ),
            HealthItem(
                HealthCheck.CHANNEL,
                "Reminders are not blocked",
                channelOk,
                "The reminder category is switched off in the phone's notification settings."
            ),
            HealthItem(
                HealthCheck.EXACT_ALARMS,
                "Reminders ring on time",
                exactOk,
                "Without \"Alarms & reminders\" the phone may delay reminders by several minutes."
            ),
            HealthItem(
                HealthCheck.BATTERY,
                "Battery saver will not stop reminders",
                BackgroundAccess.isIgnoringBatteryOptimizations(appContext),
                "Battery restrictions can stop reminders when the app is closed."
            )
        )
    }

    /** Opens the phone screen where a failed check can be fixed. */
    fun openFix(context: Context, check: HealthCheck) {
        val pkg = context.packageName
        val intent = when (check) {
            HealthCheck.NOTIFICATIONS, HealthCheck.CHANNEL ->
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
            HealthCheck.EXACT_ALARMS ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$pkg"))
                } else {
                    return
                }
            HealthCheck.BATTERY -> {
                BackgroundAccess.requestIgnoreBatteryOptimizations(context)
                return
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    /** Rings a test reminder through the real alarm and notification path after [seconds]. */
    fun scheduleTest(context: Context, seconds: Int = 10) {
        val appContext = context.applicationContext
        val am = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(appContext, TaskReminderReceiver::class.java).apply {
            action = "com.example.ACTION_TASK_REMINDER"
            data = Uri.parse("unitask://reminder-test")
            putExtra(TaskReminderReceiver.EXTRA_TASK_TITLE, "Test reminder")
            putExtra(TaskReminderReceiver.EXTRA_COURSE_CODE, "MYTASKS")
            putExtra(TaskReminderReceiver.EXTRA_DUE_INFO, "It works. Reminders reach this phone.")
            putExtra(TaskReminderReceiver.EXTRA_NOTIFICATION_ID, TEST_NOTIFICATION_ID)
        }
        val pending = PendingIntent.getBroadcast(
            appContext,
            TEST_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = System.currentTimeMillis() + seconds * 1000L
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        } catch (_: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }
}
