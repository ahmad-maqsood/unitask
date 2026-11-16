package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.TaskItem
import com.example.data.local.TaskStatus
import com.example.data.local.TaskWithCourse
import com.example.data.local.deadlineMillis
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar

@Composable
fun MyTasksTab(
    tasks: List<TaskWithCourse>,
    onStatusCycle: (TaskItem) -> Unit,
    onUndoTick: (TaskItem) -> Unit,
    onEditTask: (TaskItem) -> Unit,
    onDeleteTask: (TaskItem) -> Unit,
    onUndoDeleteTask: (TaskItem) -> Unit,
    onQuickAdd: (String) -> Unit,
    onAddTaskClick: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    onChecklistToggle: ((TaskItem, Int) -> Unit)? = null
) {
    // Done starts collapsed, like before. Every other section starts open.
    val sectionCollapse = rememberSectionCollapse(TaskSectionKey.DONE)
    val coroutineScope = rememberCoroutineScope()
    var snackbarJob by remember { mutableStateOf<Job?>(null) }

    val handleSwipeTickDone: (TaskItem) -> Unit = { task ->
        snackbarJob?.cancel()
        onStatusCycle(task)
        snackbarJob = coroutineScope.launch {
            try {
                val result = withTimeoutOrNull(5000L) {
                    snackbarHostState.showSnackbar(
                        message = if (task.status == TaskStatus.COMPLETED) "\"${task.title}\" marked as not done" else "\"${task.title}\" marked as done",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Indefinite
                    )
                }
                if (result == SnackbarResult.ActionPerformed) {
                    onUndoTick(task)
                }
            } finally {
                snackbarHostState.currentSnackbarData?.dismiss()
            }
        }
    }

    val handleSwipeDelete: (TaskItem) -> Unit = { task ->
        snackbarJob?.cancel()
        onDeleteTask(task)
        snackbarJob = coroutineScope.launch {
            try {
                val result = withTimeoutOrNull(5000L) {
                    snackbarHostState.showSnackbar(
                        message = "Task moved to Recently deleted",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Indefinite
                    )
                }
                if (result == SnackbarResult.ActionPerformed) {
                    onUndoDeleteTask(task)
                }
            } finally {
                snackbarHostState.currentSnackbarData?.dismiss()
            }
        }
    }

    // Re-sorted when the minute changes, so a task moves to Overdue (or Today) on its own.
    val now by rememberMinuteClock()
    val sections by remember(tasks) { derivedStateOf { groupTasks(tasks, now) } }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Quick add field at the top
        item(key = "quick_add_field") {
            QuickAddTaskField(
                onQuickAdd = onQuickAdd,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        if (tasks.isEmpty()) {
            item(key = "empty_state") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("my_tasks_empty_state")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.TaskAlt,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.my_tasks_no_personal_tasks_yet),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.my_tasks_add_tasks_to_dos_or),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = onAddTaskClick,
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("empty_state_add_my_task_button")
                            ) {
                                Text(stringResource(R.string.my_tasks_add_task))
                            }
                        }
                    }
                }
            }
        } else {
            // Overdue section
            if (sections.overdue.isNotEmpty()) {
                item(key = "header_overdue") {
                    CollapsibleSectionHeader(
                        title = stringResource(R.string.deadlines_overdue),
                        count = sections.overdue.size,
                        isOverdue = true,
                        isCollapsed = sectionCollapse.isCollapsed(TaskSectionKey.OVERDUE),
                        onToggle = { sectionCollapse.toggle(TaskSectionKey.OVERDUE) }
                    )
                }
                items(sections.overdue.shownUnless(sectionCollapse.isCollapsed(TaskSectionKey.OVERDUE)), key = { it.task.id }) { taskWithCourse ->
                    SwipeableTaskItem(
                        taskWithCourse = taskWithCourse,
                        onStatusCycle = onStatusCycle,
                        onEditClick = onEditTask,
                        onDeleteClick = handleSwipeDelete,
                        onSwipeTickDone = handleSwipeTickDone,
                        onSwipeDelete = handleSwipeDelete,
                        modifier = Modifier.animateItem(),
                        onChecklistToggle = onChecklistToggle
                    )
                }
            }

            // Today section
            if (sections.today.isNotEmpty()) {
                item(key = "header_today") {
                    CollapsibleSectionHeader(
                        title = stringResource(R.string.my_tasks_today),
                        count = sections.today.size,
                        isCollapsed = sectionCollapse.isCollapsed(TaskSectionKey.TODAY),
                        onToggle = { sectionCollapse.toggle(TaskSectionKey.TODAY) }
                    )
                }
                items(sections.today.shownUnless(sectionCollapse.isCollapsed(TaskSectionKey.TODAY)), key = { it.task.id }) { taskWithCourse ->
                    SwipeableTaskItem(
                        taskWithCourse = taskWithCourse,
                        onStatusCycle = onStatusCycle,
                        onEditClick = onEditTask,
                        onDeleteClick = handleSwipeDelete,
                        onSwipeTickDone = handleSwipeTickDone,
                        onSwipeDelete = handleSwipeDelete,
                        modifier = Modifier.animateItem(),
                        onChecklistToggle = onChecklistToggle
                    )
                }
            }

            // Tomorrow section
            if (sections.tomorrow.isNotEmpty()) {
                item(key = "header_tomorrow") {
                    CollapsibleSectionHeader(
                        title = stringResource(R.string.my_tasks_tomorrow),
                        count = sections.tomorrow.size,
                        isCollapsed = sectionCollapse.isCollapsed(TaskSectionKey.TOMORROW),
                        onToggle = { sectionCollapse.toggle(TaskSectionKey.TOMORROW) }
                    )
                }
                items(sections.tomorrow.shownUnless(sectionCollapse.isCollapsed(TaskSectionKey.TOMORROW)), key = { it.task.id }) { taskWithCourse ->
                    SwipeableTaskItem(
                        taskWithCourse = taskWithCourse,
                        onStatusCycle = onStatusCycle,
                        onEditClick = onEditTask,
                        onDeleteClick = handleSwipeDelete,
                        onSwipeTickDone = handleSwipeTickDone,
                        onSwipeDelete = handleSwipeDelete,
                        modifier = Modifier.animateItem(),
                        onChecklistToggle = onChecklistToggle
                    )
                }
            }

            // Later section
            if (sections.later.isNotEmpty()) {
                item(key = "header_later") {
                    CollapsibleSectionHeader(
                        title = stringResource(R.string.backup_reminder_card_later),
                        count = sections.later.size,
                        isCollapsed = sectionCollapse.isCollapsed(TaskSectionKey.LATER),
                        onToggle = { sectionCollapse.toggle(TaskSectionKey.LATER) }
                    )
                }
                items(sections.later.shownUnless(sectionCollapse.isCollapsed(TaskSectionKey.LATER)), key = { it.task.id }) { taskWithCourse ->
                    SwipeableTaskItem(
                        taskWithCourse = taskWithCourse,
                        onStatusCycle = onStatusCycle,
                        onEditClick = onEditTask,
                        onDeleteClick = handleSwipeDelete,
                        onSwipeTickDone = handleSwipeTickDone,
                        onSwipeDelete = handleSwipeDelete,
                        modifier = Modifier.animateItem(),
                        onChecklistToggle = onChecklistToggle
                    )
                }
            }

            // Turned off section
            if (sections.turnedOff.isNotEmpty()) {
                item(key = "header_turned_off") {
                    CollapsibleSectionHeader(
                        title = stringResource(R.string.deadlines_turned_off),
                        count = sections.turnedOff.size,
                        isCollapsed = sectionCollapse.isCollapsed(TaskSectionKey.TURNED_OFF),
                        onToggle = { sectionCollapse.toggle(TaskSectionKey.TURNED_OFF) }
                    )
                }
                items(sections.turnedOff.shownUnless(sectionCollapse.isCollapsed(TaskSectionKey.TURNED_OFF)), key = { it.task.id }) { taskWithCourse ->
                    SwipeableTaskItem(
                        taskWithCourse = taskWithCourse,
                        onStatusCycle = onStatusCycle,
                        onEditClick = onEditTask,
                        onDeleteClick = handleSwipeDelete,
                        onSwipeTickDone = handleSwipeTickDone,
                        onSwipeDelete = handleSwipeDelete,
                        modifier = Modifier.animateItem(),
                        onChecklistToggle = onChecklistToggle
                    )
                }
            }

            // Done section
            if (sections.done.isNotEmpty()) {
                item(key = "header_done") {
                    CollapsibleSectionHeader(
                        title = stringResource(R.string.common_done),
                        count = sections.done.size,
                        isCollapsed = sectionCollapse.isCollapsed(TaskSectionKey.DONE),
                        onToggle = { sectionCollapse.toggle(TaskSectionKey.DONE) }
                    )
                }
                items(sections.done.shownUnless(sectionCollapse.isCollapsed(TaskSectionKey.DONE)), key = { it.task.id }) { taskWithCourse ->
                    SwipeableTaskItem(
                        taskWithCourse = taskWithCourse,
                        onStatusCycle = onStatusCycle,
                        onEditClick = onEditTask,
                        onDeleteClick = handleSwipeDelete,
                        onSwipeTickDone = handleSwipeTickDone,
                        onSwipeDelete = handleSwipeDelete,
                        modifier = Modifier.animateItem(),
                        onChecklistToggle = onChecklistToggle
                    )
                }
            }
        }
    }
}

