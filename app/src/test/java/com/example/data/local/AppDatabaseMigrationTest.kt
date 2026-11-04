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
        } finally {
            db.close()
        }
    }
}
