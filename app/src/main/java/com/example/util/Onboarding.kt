package com.example.util

import android.content.Context

/** The phone settings shown on the second welcome page, in the order they appear. */
enum class OnboardingSetting {
    NOTIFICATIONS,
    BATTERY
}

object OnboardingLogic {

    /**
     * The welcome screens are for a brand new install only. Anyone who finished them already, or who
     * has used the app before this feature existed, goes straight to the app.
     */
    fun shouldShow(alreadyDone: Boolean, usedAppBefore: Boolean): Boolean =
        !alreadyDone && !usedAppBefore

    /** True when every setting on the page is turned on. */
    fun allOn(on: Set<OnboardingSetting>): Boolean =
        on.containsAll(OnboardingSetting.entries)
}

/** Remembers that the welcome screens were finished or skipped. Saved in SharedPreferences. */
object OnboardingPrefs {

    private const val PREFS_NAME = "unitask_settings"
    private const val KEY_DONE = "onboarding_done"

    fun shouldShow(context: Context): Boolean {
        val settings = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Signs that the person already used UniTask before the welcome screens were added.
        val getStarted = GetStartedPrefs(context)
        val usedBefore = settings.getBoolean("first_reminder_saved", false) ||
            settings.getBoolean("notification_permission_prompted", false) ||
            settings.getBoolean("background_setup_prompted", false) ||
            getStarted.dismissed ||
            getStarted.doneSteps.isNotEmpty()
        return OnboardingLogic.shouldShow(
            alreadyDone = settings.getBoolean(KEY_DONE, false),
            usedAppBefore = usedBefore
        )
    }

    fun markDone(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DONE, true)
            .apply()
    }
}