@Composable
fun QuickAddTaskField(
    onQuickAdd: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var title by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    val submit = {
        if (title.isNotBlank()) {
            onQuickAdd(title.trim())
            title = ""
            focusManager.clearFocus()
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("my_tasks_quick_add_container"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(R.string.my_tasks_add_quick_task),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            BasicTextField(
                value = title,
                onValueChange = { title = it.take(200) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { submit() }
                ),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (title.isEmpty()) {
                            Text(
                                text = stringResource(R.string.my_tasks_add_a_task),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("my_tasks_quick_add_input")
            )
            if (title.isNotBlank()) {
                IconButton(
                    onClick = { submit() },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("my_tasks_quick_add_submit_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(R.string.my_tasks_save_task),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableTaskItem(
    taskWithCourse: TaskWithCourse,
    onStatusCycle: (TaskItem) -> Unit,
    onEditClick: (TaskItem) -> Unit,
    onDeleteClick: (TaskItem) -> Unit,
    onSwipeTickDone: (TaskItem) -> Unit,
    onSwipeDelete: (TaskItem) -> Unit,
    modifier: Modifier = Modifier,
    onChecklistToggle: ((TaskItem, Int) -> Unit)? = null
) {
    val task = taskWithCourse.task
    val haptics = rememberAppHaptics()
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            when (dismissValue) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    haptics.firm()
                    onSwipeTickDone(task)
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    haptics.firm()
                    onSwipeDelete(task)
                    true
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color by animateColorAsState(
                targetValue = when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.StartToEnd -> swipeToDoneColor(task.status == TaskStatus.COMPLETED)
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error
                    SwipeToDismissBoxValue.Settled -> Color.Transparent
                },
                label = "swipe_bg_color"
            )

            val alignment = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                else -> Alignment.Center
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment
            ) {
                when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> {
                        val isDone = task.status == TaskStatus.COMPLETED
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isDone) Icons.AutoMirrored.Filled.Undo else Icons.Default.Check,
                                contentDescription = stringResource(if (isDone) R.string.deadline_task_cards_mark_not_done else R.string.deadline_task_cards_mark_done),
                                tint = Color.White
                            )
                            Text(
                                text = stringResource(if (isDone) R.string.deadline_task_cards_not_done else R.string.common_done),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                    SwipeToDismissBoxValue.EndToStart -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.common_delete),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = stringResource(R.string.common_delete),
                                tint = Color.White
                            )
                        }
                    }
                    else -> {}
                }
            }
        }
    ) {
        TaskCard(
            taskWithCourse = taskWithCourse,
            onStatusCycle = onStatusCycle,
            onEditClick = onEditClick,
            onDeleteClick = onDeleteClick,
            onChecklistToggle = onChecklistToggle
        )
    }
}

