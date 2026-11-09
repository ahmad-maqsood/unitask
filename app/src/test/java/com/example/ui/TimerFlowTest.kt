package com.example.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h1600dp")
class TimerFlowTest {

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
    fun startShowsPause_pauseShowsResume_resetReturnsToStartAndTimeZero() {
        // Initially in idle stopwatch mode: Start button is shown
        composeRule.onNodeWithTag("timer_start_button").assertIsDisplayed()
        composeRule.onNodeWithTag("timer_time_text").assertTextEquals("00:00")

        // Start shows Pause
        composeRule.onNodeWithTag("timer_start_button").performClick()
        composeRule.onNodeWithTag("timer_pause_button").assertIsDisplayed()

        currentTime += 5_000L
        // Pause shows Resume and Reset
        composeRule.onNodeWithTag("timer_pause_button").performClick()
        composeRule.onNodeWithTag("timer_resume_button").assertIsDisplayed()
        composeRule.onNodeWithTag("timer_reset_button").assertIsDisplayed()

        // Reset returns to Start and time to 00:00
        composeRule.onNodeWithTag("timer_reset_button").performClick()
        composeRule.onNodeWithTag("timer_start_button").assertIsDisplayed()
        composeRule.onNodeWithTag("timer_time_text").assertTextEquals("00:00")
    }

    @Test
    fun switchingToCountdownShowsPresetsAndDefaultLength() {
        composeRule.onNodeWithTag("timer_mode_countdown").performClick()

        // Presets are visible
        composeRule.onNodeWithTag("timer_preset_15").assertIsDisplayed()
        composeRule.onNodeWithTag("timer_preset_25").assertIsDisplayed()
        composeRule.onNodeWithTag("timer_preset_45").assertIsDisplayed()
        composeRule.onNodeWithTag("timer_preset_60").assertIsDisplayed()
        composeRule.onNodeWithTag("timer_preset_custom").assertIsDisplayed()

        // Default 25 min shows 25:00
        composeRule.onNodeWithTag("timer_time_text", useUnmergedTree = true).assertTextEquals("25:00")
    }

    @Test
    fun modeSwitchAndPresetsAreDisabledWhileRunning() {
        composeRule.onNodeWithTag("timer_mode_countdown").performClick()
        composeRule.onNodeWithTag("timer_start_button").performClick()

        // Mode switch buttons and preset chips disabled while running
        composeRule.onNodeWithTag("timer_mode_stopwatch").assertIsNotEnabled()
        composeRule.onNodeWithTag("timer_mode_countdown").assertIsNotEnabled()
        composeRule.onNodeWithTag("timer_preset_15").assertIsNotEnabled()
        composeRule.onNodeWithTag("timer_preset_25").assertIsNotEnabled()
    }

    @Test
    fun advancingFakeClockPastLengthAndCallingMarkFinishedIfDueShowsTimeIsUpAndZero() {
        composeRule.onNodeWithTag("timer_mode_countdown").performClick()
        composeRule.onNodeWithTag("timer_start_button").performClick()

        // Advance fake clock past the 25 min (1_500_000 ms) duration
        currentTime += 1_500_000L + 1_000L
        controller.markFinishedIfDue()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("timer_time_text").assertTextEquals("00:00")
        composeRule.onNodeWithText("Time is up").assertIsDisplayed()
    }

    @Test
    fun fullscreenModeHidesModeSwitchAndPresetsAndShowsExitButton() {
        // Switch to countdown mode so presets are initially displayed
        composeRule.onNodeWithTag("timer_mode_countdown").performClick()
        composeRule.onNodeWithTag("timer_preset_15").assertIsDisplayed()
        composeRule.onNodeWithTag("timer_fullscreen_button").assertIsDisplayed()

        // Enter fullscreen mode
        composeRule.onNodeWithTag("timer_fullscreen_button").performClick()

        // Mode switch, presets, and fullscreen toggle button should no longer exist in the composition
        composeRule.onNodeWithTag("timer_mode_stopwatch").assertDoesNotExist()
        composeRule.onNodeWithTag("timer_mode_countdown").assertDoesNotExist()
        composeRule.onNodeWithTag("timer_preset_15").assertDoesNotExist()
        composeRule.onNodeWithTag("timer_fullscreen_button").assertDoesNotExist()

        // Exit fullscreen button and clock are displayed
        composeRule.onNodeWithTag("timer_exit_fullscreen_button").assertIsDisplayed()
        composeRule.onNodeWithTag("timer_time_text").assertIsDisplayed()

        // Tapping exit fullscreen button restores normal mode
        composeRule.onNodeWithTag("timer_exit_fullscreen_button").performClick()

        composeRule.onNodeWithTag("timer_exit_fullscreen_button").assertDoesNotExist()
        composeRule.onNodeWithTag("timer_fullscreen_button").assertIsDisplayed()
        composeRule.onNodeWithTag("timer_mode_stopwatch").assertIsDisplayed()
        composeRule.onNodeWithTag("timer_mode_countdown").assertIsDisplayed()
        composeRule.onNodeWithTag("timer_preset_15").assertIsDisplayed()
        composeRule.onNodeWithTag("timer_time_text", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun controlsWorkInFullscreenMode() {
        // Enter fullscreen mode
        composeRule.onNodeWithTag("timer_fullscreen_button").performClick()
        composeRule.onNodeWithTag("timer_exit_fullscreen_button").assertIsDisplayed()

        // Start shows Pause
        composeRule.onNodeWithTag("timer_start_button").performClick()
        composeRule.onNodeWithTag("timer_pause_button").assertIsDisplayed()

        currentTime += 5_000L
        // Pause shows Resume and Reset
        composeRule.onNodeWithTag("timer_pause_button").performClick()
        composeRule.onNodeWithTag("timer_resume_button").assertIsDisplayed()
        composeRule.onNodeWithTag("timer_reset_button").assertIsDisplayed()

        // Resume works
        composeRule.onNodeWithTag("timer_resume_button").performClick()
        composeRule.onNodeWithTag("timer_pause_button").assertIsDisplayed()
        
        currentTime += 5_000L
        // Pause again and Reset
        composeRule.onNodeWithTag("timer_pause_button").performClick()
        composeRule.onNodeWithTag("timer_reset_button").performClick()

        // Reset returns to Start and time to 00:00
        composeRule.onNodeWithTag("timer_start_button").assertIsDisplayed()
        composeRule.onNodeWithTag("timer_time_text").assertTextEquals("00:00")
    }
    
    @Test
    fun keepScreenOnToggleInFullscreenSwitchesAndIsRemembered() {
        composeRule.onNodeWithTag("timer_fullscreen_button").performClick()

        composeRule.onNodeWithTag("timer_keep_awake_button")
            .assertContentDescriptionEquals("Keep screen on: on")

        composeRule.onNodeWithTag("timer_keep_awake_button").performClick()
        composeRule.onNodeWithTag("timer_keep_awake_button")
            .assertContentDescriptionEquals("Keep screen on: off")
        org.junit.Assert.assertFalse(controller.keepScreenOnInFullscreen())
    }
}
