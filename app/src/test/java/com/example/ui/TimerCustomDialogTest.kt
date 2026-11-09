package com.example.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.ui.screens.TimerScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.util.timer.CountdownAlarmHook
import com.example.util.timer.TimerController
import com.example.util.timer.TimerMode
import com.example.util.timer.TimerState
import com.example.util.timer.TimerStore
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for the custom countdown length dialog.
 *
 * These live in their own class on purpose: they use the default Robolectric screen size.
 * On the very tall screen used by TimerFlowTest, opening this dialog never goes idle and the
 * test run eventually runs out of memory.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TimerCustomDialogTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var currentTime = 10_000L
    private lateinit var store: TimerStore
    private lateinit var controller: TimerController

    private val fakeAlarm = object : CountdownAlarmHook {
        override fun schedule(endAtMillis: Long) {}
        override fun cancel() {}
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        store = TimerStore(context)
        store.save(TimerState(mode = TimerMode.STOPWATCH))
        currentTime = 10_000L

        controller = TimerController(
            store = store,
            clock = { currentTime },
            alarm = fakeAlarm
        )
        TimerController.setInstanceForTests(controller)

        composeRule.setContent {
            MyApplicationTheme {
                TimerScreen(
                    onClose = {},
                    controller = controller
                )
            }
        }
    }

    @After
    fun tearDown() {
        TimerController.setInstanceForTests(null)
    }

    @Test
    fun tappingClockWhileIdleInCountdownModeOpensCustomDialog() {
        composeRule.onNodeWithTag("timer_mode_countdown").performClick()
        composeRule.onNodeWithTag("timer_clock_edit").performClick()
        composeRule.onNodeWithTag("timer_custom_input").assertIsDisplayed()
        composeRule.onNodeWithTag("timer_custom_cancel_button").performClick()
    }

    @Test
    fun customLengthOutOfBoundsShowsErrorAndIsNotApplied() {
        composeRule.onNodeWithTag("timer_mode_countdown").performClick()
        // The clock sits at the top of the screen, so it can be tapped without scrolling.
        composeRule.onNodeWithTag("timer_clock_edit").performClick()

        // Custom duration dialog is open
        composeRule.onNodeWithTag("timer_custom_input").performTextClearance()
        composeRule.onNodeWithTag("timer_custom_input").performTextInput("0")
        composeRule.onNodeWithText("Choose between 1 minute and 10 hours").assertIsDisplayed()
        composeRule.onNodeWithTag("timer_custom_set_button").assertIsNotEnabled()

        composeRule.onNodeWithTag("timer_custom_hours_input").performTextClearance()
        composeRule.onNodeWithTag("timer_custom_hours_input").performTextInput("11")
        composeRule.onNodeWithText("Choose between 1 minute and 10 hours").assertIsDisplayed()
        composeRule.onNodeWithTag("timer_custom_set_button").assertIsNotEnabled()

        composeRule.onNodeWithTag("timer_custom_cancel_button").performClick()
        // Length remains default 25:00
        composeRule.onNodeWithTag("timer_time_text", useUnmergedTree = true).assertTextEquals("25:00")
    }

    @Test
    fun customDurationQuickAddAndSetUpdatesLengthAndCustomChipLabel() {
        composeRule.onNodeWithTag("timer_mode_countdown").performClick()
        composeRule.onNodeWithTag("timer_clock_edit").performClick()

        composeRule.onNodeWithTag("timer_custom_hours_input").performTextClearance()
        composeRule.onNodeWithTag("timer_custom_input").performTextClearance()
        // The quick-add chips scroll sideways inside the dialog, so scroll them into view first.
        composeRule.onNodeWithTag("timer_quick_add_60").performScrollTo().performClick()
        composeRule.onNodeWithTag("timer_quick_add_30").performScrollTo().performClick()
        composeRule.onNodeWithText("Total: 1 h 30 min").assertIsDisplayed()

        composeRule.onNodeWithTag("timer_custom_set_button").performClick()
        composeRule.onNodeWithTag("timer_time_text", useUnmergedTree = true).assertTextEquals("1:30:00")
        // The Custom chip label is below the visible area on the default screen, so check it exists.
        composeRule.onNodeWithText("1 h 30 min").assertExists()
    }
}