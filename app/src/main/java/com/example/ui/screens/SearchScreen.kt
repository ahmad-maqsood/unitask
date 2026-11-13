package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.TaskStatus
import com.example.data.local.TaskWithCourse
import com.example.data.local.isMyTasksCourse
import com.example.util.AppFeature
import com.example.util.DateFormats

/**
 * Full-screen search across tasks, notes, courses, and settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onClose: () -> Unit,
    onNavigateToPersonalTask: (taskWithCourse: TaskWithCourse, isMyTask: Boolean) -> Unit,
    onNavigateToGroupTask: (taskTitle: String) -> Unit,
    onNavigateToCourse: (courseId: Long) -> Unit,
    onRestoreSemester: (semesterName: String) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToSettings: (page: SettingsPage?, opensAccount: Boolean) -> Unit = { _, _ -> },
    groupTasks: List<TaskWithCourse> = emptyList(),
    viewModel: SearchViewModel = viewModel()
) {
    LaunchedEffect(groupTasks) {
        viewModel.setGroupTasks(groupTasks)
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.resetSearch() }
    }

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val includeArchived by viewModel.includeArchived.collectAsStateWithLifecycle()
    val includeGuides by viewModel.includeGuides.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()

    var tasksExpanded by rememberSaveable { mutableStateOf(false) }
    var notesExpanded by rememberSaveable { mutableStateOf(false) }
    var coursesExpanded by rememberSaveable { mutableStateOf(false) }
    var settingsExpanded by rememberSaveable { mutableStateOf(false) }
    var guidesExpanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(searchQuery) {
        tasksExpanded = false
        notesExpanded = false
        coursesExpanded = false
        settingsExpanded = false
        guidesExpanded = false
    }

    var openedNoteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editorSession by rememberSaveable { mutableIntStateOf(0) }
    var archivedDialogSemester by remember { mutableStateOf<String?>(null) }
    var guideDialogTitle by rememberSaveable { mutableStateOf<String?>(null) }
    var showSearchOptions by rememberSaveable { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var isSearchFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    BackHandler(enabled = openedNoteId == null) {
        if (isSearchFocused) {
            focusManager.clearFocus()
        } else {
            onClose()
        }
    }

    if (openedNoteId != null) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            NoteEditorScreen(
                noteId = openedNoteId,
                viewModelSession = editorSession,
                onClose = { openedNoteId = null },
                onDeleted = { openedNoteId = null }
            )
        }
        return
    }

    guideDialogTitle?.let { title ->
        FeatureGuideDialog(
            onDismiss = { guideDialogTitle = null },
            initialFeatureTitle = title
        )
    }

    if (showSearchOptions) {
        SearchOptionsDialog(
            includeArchived = includeArchived,
            includeGuides = includeGuides,
            onIncludeArchivedChange = { viewModel.setIncludeArchived(it) },
            onIncludeGuidesChange = { viewModel.setIncludeGuides(it) },
            onDismiss = { showSearchOptions = false }
        )
    }

    if (archivedDialogSemester != null) {
        val semesterName = archivedDialogSemester ?: ""
        AlertDialog(
            onDismissRequest = { archivedDialogSemester = null },
            title = { Text("Archived in $semesterName") },
            text = {
                Text("This item is part of $semesterName. Restore the semester to view and edit its contents.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        archivedDialogSemester = null
                        onRestoreSemester(semesterName)
                    }
                ) {
                    Text("Restore semester")
                }
            },
            dismissButton = {
                TextButton(onClick = { archivedDialogSemester = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("search_screen"),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // The app draws edge to edge, so keep the search box below the status bar.
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.onSearchQueryChange(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { isSearchFocused = it.isFocused }
                        .testTag("search_input"),
                    placeholder = { Text("Search tasks, notes, courses, settings...") },
                    leadingIcon = {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.testTag("search_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear search"
                                    )
                                }
                            }
                            // The search options live behind this icon. It is tinted while any
                            // option is on, so it is clear that the search is widened.
                            IconButton(
                                onClick = { showSearchOptions = true },
                                modifier = Modifier.testTag("search_options_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Search options",
                                    tint = if (includeArchived || includeGuides) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                searchQuery.isBlank() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp, vertical = 48.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Text(
                            text = "Type to search tasks, notes, courses and settings",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                results.isEmpty -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp, vertical = 48.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Text(
                            text = "Nothing found for '$searchQuery'",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Section: Tasks
                        if (results.tasks.isNotEmpty()) {
                            item(key = "header_tasks") {
                                Text(
                                    text = "Tasks (${results.tasks.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                )
                            }
                            val visibleTasks = visibleSectionItems(results.tasks, tasksExpanded)
                            items(
                                items = visibleTasks,
                                key = { "task_${it.task.id}_${it.archivedSemester ?: "act"}" }
                            ) { taskResult ->
                                SearchTaskRow(
                                    result = taskResult,
                                    onClick = {
                                        when (val target = resolveTaskClickTarget(taskResult)) {
                                            is SearchNavigationTarget.ShowArchivedDialog -> {
                                                archivedDialogSemester = target.semesterName
                                            }
                                            is SearchNavigationTarget.DeadlinesGroupTask -> {
                                                onNavigateToGroupTask(target.taskTitle)
                                            }
                                            is SearchNavigationTarget.EditPersonalTask -> {
                                                onNavigateToPersonalTask(target.taskWithCourse, target.isMyTask)
                                            }
                                            else -> {}
                                        }
                                    }
                                )
                            }
                            if (shouldShowExpandButton(results.tasks.size, tasksExpanded)) {
                                item(key = "more_tasks") {
                                    TextButton(
                                        onClick = { tasksExpanded = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(formatShowAllButtonText(results.tasks.size))
                                    }
                                }
                            }
                        }

                        // Section: Notes
                        if (results.notes.isNotEmpty()) {
                            item(key = "header_notes") {
                                Text(
                                    text = "Notes (${results.notes.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                                )
                            }
                            val visibleNotes = visibleSectionItems(results.notes, notesExpanded)
                            items(
                                items = visibleNotes,
                                key = { "note_${it.noteItem.note.id}_${it.archivedSemester ?: "act"}" }
                            ) { noteResult ->
                                SearchNoteRow(
                                    result = noteResult,
                                    onClick = {
                                        when (val target = resolveNoteClickTarget(noteResult)) {
                                            is SearchNavigationTarget.ShowArchivedDialog -> {
                                                archivedDialogSemester = target.semesterName
                                            }
                                            is SearchNavigationTarget.OpenNote -> {
                                                openedNoteId = target.noteId
                                                editorSession = NoteEditorSessions.next()
                                            }
                                            else -> {}
                                        }
                                    }
                                )
                            }
                            if (shouldShowExpandButton(results.notes.size, notesExpanded)) {
                                item(key = "more_notes") {
                                    TextButton(
                                        onClick = { notesExpanded = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(formatShowAllButtonText(results.notes.size))
                                    }
                                }
                            }
                        }

                        // Section: Courses
                        if (results.courses.isNotEmpty()) {
                            item(key = "header_courses") {
                                Text(
                                    text = "Courses (${results.courses.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                                )
                            }
                            val visibleCourses = visibleSectionItems(results.courses, coursesExpanded)
                            items(
                                items = visibleCourses,
                                key = { "course_${it.course.id}_${it.archivedSemester ?: "act"}" }
                            ) { courseResult ->
                                SearchCourseRow(
                                    result = courseResult,
                                    onClick = {
                                        when (val target = resolveCourseClickTarget(courseResult)) {
                                            is SearchNavigationTarget.ShowArchivedDialog -> {
                                                archivedDialogSemester = target.semesterName
                                            }
                                            is SearchNavigationTarget.OpenCourse -> {
                                                onNavigateToCourse(target.courseId)
                                            }
                                            else -> {}
                                        }
                                    }
                                )
                            }
                            if (shouldShowExpandButton(results.courses.size, coursesExpanded)) {
                                item(key = "more_courses") {
                                    TextButton(
                                        onClick = { coursesExpanded = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(formatShowAllButtonText(results.courses.size))
                                    }
                                }
                            }
                        }

                        // Section: Settings
                        if (results.settings.isNotEmpty()) {
                            item(key = "header_settings") {
                                Text(
                                    text = "Settings (${results.settings.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                                )
                            }
                            val visibleSettings = visibleSectionItems(results.settings, settingsExpanded)
                            items(
                                items = visibleSettings,
                                key = { "setting_${it.id}" }
                            ) { entry ->
                                SearchSettingRow(
                                    entry = entry,
                                    onClick = {
                                        val target = resolveSettingClickTarget(entry)
                                        if (target is SearchNavigationTarget.OpenSetting) {
                                            onNavigateToSettings(target.page, target.opensAccount)
                                        }
                                    }
                                )
                            }
                            if (shouldShowExpandButton(results.settings.size, settingsExpanded)) {
                                item(key = "more_settings") {
                                    TextButton(
                                        onClick = { settingsExpanded = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(formatShowAllButtonText(results.settings.size))
                                    }
                                }
                            }
                        }

                        // Section: App guides ("Learn to use the app"), only with the switch on
                        if (results.guides.isNotEmpty()) {
                            item(key = "header_guides") {
                                Text(
                                    text = "App guides (${results.guides.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                                )
                            }
                            val visibleGuides = visibleSectionItems(results.guides, guidesExpanded)
                            items(
                                items = visibleGuides,
                                key = { "guide_${it.title}" }
                            ) { feature ->
                                SearchGuideRow(
                                    feature = feature,
                                    onClick = {
                                        val target = resolveGuideClickTarget(feature)
                                        if (target is SearchNavigationTarget.OpenGuide) {
                                            guideDialogTitle = target.featureTitle
                                        }
                                    }
                                )
                            }
                            if (shouldShowExpandButton(results.guides.size, guidesExpanded)) {
                                item(key = "more_guides") {
                                    TextButton(
                                        onClick = { guidesExpanded = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(formatShowAllButtonText(results.guides.size))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The search options: what else to include in the results. Each switch applies at once. */
