package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.graphics.toColorInt
import com.example.R
import com.example.ui.theme.courseColorName
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.Course
import com.example.data.local.MY_TASKS_COURSE_CODE
import com.example.ui.theme.CourseColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditCourseDialog(
    courseToEdit: Course?,
    onDismiss: () -> Unit,
    onSave: (name: String, code: String, instructor: String, colorHex: String, location: String) -> Unit,
    duplicateError: String? = null,
    onClearDuplicateError: () -> Unit = {},
    archivedConflict: ArchivedCourseConflict? = null,
    onCheckArchivedCode: (suspend (String) -> String?)? = null,
    onRestoreSemester: (String) -> Unit = {}
) {

    var code by rememberSaveable { mutableStateOf(courseToEdit?.code ?: "") }
    var name by rememberSaveable { mutableStateOf(courseToEdit?.name ?: "") }
    var instructor by rememberSaveable { mutableStateOf(courseToEdit?.instructor ?: "") }
    var location by rememberSaveable { mutableStateOf(courseToEdit?.location ?: "") }
    var selectedColor by rememberSaveable { mutableStateOf(courseToEdit?.colorHex ?: CourseColors.first()) }
    var codeError by rememberSaveable { mutableStateOf(false) }
    var reservedCodeError by rememberSaveable { mutableStateOf(false) }
    var nameError by rememberSaveable { mutableStateOf(false) }

    var typingArchivedConflict by remember { mutableStateOf<ArchivedCourseConflict?>(null) }

    LaunchedEffect(code) {
        val trimmed = code.trim()
        if (trimmed.isNotBlank() && onCheckArchivedCode != null) {
            val semester = onCheckArchivedCode(trimmed)
            if (semester != null) {
                typingArchivedConflict = ArchivedCourseConflict(
                    code = trimmed.uppercase(),
                    semesterName = semester
                )
            } else {
                typingArchivedConflict = null
            }
        } else {
            typingArchivedConflict = null
        }
    }

    val activeArchivedConflict = typingArchivedConflict ?: archivedConflict

    // A tap outside or the back gesture asks first when something was typed. The Cancel button
    // is a deliberate tap, so it still closes right away.
    val discardGuard = rememberDiscardGuard(
        snapshot = changeSnapshot(code, name, instructor, location, selectedColor),
        onClose = onDismiss
    )
    discardGuard.Render()

    AlertDialog(
        onDismissRequest = { discardGuard.request() },
        title = {
            Text(
                text = stringResource(if (courseToEdit != null) R.string.add_edit_course_edit_course else R.string.add_edit_course_add_new_course),
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
                        reservedCodeError = false
                        onClearDuplicateError()
                    },

                    label = { Text(stringResource(R.string.add_edit_course_course_code)) },
                    placeholder = { Text(stringResource(R.string.add_edit_course_e_g_cs_301_math)) },
                    isError = codeError || reservedCodeError || duplicateError != null || activeArchivedConflict != null,
                    supportingText = when {
                        codeError -> { { Text(stringResource(R.string.add_edit_course_course_code_is_required)) } }
                        reservedCodeError -> { { Text(stringResource(R.string.add_edit_course_this_code_is_reserved)) } }
                        duplicateError != null -> { { Text(duplicateError) } }
                        activeArchivedConflict != null -> {
                            {
                                Column(modifier = Modifier.padding(top = 4.dp)) {
                                    Text(
                                        text = stringResource(R.string.add_edit_course_is_archived_in, activeArchivedConflict.code, activeArchivedConflict.semesterName),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    TextButton(
                                        onClick = {
                                            onRestoreSemester(activeArchivedConflict.semesterName)
                                        },
                                        modifier = Modifier.padding(top = 2.dp)
                                    ) {
                                        Text(stringResource(R.string.add_edit_course_restore, activeArchivedConflict.semesterName))
                                    }
                                }
                            }
                        }
                        else -> null
                    },

                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("course_code_input")
                )

                // Course Name
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it.take(100)
                        if (it.isNotBlank()) nameError = false
                    },
                    label = { Text(stringResource(R.string.add_edit_course_course_name)) },
                    placeholder = { Text(stringResource(R.string.add_edit_course_e_g_data_structures_algorithms)) },
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text(stringResource(R.string.add_edit_course_course_name_is_required)) }
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
                    label = { Text(stringResource(R.string.add_edit_course_instructor_professor)) },
                    placeholder = { Text(stringResource(R.string.add_edit_course_e_g_dr_jane_doe)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("course_instructor_input")
                )

                // Location / Room
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text(stringResource(R.string.add_edit_course_classroom_location)) },
                    placeholder = { Text(stringResource(R.string.add_edit_course_e_g_science_bldg_rm)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("course_location_input")
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
    // The 48dp touch areas already provide the spacing between swatches.
    horizontalArrangement = Arrangement.spacedBy(0.dp),
    verticalArrangement = Arrangement.spacedBy(0.dp)
) {
    CourseColors.forEach { hex ->
        val color = Color(hex.toColorInt())
        val isSelected = selectedColor.equals(hex, ignoreCase = true)
        // Outer box: 48dp touch target with a spoken name. Inner box: the 34dp visible circle.
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
                        contentDescription = null, // the selected state is announced by the radio button
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
                    val norm = code.replace(" ", "").trim().uppercase()
                    val isReserved = norm == MY_TASKS_COURSE_CODE
                    val isCodeEmpty = code.isBlank()
                    val isNameEmpty = name.isBlank()
                    if (isCodeEmpty) codeError = true
                    if (isReserved) reservedCodeError = true
                    if (isNameEmpty) nameError = true

                    if (!isCodeEmpty && !isNameEmpty && !isReserved && activeArchivedConflict == null) {
                        onSave(name, code, instructor, selectedColor, location)
                    }
                },
                modifier = Modifier.testTag("course_save_button")
            ) {
                Text(stringResource(if (courseToEdit != null) R.string.add_edit_course_update else R.string.add_edit_course_add_course))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("course_cancel_button")
            ) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