private data class TaskSections(
    val overdue: List<TaskWithCourse>,
    val today: List<TaskWithCourse>,
    val tomorrow: List<TaskWithCourse>,
    val later: List<TaskWithCourse>,
    // Open tasks that are turned off: never overdue or due today, whatever their date.
    val turnedOff: List<TaskWithCourse>,
    val done: List<TaskWithCourse>
)

private fun groupTasks(tasks: List<TaskWithCourse>, nowMillis: Long = System.currentTimeMillis()): TaskSections {
    val calNow = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val currentYear = calNow.get(Calendar.YEAR)
    val currentDayOfYear = calNow.get(Calendar.DAY_OF_YEAR)

    val calTomorrow = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        add(Calendar.DAY_OF_YEAR, 1)
    }
    val tomorrowYear = calTomorrow.get(Calendar.YEAR)
    val tomorrowDayOfYear = calTomorrow.get(Calendar.DAY_OF_YEAR)

    val overdue = mutableListOf<TaskWithCourse>()
    val today = mutableListOf<TaskWithCourse>()
    val tomorrow = mutableListOf<TaskWithCourse>()
    val later = mutableListOf<TaskWithCourse>()
    val turnedOff = mutableListOf<TaskWithCourse>()
    val done = mutableListOf<TaskWithCourse>()

    for (item in tasks) {
        val task = item.task
        if (task.status == TaskStatus.COMPLETED) {
            done.add(item)
            continue
        }

        if (!task.isEnabled) {
            turnedOff.add(item)
            continue
        }

        val taskDeadline = task.deadlineMillis
        if (taskDeadline < nowMillis) {
            overdue.add(item)
        } else {
            val taskCal = Calendar.getInstance().apply { timeInMillis = task.dueDateEpochMillis }
            val taskYear = taskCal.get(Calendar.YEAR)
            val taskDayOfYear = taskCal.get(Calendar.DAY_OF_YEAR)

            if (taskYear == currentYear && taskDayOfYear == currentDayOfYear) {
                today.add(item)
            } else if (taskYear == tomorrowYear && taskDayOfYear == tomorrowDayOfYear) {
                tomorrow.add(item)
            } else {
                later.add(item)
            }
        }
    }

    return TaskSections(
        overdue = overdue.sortedBy { it.task.deadlineMillis },
        today = today.sortedBy { it.task.deadlineMillis },
        tomorrow = tomorrow.sortedBy { it.task.deadlineMillis },
        later = later.sortedBy { it.task.deadlineMillis },
        turnedOff = turnedOff.sortedBy { it.task.deadlineMillis },
        done = done.sortedByDescending { it.task.deadlineMillis }
    )
}
