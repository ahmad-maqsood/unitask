package com.example.data.local

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards against wiping a user's data by mistake. Room deletes the whole database when it has no
 * migration for an upgrade and the old version is on the "destructive" list. These checks fail the
 * build before that can reach users:
 *  - the migrations form one unbroken chain up to the newest database version,
 *  - the newest version has an exported schema file (so the app was built with it),
 *  - only never-released versions are allowed to be reset.
 */
class AppDatabaseSafetyTest {

    private val migrations = AppDatabase.ALL_MIGRATIONS.sortedBy { it.startVersion }

    private fun schemaVersions(): List<Int> {
        val dir = listOf(
            File("schemas/com.example.data.local.AppDatabase"),
            File("app/schemas/com.example.data.local.AppDatabase")
        ).firstOrNull { it.isDirectory }
        assertTrue("could not find the exported Room schema folder", dir != null)
        return dir!!.listFiles { f -> f.extension == "json" }!!
            .mapNotNull { it.nameWithoutExtension.toIntOrNull() }
            .sorted()
    }

    @Test
    fun migrationsFormAnUnbrokenChain() {
        for ((a, b) in migrations.zipWithNext()) {
            assertEquals(
                "no migration goes from version ${a.endVersion} (after ${a.startVersion} to ${a.endVersion})",
                a.endVersion, b.startVersion
            )
        }
        for (m in migrations) {
            assertEquals("migration ${m.startVersion} to ${m.endVersion} must move up by exactly one version", 1, m.endVersion - m.startVersion)
        }
    }

    @Test
    fun migrationsReachTheNewestSchemaVersion() {
        val newest = schemaVersions().last()
        assertEquals(
            "the newest exported schema is version $newest but the migrations stop at ${migrations.last().endVersion}. " +
                "Add a migration to AppDatabase.ALL_MIGRATIONS for the new version.",
            newest, migrations.last().endVersion
        )
    }

    @Test
    fun everySchemaVersionFromTheFirstExportedOneExists() {
        val versions = schemaVersions()
        assertEquals("a schema file is missing between ${versions.first()} and ${versions.last()}",
            (versions.first()..versions.last()).toList(), versions)
    }

    @Test
    fun onlyNeverReleasedVersionsCanBeWiped() {
        val firstMigrationFrom = migrations.first().startVersion
        for (v in AppDatabase.PRE_RELEASE_VERSIONS) {
            assertTrue(
                "version $v is allowed to be wiped but a migration starts at $firstMigrationFrom, so released data could be lost",
                v < firstMigrationFrom
            )
        }
    }
}
