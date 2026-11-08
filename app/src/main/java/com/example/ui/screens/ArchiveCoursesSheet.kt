package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ArchiveCourseItem

/**
 * Bottom sheet allowing students to archive completed courses into a named semester.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveCoursesSheet(
    courses: List<ArchiveCourseItem>,
    existingSemesterNames: List<String>,
    onConfirmArchive: (courseIds: List<Long>, semesterName: String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedIds by remember(courses) {
        mutableStateOf(courses.map { it.id }.toSet())
    }
    var semesterName by rememberSaveable { mutableStateOf("") }

    val validation = validateSemesterName(semesterName)
    val existingMatch = findExistingSemester(semesterName, existingSemesterNames)

    val selectedCourses = courses.filter { it.id in selectedIds }
    val taskCount = selectedCourses.sumOf { it.taskCount }
    val classCount = selectedCourses.sumOf { it.classCount }
    val noteCount = selectedCourses.sumOf { it.noteCount }
    val summaryText = archiveSummaryText(taskCount, classCount, noteCount, existingMatch)

    val allSelected = courses.isNotEmpty() && selectedIds.size == courses.size
    val canConfirm = validation.isValid && selectedIds.isNotEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = Modifier.testTag("archive_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header row with title and select all / none toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Archive courses",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (courses.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            selectedIds = if (allSelected) {
                                emptySet()
                            } else {
                                courses.map { it.id }.toSet()
                            }
                        }
                    ) {
                        Text(if (allSelected) "Select none" else "Select all")
                    }
                }
            }

            Text(
                text = "Pick the courses you finished this semester. Archived courses, their tasks, classes and notes are hidden and their reminders stop.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Checklist of courses
            if (courses.isEmpty()) {
                Text(
                    text = "No active courses to archive.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    courses.forEach { course ->
                        val isChecked = course.id in selectedIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedIds = if (isChecked) {
                                        selectedIds - course.id
                                    } else {
                                        selectedIds + course.id
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    selectedIds = if (checked) {
                                        selectedIds + course.id
                                    } else {
                                        selectedIds - course.id
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = course.code,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (course.name.isNotBlank()) {
                                    Text(
                                        text = course.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.size(8.dp))
                            val taskLabel = if (course.taskCount == 1) "1 task" else "${course.taskCount} tasks"
                            Text(
                                text = taskLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Semester Name Input
            OutlinedTextField(
                value = semesterName,
                onValueChange = {
                    if (it.length <= 40) {
                        semesterName = it
                    }
                },
                label = { Text("Semester name") },
                placeholder = { Text("e.g. Fall 2026") },
                singleLine = true,
                isError = semesterName.isNotBlank() && !validation.isValid,
                supportingText = {
                    if (semesterName.isNotBlank() && !validation.isValid && validation.errorMessage != null) {
                        Text(validation.errorMessage)
                    } else {
                        Text("${semesterName.length}/40")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("archive_semester_name_input")
            )

            // Live Summary
            Text(
                text = summaryText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        if (canConfirm) {
                            onConfirmArchive(selectedIds.toList(), validation.trimmedName)
                        }
                    },
                    enabled = canConfirm,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("archive_confirm_button")
                ) {
                    Text("Archive")
                }
            }
        }
    }
}
