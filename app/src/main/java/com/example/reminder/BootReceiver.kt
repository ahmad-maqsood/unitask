package com.example.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.local.AppDatabase
import com.example.util.timer.TimerController
import com.example.util.timer.TimerLogic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Alarms are wiped by a reboot or an app update; this puts every unfinished reminder back. */
class BootReceiver : BroadcastReceiver() {

    companion object {
        /** Sent instead of (or as well as) BOOT_COMPLETED by Xiaomi, Poco and HTC phones on a fast restart. */
        const val ACTION_QUICKBOOT = "android.intent.action.QUICKBOOT_POWERON"
        const val ACTION_HTC_QUICKBOOT = "com.htc.intent.action.QUICKBOOT_POWERON"

        /** Sent when the person allows "Alarms & reminders" for the app. Alarms set before are not exact. */
        const val ACTION_EXACT_ALARM_ALLOWED = "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"

        /** The phone just started or the app was just updated. Reminders that never rang are announced. */
        fun isStartAction(action: String?): Boolean =
            action == Intent.ACTION_BOOT_COMPLETED ||
                action == Intent.ACTION_MY_PACKAGE_REPLACED ||
                action == ACTION_QUICKBOOT ||
                action == ACTION_HTC_QUICKBOOT

        /** Every broadcast that makes the alarms be set again. */
        fun handles(action: String?): Boolean =
            isStartAction(action) ||
                action == Intent.ACTION_TIMEZONE_CHANGED ||
                action == Intent.ACTION_TIME_CHANGED ||
                action == ACTION_EXACT_ALARM_ALLOWED
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (!handles(action)) return

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
                if (isStartAction(action)) {
                    step("missed reminders") { MissedReminders.notifyMissed(appContext, db) }
                }
                step("task reminders") { ReminderScheduler(appContext).rescheduleAll(db) }
                step("class reminders") { ClassReminderScheduler(appContext).rescheduleAll(db) }
                step("group reminders") { GroupReminderScheduler(appContext).rescheduleAll() }
                step("snoozed reminders") { SnoozeStore.restoreAll(appContext) }
                step("daily summary") { DailySummaryScheduler(appContext).reschedule() }
                step("repeat rollover") { RepeatRolloverReceiver.rolloverAndReschedule(appContext) }
                // Also starts the 15 minute safety net right after an update, before the app is opened.
                step("reminder guard") { ReminderGuardWorker.schedule(appContext) }
                step("countdown timer") {
                    val controller = TimerController.get(appContext)
                    val stateBefore = controller.state.value
                    val ring = !TimerLogic.isAlarmLate(stateBefore, controller.now())
                    val endedWhileOff = controller.restoreAfterReboot()
                    if (endedWhileOff) {
                        CountdownNotifier.notifyFinished(
                            appContext,
                            controller.state.value.countdownTotalMillis,
                            ring = ring
                        )
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
