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
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.local.TaskStatus
import com.example.data.local.computeDeadlineMillis
import com.example.data.local.isMyTasksCourse
import com.example.util.NotificationSound
import com.example.util.TimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/** Fires once a day at the time chosen in Settings and shows today's classes and deadlines. */
class DailySummaryReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DailySummaryReceiver"
        const val CHANNEL_ID = "unitask_daily_summary_v1"
        private const val CHANNEL_NAME = "Daily summary"
        private const val NOTIFICATION_ID = 7702
        private const val MAX_LINES = 4
    }

    /** One task due on some day, from your own tasks or from a group. */
    private data class DueItem(val deadlineMillis: Long, val label: String, val title: String)
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DailySummaryScheduler.ACTION) return

        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Tomorrow's alarm first, so a problem below can never end the series.
                DailySummaryScheduler(appContext).reschedule()
                if (DailySummaryScheduler.isEnabled(appContext)) postSummary(appContext)
            } catch (e: Exception) {
                Log.w(TAG, "Could not show the daily summary: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun postSummary(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val db = AppDatabase.getDatabase(context)
        val courses = db.courseDao().getAllCoursesList().associateBy { it.id }

        // 1 = Monday ... 7 = Sunday, same as the timetable.
        val today = (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1
        val classes = db.lectureSlotDao().getAllSlotsSync()
            .filter { !it.isDeleted && it.courseId in courses && it.dayOfWeek == today }
            .sortedBy { it.startHour * 60 + it.startMinute }

        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val startOfTomorrow = Calendar.getInstance().apply {
            timeInMillis = startOfToday
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis

        val personalTasks = db.taskDao().getAllActiveTasksSync()
            .filter { it.courseId in courses && it.status != TaskStatus.COMPLETED }
            .map {
                val course = courses[it.courseId]
                val label = if (course?.isMyTasksCourse() == true) "My task" else course?.code.orEmpty()
                DueItem(
                    computeDeadlineMillis(it.dueDateEpochMillis, it.dueTimeHour, it.dueTimeMinute),
                    label,
                    it.title
                )
            }
        // Group tasks that show on your Deadlines tab and that you have not marked done.
        val groupTasks = GroupTaskProcessor.pendingSummaryTasks(context)
            .map { DueItem(it.deadlineMillis, it.label.ifBlank { it.groupName }, it.title) }
        val pendingTasks = personalTasks + groupTasks
        val dueToday = pendingTasks
            .filter { it.deadlineMillis >= startOfToday && it.deadlineMillis < startOfTomorrow }
            .sortedBy { it.deadlineMillis }
        val overdue = pendingTasks.count { it.deadlineMillis < startOfToday }

        // Nothing planned today: stay quiet instead of sending an empty notification.
        if (classes.isEmpty() && dueToday.isEmpty()) return

        val parts = ArrayList<String>()
        if (classes.isNotEmpty()) parts.add(if (classes.size == 1) "1 class" else "${classes.size} classes")
        if (dueToday.isNotEmpty()) {
            parts.add(if (dueToday.size == 1) "1 task due today" else "${dueToday.size} tasks due today")
        }
        if (overdue > 0) parts.add("$overdue overdue")
        val summary = parts.joinToString(" \u2022 ")

        val lines = ArrayList<String>()
        for (slot in classes.take(MAX_LINES)) {
            val code = courses[slot.courseId]?.code.orEmpty()
            lines.add("${TimeFormatter.format12Hour(slot.startHour, slot.startMinute)} \u2022 $code")
        }
        if (classes.size > MAX_LINES) lines.add("+${classes.size - MAX_LINES} more classes")
        for (item in dueToday.take(MAX_LINES)) {
            val cal = Calendar.getInstance().apply { timeInMillis = item.deadlineMillis }
            val time = TimeFormatter.format12Hour(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
            lines.add("Due $time • ${item.label}: ${item.title}")
        }
        if (dueToday.size > MAX_LINES) lines.add("+${dueToday.size - MAX_LINES} more tasks")

        ensureChannel(context)

        val openApp = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NotificationSound.channelId(context, CHANNEL_ID))
            .setSmallIcon(R.drawable.ic_stat_unitask)
            .setContentTitle("Your day in UniTask")
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary + "\n" + lines.joinToString("\n")))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .apply {
                val customSound = NotificationSound.uri(context)
                if (customSound != null) setSound(customSound) else setDefaults(NotificationCompat.DEFAULT_SOUND)
            }
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val targetId = NotificationSound.channelId(context, CHANNEL_ID)
        NotificationSound.deleteOtherChannels(context, manager, CHANNEL_ID)
        if (manager.getNotificationChannel(targetId) == null) {
            val channel = NotificationChannel(targetId, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "One alert each day with your classes and deadlines"
                NotificationSound.uri(context)?.let { setSound(it, NotificationSound.audioAttributes()) }
            }
            manager.createNotificationChannel(channel)
        }
    }
}