package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build

/**
 * "Alarm for high priority tasks": when this is on, reminders of HIGH priority tasks ring like an
 * alarm (alarm sound and volume, repeating, vibrating) instead of the normal notification sound.
 * Off by default. The setting is kept in SharedPreferences, next to the other settings.
 */
object HighPriorityAlarm {
    private const val PREFS = "unitask_settings"
    private const val KEY_ENABLED = "high_priority_alarm_enabled"

    // Do not start this with another channel's id: NotificationSound.deleteOtherChannels
    // removes channels whose id starts with "<baseId>_".
    const val CHANNEL_ID = "unitask_high_priority_alarm_v1"

    /** The alarm stops by itself after this long if you do not react. */
    const val RING_MILLIS = 60_000L

    val VIBRATION_PATTERN = longArrayOf(0, 700, 400, 700, 400, 700)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, value).apply()
    }

    /** The phone's alarm sound, or its ringtone / notification sound if there is no alarm sound. */
    fun soundUri(): Uri =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "High priority alarms",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Rings like an alarm for reminders of high priority tasks"
            enableVibration(true)
            vibrationPattern = VIBRATION_PATTERN
            setSound(
                soundUri(),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        manager.createNotificationChannel(channel)
    }
}