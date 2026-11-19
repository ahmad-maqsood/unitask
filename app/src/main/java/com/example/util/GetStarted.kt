package com.example.util

import android.content.Context
import androidx.annotation.StringRes
import com.example.R
import androidx.core.content.edit

/** The steps of the "Get started" checklist on the Deadlines screen, in the order they are shown. */
enum class GetStartedStep(
    val title: String,
    val hint: String,
    @StringRes val titleRes: Int,
    @StringRes val hintRes: Int,
    val isSystem: Boolean = false
) {
    // Phone settings. These are read live from the phone, never saved as ticked, so a step
    // un-ticks again if the person turns the setting back off.
    NOTIFICATIONS("Allow notifications", "So reminders and group alerts can reach you", R.string.get_started_step_notifications_title, R.string.get_started_step_notifications_hint, isSystem = true),
    BATTERY("Turn off battery optimization", "Lets reminders ring on time in the background", R.string.get_started_step_battery_title, R.string.get_started_step_battery_hint, isSystem = true),

    COURSE("Add a course", "Courses tab, then tap +", R.string.get_started_step_course_title, R.string.get_started_step_course_hint),
    DEADLINE("Add a deadline", "Deadlines tab, then tap +", R.string.get_started_step_deadline_title, R.string.get_started_step_deadline_hint),
    REMINDER("Set a reminder", "Use the Remind me row when you add or edit a deadline", R.string.get_started_step_reminder_title, R.string.get_started_step_reminder_hint),
    CLASS("Add a class", "Timetable tab, then tap +", R.string.get_started_step_class_title, R.string.get_started_step_class_hint),
    TIMER("Try the timer", "More menu, then Timer", R.string.get_started_step_timer_title, R.string.get_started_step_timer_hint),
    BACKUP("Back up your data", "Settings, then Backup & data, then Connect Google Drive", R.string.get_started_step_backup_title, R.string.get_started_step_backup_hint)
}

object GetStartedLogic {

    /** Which steps the person's data and phone settings show as done right now. */
    fun detect(
        hasCourse: Boolean,
        hasDeadline: Boolean,
        hasReminder: Boolean,
        hasClass: Boolean,
        timerTried: Boolean,
        notificationsOn: Boolean = false,
        batteryOptimizationOff: Boolean = false,
        driveConnected: Boolean = false
    ): Set<GetStartedStep> = buildSet {
        if (hasCourse) add(GetStartedStep.COURSE)
        if (hasDeadline) add(GetStartedStep.DEADLINE)
        if (hasReminder) add(GetStartedStep.REMINDER)
        if (hasClass) add(GetStartedStep.CLASS)
        if (timerTried) add(GetStartedStep.TIMER)
        if (driveConnected) add(GetStartedStep.BACKUP)
        if (notificationsOn) add(GetStartedStep.NOTIFICATIONS)
        if (batteryOptimizationOff) add(GetStartedStep.BATTERY)
    }

    fun isFinished(
        done: Set<GetStartedStep>,
        required: Collection<GetStartedStep> = GetStartedStep.entries
    ): Boolean = done.containsAll(required)
}

/**
 * Remembers which steps were ticked and whether the card was dismissed.
 * A step stays ticked even if the person later deletes the course or task that ticked it.
 * Saved in SharedPreferences, so nothing changes in the database.
 */
class GetStartedPrefs(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("get_started_prefs", Context.MODE_PRIVATE)

    var dismissed: Boolean
        get() = prefs.getBoolean(KEY_DISMISSED, false)
        set(value) = prefs.edit { putBoolean(KEY_DISMISSED, value) }

    var doneSteps: Set<GetStartedStep>
        get() {
            val names = prefs.getStringSet(KEY_DONE, emptySet()).orEmpty()
            return GetStartedStep.entries.filter { it.name in names }.toSet()
        }
        set(value) = prefs.edit { putStringSet(KEY_DONE, value.map { it.name }.toSet()) }

    /**
     * Adds [steps] to the saved ticks without losing any already saved, and returns the full set.
     * Safe to call from more than one screen, for example the timer step is saved from the main
     * screen while the card is saved from Deadlines.
     */
    fun markDone(steps: Set<GetStartedStep>): Set<GetStartedStep> {
        val merged = doneSteps + steps
        if (merged != doneSteps) doneSteps = merged
        return merged
    }

    private companion object {
        const val KEY_DISMISSED = "dismissed"
        const val KEY_DONE = "done_steps"
    }
}
