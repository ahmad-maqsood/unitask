package com.example.ui.screens

import androidx.compose.material.icons.filled.Alarm
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.util.DateFormats
import com.example.data.groups.GroupMember
import com.example.data.groups.GroupTask

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
    if (showDeleteConfirm) {
        ConfirmDeleteDialog(
            title = "Delete task?",
            message = "\"${task.title}\" will be deleted for everyone in the group.",
            onConfirm = onDelete,
            onDismiss = { showDeleteConfirm = false }
        )
    }

    val dueDateStr = remember(task.dueDateEpochMillis) {
        DateFormats.shortDate(task.dueDateEpochMillis)
    }
    val timeStr = remember(task.dueTimeHour, task.dueTimeMinute) {
        com.example.util.TimeFormatter.format12Hour(task.dueTimeHour, task.dueTimeMinute)
    }

    // The alarm only rings at a reminder time, so it needs a reminder and a task that is not done yet.
    val showAlarmChip = task.priority == "HIGH" && !isDone && com.example.reminder.GroupReminderScheduler.optionsFor(task).isNotEmpty()
    val hasTopRow = canModify || !task.courseLabel.isNullOrBlank() || showAlarmChip

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isDone) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDone) 0.dp else 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("group_task_card_${task.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Personal Done Checkbox
                IconButton(
                    onClick = onToggleDone,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("group_task_done_checkbox_${task.id}")
                ) {
                    Icon(
                        imageVector = if (isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = if (isDone) "Mark not done" else "Mark done",
                        tint = if (isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Chips take the free width. A long course name shrinks and wraps instead of
                        // pushing the priority chip and the buttons out of the card.
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (!task.courseLabel.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    Text(
                                        text = courseText ?: task.courseLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            if (showAlarmChip) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Alarm,
                                            contentDescription = "Rings like an alarm",
                                            tint = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = "Alarm",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }
                        }

                        if (canModify) {
                            IconButton(
                                onClick = onEdit,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("group_task_edit_${task.id}")
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit task", modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = { showDeleteConfirm = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("group_task_delete_${task.id}")
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete task",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    if (hasTopRow) Spacer(Modifier.height(4.dp))

                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None,
                        color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )

                    if (!task.description.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = task.description,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                        Text(
                        text = "Due: $dueDateStr at $timeStr",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (task.createdByName.isNotBlank()) {
                        Text(
                            text = "Added by ${task.createdByName}",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

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
                    Text(
                        text = "Tagged: $tagText" + if (notOnMyDeadlines) " (not on your Deadlines)" else "",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
