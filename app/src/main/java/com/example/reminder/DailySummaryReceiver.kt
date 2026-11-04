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
import com.example.data.local.startOfLocalDay
import com.example.data.local.AppDatabase
import com.example.data.local.TaskStatus
import com.example.data.local.computeDeadlineMillis
import com.example.data.local.isMyTasksCourse
import com.example.util.NotificationHistory
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
        const val GROUP_KEY_DAILY = "com.example.DAILY_SUMMARY"
        private const val PRIMARY_COLOR = 0xFF4F46E5.toInt()
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
        // Without this permission the summary still goes to the bell, only the notification is skipped.
        val canNotify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

        val db = AppDatabase.getDatabase(context)
        val courses = db.courseDao().getAllCoursesList().associateBy { it.id }

        // 1 = Monday ... 7 = Sunday, same as the timetable.
        val today = (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1
        val classes = db.lectureSlotDao().getAllSlotsSync()
            .filter {
                !it.isDeleted &&
                    it.courseId in courses &&
                    it.dayOfWeek == today &&
                    com.example.util.isSlotActive(context, it, System.currentTimeMillis())
            }
            .sortedBy { it.startHour * 60 + it.startMinute }

        val startOfToday = startOfLocalDay(System.currentTimeMillis())
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
        if (classes.isEmpty() && dueToday.isEmpty() && overdue == 0) return

        val parts = ArrayList<String>()
        if (classes.isNotEmpty()) parts.add(if (classes.size == 1) "1 class" else "${classes.size} classes")
        if (dueToday.isNotEmpty()) {
            parts.add(if (dueToday.size == 1) "1 task due today" else "${dueToday.size} tasks due today")
        }
        if (overdue > 0) {
            if (classes.isEmpty() && dueToday.isEmpty()) {
                parts.add(if (overdue == 1) "1 overdue task" else "$overdue overdue tasks")
            } else {
                parts.add("$overdue overdue")
            }
        }
        val summary = parts.joinToString(" \u2022 ")

        // Every class and task, for the bell. The notification below only has room for a few.
        val classLines = classes.map { slot ->
            val code = courses[slot.courseId]?.code.orEmpty()
            "${TimeFormatter.format12Hour(slot.startHour, slot.startMinute)} \u2022 $code"
        }
        val dueLines = dueToday.map { item ->
            val cal = Calendar.getInstance().apply { timeInMillis = item.deadlineMillis }
            val time = TimeFormatter.format12Hour(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
            "Due $time \u2022 ${item.label}: ${item.title}"
        }

        // Keep the full text for the bell, even when notifications are switched off.
        NotificationHistory.add(
            context,
            kind = "Daily summary",
            title = "Today in UniTask",
            body = (listOf(summary) + classLines + dueLines).joinToString("\n"),
            target = "tab:DEADLINES",
            key = "daily|$startOfToday"
        )

        if (!canNotify) return

        val lines = ArrayList<String>()
        lines.addAll(classLines.take(MAX_LINES))
        if (classLines.size > MAX_LINES) {
            lines.add("+${classLines.size - MAX_LINES} more classes \u2022 see the bell in UniTask")
        }
        lines.addAll(dueLines.take(MAX_LINES))
        if (dueLines.size > MAX_LINES) {
            lines.add("+${dueLines.size - MAX_LINES} more tasks \u2022 see the bell in UniTask")
        }

        ensureChannel(context)

        val openApp = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_bell", true)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val bigText = if (lines.isEmpty()) summary else (summary + "\n" + lines.joinToString("\n"))

        val notification = NotificationCompat.Builder(context, NotificationSound.channelId(context, CHANNEL_ID))
            .setSmallIcon(R.drawable.ic_stat_unitask)
            .setColor(PRIMARY_COLOR)
            .setContentTitle("Today in UniTask")
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setGroup(GROUP_KEY_DAILY)
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