package com.example.util.markdown

// We keep both limits: 100 snapshots caps the number of undo steps for short notes,
// while maxTotalChars (3,000,000 characters, at most ~6 MB of UTF-16 text) caps memory usage
// when editing large notes close to the 300,000 character limit (about 10 undo steps).
class EditHistory(
    private val limit: Int = 100,
    private val maxTotalChars: Int = 3_000_000
) {

    private val undoStack = ArrayDeque<EditorText>()
    private val redoStack = ArrayDeque<EditorText>()

    var totalChars: Int = 0
        private set

    val canUndo: Boolean
        get() = undoStack.isNotEmpty()

    val canRedo: Boolean
        get() = redoStack.isNotEmpty()

    fun checkpoint(state: EditorText) {
        if (undoStack.lastOrNull() == state) {
            return
        }
        undoStack.addLast(state)
        totalChars += state.text.length

        while (redoStack.isNotEmpty()) {
            val discarded = redoStack.removeLast()
            totalChars -= discarded.text.length
        }

        trimUndoStack()
    }

    fun undo(current: EditorText): EditorText? {
        if (undoStack.isEmpty()) return null
        redoStack.addLast(current)
        totalChars += current.text.length
        val restored = undoStack.removeLast()
        totalChars -= restored.text.length
        return restored
    }

    fun redo(current: EditorText): EditorText? {
        if (redoStack.isEmpty()) return null
        val next = redoStack.removeLast()
        totalChars -= next.text.length
        undoStack.addLast(current)
        totalChars += current.text.length
        trimUndoStack()
        return next
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        totalChars = 0
    }

    private fun trimUndoStack() {
        while (undoStack.size > limit) {
            val dropped = undoStack.removeFirst()
            totalChars -= dropped.text.length
        }
        while (totalChars > maxTotalChars && undoStack.size > 1) {
            val dropped = undoStack.removeFirst()
            totalChars -= dropped.text.length
        }
    }
}
