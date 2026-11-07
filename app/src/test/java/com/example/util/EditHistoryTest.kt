package com.example.util

import com.example.util.markdown.EditHistory
import com.example.util.markdown.EditorText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditHistoryTest {

    @Test
    fun checkpointAndUndoRestoresPreviousState() {
        val history = EditHistory()
        assertFalse(history.canUndo)
        assertFalse(history.canRedo)

        val stateA = EditorText("Hello", 5, 5)
        val stateB = EditorText("Hello world", 11, 11)

        history.checkpoint(stateA)
        assertTrue(history.canUndo)
        assertFalse(history.canRedo)

        val undone = history.undo(current = stateB)
        assertEquals(stateA, undone)
        assertFalse(history.canUndo)
        assertTrue(history.canRedo)
    }

    @Test
    fun redoRestoresFutureStateAfterUndo() {
        val history = EditHistory()
        val stateA = EditorText("A", 1, 1)
        val stateB = EditorText("AB", 2, 2)

        history.checkpoint(stateA)
        val undone = history.undo(current = stateB)
        assertEquals(stateA, undone)

        val redone = history.redo(current = stateA)
        assertEquals(stateB, redone)
        assertTrue(history.canUndo)
        assertFalse(history.canRedo)
    }

    @Test
    fun duplicateCheckpointIsIgnored() {
        val history = EditHistory()
        val stateA = EditorText("Same", 4, 4)
        val stateB = EditorText("Different", 9, 9)

        history.checkpoint(stateA)
        history.checkpoint(stateA)

        val firstUndo = history.undo(current = stateB)
        assertEquals(stateA, firstUndo)

        val secondUndo = history.undo(current = stateA)
        assertNull(secondUndo)
        assertFalse(history.canUndo)
    }

    @Test
    fun redoStackIsClearedByNewCheckpoint() {
        val history = EditHistory()
        val stateA = EditorText("Initial", 7, 7)
        val stateB = EditorText("Second", 6, 6)
        val stateC = EditorText("Branch", 6, 6)

        history.checkpoint(stateA)
        val undone = history.undo(current = stateB)
        assertEquals(stateA, undone)
        assertTrue(history.canRedo)

        // New edit checkpoint clears redo stack
        history.checkpoint(stateC)
        assertFalse(history.canRedo)
        assertNull(history.redo(current = stateC))
    }

    @Test
    fun historyDropsOldestSnapshotsBeyondLimit() {
        val history = EditHistory(limit = 3)
        val state1 = EditorText("One", 3, 3)
        val state2 = EditorText("Two", 3, 3)
        val state3 = EditorText("Three", 5, 5)
        val state4 = EditorText("Four", 4, 4)
        val state5 = EditorText("Five", 4, 4)

        history.checkpoint(state1)
        history.checkpoint(state2)
        history.checkpoint(state3)
        history.checkpoint(state4) // state1 should be dropped beyond limit 3

        assertEquals(state4, history.undo(current = state5))
        assertEquals(state3, history.undo(current = state4))
        assertEquals(state2, history.undo(current = state3))
        assertNull(history.undo(current = state2))
    }

    @Test
    fun clearResetsBothUndoAndRedoStacks() {
        val history = EditHistory()
        val stateA = EditorText("A", 1, 1)
        val stateB = EditorText("B", 1, 1)

        history.checkpoint(stateA)
        history.undo(current = stateB)
        assertTrue(history.canRedo)

        history.clear()
        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
        assertNull(history.undo(stateA))
        assertNull(history.redo(stateB))
    }

    @Test
    fun undoAndRedoOnEmptyHistoryReturnNull() {
        val history = EditHistory()
        val state = EditorText("Text", 4, 4)
        assertNull(history.undo(state))
        assertNull(history.redo(state))
    }

    @Test
    fun historyDropsOldestSnapshotsBeyondCharacterBudget() {
        val history = EditHistory(limit = 100, maxTotalChars = 100)
        val state1 = EditorText("A".repeat(40), 40, 40)
        val state2 = EditorText("B".repeat(40), 40, 40)
        val state3 = EditorText("C".repeat(40), 40, 40)
        val state4 = EditorText("D".repeat(40), 40, 40)

        history.checkpoint(state1)
        history.checkpoint(state2)
        assertEquals(80, history.totalChars)

        // Adding state3 brings total to 120, dropping state1 to stay <= 100
        history.checkpoint(state3)
        assertEquals(80, history.totalChars)

        // Adding state4 brings total to 120, dropping state2 to stay <= 100
        history.checkpoint(state4)
        assertEquals(80, history.totalChars)

        // Oldest (state1, state2) were dropped, newest (state3, state4) are kept
        val current = EditorText("E".repeat(40), 40, 40)
        assertEquals(state4, history.undo(current = current))
        assertEquals(state3, history.undo(current = state4))
        assertNull(history.undo(current = state3))
        assertFalse(history.canUndo)
    }

    @Test
    fun lastSnapshotIsNeverDroppedByBudgetAlone() {
        val history = EditHistory(limit = 100, maxTotalChars = 100)
        val singleOversizedSnapshot = EditorText("X".repeat(150), 150, 150)

        history.checkpoint(singleOversizedSnapshot)
        assertEquals(150, history.totalChars)
        assertTrue(history.canUndo)

        val current = EditorText("Y", 1, 1)
        val undone = history.undo(current = current)
        assertEquals(singleOversizedSnapshot, undone)
    }

    @Test
    fun clearResetsTotalChars() {
        val history = EditHistory(limit = 100, maxTotalChars = 100)
        val state1 = EditorText("1".repeat(40), 40, 40)
        val state2 = EditorText("2".repeat(40), 40, 40)

        history.checkpoint(state1)
        history.checkpoint(state2)
        assertEquals(80, history.totalChars)

        history.clear()
        assertEquals(0, history.totalChars)
        assertFalse(history.canUndo)
        assertFalse(history.canRedo)

        // Verifying that after clear, we can use the full budget again without premature drops
        history.checkpoint(state1)
        history.checkpoint(state2)
        assertEquals(80, history.totalChars)
        val current = EditorText("3".repeat(40), 40, 40)
        assertEquals(state2, history.undo(current))
        assertEquals(state1, history.undo(state2))
    }

    @Test
    fun redoDropsOldestUndoSnapshotWhenOverBudget() {
        val history = EditHistory(limit = 100, maxTotalChars = 100)
        val state1 = EditorText("1".repeat(40), 40, 40)
        val state2 = EditorText("2".repeat(40), 40, 40)

        history.checkpoint(state1)
        history.checkpoint(state2)

        val undone = history.undo(current = EditorText("3".repeat(40), 40, 40))
        assertEquals(state2, undone)

        // Redoing with a large current state pushes total over budget, dropping state1
        val largeCurrent = EditorText("X".repeat(70), 70, 70)
        val redone = history.redo(current = largeCurrent)
        assertEquals(EditorText("3".repeat(40), 40, 40), redone)

        // Only largeCurrent remains in undoStack; state1 was dropped
        val undoAfterRedo = history.undo(current = redone!!)
        assertEquals(largeCurrent, undoAfterRedo)
        assertNull(history.undo(current = largeCurrent))
    }
}

