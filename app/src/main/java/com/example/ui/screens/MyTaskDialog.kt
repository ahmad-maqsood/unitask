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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.Priority
import com.example.data.local.ReminderOption
import com.example.data.local.RepeatRule
import com.example.data.local.TaskItem
import com.example.data.local.repeat
import com.example.util.DateFormats
import com.example.util.HighPriorityAlarm
import com.example.util.TimeFormatter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.key
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import com.example.util.DraftChecklistItem
import com.example.util.buildDescriptionWithChecklist
import com.example.util.splitNotesAndChecklist
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
        repeat: RepeatRule,
        autoDeleteWhenDone: Boolean
    ) -> Unit,
    onDismiss: () -> Unit
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
    var autoDeleteWhenDone by rememberSaveable { mutableStateOf(taskToEdit?.autoDeleteWhenDone ?: false) }
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
            initialHour = dueHour ?: 12,
            initialMinute = dueMinute ?: 0,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute ->
                dueHour = hour
                dueMinute = minute
                showTimePicker = false
            }
        )
    }

    val isDateOrTimeEmpty = selectedDueDateMillis == null || dueHour == null || dueMinute == null
    val isSaveEnabled = title.isNotBlank() && !isDateOrTimeEmpty

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
                            .testTag("my_task_cancel_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                    Text(
                        text = if (taskToEdit != null) "Edit My Task" else "Add My Task",
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
                            } else if (selectedDueDateMillis != null && dueHour != null && dueMinute != null) {
                                onSave(
                                    title.trim(),
                                    buildDescriptionWithChecklist(description, checklist).trim(),
                                    selectedDueDateMillis!!,
                                    dueHour!!,
                                    dueMinute!!,
                                    priority,
                                    reminderOptions,
                                    repeatRule,
                                    autoDeleteWhenDone
                                )
                            }
                        },
                        enabled = isSaveEnabled,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("my_task_save_button")
                    ) {
                        Text(if (taskToEdit != null) "Save" else "Add Task")
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
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
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
                    placeholder = { Text("Add any details or notes") },
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("my_task_description_input")
                )
                // Checklist
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
                            modifier = Modifier.testTag("my_task_add_checklist_item")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add item", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    if (checklist.isEmpty()) {
                        Text(
                            text = "Break the task into small steps you can tick off.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }

                    checklist.forEach { item ->
                        key(item.id) {
                            val itemFocus = remember { FocusRequester() }
                            LaunchedEffect(checklistFocusId) {
                                if (checklistFocusId == item.id) {
                                    runCatching { itemFocus.requestFocus() }
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
                                        .focusRequester(itemFocus)
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
                                    .heightIn(min = 48.dp)
                                    .padding(
                                        start = 10.dp,
                                        top = 10.dp,
                                        bottom = 10.dp,
                                        // The clear button supplies the right-hand spacing while it is shown.
                                        end = if (selectedDueDateMillis != null) 0.dp else 10.dp
                                    ),
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
                                            ShrinkToFitText(
                                                text = DateFormats.fullDate(selectedDueDateMillis!!),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                minFontSize = 9.sp
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
                                        // 34 x 48 dp tap area. Wider would squeeze the date text in the half-width card.
                                        modifier = Modifier.size(width = 34.dp, height = 48.dp)
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
                                    .heightIn(min = 48.dp)
                                    .padding(
                                        start = 10.dp,
                                        top = 10.dp,
                                        bottom = 10.dp,
                                        // The clear button supplies the right-hand spacing while it is shown.
                                        end = if (!isTimeMissing) 0.dp else 10.dp
                                    ),
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
                                            ShrinkToFitText(
                                                text = timeStr,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                minFontSize = 9.sp
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
                                        // 34 x 48 dp tap area. Wider would squeeze the time text in the narrow card.
                                        modifier = Modifier.size(width = 34.dp, height = 48.dp)
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

                    // Quick time chips
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        QuickDateChip("12:00 PM", selected = dueHour == 12 && dueMinute == 0) {
                            dueHour = 12
                            dueMinute = 0
                        }
                        QuickDateChip("11:59 PM", selected = dueHour == 23 && dueMinute == 59) {
                            dueHour = 23
                            dueMinute = 59
                        }
                        QuickDateChip("9:00 AM", selected = dueHour == 9 && dueMinute == 0) {
                            dueHour = 9
                            dueMinute = 0
                        }
                        QuickDateChip("1:00 PM", selected = dueHour == 13 && dueMinute == 0) {
                            dueHour = 13
                            dueMinute = 0
                        }
                        QuickDateChip("5:00 PM", selected = dueHour == 17 && dueMinute == 0) {
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
                                label = { ShrinkToFitText(text = p.name.lowercase().replaceFirstChar { it.uppercase() }, minFontSize = 10.sp) },
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

                // Delete when done. Only for tasks that do not repeat.
                if (repeatRule == RepeatRule.NONE) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { autoDeleteWhenDone = !autoDeleteWhenDone }
                            .testTag("my_task_auto_delete_row"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Delete when done",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Removed automatically after the day you finish it.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoDeleteWhenDone,
                            onCheckedChange = { autoDeleteWhenDone = it }
                        )
                    }
                }

                // Reminders: one row that opens a small picker (several times can still be chosen)
                ReminderPickerRow(
                    selected = reminderOptions,
                    onSelectedChange = { reminderOptions = it },
                    chipTagPrefix = "my_task_reminder_chip",
                    rowTestTag = "my_task_reminder_row",
                    clearTestTag = "my_task_clear_reminders_button",
                    doneTestTag = "my_task_reminder_done_button"
                )
            }
            }
        }
    }
}


