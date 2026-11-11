package com.example.ui.screens

import android.app.Application
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.Course
import com.example.data.local.isMyTasksCourse
import com.example.data.repository.NoteRepository
import com.example.util.markdown.EditHistory
import com.example.util.markdown.EditorText
import com.example.util.markdown.continueListOnEnter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * Hands out a new number every time any screen opens the note editor. It is shared by every
 * screen that opens an editor, so two opens never get the same number while the app runs.
 * (A counter inside one screen restarts at 0 when that screen is closed, and the editor
 * ViewModel outlives the screen, so it would wrongly treat the next open as the same editor.)
 */
object NoteEditorSessions {
    private val counter = java.util.concurrent.atomic.AtomicInteger(0)
    fun next(): Int = counter.incrementAndGet()
}
class NoteEditorViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = NoteRepository(database.noteDao())

    val editHistory = EditHistory()

    var canUndo by mutableStateOf(false)
        private set

    var canRedo by mutableStateOf(false)
        private set

    private var lastTypingTime = 0L

    private fun updateHistoryState() {
        canUndo = editHistory.canUndo
        canRedo = editHistory.canRedo
    }

    var currentId by mutableStateOf(0L)
        private set

    var title by mutableStateOf("")
        private set

    var bodyValue by mutableStateOf(TextFieldValue(""))
        private set

    var selectedCourseId by mutableStateOf<Long?>(null)
        private set

    var isPinned by mutableStateOf(false)
        private set

    var keepScreenOn by mutableStateOf(false)
        private set

    var isLoaded by mutableStateOf(false)
        private set

    private var autosaveJob: Job? = null
    private var loadJob: Job? = null
    private var currentSession: Int = -1
    private var discarded = false
    @Volatile
    private var trashed = false

    @Volatile
    private var ownVersions: List<Pair<String, String>> = emptyList()
    fun discardUnsaved() {
        discarded = true
        autosaveJob?.cancel()
    }
    private val saveMutex = Mutex()

    val activeCourses: Flow<List<Course>> = database.courseDao().getAllCourses()
        .map { list -> list.filter { !it.isDeleted && !it.isMyTasksCourse() } }

    fun loadNote(noteId: Long?, session: Int) {
        if (isLoaded && session == currentSession) return
        currentSession = session
        loadJob?.cancel()
        loadJob = null
        autosaveJob?.cancel()
        autosaveJob = null

        currentId = 0L
        title = ""
        bodyValue = TextFieldValue("")
        selectedCourseId = null
        isPinned = false
        keepScreenOn = false
        editHistory.clear()
        updateHistoryState()
        lastTypingTime = 0L
        ownVersions = emptyList()
        discarded = false
        trashed = false
        isLoaded = false

        if (noteId == null || noteId == 0L) {
            isLoaded = true
            return
        }

        val targetSession = session
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            val note = repository.getNote(noteId)
            if (!isActive || targetSession != currentSession) return@launch
            withContext(Dispatchers.Main) {
                if (!isActive || targetSession != currentSession) return@withContext
                if (note != null) {
                    currentId = note.id
                    title = note.title
                    bodyValue = TextFieldValue(note.body, TextRange(note.body.length))
                    selectedCourseId = note.courseId
                    isPinned = note.isPinned
                    keepScreenOn = note.keepScreenOn
                    rememberOwnVersion(note.title, note.body)
                }
                editHistory.clear()
                lastTypingTime = 0L
                updateHistoryState()
                isLoaded = true
            }
        }
    }
    private fun rememberOwnVersion(t: String, b: String) {
        ownVersions = (ownVersions + (t to b)).takeLast(4)
    }

    /**
     * An outside change (such as a backup restore) can change this note while it is open.
     * Show that newer text instead of saving the old text over it on the next keystroke.
     */
    suspend fun watchForOutsideChanges() {
        val id = currentId
        if (id <= 0L) return
        val watchSession = currentSession
        repository.observeNote(id).collect { note ->
            if (watchSession != currentSession) return@collect
            if (note == null || note.isDeleted || trashed || discarded) return@collect
            if ((note.title to note.body) in ownVersions) return@collect
            if (note.title == title && note.body == bodyValue.text) return@collect
            autosaveJob?.cancel()
            title = note.title
            bodyValue = TextFieldValue(note.body, TextRange(note.body.length))
            editHistory.clear()
            lastTypingTime = 0L
            updateHistoryState()
            rememberOwnVersion(note.title, note.body)
        }
    }
    
    fun onBodyChange(newBody: TextFieldValue) {
        val before = EditorText(
            text = bodyValue.text,
            selStart = bodyValue.selection.start,
            selEnd = bodyValue.selection.end
        )
        val after = EditorText(
            text = newBody.text,
            selStart = newBody.selection.start,
            selEnd = newBody.selection.end
        )

        val continued = continueListOnEnter(before, after)
        val effectiveBody = if (continued != null) {
            TextFieldValue(
                text = continued.text,
                selection = TextRange(continued.selStart, continued.selEnd)
            )
        } else {
            newBody
        }

        if (effectiveBody.text != bodyValue.text) {
            val now = System.currentTimeMillis()
            if (now - lastTypingTime > 500L) {
                editHistory.checkpoint(before)
                updateHistoryState()
            }
            lastTypingTime = now
        }

        updateBody(effectiveBody)
    }

    fun checkpointBeforeToolbar() {
        val current = EditorText(
            text = bodyValue.text,
            selStart = bodyValue.selection.start,
            selEnd = bodyValue.selection.end
        )
        editHistory.checkpoint(current)
        lastTypingTime = 0L
        updateHistoryState()
    }

    fun undo() {
        val current = EditorText(
            text = bodyValue.text,
            selStart = bodyValue.selection.start,
            selEnd = bodyValue.selection.end
        )
        val target = editHistory.undo(current) ?: return
        lastTypingTime = 0L
        updateHistoryState()
        updateBody(
            TextFieldValue(
                text = target.text,
                selection = TextRange(
                    target.selStart.coerceIn(0, target.text.length),
                    target.selEnd.coerceIn(0, target.text.length)
                )
            )
        )
    }

    fun redo() {
        val current = EditorText(
            text = bodyValue.text,
            selStart = bodyValue.selection.start,
            selEnd = bodyValue.selection.end
        )
        val target = editHistory.redo(current) ?: return
        lastTypingTime = 0L
        updateHistoryState()
        updateBody(
            TextFieldValue(
                text = target.text,
                selection = TextRange(
                    target.selStart.coerceIn(0, target.text.length),
                    target.selEnd.coerceIn(0, target.text.length)
                )
            )
        )
    }

    fun updateTitle(newTitle: String) {
        val limited = newTitle.take(NoteRepository.MAX_TITLE)
        if (limited == title) return
        title = limited
        scheduleAutosave()
    }

    fun updateBody(newBody: TextFieldValue) {
        val limitedText = newBody.text.take(NoteRepository.MAX_BODY)
        val clampedSelection = TextRange(
            newBody.selection.start.coerceIn(0, limitedText.length),
            newBody.selection.end.coerceIn(0, limitedText.length)
        )
        bodyValue = TextFieldValue(limitedText, clampedSelection)
        scheduleAutosave()
    }

    fun setCourse(courseId: Long?) {
        if (selectedCourseId == courseId) return
        selectedCourseId = courseId
        scheduleAutosave()
    }

    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(600)
            performSave()
        }
    }

    fun cancelAutosave() {
        autosaveJob?.cancel()
    }

    fun saveImmediately() {
        autosaveJob?.cancel()
        viewModelScope.launch(NonCancellable + Dispatchers.IO) {
            performSave()
        }
    }

    suspend fun saveAndFlush(): Long {
        autosaveJob?.cancel()
        return withContext(Dispatchers.IO) {
            performSave()
        }
    }



    private suspend fun performSave(): Long = saveMutex.withLock {
        if (discarded || trashed) return@withLock 0L
        val saveSession = currentSession
        val idToSave = currentId
        val t = title
        val b = bodyValue.text
        val cId = selectedCourseId
        // Written before the save, so the database copy of our own text is not mistaken
        // for an edit that came from another phone
        rememberOwnVersion(t, b)
        val savedId = repository.saveNote(
            id = idToSave,
            title = t,
            body = b,
            courseId = cId,
            keepScreenOn = keepScreenOn
        )
        if (saveSession != currentSession) return@withLock savedId
        if (savedId > 0L && currentId == 0L) {
            withContext(Dispatchers.Main) {
                if (saveSession == currentSession && currentId == 0L) {
                    currentId = savedId
                }
            }
        }

        savedId
    }

    fun togglePinned() {
        viewModelScope.launch {
            val target = !isPinned
            isPinned = target
            if (currentId == 0L) {
                val savedId = saveAndFlush()
                if (savedId > 0L) {
                    repository.setPinned(savedId, target)
                }
            } else {
                repository.setPinned(currentId, target)
            }
        }
    }

    /**
     * Turns "Keep screen on" on or off for this note. A note that is not saved yet keeps the
     * choice in memory and stores it with its first save.
     */
    fun toggleKeepScreenOn(enabled: Boolean) {
        if (keepScreenOn == enabled) return
        keepScreenOn = enabled
        val id = currentId
        if (id > 0L) {
            viewModelScope.launch {
                repository.setKeepScreenOn(id, enabled)
            }
        }
    }

    suspend fun moveToTrash(id: Long) = withContext(Dispatchers.IO) {
        cancelAutosave()
        // Under the save lock, so a save that is already running cannot bring the note back
        saveMutex.withLock {
            trashed = true
            repository.moveToTrash(id)
        }
    }
}
