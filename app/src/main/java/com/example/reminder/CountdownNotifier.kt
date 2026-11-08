package com.example.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.util.HighPriorityAlarm

/**
 * Delivers and cancels the notification shown when a countdown timer ends.
 */
object CountdownNotifier {

    const val CHANNEL_ID = "unitask_countdown_finished_v1"
    private const val OLD_CHANNEL_ID = "unitask_timer_v1"
    private const val NOTIFICATION_ID = 9920
    private const val REQUEST_CODE_SILENCE = 9924
    private const val REQUEST_CODE_STOP = 9925
    const val EXTRA_TOTAL_MILLIS = "extra_countdown_total_millis"

    private fun ensureChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CHANNEL_ID) == null) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Countdown finished",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when a timer or countdown finishes."
                enableVibration(true)
                vibrationPattern = HighPriorityAlarm.VIBRATION_PATTERN
                setSound(HighPriorityAlarm.soundUri(), audioAttributes)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(channel)
            nm.deleteNotificationChannel(OLD_CHANNEL_ID)
        }
    }

    private fun scheduleSilenceAlarm(context: Context, totalMillis: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val silenceIntent = Intent(context, TimerActionReceiver::class.java).apply {
            action = TimerActionReceiver.ACTION_SILENCE_COUNTDOWN
            putExtra(EXTRA_TOTAL_MILLIS, totalMillis)
        }
        val silencePending = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_SILENCE,
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

    private fun cancelSilenceAlarm(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val silenceIntent = Intent(context, TimerActionReceiver::class.java).apply {
            action = TimerActionReceiver.ACTION_SILENCE_COUNTDOWN
        }
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_SILENCE,
            silenceIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pending != null) {
            am.cancel(pending)
            pending.cancel()
        }
    }

    fun notifyFinished(context: Context, totalMillis: Long, ring: Boolean = true) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val channelId = if (ring) {
            ensureChannel(nm)
            CHANNEL_ID
        } else {
            cancelSilenceAlarm(context)
            HighPriorityAlarm.ensureChannel(context)
            HighPriorityAlarm.CHANNEL_ID_SILENCED
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPending = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(context, TimerActionReceiver::class.java).apply {
            action = TimerActionReceiver.ACTION_STOP_COUNTDOWN
        }
        val stopPending = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_STOP,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val n = maxOf(1L, totalMillis / 60_000L)
        val text = "Your $n minute countdown is done."

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_unitask)
            .setContentTitle("Countdown finished")
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .setDeleteIntent(stopPending)
            .addAction(R.drawable.ic_timer_stop, "Stop", stopPending)

        if (ring) {
            builder.setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSound(HighPriorityAlarm.soundUri(), AudioManager.STREAM_ALARM)
                .setVibrate(HighPriorityAlarm.VIBRATION_PATTERN)
        } else {
            builder.setPriority(NotificationCompat.PRIORITY_LOW)
                .setSound(null)
                .setVibrate(null)
                .setOnlyAlertOnce(true)
        }

        val notification = builder.build()
        if (ring) {
            notification.flags = notification.flags or Notification.FLAG_INSISTENT
            scheduleSilenceAlarm(context, totalMillis)
        }

        nm.notify(NOTIFICATION_ID, notification)
    }

    fun cancelFinished(context: Context) {
        cancelSilenceAlarm(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.cancel(NOTIFICATION_ID)
    }
}
