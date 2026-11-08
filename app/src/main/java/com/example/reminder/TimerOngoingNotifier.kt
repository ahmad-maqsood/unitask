package com.example.reminder

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.util.timer.TimerLogic
import com.example.util.timer.TimerMode
import com.example.util.timer.TimerOngoingHook
import com.example.util.timer.TimerState

/**
 * Shows an ongoing notification while a stopwatch or countdown is running or paused,
 * with round Pause/Resume and Stop icon buttons for use from the lock screen or shade.
 *
 * While running, the clock in the notification is drawn by a Chronometer view, so it
 * ticks live without the app staying awake. Every state change posts a fresh notification
 * with the right start/end time.
 */
class TimerOngoingNotifier(context: Context) : TimerOngoingHook {

    private val appContext = context.applicationContext

    override fun update(state: TimerState, now: Long) {
        val finishedCountdown = state.mode == TimerMode.COUNTDOWN &&
            TimerLogic.isRunning(state) && TimerLogic.remainingMillis(state, now) <= 0L
        if (TimerLogic.isIdle(state) || finishedCountdown) {
            cancel()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        ensureChannel(nm)

        val countdown = state.mode == TimerMode.COUNTDOWN
        val running = TimerLogic.isRunning(state)
        val totalMinutes = maxOf(1L, state.countdownTotalMillis / 60_000L)

        val title = if (countdown) "Countdown" else "Stopwatch"
        val views = RemoteViews(appContext.packageName, R.layout.notification_timer)
        views.setTextViewText(R.id.timer_notif_label, title)

        if (running) {
            val whenMillis = if (countdown) {
                now + TimerLogic.remainingMillis(state, now)
            } else {
                now - TimerLogic.elapsedMillis(state, now)
            }
            // Chronometer counts against the elapsedRealtime clock, so convert the wall-clock target.
            val base = SystemClock.elapsedRealtime() + (whenMillis - now)
            views.setTextViewText(
                R.id.timer_notif_label,
                if (countdown) "$totalMinutes minute countdown" else title
            )
            views.setViewVisibility(R.id.timer_notif_chronometer, View.VISIBLE)
            views.setViewVisibility(R.id.timer_notif_paused_text, View.GONE)
            views.setChronometerCountDown(R.id.timer_notif_chronometer, countdown)
            views.setChronometer(R.id.timer_notif_chronometer, base, null, true)
        } else {
            val shown = if (countdown) {
                TimerLogic.formatClock(TimerLogic.remainingMillis(state, now), roundUp = true)
            } else {
                TimerLogic.formatClock(TimerLogic.elapsedMillis(state, now), roundUp = false)
            }
            views.setViewVisibility(R.id.timer_notif_chronometer, View.GONE)
            views.setViewVisibility(R.id.timer_notif_paused_text, View.VISIBLE)
            views.setTextViewText(R.id.timer_notif_paused_text, shown)
            views.setTextViewText(R.id.timer_notif_label, "$title \u2022 Paused")
        }

        val primaryIcon = if (running) R.drawable.ic_timer_pause else R.drawable.ic_timer_play
        val primaryTitle = appContext.getString(
            if (running) R.string.timer_notif_pause else R.string.timer_notif_resume
        )
        val primaryAction = if (running) TimerActionReceiver.ACTION_PAUSE else TimerActionReceiver.ACTION_RESUME

        views.setImageViewResource(R.id.timer_notif_primary, primaryIcon)
        views.setContentDescription(R.id.timer_notif_primary, primaryTitle)
        views.setOnClickPendingIntent(R.id.timer_notif_primary, actionIntent(primaryAction))
        views.setOnClickPendingIntent(
            R.id.timer_notif_stop,
            actionIntent(TimerActionReceiver.ACTION_STOP)
        )

        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_unitask)
            .setContentTitle(title)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setShowWhen(false)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(views)
            .setCustomBigContentView(views)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAppIntent())

        nm.notify(NOTIFICATION_ID, builder.build())
    }

    override fun cancel() {
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.cancel(NOTIFICATION_ID)
    }

    private fun ensureChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Running timer",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Shows the stopwatch or countdown while it is running."
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(channel)
            nm.deleteNotificationChannel(OLD_CHANNEL_ID)
        }
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            appContext,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun actionIntent(action: String): PendingIntent {
        val intent = Intent(appContext, TimerActionReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            appContext,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val CHANNEL_ID = "unitask_timer_running_v2"
        private const val OLD_CHANNEL_ID = "unitask_timer_running_v1"
        const val NOTIFICATION_ID = 9922
    }
}
