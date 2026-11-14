package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import com.example.util.markdown.lineIndexAtOffset
import com.example.util.markdown.offsetOfLine
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.material3.AssistChip
import androidx.compose.foundation.clickable
import com.example.data.local.Note
import androidx.compose.ui.text.style.TextOverflow
import com.example.util.markdown.activeNoteLinkQuery
import com.example.util.markdown.applyNoteLinkChoice
import com.example.ui.theme.isDarkTheme
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import com.example.util.NoteExportFormat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.example.util.NoteImages
import com.example.util.NoteShare
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.repository.NoteRepository
import com.example.util.markdown.EditorText
import com.example.util.markdown.insertImageLine
import com.example.util.markdown.insertLink
import com.example.util.markdown.insertTable
import com.example.util.markdown.toggleCheckboxAtLine
import com.example.util.markdown.toggleLinePrefix
import com.example.util.markdown.toggleWrap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.example.ui.theme.rememberCourseColor

// When true, opening an existing note starts in Preview mode.
// Set this to false to always open existing notes in Edit mode.
private const val OPEN_EXISTING_NOTES_IN_PREVIEW = true

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    noteId: Long?,
    onClose: () -> Unit,
    onDeleted: (Long) -> Unit,
    viewModelSession: Int = 0,
    newNoteFolderId: Long? = null,
    viewModel: NoteEditorViewModel = viewModel(key = "note_editor")
) {
    LaunchedEffect(noteId, viewModelSession) {
        viewModel.loadNote(noteId, viewModelSession, newNoteFolderId)
    }

    // Watch the open note only while the editor is on screen
    val editorLoaded = viewModel.isLoaded
    LaunchedEffect(viewModel, editorLoaded, viewModel.currentId) {
        if (editorLoaded) viewModel.watchForOutsideChanges()
    }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val courses by viewModel.activeCourses.collectAsStateWithLifecycle(emptyList())

    val isExistingNote = (noteId != null && noteId > 0L)
    var isPreviewMode by rememberSaveable(noteId) {
        mutableStateOf(isExistingNote && OPEN_EXISTING_NOTES_IN_PREVIEW)
    }

    // Keeps Edit and Preview at the same place in the note
    val previewListState = rememberLazyListState()

    // Pinch with two fingers to change the text size. It is remembered between notes and launches.
    var noteTextScale by remember { mutableStateOf(loadNoteTextScale(context)) }
    LaunchedEffect(noteTextScale) {
        kotlinx.coroutines.delay(400)
        saveNoteTextScale(context, noteTextScale)
    }
    val onPinchZoom: (Float) -> Unit = { zoom ->
        noteTextScale = (noteTextScale * zoom).coerceIn(NOTE_TEXT_SCALE_MIN, NOTE_TEXT_SCALE_MAX)
    }
    val editScrollState = rememberScrollState()
    var previewStartLines by remember { mutableStateOf<List<Int>>(emptyList()) }
    var bodyLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var editViewportPx by remember { mutableStateOf(0f) }
    var previewTargetLine by remember { mutableStateOf<Int?>(null) }
    var editTargetOffset by remember { mutableStateOf<Int?>(null) }

    // Find in note. The matches come from the note text; Edit and Preview both show them.
    var showFind by rememberSaveable { mutableStateOf(false) }
    var findQuery by rememberSaveable { mutableStateOf("") }
    var findIndex by rememberSaveable { mutableStateOf(0) }
    val findMatches = remember(viewModel.bodyValue.text, findQuery, showFind) {
        if (showFind) NoteFind.matches(viewModel.bodyValue.text, findQuery) else emptyList()
    }
    val currentFindMatch = if (findMatches.isEmpty()) -1 else findIndex.coerceIn(0, findMatches.size - 1)
    val density = LocalDensity.current
    // Bring the current match into view: in Edit by scrolling to its text, in Preview to the block it is in.
    LaunchedEffect(showFind, findQuery, findIndex, isPreviewMode) {
        if (!showFind || currentFindMatch < 0) return@LaunchedEffect
        val offset = findMatches[currentFindMatch]
        val text = viewModel.bodyValue.text
        if (isPreviewMode) {
            val line = lineIndexAtOffset(text, offset)
            if (previewStartLines.isNotEmpty()) {
                previewListState.animateScrollToItem(
                    previewItemIndexForBlock(
                        com.example.util.markdown.blockIndexForLine(previewStartLines, line),
                        hasHeader = true
                    )
                )
            }
        } else {
            val layout = bodyLayout ?: return@LaunchedEffect
            if (layout.layoutInput.text.text != text || offset >= text.length) return@LaunchedEffect
            val top = layout.getBoundingBox(offset).top + with(density) { 12.dp.toPx() }
            editScrollState.animateScrollTo((top - editViewportPx * 0.3f).toInt().coerceAtLeast(0))
        }
    }

    LaunchedEffect(noteId, viewModelSession) {
        previewTargetLine = null
        editTargetOffset = null
        bodyLayout = null
    }

    fun switchToPreview() {
        if (isPreviewMode) return
        val text = viewModel.bodyValue.text
        val layout = bodyLayout
        previewTargetLine = if (layout != null && layout.layoutInput.text.text == text) {
            // The first line showing at the top of the editor
            val visualLine = layout.getLineForVerticalPosition(editScrollState.value.toFloat())
            lineIndexAtOffset(text, layout.getLineStart(visualLine))
        } else {
            lineIndexAtOffset(text, viewModel.bodyValue.selection.start)
        }
        isPreviewMode = true
    }

    fun switchToEdit() {
        if (!isPreviewMode) return
        val line = previewStartLines.getOrNull(
            previewBlockForItem(previewListState.firstVisibleItemIndex, hasHeader = true)
        ) ?: 0
        editTargetOffset = offsetOfLine(viewModel.bodyValue.text, line)
        bodyLayout = null // wait for the fresh layout of the editor
        isPreviewMode = false
    }

    // Scrolls the editor to the place the preview was showing, once its text has been laid out
    LaunchedEffect(isPreviewMode, bodyLayout, editTargetOffset) {
        val target = editTargetOffset
        val layout = bodyLayout
        if (!isPreviewMode && target != null && layout != null) {
            val length = layout.layoutInput.text.text.length
            val top = layout.getLineTop(layout.getLineForOffset(target.coerceIn(0, length))).toInt()
            withTimeoutOrNull(400) { snapshotFlow { editScrollState.maxValue }.first { it >= top } }
            editScrollState.scrollTo(top)
            editTargetOffset = null
        }
    }

    // Keeps the line you are typing on in view. The editor is one tall text field inside a scrolling
    // column, so without this the cursor moves below the visible area when you press Enter.
    val cursorDensity = LocalDensity.current
    LaunchedEffect(bodyLayout, viewModel.bodyValue.selection, viewModel.bodyValue.text, editViewportPx, isPreviewMode) {
        val layout = bodyLayout ?: return@LaunchedEffect
        if (isPreviewMode || editTargetOffset != null || editViewportPx <= 0f) return@LaunchedEffect
        val bodyText = viewModel.bodyValue.text
        // Wait for the layout of the current text, otherwise the cursor position would be stale
        if (layout.layoutInput.text.text != bodyText) return@LaunchedEffect
        val cursor = viewModel.bodyValue.selection.end.coerceIn(0, bodyText.length)
        val rect = layout.getCursorRect(cursor)
        val topPadding = with(cursorDensity) { 12.dp.toPx() } // the editor's top padding
        val margin = with(cursorDensity) { 24.dp.toPx() }
        val cursorTop = rect.top + topPadding
        val cursorBottom = rect.bottom + topPadding
        val current = editScrollState.value
        val target = when {
            cursorBottom + margin > current + editViewportPx -> (cursorBottom + margin - editViewportPx).toInt()
            cursorTop - margin < current -> (cursorTop - margin).coerceAtLeast(0f).toInt()
            else -> return@LaunchedEffect
        }
        // A new last line only counts toward the scroll range after the next layout pass
        withTimeoutOrNull(300) { snapshotFlow { editScrollState.maxValue }.first { it >= target } }
        editScrollState.scrollTo(target.coerceAtMost(editScrollState.maxValue))
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

    val showImageLimitMessage = {
        com.example.ui.AppMessages.show("A note can hold up to ${NoteImages.MAX_IMAGES_PER_NOTE} images")
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val count = NoteImages.countImageReferences(viewModel.bodyValue.text)
            if (!NoteImages.canAddImage(count)) {
                showImageLimitMessage()
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
                        com.example.ui.AppMessages.show("Could not add that image")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    com.example.ui.AppMessages.show("Could not add that image")
                } finally {
                    isImportingImage = false
                }
            }
        }
    }
    
    var isWorking by remember { mutableStateOf(false) }
    var pendingSaveFormat by rememberSaveable { mutableStateOf<String?>(null) }
    val saveDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        val formatName = pendingSaveFormat
        pendingSaveFormat = null
        if (uri != null && formatName != null) {
            val format = try {
                NoteExportFormat.valueOf(formatName)
            } catch (_: Exception) {
                null
            }
            if (format != null) {
                val currentTitle = viewModel.title
                val currentBody = viewModel.bodyValue.text
                val isPdf = (format == NoteExportFormat.PDF)
                coroutineScope.launch {
                    if (isPdf) isWorking = true
                    try {
                        val ok = NoteShare.saveToUri(
                            context = context,
                            uri = uri,
                            title = currentTitle,
                            body = currentBody,
                            format = format
                        )
                        val message = if (ok) "Saved to your device" else "Could not save the file"
                        com.example.ui.AppMessages.show(message)
                    } catch (_: Exception) {
                        com.example.ui.AppMessages.show("Could not save the file")
                    } finally {
                        if (isPdf) isWorking = false
                    }
                }
            } else {
                com.example.ui.AppMessages.show("Could not save the file")
            }
        }
    }

    // Keeps the screen awake only while this note is open and its option is on
    val hostView = LocalView.current
    val keepScreenOn = viewModel.keepScreenOn
    DisposableEffect(hostView, keepScreenOn) {
        hostView.keepScreenOn = keepScreenOn
        onDispose { hostView.keepScreenOn = false }
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

    // ---- links between notes ([[Note title]])
    val allNotes by viewModel.allNotes.collectAsStateWithLifecycle(emptyList())
    // Notes we came from by tapping links, so Back returns to them instead of closing the editor
    val linkBackStack = remember { mutableStateListOf<Long>() }

    // Saves this note, then shows another one in the same editor
    val showNote: (Long) -> Unit = { targetId ->
        coroutineScope.launch {
            viewModel.saveAndFlush()
            showFind = false
            viewModel.loadNote(targetId, NoteEditorSessions.next())
            snapshotFlow { viewModel.isLoaded }.first { it }
            previewListState.scrollToItem(0)
        }
    }
    val openLinkedNote: (Long) -> Unit = { targetId ->
        val fromId = viewModel.currentId
        if (targetId != fromId) {
            if (fromId > 0L) linkBackStack.add(fromId)
            showNote(targetId)
        }
    }
    val openLinkByTitle: (String) -> Unit = { title ->
        val target = findNoteForLink(allNotes, title)
        if (target == null) {
            com.example.ui.AppMessages.show("No note called \"$title\"")
        } else {
            openLinkedNote(target.id)
        }
    }
    val openLinkByTitleState = rememberUpdatedState(openLinkByTitle)
    val noteLinkHandler = remember { { title: String -> openLinkByTitleState.value(title) } }

    // Registered after the close handler above, so Back goes to the previous note first
    BackHandler(enabled = linkBackStack.isNotEmpty()) {
        val previousId = linkBackStack.removeAt(linkBackStack.lastIndex)
        showNote(previousId)
    }
    // Registered last, so Back closes the find bar before anything else
    BackHandler(enabled = showFind) {
        showFind = false
    }

    var showCoursePicker by remember { mutableStateOf(false) }
    // A long title shows 3 lines in Preview; tapping it shows all of it. Back to 3 lines on another note.
    var titleExpanded by rememberSaveable(viewModel.currentId) { mutableStateOf(false) }
    var titleOverflows by remember(viewModel.currentId) { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showShareDialog by rememberSaveable { mutableStateOf(false) }
    var showSaveDialog by rememberSaveable { mutableStateOf(false) }

    val selectedCourse = courses.find { it.id == viewModel.selectedCourseId }
    val courseColor = rememberCourseColor(selectedCourse?.colorHex)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
    ) {
        // On narrow screens the five action icons leave no room for the course pill in the bar,
        // so it moves to its own line below the bar.
        val compactTopBar = LocalConfiguration.current.screenWidthDp < 420
        TopAppBar(
            title = {
                if (!isPreviewMode && !compactTopBar) {
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
                IconButton(
                    onClick = handleClose,
                    modifier = Modifier.testTag("note_editor_back_button")
                ) {
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
                    onClick = { switchToEdit() },
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
                    onClick = { switchToPreview() },
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
                            text = { Text("Find in note") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier.testTag("note_menu_find"),
                            onClick = {
                                showOverflowMenu = false
                                showFind = true
                            }
                        )
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
                            text = { Text("Share") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null
                                )
                            },
                            enabled = viewModel.title.isNotBlank() || viewModel.bodyValue.text.isNotBlank(),
                            modifier = Modifier.testTag("note_menu_share"),
                            onClick = {
                                showOverflowMenu = false
                                showShareDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Save to device") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = null
                                )
                            },
                            enabled = viewModel.title.isNotBlank() || viewModel.bodyValue.text.isNotBlank(),
                            modifier = Modifier.testTag("note_menu_save"),
                            onClick = {
                                showOverflowMenu = false
                                showSaveDialog = true
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

        if (!isPreviewMode && compactTopBar) {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                CoursePickerPill(
                    selectedCourse = selectedCourse,
                    courseColor = courseColor,
                    courses = courses,
                    expanded = showCoursePicker,
                    onExpandedChange = { showCoursePicker = it },
                    onSelectCourse = { viewModel.setCourse(it) }
                )
            }
        }

        if (showFind) {
            NoteFindBar(
                query = findQuery,
                onQueryChange = {
                    findQuery = it
                    findIndex = 0
                },
                currentMatch = currentFindMatch.coerceAtLeast(0),
                totalMatches = findMatches.size,
                onPrevious = { findIndex = NoteFind.step(currentFindMatch.coerceAtLeast(0), findMatches.size, forward = false) },
                onNext = { findIndex = NoteFind.step(currentFindMatch.coerceAtLeast(0), findMatches.size, forward = true) },
                onClose = { showFind = false }
            )
        }

        // Edit mode keeps it above the title. In Preview it is part of the note header, which
        // scrolls away with the note instead of staying on screen.
        if (!isPreviewMode) {
            KeepScreenOnRow(
                checked = viewModel.keepScreenOn,
                onCheckedChange = { viewModel.toggleKeepScreenOn(it) }
            )
        }

        if (isPreviewMode) {
            // Preview mode: MarkdownPreview(body), with the Keep screen on row, the title
            // (headlineSmall, 3 lines until tapped) and the course chip as its first list item
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pinchToZoomText(onPinchZoom)
            ) {
                CompositionLocalProvider(
                    LocalNoteFindQuery provides (if (showFind) findQuery else ""),
                    LocalNoteLinkHandler provides noteLinkHandler
                ) {
                WithNoteTextScale(noteTextScale) {
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
                        listState = previewListState,
                        initialSourceLine = previewTargetLine,
                        onBlockStartLines = { previewStartLines = it },
                        header = {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                KeepScreenOnRow(
                                    checked = viewModel.keepScreenOn,
                                    onCheckedChange = { viewModel.toggleKeepScreenOn(it) },
                                    horizontalPadding = 0.dp
                                )
                                val titleBlank = viewModel.title.isBlank()
                                Text(
                                    text = viewModel.title.ifBlank { "Untitled Note" },
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (titleBlank) {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    maxLines = if (titleExpanded) Int.MAX_VALUE else 3,
                                    overflow = TextOverflow.Ellipsis,
                                    onTextLayout = { if (!titleExpanded) titleOverflows = it.hasVisualOverflow },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = titleOverflows || titleExpanded) {
                                            titleExpanded = !titleExpanded
                                        }
                                        .padding(vertical = 4.dp)
                                        .testTag("note_preview_title")
                                )
                                if (titleOverflows || titleExpanded) {
                                    Text(
                                        text = if (titleExpanded) "Show less" else "Show full title",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clickable { titleExpanded = !titleExpanded }
                                            .padding(vertical = 4.dp)
                                            .testTag("note_preview_title_toggle")
                                    )
                                }
                                Box(modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)) {
                                    CoursePickerPill(
                                        selectedCourse = selectedCourse,
                                        courseColor = courseColor,
                                        courses = courses,
                                        expanded = showCoursePicker,
                                        onExpandedChange = { showCoursePicker = it },
                                        onSelectCourse = { viewModel.setCourse(it) }
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
                }

                // Notes that have a [[link]] to this one
                val bodyForLinks = viewModel.bodyValue.text
                val backlinks = remember(allNotes, viewModel.title, bodyForLinks, viewModel.currentId) {
                    findBacklinks(
                        notes = allNotes,
                        currentId = viewModel.currentId,
                        currentTitle = noteDisplayTitle(Note(title = viewModel.title, body = bodyForLinks))
                    )
                }
                if (backlinks.isNotEmpty()) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Text(
                            text = "Linked from",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .testTag("note_backlinks"),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            backlinks.forEach { source ->
                                AssistChip(
                                    onClick = { openLinkedNote(source.id) },
                                    label = {
                                        Text(
                                            text = noteDisplayTitle(source),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
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

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pinchToZoomText(onPinchZoom)
            ) {
                val viewportHeight = maxHeight
                val markerColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                val darkCode = isDarkTheme()
                val activeFindQuery = if (showFind) findQuery else ""
                val editorTransformation = remember(markerColor, darkCode, activeFindQuery, currentFindMatch) {
                    MarkdownEditorTransformation(markerColor, darkCode, activeFindQuery, currentFindMatch)
                }
                val viewportPx = with(LocalDensity.current) { maxHeight.toPx() }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { editViewportPx = it.height.toFloat() }
                        .verticalScroll(editScrollState)
                ) {
                    WithNoteTextScale(noteTextScale) {
                        BasicTextField(
                            value = viewModel.bodyValue,
                            onValueChange = { viewModel.onBodyChange(it) },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            visualTransformation = editorTransformation,
                            onTextLayout = { bodyLayout = it },
                            decorationBox = { innerTextField ->
                                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                    if (viewModel.bodyValue.text.isEmpty()) {
                                        Text(
                                            text = "Start writing",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                    innerTextField()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                // At least the visible height, so tapping the empty space still starts typing
                                .heightIn(min = viewportHeight)
                                .testTag("note_body_input")
                        )
                    }
                }
                ScrollStateScrollbar(editScrollState, viewportPx)
            }

            // Suggestions while a note link is being typed: "[[" followed by part of a title
            val typedSelection = viewModel.bodyValue.selection
            val linkQuery = if (typedSelection.collapsed) {
                activeNoteLinkQuery(viewModel.bodyValue.text, typedSelection.end)
            } else {
                null
            }
            val linkSuggestions = remember(allNotes, viewModel.currentId, linkQuery?.query) {
                if (linkQuery == null) emptyList()
                else noteLinkSuggestions(allNotes, viewModel.currentId, linkQuery.query)
            }
            if (linkQuery != null && linkSuggestions.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_link_suggestions")
                ) {
                    Column {
                        linkSuggestions.forEach { suggestion ->
                            val suggestionTitle = noteDisplayTitle(suggestion)
                            Text(
                                text = suggestionTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        applyEditing { applyNoteLinkChoice(it, linkQuery, suggestionTitle) }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }
                    }
                }
            }

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
                            if (!NoteImages.canAddImage(count)) {
                                showImageLimitMessage()
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

    if (showShareDialog) {
        NoteExportDialog(
            title = "Share as",
            onPick = { format ->
                showShareDialog = false
                val currentTitle = viewModel.title
                val currentBody = viewModel.bodyValue.text
                val isPdf = (format == NoteExportFormat.PDF)
                coroutineScope.launch {
                    if (isPdf) isWorking = true
                    try {
                        NoteShare.shareAsFileAsync(
                            context = context,
                            title = currentTitle,
                            body = currentBody,
                            format = format
                        )
                    } catch (_: Exception) {
                        com.example.ui.AppMessages.show("Could not share the file")
                    } finally {
                        if (isPdf) isWorking = false
                    }
                }
            },
            onDismiss = { showShareDialog = false }
        )
    }

    if (showSaveDialog) {
        NoteExportDialog(
            title = "Save as",
            onPick = { format ->
                showSaveDialog = false
                pendingSaveFormat = format.name
                val suggestedName = NoteShare.fileNameFor(viewModel.title, format)
                saveDocumentLauncher.launch(suggestedName)
            },
            onDismiss = { showSaveDialog = false }
        )
    }

    if (isWorking) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Please wait") },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text("Creating PDF...")
                }
            }
        )
    }
}

/** "Keep screen on" with its switch. The whole row is the tap target. */
@Composable
private fun KeepScreenOnRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    horizontalPadding: Dp = 16.dp
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .padding(horizontal = horizontalPadding, vertical = 2.dp)
            .testTag("note_keep_screen_on_row"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Keep screen on",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Switch(
            checked = checked,
            onCheckedChange = null,
            modifier = Modifier.testTag("note_keep_screen_on_switch")
        )
    }
}
