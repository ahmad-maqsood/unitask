package com.example.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {

    @Test
    fun blankQueryReturnsNothing() {
        assertTrue(searchSettings("").isEmpty())
        assertTrue(searchSettings("   ").isEmpty())
    }

    @Test
    fun findsSettingByTitle() {
        val results = searchSettings("notification sound")
        assertEquals(listOf("sound"), results.map { it.id })
        assertEquals(SettingsPage.Notifications, results.first().page)
    }

    @Test
    fun findsSettingByKeyword() {
        val results = searchSettings("dark")
        assertTrue(results.any { it.id == "theme" })
        assertEquals(SettingsPage.General, results.first { it.id == "theme" }.page)
    }

    @Test
    fun matchingIsCaseInsensitive() {
        assertTrue(searchSettings("BATTERY").any { it.id == "background_alerts" })
    }

    @Test
    fun everyWordMustMatch() {
        val results = searchSettings("daily time")
        assertTrue(results.any { it.id == "summary_time" })
        assertFalse(results.any { it.id == "theme" })
    }

    @Test
    fun accountRowOpensSignInSheetNotAPage() {
        val account = searchSettings("sign in").first { it.id == "account" }
        assertTrue(account.opensAccount)
        assertNull(account.page)
        val target = resolveSettingClickTarget(account) as SearchNavigationTarget.OpenSetting
        assertTrue(target.opensAccount)
    }

    @Test
    fun clickOnSettingOpensItsPage() {
        val entry = searchSettings("source code").first()
        val target = resolveSettingClickTarget(entry) as SearchNavigationTarget.OpenSetting
        assertEquals(SettingsPage.About, target.page)
        assertFalse(target.opensAccount)
    }

    @Test
    fun entryIdsAreUnique() {
        val ids = allSettingsSearchEntries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun everyPageHasAtLeastOneEntry() {
        SettingsPage.entries.forEach { page ->
            assertTrue("No search entry for $page", allSettingsSearchEntries.any { it.page == page })
        }
    }

    @Test
    fun searchEverythingIncludesSettingsOnlyWhenGiven() {
        val without = searchEverything("battery", emptyList(), emptyList(), emptyList())
        assertTrue(without.isEmpty)

        val with = searchEverything(
            query = "battery",
            tasks = emptyList(),
            notes = emptyList(),
            courses = emptyList(),
            settingsEntries = allSettingsSearchEntries
        )
        assertFalse(with.isEmpty)
        assertEquals(with.settings.size, with.totalCount)
    }
}
