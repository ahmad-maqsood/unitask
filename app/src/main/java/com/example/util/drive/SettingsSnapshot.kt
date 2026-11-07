package com.example.util.drive

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.reminder.ClassReminderScheduler
import com.example.reminder.DailySummaryScheduler
import com.example.ui.screens.NotesSort
import com.example.ui.theme.AccentTheme
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.ThemePreference
import com.example.util.HighPriorityAlarm
import com.example.util.NotificationSound
import com.example.util.WeekParity
import com.example.util.timer.TimerLogic
import com.example.util.timer.TimerStore
import org.json.JSONObject

/**
 * Captures user-facing app settings into a JSON object for backup,
 * and restores them using the same public setters and side effects
 * as the Settings screen.
 */
object SettingsSnapshot {

    const val SETTINGS_VERSION = 1
    private val ALLOWED_SOUND_CHOICES = setOf("default", "system", "s2", "s3", "s4")

    /**
     * Reads all allowlisted user settings through their public getters.
     * Never captures device-specific flags, permissions, tokens, or running timers.
     */
    fun capture(context: Context): JSONObject {
        val obj = JSONObject()
        obj.put("settingsVersion", SETTINGS_VERSION)

        // Theme and accent
        ThemePreference.load(context)
        obj.put("themeMode", ThemePreference.mode.name)
        obj.put("themeAccent", ThemePreference.accent.name)

        // Week A anchor (only when set)
        val anchor = WeekParity.getWeekAAnchor(context)
        if (anchor != null && anchor > 0L) {
            obj.put("weekAAnchorMillis", anchor)
        }

        // Notification sound: phone ringtone Uri is device-specific, so map it to system
        val currentChoice = NotificationSound.choice(context)
        val safeChoice = if (currentChoice in ALLOWED_SOUND_CHOICES) currentChoice else "system"
        obj.put("soundChoice", safeChoice)

        // Daily summary
        obj.put("dailySummaryEnabled", DailySummaryScheduler.isEnabled(context))
        obj.put("dailySummaryHour", DailySummaryScheduler.hour(context))
        obj.put("dailySummaryMinute", DailySummaryScheduler.minute(context))

        // Class reminders
        obj.put("classStartNotificationsEnabled", ClassReminderScheduler.isEnabled(context))

        // High priority alarm
        obj.put("highPriorityAlarmEnabled", HighPriorityAlarm.isEnabled(context))

        // Notes sort
        val notesPrefs = context.getSharedPreferences("notes_prefs", Context.MODE_PRIVATE)
        val savedSort = notesPrefs.getString("sort", null)
        val sort = NotesSort.entries.firstOrNull { it.name == savedSort } ?: NotesSort.UPDATED
        obj.put("notesSort", sort.name)

        // Timer settings
        val timerStore = TimerStore(context)
        obj.put("timerLastCountdownMinutes", timerStore.lastCountdownMinutes())
        obj.put("timerKeepScreenOnFullscreen", timerStore.keepScreenOnInFullscreen())

        return obj
    }

