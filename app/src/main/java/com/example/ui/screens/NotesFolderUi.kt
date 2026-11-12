package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.ui.theme.CourseColors
import com.example.ui.theme.courseColorName
import com.example.ui.theme.parseCourseColorOrNull
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.NoteFolder
import com.example.data.repository.NoteRepository

/** The colour to draw for a folder, or null when it has none (callers pick a neutral fallback). */
fun folderColorOrNull(colorHex: String?): Color? = parseCourseColorOrNull(colorHex)

/** Asks for a folder name and colour. Used both to create a folder and to edit one. */
@Composable
fun FolderNameDialog(
    title: String,
    confirmLabel: String,
    initialName: String = "",
    initialColorHex: String = "",
    error: String? = null,
    onConfirm: (name: String, colorHex: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var colorHex by remember { mutableStateOf(initialColorHex) }
    val canSubmit = name.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(NoteRepository.MAX_FOLDER_NAME) },
                    label = { Text("Folder name") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = { if (error != null) Text(error) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("folder_name_input")
                )
                Text(
                    text = "Colour",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 8.dp)
                )
                FolderColorPicker(
                    selectedHex = colorHex,
                    onSelect = { colorHex = it }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, colorHex) },
                enabled = canSubmit,
                modifier = Modifier.testTag("folder_name_confirm")
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/** A row of colour dots. The first option means "no colour"; tapping the chosen colour again clears it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FolderColorPicker(
    selectedHex: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        val options = listOf("") + CourseColors
        options.forEach { hex ->
            val isSelected = if (hex.isEmpty()) selectedHex.isBlank() else selectedHex.equals(hex, ignoreCase = true)
            val swatch = folderColorOrNull(hex)
            // Outer box: 48dp touch target with a spoken name. Inner box: the 32dp visible circle.
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = { onSelect(hex) }
                    )
                    .semantics { contentDescription = if (hex.isEmpty()) "No colour" else courseColorName(hex) }
                    .testTag("folder_color_${hex.ifEmpty { "none" }}"),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(swatch ?: MaterialTheme.colorScheme.surfaceVariant)
                        .then(
                            if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            else if (swatch == null) Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = if (swatch == null) MaterialTheme.colorScheme.onSurface else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Lets the user pick where one or more notes go: out of every folder, into an existing folder,
 * or into a brand new one.
 */
@Composable
fun MoveToFolderDialog(
    noteCount: Int,
    folders: List<NoteFolder>,
    folderCounts: Map<Long?, Int>,
    onPickFolder: (Long?) -> Unit,
    onNewFolder: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (noteCount == 1) "Move note to" else "Move $noteCount notes to")
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 340.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                FolderPickRow(
                    label = "No folder",
                    colorHex = "",
                    count = folderCounts[null] ?: 0,
                    modifier = Modifier.testTag("move_to_no_folder"),
                    onClick = { onPickFolder(null) }
                )
                for (folder in folders) {
                    FolderPickRow(
                        label = folder.name,
                        colorHex = folder.colorHex,
                        count = folderCounts[folder.id] ?: 0,
                        modifier = Modifier.testTag("move_to_folder_${folder.id}"),
                        onClick = { onPickFolder(folder.id) }
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNewFolder)
                        .padding(vertical = 12.dp)
                        .testTag("move_to_new_folder"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CreateNewFolder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "New folder",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun FolderPickRow(
    label: String,
    colorHex: String,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = null,
            tint = folderColorOrNull(colorHex) ?: MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Confirms deleting a folder. The notes inside are kept. */
@Composable
fun DeleteFolderDialog(
    folder: NoteFolder,
    noteCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete folder?") },
        text = {
            Text(
                if (noteCount == 0) {
                    "\"${folder.name}\" will be deleted."
                } else {
                    "\"${folder.name}\" will be deleted. Its $noteCount " +
                        (if (noteCount == 1) "note is" else "notes are") +
                        " kept and moved out of the folder."
                }
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag("delete_folder_confirm")
            ) {
                Text("Delete folder", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
