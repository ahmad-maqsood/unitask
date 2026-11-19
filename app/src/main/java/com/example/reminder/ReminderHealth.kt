package com.example.reminder

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.example.R
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
                appContext.getString(R.string.health_notifications_title),
                notificationsOn,
                appContext.getString(R.string.health_notifications_hint)
            ),
            HealthItem(
                HealthCheck.CHANNEL,
                appContext.getString(R.string.health_channel_title),
                channelOk,
                appContext.getString(R.string.health_channel_hint)
            ),
            HealthItem(
                HealthCheck.EXACT_ALARMS,
                appContext.getString(R.string.health_exact_title),
                exactOk,
                appContext.getString(R.string.health_exact_hint)
            ),
            HealthItem(
                HealthCheck.BATTERY,
                appContext.getString(R.string.health_battery_title),
                BackgroundAccess.isIgnoringBatteryOptimizations(appContext),
                appContext.getString(R.string.health_battery_hint)
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
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:$pkg".toUri())
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
        } catch (e: Exception) {
            android.util.Log.w("UniTask", "Could not open the phone setting screen", e)
        }
    }

    /** Rings a test reminder through the real alarm and notification path after [seconds]. */
    fun scheduleTest(context: Context, seconds: Int = 10) {
        val appContext = context.applicationContext
        val am = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(appContext, TaskReminderReceiver::class.java).apply {
            action = "com.example.ACTION_TASK_REMINDER"
            data = "unitask://reminder-test".toUri()
            putExtra(TaskReminderReceiver.EXTRA_TASK_TITLE, appContext.getString(R.string.health_test_title))
            putExtra(TaskReminderReceiver.EXTRA_COURSE_CODE, "MYTASKS")
            putExtra(TaskReminderReceiver.EXTRA_DUE_INFO, appContext.getString(R.string.health_test_body))
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
