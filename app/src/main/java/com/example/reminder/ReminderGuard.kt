package com.example.reminder

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.local.AppDatabase
import com.example.data.local.ReminderOption
import com.example.data.local.TaskStatus
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/**
 * The safety net under the reminder alarms.
 *
 * Alarms can disappear while the phone stays on: a battery cleaner, "swipe away from recents" on some
 * brands, or a force stop. Before this guard, nothing noticed until the phone restarted or the app was
 * opened. Now [run] checks about every 15 minutes (and when "Check reminders" opens):
 *
 * 1. A reminder that should have rung more than 5 minutes ago and never did is shown now.
 * 2. Every upcoming reminder alarm that is missing is set again.
 * 3. Snoozes, class alarms, group reminders and the daily summary are set again.
 *
 * Step 1 must come before step 2, because setting an alarm again clears its "waiting" record.
 */
object ReminderGuard {
    const val WORK_NAME = "unitask_reminder_guard"
    private const val TAG = "ReminderGuard"

    /** Safe to call often. Each step has its own try/catch, so one failure never skips the others. */
    suspend fun run(context: Context, database: AppDatabase) {
        val app = context.applicationContext
        val scheduler = ReminderScheduler(app)
        val now = System.currentTimeMillis()

        step("late reminders") { deliverLate(app, database, scheduler, now) }
        step("forget old reminders") { MissedReminders.pruneExpired(app, now) }
        step("task alarms") { scheduler.rearmMissing(database) }
        step("snoozed reminders") { SnoozeStore.restoreAll(app) }
        step("class alarms") { ClassReminderScheduler(app).rescheduleAll(database) }
        step("group alarms") { GroupReminderScheduler(app).rescheduleAll() }
        step("daily summary") { DailySummaryScheduler(app).reschedule() }
    }

    private suspend fun deliverLate(
        context: Context,
        database: AppDatabase,
        scheduler: ReminderScheduler,
        now: Long
    ) {
        for (late in MissedReminders.waitingLate(context, now)) {
            // Forget it first: if anything below fails, the same reminder must not be sent again and again.
            MissedReminders.markHandled(context, late.taskId, late.optionName, late.triggerAt)

            val option = ReminderOption.entries.firstOrNull { it.name == late.optionName } ?: continue
            val task = database.taskDao().getTaskById(late.taskId) ?: continue
            if (task.status == TaskStatus.COMPLETED) continue
            val courseCode = database.courseDao().getCourseByIdSync(task.courseId)?.code.orEmpty()
            scheduler.deliverLate(task, courseCode, option, late.triggerAt)
        }
    }

    private suspend fun step(name: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "$name failed: ${e.message}")
        }
    }
}

/** Runs [ReminderGuard] every 15 minutes (the shortest time WorkManager allows), with no network needed. */
class ReminderGuardWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        try {
            ReminderGuard.run(applicationContext, AppDatabase.getDatabase(applicationContext))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // No retry: the next 15 minute run is the retry.
            Log.w("ReminderGuardWorker", "Guard run failed: ${e.message}")
        }
        return Result.success()
    }

    companion object {
        /** Safe to call every time the app starts: an existing schedule is kept. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderGuardWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                ReminderGuard.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