    /**
     * Applies allowlisted settings defensively.
     * Returns the count of settings successfully applied.
     */
    suspend fun apply(context: Context, settings: JSONObject?): Int {
        if (settings == null) return 0
        var appliedCount = 0

        // Theme mode
        if (settings.has("themeMode")) {
            val raw = settings.opt("themeMode")
            if (raw is String) {
                val mode = ThemeMode.entries.firstOrNull { it.name == raw }
                if (mode != null) {
                    ThemePreference.set(context, mode)
                    appliedCount++
                }
            }
        }

        // Theme accent
        if (settings.has("themeAccent")) {
            val raw = settings.opt("themeAccent")
            if (raw is String) {
                val accent = AccentTheme.entries.firstOrNull { it.name == raw }
                if (accent != null) {
                    ThemePreference.setAccent(context, accent)
                    appliedCount++
                }
            }
        }

        // Week A anchor
        if (settings.has("weekAAnchorMillis")) {
            val raw = settings.opt("weekAAnchorMillis")
            if (raw is Number) {
                val anchor = raw.toLong()
                val now = System.currentTimeMillis()
                val oneYearAhead = now + 365L * 24 * 60 * 60 * 1000L
                if (anchor > 0L && anchor <= oneYearAhead) {
                    WeekParity.setWeekAAnchor(context, anchor)
                    appliedCount++
                }
            }
        }

        // Sound choice
        if (settings.has("soundChoice")) {
            val raw = settings.opt("soundChoice")
            if (raw is String) {
                if (raw == NotificationSound.CHOICE_SYSTEM) {
                    NotificationSound.setChoice(context, NotificationSound.CHOICE_SYSTEM)
                    appliedCount++
                } else if (raw in setOf("default", "s2", "s3", "s4")) {
                    val available = NotificationSound.availableBuiltIn(context).map { it.key }
                    if (raw in available) {
                        NotificationSound.setChoice(context, raw)
                        appliedCount++
                    }
                }
            }
        }

        // Daily summary
        var summaryChanged = false
        if (settings.has("dailySummaryEnabled")) {
            val raw = settings.opt("dailySummaryEnabled")
            if (raw is Boolean) {
                DailySummaryScheduler.setEnabled(context, raw)
                summaryChanged = true
                appliedCount++
            }
        }
        if (settings.has("dailySummaryHour")) {
            val raw = settings.opt("dailySummaryHour")
            if (raw is Number && raw !is Boolean) {
                val hour = raw.toInt()
                if (hour in 0..23) {
                    DailySummaryScheduler.setTime(context, hour, DailySummaryScheduler.minute(context))
                    summaryChanged = true
                    appliedCount++
                }
            }
        }
        if (settings.has("dailySummaryMinute")) {
            val raw = settings.opt("dailySummaryMinute")
            if (raw is Number && raw !is Boolean) {
                val minute = raw.toInt()
                if (minute in 0..59) {
                    DailySummaryScheduler.setTime(context, DailySummaryScheduler.hour(context), minute)
                    summaryChanged = true
                    appliedCount++
                }
            }
        }
        if (summaryChanged) {
            DailySummaryScheduler(context).reschedule()
        }

        // Class start notifications
        if (settings.has("classStartNotificationsEnabled")) {
            val raw = settings.opt("classStartNotificationsEnabled")
            if (raw is Boolean) {
                ClassReminderScheduler.setEnabled(context, raw)
                val db = AppDatabase.getDatabase(context)
                ClassReminderScheduler(context).rescheduleAll(db)
                appliedCount++
            }
        }

        // High priority alarm
        if (settings.has("highPriorityAlarmEnabled")) {
            val raw = settings.opt("highPriorityAlarmEnabled")
            if (raw is Boolean) {
                HighPriorityAlarm.setEnabled(context, raw)
                if (raw) {
                    HighPriorityAlarm.ensureChannel(context)
                }
                appliedCount++
            }
        }

        // Notes sort
        if (settings.has("notesSort")) {
            val raw = settings.opt("notesSort")
            if (raw is String) {
                val sort = NotesSort.entries.firstOrNull { it.name == raw }
                if (sort != null) {
                    context.getSharedPreferences("notes_prefs", Context.MODE_PRIVATE)
                        .edit()
                        .putString("sort", sort.name)
                        .apply()
                    appliedCount++
                }
            }
        }

        // Timer settings
        val timerStore = TimerStore(context)
        if (settings.has("timerLastCountdownMinutes")) {
            val raw = settings.opt("timerLastCountdownMinutes")
            if (raw is Number && raw !is Boolean) {
                val minutes = raw.toInt()
                if (minutes in TimerLogic.MIN_COUNTDOWN_MINUTES..TimerLogic.MAX_COUNTDOWN_MINUTES) {
                    timerStore.setLastCountdownMinutes(minutes)
                    appliedCount++
                }
            }
        }
        if (settings.has("timerKeepScreenOnFullscreen")) {
            val raw = settings.opt("timerKeepScreenOnFullscreen")
            if (raw is Boolean) {
                timerStore.setKeepScreenOnInFullscreen(raw)
                appliedCount++
            }
        }

        return appliedCount
    }
}
