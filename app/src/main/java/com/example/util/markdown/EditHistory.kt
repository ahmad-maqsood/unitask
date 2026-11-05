package com.example.util.markdown

class EditHistory(private val limit: Int = 100) {

    private val undoStack = ArrayDeque<EditorText>()
    private val redoStack = ArrayDeque<EditorText>()

    val canUndo: Boolean
        get() = undoStack.isNotEmpty()

    val canRedo: Boolean
        get() = redoStack.isNotEmpty()

    fun checkpoint(state: EditorText) {
        if (undoStack.lastOrNull() == state) {
            return
        }
        undoStack.addLast(state)
        redoStack.clear()
        while (undoStack.size > limit) {
            undoStack.removeFirst()
        }
    }

    fun undo(current: EditorText): EditorText? {
        if (undoStack.isEmpty()) return null
        redoStack.addLast(current)
        return undoStack.removeLast()
    }

    fun redo(current: EditorText): EditorText? {
        if (redoStack.isEmpty()) return null
        val next = redoStack.removeLast()
        undoStack.addLast(current)
        while (undoStack.size > limit) {
            undoStack.removeFirst()
        }
        return next
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}

