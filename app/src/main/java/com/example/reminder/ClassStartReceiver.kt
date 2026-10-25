package com.example.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.data.local.AppDatabase
import com.example.data.local.Course
import com.example.data.local.LectureSlot
import com.example.util.NotificationSound
import com.example.util.TimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Fires when a class starts and shows "Class in progress". The notification counts down to the end
 * of the class and removes itself when the class is over. It also sets the alarm for next week.
 */
class ClassStartReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ClassStartReceiver"
        const val CHANNEL_ID = "unitask_class_start_v1"
        private const val CHANNEL_NAME = "Class started"
        const val EXTRA_SLOT_ID = "extra_class_slot_id"
        const val EXTRA_START_MILLIS = "extra_class_start_millis"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ClassReminderScheduler.ACTION_CLASS_START) return
        val slotId = intent.getLongExtra(EXTRA_SLOT_ID, -1L)
        if (slotId <= 0L) return
        val startMillis = intent.getLongExtra(EXTRA_START_MILLIS, 0L)

        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handle(appContext, slotId, startMillis)
            } catch (e: Exception) {
                Log.w(TAG, "Could not handle class start: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, slotId: Long, startMillis: Long) {
        val database = AppDatabase.getDatabase(context)
        val scheduler = ClassReminderScheduler(context)

        val slot = database.lectureSlotDao().getSlotById(slotId)
        val course = if (slot != null && !slot.isDeleted) {
            database.courseDao().getCourseByIdSync(slot.courseId)
        } else {
            null
        }
        if (slot == null || course == null) {
            // The class was deleted after this alarm was set.
            scheduler.cancelSlot(slotId)
            return
        }
        if (!ClassReminderScheduler.isEnabled(context)) return

        // Set next week's alarm first, so a problem below can never end the series.
        val now = System.currentTimeMillis()
        scheduler.scheduleSlot(slot, maxOf(now, startMillis) + 1_000L)

        val endMillis = classEndMillis(slot, startMillis)
        if (now >= endMillis) return // the phone was off or asleep and the class is already over

        postNotification(context, slot, course, endMillis, now)
    }

    private fun classEndMillis(slot: LectureSlot, startMillis: Long): Long {
        val base = if (startMillis > 0L) startMillis else System.currentTimeMillis()
        return Calendar.getInstance().apply {
            timeInMillis = base
            set(Calendar.HOUR_OF_DAY, slot.endHour)
            set(Calendar.MINUTE, slot.endMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun postNotification(
        context: Context,
        slot: LectureSlot,
        course: Course,
        endMillis: Long,
        now: Long
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        ensureChannel(context)

        val notificationId = ClassReminderScheduler.notificationIdFor(slot.id)
        val openApp = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val room = slot.classroom.ifBlank { course.location }
        val teacher = slot.instructor.ifBlank { course.instructor }
        val timeRange = TimeFormatter.formatTimeRange(
            slot.startHour, slot.startMinute, slot.endHour, slot.endMinute
        )
        val details = buildList {
            add(timeRange)
            if (room.isNotBlank()) add("Room: $room")
            if (teacher.isNotBlank()) add(teacher)
        }.joinToString(" \u2022 ")

        val notification = NotificationCompat.Builder(context, NotificationSound.channelId(context, CHANNEL_ID))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Class in progress: ${course.code}")
            .setContentText("${course.name} \u2022 $timeRange")
            .setStyle(NotificationCompat.BigTextStyle().bigText("${course.name}\n$details"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            // Shows a countdown to the end of the class and removes itself when the class is over.
            .setWhen(endMillis)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setTimeoutAfter(endMillis - now)
            .apply {
                val customSound = NotificationSound.uri(context)
                if (customSound != null) setSound(customSound) else setDefaults(NotificationCompat.DEFAULT_SOUND)
            }
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(notificationId, notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val targetId = NotificationSound.channelId(context, CHANNEL_ID)
        manager.deleteNotificationChannel(NotificationSound.otherChannelId(context, CHANNEL_ID))
        if (manager.getNotificationChannel(targetId) == null) {
            val channel = NotificationChannel(targetId, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Tells you when one of your classes starts"
                enableVibration(true)
                NotificationSound.uri(context)?.let { setSound(it, NotificationSound.audioAttributes()) }
            }
            manager.createNotificationChannel(channel)
        }
    }
}