package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Sort
import androidx.compose.ui.platform.LocalContext
import com.example.data.repository.NoteRepository
import com.example.util.MarkdownFileReader
import com.example.util.MarkdownReadResult
import com.example.util.NoteShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.NoteListItem
import com.example.util.DateFormats
import java.util.Calendar
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    onOpenNote: (Long) -> Unit,
    onNewNote: () -> Unit,
    onOpenTrash: () -> Unit = {},
    onClose: () -> Unit,
    viewModel: NotesViewModel = viewModel(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val currentSort by viewModel.sort.collectAsStateWithLifecycle()
    val courses by viewModel.activeCourses.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var snackbarJob by remember { mutableStateOf<Job?>(null) }
    var showListOverflowMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                importNoteFile(context, uri, viewModel, snackbarHostState)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Notes",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(
                            onClick = { showSortMenu = true },
                            modifier = Modifier.testTag("notes_sort_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sort,
                                contentDescription = "Sort notes"
                            )
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Last edited",
                                        fontWeight = if (currentSort == NotesSort.UPDATED) FontWeight.Bold else FontWeight.Normal,
                                        color = if (currentSort == NotesSort.UPDATED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    viewModel.setSort(NotesSort.UPDATED)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Date created",
                                        fontWeight = if (currentSort == NotesSort.CREATED) FontWeight.Bold else FontWeight.Normal,
                                        color = if (currentSort == NotesSort.CREATED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    viewModel.setSort(NotesSort.CREATED)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Title A to Z",
                                        fontWeight = if (currentSort == NotesSort.TITLE) FontWeight.Bold else FontWeight.Normal,
                                        color = if (currentSort == NotesSort.TITLE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    viewModel.setSort(NotesSort.TITLE)
                                    showSortMenu = false
                                }
                            )
                        }
                    }

                    Box {
                        IconButton(
                            onClick = { showListOverflowMenu = true },
                            modifier = Modifier.testTag("notes_list_overflow_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options"
                            )
                        }

                        DropdownMenu(
                            expanded = showListOverflowMenu,
                            onDismissRequest = { showListOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Import .md or .txt file") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    showListOverflowMenu = false
                                    importLauncher.launch(
                                        arrayOf(
                                            "text/markdown",
                                            "text/plain",
                                            "text/x-markdown",
                                            "application/octet-stream"
                                        )
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Recently deleted") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    showListOverflowMenu = false
                                    onOpenTrash()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewNote,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New note") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("notes_fab_new_note")
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("notes_search_input"),
                placeholder = {
                    Text(
                        text = "Search notes",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search"
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            // Horizontally scrolling filter chips row (shown only when at least one course exists)
            if (courses.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        FilterChip(
                            selected = selectedFilter is NotesFilter.All,
                            onClick = { viewModel.setFilter(NotesFilter.All) },
                            label = { Text("All") },
                            modifier = Modifier.testTag("notes_filter_all")
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedFilter is NotesFilter.NoCourse,
                            onClick = { viewModel.setFilter(NotesFilter.NoCourse) },
                            label = { Text("No course") },
                            modifier = Modifier.testTag("notes_filter_no_course")
                        )
                    }
                    items(courses, key = { it.id }) { course ->
                        val isSelected = selectedFilter is NotesFilter.Course &&
                            (selectedFilter as NotesFilter.Course).courseId == course.id
                        val courseColor = remember(course.colorHex) {
                            try {
                                Color(android.graphics.Color.parseColor(course.colorHex))
                            } catch (_: Exception) {
                                Color(0xFF4F46E5)
                            }
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.setFilter(NotesFilter.Course(course.id)) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(courseColor, CircleShape)
                                    )
                            },
                            label = { Text(course.code) },
                            modifier = Modifier.testTag("notes_filter_course_${course.id}")
                        )
                    }
                }
            }

            // Notes list or empty state
            if (notes.isEmpty()) {
                val isFiltered = searchQuery.isNotEmpty() || selectedFilter !is NotesFilter.All
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isFiltered) Icons.Default.Search else Icons.Default.EditNote,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = if (isFiltered) "No notes found" else "No notes yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = if (isFiltered) {
                                    "No notes match your search or filter."
                                } else {
                                    "Capture your thoughts, lecture notes, or ideas."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            if (!isFiltered) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = onNewNote,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("New note")
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("notes_list"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(notes, key = { it.note.id }) { item ->
                        NoteCard(
                            item = item,
                            onClick = { onOpenNote(item.note.id) },
                            onTogglePin = { viewModel.setPinned(item.note.id, !item.note.isPinned) },
                            onDelete = {
                                val noteId = item.note.id
                                viewModel.moveToTrash(noteId)
                                snackbarJob?.cancel()
                                snackbarJob = coroutineScope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Note deleted",
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.restoreFromTrash(noteId)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteCard(
    item: NoteListItem,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val displayTitle = remember(item.note) { noteDisplayTitle(item.note) }
    val previewText = remember(item.note.body) { notePreviewText(item.note.body, maxLines = 2) }
    val updatedLabel = remember(item.note.updatedAt) { formatNoteDate(item.note.updatedAt) }

    val courseColor = remember(item.courseColorHex) {
        item.courseColorHex?.let {
            try {
                Color(android.graphics.Color.parseColor(it))
            } catch (_: Exception) {
                Color(0xFF4F46E5)
            }
        } ?: Color(0xFF4F46E5)
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("note_card_${item.note.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.note.isPinned) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Pinned",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(16.dp)
                                .padding(end = 4.dp)
                                .testTag("note_pin_icon_${item.note.id}")
                        )
                    }
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Note actions",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (item.note.isPinned) "Unpin" else "Pin") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                showMenu = false
                                onTogglePin()
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
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            if (previewText.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = previewText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (!item.courseCode.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = courseColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = item.courseCode,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = courseColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Text(
                    text = updatedLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatNoteDate(millis: Long): String {
    val now = System.currentTimeMillis()
    val calNow = Calendar.getInstance().apply { timeInMillis = now }
    val calNote = Calendar.getInstance().apply { timeInMillis = millis }
    val isToday = calNow.get(Calendar.YEAR) == calNote.get(Calendar.YEAR) &&
        calNow.get(Calendar.DAY_OF_YEAR) == calNote.get(Calendar.DAY_OF_YEAR)
    return if (isToday) DateFormats.clock(millis) else DateFormats.shortDate(millis)
}


private suspend fun importNoteFile(
    context: Context,
    uri: Uri,
    viewModel: NotesViewModel,
    snackbarHostState: SnackbarHostState
) {
    val (displayName, readResult) = MarkdownFileReader.readFromUri(context, uri)

    when (readResult) {
        is MarkdownReadResult.TooLarge -> {
            snackbarHostState.showSnackbar("File is too large (limit 1 MB)")
        }
        is MarkdownReadResult.Unreadable -> {
            snackbarHostState.showSnackbar("Could not read that file")
        }
        is MarkdownReadResult.Success -> {
            try {
                val (rawTitle, rawBody) = NoteShare.noteFromImportedText(displayName, readResult.text)
                if (rawBody.length > NoteRepository.MAX_BODY) {
                    snackbarHostState.showSnackbar("File is too long for a note (limit 100,000 characters)")
                    return
                }
                val title = rawTitle.take(NoteRepository.MAX_TITLE)
                val body = rawBody.take(NoteRepository.MAX_BODY)
                val newId = viewModel.repository.saveNote(
                    id = 0L,
                    title = title,
                    body = body,
                    courseId = null
                )
                if (newId > 0L) {
                    viewModel.triggerSync()
                    snackbarHostState.showSnackbar("Note imported")
                } else {
                    snackbarHostState.showSnackbar("Could not read that file")
                }
            } catch (_: Exception) {
                snackbarHostState.showSnackbar("Could not read that file")
            }
        }
    }
}