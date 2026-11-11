package com.example.ui.screens

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.DateFormats
import com.example.data.local.priorityNameForRingAlarm
import com.example.data.groups.GroupCourse
import com.example.data.groups.GroupTask
import com.example.data.groups.GroupMember
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material.icons.filled.Check
import com.example.data.local.ReminderOption
import com.example.data.local.encodeReminderOptions
import com.example.reminder.GroupReminderScheduler
import com.example.util.applyPickedDate
import com.example.util.localMillisToPickerUtcMillis
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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
    val focusManager = LocalFocusManager.current

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

    var dueHour by rememberSaveable { mutableIntStateOf(taskToEdit?.dueTimeHour ?: 12) }
    var dueMinute by rememberSaveable { mutableIntStateOf(taskToEdit?.dueTimeMinute ?: 0) }
    var ringAlarm by rememberSaveable { mutableStateOf(taskToEdit?.priority == "HIGH") }
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
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
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

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = localMillisToPickerUtcMillis(selectedDueDateMillis)
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { pickedUtc ->
                            selectedDueDateMillis = applyPickedDate(selectedDueDateMillis, pickedUtc)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDatePicker = false }
                ) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

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

    // Course picker and tag picker state
    var courseMenuOpen by remember { mutableStateOf(false) }
    var customCourse by rememberSaveable {
        mutableStateOf(
            courseLabel.isNotBlank() && groupCourses.none { it.code.equals(courseLabel, ignoreCase = true) }
        )
    }
    var showTagPicker by rememberSaveable { mutableStateOf(false) }
    val sortedMembers = remember(members) { members.sortedBy { it.displayName.lowercase() } }

    if (showTagPicker) {
        AlertDialog(
            onDismissRequest = { showTagPicker = false },
            title = { Text("Tag members", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Only tagged members see this task in their Deadlines. Everyone in the group can still see it in the group.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                tagAll = true
                                taggedUids = emptySet()
                                tagError = false
                            }
                            .heightIn(min = 48.dp)
                            .testTag("group_task_tag_all"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = tagAll, onCheckedChange = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("@all (everyone)", fontWeight = FontWeight.SemiBold)
                    }
                    sortedMembers.forEach { m ->
                        val isTagged = !tagAll && m.uid in taggedUids
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    tagError = false
                                    if (tagAll) {
                                        // Leaving @all: the creator of a new task starts out tagged
                                        // and can tap their own row to untag themselves.
                                        tagAll = false
                                        taggedUids = if (taskToEdit == null && currentUid.isNotBlank()) {
                                            setOf(currentUid)
                                        } else {
                                            emptySet()
                                        }
                                    }
                                    taggedUids = if (m.uid in taggedUids) taggedUids - m.uid else taggedUids + m.uid
                                }
                                .heightIn(min = 48.dp)
                                .testTag("group_task_tag_${m.uid}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = isTagged, onCheckedChange = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = m.displayName.ifBlank { "Student" } +
                                    if (m.uid == currentUid) " (you)" else "",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTagPicker = false }) { Text("Done") }
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top bar: close, title and Save. It stays on screen, so Save never scrolls away
                // or ends up behind the keyboard.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("group_task_cancel_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                    Text(
                        text = if (taskToEdit == null) "New Group Task" else "Edit Group Task",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )
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
                                priorityNameForRingAlarm(ringAlarm, taskToEdit?.priority),
                                encodeReminderOptions(reminderOptions),
                                if (tagAll) emptyList() else taggedUids.toList()
                            )
                        },
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("group_task_save_button")
                    ) {
                        Text(if (taskToEdit == null) "Create Task" else "Save Changes")
                    }
                }
                HorizontalDivider()

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
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
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("group_task_title_input")
                    )

                    // Course / Subject: one row that opens a menu, however many courses the group has
                    Column {
                        if (groupCourses.isNotEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedCard(
                                    onClick = { courseMenuOpen = true },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("group_task_course_row")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Course",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1
                                        )
                                        val matched = groupCourses.firstOrNull { it.code.equals(courseLabel, ignoreCase = true) }
                                        val summary = when {
                                            matched != null -> "${matched.code} (${matched.name})"
                                            customCourse -> courseLabel.ifBlank { "Other" }
                                            else -> "None"
                                        }
                                        Text(
                                            text = summary,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (summary == "None") {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            } else {
                                                MaterialTheme.colorScheme.primary
                                            },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.End,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Choose course",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                DropdownMenu(
                                    expanded = courseMenuOpen,
                                    onDismissRequest = { courseMenuOpen = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("None") },
                                        onClick = {
                                            courseLabel = ""
                                            customCourse = false
                                            courseMenuOpen = false
                                        },
                                        modifier = Modifier.testTag("group_task_course_none")
                                    )
                                    groupCourses.forEach { c ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = "${c.code} (${c.name})",
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            },
                                            trailingIcon = if (courseLabel.equals(c.code, ignoreCase = true)) {
                                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                            } else null,
                                            onClick = {
                                                courseLabel = c.code
                                                customCourse = false
                                                courseMenuOpen = false
                                            },
                                            modifier = Modifier.testTag("group_task_course_chip_${c.code}")
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Other (type my own)") },
                                        onClick = {
                                            if (groupCourses.any { it.code.equals(courseLabel, ignoreCase = true) }) courseLabel = ""
                                            customCourse = true
                                            courseMenuOpen = false
                                        },
                                        modifier = Modifier.testTag("group_task_course_other")
                                    )
                                }
                            }
                        }
                        // The free text field only shows when the group has no courses or "Other" is chosen
                        if (groupCourses.isEmpty() || customCourse) {
                            if (groupCourses.isNotEmpty()) Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = courseLabel,
                                onValueChange = { courseLabel = it.take(60) },
                                label = { Text("Course Code / Name (optional)") },
                                placeholder = { Text("e.g. CS 301, Physics Lab") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("group_task_course_label_input")
                            )
                        }
                    }

                    // Description
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it.take(2000) },
                        label = { Text("Description & Notes (optional)") },
                        placeholder = { Text("e.g. Prepare slides and code demo") },
                        minLines = 2,
                        maxLines = 6,
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
                            val formattedDate = DateFormats.fullDate(selectedDueDateMillis)

                            OutlinedCard(
                                modifier = Modifier
                                    .weight(1.3f)
                                    .clickable {
                                        showDatePicker = true
                                    }
                                    .testTag("group_task_date_picker_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .padding(horizontal = 10.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarToday,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    ShrinkToFitText(
                                        text = formattedDate,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        minFontSize = 9.sp
                                    )
                                }
                            }

                            // Time Selector
                            val formattedTime = com.example.util.TimeFormatter.format12Hour(dueHour, dueMinute)
                            OutlinedCard(
                                modifier = Modifier
                                    .weight(0.9f)
                                    .clickable { showTimePicker = true }
                                    .testTag("group_task_time_picker_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .padding(horizontal = 10.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccessTime,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    ShrinkToFitText(
                                        text = formattedTime,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        minFontSize = 9.sp
                                    )
                                }
                            }
                        }
                    }

                    // Reminders: every member of the group gets an alert at each time you pick
                    ReminderPickerRow(
                        selected = reminderOptions,
                        onSelectedChange = { reminderOptions = it },
                        chipTagPrefix = "group_reminder_chip",
                        rowTestTag = "group_reminder_row",
                        clearTestTag = "group_clear_reminders_button",
                        doneTestTag = "group_reminder_done_button",
                        pickerNote = "Everyone tagged gets these alerts."
                    )

                    // Ring alarm: the reminders above ring like an alarm for the tagged members
                    RingAlarmToggleRow(
                        checked = ringAlarm,
                        onCheckedChange = { ringAlarm = it },
                        hasReminders = reminderOptions.isNotEmpty(),
                        testTag = "group_ring_alarm_row",
                        forGroup = true
                    )

                    // Tag members: one row that opens a checklist. Only tagged members see this
                    // task on their Deadlines tab.
                    Column {
                        OutlinedCard(
                            onClick = { showTagPicker = true },
                            shape = RoundedCornerShape(12.dp),
                            border = if (tagError) BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                            else CardDefaults.outlinedCardBorder(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("group_task_tag_row")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Tag members",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                                Text(
                                    text = if (tagAll) "@all" else "${taggedUids.size} tagged",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when {
                                tagError -> "Pick @all or at least one member."
                                tagAll -> "Everyone in the group sees this in their Deadlines."
                                else -> "Only tagged members see this in their Deadlines."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (tagError) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
