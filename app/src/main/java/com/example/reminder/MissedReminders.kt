package com.example.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.local.ReminderOption
import com.example.data.local.TaskStatus
import com.example.data.local.deadlineMillis
import com.example.util.NotificationHistory
import com.example.util.DateFormats

/** One reminder alarm that was set and is waiting to ring. */
data class ArmedReminder(val taskId: Long, val optionName: String, val triggerAt: Long)

/**
 * Keeps a small list of reminder alarms that are waiting to ring. A reminder leaves the list when
 * it rings, or when its task's alarms are cancelled or set again. After the phone restarts, anything
 * still on the list whose time has passed never rang (the phone was off), so we tell the user.
 */
object MissedReminders {
    private const val PREFS = "missed_reminders"
    private const val KEY_ARMED = "armed"
    private const val CHANNEL_ID = "unitask_missed_reminders"
    private const val NOTIFICATION_ID = 9911

    /** Only reminders from the last 12 hours are announced. Older ones are no longer useful. */
    const val WINDOW_MILLIS = 12 * 60 * 60 * 1000L

    private val lock = Any()

    fun keyFor(taskId: Long, optionName: String, triggerAt: Long): String =
        "$taskId|$optionName|$triggerAt"

    private fun parseKey(key: String): ArmedReminder? {
        val parts = key.split("|")
        if (parts.size != 3) return null
        val taskId = parts[0].toLongOrNull() ?: return null
        val triggerAt = parts[2].toLongOrNull() ?: return null
        return ArmedReminder(taskId, parts[1], triggerAt)
    }

    /** Which waiting reminders were due in the last [windowMillis] but never rang. Oldest first. */
    fun pickMissed(keys: Set<String>, now: Long, windowMillis: Long = WINDOW_MILLIS): List<ArmedReminder> =
        keys.mapNotNull { parseKey(it) }
            .filter { it.triggerAt <= now && it.triggerAt > now - windowMillis }
            .sortedBy { it.triggerAt }

    /**
     * While the phone is on, a waiting reminder is only treated as lost once it is this late.
     * Battery saving can delay a normal alarm by a few minutes, and the real alarm must get its chance.
     */
    const val LATE_GRACE_MILLIS = 5 * 60 * 1000L

    /**
     * Which waiting reminders are overdue by more than [graceMillis] but still inside the window.
     * These alarms never rang although the phone was on (an app cleaner removed them). Oldest first.
     */
    fun pickLate(
        keys: Set<String>,
        now: Long,
        graceMillis: Long = LATE_GRACE_MILLIS,
        windowMillis: Long = WINDOW_MILLIS
    ): List<ArmedReminder> =
        keys.mapNotNull { parseKey(it) }
            .filter { it.triggerAt <= now - graceMillis && it.triggerAt > now - windowMillis }
            .sortedBy { it.triggerAt }

    /** The waiting reminders that are overdue right now. See [pickLate]. */
    fun waitingLate(context: Context, now: Long = System.currentTimeMillis()): List<ArmedReminder> =
        synchronized(lock) { pickLate(load(context), now) }

    /** Forgets reminders older than the window. They can never be announced, so the list must not keep them. */
    fun pruneExpired(context: Context, now: Long = System.currentTimeMillis()) {
        try {
            synchronized(lock) {
                val keys = load(context)
                val kept = keys.filterNot { (parseKey(it)?.triggerAt ?: 0L) <= now - WINDOW_MILLIS }.toSet()
                if (kept.size != keys.size) save(context, kept)
            }
        } catch (_: Exception) {
        }
    }

    private fun load(context: Context): MutableSet<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_ARMED, emptySet())
            .orEmpty()
            .toMutableSet()

    private fun save(context: Context, keys: Set<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_ARMED, keys).apply()
    }

    fun recordArmed(context: Context, taskId: Long, option: ReminderOption, triggerAt: Long) {
        try {
            synchronized(lock) {
                val keys = load(context)
                keys.add(keyFor(taskId, option.name, triggerAt))
                save(context, keys)
            }
        } catch (_: Exception) {
        }
    }

    /** The alarm rang (or was skipped on purpose): it is not waiting any more. */
    fun markHandled(context: Context, taskId: Long, optionName: String?, triggerAt: Long) {
        if (taskId <= 0L || optionName.isNullOrBlank() || triggerAt <= 0L) return
        try {
            synchronized(lock) {
                val keys = load(context)
                if (keys.remove(keyFor(taskId, optionName, triggerAt))) save(context, keys)
            }
        } catch (_: Exception) {
        }
    }

    /** All alarms of this task were cancelled (edited, done or deleted). */
    fun forgetTask(context: Context, taskId: Long) {
        try {
            synchronized(lock) {
                val keys = load(context)
                if (keys.removeAll { it.startsWith("$taskId|") }) save(context, keys)
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Call after the phone started, BEFORE the alarms are set again (setting them again clears the
     * list). Shows one notification for reminders that should have rung while the phone was off.
     */
    suspend fun notifyMissed(context: Context, database: AppDatabase) {
        val now = System.currentTimeMillis()
        val missed = synchronized(lock) {
            val keys = load(context)
            val due = pickMissed(keys, now)
            // Forget everything already in the past, so each reminder is announced only once.
            save(context, keys.filterNot { (parseKey(it)?.triggerAt ?: 0L) <= now }.toSet())
            due
        }
        if (missed.isEmpty()) return

        val lines = mutableListOf<String>()
        for (taskId in missed.map { it.taskId }.distinct()) {
            val task = database.taskDao().getTaskById(taskId) ?: continue
            if (task.status == TaskStatus.COMPLETED) continue
            lines += "${task.title}: due ${DateFormats.shortDateAtClock(task.deadlineMillis)}"
        }
        if (lines.isEmpty()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Missed reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Tells you about reminders that could not ring because the phone was off."
                }
            )
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("opened_task_id", -1L)
        }
        val openPending = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (lines.size == 1) "Missed reminder" else "${lines.size} missed reminders"
        val inbox = NotificationCompat.InboxStyle().setSummaryText("Your phone was off or restarted")
        lines.forEach { inbox.addLine(it) }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_unitask)
            .setContentTitle(title)
            .setContentText(lines.first())
            .setStyle(inbox)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .build()
        nm.notify(NOTIFICATION_ID, notification)

        NotificationHistory.add(
            context,
            kind = "Missed reminder",
            title = title,
            body = lines.joinToString("\n"),
            target = "tab:DEADLINES",
            key = "missed|$now"
        )
    }
}
