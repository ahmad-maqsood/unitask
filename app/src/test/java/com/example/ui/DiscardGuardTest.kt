package com.example.ui

import androidx.compose.runtime.mutableStateOf
import com.example.ui.screens.DiscardGuard
import com.example.ui.screens.changeSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The "Discard changes?" question must appear only when the person actually changed something. */
class DiscardGuardTest {

    private fun guardWith(start: String, onClose: () -> Unit): Pair<DiscardGuard, androidx.compose.runtime.MutableState<Boolean>> {
        val asking = mutableStateOf(false)
        val guard = DiscardGuard(start, asking)
        guard.onClose = onClose
        return guard to asking
    }

    @Test
    fun closesAtOnceWhenNothingChanged() {
        var closed = 0
        val (guard, asking) = guardWith(changeSnapshot("", 12, 0)) { closed++ }
        guard.snapshot = changeSnapshot("", 12, 0)

        guard.request()

        assertEquals(1, closed)
        assertFalse(asking.value)
    }

    @Test
    fun asksInsteadOfClosingWhenSomethingChanged() {
        var closed = 0
        val (guard, asking) = guardWith(changeSnapshot("", 12, 0)) { closed++ }
        guard.snapshot = changeSnapshot("Read chapter 3", 12, 0)

        guard.request()

        assertEquals(0, closed)
        assertTrue(asking.value)
    }

    @Test
    fun changingBackToTheStartingValuesCountsAsNoChange() {
        var closed = 0
        val (guard, asking) = guardWith(changeSnapshot("Quiz", 9, 30)) { closed++ }
        guard.snapshot = changeSnapshot("Quiz 2", 9, 30)
        guard.snapshot = changeSnapshot("Quiz", 9, 30)

        guard.request()

        assertEquals(1, closed)
        assertFalse(asking.value)
    }

    @Test
    fun differentFieldValuesNeverShareASnapshot() {
        // Moving text between neighbouring fields must still count as a change.
        assertNotEquals(changeSnapshot("ab", "c"), changeSnapshot("a", "bc"))
        assertNotEquals(changeSnapshot(true, 5), changeSnapshot(false, 5))
    }
}
