package com.example.ui.screens

import android.app.DatePickerDialog
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
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.Course
import com.example.data.local.Priority
import com.example.data.local.ReminderOption
import com.example.data.local.TaskAttachment
import com.example.data.local.TaskItem
import com.example.data.local.TaskStatus
import com.example.util.AttachmentManager
import com.example.util.SavedAttachmentInfo
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
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

    var title by rememberSaveable { mutableStateOf(taskToEdit?.title ?: "") }
    var description by rememberSaveable { mutableStateOf(taskToEdit?.description ?: "") }

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

    var dueHour by rememberSaveable { mutableIntStateOf(taskToEdit?.dueTimeHour ?: 23) }
    var dueMinute by rememberSaveable { mutableIntStateOf(taskToEdit?.dueTimeMinute ?: 59) }
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
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

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

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            isAttachingFile = true
            coroutineScope.launch {
                val savedInfo = AttachmentManager.copyUriToInternalStorage(context, selectedUri)
                if (savedInfo != null) {
                    newAttachments = newAttachments + savedInfo
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
                                        val cColor = try {
                                            Color(android.graphics.Color.parseColor(selectedCourse.colorHex))
                                        } catch (_: Exception) {
                                            Color(0xFF4F46E5)
                                        }
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
                                    val cColor = try {
                                        Color(android.graphics.Color.parseColor(c.colorHex))
                                    } catch (_: Exception) {
                                        Color(0xFF4F46E5)
                                    }
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
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("task_description_input")
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
                        // Date selector
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
                                Text(
                                    text = formattedDate,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
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
                                Text(
                                    text = timeStr,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
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
                                    .testTag("priority_chip_${p.name}")
                            )
                        }
                    }
                }

                // Status
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
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("status_chip_${s.name}")
                            )
                        }
                    }
                }

                // Local Notification Reminders (Multiple selections allowed)
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
                                modifier = Modifier.testTag("clear_reminders_button")
                            ) {
                                Text("Clear all", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (reminderOptions.isEmpty()) {
                            "Select multiple times to receive multiple alerts, or none."
                        } else {
                            "${reminderOptions.size} reminder${if (reminderOptions.size > 1) "s" else ""} selected (alerts will fire at each time)"
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
                                modifier = Modifier.testTag("reminder_chip_${opt.name}")
                            )
                        }
                    }
                }

                // Offline File Attachments Section
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
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Offline Attachments",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        OutlinedButton(
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
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("attach_file_button")
                        ) {
                            if (isAttachingFile) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.size(6.dp))
                                Text("Importing...", style = MaterialTheme.typography.labelSmall)
                            } else {
                                Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.size(4.dp))
                                Text("Attach File", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Existing attachments
                    if (existingAttachments.isNotEmpty()) {
                        Text(
                            text = "Saved files:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            existingAttachments.forEach { att ->
                                AttachmentChip(
                                    fileName = att.fileName,
                                    fileSize = att.fileSize,
                                    mimeType = att.mimeType,
                                    onDelete = { onDeleteExistingAttachment?.invoke(att) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    // Newly attached files (pending save)
                    if (newAttachments.isNotEmpty()) {
                        Text(
                            text = "New files to attach:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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

                    if (existingAttachments.isEmpty() && newAttachments.isEmpty()) {
                        Text(
                            text = "No files attached. Attach PDFs, syllabus sheets, or rubric images for offline viewing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
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
                            description,
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
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
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
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
