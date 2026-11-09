package com.example.ui.screens

import com.example.util.AppFeature
import com.example.util.FeatureGuide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideSearchTest {

    private val guides = listOf(
        AppFeature("Repeating Tasks", "Tasks that come back daily or weekly.", "repeat", 1, "1. Open the Repeat option."),
        AppFeature("Timer and Countdown", "Track study time.", "timer", 2, "1. Tap Start. Repeat as needed."),
        AppFeature("Courses", "Keep your subjects in one place.", "school", 3, "1. Open the Courses tab.")
    )

    private fun search(query: String, include: Boolean = true): SearchResults = searchEverything(
        query = query,
        tasks = emptyList(),
        notes = emptyList(),
        courses = emptyList(),
        guideEntries = if (include) guides else emptyList()
    )

    @Test
    fun guidesAreNotSearchedWhenNoneAreGiven() {
        val results = search("repeating", include = false)
        assertTrue(results.guides.isEmpty())
        assertTrue(results.isEmpty)
    }

    @Test
    fun findsGuideByTitleDescriptionAndSteps() {
        assertEquals(listOf("Courses"), search("courses").guides.map { it.title })
        assertEquals(listOf("Timer and Countdown"), search("study time").guides.map { it.title })
        assertEquals(listOf("Courses"), search("tab").guides.map { it.title })
    }

    @Test
    fun everyWordMustMatch() {
        assertEquals(listOf("Repeating Tasks"), search("repeating daily").guides.map { it.title })
        assertTrue(search("repeating study").guides.isEmpty())
    }

    @Test
    fun matchingIsCaseInsensitive() {
        assertEquals(listOf("Timer and Countdown"), search("COUNTDOWN").guides.map { it.title })
    }

    @Test
    fun titleMatchesComeBeforeStepMatches() {
        // "repeat" is in the title of one guide and only in the steps of another.
        val titles = search("repeat").guides.map { it.title }
        assertEquals(listOf("Repeating Tasks", "Timer and Countdown"), titles)
    }

    @Test
    fun guidesCountInTotals() {
        val results = search("courses")
        assertFalse(results.isEmpty)
        assertEquals(results.guides.size, results.totalCount)
    }

    @Test
    fun blankQueryFindsNothing() {
        assertTrue(search("   ").isEmpty)
    }

    @Test
    fun clickOnGuideOpensThatGuide() {
        val target = resolveGuideClickTarget(guides[1]) as SearchNavigationTarget.OpenGuide
        assertEquals("Timer and Countdown", target.featureTitle)
    }

    @Test
    fun realGuideListIsSearchable() {
        val results = searchEverything(
            query = "snooze",
            tasks = emptyList(),
            notes = emptyList(),
            courses = emptyList(),
            guideEntries = FeatureGuide.all
        )
        assertEquals("Snooze a Reminder", results.guides.first().title)
    }

    @Test
    fun everyGuideHasAnIconThatCanBeDrawn() {
        FeatureGuide.all.forEach { feature ->
            assertNotNull("No icon named '${feature.icon}' for ${feature.title}", featureIcon(feature.icon))
        }
    }

    @Test
    fun guideTitlesAreUnique() {
        val titles = FeatureGuide.all.map { it.title }
        assertEquals(titles.size, titles.toSet().size)
    }

    @Test
    fun guidesDoNotAllShareTheSameFewIcons() {
        val iconCounts = FeatureGuide.all.groupingBy { it.icon }.eachCount()
        // Icons are meant to tell features apart, so no icon is used for most of the list.
        assertTrue(iconCounts.values.max() <= 4)
    }
}
