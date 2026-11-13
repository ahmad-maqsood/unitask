package com.example.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.util.timer.AutoStartAlarmHook

/**
 * The system alarm that starts the next Pomodoro round a few seconds after one ends.
 * It goes to [TimerActionReceiver], so the next round starts even when the app is closed.
 */
class AutoStartAlarm(private val context: Context) : AutoStartAlarmHook {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, TimerActionReceiver::class.java).apply {
            action = TimerActionReceiver.ACTION_AUTO_START_NEXT
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun schedule(triggerAtMillis: Long) {
        val pi = pendingIntent()
        alarmManager?.let { am ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (am.canScheduleExactAlarms()) {
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
                    } else {
                        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
                    }
                } else {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
                }
            } catch (_: SecurityException) {
                // Fall back to a standard inexact alarm.
                am.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
        }
    }

    override fun cancel() {
        val pi = pendingIntent()
        alarmManager?.cancel(pi)
        pi.cancel()
    }

    companion object {
        private const val REQUEST_CODE = 9926
    }
}
