package com.example.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.local.AppDatabase
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
                ReminderScheduler(appContext).rescheduleAll(AppDatabase.getDatabase(appContext))
                ClassReminderScheduler(appContext).rescheduleAll(AppDatabase.getDatabase(appContext))
                GroupReminderScheduler(appContext).rescheduleAll()
                DailySummaryScheduler(appContext).reschedule()
            } finally {
                pending.finish()
            }
        }
    }
}