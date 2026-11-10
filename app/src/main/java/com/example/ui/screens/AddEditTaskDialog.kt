package com.example.ui.screens

import android.app.TimePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.Course
import com.example.data.local.Priority
import com.example.data.local.ReminderOption
import com.example.data.local.TaskAttachment
import com.example.data.local.TaskItem
import com.example.data.local.TaskStatus
import com.example.util.HighPriorityAlarm
import com.example.util.DateFormats
import com.example.util.AttachmentManager
import com.example.util.SavedAttachmentInfo
import com.example.util.applyPickedDate
import com.example.util.localMillisToPickerUtcMillis
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import com.example.util.DraftChecklistItem
import com.example.util.buildDescriptionWithChecklist
import com.example.util.splitNotesAndChecklist
import com.example.ui.theme.rememberCourseColor

/** Text for the "Remind me" row: "Off", one label, or the earliest label plus how many more. */
private fun reminderSummary(options: Set<ReminderOption>): String {
    if (options.isEmpty()) return "Off"
    val sorted = options.sortedBy { it.offsetMinutes }
    val first = sorted.first().label
    return if (sorted.size == 1) first else "$first +${sorted.size - 1} more"
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AddEditTaskDialog(
    courses: List<Course>,
    taskToEdit: TaskItem?,
    existingAttachments: List<TaskAttachment> = emptyList(),
    initialCourseId: Long?,
    onDismiss: () -> Unit,
    onDeleteExistingAttachment: ((TaskAttachment) -> Unit)? = null,
    onSave: (
        title: String,
        description: String,
        courseId: Long,
        dueDateMillis: Long,
        dueHour: Int,
        dueMinute: Int,
        priority: Priority,
        status: TaskStatus,
        reminderOptions: Set<ReminderOption>,
        newAttachments: List<SavedAttachmentInfo>
    ) -> Unit,
    onNeedCreateCourse: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var title by rememberSaveable { mutableStateOf(taskToEdit?.title ?: "") }
    // The saved description holds notes AND "- [ ] item" lines. In the dialog they are edited separately.
    val initialSplit = remember { splitNotesAndChecklist(taskToEdit?.description ?: "") }
    var description by rememberSaveable { mutableStateOf(initialSplit.first) }
    var checklist by rememberSaveable(
        stateSaver = listSaver<List<DraftChecklistItem>, String>(
            save = { list -> list.flatMap { listOf(it.id.toString(), it.text, it.checked.toString()) } },
            restore = { flat ->
                flat.chunked(3).mapNotNull { c ->
                    if (c.size == 3) DraftChecklistItem(c[0].toLongOrNull() ?: 0L, c[1], c[2].toBoolean()) else null
                }
            }
        )
    ) { mutableStateOf(initialSplit.second) }
    var checklistFocusId by remember { mutableStateOf<Long?>(null) }

    var selectedCourseId by rememberSaveable {
        mutableLongStateOf(
            taskToEdit?.courseId
                ?: initialCourseId
                ?: courses.firstOrNull()?.id
                ?: 0L
        )
    }

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
    var priority by rememberSaveable { mutableStateOf(taskToEdit?.priority ?: Priority.MEDIUM) }
    var status by rememberSaveable { mutableStateOf(taskToEdit?.status ?: TaskStatus.PENDING) }
    var reminderOptions by rememberSaveable(
        stateSaver = listSaver<Set<ReminderOption>, String>(
            save = { it.map { opt -> opt.name } },
            restore = { list -> list.mapNotNull { name -> ReminderOption.entries.firstOrNull { o -> o.name == name } }.toSet() }
        )
    ) {
        mutableStateOf(taskToEdit?.reminderOptions ?: setOf(ReminderOption.TWO_HOURS_BEFORE))
    }

    // Offline Attachments State
    var newAttachments by rememberSaveable(
        stateSaver = listSaver<List<SavedAttachmentInfo>, String>(
            save = { list ->
                list.flatMap { listOf(it.fileName, it.relativePath, it.fileSize.toString(), it.mimeType) }
            },
            restore = { flat ->
                flat.chunked(4).mapNotNull { chunk ->
                    if (chunk.size == 4) {
                        SavedAttachmentInfo(
                            fileName = chunk[0],
                            relativePath = chunk[1],
                            fileSize = chunk[2].toLongOrNull() ?: 0L,
                            mimeType = chunk[3]
                        )
                    } else null
                }
            }
        )
    ) { mutableStateOf<List<SavedAttachmentInfo>>(emptyList()) }
    var isAttachingFile by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var showReminderPicker by rememberSaveable { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

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

    if (showReminderPicker) {
        AlertDialog(
            onDismissRequest = { showReminderPicker = false },
            title = { Text("Remind me", fontWeight = FontWeight.Bold) },
            text = {
                ReminderChipGrid(
                    selected = reminderOptions,
                    onSelectedChange = { reminderOptions = it },
                    testTagPrefix = "reminder_chip"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { showReminderPicker = false },
                    modifier = Modifier.testTag("reminder_done_button")
                ) {
                    Text("Done")
                }
            },
            dismissButton = {
                if (reminderOptions.isNotEmpty()) {
                    TextButton(
                        onClick = { reminderOptions = emptySet() },
                        modifier = Modifier.testTag("clear_reminders_button")
                    ) {
                        Text("Clear all")
                    }
                }
            }
        )
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            isAttachingFile = true
            coroutineScope.launch {
                val savedInfo = AttachmentManager.copyUriToInternalStorage(context, selectedUri)
                if (savedInfo != null) {
                    newAttachments = newAttachments + savedInfo
                } else {
                    android.widget.Toast.makeText(
                        context,
                        "Couldn't attach that file. The limit is 25 MB.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
                isAttachingFile = false
            }
        }
    }

    var titleError by remember { mutableStateOf(false) }
    var courseError by remember { mutableStateOf(false) }
    var courseDropdownExpanded by remember { mutableStateOf(false) }

    val selectedCourse = courses.find { it.id == selectedCourseId }
    // Deleting the unsaved files must not depend on this screen staying alive, so use its own scope.
    val cleanupAndDismiss: () -> Unit = {
        val unsaved = newAttachments
        if (unsaved.isNotEmpty()) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                unsaved.forEach { AttachmentManager.deleteAttachmentFile(context, it.relativePath) }
            }
        }
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = cleanupAndDismiss,
        title = {
            Text(
                text = if (taskToEdit != null) "Edit Assignment" else "New Assignment",
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
                // Course Picker
                Column {
                    Text(
                        text = "Course *",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    if (courses.isEmpty()) {
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNeedCreateCourse() }
                        ) {
                            Text(
                                text = "+ Add a course first to link tasks",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    } else {
                        Box {
                            OutlinedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { courseDropdownExpanded = true }
                                    .testTag("course_picker_card"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    if (selectedCourse != null) {
                                        val cColor = rememberCourseColor(selectedCourse.colorHex)
                                        Row(
                                            modifier = Modifier.weight(1f, fill = false),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .clip(CircleShape)
                                                    .background(cColor)
                                            )
                                            Text(
                                                text = "${selectedCourse.code} - ${selectedCourse.name}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    } else {
                                        Text("Select Course", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }

                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Expand courses",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = courseDropdownExpanded,
                                onDismissRequest = { courseDropdownExpanded = false }
                            ) {
                                courses.forEach { c ->
                                    val cColor = rememberCourseColor(c.colorHex)
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(12.dp)
                                                        .clip(CircleShape)
                                                        .background(cColor)
                                                )
                                                Text("${c.code} • ${c.name}")
                                            }
                                        },
                                        onClick = {
                                            selectedCourseId = c.id
                                            courseError = false
                                            courseDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (courseError) {
                        Text(
                            text = "Please select or create a course",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                        )
                    }
                }

                // Assignment Title
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it.take(200)
                        if (it.isNotBlank()) titleError = false
                    },
                    label = { Text("Assignment Title *") },
                    placeholder = { Text("e.g. Midterm Lab Report, HW 3") },
                    isError = titleError,
                    supportingText = if (titleError) {
                        { Text("Title is required") }
                    } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("task_title_input")
                )

                // Description
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description & Notes (optional)") },
                    placeholder = { Text("e.g. 5 pages, cover chapters 1-4") },
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("task_description_input")
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Checklist",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        TextButton(
                            onClick = {
                                val newId = (checklist.maxOfOrNull { it.id } ?: -1L) + 1L
                                checklist = checklist + DraftChecklistItem(newId, "", false)
                                checklistFocusId = newId
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.testTag("task_add_checklist_item")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add item", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    if (checklist.isEmpty()) {
                        Text(
                            text = "Break the assignment into small steps you can tick off.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }

                    checklist.forEach { item ->
                        key(item.id) {
                            val focusRequester = remember { FocusRequester() }
                            LaunchedEffect(checklistFocusId) {
                                if (checklistFocusId == item.id) {
                                    runCatching { focusRequester.requestFocus() }
                                    checklistFocusId = null
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = item.checked,
                                    onCheckedChange = { isChecked ->
                                        checklist = checklist.map {
                                            if (it.id == item.id) it.copy(checked = isChecked) else it
                                        }
                                    }
                                )
                                OutlinedTextField(
                                    value = item.text,
                                    onValueChange = { newText ->
                                        val clean = newText.replace("\n", " ").take(200)
                                        checklist = checklist.map {
                                            if (it.id == item.id) it.copy(text = clean) else it
                                        }
                                    },
                                    placeholder = { Text("Checklist item") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                    keyboardActions = KeyboardActions(
                                        onNext = {
                                            // Enter/Next on a filled item -> new empty item right below it
                                            if (item.text.isNotBlank()) {
                                                val newId = (checklist.maxOfOrNull { it.id } ?: -1L) + 1L
                                                val position = checklist.indexOfFirst { it.id == item.id } + 1
                                                checklist = checklist.toMutableList().apply {
                                                    add(position, DraftChecklistItem(newId, "", false))
                                                }
                                                checklistFocusId = newId
                                            }
                                        }
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusRequester(focusRequester)
                                )
                                IconButton(
                                    onClick = { checklist = checklist.filter { it.id != item.id } }
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove item")
                                }
                            }
                        }
                    }
                }

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
                        // Date selector
                        val formattedDate = DateFormats.fullDate(selectedDueDateMillis)

                        OutlinedCard(
                            modifier = Modifier
                                .weight(1.3f)
                                .clickable {
                                    showDatePicker = true
                                }
                                .testTag("due_date_picker_button"),
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
                                ShrinkToFitText(
                                    text = formattedDate,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    minFontSize = 9.sp
                                )
                            }
                        }

                        // Time selector
                        val timeStr = com.example.util.TimeFormatter.format12Hour(dueHour, dueMinute)
                        OutlinedCard(
                            modifier = Modifier
                                .weight(0.9f)
                                .clickable {
                                    showTimePicker = true
                                }
                                .testTag("due_time_picker_button"),
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
                                ShrinkToFitText(
                                    text = timeStr,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    minFontSize = 9.sp
                                )
                            }
                        }
                    }

                    // Quick date chips
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        QuickDateChip("Today", selected = isDaysFromToday(selectedDueDateMillis, 0)) {
                            val c = Calendar.getInstance()
                            selectedDueDateMillis = c.timeInMillis
                        }
                        QuickDateChip("Tomorrow", selected = isDaysFromToday(selectedDueDateMillis, 1)) {
                            val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
                            selectedDueDateMillis = c.timeInMillis
                        }
                        QuickDateChip("In 3 Days", selected = isDaysFromToday(selectedDueDateMillis, 3)) {
                            val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 3) }
                            selectedDueDateMillis = c.timeInMillis
                        }
                        QuickDateChip("Next Week", selected = isDaysFromToday(selectedDueDateMillis, 7)) {
                            val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 7) }
                            selectedDueDateMillis = c.timeInMillis
                        }
                    }
                    // Quick time chips (same as the My tasks dialog)
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        QuickDateChip("9:00 AM", selected = dueHour == 9 && dueMinute == 0) { dueHour = 9; dueMinute = 0 }
                        QuickDateChip("12:00 PM", selected = dueHour == 12 && dueMinute == 0) { dueHour = 12; dueMinute = 0 }
                        QuickDateChip("5:00 PM", selected = dueHour == 17 && dueMinute == 0) { dueHour = 17; dueMinute = 0 }
                        QuickDateChip("11:59 PM", selected = dueHour == 23 && dueMinute == 59) { dueHour = 23; dueMinute = 59 }
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
                                label = { ShrinkToFitText(text = p.name.lowercase().replaceFirstChar { it.uppercase() }, minFontSize = 10.sp) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("priority_chip_${p.name}")
                            )
                        }
                    }
                    val highAlarmOn = remember { HighPriorityAlarm.isEnabled(context) }
                    if (priority == Priority.HIGH) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (highAlarmOn) {
                                "High priority tasks ring like an alarm."
                            } else {
                                "Turn on \"Alarm for high priority tasks\" in Settings to make this ring like an alarm."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (highAlarmOn) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }

                // Status. A new task always starts as Pending, so the choice only matters when editing.
                if (taskToEdit != null) {
                    Column {
                        Text(
                            text = "Status",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            TaskStatus.entries.forEach { s ->
                                val label = when (s) {
                                    TaskStatus.PENDING -> "Pending"
                                    TaskStatus.COMPLETED -> "Done"
                                }
                                FilterChip(
                                    selected = status == s,
                                    onClick = { status = s },
                                    label = { ShrinkToFitText(text = label, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("status_chip_${s.name}")
                                )
                            }
                        }
                    }
                }

                // Reminders: one row that shows the current choice and opens a small picker.
                OutlinedCard(
                    onClick = { showReminderPicker = true },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reminder_row")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Remind me",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = reminderSummary(reminderOptions),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (reminderOptions.isEmpty()) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }


                // Attachments: one compact row. Saved and new files only take space once they exist.
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val attachedCount = existingAttachments.size + newAttachments.size
                    OutlinedCard(
                        onClick = {
                            filePickerLauncher.launch(
                                arrayOf(
                                    "application/pdf",
                                    "image/*",
                                    "text/*",
                                    "application/msword",
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                    "*/*"
                                )
                            )
                        },
                        enabled = !isAttachingFile,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("attach_file_button")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isAttachingFile) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AttachFile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = if (isAttachingFile) "Importing..." else "Attach file",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            if (attachedCount > 0 && !isAttachingFile) {
                                Text(
                                    text = "$attachedCount attached",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // Existing attachments
                    existingAttachments.forEach { att ->
                        AttachmentChip(
                            fileName = att.fileName,
                            fileSize = att.fileSize,
                            mimeType = att.mimeType,
                            onDelete = { onDeleteExistingAttachment?.invoke(att) }
                        )
                    }

                    // Newly attached files (pending save)
                    newAttachments.forEach { att ->
                        AttachmentChip(
                            fileName = att.fileName,
                            fileSize = att.fileSize,
                            mimeType = att.mimeType,
                            onDelete = {
                                coroutineScope.launch {
                                    AttachmentManager.deleteAttachmentFile(context, att.relativePath)
                                }
                                newAttachments = newAttachments - att
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val isTitleEmpty = title.isBlank()
                    val isCourseEmpty = selectedCourseId == 0L || selectedCourse == null
                    if (isTitleEmpty) titleError = true
                    if (isCourseEmpty) courseError = true

                    if (!isTitleEmpty && !isCourseEmpty) {
                        onSave(
                            title,
                            buildDescriptionWithChecklist(description, checklist),
                            selectedCourseId,
                            selectedDueDateMillis,
                            dueHour,
                            dueMinute,
                            priority,
                            status,
                            reminderOptions,
                            newAttachments
                        )
                    }
                },
                modifier = Modifier.testTag("task_save_button")
            ) {
                Text(if (taskToEdit != null) "Save" else "Add Task")
            }
        },
        dismissButton = {
            TextButton(
                onClick = cleanupAndDismiss,
                modifier = Modifier.testTag("task_cancel_button")
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AttachmentChip(
    fileName: String,
    fileSize: Long,
    mimeType: String,
    onDelete: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val icon = when {
        mimeType.contains("pdf", ignoreCase = true) || fileName.endsWith(".pdf", ignoreCase = true) -> Icons.Default.PictureAsPdf
        mimeType.startsWith("image", ignoreCase = true) || fileName.endsWith(".png", ignoreCase = true) || fileName.endsWith(".jpg", ignoreCase = true) -> Icons.Default.Image
        else -> Icons.Default.Description
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = AttachmentManager.formatFileSize(fileSize),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove attachment",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

