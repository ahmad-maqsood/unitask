package com.example.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.util.timer.TimerController
import com.example.util.timer.TimerLogic
import com.example.util.timer.TimerMode

/**
 * BroadcastReceiver triggered by AlarmManager when a countdown duration expires.
 */
class CountdownReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val controller = TimerController.get(context)
        val state = controller.state.value

        // Ignore the alarm unless the state is a running COUNTDOWN.
        if (state.mode != TimerMode.COUNTDOWN || !TimerLogic.isRunning(state)) {
            return
        }

        val ring = !TimerLogic.isAlarmLate(state, controller.now())
        // Alarms can arrive a moment early; tolerance allows ending cleanly.
        val finished = controller.markFinishedIfDue(toleranceMillis = 2000L)
        if (finished) {
            CountdownNotifier.notifyFinished(context, state.countdownTotalMillis, ring = ring)
        }
    }
}
