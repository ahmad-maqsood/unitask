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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.groups.GroupTask
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroupTaskDialog(
    taskToEdit: GroupTask?,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        description: String?,
        courseLabel: String?,
        dueDateMillis: Long,
        dueHour: Int,
        dueMinute: Int,
        priority: String
    ) -> Unit
) {
    val context = LocalContext.current

    var title by remember { mutableStateOf(taskToEdit?.title ?: "") }
    var description by remember { mutableStateOf(taskToEdit?.description ?: "") }
    var courseLabel by remember { mutableStateOf(taskToEdit?.courseLabel ?: "") }

    var selectedDueDateMillis by remember {
        mutableLongStateOf(
            taskToEdit?.dueDateEpochMillis ?: run {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, 2)
                cal.timeInMillis
            }
        )
    }

    var dueHour by remember { mutableIntStateOf(taskToEdit?.dueTimeHour ?: 23) }
    var dueMinute by remember { mutableIntStateOf(taskToEdit?.dueTimeMinute ?: 59) }
    var priority by remember { mutableStateOf(taskToEdit?.priority ?: "MEDIUM") }

    var showTimePicker by remember { mutableStateOf(false) }
    var titleError by remember { mutableStateOf(false) }

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

                // Course Label (Optional free text)
                OutlinedTextField(
                    value = courseLabel,
                    onValueChange = { courseLabel = it.take(60) },
                    label = { Text("Course / Subject (optional)") },
                    placeholder = { Text("e.g. CS 301, Physics Lab") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("group_task_course_label_input")
                )

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
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) {
                        titleError = true
                        return@Button
                    }
                    onSave(
                        title.trim(),
                        description.trim().ifEmpty { null },
                        courseLabel.trim().ifEmpty { null },
                        selectedDueDateMillis,
                        dueHour,
                        dueMinute,
                        priority
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
