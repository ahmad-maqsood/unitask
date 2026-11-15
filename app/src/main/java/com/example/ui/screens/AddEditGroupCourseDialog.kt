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
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.R
import com.example.ui.theme.courseColorName
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
import com.example.ui.theme.rememberCourseColor

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
                text = stringResource(if (courseToEdit != null) R.string.add_edit_group_course_edit_group_course else R.string.add_edit_group_course_add_group_course),
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
                    label = { Text(stringResource(R.string.add_edit_course_course_code)) },
                    placeholder = { Text(stringResource(R.string.add_edit_group_course_e_g_cs_503_math)) },
                    isError = codeError || courseSaveError != null,
                    supportingText = when {
                        courseSaveError != null -> {
                            { Text(courseSaveError, color = MaterialTheme.colorScheme.error) }
                        }
                        codeError -> {
                            { Text(stringResource(R.string.add_edit_course_course_code_is_required)) }
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
                    label = { Text(stringResource(R.string.add_edit_course_course_name)) },
                    placeholder = { Text(stringResource(R.string.add_edit_group_course_e_g_theory_of_automata)) },
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text(stringResource(R.string.add_edit_course_course_name_is_required)) }
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
                    label = { Text(stringResource(R.string.add_edit_course_instructor_professor)) },
                    placeholder = { Text(stringResource(R.string.add_edit_group_course_e_g_dr_turing)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("group_course_instructor_input")
                )

                // Color Tag Picker
                Column {
                    Text(
                        text = stringResource(R.string.add_edit_course_course_color_tag),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
FlowRow(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(0.dp),
    verticalArrangement = Arrangement.spacedBy(0.dp)
) {
    CourseColors.forEach { hex ->
        val color = rememberCourseColor(hex)
        val isSelected = selectedColor.equals(hex, ignoreCase = true)
        Box(
            modifier = Modifier
                .size(48.dp)
                .selectable(
                    selected = isSelected,
                    role = Role.RadioButton,
                    onClick = { selectedColor = hex }
                )
                .semantics { contentDescription = courseColorName(hex) },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(color)
                    .then(
                        if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
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
                Text(stringResource(if (courseToEdit != null) R.string.add_edit_course_update else R.string.add_edit_course_add_course))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("group_course_cancel_button")
            ) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
