package com.example.util.drive

import android.content.Context
import android.content.SharedPreferences
import androidx.room.InvalidationTracker
import com.example.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Notices when backed up data changes and asks [DriveAutoBackup] for a backup.
 *
 * Two sources are watched:
 * - the database tables that go into a backup (courses, tasks, task attachments, timetable
 *   slots, notes and note folders), through Room's invalidation tracker, so every write counts
 *   wherever it comes from (screens, notification buttons, imports);
 * - the small history files that also go into a backup (ticked task history and timer history);
 * - the app settings that go into a backup (theme, week A anchor, notification sound, daily
 *   summary, class reminders, notes sort). Only those keys count: the same file also holds values
 *   the app rewrites on its own (such as scheduled reminder ids), which must not cause backups.
 *
 * It does nothing until Google Drive is connected and automatic backup is on. Starting it more than
 * once is safe. The one setting not watched is the last countdown length of the timer, because its
 * file also changes on every timer start and stop. It is picked up by the next backup that runs
 * for another reason, at the latest the daily one.
 */
object DriveChangeWatcher {

    private val TABLES = arrayOf(
        "courses",
        "tasks",
        "task_attachments",
        "lecture_slots",
        "notes",
        "note_folders"
    )

    /** Files whose every change counts as a data change. */
    private val PREF_FILES = listOf("completion_log", "tools_timer_history")

    /**
     * Settings files where only these keys count. Must match what SettingsSnapshot.capture reads.
     */
    internal val SETTINGS_KEYS: Map<String, Set<String>> = mapOf(
        "unitask_settings" to setOf(
            "theme_mode",
            "theme_accent",
            "week_a_anchor_millis",
            "sound_choice",
            "daily_summary_enabled",
            "daily_summary_hour",
            "daily_summary_minute",
            "class_start_notifications_enabled"
        ),
        "notes_prefs" to setOf("sort")
    )

    /**
     * Whether a change to [key] in a settings file counts. A null key means the whole file was
     * cleared, which counts.
     */
    internal fun isBackedUpSettingKey(file: String, key: String?): Boolean {
        val keys = SETTINGS_KEYS[file] ?: return false
        return key == null || key in keys
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    private var watchedDb: AppDatabase? = null
    private var observer: InvalidationTracker.Observer? = null
    private var prefsRegistered = false

    // Kept in fields because SharedPreferences holds its listeners weakly.
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        appContext?.let { DriveAutoBackup.onDataChanged(it) }
    }

    private val settingsListeners: Map<String, SharedPreferences.OnSharedPreferenceChangeListener> =
        SETTINGS_KEYS.keys.associateWith { file ->
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (isBackedUpSettingKey(file, key)) {
                    appContext?.let { DriveAutoBackup.onDataChanged(it) }
                }
            }
        }

    @Volatile
    private var appContext: Context? = null

    fun start(context: Context) {
        val app = context.applicationContext
        try {
            val prefs = DrivePrefs(app)
            if (!prefs.isConnected || !prefs.autoEnabled) return
            appContext = app

            scope.launch {
                try {
                    registerDatabaseObserver(app)
                    registerPrefListeners(app)
                } catch (e: Exception) {
                    android.util.Log.w("UniTask", "Could not start the backup change watcher", e)
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("UniTask", "Could not start the backup change watcher", e)
        }
    }

    private fun registerDatabaseObserver(app: Context) {
        val db = AppDatabase.getDatabase(app)
        synchronized(lock) {
            if (watchedDb === db) return
            val newObserver = object : InvalidationTracker.Observer(TABLES) {
                override fun onInvalidated(tables: Set<String>) {
                    DriveAutoBackup.onDataChanged(app)
                }
            }
            val oldDb = watchedDb
            val oldObserver = observer
            if (oldDb != null && oldObserver != null) {
                try {
                    oldDb.invalidationTracker.removeObserver(oldObserver)
                } catch (_: Exception) {
                    // The old database may already be closed. Nothing to clean up then.
                }
            }
            db.invalidationTracker.addObserver(newObserver)
            watchedDb = db
            observer = newObserver
        }
    }

    private fun registerPrefListeners(app: Context) {
        synchronized(lock) {
            if (prefsRegistered) return
            for (name in PREF_FILES) {
                app.getSharedPreferences(name, Context.MODE_PRIVATE)
                    .registerOnSharedPreferenceChangeListener(prefListener)
            }
            for ((name, listener) in settingsListeners) {
                app.getSharedPreferences(name, Context.MODE_PRIVATE)
                    .registerOnSharedPreferenceChangeListener(listener)
            }
            prefsRegistered = true
        }
    }
}
