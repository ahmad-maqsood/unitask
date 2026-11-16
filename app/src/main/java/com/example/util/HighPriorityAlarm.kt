package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import com.example.R

/**
 * The "Ring alarm" switch on a task is saved as HIGH priority (see TaskItem.ringsAlarm). Reminders of
 * those tasks ring like an alarm (alarm sound and volume, repeating, vibrating) instead of the
 * normal notification sound. This object holds the alarm sound, vibration and notification channels.
 */
object HighPriorityAlarm {
    // Do not start this with another channel's id: NotificationSound.deleteOtherChannels
    // removes channels whose id starts with "<baseId>_".
    const val CHANNEL_ID = "unitask_high_priority_alarm_v1"
    const val CHANNEL_ID_SILENCED = "unitask_high_priority_alarm_silenced_v1"

    /** The alarm stops by itself after this long if you do not react. */
    const val RING_MILLIS = 60_000L

    val VIBRATION_PATTERN = longArrayOf(0, 700, 400, 700, 400, 700)

    /** The phone's alarm sound, or its ringtone / notification sound if there is no alarm sound. */
    fun soundUri(): Uri =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.high_priority_alarm_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.high_priority_alarm_channel_description)
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
        if (manager.getNotificationChannel(CHANNEL_ID_SILENCED) == null) {
            val silencedChannel = NotificationChannel(
                CHANNEL_ID_SILENCED,
                context.getString(R.string.high_priority_alarm_silenced_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.high_priority_alarm_silenced_channel_description)
                enableVibration(false)
                setSound(null, null)
            }
            manager.createNotificationChannel(silencedChannel)
        }
    }
}