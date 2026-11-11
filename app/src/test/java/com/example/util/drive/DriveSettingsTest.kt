package com.example.util.drive

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.Course
import com.example.data.local.TaskItem
import com.example.reminder.ClassReminderScheduler
import com.example.reminder.DailySummaryScheduler
import com.example.ui.screens.NotesSort
import com.example.ui.theme.AccentTheme
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.ThemePreference
import com.example.util.BackupResult
import com.example.util.LocalBackupManager
import com.example.util.NotificationSound
import com.example.util.WeekParity
import com.example.util.timer.TimerStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DriveSettingsTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = AppDatabase.getDatabase(context)
        runBlocking(Dispatchers.IO) { db.clearAllTables() }
        clearAllPrefs()
        NotificationSound.choice(context) // run the one-time fresh-install check now, so it can't wipe a restored choice later
    }

    @After
    fun tearDown() {
        db.close()
        AppDatabase::class.java.getDeclaredField("INSTANCE")
            .apply { isAccessible = true }
            .set(null, null)
        clearAllPrefs()
    }

    private fun clearAllPrefs() {
        listOf("unitask_settings", "notes_prefs", "tools_timer_prefs", "drive_backup_prefs").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        ThemePreference.load(context)
        WeekParity.setWeekAAnchor(context, null)
    }

    @Test
    fun capture_containsOnlyAllowlistedKeys_andMapsPhoneSoundToSystem() {
        val settingsPrefs = context.getSharedPreferences("unitask_settings", Context.MODE_PRIVATE)
        settingsPrefs.edit()
            .putString("sound_choice", NotificationSound.CHOICE_PHONE)
            .putString("sound_phone_uri", "content://media/internal/audio/media/10")
            .putBoolean("notification_permission_prompted", true)
            .putBoolean("background_setup_prompted", true)
            .putBoolean("autostart_step_done", true)
            .putString("class_start_scheduled_slot_ids", "1,2,3")
            .putBoolean("use_system_sound", true)
            .commit()

        val timerPrefs = context.getSharedPreferences("tools_timer_prefs", Context.MODE_PRIVATE)
        timerPrefs.edit()
            .putLong("running_since_millis", 123456789L)
            .putString("mode", "STOPWATCH")
            .commit()

        val drivePrefs = context.getSharedPreferences("drive_backup_prefs", Context.MODE_PRIVATE)
        drivePrefs.edit()
            .putString("connected_email", "user@example.com")
            .commit()

        val captured = SettingsSnapshot.capture(context)

        val allowedKeys = setOf(
            "settingsVersion",
            "themeMode",
            "themeAccent",
            "soundChoice",
            "dailySummaryEnabled",
            "dailySummaryHour",
            "dailySummaryMinute",
            "classStartNotificationsEnabled",
            "notesSort",
            "timerLastCountdownMinutes",
            "timerKeepScreenOnFullscreen"
        )

        val keysInJson = captured.keys().asSequence().toSet()
        assertEquals(allowedKeys, keysInJson)

        // Forbidden keys must never exist
        assertFalse(captured.has("sound_phone_uri"))
        assertFalse(captured.has("notification_permission_prompted"))
        assertFalse(captured.has("background_setup_prompted"))
        assertFalse(captured.has("autostart_step_done"))
        assertFalse(captured.has("class_start_scheduled_slot_ids"))
        assertFalse(captured.has("use_system_sound"))
        assertFalse(captured.has("running_since_millis"))
        assertFalse(captured.has("mode"))
        assertFalse(captured.has("connected_email"))

        // Phone sound choice must be mapped to system
        assertEquals("system", captured.getString("soundChoice"))
    }

    @Test
    fun roundTrip_restoresAllSettingsAndTriggersSideEffectsWithoutRestart() = runBlocking {
        val courseDao = db.courseDao()
        val taskDao = db.taskDao()
        val fakeStore = FakeDriveStore()

        val cId = courseDao.insertCourse(Course(name = "Math", code = "MATH 101", colorHex = "#123456"))
        taskDao.insertTask(TaskItem(courseId = cId, title = "Task", dueDateEpochMillis = 1000L))

        // Set non-default settings
        ThemePreference.set(context, ThemeMode.DARK)
        ThemePreference.setAccent(context, AccentTheme.ROSE)

        val anchor = System.currentTimeMillis() + 86_400_000L
        WeekParity.setWeekAAnchor(context, anchor)

        DailySummaryScheduler.setEnabled(context, true)
        DailySummaryScheduler.setTime(context, 8, 30)
        DailySummaryScheduler(context).reschedule()

        ClassReminderScheduler.setEnabled(context, false)
        ClassReminderScheduler(context).rescheduleAll(db)

        NotificationSound.setChoice(context, "s3")

        context.getSharedPreferences("notes_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("sort", NotesSort.TITLE.name)
            .commit()

        TimerStore(context).setLastCountdownMinutes(45)
        TimerStore(context).setKeepScreenOnInFullscreen(false)

        // Back up to Drive
        val backupRes = DriveBackupManager.backUp(context, fakeStore, force = true)
        assertTrue(backupRes is DriveBackupResult.Success)

        // Reset every setting to defaults
        ThemePreference.set(context, ThemeMode.SYSTEM)
        ThemePreference.setAccent(context, AccentTheme.INDIGO)
        WeekParity.setWeekAAnchor(context, null)
        DailySummaryScheduler.setEnabled(context, false)
        DailySummaryScheduler.setTime(context, 7, 0)
        DailySummaryScheduler(context).cancel()
        ClassReminderScheduler.setEnabled(context, true)
        NotificationSound.setChoice(context, NotificationSound.CHOICE_SYSTEM)
        context.getSharedPreferences("notes_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("sort", NotesSort.UPDATED.name)
            .commit()
        TimerStore(context).setLastCountdownMinutes(25)
        TimerStore(context).setKeepScreenOnInFullscreen(true)

        // Verify they were reset
        assertEquals(ThemeMode.SYSTEM, ThemePreference.mode)
        assertEquals(AccentTheme.INDIGO, ThemePreference.accent)
        assertFalse(DailySummaryScheduler.isEnabled(context))
        assertTrue(ClassReminderScheduler.isEnabled(context))

        // Restore from Drive
        val restoreRes = DriveBackupManager.restore(context, fakeStore)
        assertTrue(restoreRes is BackupResult.Success)
        val counts = (restoreRes as BackupResult.Success).counts
        assertTrue(counts.settingsApplied > 0)

        // Verify all settings and in-memory Compose state are restored
        assertEquals(ThemeMode.DARK, ThemePreference.mode)
        assertEquals(AccentTheme.ROSE, ThemePreference.accent)
        assertEquals(anchor, WeekParity.getWeekAAnchor(context))
        assertEquals(anchor, WeekParity.inMemoryAnchorMillis)

        assertTrue(DailySummaryScheduler.isEnabled(context))
        assertEquals(8, DailySummaryScheduler.hour(context))
        assertEquals(30, DailySummaryScheduler.minute(context))

        // Shadow AlarmManager has scheduled alarm for daily summary
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val shadowAm = shadowOf(am)
        assertNotNull(shadowAm.nextScheduledAlarm)

        assertFalse(ClassReminderScheduler.isEnabled(context))

        assertEquals("s3", NotificationSound.choice(context))

        val restoredSort = context.getSharedPreferences("notes_prefs", Context.MODE_PRIVATE).getString("sort", null)
        assertEquals(NotesSort.TITLE.name, restoredSort)

        assertEquals(45, TimerStore(context).lastCountdownMinutes())
        assertFalse(TimerStore(context).keepScreenOnInFullscreen())
    }

    @Test
    fun restoreWithoutSettingsKey_changesNoSettingAndDoesNotFail() = runBlocking {
        ThemePreference.set(context, ThemeMode.LIGHT)
        TimerStore(context).setLastCountdownMinutes(30)

        val dataWithoutSettings = JSONObject().apply {
            put("courses", JSONArray())
            put("slots", JSONArray())
            put("tasks", JSONArray())
            put("notes", JSONArray())
        }

        val counts = LocalBackupManager.applyBackupData(context, dataWithoutSettings, null)
        assertEquals(0, counts.settingsApplied)

        // Existing settings remain untouched
        assertEquals(ThemeMode.LIGHT, ThemePreference.mode)
        assertEquals(30, TimerStore(context).lastCountdownMinutes())
    }

    @Test
    fun invalidValues_areSkippedOneByOne_andValidOnesStillApply() = runBlocking {
        ThemePreference.set(context, ThemeMode.SYSTEM)
        ThemePreference.setAccent(context, AccentTheme.INDIGO)
        DailySummaryScheduler.setTime(context, 7, 0)
        TimerStore(context).setLastCountdownMinutes(25)

        val farFuture = System.currentTimeMillis() + 10L * 365L * 24 * 60 * 60 * 1000L

        val invalidSettings = JSONObject().apply {
            put("themeMode", "PURPLE") // invalid enum
            put("themeAccent", "NOPE") // invalid enum
            put("weekAAnchorMillis", farFuture) // too far in future
            put("dailySummaryHour", 99) // out of range
            put("dailySummaryMinute", -1) // out of range
            put("timerLastCountdownMinutes", 100000) // out of range
            put("dailySummaryEnabled", "true") // string instead of boolean
            put("soundChoice", "s999") // non-existent sound

            // Valid settings in the same payload
            put("classStartNotificationsEnabled", false)
            put("notesSort", "CREATED")
            put("timerKeepScreenOnFullscreen", false)
        }

        val applied = SettingsSnapshot.apply(context, invalidSettings)
        assertEquals(3, applied)

        // Invalid were skipped
        assertEquals(ThemeMode.SYSTEM, ThemePreference.mode)
        assertEquals(AccentTheme.INDIGO, ThemePreference.accent)
        assertEquals(7, DailySummaryScheduler.hour(context))
        assertEquals(0, DailySummaryScheduler.minute(context))
        assertEquals(25, TimerStore(context).lastCountdownMinutes())

        // Valid were applied
        assertFalse(ClassReminderScheduler.isEnabled(context))
        val sort = context.getSharedPreferences("notes_prefs", Context.MODE_PRIVATE).getString("sort", null)
        assertEquals(NotesSort.CREATED.name, sort)
        assertFalse(TimerStore(context).keepScreenOnInFullscreen())
    }

    @Test
    fun hostileSettingsObject_changesNothingOutsideAllowlist() = runBlocking {
        val hostile = JSONObject().apply {
            put("notification_permission_prompted", true)
            put("sound_phone_uri", "content://hostile/uri")
            put("autostart_step_done", true)
            put("custom_hack_key", "injected_value")
        }

        val applied = SettingsSnapshot.apply(context, hostile)
        assertEquals(0, applied)

        val prefs = context.getSharedPreferences("unitask_settings", Context.MODE_PRIVATE)
        assertFalse(prefs.contains("notification_permission_prompted"))
        assertFalse(prefs.contains("sound_phone_uri"))
        assertFalse(prefs.contains("autostart_step_done"))
        assertFalse(prefs.contains("custom_hack_key"))
    }

    @Test
    fun settingsOnlyChange_makesDataHashDiffer_andRestoreThenBackupGivesUnchanged() = runBlocking {
        val courseDao = db.courseDao()
        val taskDao = db.taskDao()
        val fakeStore = FakeDriveStore()

        val cId = courseDao.insertCourse(Course(name = "Math", code = "MATH 101", colorHex = "#123456"))
        taskDao.insertTask(TaskItem(courseId = cId, title = "Task", dueDateEpochMillis = 1000L))

        // Initial backup
        val res1 = DriveBackupManager.backUp(context, fakeStore, force = true)
        assertTrue(res1 is DriveBackupResult.Success)
        val hash1 = fakeStore.files.values.first { it.name.startsWith("unitask-backup-") }.props["dataHash"]
        assertNotNull(hash1)

        // Change only one setting
        TimerStore(context).setLastCountdownMinutes(50)

        // Second backup
        val res2 = DriveBackupManager.backUp(context, fakeStore, force = false)
        assertTrue(res2 is DriveBackupResult.Success)
        val hash2 = (res2 as DriveBackupResult.Success).info.slotFileId.let { id ->
            fakeStore.files[id]?.props?.get("dataHash")
        }
        assertNotNull(hash2)
        assertFalse(hash1 == hash2)

        // Restore backup 2
        val restoreRes = DriveBackupManager.restore(context, fakeStore)
        assertTrue(restoreRes is BackupResult.Success)
        assertEquals(50, TimerStore(context).lastCountdownMinutes())

        // Third backup without any change must return Unchanged
        val res3 = DriveBackupManager.backUp(context, fakeStore, force = false)
        assertTrue(res3 is DriveBackupResult.Unchanged)
    }
}
