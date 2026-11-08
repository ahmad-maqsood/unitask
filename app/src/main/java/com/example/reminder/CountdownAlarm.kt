package com.example.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.util.timer.CountdownAlarmHook

/**
 * Manages the single system alarm scheduled for an active countdown.
 */
class CountdownAlarm(private val context: Context) : CountdownAlarmHook {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, CountdownReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun schedule(endAtMillis: Long) {
        CountdownNotifier.cancelFinished(context)
        val pi = pendingIntent()
        alarmManager?.let { am ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (am.canScheduleExactAlarms()) {
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAtMillis, pi)
                    } else {
                        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAtMillis, pi)
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAtMillis, pi)
                } else {
                    am.setExact(AlarmManager.RTC_WAKEUP, endAtMillis, pi)
                }
            } catch (_: SecurityException) {
                // Fallback to standard inexact alarm
                am.set(AlarmManager.RTC_WAKEUP, endAtMillis, pi)
            }
        }
    }

    override fun cancel() {
        val pi = pendingIntent()
        alarmManager?.cancel(pi)
        pi.cancel()
        CountdownNotifier.cancelFinished(context)
    }

    companion object {
        private const val REQUEST_CODE = 9921
    }
}
