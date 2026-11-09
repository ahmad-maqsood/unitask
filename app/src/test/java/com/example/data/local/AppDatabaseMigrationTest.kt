package com.example.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Builds a database exactly as version 9 left it (with one course and one task, like a real user),
 * then opens it with the app's real migrations. Room checks the migrated tables against the current
 * code when it opens, so a wrong migration fails here instead of crashing users after an update.
 * This checks the 9 to 10 migration (the new repeatAnchorDay column). When a later version is added to
 * AppDatabase.ALL_MIGRATIONS, this same test checks the whole chain from 9 with no change.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppDatabaseMigrationTest {

    private val dbName = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun existingUserDataSurvivesUpgradeToLatestVersion() {
        // 1. A version 9 database holding one course and one task.
        val old = helper.createDatabase(dbName, 9)
        old.execSQL(
            "INSERT INTO courses (id, name, code, instructor, colorHex, location, isSynced, cloudId, isDeleted) " +
                "VALUES (1, 'Algorithms', 'CS201', '', '#4F46E5', '', 0, '', 0)"
        )
        old.execSQL(
            "INSERT INTO tasks (id, courseId, title, description, dueDateEpochMillis, dueTimeHour, dueTimeMinute, " +
                "priority, status, reminderOption, reminderEpochMillis, isSynced, cloudId, isDeleted, createdAt, " +
                "repeatRule, completedAtMillis) " +
                "VALUES (1, 1, 'Lab report', 'old user task', 1700000000000, 12, 0, " +
                "'MEDIUM', 'PENDING', 'NONE', NULL, 0, '', 0, 1700000000000, 'NONE', 0)"
        )
        old.close()

        // 2. Open it the way the app does. Room throws here if the schema does not match the code.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .build()
        try {
            // 3. The old task is still there, unchanged.
            db.openHelper.writableDatabase
                .query("SELECT title, description FROM tasks WHERE id = 1")
                .use { cursor ->
                    assertTrue("the old task is missing after the upgrade", cursor.moveToFirst())
                    assertEquals("Lab report", cursor.getString(0))
                    assertEquals("old user task", cursor.getString(1))
                }

            // 4. The new column exists, and the old task starts at 0 (not set).
            db.openHelper.writableDatabase
                .query("SELECT repeatAnchorDay FROM tasks WHERE id = 1")
                .use { cursor ->
                    assertTrue("repeatAnchorDay is missing after the upgrade", cursor.moveToFirst())
                    assertEquals(0, cursor.getInt(0))
                }

            db.openHelper.writableDatabase
                .query("SELECT deletedAt FROM tasks WHERE id = 1")
                .use { cursor ->
                    assertTrue("deletedAt is missing after the upgrade", cursor.moveToFirst())
                    assertEquals(0L, cursor.getLong(0))
                }

            db.openHelper.writableDatabase
                .query("SELECT archivedSemester FROM courses WHERE id = 1")
                .use { cursor ->
                    assertTrue("archivedSemester is missing after the upgrade", cursor.moveToFirst())
                    assertEquals("", cursor.getString(0))
                }
        } finally {
            db.close()
        }
    }

    @Test
    fun migrating11To12KeepsOldDataAndEnablesNotes() {
        // 1. A version 11 database holding one course and one task.
        val old = helper.createDatabase("migration-test-11-12", 11)
        old.execSQL(
            "INSERT INTO courses (id, name, code, instructor, colorHex, location, isSynced, cloudId, isDeleted) " +
                "VALUES (1, 'Algorithms', 'CS201', '', '#4F46E5', '', 0, '', 0)"
        )
        old.execSQL(
            "INSERT INTO tasks (id, courseId, title, description, dueDateEpochMillis, dueTimeHour, dueTimeMinute, " +
                "priority, status, reminderOption, reminderEpochMillis, isSynced, cloudId, isDeleted, createdAt, " +
                "repeatRule, completedAtMillis, repeatAnchorDay, autoDeleteWhenDone) " +
                "VALUES (1, 1, 'Lab report', 'old user task', 1700000000000, 12, 0, " +
                "'MEDIUM', 'PENDING', 'NONE', NULL, 0, '', 0, 1700000000000, 'NONE', 0, 0, 0)"
        )
        old.close()

        // 2. Open it the way the app does. Room throws here if the schema does not match the code.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.databaseBuilder(context, AppDatabase::class.java, "migration-test-11-12")
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .build()
        try {
            // 3. The old task is still there, unchanged.
            db.openHelper.writableDatabase
                .query("SELECT title, description FROM tasks WHERE id = 1")
                .use { cursor ->
                    assertTrue("the old task is missing after the upgrade", cursor.moveToFirst())
                    assertEquals("Lab report", cursor.getString(0))
                    assertEquals("old user task", cursor.getString(1))
                }

            // 4. The notes table can be written to and read afterwards.
            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO notes (id, title, body, courseId, isPinned, createdAt, updatedAt, isDeleted, deletedAt, isSynced, cloudId, cloudUpdatedAt) " +
                    "VALUES (1, 'Lecture 1', 'Intro to graphs', 1, 0, 1700000000000, 1700000000000, 0, 0, 0, '', 0)"
            )
            db.openHelper.writableDatabase
                .query("SELECT title, body, courseId FROM notes WHERE id = 1")
                .use { cursor ->
                    assertTrue("the note is missing after writing to notes table", cursor.moveToFirst())
                    assertEquals("Lecture 1", cursor.getString(0))
                    assertEquals("Intro to graphs", cursor.getString(1))
                    assertEquals(1L, cursor.getLong(2))
                }
        } finally {
            db.close()
        }
    }

    @Test
    fun migrating12To13KeepsOldDataAndAddsDeletedAt() {
        // 1. A version 12 database holding one course, one task and one lecture slot.
        val old = helper.createDatabase("migration-test-12-13", 12)
        old.execSQL(
            "INSERT INTO courses (id, name, code, instructor, colorHex, location, isSynced, cloudId, isDeleted) " +
                "VALUES (1, 'Algorithms', 'CS201', 'Dr. Smith', '#4F46E5', 'Room 101', 0, '', 0)"
        )
        old.execSQL(
            "INSERT INTO tasks (id, courseId, title, description, dueDateEpochMillis, dueTimeHour, dueTimeMinute, " +
                "priority, status, reminderOption, reminderEpochMillis, isSynced, cloudId, isDeleted, createdAt, " +
                "repeatRule, completedAtMillis, repeatAnchorDay, autoDeleteWhenDone) " +
                "VALUES (1, 1, 'Lab report', 'old user task', 1700000000000, 12, 0, " +
                "'MEDIUM', 'PENDING', 'NONE', NULL, 0, '', 0, 1700000000000, 'NONE', 0, 0, 0)"
        )
        old.execSQL(
            "INSERT INTO lecture_slots (id, courseId, dayOfWeek, startHour, startMinute, endHour, endMinute, " +
                "classroom, instructor, isSynced, cloudId, isDeleted) " +
                "VALUES (1, 1, 1, 9, 0, 10, 30, 'Hall A', 'Dr. Smith', 0, '', 0)"
        )
        old.close()

        // 2. Open it the way the app does. Room throws here if the schema does not match the code.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.databaseBuilder(context, AppDatabase::class.java, "migration-test-12-13")
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .build()
        try {
            // 3. The old task and slot are still there, unchanged.
            db.openHelper.writableDatabase
                .query("SELECT title, description FROM tasks WHERE id = 1")
                .use { cursor ->
                    assertTrue("the old task is missing after the upgrade", cursor.moveToFirst())
                    assertEquals("Lab report", cursor.getString(0))
                    assertEquals("old user task", cursor.getString(1))
                }

            db.openHelper.writableDatabase
                .query("SELECT classroom, instructor FROM lecture_slots WHERE id = 1")
                .use { cursor ->
                    assertTrue("the old slot is missing after the upgrade", cursor.moveToFirst())
                    assertEquals("Hall A", cursor.getString(0))
                    assertEquals("Dr. Smith", cursor.getString(1))
                }

            // 4. The new deletedAt column exists in both tables and defaults to 0.
            db.openHelper.writableDatabase
                .query("SELECT deletedAt FROM tasks WHERE id = 1")
                .use { cursor ->
                    assertTrue("deletedAt is missing from tasks after the upgrade", cursor.moveToFirst())
                    assertEquals(0L, cursor.getLong(0))
                }

            db.openHelper.writableDatabase
                .query("SELECT deletedAt FROM lecture_slots WHERE id = 1")
                .use { cursor ->
                    assertTrue("deletedAt is missing from lecture_slots after the upgrade", cursor.moveToFirst())
                    assertEquals(0L, cursor.getLong(0))
                }
        } finally {
            db.close()
        }
    }

    @Test
    fun migrating13To14KeepsOldDataAndAddsArchivedSemester() {
        // 1. A version 13 database holding one course.
        val old = helper.createDatabase("migration-test-13-14", 13)
        old.execSQL(
            "INSERT INTO courses (id, name, code, instructor, colorHex, location, isSynced, cloudId, isDeleted) " +
                "VALUES (1, 'Algorithms', 'CS201', 'Dr. Smith', '#4F46E5', 'Room 101', 0, '', 0)"
        )
        old.close()

        // 2. Open it the way the app does. Room throws here if schema does not match code.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.databaseBuilder(context, AppDatabase::class.java, "migration-test-13-14")
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .build()
        try {
            // 3. The old course is still there, unchanged.
            db.openHelper.writableDatabase
                .query("SELECT name, code FROM courses WHERE id = 1")
                .use { cursor ->
                    assertTrue("the old course is missing after the upgrade", cursor.moveToFirst())
                    assertEquals("Algorithms", cursor.getString(0))
                    assertEquals("CS201", cursor.getString(1))
                }

            // 4. The new archivedSemester column exists and defaults to ''.
            db.openHelper.writableDatabase
                .query("SELECT archivedSemester FROM courses WHERE id = 1")
                .use { cursor ->
                    assertTrue("archivedSemester is missing from courses after the upgrade", cursor.moveToFirst())
                    assertEquals("", cursor.getString(0))
                }
        } finally {
            db.close()
        }
    }

    @Test
    fun migrating14To15AddsKeepScreenOnOffForExistingNotes() {
        val old = helper.createDatabase("migration-test-14-15", 14)
        old.execSQL(
            "INSERT INTO notes (id, title, body, courseId, isPinned, createdAt, updatedAt, isDeleted, deletedAt, isSynced, cloudId, cloudUpdatedAt) " +
                "VALUES (1, 'Lecture 1', 'Intro to graphs', NULL, 0, 1700000000000, 1700000000000, 0, 0, 0, '', 0)"
        )
        old.close()

        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.databaseBuilder(context, AppDatabase::class.java, "migration-test-14-15")
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .build()
        try {
            db.openHelper.writableDatabase
                .query("SELECT title, body, keepScreenOn FROM notes WHERE id = 1")
                .use { cursor ->
                    assertTrue("the old note is missing after the upgrade", cursor.moveToFirst())
                    assertEquals("Lecture 1", cursor.getString(0))
                    assertEquals("Intro to graphs", cursor.getString(1))
                    assertEquals(0, cursor.getInt(2))
                }
        } finally {
            db.close()
        }
    }
}
