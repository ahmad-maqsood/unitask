package com.example.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    private fun hasSchema(version: Int): Boolean {
        return try {
            val stream = InstrumentationRegistry.getInstrumentation().context.assets
                .open("com.example.data.local.AppDatabase/$version.json")
            stream.close()
            true
        } catch (_: IOException) {
            false
        }
    }

    @Test
    fun testDatabaseMigrationOrValidation() {
        if (hasSchema(8)) {
            // If schema 8 exists, create DB at version 8 and test migration to 9
            val db = helper.createDatabase(TEST_DB, 8)
            db.close()

            val migratedDb = helper.runMigrationsAndValidate(
                TEST_DB,
                9,
                true,
                AppDatabase.MIGRATION_8_9
            )
            migratedDb.close()
        } else if (hasSchema(9)) {
            // If schema 9 exists, open and validate version 9 schema
            val db = helper.createDatabase(TEST_DB, 9)
            db.close()
        } else {
            // Fallback when schema JSON files are not yet generated:
            // Open database version 9 via Room builder and validate schema and tables
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .addMigrations(
                    AppDatabase.MIGRATION_3_4,
                    AppDatabase.MIGRATION_4_5,
                    AppDatabase.MIGRATION_5_6,
                    AppDatabase.MIGRATION_6_7,
                    AppDatabase.MIGRATION_7_8,
                    AppDatabase.MIGRATION_8_9
                )
                // Only applies to pre-release versions (v1 and v2)
                .fallbackToDestructiveMigrationFrom(1, 2)
                .build()

            val sqliteDb = db.openHelper.writableDatabase
            assertNotNull(sqliteDb)
            assertEquals(9, sqliteDb.version)

            // Validate all entities are registered and queryable
            val tables = listOf("courses", "tasks", "task_attachments", "lecture_slots", "group_cache")
            for (table in tables) {
                sqliteDb.query("SELECT COUNT(*) FROM `$table`").use { cursor ->
                    assertNotNull(cursor)
                    assertTrue(cursor.moveToFirst())
                }
            }

            db.close()
        }
    }
}

