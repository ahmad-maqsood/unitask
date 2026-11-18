package com.example.ui.screens

import android.content.Context
import androidx.annotation.StringRes
import com.example.R

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
    val keywords: List<String> = emptyList(),
    // The texts above are English. These point to the same texts in the phone's language.
    @StringRes val titleRes: Int = 0,
    @StringRes val summaryRes: Int = 0,
    @StringRes val pageLabelRes: Int = 0
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
        titleRes = R.string.cloud_backup_online_account,
        summaryRes = R.string.settings_search_sign_in_for_groups,
        pageLabelRes = R.string.settings_settings,
        title = "Online account",
        summary = "Sign in for study groups",
        pageLabel = "Settings",
        opensAccount = true,
        keywords = listOf("sign in", "login", "log in", "google", "groups", "cloud", "sign out", "logout")
    ),
    SettingsSearchEntry(
        id = "theme",
        titleRes = R.string.settings_theme,
        summaryRes = R.string.settings_search_theme_summary,
        pageLabelRes = R.string.settings_general,
        title = "Theme",
        summary = "Light, dark or follow the phone",
        pageLabel = "General",
        page = SettingsPage.General,
        keywords = listOf("dark mode", "light mode", "dark", "light", "system", "appearance", "night")
    ),
    SettingsSearchEntry(
        id = "accent",
        titleRes = R.string.settings_accent_colour,
        summaryRes = R.string.settings_search_accent_summary,
        pageLabelRes = R.string.settings_general,
        title = "Accent colour",
        summary = "Pick the colour the app uses",
        pageLabel = "General",
        page = SettingsPage.General,
        keywords = listOf("color", "colour", "accent", "palette", "style")
    ),
    SettingsSearchEntry(
        id = "week_parity",
        titleRes = R.string.settings_this_week_is,
        summaryRes = R.string.settings_search_week_summary,
        pageLabelRes = R.string.settings_general,
        title = "This week is",
        summary = "Choose Week A or Week B for the timetable",
        pageLabel = "General",
        page = SettingsPage.General,
        keywords = listOf("week a", "week b", "timetable", "schedule", "parity", "alternate")
    ),
    SettingsSearchEntry(
        id = "sound",
        titleRes = R.string.settings_notification_sound,
        summaryRes = R.string.settings_search_sound_summary,
        pageLabelRes = R.string.settings_notifications_reminders,
        title = "Notification sound",
        summary = "Choose the reminder sound",
        pageLabel = "Notifications & reminders",
        page = SettingsPage.Notifications,
        keywords = listOf("ringtone", "tone", "alert sound", "audio")
    ),
    SettingsSearchEntry(
        id = "background_alerts",
        titleRes = R.string.settings_background_alerts,
        summaryRes = R.string.settings_search_background_summary,
        pageLabelRes = R.string.settings_notifications_reminders,
        title = "Background alerts",
        summary = "Keep reminders reliable (battery optimization)",
        pageLabel = "Notifications & reminders",
        page = SettingsPage.Notifications,
        keywords = listOf("battery", "optimization", "optimisation", "autostart", "auto-start", "background", "restrict", "reliable")
    ),
    SettingsSearchEntry(
        id = "check_reminders",
        titleRes = R.string.reminder_check_check_reminders,
        summaryRes = R.string.settings_search_check_reminders_summary,
        pageLabelRes = R.string.settings_notifications_reminders,
        title = "Check reminders",
        summary = "Send a test reminder to this phone",
        pageLabel = "Notifications & reminders",
        page = SettingsPage.Notifications,
        keywords = listOf("test", "notification test", "not working", "permission")
    ),
    SettingsSearchEntry(
        id = "class_notifications",
        titleRes = R.string.settings_class_start_notifications,
        summaryRes = R.string.settings_search_class_summary,
        pageLabelRes = R.string.settings_notifications_reminders,
        title = "Class start notifications",
        summary = "Alert before and when each class starts",
        pageLabel = "Notifications & reminders",
        page = SettingsPage.Notifications,
        keywords = listOf("class", "lecture", "timetable", "10 minutes")
    ),
    SettingsSearchEntry(
        id = "daily_summary",
        titleRes = R.string.settings_daily_summary,
        summaryRes = R.string.settings_search_daily_summary_summary,
        pageLabelRes = R.string.settings_notifications_reminders,
        title = "Daily summary",
        summary = "A morning summary of what is due",
        pageLabel = "Notifications & reminders",
        page = SettingsPage.Notifications,
        keywords = listOf("morning", "digest", "today", "overview")
    ),
    SettingsSearchEntry(
        id = "summary_time",
        titleRes = R.string.settings_summary_time,
        summaryRes = R.string.settings_search_summary_time_summary,
        pageLabelRes = R.string.settings_notifications_reminders,
        title = "Summary time",
        summary = "When the daily summary arrives",
        pageLabel = "Notifications & reminders",
        page = SettingsPage.Notifications,
        keywords = listOf("daily summary time", "clock", "hour")
    ),
    SettingsSearchEntry(
        id = "drive_backup",
        titleRes = R.string.drive_backup_google_drive_backup,
        summaryRes = R.string.settings_search_drive_summary,
        pageLabelRes = R.string.settings_backup_data,
        title = "Google Drive backup",
        summary = "Back up and restore your data with Google Drive",
        pageLabel = "Backup & data",
        page = SettingsPage.Backup,
        keywords = listOf("backup", "restore", "import", "drive", "cloud", "sync", "export")
    ),
    SettingsSearchEntry(
        id = "local_backup",
        titleRes = R.string.local_backup_backup_file,
        summaryRes = R.string.settings_search_local_backup_summary,
        pageLabelRes = R.string.settings_backup_data,
        title = "Backup file",
        summary = "Save or load a backup file on this phone",
        pageLabel = "Backup & data",
        page = SettingsPage.Backup,
        keywords = listOf("local backup", "backup", "restore", "export", "import", "file", "download")
    ),
    SettingsSearchEntry(
        id = "learn",
        titleRes = R.string.search_learn_to_use_the_app,
        summaryRes = R.string.settings_step_by_step_help_for,
        pageLabelRes = R.string.settings_about_help,
        title = "Learn to use the app",
        summary = "Step by step help for every feature",
        pageLabel = "About & help",
        page = SettingsPage.About,
        keywords = listOf("help", "guide", "tutorial", "how to", "features", "instructions")
    ),
    SettingsSearchEntry(
        id = "source_code",
        titleRes = R.string.settings_source_code,
        summaryRes = R.string.settings_search_source_summary,
        pageLabelRes = R.string.settings_about_help,
        title = "Source code",
        summary = "Open the project on GitHub",
        pageLabel = "About & help",
        page = SettingsPage.About,
        keywords = listOf("github", "repository", "repo", "open source", "code")
    ),
    SettingsSearchEntry(
        id = "latest_release",
        titleRes = R.string.settings_latest_release,
        summaryRes = R.string.settings_open_the_newest_version_on,
        pageLabelRes = R.string.settings_about_help,
        title = "Latest release",
        summary = "Open the newest version on GitHub",
        pageLabel = "About & help",
        page = SettingsPage.About,
        keywords = listOf("download", "apk", "newest", "new version", "releases")
    ),
    SettingsSearchEntry(
        id = "report_problem",
        titleRes = R.string.crash_report_report_a_problem,
        summaryRes = R.string.settings_search_report_summary,
        pageLabelRes = R.string.settings_about_help,
        title = "Report a problem",
        summary = "Opens a short form",
        pageLabel = "About & help",
        page = SettingsPage.About,
        keywords = listOf("bug", "issue", "feedback", "error", "broken")
    ),
    SettingsSearchEntry(
        id = "request_feature",
        titleRes = R.string.settings_request_a_feature,
        summaryRes = R.string.settings_search_request_summary,
        pageLabelRes = R.string.settings_about_help,
        title = "Request a feature",
        summary = "Tell us what you would like to see",
        pageLabel = "About & help",
        page = SettingsPage.About,
        keywords = listOf("idea", "suggestion", "suggest", "feedback", "wish")
    ),
    SettingsSearchEntry(
        id = "updates",
        titleRes = R.string.settings_check_for_updates,
        summaryRes = R.string.settings_search_updates_summary,
        pageLabelRes = R.string.settings_about_help,
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

/**
 * The same entry with its title, summary and page name in the phone's language. The English title
 * stays searchable as a keyword, so typing "dark" still finds Theme on a phone set to Urdu.
 */
fun SettingsSearchEntry.localized(context: Context): SettingsSearchEntry {
    val localTitle = if (titleRes != 0) context.getString(titleRes) else title
    return copy(
        title = localTitle,
        summary = if (summaryRes != 0) context.getString(summaryRes) else summary,
        pageLabel = if (pageLabelRes != 0) context.getString(pageLabelRes) else pageLabel,
        keywords = if (localTitle != title) keywords + title else keywords
    )
}

/** [allSettingsSearchEntries] in the phone's language. */
fun localizedSettingsSearchEntries(context: Context): List<SettingsSearchEntry> =
    allSettingsSearchEntries.map { it.localized(context) }
