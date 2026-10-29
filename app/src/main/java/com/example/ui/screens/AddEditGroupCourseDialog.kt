package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.groups.GroupCourse
import com.example.ui.theme.CourseColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditGroupCourseDialog(
    courseToEdit: GroupCourse?,
    courseSaveError: String? = null,
    onClearError: () -> Unit = {},
    onDismiss: () -> Unit,
    onSave: (code: String, name: String, instructor: String, colorHex: String) -> Unit
) {
    var code by rememberSaveable { mutableStateOf(courseToEdit?.code ?: "") }
    var name by rememberSaveable { mutableStateOf(courseToEdit?.name ?: "") }
    var instructor by rememberSaveable { mutableStateOf(courseToEdit?.instructor ?: "") }
    var selectedColor by rememberSaveable { mutableStateOf(courseToEdit?.colorHex ?: CourseColors.first()) }
    var codeError by rememberSaveable { mutableStateOf(false) }
    var nameError by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (courseToEdit != null) "Edit Group Course" else "Add Group Course",
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
                // Course Code
                OutlinedTextField(
                    value = code,
                    onValueChange = {
                        code = it.take(30)
                        if (it.isNotBlank()) codeError = false
                        onClearError()
                    },
                    label = { Text("Course Code *") },
                    placeholder = { Text("e.g. CS-503, MATH-240") },
                    isError = codeError || courseSaveError != null,
                    supportingText = when {
                        courseSaveError != null -> {
                            { Text(courseSaveError, color = MaterialTheme.colorScheme.error) }
                        }
                        codeError -> {
                            { Text("Course code is required") }
                        }
                        else -> null
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("group_course_code_input")
                )

                // Course Name
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it.take(100)
                        if (it.isNotBlank()) nameError = false
                        onClearError()
                    },
                    label = { Text("Course Name *") },
                    placeholder = { Text("e.g. Theory of Automata") },
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text("Course name is required") }
                    } else null,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("group_course_name_input")
                )

                // Instructor
                OutlinedTextField(
                    value = instructor,
                    onValueChange = { instructor = it.take(60) },
                    label = { Text("Instructor / Professor") },
                    placeholder = { Text("e.g. Dr. Turing") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("group_course_instructor_input")
                )

                // Color Tag Picker
                Column {
                    Text(
                        text = "Course Color Tag",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CourseColors.forEach { hex ->
                            val color = try {
                                Color(android.graphics.Color.parseColor(hex))
                            } catch (_: Exception) {
                                Color(0xFF4F46E5)
                            }
                            val isSelected = selectedColor.equals(hex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .clickable { selectedColor = hex }
                                    .then(
                                        if (isSelected) Modifier.border(
                                            2.dp,
                                            MaterialTheme.colorScheme.onSurface,
                                            CircleShape
                                        ) else Modifier
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected color",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val isCodeEmpty = code.isBlank()
                    val isNameEmpty = name.isBlank()
                    if (isCodeEmpty) codeError = true
                    if (isNameEmpty) nameError = true

                    if (!isCodeEmpty && !isNameEmpty) {
                        onSave(code.trim(), name.trim(), instructor.trim(), selectedColor)
                    }
                },
                modifier = Modifier.testTag("group_course_save_button")
            ) {
                Text(if (courseToEdit != null) "Update" else "Add Course")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("group_course_cancel_button")
            ) {
                Text("Cancel")
            }
        }
    )
}
