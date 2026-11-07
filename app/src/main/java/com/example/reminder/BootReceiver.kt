package com.example.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.local.AppDatabase
import com.example.util.timer.TimerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Alarms are wiped by a reboot or an app update; this puts every unfinished reminder back. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != Intent.ACTION_TIMEZONE_CHANGED &&
            action != Intent.ACTION_TIME_CHANGED
        ) return

        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Each step on its own: one failing step (for example a group lookup with no
                // internet) must not crash the app or leave the other alarms missing.
                suspend fun step(name: String, block: suspend () -> Unit) {
                    try {
                        block()
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        android.util.Log.w("BootReceiver", "$name failed: ${e.message}")
                    }
                }
                val db = AppDatabase.getDatabase(appContext)
                // Reminders that should have rung while the phone was off. This must run BEFORE the
                // alarms are set again, because setting them again clears the waiting list.
                if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
                    step("missed reminders") { MissedReminders.notifyMissed(appContext, db) }
                }
                step("task reminders") { ReminderScheduler(appContext).rescheduleAll(db) }
                step("class reminders") { ClassReminderScheduler(appContext).rescheduleAll(db) }
                step("group reminders") { GroupReminderScheduler(appContext).rescheduleAll() }
                step("snoozed reminders") { SnoozeStore.restoreAll(appContext) }
                step("daily summary") { DailySummaryScheduler(appContext).reschedule() }
                step("repeat rollover") { RepeatRolloverReceiver.rolloverAndReschedule(appContext) }
                step("countdown timer") {
                    val controller = TimerController.get(appContext)
                    val endedWhileOff = controller.restoreAfterReboot()
                    if (endedWhileOff) {
                        CountdownNotifier.notifyFinished(appContext, controller.state.value.countdownTotalMillis)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
