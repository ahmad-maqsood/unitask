package com.example.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
            ACTION_STOP_COUNTDOWN -> CountdownNotifier.cancelFinished(context)
            ACTION_SILENCE_COUNTDOWN -> {
                val totalMillis = intent.getLongExtra(CountdownNotifier.EXTRA_TOTAL_MILLIS, 0L)
                CountdownNotifier.notifyFinished(context, totalMillis, ring = false)
            }
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.example.timer.action.PAUSE"
        const val ACTION_RESUME = "com.example.timer.action.RESUME"
        const val ACTION_STOP = "com.example.timer.action.STOP"
        const val ACTION_STOP_COUNTDOWN = "com.example.timer.action.STOP_COUNTDOWN"
        const val ACTION_SILENCE_COUNTDOWN = "com.example.timer.action.SILENCE_COUNTDOWN"
    }
}
