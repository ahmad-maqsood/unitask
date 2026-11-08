package com.example.ui

import com.example.ui.screens.buildToolsList
import com.example.util.FeatureGuide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolsListTest {

    @Test
    fun testToolsListContainsRecentlyDeletedTile() {
        var notesClicked = false
        var timerClicked = false
        var recentlyDeletedClicked = false

        val tools = buildToolsList(
            onOpenNotes = { notesClicked = true },
            onOpenTimer = { timerClicked = true },
            onOpenRecentlyDeleted = { recentlyDeletedClicked = true }
        )

        assertEquals(3, tools.size)

        val recentlyDeleted = tools.find { it.label == "Recently deleted" }
        assertNotNull(recentlyDeleted)
        assertEquals("Restore tasks and notes", recentlyDeleted?.description)
        assertEquals("tools_tile_recently_deleted", recentlyDeleted?.testTag)

        recentlyDeleted?.onClick?.invoke()
        assertTrue(recentlyDeletedClicked)
    }

    @Test
    fun testFeatureGuideContainsRecentlyDeleted() {
        val feature = FeatureGuide.all.find { it.title == "Recently deleted" }
        assertNotNull(feature)
        assertTrue(feature?.howTo?.contains("Recently deleted") == true)
        assertTrue(feature?.howTo?.contains("30 days") == true)
    }
}
