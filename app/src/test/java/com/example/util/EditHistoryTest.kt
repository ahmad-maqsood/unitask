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
}

