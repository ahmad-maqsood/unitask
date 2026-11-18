package com.example.ui.screens

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsSearchLocalizedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun everyEntryPointsToItsTexts() {
        allSettingsSearchEntries.forEach { entry ->
            assertNotEquals("${entry.id} has no title text", 0, entry.titleRes)
            assertNotEquals("${entry.id} has no summary text", 0, entry.summaryRes)
            assertNotEquals("${entry.id} has no page name text", 0, entry.pageLabelRes)
        }
    }

    @Test
    fun englishPhoneGetsTheSameTextsAsTheEnglishList() {
        val localized = localizedSettingsSearchEntries(context)
        assertEquals(allSettingsSearchEntries.map { it.title }, localized.map { it.title })
        assertEquals(allSettingsSearchEntries.map { it.summary }, localized.map { it.summary })
        assertEquals(allSettingsSearchEntries.map { it.pageLabel }, localized.map { it.pageLabel })
    }

    @Test
    @Config(qualifiers = "ur")
    fun urduPhoneGetsUrduTextsButEnglishWordsStillFindThem() {
        val localized = localizedSettingsSearchEntries(context)
        val theme = localized.first { it.id == "theme" }

        assertNotEquals("Theme", theme.title)
        assertTrue(matchesSetting(theme, listOf("dark")))
        assertTrue(matchesSetting(theme, listOf("Theme")))
        assertTrue(matchesSetting(theme, listOf(theme.title)))
    }
}
