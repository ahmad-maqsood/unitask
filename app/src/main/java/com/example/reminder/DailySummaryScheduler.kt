package com.example.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import java.util.Calendar

/**
 * One alarm per day for the "daily summary" notification. The setting (on/off and time) is kept in
 * SharedPreferences. When the alarm fires, [DailySummaryReceiver] shows the summary and sets the
 * alarm for the next day.
 */
class DailySummaryScheduler(context: Context) {

    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    companion object {
        private const val PREFS = "unitask_settings"
        private const val KEY_ENABLED = "daily_summary_enabled"
        private const val KEY_HOUR = "daily_summary_hour"
        private const val KEY_MINUTE = "daily_summary_minute"
        const val ACTION = "com.example.ACTION_DAILY_SUMMARY"
        private const val REQUEST_CODE = 7701

        private fun prefs(context: Context) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)
        fun hour(context: Context): Int = prefs(context).getInt(KEY_HOUR, 7)
        fun minute(context: Context): Int = prefs(context).getInt(KEY_MINUTE, 0)

        fun setEnabled(context: Context, value: Boolean) {
            prefs(context).edit().putBoolean(KEY_ENABLED, value).apply()
        }

        fun setTime(context: Context, hour: Int, minute: Int) {
            prefs(context).edit().putInt(KEY_HOUR, hour).putInt(KEY_MINUTE, minute).apply()
        }

        /** The next time after [afterMillis] that the clock shows [hour]:[minute]. */
        fun nextTriggerMillis(hour: Int, minute: Int, afterMillis: Long): Long {
            val cal = Calendar.getInstance().apply {
                timeInMillis = afterMillis
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (cal.timeInMillis <= afterMillis) cal.add(Calendar.DAY_OF_YEAR, 1)
            return cal.timeInMillis
        }
    }

    /** Makes the alarm match the setting: sets it when on, removes it when off. Safe to call often. */
    fun reschedule() {
        if (isEnabled(appContext)) schedule() else cancel()
    }

    private fun intent(): Intent =
        Intent(appContext, DailySummaryReceiver::class.java).apply {
            action = ACTION
            data = Uri.parse("unitask://daily-summary")
        }

    private fun schedule() {
        val am = alarmManager ?: return
        val trigger = nextTriggerMillis(hour(appContext), minute(appContext), System.currentTimeMillis())
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE,
            intent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pendingIntent)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pendingIntent)
                }
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pendingIntent)
            }
        } catch (_: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, trigger, pendingIntent)
        }
    }

    private fun cancel() {
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE,
            intent(),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager?.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}