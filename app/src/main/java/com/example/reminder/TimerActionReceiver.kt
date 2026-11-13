package com.example.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.util.timer.PomodoroPhase
import com.example.util.timer.TimerController

/**
 * Handles action buttons from the timer and countdown notifications.
 */
class TimerActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PAUSE -> TimerController.get(context).pause()
            ACTION_RESUME -> TimerController.get(context).start()
            ACTION_STOP -> TimerController.get(context).reset()
            ACTION_STOP_COUNTDOWN -> {
                CountdownNotifier.cancelFinished(context)
                // Stop also means "do not start the next Pomodoro round for me".
                TimerController.get(context).cancelAutoStart()
            }
            // Alarms can arrive a moment early; tolerance lets the round start cleanly.
            ACTION_AUTO_START_NEXT -> TimerController.get(context).autoStartIfDue(toleranceMillis = 2000L)
            ACTION_SILENCE_COUNTDOWN -> {
                // The user may already have opened or dismissed it during the 60 s of ringing.
                if (!CountdownNotifier.isFinishedNotificationShowing(context)) return
                val totalMillis = intent.getLongExtra(CountdownNotifier.EXTRA_TOTAL_MILLIS, 0L)
                val phase = intent.getStringExtra(CountdownNotifier.EXTRA_POMODORO_PHASE)?.let {
                    try {
                        PomodoroPhase.valueOf(it)
                    } catch (_: IllegalArgumentException) {
                        null
                    }
                }
                CountdownNotifier.notifyFinished(context, totalMillis, ring = false, phase = phase)
            }
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.example.timer.action.PAUSE"
        const val ACTION_RESUME = "com.example.timer.action.RESUME"
        const val ACTION_STOP = "com.example.timer.action.STOP"
        const val ACTION_STOP_COUNTDOWN = "com.example.timer.action.STOP_COUNTDOWN"
        const val ACTION_SILENCE_COUNTDOWN = "com.example.timer.action.SILENCE_COUNTDOWN"
        const val ACTION_AUTO_START_NEXT = "com.example.timer.action.AUTO_START_NEXT"
    }
}