@Composable
private fun SearchOptionsDialog(
    includeArchived: Boolean,
    includeGuides: Boolean,
    onIncludeArchivedChange: (Boolean) -> Unit,
    onIncludeGuidesChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Search options") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Include archived",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = includeArchived,
                        onCheckedChange = onIncludeArchivedChange,
                        modifier = Modifier.testTag("search_archived_switch")
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Include app guides",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = includeGuides,
                        onCheckedChange = onIncludeGuidesChange,
                        modifier = Modifier.testTag("search_guides_switch")
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("search_options_done_button")
            ) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun SearchTaskRow(
    result: SearchTaskResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = result.task.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (result.isArchived) {
                    Spacer(modifier = Modifier.width(8.dp))
                    SemesterChip(semesterName = result.archivedSemester ?: "")
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val courseCode = if (result.course?.isMyTasksCourse() == true) "My tasks" else result.course?.code.orEmpty()
                if (courseCode.isNotBlank()) {
                    Text(
                        text = courseCode,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (result.task.dueDateEpochMillis > 0L) {
                    Text(
                        text = DateFormats.shortDate(result.task.dueDateEpochMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                val statusLabel = when (result.task.status) {
                    TaskStatus.COMPLETED -> "Completed"
                    TaskStatus.PENDING -> "Pending"
                }
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (result.task.status == TaskStatus.COMPLETED) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun SearchNoteRow(
    result: SearchNoteResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = result.noteItem.note.title.ifBlank { "Untitled note" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (result.isArchived) {
                    Spacer(modifier = Modifier.width(8.dp))
                    SemesterChip(semesterName = result.archivedSemester ?: "")
                }
            }

            if (result.snippet.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                val highlightColor = MaterialTheme.colorScheme.primary
                val annotatedSnippet = remember(result.snippet, result.matchRange, highlightColor) {
                    buildAnnotatedString {
                        // Show line breaks as spaces so the two-line preview is not used up by
                        // short lines and the matched word stays visible. Each character is
                        // swapped one for one, so the highlight range below is still correct.
                        append(result.snippet.replace('\n', ' ').replace('\r', ' '))
                        val range = result.matchRange
                        if (range != null && range.first in 0 until result.snippet.length) {
                            val end = minOf(result.snippet.length, range.last + 1)
                            addStyle(
                                SpanStyle(
                                    fontWeight = FontWeight.Bold,
                                    color = highlightColor
                                ),
                                range.first,
                                end
                            )
                        }
                    }
                }
                Text(
                    text = annotatedSnippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val courseCode = result.noteItem.courseCode
            val folderName = result.folderName
            if (!courseCode.isNullOrBlank() || folderName != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!courseCode.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = courseCode,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (folderName != null) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "Folder",
                            tint = folderColorOrNull(result.folderColorHex) ?: MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = folderName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchCourseRow(
    result: SearchCourseResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val code = if (result.course.isMyTasksCourse()) "My tasks" else result.course.code
                Text(
                    text = code,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (result.isArchived) {
                    Spacer(modifier = Modifier.width(8.dp))
                    SemesterChip(semesterName = result.archivedSemester ?: "")
                }
            }

            val name = if (result.course.isMyTasksCourse()) "Personal tasks" else result.course.name
            if (name.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (result.course.instructor.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = result.course.instructor,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SearchSettingRow(
    entry: SettingsSearchEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("search_setting_${entry.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = entry.summary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Settings > ${entry.pageLabel}".replace("Settings > Settings", "Settings"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SearchGuideRow(
    feature: AppFeature,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("search_guide_${feature.title}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val vector = featureIcon(feature.icon)
            if (vector != null) {
                Icon(
                    imageVector = vector,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = feature.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = feature.description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Learn to use the app",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SemesterChip(
    semesterName: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = modifier
    ) {
        Text(
            text = semesterName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
