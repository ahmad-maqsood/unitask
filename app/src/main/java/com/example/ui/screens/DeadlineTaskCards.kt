package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.groups.GroupTaskEntry
import com.example.data.local.TaskItem
import com.example.data.local.TaskStatus
import com.example.data.local.TaskWithCourse
import androidx.compose.animation.animateColorAsState
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.rememberUpdatedState

@Composable
internal fun SelectableTaskCard(
    taskWithCourse: TaskWithCourse,
    isBatchMode: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onStatusCycle: (TaskItem) -> Unit,
    onEditTask: (TaskItem) -> Unit,
    onDeleteTask: (TaskItem) -> Unit,
    modifier: Modifier = Modifier,
    groupEntry: GroupTaskEntry? = null,
    onSwipeToggleDone: ((TaskItem) -> Unit)? = null,
    onChecklistToggle: ((TaskItem, Int) -> Unit)? = null
) {
    val checklistToggle = if (groupEntry == null) onChecklistToggle else null
    val haptics = rememberAppHaptics()
    if (isBatchMode) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = {
                    haptics.tick()
                    onToggleSelect()
                },
                modifier = Modifier.padding(end = 4.dp)
            )
            Box(
                modifier = Modifier.weight(1f).clickable {
                    haptics.tick()
                    onToggleSelect()
                }
            ) {
                TaskCard(
                    taskWithCourse = taskWithCourse,
                    onStatusCycle = onStatusCycle,
                    onEditClick = onEditTask,
                    onDeleteClick = onDeleteTask,
                    groupEntry = groupEntry,
                    onChecklistToggle = checklistToggle
                )
            }
        }
    } else {
        if (onSwipeToggleDone != null) {
            SwipeDoneTaskCard(
                taskWithCourse = taskWithCourse,
                onSwipeToggleDone = onSwipeToggleDone,
                onStatusCycle = onStatusCycle,
                onEditTask = onEditTask,
                onDeleteTask = onDeleteTask,
                modifier = modifier,
                groupEntry = groupEntry,
                onChecklistToggle = checklistToggle
            )
        } else {
            TaskCard(
                taskWithCourse = taskWithCourse,
                onStatusCycle = onStatusCycle,
                onEditClick = onEditTask,
                onDeleteClick = onDeleteTask,
                modifier = modifier,
                groupEntry = groupEntry,
                onChecklistToggle = checklistToggle
            )
        }
    }
}
/**
 * A task card you can swipe to the right to mark done (or not done).
 * Swiping left does nothing here: deleting stays behind the card's own delete button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwipeDoneTaskCard(
    taskWithCourse: TaskWithCourse,
    onSwipeToggleDone: (TaskItem) -> Unit,
    onStatusCycle: (TaskItem) -> Unit,
    onEditTask: (TaskItem) -> Unit,
    onDeleteTask: (TaskItem) -> Unit,
    modifier: Modifier = Modifier,
    groupEntry: GroupTaskEntry? = null,
    onChecklistToggle: ((TaskItem, Int) -> Unit)? = null
) {
    val task = taskWithCourse.task
    val isDone = task.status == TaskStatus.COMPLETED
    // The swipe state lives on after the task changes, so always read the newest values.
    val latestTask by rememberUpdatedState(task)
    val latestOnSwipe by rememberUpdatedState(onSwipeToggleDone)
    val haptics = rememberAppHaptics()
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd) {
                haptics.firm()
                latestOnSwipe(latestTask)
            }
            false // always snap back; the task itself moves to its new section
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromEndToStart = false,
        backgroundContent = {
            val color by animateColorAsState(
                targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd) {
                    if (isDone) Color(0xFFD97706) else Color(0xFF16A34A)
                } else {
                    Color.Transparent
                },
                label = "deadline_swipe_bg"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isDone) Icons.AutoMirrored.Filled.Undo else Icons.Default.Check,
                            contentDescription = if (isDone) "Mark not done" else "Mark done",
                            tint = Color.White
                        )
                        Text(
                            text = if (isDone) "Not done" else "Done",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    ) {
        TaskCard(
            taskWithCourse = taskWithCourse,
            onStatusCycle = onStatusCycle,
            onEditClick = onEditTask,
            onDeleteClick = onDeleteTask,
            groupEntry = groupEntry,
            onChecklistToggle = onChecklistToggle
        )
    }
}
