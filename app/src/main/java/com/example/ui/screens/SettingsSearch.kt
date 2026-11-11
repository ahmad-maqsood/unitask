package com.example.ui.screens

/**
 * One searchable item in the Settings sheet.
 *
 * [page] is the Settings page the item lives on. [opensAccount] marks the Account row,
 * which opens the sign in sheet instead of a Settings page.
 * [keywords] are extra words people may type (for example "dark" for the theme).
 */
data class SettingsSearchEntry(
    val id: String,
    val title: String,
    val summary: String,
    val pageLabel: String,
    val page: SettingsPage? = null,
    val opensAccount: Boolean = false,
    val keywords: List<String> = emptyList()
)

/** Asks the Settings button to open the sheet, optionally on a specific page. */
data class SettingsOpenRequest(
    val page: SettingsPage?,
    val requestId: Long = System.nanoTime()
)

/**
 * Every setting a person can find in the Settings sheet. Keep this in step with
 * SettingsSheet.kt when a row is added, renamed or removed.
 */
val allSettingsSearchEntries: List<SettingsSearchEntry> = listOf(
    SettingsSearchEntry(
        id = "account",
        title = "Online account",
        summary = "Sign in for groups and chat",
        pageLabel = "Settings",
        opensAccount = true,
        keywords = listOf("sign in", "login", "log in", "google", "groups", "chat", "cloud", "sign out", "logout")
    ),
    SettingsSearchEntry(
        id = "theme",
        title = "Theme",
        summary = "Light, dark or follow the phone",
        pageLabel = "General",
        page = SettingsPage.General,
        keywords = listOf("dark mode", "light mode", "dark", "light", "system", "appearance", "night")
    ),
    SettingsSearchEntry(
        id = "accent",
        title = "Accent colour",
        summary = "Pick the colour the app uses",
        pageLabel = "General",
        page = SettingsPage.General,
        keywords = listOf("color", "colour", "accent", "palette", "style")
    ),
    SettingsSearchEntry(
        id = "week_parity",
        title = "This week is",
        summary = "Choose Week A or Week B for the timetable",
        pageLabel = "General",
        page = SettingsPage.General,
        keywords = listOf("week a", "week b", "timetable", "schedule", "parity", "alternate")
    ),
    SettingsSearchEntry(
        id = "sound",
        title = "Notification sound",
        summary = "Choose the reminder sound",
        pageLabel = "Notifications & reminders",
        page = SettingsPage.Notifications,
        keywords = listOf("ringtone", "tone", "alert sound", "audio")
    ),
    SettingsSearchEntry(
        id = "background_alerts",
        title = "Background alerts",
        summary = "Keep reminders reliable (battery and autostart)",
        pageLabel = "Notifications & reminders",
        page = SettingsPage.Notifications,
        keywords = listOf("battery", "optimization", "optimisation", "autostart", "background", "restrict", "reliable")
    ),
    SettingsSearchEntry(
        id = "check_reminders",
        title = "Check reminders",
        summary = "Send a test reminder to this phone",
        pageLabel = "Notifications & reminders",
        page = SettingsPage.Notifications,
        keywords = listOf("test", "notification test", "not working", "permission")
    ),
    SettingsSearchEntry(
        id = "class_notifications",
        title = "Class start notifications",
        summary = "Alert before and when each class starts",
        pageLabel = "Notifications & reminders",
        page = SettingsPage.Notifications,
        keywords = listOf("class", "lecture", "timetable", "10 minutes")
    ),
    SettingsSearchEntry(
        id = "daily_summary",
        title = "Daily summary",
        summary = "A morning summary of what is due",
        pageLabel = "Notifications & reminders",
        page = SettingsPage.Notifications,
        keywords = listOf("morning", "digest", "today", "overview")
    ),
    SettingsSearchEntry(
        id = "summary_time",
        title = "Summary time",
        summary = "When the daily summary arrives",
        pageLabel = "Notifications & reminders",
        page = SettingsPage.Notifications,
        keywords = listOf("daily summary time", "clock", "hour")
    ),
    SettingsSearchEntry(
        id = "drive_backup",
        title = "Google Drive backup",
        summary = "Back up and restore your data with Google Drive",
        pageLabel = "Backup & data",
        page = SettingsPage.Backup,
        keywords = listOf("backup", "restore", "import", "drive", "cloud", "sync", "export")
    ),
    SettingsSearchEntry(
        id = "local_backup",
        title = "Backup file",
        summary = "Save or load a backup file on this phone",
        pageLabel = "Backup & data",
        page = SettingsPage.Backup,
        keywords = listOf("local backup", "backup", "restore", "export", "import", "file", "download")
    ),
    SettingsSearchEntry(
        id = "learn",
        title = "Learn to use the app",
        summary = "Step by step help for every feature",
        pageLabel = "About & help",
        page = SettingsPage.About,
        keywords = listOf("help", "guide", "tutorial", "how to", "features", "instructions")
    ),
    SettingsSearchEntry(
        id = "source_code",
        title = "Source code",
        summary = "Open the project on GitHub",
        pageLabel = "About & help",
        page = SettingsPage.About,
        keywords = listOf("github", "repository", "repo", "open source", "code")
    ),
    SettingsSearchEntry(
        id = "report_problem",
        title = "Report a problem",
        summary = "Opens a short form",
        pageLabel = "About & help",
        page = SettingsPage.About,
        keywords = listOf("bug", "issue", "feedback", "error", "broken")
    ),
    SettingsSearchEntry(
        id = "request_feature",
        title = "Request a feature",
        summary = "Tell us what you would like to see",
        pageLabel = "About & help",
        page = SettingsPage.About,
        keywords = listOf("idea", "suggestion", "suggest", "feedback", "wish")
    ),
    SettingsSearchEntry(
        id = "updates",
        title = "Check for updates",
        summary = "See if a newer version is available",
        pageLabel = "About & help",
        page = SettingsPage.About,
        keywords = listOf("update", "upgrade", "version", "release", "new version")
    )
)

/**
 * True when EVERY word is found in the title, summary, page name or keywords of [entry].
 */
fun matchesSetting(entry: SettingsSearchEntry, words: List<String>): Boolean {
    return words.all { word ->
        entry.title.contains(word, ignoreCase = true) ||
            entry.summary.contains(word, ignoreCase = true) ||
            entry.pageLabel.contains(word, ignoreCase = true) ||
            entry.keywords.any { it.contains(word, ignoreCase = true) }
    }
}

/** Settings that match [query], in the order they appear in Settings. Blank query gives none. */
fun searchSettings(
    query: String,
    entries: List<SettingsSearchEntry> = allSettingsSearchEntries
): List<SettingsSearchEntry> {
    val words = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return emptyList()
    return entries.filter { matchesSetting(it, words) }
}
