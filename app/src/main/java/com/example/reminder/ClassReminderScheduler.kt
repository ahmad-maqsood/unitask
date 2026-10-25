package com.example.reminder

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.example.data.local.AppDatabase
import com.example.data.local.LectureSlot
import java.util.Calendar

/**
 * "Class started" alarms. There is one alarm per timetable slot and it is always set for the NEXT
 * start of that slot. When it fires, [ClassStartReceiver] shows the notification and sets the alarm
 * for the following week, so the series never runs out.
 *
 * Call [rescheduleAll] after anything that can change the timetable (slot or course edited,
 * deleted, restored, synced, phone restarted). It is safe to call as often as you like.
 */
class ClassReminderScheduler(context: Context) {

    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS = "unitask_settings"
        private const val KEY_ENABLED = "class_start_notifications_enabled"
        private const val KEY_SCHEDULED_IDS = "class_start_scheduled_slot_ids"
        const val ACTION_CLASS_START = "com.example.ACTION_CLASS_START"
        const val ACTION_CLASS_SOON = "com.example.ACTION_CLASS_SOON"

        /** How many minutes before a class the early alert comes. Change 10 to any number you like. */
        const val LEAD_MINUTES = 10
        const val LEAD_MILLIS = LEAD_MINUTES * 60_000L
        private val LOCK = Any()

        fun isEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

        fun setEnabled(context: Context, value: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ENABLED, value)
                .apply()
        }

        fun notificationIdFor(slotId: Long): Int = ("class|" + slotId).hashCode()

        fun soonNotificationIdFor(slotId: Long): Int = ("class_soon|" + slotId).hashCode()

        private fun requestCodeFor(slotId: Long): Int = ("class_alarm|" + slotId).hashCode()

        private fun soonRequestCodeFor(slotId: Long): Int = ("class_soon_alarm|" + slotId).hashCode()

        /** The next time after [afterMillis] that this weekly slot starts (1 = Monday ... 7 = Sunday). */
        fun nextStartMillis(slot: LectureSlot, afterMillis: Long): Long {
            val wantedDay = if (slot.dayOfWeek == 7) Calendar.SUNDAY else slot.dayOfWeek + 1
            val cal = Calendar.getInstance().apply {
                timeInMillis = afterMillis
                set(Calendar.HOUR_OF_DAY, slot.startHour)
                set(Calendar.MINUTE, slot.startMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val daysAhead = (wantedDay - cal.get(Calendar.DAY_OF_WEEK) + 7) % 7
            cal.add(Calendar.DAY_OF_YEAR, daysAhead)
            if (cal.timeInMillis <= afterMillis) cal.add(Calendar.DAY_OF_YEAR, 7)
            return cal.timeInMillis
        }
    }

    /** Makes the alarms match the current timetable (or removes them all when the setting is off). */
    suspend fun rescheduleAll(database: AppDatabase) {
        val activeCourseIds = database.courseDao().getAllCoursesList().map { it.id }.toSet()
        val wanted = database.lectureSlotDao().getAllSlotsSync()
            .filter { !it.isDeleted && it.courseId in activeCourseIds }
        val enabled = isEnabled(appContext)

        synchronized(LOCK) {
            val previous = prefs.getStringSet(KEY_SCHEDULED_IDS, emptySet())
                .orEmpty()
                .mapNotNull { it.toLongOrNull() }
                .toSet()
            val keep: Set<Long> = if (enabled) wanted.map { it.id }.toSet() else emptySet()

            for (id in previous) {
                if (id !in keep) cancelSlot(id)
            }
            if (enabled) {
                for (slot in wanted) scheduleSlot(slot)
            }
            prefs.edit()
                .putStringSet(KEY_SCHEDULED_IDS, keep.map { it.toString() }.toSet())
                .apply()
        }
    }

    /** Sets both alarms (10 minutes before, and at the start) for the next time [slot] happens. */
    fun scheduleSlot(slot: LectureSlot, afterMillis: Long = System.currentTimeMillis()) {
        scheduleSoonAlarm(slot, afterMillis)
        scheduleStartAlarm(slot, afterMillis)
    }

    /** Sets only the "class started" alarm. */
    fun scheduleStartAlarm(slot: LectureSlot, afterMillis: Long = System.currentTimeMillis()) {
        val trigger = nextStartMillis(slot, afterMillis)
        setAlarm(trigger, requestCodeFor(slot.id), intentFor(slot.id, trigger))
    }

    /** Sets only the "class starts in 10 minutes" alarm. */
    fun scheduleSoonAlarm(slot: LectureSlot, afterMillis: Long = System.currentTimeMillis()) {
        val classStart = nextStartMillis(slot, afterMillis + LEAD_MILLIS)
        setAlarm(
            classStart - LEAD_MILLIS,
            soonRequestCodeFor(slot.id),
            soonIntentFor(slot.id, classStart)
        )
    }

    private fun setAlarm(trigger: Long, requestCode: Int, intent: Intent) {
        val am = alarmManager ?: return
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            requestCode,
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

    /** Removes both alarms of one slot and any notification they already posted. */
    fun cancelSlot(slotId: Long) {
        cancelAlarm(requestCodeFor(slotId), intentFor(slotId, 0L))
        cancelAlarm(soonRequestCodeFor(slotId), soonIntentFor(slotId, 0L))
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.cancel(notificationIdFor(slotId))
        manager?.cancel(soonNotificationIdFor(slotId))
    }

    private fun cancelAlarm(requestCode: Int, intent: Intent) {
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager?.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    /** Alarms are matched on action, data and request code; the extras may differ between calls. */
    private fun intentFor(slotId: Long, startMillis: Long): Intent =
        Intent(appContext, ClassStartReceiver::class.java).apply {
            action = ACTION_CLASS_START
            data = Uri.parse("unitask://class-start/$slotId")
            putExtra(ClassStartReceiver.EXTRA_SLOT_ID, slotId)
            putExtra(ClassStartReceiver.EXTRA_START_MILLIS, startMillis)
        }

    /** Same idea for the "10 minutes before" alarm. [startMillis] is when the class itself starts. */
    private fun soonIntentFor(slotId: Long, startMillis: Long): Intent =
        Intent(appContext, ClassStartReceiver::class.java).apply {
            action = ACTION_CLASS_SOON
            data = Uri.parse("unitask://class-soon/$slotId")
            putExtra(ClassStartReceiver.EXTRA_SLOT_ID, slotId)
            putExtra(ClassStartReceiver.EXTRA_START_MILLIS, startMillis)
        }
}