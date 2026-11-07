package com.example.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.ui.screens.ToolsButton
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ToolsButtonTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun whenTimerInactive_hasToolsContentDescription_andClickCallsOnClick() {
        var clicked = false
        composeRule.setContent {
            MyApplicationTheme {
                ToolsButton(
                    timerActive = false,
                    onClick = { clicked = true }
                )
            }
        }

        composeRule.onNodeWithTag("top_bar_tools_button")
            .assertContentDescriptionEquals("Tools")
            .performClick()

        assertEquals(true, clicked)
    }

    @Test
    fun whenTimerActive_hasTimerActiveContentDescription_andClickCallsOnClick() {
        var clicked = false
        composeRule.setContent {
            MyApplicationTheme {
                ToolsButton(
                    timerActive = true,
                    onClick = { clicked = true }
                )
            }
        }

        composeRule.onNodeWithTag("top_bar_tools_button")
            .assertContentDescriptionEquals("Tools, timer active")
            .performClick()

        assertEquals(true, clicked)
    }

    @Test
    fun togglingTimerActiveUpdatesContentDescription() {
        val timerActive = mutableStateOf(false)
        composeRule.setContent {
            MyApplicationTheme {
                ToolsButton(
                    timerActive = timerActive.value,
                    onClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("top_bar_tools_button")
            .assertContentDescriptionEquals("Tools")

        timerActive.value = true
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("top_bar_tools_button")
            .assertContentDescriptionEquals("Tools, timer active")
    }
}
