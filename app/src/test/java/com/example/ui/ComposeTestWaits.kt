package com.example.ui

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText

/** How long the tests wait for something that appears after background work. */
internal const val UI_WAIT_TIMEOUT_MILLIS = 10_000L

/**
 * Waits until some text is on screen. Use it after a click whose result appears after background
 * work, instead of checking straight away: the check can run before the screen has changed and
 * fail now and then.
 *
 * @param substring True to match text that only contains [text].
 */
internal fun ComposeTestRule.waitForText(
    text: String,
    substring: Boolean = false,
    timeoutMillis: Long = UI_WAIT_TIMEOUT_MILLIS
) {
    waitUntil(timeoutMillis) {
        onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
    }
}

/** Waits until an element with this test tag is on screen. */
internal fun ComposeTestRule.waitForTag(
    tag: String,
    timeoutMillis: Long = UI_WAIT_TIMEOUT_MILLIS
) {
    waitUntil(timeoutMillis) {
        onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
}
