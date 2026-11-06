package com.example.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.sync.SyncScheduler
import com.example.util.NoteImages
import com.example.util.NoteShare
import com.example.util.currentSupabaseUid
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.AppDatabase
import com.example.data.local.Course
import com.example.data.local.isMyTasksCourse
import com.example.data.repository.NoteRepository
import com.example.util.markdown.EditHistory
import com.example.util.markdown.EditorText
import com.example.util.markdown.continueListOnEnter
import com.example.util.markdown.insertImageLine
import com.example.util.markdown.insertLink
import com.example.util.markdown.insertTable
import com.example.util.markdown.toggleCheckboxAtLine
import com.example.util.markdown.toggleLinePrefix
import com.example.util.markdown.toggleWrap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// When true, opening an existing note starts in Preview mode.
// Set this to false to always open existing notes in Edit mode.
private const val OPEN_EXISTING_NOTES_IN_PREVIEW = true

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

    var isLoaded by mutableStateOf(false)
        private set

    private var autosaveJob: Job? = null
    private var discarded = false
    @Volatile
    private var trashed = false
    private var watchJob: Job? = null

    @Volatile
    private var ownVersions: List<Pair<String, String>> = emptyList()
    fun discardUnsaved() {
        discarded = true
        autosaveJob?.cancel()
    }
    private val saveMutex = Mutex()

    val activeCourses: Flow<List<Course>> = database.courseDao().getAllCourses()
        .map { list -> list.filter { !it.isDeleted && !it.isMyTasksCourse() } }

    fun loadNote(noteId: Long?) {
        if (isLoaded) return
        if (noteId == null || noteId == 0L) {
            currentId = 0L
            editHistory.clear()
            lastTypingTime = 0L
            updateHistoryState()
            isLoaded = true
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val note = repository.getNote(noteId)
            withContext(Dispatchers.Main) {
                if (note != null) {
                    currentId = note.id
                    title = note.title
                    bodyValue = TextFieldValue(note.body, TextRange(note.body.length))
                    selectedCourseId = note.courseId
                    isPinned = note.isPinned
                    rememberOwnVersion(note.title, note.body)
                    watchForOutsideChanges(note.id)
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
     * Cloud sync can change this note while it is open. Show that newer text instead of
     * saving the old text over it on the next keystroke.
     */
    private fun watchForOutsideChanges(id: Long) {
        watchJob?.cancel()
        watchJob = viewModelScope.launch {
            repository.observeNote(id).collect { note ->
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

    private fun triggerSyncIfLoggedIn() {
        if (currentSupabaseUid() != null) {
            try {
                SyncScheduler.scheduleSync(getApplication())
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun performSave(): Long = saveMutex.withLock {
        if (discarded || trashed) return@withLock 0L
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
            courseId = cId
        )
        if (savedId > 0L && currentId == 0L) {
            withContext(Dispatchers.Main) {
                currentId = savedId
            }
        }
        if (savedId > 0L) {
            triggerSyncIfLoggedIn()
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
                    triggerSyncIfLoggedIn()
                }
            } else {
                repository.setPinned(currentId, target)
                triggerSyncIfLoggedIn()
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
        triggerSyncIfLoggedIn()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    noteId: Long?,
    onClose: () -> Unit,
    onDeleted: (Long) -> Unit,
    viewModelKey: String = "note_editor",
    viewModel: NoteEditorViewModel = viewModel(key = viewModelKey)
) {
    LaunchedEffect(noteId) {
        viewModel.loadNote(noteId)
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val courses by viewModel.activeCourses.collectAsStateWithLifecycle(emptyList())

    val isExistingNote = (noteId != null && noteId > 0L)
    var isPreviewMode by rememberSaveable(noteId) {
        mutableStateOf(isExistingNote && OPEN_EXISTING_NOTES_IN_PREVIEW)
    }

    var isImportingImage by remember { mutableStateOf(false) }
    
    fun applyEditing(transform: (EditorText) -> EditorText) {
        viewModel.checkpointBeforeToolbar()
        val current = EditorText(
            text = viewModel.bodyValue.text,
            selStart = viewModel.bodyValue.selection.min,
            selEnd = viewModel.bodyValue.selection.max
        )
        val result = transform(current)
        viewModel.updateBody(
            TextFieldValue(
                text = result.text,
                selection = TextRange(result.selStart, result.selEnd)
            )
        )
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val count = NoteImages.countImageReferences(viewModel.bodyValue.text)
            if (count >= NoteImages.MAX_IMAGES_PER_NOTE) {
                Toast.makeText(context, "A note can hold up to 10 images", Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }
            isImportingImage = true
            coroutineScope.launch {
                try {
                    val imageName = NoteImages.importImage(context, uri)
                    if (imageName != null) {
                        applyEditing { current ->
                            insertImageLine(current, NoteImages.imageMarkdown(imageName))
                        }
                    } else {
                        Toast.makeText(context, "Could not add that image", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    Toast.makeText(context, "Could not add that image", Toast.LENGTH_SHORT).show()
                } finally {
                    isImportingImage = false
                }
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                viewModel.saveImmediately()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.saveImmediately()
        }
    }

    val handleClose: () -> Unit = {
        coroutineScope.launch {
            viewModel.saveAndFlush()
            onClose()
        }
    }

    BackHandler(enabled = true) {
        handleClose()
    }

    var showCoursePicker by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    val selectedCourse = courses.find { it.id == viewModel.selectedCourseId }
    val courseColor = remember(selectedCourse?.colorHex) {
        selectedCourse?.colorHex?.let {
            try {
                Color(android.graphics.Color.parseColor(it))
            } catch (_: Exception) {
                Color(0xFF4F46E5)
            }
        } ?: Color(0xFF4F46E5)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars))
    ) {
        TopAppBar(
            title = {
                if (!isPreviewMode) {
                    CoursePickerPill(
                        selectedCourse = selectedCourse,
                        courseColor = courseColor,
                        courses = courses,
                        expanded = showCoursePicker,
                        onExpandedChange = { showCoursePicker = it },
                        onSelectCourse = { viewModel.setCourse(it) }
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = handleClose) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            },
            actions = {
                if (!isPreviewMode) {
                    IconButton(
                        onClick = { viewModel.undo() },
                        enabled = viewModel.canUndo,
                        modifier = Modifier.testTag("note_undo_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo"
                        )
                    }
                    IconButton(
                        onClick = { viewModel.redo() },
                        enabled = viewModel.canRedo,
                        modifier = Modifier.testTag("note_redo_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "Redo"
                        )
                    }
                }

                // Edit mode toggle button
                IconButton(
                    onClick = { isPreviewMode = false },
                    modifier = Modifier.testTag("note_mode_edit_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = if (!isPreviewMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                // Preview mode toggle button
                IconButton(
                    onClick = { isPreviewMode = true },
                    modifier = Modifier.testTag("note_mode_preview_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = "Preview",
                        tint = if (isPreviewMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                Box {
                    IconButton(
                        onClick = { showOverflowMenu = true },
                        modifier = Modifier.testTag("note_editor_overflow_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options"
                        )
                    }

                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (viewModel.isPinned) "Unpin" else "Pin") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = null
                                )
                            },
                            enabled = viewModel.currentId > 0L || viewModel.title.isNotBlank() || viewModel.bodyValue.text.isNotBlank(),
                            onClick = {
                                showOverflowMenu = false
                                viewModel.togglePinned()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Share as text") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null
                                )
                            },
                            enabled = viewModel.title.isNotBlank() || viewModel.bodyValue.text.isNotBlank(),
                            onClick = {
                                showOverflowMenu = false
                                NoteShare.shareAsText(
                                    context = context,
                                    title = viewModel.title,
                                    body = viewModel.bodyValue.text
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Share as .md file") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null
                                )
                            },
                            enabled = viewModel.title.isNotBlank() || viewModel.bodyValue.text.isNotBlank(),
                            onClick = {
                                showOverflowMenu = false
                                NoteShare.shareAsMarkdownFile(
                                    context = context,
                                    title = viewModel.title,
                                    body = viewModel.bodyValue.text
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                showOverflowMenu = false
                                coroutineScope.launch {
                                    val idToDelete = viewModel.currentId
                                    if (idToDelete > 0L) {
                                        viewModel.moveToTrash(idToDelete)
                                        onDeleted(idToDelete)
                                    } else {
                                        viewModel.discardUnsaved()
                                        onClose()
                                    }
                                }
                            }
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        if (isPreviewMode) {
            // Preview mode: shows title (headlineSmall), course chip, then MarkdownPreview(body)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Text(
                    text = viewModel.title.ifBlank { "Untitled Note" },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (viewModel.title.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("note_preview_title")
                )

                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
                    CoursePickerPill(
                        selectedCourse = selectedCourse,
                        courseColor = courseColor,
                        courses = courses,
                        expanded = showCoursePicker,
                        onExpandedChange = { showCoursePicker = it },
                        onSelectCourse = { viewModel.setCourse(it) }
                    )
                }

                MarkdownPreview(
                    markdown = viewModel.bodyValue.text,
                    onToggleChecklist = { lineIndex ->
                        viewModel.checkpointBeforeToolbar()
                        val newText = toggleCheckboxAtLine(viewModel.bodyValue.text, lineIndex)
                        viewModel.updateBody(
                            TextFieldValue(
                                text = newText,
                                selection = viewModel.bodyValue.selection
                            )
                        )
                    },
                    imageContent = { src, alt ->
                        NoteImagePreview(src = src, alt = alt)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        } else {
            // Edit mode: Title input field, Body input field, Character limit, Formatting toolbar
            TextField(
                value = viewModel.title,
                onValueChange = { viewModel.updateTitle(it) },
                placeholder = {
                    Text(
                        text = "Title",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                },
                textStyle = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("note_title_input")
            )

            TextField(
                value = viewModel.bodyValue,
                onValueChange = { viewModel.onBodyChange(it) },
                placeholder = {
                    Text(
                        text = "Start writing",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("note_body_input")
            )

            // Word and character count line
            val bodyText = viewModel.bodyValue.text
            val words = remember(bodyText) { wordCount(bodyText) }
            val chars = remember(bodyText) { characterCount(bodyText) }
            val wordsLabel = if (words == 1) "1 word" else "$words words"
            val charsLabel = if (chars == 1) "1 character" else "$chars characters"
            val remainingBodyChars = NoteRepository.MAX_BODY - chars

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$wordsLabel  |  $charsLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("note_word_count")
                )

                if (remainingBodyChars < 1000) {
                    Text(
                        text = "$remainingBodyChars characters left",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (remainingBodyChars < 100) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Markdown editing toolbar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { applyEditing { toggleLinePrefix(it, "## ") } },
                        modifier = Modifier.testTag("toolbar_heading")
                    ) {
                        Icon(Icons.Default.Title, contentDescription = "Heading")
                    }
                    IconButton(
                        onClick = { applyEditing { toggleWrap(it, "**") } },
                        modifier = Modifier.testTag("toolbar_bold")
                    ) {
                        Icon(Icons.Default.FormatBold, contentDescription = "Bold")
                    }
                    IconButton(
                        onClick = { applyEditing { toggleWrap(it, "*") } },
                        modifier = Modifier.testTag("toolbar_italic")
                    ) {
                        Icon(Icons.Default.FormatItalic, contentDescription = "Italic")
                    }
                    IconButton(
                        onClick = { applyEditing { toggleWrap(it, "~~") } },
                        modifier = Modifier.testTag("toolbar_strikethrough")
                    ) {
                        Icon(Icons.Default.FormatStrikethrough, contentDescription = "Strikethrough")
                    }
                    IconButton(
                        onClick = { applyEditing { toggleLinePrefix(it, "- ") } },
                        modifier = Modifier.testTag("toolbar_bullet")
                    ) {
                        Icon(Icons.Default.FormatListBulleted, contentDescription = "Bullet list")
                    }
                    IconButton(
                        onClick = { applyEditing { toggleLinePrefix(it, "1. ") } },
                        modifier = Modifier.testTag("toolbar_numbered")
                    ) {
                        Icon(Icons.Default.FormatListNumbered, contentDescription = "Numbered list")
                    }
                    IconButton(
                        onClick = { applyEditing { toggleLinePrefix(it, "- [ ] ") } },
                        modifier = Modifier.testTag("toolbar_checklist")
                    ) {
                        Icon(Icons.Default.Checklist, contentDescription = "Checklist")
                    }
                    IconButton(
                        onClick = { applyEditing { toggleLinePrefix(it, "> ") } },
                        modifier = Modifier.testTag("toolbar_quote")
                    ) {
                        Icon(Icons.Default.FormatQuote, contentDescription = "Quote")
                    }
                    IconButton(
                        onClick = { applyEditing { toggleWrap(it, "`") } },
                        modifier = Modifier.testTag("toolbar_code")
                    ) {
                        Icon(Icons.Default.Code, contentDescription = "Inline code")
                    }
                    IconButton(
                        onClick = { applyEditing { insertLink(it) } },
                        modifier = Modifier.testTag("toolbar_link")
                    ) {
                        Icon(Icons.Default.Link, contentDescription = "Link")
                    }
                    IconButton(
                        onClick = { applyEditing { insertTable(it) } },
                        modifier = Modifier.testTag("toolbar_table")
                    ) {
                        Icon(Icons.Default.TableChart, contentDescription = "Table")
                    }
                    IconButton(
                        onClick = {
                            val count = NoteImages.countImageReferences(viewModel.bodyValue.text)
                            if (count >= NoteImages.MAX_IMAGES_PER_NOTE) {
                                Toast.makeText(context, "A note can hold up to 10 images", Toast.LENGTH_SHORT).show()
                            } else {
                                pickImageLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        },
                        modifier = Modifier.testTag("toolbar_image")
                    ) {
                        Icon(Icons.Default.Image, contentDescription = "Image")
                    }
                    if (isImportingImage) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(20.dp)
                                .padding(horizontal = 4.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CoursePickerPill(
    selectedCourse: Course?,
    courseColor: Color,
    courses: List<Course>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelectCourse: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (selectedCourse != null) courseColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onExpandedChange(true) }
                .testTag("note_course_picker_button")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (selectedCourse != null) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(courseColor, CircleShape)
                    )
                    Text(
                        text = selectedCourse.code,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = courseColor
                    )
                } else {
                    Text(
                        text = "No course",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Pick course",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            DropdownMenuItem(
                text = { Text("No course") },
                onClick = {
                    onSelectCourse(null)
                    onExpandedChange(false)
                }
            )
            courses.forEach { course ->
                val cColor = try {
                    Color(android.graphics.Color.parseColor(course.colorHex))
                } catch (_: Exception) {
                    Color(0xFF4F46E5)
                }
                DropdownMenuItem(
                    text = { Text("${course.code} - ${course.name}") },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(cColor, CircleShape)
                        )
                    },
                    onClick = {
                        onSelectCourse(course.id)
                        onExpandedChange(false)
                    }
                )
            }
        }
    }
}

@Composable
private fun NoteImagePreview(src: String, alt: String) {
    if (!src.startsWith(NoteImages.PREFIX)) {
        MarkdownImageFallback(src = src, alt = alt)
        return
    }

    val name = src.substring(NoteImages.PREFIX.length).trim()
    if (!NoteImages.isValidImageName(name)) {
        MarkdownImageFallback(src = src, alt = alt)
        return
    }

    val context = LocalContext.current
    val density = LocalDensity.current
    val screenWidthPx = remember(context, density) {
        context.resources.displayMetrics.widthPixels
    }

    val bitmapState = produceState<Bitmap?>(initialValue = null, key1 = name) {
        value = withContext(Dispatchers.IO) {
            try {
                val file = NoteImages.imageFile(context, name) ?: return@withContext null
                if (!file.exists()) return@withContext null

                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
                if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return@withContext null

                val targetWidth = if (screenWidthPx > 0) screenWidthPx else 1080
                val sampleSize = NoteImages.computeSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, targetWidth)
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
            } catch (_: Exception) {
                null
            }
        }
    }

    val bitmap = bitmapState.value
    var showFullScreen by remember { mutableStateOf(false) }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = alt.ifBlank { "Note image" },
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { showFullScreen = true }
                .testTag("note_preview_image")
        )

        if (showFullScreen) {
            Dialog(
                onDismissRequest = { showFullScreen = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .clickable { showFullScreen = false },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = alt.ifBlank { "Note image" },
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { showFullScreen = false }
                    )
                }
            }
        }
    } else {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .testTag("note_image_missing_placeholder")
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Image not available on this phone",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

