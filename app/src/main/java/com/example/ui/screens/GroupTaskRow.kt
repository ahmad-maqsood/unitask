package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.groups.GroupMember
import com.example.data.groups.GroupTask
import java.util.Calendar

/**
 * A group task card. It follows the same layout as the personal [TaskCard]: a colour strip on the
 * left, a badge row with the options menu, the title, small info chips, and a bottom row with the
 * due time and the Pending/Done chip.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroupTaskRow(
    task: GroupTask,
    members: List<GroupMember>,
    currentUid: String,
    isDone: Boolean,
    canModify: Boolean,
    courseText: String? = null,
    onToggleDone: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    if (showDeleteConfirm) {
        ConfirmDeleteDialog(
            title = "Delete task?",
            message = "\"${task.title}\" will be deleted for everyone in the group.",
            onConfirm = onDelete,
            onDismiss = { showDeleteConfirm = false }
        )
    }

    val dueText = remember(task.dueDateEpochMillis, task.dueTimeHour, task.dueTimeMinute) {
        formatDueDate(task.dueDateEpochMillis, task.dueTimeHour, task.dueTimeMinute)
    }
    val isOverdue = remember(task.dueDateEpochMillis, task.dueTimeHour, task.dueTimeMinute, isDone) {
        if (isDone) {
            false
        } else {
            val due = Calendar.getInstance().apply {
                timeInMillis = task.dueDateEpochMillis
                set(Calendar.HOUR_OF_DAY, task.dueTimeHour)
                set(Calendar.MINUTE, task.dueTimeMinute)
            }
            due.timeInMillis < System.currentTimeMillis()
        }
    }

    // The alarm only rings at a reminder time, so it needs a reminder and a task that is not done yet.
    val showAlarmChip = task.priority == "HIGH" && !isDone &&
        com.example.reminder.GroupReminderScheduler.optionsFor(task).isNotEmpty()
    val hasBadge = !task.courseLabel.isNullOrBlank()

    val tagText = remember(task.assignedTo, members) {
        if (task.assignedTo.isEmpty()) {
            "@all"
        } else {
            task.assignedTo.joinToString(", ") { uid ->
                "@" + (members.find { it.uid == uid }?.displayName?.takeIf { it.isNotBlank() } ?: "Member")
            }
        }
    }
    val notOnMyDeadlines = task.assignedTo.isNotEmpty() && currentUid !in task.assignedTo

    val stripColor = MaterialTheme.colorScheme.primary

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isDone) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDone) 0.dp else 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("group_task_card_${task.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Left colour strip, as tall as the card whatever it contains.
                .drawBehind {
                    drawRect(color = stripColor, size = Size(6.dp.toPx(), size.height))
                }
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 20.dp, top = 10.dp, end = 14.dp, bottom = 12.dp)
            ) {
                val titleContent: @Composable () -> Unit = {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None
                        ),
                        color = if (isDone) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Top row: course badge (or the title when there is no course) beside the menu button.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        if (hasBadge) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = stripColor.copy(alpha = 0.15f),
                                contentColor = stripColor
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Groups,
                                        contentDescription = "Group task",
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = courseText ?: task.courseLabel.orEmpty(),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                }
                            }
                        } else {
                            titleContent()
                        }
                    }

                    if (canModify) {
                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                    .testTag("group_task_menu_${task.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Task options",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Edit Task") },
                                    onClick = {
                                        showMenu = false
                                        onEdit()
                                    },
                                    modifier = Modifier.testTag("group_task_edit_${task.id}"),
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete Task", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showMenu = false
                                        showDeleteConfirm = true
                                    },
                                    modifier = Modifier.testTag("group_task_delete_${task.id}"),
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

                if (hasBadge) {
                    Spacer(Modifier.height(2.dp))
                    titleContent()
                }

                if (!task.description.isNullOrBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Who added it and who it is for, as small chips like the personal card's.
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (showAlarmChip) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Alarm,
                                    contentDescription = "Rings like an alarm",
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Alarm",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    if (task.createdByName.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Added by ${task.createdByName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ) {
                        Text(
                            text = "Tagged: $tagText",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                if (notOnMyDeadlines) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Not on your Deadlines",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Bottom row: due time and the personal Pending/Done chip.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = "Due time",
                            tint = if (isOverdue) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isOverdue) "Overdue! ($dueText)" else dueText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = if (isOverdue) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isOverdue) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    StatusToggleChip(
                        status = if (isDone) com.example.data.local.TaskStatus.COMPLETED
                        else com.example.data.local.TaskStatus.PENDING,
                        onClick = onToggleDone,
                        testTag = "group_task_done_checkbox_${task.id}"
                    )
                }
            }
        }
    }
}
