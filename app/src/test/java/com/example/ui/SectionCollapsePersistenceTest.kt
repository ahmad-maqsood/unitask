package com.example.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.ui.screens.TaskSectionKey
import com.example.ui.screens.rememberSectionCollapse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Checks that collapsed sections are remembered when the screen is built again, which is what
 * happens when the app is closed and opened. Taking the screen out of the composition and putting
 * it back gives the same fresh start, because nothing is kept in memory for it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SectionCollapsePersistenceTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun clearSavedSections() {
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("section_collapse_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    /** One line per section, such as "done:collapsed" or "today:open". Tapping a line toggles it. */
    @Composable
    private fun Sections(storageKey: String, vararg initiallyCollapsed: String) {
        val collapse = rememberSectionCollapse(storageKey, *initiallyCollapsed)
        // A Column, so the lines sit under each other. Without it they would overlap and a tap on
        // one line would land on the line drawn on top of it.
        Column {
            listOf(TaskSectionKey.TODAY, TaskSectionKey.DONE).forEach { key ->
                Text(
                    text = "$key:" + if (collapse.isCollapsed(key)) "collapsed" else "open",
                    modifier = Modifier
                        .testTag("$storageKey-$key")
                        .clickable { collapse.toggle(key) }
                )
            }
        }
    }

    private fun assertSection(storageKey: String, key: String, expected: String) {
        composeRule.onNodeWithTag("$storageKey-$key").assertTextEquals("$key:$expected")
    }

    @Test
    fun startsFromTheDefaultsBeforeAnythingWasChanged() {
        composeRule.setContent { Sections("my_tasks", TaskSectionKey.DONE) }

        assertSection("my_tasks", TaskSectionKey.TODAY, "open")
        assertSection("my_tasks", TaskSectionKey.DONE, "collapsed")
    }

    @Test
    fun collapsedAndExpandedSectionsComeBackAfterTheScreenIsBuiltAgain() {
        var showScreen by mutableStateOf(true)
        composeRule.setContent {
            if (showScreen) Sections("my_tasks", TaskSectionKey.DONE)
        }

        // Collapse Today and open Done: the opposite of the defaults.
        composeRule.onNodeWithTag("my_tasks-today").performClick()
        composeRule.onNodeWithTag("my_tasks-done").performClick()
        assertSection("my_tasks", TaskSectionKey.TODAY, "collapsed")
        assertSection("my_tasks", TaskSectionKey.DONE, "open")

        // Close and open the screen again.
        showScreen = false
        composeRule.waitForIdle()
        showScreen = true
        composeRule.waitForIdle()

        assertSection("my_tasks", TaskSectionKey.TODAY, "collapsed")
        assertSection("my_tasks", TaskSectionKey.DONE, "open")
    }

    @Test
    fun anEmptyChoiceIsRememberedInsteadOfGoingBackToTheDefaults() {
        var showScreen by mutableStateOf(true)
        composeRule.setContent {
            if (showScreen) Sections("my_tasks", TaskSectionKey.DONE)
        }

        // Done starts collapsed; opening it leaves nothing collapsed.
        composeRule.onNodeWithTag("my_tasks-done").performClick()
        showScreen = false
        composeRule.waitForIdle()
        showScreen = true
        composeRule.waitForIdle()

        assertSection("my_tasks", TaskSectionKey.DONE, "open")
    }

    @Test
    fun eachListRemembersItsOwnSections() {
        var showScreen by mutableStateOf(true)
        composeRule.setContent {
            if (showScreen) {
                Column {
                    Sections("my_tasks")
                    Sections("deadlines")
                }
            }
        }

        composeRule.onNodeWithTag("my_tasks-today").performClick()
        showScreen = false
        composeRule.waitForIdle()
        showScreen = true
        composeRule.waitForIdle()

        assertSection("my_tasks", TaskSectionKey.TODAY, "collapsed")
        assertSection("deadlines", TaskSectionKey.TODAY, "open")
    }
}
