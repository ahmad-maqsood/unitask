package com.example.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.util.timer.TimerController

/**
 * Handles the Pause, Resume and Stop buttons on the running-timer notification.
 * The controller updates the saved state, the alarm and the notification itself.
 */
class TimerActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val controller = TimerController.get(context)
        when (intent.action) {
            ACTION_PAUSE -> controller.pause()
            ACTION_RESUME -> controller.start()
            ACTION_STOP -> controller.reset()
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.example.timer.action.PAUSE"
        const val ACTION_RESUME = "com.example.timer.action.RESUME"
        const val ACTION_STOP = "com.example.timer.action.STOP"
    }
}
