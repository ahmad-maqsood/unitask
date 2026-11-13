package com.example.util

import android.content.Context

/**
 * The one-time tips. Each one is shown the first time its screen opens and has a "Learn more"
 * link that opens the guide at [guideTitle], which must match a title in [FeatureGuide].
 */
enum class AppTip(val text: String, val guideTitle: String) {
    NOTES(
        "Write lecture notes with Markdown, and tag a note with a course to find it fast.",
        "Notes"
    ),
    TIMER(
        "Pick Stopwatch, Countdown or Pomodoro, then start it. Full screen is in the top bar.",
        "Timer and Countdown"
    ),
    GROUPS(
        "Study with classmates: create a group or join one with a code, then share tasks and courses.",
        "Study Groups & QR Codes"
    )
}

/** Remembers which tips were already shown, so each appears only once. Saved in SharedPreferences. */
class TipPrefs(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("app_tips_prefs", Context.MODE_PRIVATE)

    fun isSeen(tip: AppTip): Boolean = tip.name in prefs.getStringSet(KEY_SEEN, emptySet()).orEmpty()

    fun markSeen(tip: AppTip) {
        val seen = prefs.getStringSet(KEY_SEEN, emptySet()).orEmpty()
        if (tip.name in seen) return
        prefs.edit().putStringSet(KEY_SEEN, seen + tip.name).apply()
    }

    private companion object {
        const val KEY_SEEN = "seen_tips"
    }
}
