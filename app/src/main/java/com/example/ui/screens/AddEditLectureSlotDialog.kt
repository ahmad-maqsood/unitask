package com.example.ui.screens

import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.Course
import com.example.data.local.LectureSlot

val DaysOfWeekLabels = listOf(
    1 to "Mon",
    2 to "Tue",
    3 to "Wed",
    4 to "Thu",
    5 to "Fri",
    6 to "Sat",
    7 to "Sun"
)

@Composable
fun AddEditLectureSlotDialog(
    courses: List<Course>,
    slotToEdit: LectureSlot?,
    preselectedCourseId: Long?,
    preselectedDay: Int = 1,
    onDismiss: () -> Unit,
    onSave: (
        courseId: Long,
        dayOfWeek: Int,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        classroom: String,
        instructor: String
    ) -> Unit
) {
    val context = LocalContext.current

    var selectedCourseId by remember {
        mutableLongStateOf(
            slotToEdit?.courseId
                ?: preselectedCourseId
                ?: courses.firstOrNull()?.id
                ?: 0L
        )
    }

    var dayOfWeek by remember { mutableIntStateOf(slotToEdit?.dayOfWeek ?: preselectedDay) }
    var startHour by remember { mutableIntStateOf(slotToEdit?.startHour ?: 10) }
    var startMinute by remember { mutableIntStateOf(slotToEdit?.startMinute ?: 0) }
    var endHour by remember { mutableIntStateOf(slotToEdit?.endHour ?: 11) }
    var endMinute by remember { mutableIntStateOf(slotToEdit?.endMinute ?: 30) }

    val defaultCourse = courses.find { it.id == selectedCourseId }
    var classroom by remember { mutableStateOf(slotToEdit?.classroom ?: defaultCourse?.location ?: "") }
    var instructor by remember { mutableStateOf(slotToEdit?.instructor ?: defaultCourse?.instructor ?: "") }

    var courseDropdownExpanded by remember { mutableStateOf(false) }
    val selectedCourse = courses.find { it.id == selectedCourseId }

    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    if (showStartTimePicker) {
        Material3TimePickerDialog(
            title = "Select Class Start Time",
            initialHour = startHour,
            initialMinute = startMinute,
            onDismiss = { showStartTimePicker = false },
            onConfirm = { hour, minute ->
                startHour = hour
                startMinute = minute
                showStartTimePicker = false
            }
        )
    }

    if (showEndTimePicker) {
        Material3TimePickerDialog(
            title = "Select Class End Time",
            initialHour = endHour,
            initialMinute = endMinute,
            onDismiss = { showEndTimePicker = false },
            onConfirm = { hour, minute ->
                endHour = hour
                endMinute = minute
                showEndTimePicker = false
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (slotToEdit != null) "Edit Lecture Slot" else "Add Lecture Slot",
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
                // Course Dropdown
                Column {
                    Text(
                        text = "Course",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    Box {
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { courseDropdownExpanded = true }
                                .testTag("slot_course_picker"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = selectedCourse?.let { "${it.code} - ${it.name}" } ?: "Select Course",
                                    modifier = Modifier.weight(1f, fill = false),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = courseDropdownExpanded,
                            onDismissRequest = { courseDropdownExpanded = false }
                        ) {
                            courses.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text("${c.code} • ${c.name}") },
                                    onClick = {
                                        // Switching course fills in that course's room and instructor.
                                        // Re-picking the same course keeps anything already typed.
                                        if (c.id != selectedCourseId) {
                                            classroom = c.location
                                            instructor = c.instructor
                                        }
                                        selectedCourseId = c.id
                                        courseDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Day of Week Chips
                Column {
                    Text(
                        text = "Day of the Week",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DaysOfWeekLabels.forEach { (dayInt, label) ->
                            FilterChip(
                                selected = dayOfWeek == dayInt,
                                onClick = { dayOfWeek = dayInt },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag("day_chip_$dayInt")
                            )
                        }
                    }
                }

                // Start Time & End Time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Start Time
                    OutlinedCard(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                showStartTimePicker = true
                            }
                            .testTag("slot_start_time_picker"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Start Time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.size(4.dp))
                                Text(
                                    text = com.example.util.TimeFormatter.format12Hour(startHour, startMinute),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // End Time
                    OutlinedCard(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                showEndTimePicker = true
                            }
                            .testTag("slot_end_time_picker"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("End Time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.size(4.dp))
                                Text(
                                    text = com.example.util.TimeFormatter.format12Hour(endHour, endMinute),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Classroom / Hall
                OutlinedTextField(
                    value = classroom,
                    onValueChange = { classroom = it },
                    label = { Text("Classroom / Hall") },
                    placeholder = { Text("e.g. Faraday Hall, Room 102") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("slot_classroom_input")
                )

                // Instructor
                OutlinedTextField(
                    value = instructor,
                    onValueChange = { instructor = it },
                    label = { Text("Instructor / Lecturer") },
                    placeholder = { Text("e.g. Prof. Feynman") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("slot_instructor_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val startTotal = startHour * 60 + startMinute
                    val endTotal = endHour * 60 + endMinute
                    if (endTotal <= startTotal) {
                        android.widget.Toast.makeText(
                            context,
                            "End time must be after the start time",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    } else if (selectedCourseId > 0) {
                        onSave(
                            selectedCourseId,
                            dayOfWeek,
                            startHour,
                            startMinute,
                            endHour,
                            endMinute,
                            classroom.trim(),
                            instructor.trim()
                        )
                    } else {
                        android.widget.Toast.makeText(
                            context,
                            "Add a course first, then choose it for this class",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                modifier = Modifier.testTag("slot_save_button")
            ) {
                Text(if (slotToEdit != null) "Update Slot" else "Save Slot")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("slot_cancel_button")
            ) {
                Text("Cancel")
            }
        }
    )
}
