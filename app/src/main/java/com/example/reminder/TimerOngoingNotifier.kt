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
import com.example.util.timer.TimerLogic
import com.example.util.timer.TimerMode
import com.example.util.timer.TimerOngoingHook
import com.example.util.timer.TimerState

/**
 * Shows a silent, ongoing notification while a stopwatch or countdown is running or paused,
 * with Pause/Resume and Stop buttons.
 *
 * While running, the clock in the notification is drawn by the system chronometer, so it
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

        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_unitask)
            .setContentTitle(if (countdown) "Countdown" else "Stopwatch")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAppIntent())

        if (running) {
            val whenMillis = if (countdown) {
                now + TimerLogic.remainingMillis(state, now)
            } else {
                now - TimerLogic.elapsedMillis(state, now)
            }
            builder.setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(countdown)
                .setWhen(whenMillis)
                .setContentText(if (countdown) "$totalMinutes minute countdown" else "Running")
            builder.addAction(0, "Pause", actionIntent(TimerActionReceiver.ACTION_PAUSE))
        } else {
            val shown = if (countdown) {
                TimerLogic.formatClock(TimerLogic.remainingMillis(state, now), roundUp = true)
            } else {
                TimerLogic.formatClock(TimerLogic.elapsedMillis(state, now), roundUp = false)
            }
            builder.setShowWhen(false)
                .setUsesChronometer(false)
                .setContentText("Paused at $shown")
            builder.addAction(0, "Resume", actionIntent(TimerActionReceiver.ACTION_RESUME))
        }
        builder.addAction(0, "Stop", actionIntent(TimerActionReceiver.ACTION_STOP))

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
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows the stopwatch or countdown while it is running."
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
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
        const val CHANNEL_ID = "unitask_timer_running_v1"
        const val NOTIFICATION_ID = 9922
    }
}
