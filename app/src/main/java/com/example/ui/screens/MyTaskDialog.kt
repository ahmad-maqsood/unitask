package com.example.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.Priority
import com.example.data.local.ReminderOption
import com.example.data.local.RepeatRule
import com.example.data.local.TaskItem
import com.example.data.local.repeat
import com.example.util.HighPriorityAlarm
import com.example.util.TimeFormatter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MyTaskDialog(
    taskToEdit: TaskItem?,
    onSave: (
        title: String,
        description: String,
        dueDateEpochMillis: Long,
        hour: Int,
        minute: Int,
        priority: Priority,
        reminders: Set<ReminderOption>,
        repeat: RepeatRule
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    var title by rememberSaveable { mutableStateOf(taskToEdit?.title ?: "") }
    var description by rememberSaveable { mutableStateOf(taskToEdit?.description ?: "") }

    var selectedDueDateMillis by rememberSaveable {
        mutableStateOf<Long?>(taskToEdit?.dueDateEpochMillis)
    }

    var dueHour by rememberSaveable {
        mutableStateOf<Int?>(taskToEdit?.dueTimeHour)
    }
    var dueMinute by rememberSaveable {
        mutableStateOf<Int?>(taskToEdit?.dueTimeMinute)
    }

    var priority by rememberSaveable { mutableStateOf(taskToEdit?.priority ?: Priority.MEDIUM) }

    var reminderOptions by rememberSaveable(
        stateSaver = listSaver<Set<ReminderOption>, String>(
            save = { it.map { opt -> opt.name } },
            restore = { list -> list.mapNotNull { name -> ReminderOption.entries.firstOrNull { o -> o.name == name } }.toSet() }
        )
    ) {
        mutableStateOf(taskToEdit?.reminderOptions ?: setOf(ReminderOption.AT_DEADLINE))
    }

    var repeatRule by rememberSaveable { mutableStateOf(taskToEdit?.repeat ?: RepeatRule.NONE) }

    var titleError by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var repeatDropdownExpanded by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    if (showTimePicker) {
        Material3TimePickerDialog(
            title = "Select Due Time",
            initialHour = dueHour ?: 23,
            initialMinute = dueMinute ?: 59,
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
            Text(
                text = if (taskToEdit != null) "Edit My Task" else "Add My Task",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Title
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it.take(200)
                        if (it.isNotBlank()) titleError = false
                    },
                    label = { Text("Task Title *") },
                    placeholder = { Text("e.g. Call home, Buy groceries") },
                    isError = titleError,
                    supportingText = if (titleError) {
                        { Text("Title is required") }
                    } else null,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .testTag("my_task_title_input")
                )

                // Description
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description & Notes (optional)") },
                    placeholder = { Text("Add any details or checklists") },
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("my_task_description_input")
                )

                // Due Date & Time Section
                Column {
                    Text(
                        text = "Due Date & Time *",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Date selector
                        val dateFormatter = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())
                        val isDateMissing = selectedDueDateMillis == null

                        OutlinedCard(
                            modifier = Modifier
                                .weight(1.3f)
                                .clickable {
                                    val cal = Calendar.getInstance().apply {
                                        selectedDueDateMillis?.let { timeInMillis = it }
                                    }
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
                                .testTag("my_task_due_date_button"),
                            shape = RoundedCornerShape(12.dp),
                            border = if (isDateMissing) {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                            } else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f, fill = false),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarToday,
                                        contentDescription = "Select due date",
                                        modifier = Modifier.size(16.dp),
                                        tint = if (isDateMissing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                    Column {
                                        if (selectedDueDateMillis != null) {
                                            Text(
                                                text = dateFormatter.format(Date(selectedDueDateMillis!!)),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium
                                            )
                                        } else {
                                            Text(
                                                text = "Select date",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                            Text(
                                                text = "Date required",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                                if (selectedDueDateMillis != null) {
                                    IconButton(
                                        onClick = { selectedDueDateMillis = null },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear date",
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // Time selector
                        val isTimeMissing = dueHour == null || dueMinute == null
                        val timeStr = if (!isTimeMissing) {
                            TimeFormatter.format12Hour(dueHour!!, dueMinute!!)
                        } else null

                        OutlinedCard(
                            modifier = Modifier
                                .weight(0.9f)
                                .clickable { showTimePicker = true }
                                .testTag("my_task_due_time_button"),
                            shape = RoundedCornerShape(12.dp),
                            border = if (isTimeMissing) {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                            } else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f, fill = false),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccessTime,
                                        contentDescription = "Select due time",
                                        modifier = Modifier.size(16.dp),
                                        tint = if (isTimeMissing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                    Column {
                                        if (timeStr != null) {
                                            Text(
                                                text = timeStr,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium
                                            )
                                        } else {
                                            Text(
                                                text = "Select time",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                            Text(
                                                text = "Time required",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                                if (!isTimeMissing) {
                                    IconButton(
                                        onClick = {
                                            dueHour = null
                                            dueMinute = null
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear time",
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Quick date chips
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        QuickDateChip("Today") {
                            val c = Calendar.getInstance()
                            selectedDueDateMillis = c.timeInMillis
                        }
                        QuickDateChip("Tomorrow") {
                            val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
                            selectedDueDateMillis = c.timeInMillis
                        }
                        QuickDateChip("In 3 Days") {
                            val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 3) }
                            selectedDueDateMillis = c.timeInMillis
                        }
                        QuickDateChip("Next Week") {
                            val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 7) }
                            selectedDueDateMillis = c.timeInMillis
                        }
                    }

                    // Quick time chips
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        QuickDateChip("11:59 PM") {
                            dueHour = 23
                            dueMinute = 59
                        }
                        QuickDateChip("9:00 AM") {
                            dueHour = 9
                            dueMinute = 0
                        }
                        QuickDateChip("1:00 PM") {
                            dueHour = 13
                            dueMinute = 0
                        }
                        QuickDateChip("5:00 PM") {
                            dueHour = 17
                            dueMinute = 0
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Priority.entries.forEach { p ->
                            FilterChip(
                                selected = priority == p,
                                onClick = { priority = p },
                                label = { Text(p.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("my_task_priority_${p.name}")
                            )
                        }
                    }

                    val isHighPriorityAlarmEnabled = remember { HighPriorityAlarm.isEnabled(context) }
                    if (priority == Priority.HIGH && isHighPriorityAlarmEnabled) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "High priority tasks ring like an alarm.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }

                // Repeat Rule
                Column {
                    Text(
                        text = "Repeat",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box {
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { repeatDropdownExpanded = true }
                                .testTag("my_task_repeat_card"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Repeat,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = repeatRule.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Expand repeat options",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = repeatDropdownExpanded,
                            onDismissRequest = { repeatDropdownExpanded = false }
                        ) {
                            RepeatRule.entries.forEach { rule ->
                                DropdownMenuItem(
                                    text = { Text(rule.label) },
                                    onClick = {
                                        repeatRule = rule
                                        repeatDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    if (repeatRule != RepeatRule.NONE) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "When you tick it done, it moves to the next date.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }

                // Notification Reminders (Multiple selections allowed)
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Notification Reminders",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (reminderOptions.isNotEmpty()) {
                            TextButton(
                                onClick = { reminderOptions = emptySet() },
                                modifier = Modifier.testTag("my_task_clear_reminders_button")
                            ) {
                                Text("Clear all", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (reminderOptions.isEmpty()) {
                            "Select times to receive alerts, or none."
                        } else {
                            "${reminderOptions.size} reminder${if (reminderOptions.size > 1) "s" else ""} selected"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (reminderOptions.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        fontWeight = if (reminderOptions.isEmpty()) FontWeight.Normal else FontWeight.Medium
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
                                modifier = Modifier.testTag("my_task_reminder_chip_${opt.name}")
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            val isDateOrTimeEmpty = selectedDueDateMillis == null || dueHour == null || dueMinute == null
            val isSaveEnabled = title.isNotBlank() && !isDateOrTimeEmpty

            Button(
                onClick = {
                    if (title.isBlank()) {
                        titleError = true
                    } else if (selectedDueDateMillis != null && dueHour != null && dueMinute != null) {
                        onSave(
                            title.trim(),
                            description.trim(),
                            selectedDueDateMillis!!,
                            dueHour!!,
                            dueMinute!!,
                            priority,
                            reminderOptions,
                            repeatRule
                        )
                    }
                },
                enabled = isSaveEnabled,
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("my_task_save_button")
            ) {
                Text(if (taskToEdit != null) "Save" else "Add Task")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("my_task_cancel_button")
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun QuickDateChip(label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

