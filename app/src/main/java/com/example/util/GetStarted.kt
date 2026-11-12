package com.example.util

import android.content.Context

/** The steps of the "Get started" checklist on the Deadlines screen, in the order they are shown. */
enum class GetStartedStep(val title: String, val hint: String, val isSystem: Boolean = false) {
    // Phone settings. These are read live from the phone, never saved as ticked, so a step
    // un-ticks again if the person turns the setting back off.
    NOTIFICATIONS("Allow notifications", "So reminders and group alerts can reach you", isSystem = true),
    BATTERY("Turn off battery optimization", "Lets reminders ring on time in the background", isSystem = true),
    AUTOSTART("Enable auto-start", "Keeps reminders working after your phone restarts", isSystem = true),

    COURSE("Add a course", "Courses tab, then tap +"),
    DEADLINE("Add a deadline", "Deadlines tab, then tap +"),
    REMINDER("Set a reminder", "Use the Remind me row when you add or edit a deadline"),
    CLASS("Add a class", "Timetable tab, then tap +"),
    TIMER("Try the timer", "More menu, then Timer"),
    BACKUP("Back up your data", "Settings, then Backup & data, then Connect Google Drive")
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
        autostartDone: Boolean = false,
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
        if (autostartDone) add(GetStartedStep.AUTOSTART)
    }

    /** The steps shown on this phone. Auto-start only exists on some brands, so it is left out elsewhere. */
    fun requiredSteps(needsAutostart: Boolean): List<GetStartedStep> =
        GetStartedStep.entries.filter { needsAutostart || it != GetStartedStep.AUTOSTART }

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
        set(value) = prefs.edit().putBoolean(KEY_DISMISSED, value).apply()

    var doneSteps: Set<GetStartedStep>
        get() {
            val names = prefs.getStringSet(KEY_DONE, emptySet()).orEmpty()
            return GetStartedStep.entries.filter { it.name in names }.toSet()
        }
        set(value) = prefs.edit().putStringSet(KEY_DONE, value.map { it.name }.toSet()).apply()

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
