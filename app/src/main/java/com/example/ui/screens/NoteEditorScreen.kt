package com.example.ui.screens

import android.app.Application
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
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
import com.example.util.markdown.EditorText
import com.example.util.markdown.insertLink
import com.example.util.markdown.insertTable
import com.example.util.markdown.toggleCheckboxAtLine
import com.example.util.markdown.toggleLinePrefix
import com.example.util.markdown.toggleWrap
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

    var currentId by mutableStateOf(0L)
        private set

    var title by mutableStateOf("")
        private set

    var bodyValue by mutableStateOf(TextFieldValue(""))
        private set

    var selectedCourseId by mutableStateOf<Long?>(null)
        private set

    var isLoaded by mutableStateOf(false)
        private set

    private var autosaveJob: Job? = null
    private val saveMutex = Mutex()

    val activeCourses: Flow<List<Course>> = database.courseDao().getAllCourses()
        .map { list -> list.filter { !it.isDeleted && !it.isMyTasksCourse() } }

    fun loadNote(noteId: Long?) {
        if (isLoaded) return
        if (noteId == null || noteId == 0L) {
            currentId = 0L
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
                }
                isLoaded = true
            }
        }
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
        val idToSave = currentId
        val t = title
        val b = bodyValue.text
        val cId = selectedCourseId

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
        savedId
    }

    suspend fun moveToTrash(id: Long) = withContext(Dispatchers.IO) {
        cancelAutosave()
        repository.moveToTrash(id)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    noteId: Long?,
    onClose: () -> Unit,
    onDeleted: (Long) -> Unit,
    viewModel: NoteEditorViewModel = viewModel()
) {
    LaunchedEffect(noteId) {
        viewModel.loadNote(noteId)
    }

    val coroutineScope = rememberCoroutineScope()
    val courses by viewModel.activeCourses.collectAsStateWithLifecycle(emptyList())

    val isExistingNote = (noteId != null && noteId > 0L)
    var isPreviewMode by rememberSaveable(noteId) {
        mutableStateOf(isExistingNote && OPEN_EXISTING_NOTES_IN_PREVIEW)
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

    fun applyEditing(transform: (EditorText) -> EditorText) {
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
                        val newText = toggleCheckboxAtLine(viewModel.bodyValue.text, lineIndex)
                        viewModel.updateBody(
                            TextFieldValue(
                                text = newText,
                                selection = viewModel.bodyValue.selection
                            )
                        )
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
                onValueChange = { viewModel.updateBody(it) },
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

            // Character limit warning (only when fewer than 1,000 remain)
            val remainingBodyChars = NoteRepository.MAX_BODY - viewModel.bodyValue.text.length
            if (remainingBodyChars < 1000) {
                Text(
                    text = "$remainingBodyChars characters left",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (remainingBodyChars < 100) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
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
