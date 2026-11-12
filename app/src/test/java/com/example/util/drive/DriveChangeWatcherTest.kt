package com.example.util.drive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveChangeWatcherTest {

    @Test
    fun backedUpSettingKeys_count() {
        for (key in listOf(
            "theme_mode", "theme_accent", "week_a_anchor_millis", "sound_choice",
            "daily_summary_enabled", "daily_summary_hour", "daily_summary_minute",
            "class_start_notifications_enabled"
        )) {
            assertTrue(key, DriveChangeWatcher.isBackedUpSettingKey("unitask_settings", key))
        }
        assertTrue(DriveChangeWatcher.isBackedUpSettingKey("notes_prefs", "sort"))
    }

    @Test
    fun valuesTheAppRewritesOnItsOwn_doNotCount() {
        assertFalse(DriveChangeWatcher.isBackedUpSettingKey("unitask_settings", "class_start_scheduled_slot_ids"))
        assertFalse(DriveChangeWatcher.isBackedUpSettingKey("unitask_settings", "first_reminder_saved"))
        assertFalse(DriveChangeWatcher.isBackedUpSettingKey("unitask_settings", "notification_permission_prompted"))
        assertFalse(DriveChangeWatcher.isBackedUpSettingKey("notes_prefs", "last_opened_note"))
    }

    @Test
    fun clearedFile_counts_andUnknownFile_doesNot() {
        assertTrue(DriveChangeWatcher.isBackedUpSettingKey("unitask_settings", null))
        assertFalse(DriveChangeWatcher.isBackedUpSettingKey("some_other_file", "theme_mode"))
    }

    @Test
    fun everyKeyThatSettingsSnapshotWritesIsWatched() {
        // SettingsSnapshot.capture reads these through the files above. If a setting is added there,
        // add its key to DriveChangeWatcher.SETTINGS_KEYS too.
        val watched = DriveChangeWatcher.SETTINGS_KEYS.values.flatten().toSet()
        assertTrue(watched.containsAll(listOf("theme_mode", "sound_choice", "sort", "daily_summary_hour")))
    }
}
