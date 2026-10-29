package com.example.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.repository.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Wakes up just after midnight every day and moves repeating tasks that were ticked Done on an
 * earlier day to their next date (back to Pending). The alarm sets itself again each time it
 * fires. [rolloverAndReschedule] is also called when the app opens and after a reboot or a
 * clock change, in case the alarm was missed.
 */
class RepeatRolloverReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "RepeatRollover"
        const val ACTION = "com.example.ACTION_REPEAT_ROLLOVER"
        private const val REQUEST_CODE = 7703

        /** A few seconds after the next midnight that follows [afterMillis]. */
        fun nextTriggerMillis(afterMillis: Long): Long =
            Calendar.getInstance().apply {
                timeInMillis = afterMillis
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 5)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

        /** Sets the next midnight alarm. Safe to call often: it replaces the previous one. */
        fun schedule(context: Context) {
            val appContext = context.applicationContext
            val am = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val trigger = nextTriggerMillis(System.currentTimeMillis())
            val intent = Intent(appContext, RepeatRolloverReceiver::class.java).apply {
                action = ACTION
                data = Uri.parse("unitask://repeat-rollover")
            }
            val pendingIntent = PendingIntent.getBroadcast(
                appContext,
                REQUEST_CODE,
                intent,
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

        /** Sets tomorrow's alarm, then moves any repeating task whose "Done" day is over. */
        suspend fun rolloverAndReschedule(context: Context) {
            val appContext = context.applicationContext
            // Tomorrow's alarm first, so a problem below can never end the series.
            schedule(appContext)

            val db = AppDatabase.getDatabase(appContext)
            val scheduler = ReminderScheduler(appContext)
            val repository = AppRepository(
                context = appContext,
                courseDao = db.courseDao(),
                taskDao = db.taskDao(),
                taskAttachmentDao = db.taskAttachmentDao(),
                lectureSlotDao = db.lectureSlotDao(),
                reminderScheduler = scheduler
            )
            if (repository.rolloverCompletedRepeatingTasks()) {
                // Leaves every reminder in a consistent state after the tasks moved.
                scheduler.rescheduleAll(db)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return

        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                rolloverAndReschedule(appContext)
            } catch (e: Exception) {
                Log.w(TAG, "Could not roll over repeating tasks: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }
}