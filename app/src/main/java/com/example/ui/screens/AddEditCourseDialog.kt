package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.Course
import com.example.ui.theme.CourseColors

@Composable
fun AddEditCourseDialog(
    courseToEdit: Course?,
    onDismiss: () -> Unit,
    onSave: (name: String, code: String, instructor: String, colorHex: String, location: String) -> Unit
) {
    var code by remember { mutableStateOf(courseToEdit?.code ?: "") }
    var name by remember { mutableStateOf(courseToEdit?.name ?: "") }
    var instructor by remember { mutableStateOf(courseToEdit?.instructor ?: "") }
    var location by remember { mutableStateOf(courseToEdit?.location ?: "") }
    var selectedColor by remember { mutableStateOf(courseToEdit?.colorHex ?: CourseColors.first()) }
    var codeError by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (courseToEdit != null) "Edit Course" else "Add New Course",
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
                        code = it
                        if (it.isNotBlank()) codeError = false
                    },
                    label = { Text("Course Code *") },
                    placeholder = { Text("e.g. CS 301, MATH 240") },
                    isError = codeError,
                    supportingText = if (codeError) {
                        { Text("Course code is required") }
                    } else null,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("course_code_input")
                )

                // Course Name
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (it.isNotBlank()) nameError = false
                    },
                    label = { Text("Course Name *") },
                    placeholder = { Text("e.g. Data Structures & Algorithms") },
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text("Course name is required") }
                    } else null,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("course_name_input")
                )

                // Instructor
                OutlinedTextField(
                    value = instructor,
                    onValueChange = { instructor = it },
                    label = { Text("Instructor / Professor") },
                    placeholder = { Text("e.g. Dr. Jane Doe") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("course_instructor_input")
                )

                // Location / Room
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Classroom / Location") },
                    placeholder = { Text("e.g. Science Bldg Rm 102") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("course_location_input")
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        CourseColors.forEach { hex ->
                            val color = Color(android.graphics.Color.parseColor(hex))
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
                        onSave(name, code, instructor, selectedColor, location)
                    }
                },
                modifier = Modifier.testTag("course_save_button")
            ) {
                Text(if (courseToEdit != null) "Update" else "Add Course")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("course_cancel_button")
            ) {
                Text("Cancel")
            }
        }
    )
}
