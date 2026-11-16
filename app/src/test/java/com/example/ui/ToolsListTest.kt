package com.example.ui

import com.example.R
import com.example.ui.screens.buildToolsList
import com.example.util.FeatureGuide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolsListTest {

    @Test
    fun testToolsListContainsRecentlyDeletedAndSettingsTiles() {
        var notesClicked = false
        var timerClicked = false
        var recentlyDeletedClicked = false
        var settingsClicked = false

        val tools = buildToolsList(
            onOpenNotes = { notesClicked = true },
            onOpenTimer = { timerClicked = true },
            onOpenRecentlyDeleted = { recentlyDeletedClicked = true },
            onOpenSettings = { settingsClicked = true }
        )

        assertEquals(6, tools.size)

        val recentlyDeleted = tools.find { it.labelRes == R.string.tools_recently_deleted }
        assertNotNull(recentlyDeleted)
        assertEquals(R.string.tools_recently_deleted_description, recentlyDeleted?.descriptionRes)
        assertEquals("tools_tile_recently_deleted", recentlyDeleted?.testTag)

        recentlyDeleted?.onClick?.invoke()
        assertTrue(recentlyDeletedClicked)

        val settings = tools.find { it.labelRes == R.string.tools_settings }
        assertNotNull(settings)
        assertEquals(R.string.tools_settings_description, settings?.descriptionRes)
        assertEquals("tools_tile_settings", settings?.testTag)

        settings?.onClick?.invoke()
        assertTrue(settingsClicked)

        // Search has its own top bar icon, so it is no longer a tile.
        assertEquals(null, tools.find { it.testTag == "tools_tile_search" })
    }

    @Test
    fun testToolsListHasHelpTileThatOpensTheGuide() {
        var helpClicked = false
        val tools = buildToolsList({}, {}, {}, {}, onOpenHelp = { helpClicked = true })
        val help = tools.find { it.labelRes == R.string.tools_help }
        assertNotNull(help)
        assertEquals("tools_tile_help", help?.testTag)
        help?.onClick?.invoke()
        assertTrue(helpClicked)
    }

    @Test
    fun testBeginnerTopicsComeFirstInTheGuide() {
        val firstFive = FeatureGuide.all.take(5).map { it.title }
        assertEquals(
            listOf("Courses", "Deadlines & Urgency", "Reminders", "Timetable & Schedule", "Notes"),
            firstFive
        )
    }

    @Test
    fun testSettingsTileShowsBadgeOnlyWhenUpdateAvailable() {
        val without = buildToolsList({}, {}, {}, {})
        val with = buildToolsList({}, {}, {}, {}, updateAvailable = true)
        assertEquals(false, without.first { it.labelRes == R.string.tools_settings }.showBadge)
        assertEquals(true, with.first { it.labelRes == R.string.tools_settings }.showBadge)
    }

    @Test
    fun testFeatureGuideContainsRecentlyDeleted() {
        val feature = FeatureGuide.all.find { it.title == "Recently deleted" }
        assertNotNull(feature)
        assertTrue(feature?.howTo?.contains("Recently deleted") == true)
        assertTrue(feature?.howTo?.contains("30 days") == true)
    }

    @Test
    fun testFeatureGuideContainsSearch() {
        val feature = FeatureGuide.all.find { it.title == "Search" }
        assertNotNull(feature)
        assertEquals(76, feature?.sortOrder)
        assertTrue(feature?.howTo?.contains("Search") == true)
        assertTrue(feature?.howTo?.contains("Include archived") == true)
    }
}
