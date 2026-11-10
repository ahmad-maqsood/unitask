package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.LectureSlotWithCourse
import com.example.data.local.NoteListItem
import com.example.data.local.TaskWithCourse
import com.example.data.local.isMyTasksCourse
import kotlinx.coroutines.launch
import com.example.ui.theme.rememberCourseColor

/**
 * Screen showing items in the trash (Tasks, Notes and Classes) with tabs to restore or delete forever.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentlyDeletedScreen(
    onClose: () -> Unit,
    viewModel: RecentlyDeletedViewModel = viewModel()
) {
    val deletedTasks by viewModel.deletedTasks.collectAsStateWithLifecycle()
    val deletedNotes by viewModel.deletedNotes.collectAsStateWithLifecycle()
    val deletedClasses by viewModel.deletedClasses.collectAsStateWithLifecycle()

    val validDeletedClasses = remember(deletedClasses) {
        deletedClasses.filter { it.course != null && !it.course.isDeleted }
    }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var taskToDeleteForever by remember { mutableStateOf<TaskWithCourse?>(null) }
    var noteToDeleteForever by remember { mutableStateOf<NoteListItem?>(null) }
    var classToDeleteForever by remember { mutableStateOf<LectureSlotWithCourse?>(null) }
    var showEmptyTrashDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    BackHandler(enabled = true) {
        onClose()
    }

    val currentTabHasItems = when (selectedTab) {
        0 -> deletedTasks.isNotEmpty()
        1 -> deletedNotes.isNotEmpty()
        else -> validDeletedClasses.isNotEmpty()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("recently_deleted_screen"),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    // Shrinks a little on narrow screens so it still fits beside "Empty trash".
                    ShrinkToFitText(
                        text = "Recently deleted",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        minFontSize = 16.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("recently_deleted_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (currentTabHasItems) {
                        TextButton(
                            onClick = { showEmptyTrashDialog = true },
                            modifier = Modifier.testTag("recently_deleted_empty_button")
                        ) {
                            Text(
                                text = "Empty trash",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(formatTabTitle("Tasks", deletedTasks.size)) },
                    modifier = Modifier.testTag("recently_deleted_tab_tasks")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(formatTabTitle("Notes", deletedNotes.size)) },
                    modifier = Modifier.testTag("recently_deleted_tab_notes")
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text(formatTabTitle("Classes", validDeletedClasses.size)) },
                    modifier = Modifier.testTag("recently_deleted_tab_classes")
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Items are removed for good 30 days after deleting.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }

            val now = remember { System.currentTimeMillis() }

            when (selectedTab) {
                0 -> {
                    if (deletedTasks.isEmpty()) {
                        TrashEmptyState(
                            title = "No deleted tasks",
                            subtitle = "Deleted tasks will appear here."
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("tasks_trash_list"),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(deletedTasks, key = { it.task.id }) { item ->
                                DeletedTaskCard(
                                    item = item,
                                    now = now,
                                    restoreDisabledReason = viewModel.restoreDisabledReason(item),
                                    onRestore = {
                                        viewModel.restoreTask(item.task.id)
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Task restored")
                                        }
                                    },
                                    onDeleteForever = { taskToDeleteForever = item }
                                )
                            }
                        }
                    }
                }
                1 -> {
                    if (deletedNotes.isEmpty()) {
                        TrashEmptyState(
                            title = "No deleted notes",
                            subtitle = "Deleted notes will appear here."
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("notes_trash_list"),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(deletedNotes, key = { it.note.id }) { item ->
                                DeletedNoteCard(
                                    item = item,
                                    now = now,
                                    onRestore = {
                                        viewModel.restoreNote(item.note.id)
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Note restored")
                                        }
                                    },
                                    onDeleteForever = { noteToDeleteForever = item }
                                )
                            }
                        }
                    }
                }
                else -> {
                    if (validDeletedClasses.isEmpty()) {
                        TrashEmptyState(
                            title = "No deleted classes",
                            subtitle = "Deleted classes will appear here."
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("classes_trash_list"),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(validDeletedClasses, key = { it.slot.id }) { item ->
                                DeletedClassCard(
                                    item = item,
                                    now = now,
                                    restoreDisabledReason = viewModel.restoreDisabledReason(item),
                                    onRestore = {
                                        viewModel.restoreClass(item.slot.id)
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Class restored")
                                        }
                                    },
                                    onDeleteForever = { classToDeleteForever = item }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (taskToDeleteForever != null) {
        ConfirmDeleteDialog(
            title = "Delete forever?",
            message = "This task will be permanently deleted. This cannot be undone.",
            onConfirm = {
                taskToDeleteForever?.let { viewModel.deleteTaskForever(it.task.id) }
                taskToDeleteForever = null
            },
            onDismiss = { taskToDeleteForever = null }
        )
    }

    if (noteToDeleteForever != null) {
        ConfirmDeleteDialog(
            title = "Delete forever?",
            message = "This note will be permanently deleted. This cannot be undone.",
            onConfirm = {
                noteToDeleteForever?.let { viewModel.deleteNoteForever(it.note.id) }
                noteToDeleteForever = null
            },
            onDismiss = { noteToDeleteForever = null }
        )
    }

    if (classToDeleteForever != null) {
        ConfirmDeleteDialog(
            title = "Delete forever?",
            message = "This class will be permanently deleted. This cannot be undone.",
            onConfirm = {
                classToDeleteForever?.let { viewModel.deleteClassForever(it.slot.id) }
                classToDeleteForever = null
            },
            onDismiss = { classToDeleteForever = null }
        )
    }

    if (showEmptyTrashDialog) {
        val emptyMessage = when (selectedTab) {
            0 -> "All tasks in the trash will be permanently deleted. This cannot be undone."
            1 -> "All notes in the trash will be permanently deleted. This cannot be undone."
            else -> "All classes in the trash will be permanently deleted. This cannot be undone."
        }
        ConfirmDeleteDialog(
            title = "Empty trash?",
            message = emptyMessage,
            onConfirm = {
                when (selectedTab) {
                    0 -> viewModel.emptyTaskTrash()
                    1 -> viewModel.emptyNoteTrash()
                    else -> viewModel.emptyClassTrash()
                }
                showEmptyTrashDialog = false
            },
            onDismiss = { showEmptyTrashDialog = false }
        )
    }
}

@Composable
private fun TrashEmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
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
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun DeletedTaskCard(
    item: TaskWithCourse,
    now: Long,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
    modifier: Modifier = Modifier,
    restoreDisabledReason: String? = null
) {
    val task = item.task
    val course = item.course
    val isMy = course?.isMyTasksCourse() == true
    val courseLabel = if (isMy) "My tasks" else course?.code.orEmpty()
    val courseColor = rememberCourseColor(course?.colorHex)

    val daysLeft = remember(task.deletedAt, now) {
        daysLeftInTrash(task.deletedAt, now)
    }
    val daysLeftText = remember(daysLeft) {
        formatTrashDaysLeft(daysLeft)
    }
    val dueText = remember(task.dueDateEpochMillis, task.dueTimeHour, task.dueTimeMinute) {
        formatTrashTaskDueDate(task.dueDateEpochMillis, task.dueTimeHour, task.dueTimeMinute)
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
            .testTag("trash_task_card_${task.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (dueText.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = dueText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (courseLabel.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = courseColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = courseLabel,
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
                    text = daysLeftText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onRestore,
                    enabled = restoreDisabledReason == null,
                    modifier = Modifier
                        .testTag("trash_restore_task_${task.id}")
                        .weight(1f, fill = false)
                ) {
                    Text(
                        text = restoreDisabledReason ?: "Restore",
                        textAlign = if (restoreDisabledReason != null) TextAlign.End else TextAlign.Start
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = onDeleteForever,
                    modifier = Modifier.testTag("trash_delete_forever_task_${task.id}")
                ) {
                    Text(
                        text = "Delete forever",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun DeletedNoteCard(
    item: NoteListItem,
    now: Long,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayTitle = remember(item.note) { noteDisplayTitle(item.note) }
    val previewText = remember(item.note.body) { notePreviewText(item.note.body, maxLines = 2) }
    val daysLeft = remember(item.note.deletedAt, now) {
        daysLeftInTrash(item.note.deletedAt, now)
    }
    val daysLeftText = remember(daysLeft) {
        formatTrashDaysLeft(daysLeft)
    }

    val courseColor = rememberCourseColor(item.courseColorHex)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .testTag("trash_card_${item.note.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Text(
                text = displayTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

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
                    text = daysLeftText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onRestore,
                    modifier = Modifier.testTag("trash_restore_button_${item.note.id}")
                ) {
                    Text("Restore")
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = onDeleteForever,
                    modifier = Modifier.testTag("trash_delete_forever_button_${item.note.id}")
                ) {
                    Text(
                        text = "Delete forever",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun DeletedClassCard(
    item: LectureSlotWithCourse,
    now: Long,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
    modifier: Modifier = Modifier,
    restoreDisabledReason: String? = null
) {
    val slot = item.slot
    val course = item.course
    val courseCode = course?.code.orEmpty()
    val courseName = course?.name.orEmpty()
    val courseColor = rememberCourseColor(course?.colorHex)

    val daysLeft = remember(slot.deletedAt, now) {
        daysLeftInTrash(slot.deletedAt, now)
    }
    val daysLeftText = remember(daysLeft) {
        formatTrashDaysLeft(daysLeft)
    }
    val timeText = remember(slot.dayOfWeek, slot.startHour, slot.startMinute, slot.endHour, slot.endMinute) {
        formatTrashClassTime(slot.dayOfWeek, slot.startHour, slot.startMinute, slot.endHour, slot.endMinute)
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
            .testTag("trash_class_card_${slot.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (courseCode.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = courseColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = courseCode,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = courseColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                if (courseName.isNotBlank()) {
                    Text(
                        text = courseName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = timeText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (slot.classroom.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = slot.classroom,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = daysLeftText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onRestore,
                    enabled = restoreDisabledReason == null,
                    modifier = Modifier
                        .testTag("trash_restore_class_${slot.id}")
                        .weight(1f, fill = false)
                ) {
                    Text(
                        text = restoreDisabledReason ?: "Restore",
                        textAlign = if (restoreDisabledReason != null) TextAlign.End else TextAlign.Start
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = onDeleteForever,
                    modifier = Modifier.testTag("trash_delete_forever_class_${slot.id}")
                ) {
                    Text(
                        text = "Delete forever",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
