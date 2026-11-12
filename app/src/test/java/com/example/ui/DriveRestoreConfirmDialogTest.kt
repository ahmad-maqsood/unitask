package com.example.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ui.screens.DriveRestoreConfirmDialog
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DriveRestoreConfirmDialogTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun withOneBackup_noOlderChoiceIsShown() {
        composeRule.setContent {
            MyApplicationTheme {
                DriveRestoreConfirmDialog(details = "Today 10:00, 2 courses", onConfirm = {}, onDismiss = {})
            }
        }
        composeRule.onNodeWithTag("drive_restore_older_toggle").assertDoesNotExist()
    }

    @Test
    fun withOlderBackup_toggleSwitchesBetweenLatestAndOlder() {
        composeRule.setContent {
            var useOlder by remember { mutableStateOf(false) }
            MyApplicationTheme {
                DriveRestoreConfirmDialog(
                    details = if (useOlder) "Yesterday 09:00, 1 course" else "Today 10:00, 2 courses",
                    onConfirm = {},
                    onDismiss = {},
                    olderDetails = "Yesterday 09:00, 1 course",
                    useOlder = useOlder,
                    onUseOlderChange = { useOlder = it }
                )
            }
        }

        composeRule.onNodeWithText("Use the older backup instead").assertIsDisplayed()
        composeRule.onNodeWithTag("drive_restore_older_toggle").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Use the latest backup instead").assertIsDisplayed()
    }

    @Test
    fun withOlderBackup_confirmAndToggleCallbacksFire() {
        var toggled: Boolean? = null
        var confirmed = false
        composeRule.setContent {
            MyApplicationTheme {
                DriveRestoreConfirmDialog(
                    details = "Today 10:00, 2 courses",
                    onConfirm = { confirmed = true },
                    onDismiss = {},
                    olderDetails = "Yesterday 09:00, 1 course",
                    useOlder = false,
                    onUseOlderChange = { toggled = it }
                )
            }
        }
        composeRule.onNodeWithTag("drive_restore_older_toggle").performClick()
        composeRule.onNodeWithText("Restore").performClick()
        assertTrue(toggled == true)
        assertTrue(confirmed)
    }
}
