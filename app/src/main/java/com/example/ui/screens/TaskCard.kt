package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.Priority
import com.example.data.groups.GroupTaskEntry
import com.example.util.AttachmentManager
import com.example.data.local.TaskItem
import com.example.data.local.TaskStatus
import com.example.data.local.TaskWithCourse
import com.example.ui.theme.PriorityHighBgDark
import com.example.ui.theme.PriorityHighBgLight
import com.example.ui.theme.PriorityHighFgDark
import com.example.ui.theme.PriorityHighFgLight
import com.example.ui.theme.PriorityLowBgDark
import com.example.ui.theme.PriorityLowBgLight
import com.example.ui.theme.PriorityLowFgDark
import com.example.ui.theme.PriorityLowFgLight
import com.example.ui.theme.PriorityMediumBgDark
import com.example.ui.theme.PriorityMediumBgLight
import com.example.ui.theme.PriorityMediumFgDark
import com.example.ui.theme.PriorityMediumFgLight
import com.example.ui.theme.StatusDoneBgDark
import com.example.ui.theme.StatusDoneBgLight
import com.example.ui.theme.StatusDoneFgDark
import com.example.ui.theme.StatusDoneFgLight
import com.example.ui.theme.isDarkTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import com.example.data.local.deadlineMillis
import java.util.Date
import java.util.Locale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskCard(
    taskWithCourse: TaskWithCourse,
    onStatusCycle: (TaskItem) -> Unit,
    onEditClick: (TaskItem) -> Unit,
    onDeleteClick: (TaskItem) -> Unit,
    modifier: Modifier = Modifier,
    // Set for tasks that come from a group: shows who added it and limits the menu.
    groupEntry: GroupTaskEntry? = null
) {
    val task = taskWithCourse.task
    val course = taskWithCourse.course
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    val courseColor = remember(course?.colorHex) {
        course?.colorHex?.let {
            try {
                Color(android.graphics.Color.parseColor(it))
            } catch (_: Exception) {
                Color(0xFF4F46E5)
            }
        } ?: Color(0xFF4F46E5)
    }

    val isCompleted = task.status == TaskStatus.COMPLETED

    // Calculate relative due text
    val dueText = remember(task.dueDateEpochMillis, task.dueTimeHour, task.dueTimeMinute) {
        formatDueDate(task.dueDateEpochMillis, task.dueTimeHour, task.dueTimeMinute)
    }

    val isOverdue = remember(task.dueDateEpochMillis, task.dueTimeHour, task.dueTimeMinute, task.status) {
        task.status != TaskStatus.COMPLETED && task.deadlineMillis < System.currentTimeMillis()
    }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    if (showDeleteConfirm) {
        ConfirmDeleteDialog(
            title = "Delete task?",
            message = "\"${task.title}\" will be removed from this phone and from your cloud backup.",
            onConfirm = { onDeleteClick(task) },
            onDismiss = { showDeleteConfirm = false }
        )
    }
    val cardColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isCompleted) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = androidx.compose.animation.core.tween(250),
        label = "task_card_color"
    )
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("task_item_${task.id}")
            .clickable { onEditClick(task) },
        shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCompleted) 0.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Left colour strip, as tall as the card whatever it contains.
                .drawBehind {
                    drawRect(color = courseColor, size = Size(6.dp.toPx(), size.height))
                }
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 20.dp, top = 14.dp, end = 14.dp, bottom = 14.dp)
            ) {
                // Top row: Course tag badge & Priority & Menu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Course Badge
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = courseColor.copy(alpha = 0.15f),
                            contentColor = courseColor
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (groupEntry != null) {
                                    Icon(
                                        imageVector = Icons.Default.Groups,
                                        contentDescription = "Group task",
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Text(
                                    text = course?.code ?: "General",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 130.dp)
                                )
                            }
                        }

                        // Priority Badge
                        PriorityBadge(priority = task.priority)

                        // Reminder Icon indicator
                        if (task.reminderOptions.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = "Reminders: ${task.reminderOptions.joinToString { it.label }}",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Attachment Count Indicator
                        if (taskWithCourse.attachments.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AttachFile,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "${taskWithCourse.attachments.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("task_menu_${task.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Task options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            if (groupEntry != null) {
                                // Group tasks are edited or deleted inside the group.
                                DropdownMenuItem(
                                    text = { Text("Open group") },
                                    onClick = {
                                        showMenu = false
                                        onEditClick(task)
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Groups, contentDescription = null)
                                    }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Edit Task") },
                                    onClick = {
                                        showMenu = false
                                        onEditClick(task)
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Edit, contentDescription = null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete Task", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showMenu = false
                                        showDeleteConfirm = true
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Task Title
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    color = if (isCompleted) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (groupEntry != null) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Added by ${groupEntry.task.createdByName.ifBlank { "a member" }}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Optional description snippet
                if (task.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Render Offline Attachment Chips
                if (taskWithCourse.attachments.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        taskWithCourse.attachments.forEach { att ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        AttachmentManager.openAttachment(context, att.relativePath, att.mimeType)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AttachFile,
                                        contentDescription = null,
                                        modifier = Modifier.size(13.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = att.fileName,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 140.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom row: Due date/time & Status toggle pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Due time text
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = "Due time",
                            tint = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isOverdue) "Overdue! ($dueText)" else dueText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = if (isOverdue) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Status toggle button
                    StatusToggleChip(
                        status = task.status,
                        onClick = { onStatusCycle(task) },
                        testTag = "task_status_toggle_${task.id}"
                    )
                }
            }
        }
    }
}

@Composable
fun PriorityBadge(priority: Priority) {
    val dark = isDarkTheme()
    val (bgColor, textColor, label) = when (priority) {
        Priority.HIGH -> Triple(
            if (dark) PriorityHighBgDark else PriorityHighBgLight,
            if (dark) PriorityHighFgDark else PriorityHighFgLight,
            "High"
        )
        Priority.MEDIUM -> Triple(
            if (dark) PriorityMediumBgDark else PriorityMediumBgLight,
            if (dark) PriorityMediumFgDark else PriorityMediumFgLight,
            "Medium"
        )
        Priority.LOW -> Triple(
            if (dark) PriorityLowBgDark else PriorityLowBgLight,
            if (dark) PriorityLowFgDark else PriorityLowFgLight,
            "Low"
        )
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bgColor,
        contentColor = textColor
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun StatusToggleChip(
    status: TaskStatus,
    onClick: () -> Unit,
    testTag: String
) {
    val dark = isDarkTheme()
    val (bgColor, textColor, icon, label) = when (status) {
        TaskStatus.PENDING -> Quadruple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.Default.RadioButtonUnchecked,
            "Pending"
        )
        TaskStatus.COMPLETED -> Quadruple(
            if (dark) StatusDoneBgDark else StatusDoneBgLight,
            if (dark) StatusDoneFgDark else StatusDoneFgLight,
            Icons.Default.CheckCircle,
            "Done"
        )
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bgColor,
        contentColor = textColor,
        modifier = Modifier
            .testTag(testTag)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

private fun formatDueDate(epochMillis: Long, hour: Int, minute: Int): String {
    val calendarDue = Calendar.getInstance().apply {
        timeInMillis = epochMillis
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    }

    val now = Calendar.getInstance()
    val isToday = now.get(Calendar.YEAR) == calendarDue.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == calendarDue.get(Calendar.DAY_OF_YEAR)

    val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
    val isTomorrow = tomorrow.get(Calendar.YEAR) == calendarDue.get(Calendar.YEAR) &&
            tomorrow.get(Calendar.DAY_OF_YEAR) == calendarDue.get(Calendar.DAY_OF_YEAR)

    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val formattedTime = timeFormat.format(calendarDue.time)

    return when {
        isToday -> "Today, $formattedTime"
        isTomorrow -> "Tomorrow, $formattedTime"
        else -> {
            val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
            "${dateFormat.format(calendarDue.time)}, $formattedTime"
        }
    }
}
