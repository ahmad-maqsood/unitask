package com.example.util

import android.content.Context

/** The steps of the "Get started" checklist on the Deadlines screen, in the order they are shown. */
enum class GetStartedStep(val title: String, val hint: String) {
    COURSE("Add a course", "Courses tab, then tap +"),
    DEADLINE("Add a deadline", "Deadlines tab, then tap +"),
    REMINDER("Set a reminder", "Use the Remind me row when you add or edit a deadline"),
    CLASS("Add a class", "Timetable tab, then tap +"),
    TIMER("Try the timer", "More menu, then Timer")
}

object GetStartedLogic {

    /** Which steps the person's data shows as done right now. */
    fun detect(
        hasCourse: Boolean,
        hasDeadline: Boolean,
        hasReminder: Boolean,
        hasClass: Boolean,
        timerTried: Boolean
    ): Set<GetStartedStep> = buildSet {
        if (hasCourse) add(GetStartedStep.COURSE)
        if (hasDeadline) add(GetStartedStep.DEADLINE)
        if (hasReminder) add(GetStartedStep.REMINDER)
        if (hasClass) add(GetStartedStep.CLASS)
        if (timerTried) add(GetStartedStep.TIMER)
    }

    fun isFinished(done: Set<GetStartedStep>): Boolean = done.containsAll(GetStartedStep.entries)
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
