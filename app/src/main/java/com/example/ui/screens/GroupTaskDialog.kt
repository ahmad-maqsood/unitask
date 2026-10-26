package com.example.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.groups.GroupCourse
import com.example.data.groups.GroupTask
import com.example.data.groups.GroupMember
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.Check
import com.example.data.local.ReminderOption
import com.example.data.local.encodeReminderOptions
import com.example.reminder.GroupReminderScheduler
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroupTaskDialog(
    taskToEdit: GroupTask?,
    members: List<GroupMember>,
    currentUid: String,
    onDismiss: () -> Unit,
    groupCourses: List<GroupCourse> = emptyList(),
    onSave: (
        title: String,
        description: String?,
        courseLabel: String?,
        dueDateMillis: Long,
        dueHour: Int,
        dueMinute: Int,
        priority: String,
        reminderOptions: String,
        assignedTo: List<String>
    ) -> Unit
) {
    val context = LocalContext.current

    var title by rememberSaveable { mutableStateOf(taskToEdit?.title ?: "") }
    var description by rememberSaveable { mutableStateOf(taskToEdit?.description ?: "") }
    var courseLabel by rememberSaveable { mutableStateOf(taskToEdit?.courseLabel ?: "") }

    var selectedDueDateMillis by rememberSaveable {
        mutableLongStateOf(
            taskToEdit?.dueDateEpochMillis ?: run {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, 2)
                cal.timeInMillis
            }
        )
    }

    var dueHour by rememberSaveable { mutableIntStateOf(taskToEdit?.dueTimeHour ?: 23) }
    var dueMinute by rememberSaveable { mutableIntStateOf(taskToEdit?.dueTimeMinute ?: 59) }
    var priority by rememberSaveable { mutableStateOf(taskToEdit?.priority ?: "MEDIUM") }
    // New tasks start with the usual three alerts. Old tasks show what they really use today.
    var reminderOptions by rememberSaveable(
        stateSaver = listSaver<Set<ReminderOption>, String>(
            save = { it.map { opt -> opt.name } },
            restore = { list -> list.mapNotNull { name -> ReminderOption.entries.firstOrNull { o -> o.name == name } }.toSet() }
        )
    ) {
        mutableStateOf(
            taskToEdit?.let { GroupReminderScheduler.optionsFor(it) }
                ?: GroupReminderScheduler.DEFAULT_REMINDERS
        )
    }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var titleError by rememberSaveable { mutableStateOf(false) }
    // Empty assignedTo means @all. New tasks start as @all; editing keeps what was saved.
    var tagAll by rememberSaveable { mutableStateOf(taskToEdit == null || taskToEdit.assignedTo.isEmpty()) }
    var taggedUids by rememberSaveable(
        stateSaver = listSaver<Set<String>, String>(
            save = { it.toList() },
            restore = { it.toSet() }
        )
    ) { mutableStateOf<Set<String>>(taskToEdit?.assignedTo?.toSet() ?: emptySet()) }
    var tagError by rememberSaveable { mutableStateOf(false) }

    if (showTimePicker) {
        Material3TimePickerDialog(
            title = "Select Due Time",
            initialHour = dueHour,
            initialMinute = dueMinute,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute ->
                dueHour = hour
                dueMinute = minute
                showTimePicker = false
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (taskToEdit == null) Icons.Default.Assignment else Icons.Default.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (taskToEdit == null) "New Group Task" else "Edit Group Task",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Task Title
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it.take(200)
                        if (it.isNotBlank()) titleError = false
                    },
                    label = { Text("Task Title *") },
                    placeholder = { Text("e.g. Project Milestone 1") },
                    isError = titleError,
                    supportingText = if (titleError) {
                        { Text("Title is required") }
                    } else null,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("group_task_title_input")
                )

                // Course / Subject Selection & Custom Label
                Column {
                    Text(
                        text = "Course / Subject (optional)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (groupCourses.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            groupCourses.forEach { c ->
                                val isSelected = courseLabel.equals(c.code, ignoreCase = true)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        courseLabel = if (isSelected) "" else c.code
                                    },
                                    leadingIcon = if (isSelected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    } else null,
                                    label = {
                                        Text(
                                            text = "${c.code} (${c.name})",
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    modifier = Modifier.testTag("group_task_course_chip_${c.code}")
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = courseLabel,
                        onValueChange = { courseLabel = it.take(60) },
                        label = { Text("Course Code / Name") },
                        placeholder = { Text("e.g. CS 301, Physics Lab") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("group_task_course_label_input")
                    )
                }

                // Description
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(2000) },
                    label = { Text("Description & Notes (optional)") },
                    placeholder = { Text("e.g. Prepare slides and code demo") },
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("group_task_description_input")
                )

                // Due Date & Time Section
                Column {
                    Text(
                        text = "Due Date & Time",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val dateFormatter = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())
                        val formattedDate = dateFormatter.format(Date(selectedDueDateMillis))

                        OutlinedCard(
                            modifier = Modifier
                                .weight(1.3f)
                                .clickable {
                                    val cal = Calendar.getInstance().apply { timeInMillis = selectedDueDateMillis }
                                    DatePickerDialog(
                                        context,
                                        { _, year, month, dayOfMonth ->
                                            cal.set(Calendar.YEAR, year)
                                            cal.set(Calendar.MONTH, month)
                                            cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                            selectedDueDateMillis = cal.timeInMillis
                                        },
                                        cal.get(Calendar.YEAR),
                                        cal.get(Calendar.MONTH),
                                        cal.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                }
                                .testTag("group_task_date_picker_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = formattedDate,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Time Selector
                        val formattedTime = String.format(Locale.getDefault(), "%02d:%02d", dueHour, dueMinute)
                        OutlinedCard(
                            modifier = Modifier
                                .weight(0.9f)
                                .clickable { showTimePicker = true }
                                .testTag("group_task_time_picker_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccessTime,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = formattedTime,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Priority
                Column {
                    Text(
                        text = "Priority",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("LOW", "MEDIUM", "HIGH").forEach { p ->
                            FilterChip(
                                selected = priority == p,
                                onClick = { priority = p },
                                label = { Text(p) },
                                modifier = Modifier.testTag("group_task_priority_chip_$p")
                            )
                        }
                    }
                }

                // Reminders: every member of the group gets an alert at each time you pick
                Column {
                    Text(
                        text = "Reminders",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (reminderOptions.isEmpty()) {
                            "No reminders. Tap times below to add some."
                        } else {
                            "${reminderOptions.size} reminder${if (reminderOptions.size > 1) "s" else ""} selected. Everyone tagged gets them."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (reminderOptions.isEmpty()) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ReminderOption.entries.sortedBy { it.offsetMinutes }.forEach { opt ->
                            FilterChip(
                                selected = opt in reminderOptions,
                                onClick = {
                                    reminderOptions = if (opt in reminderOptions) {
                                        reminderOptions - opt
                                    } else {
                                        reminderOptions + opt
                                    }
                                },
                                leadingIcon = if (opt in reminderOptions) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                } else null,
                                label = { Text(opt.label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag("group_reminder_chip_${opt.name}")
                            )
                        }
                    }
                }

                // Tag members: only tagged members see this task on their Deadlines tab
                Column {
                    Text(
                        text = "Tag members",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = when {
                            tagError -> "Pick @all or at least one member."
                            tagAll -> "@all: everyone in the group sees this in their Deadlines."
                            else -> "${taggedUids.size} tagged. Only they see this in their Deadlines."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (tagError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterChip(
                            selected = tagAll,
                            onClick = {
                                tagAll = true
                                taggedUids = emptySet()
                                tagError = false
                            },
                            leadingIcon = if (tagAll) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            } else null,
                            label = { Text("@all", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("group_task_tag_all")
                        )
                        members.sortedBy { it.displayName.lowercase() }.forEach { m ->
                            val isTagged = !tagAll && m.uid in taggedUids
                            FilterChip(
                                selected = isTagged,
                                onClick = {
                                    tagError = false
                                    if (tagAll) {
                                        // Leaving @all: the creator of a new task starts out tagged
                                        // and can tap their own chip to untag themselves.
                                        tagAll = false
                                        taggedUids = if (taskToEdit == null && currentUid.isNotBlank()) {
                                            setOf(currentUid)
                                        } else {
                                            emptySet()
                                        }
                                    }
                                    taggedUids = if (m.uid in taggedUids) taggedUids - m.uid else taggedUids + m.uid
                                },
                                leadingIcon = if (isTagged) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                } else null,
                                label = {
                                    Text(
                                        text = "@" + m.displayName.ifBlank { "Student" } +
                                            if (m.uid == currentUid) " (you)" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier.testTag("group_task_tag_${m.uid}")
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) {
                        titleError = true
                        return@Button
                    }
                    if (!tagAll && taggedUids.isEmpty()) {
                        tagError = true
                        return@Button
                    }
                    onSave(
                        title.trim(),
                        description.trim().ifEmpty { null },
                        courseLabel.trim().ifEmpty { null },
                        selectedDueDateMillis,
                        dueHour,
                        dueMinute,
                        priority,
                        encodeReminderOptions(reminderOptions),
                        if (tagAll) emptyList() else taggedUids.toList()
                    )
                },
                modifier = Modifier.testTag("group_task_save_button")
            ) {
                Text(if (taskToEdit == null) "Create Task" else "Save Changes")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("group_task_cancel_button")
            ) {
                Text("Cancel")
            }
        }
    )
}
