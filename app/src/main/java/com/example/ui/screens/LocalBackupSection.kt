package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.util.BackupCounts
import com.example.util.BackupResult
import com.example.util.LocalBackupManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MIN_PASSWORD_LENGTH = 6

private fun describe(counts: BackupCounts): String =
    "${counts.courses} courses, ${counts.slots} classes, ${counts.tasks} assignments"

/**
 * Settings section: save everything to one .json file and restore it on any phone, no sign-in
 * needed. Files go through the system file picker, so no storage permission is required.
 */
@Composable
fun LocalBackupSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showExportDialog by remember { mutableStateOf(false) }
    var exportPasswordInput by remember { mutableStateOf("") }
    var exportPasswordForSave by remember { mutableStateOf("") }

    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var restorePasswordInput by remember { mutableStateOf("") }
    var restorePasswordError by remember { mutableStateOf<String?>(null) }

    var isWorking by remember { mutableStateOf(false) }
    var resultTitle by remember { mutableStateOf<String?>(null) }
    var resultMessage by remember { mutableStateOf("") }

    fun showResult(title: String, message: String) {
        resultTitle = title
        resultMessage = message
    }

    fun runRestore(uri: Uri, password: String?) {
        scope.launch {
            isWorking = true
            val result = LocalBackupManager.restore(context, uri, password)
            isWorking = false
            when (result) {
                is BackupResult.Success -> {
                    restoreUri = null
                    restorePasswordInput = ""
                    restorePasswordError = null
                    showResult(
                        "Backup restored",
                        "Added ${describe(result.counts)}. Items that were already on this phone were kept."
                    )
                }
                is BackupResult.Failure -> {
                    if (result.wrongPassword) {
                        restorePasswordError = result.message
                    } else {
                        restoreUri = null
                        restorePasswordInput = ""
                        restorePasswordError = null
                        showResult("Restore failed", result.message)
                    }
                }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        val password = exportPasswordForSave
        exportPasswordForSave = ""
        if (uri != null) {
            scope.launch {
                isWorking = true
                val result = LocalBackupManager.export(context, uri, password)
                isWorking = false
                when (result) {
                    is BackupResult.Success -> showResult(
                        "Backup saved",
                        "Saved ${describe(result.counts)}. Keep the file somewhere safe, such as Google Drive " +
                            "or another phone. Attached files are not included."
                    )
                    is BackupResult.Failure -> showResult("Backup failed", result.message)
                }
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isWorking = true
                val kind = LocalBackupManager.inspect(context, uri)
                isWorking = false
                when (kind) {
                    LocalBackupManager.Inspection.INVALID ->
                        showResult("Not a UniTask backup", "The chosen file is not a UniTask backup file.")
                    LocalBackupManager.Inspection.ENCRYPTED -> {
                        restorePasswordInput = ""
                        restorePasswordError = null
                        restoreUri = uri
                    }
                    LocalBackupManager.Inspection.PLAIN -> runRestore(uri, null)
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Offline backup",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        BackupRow(
            icon = Icons.Default.Backup,
            title = "Back up to a file",
            subtitle = "Courses, timetable and assignments in one .json file",
            onClick = {
                exportPasswordInput = ""
                showExportDialog = true
            },
            testTag = "settings_local_backup_row"
        )
        BackupRow(
            icon = Icons.Default.Restore,
            title = "Restore from a file",
            subtitle = "Works on any phone, no sign-in needed",
            onClick = { restoreLauncher.launch(arrayOf("*/*")) },
            testTag = "settings_local_restore_row"
        )
    }

    if (showExportDialog) {
        val tooShort = exportPasswordInput.isNotEmpty() && exportPasswordInput.length < MIN_PASSWORD_LENGTH
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Back up to a file") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Optional: set a password to encrypt the file. Leave it empty for a plain " +
                            "file. If you forget the password, the backup cannot be opened.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = exportPasswordInput,
                        onValueChange = { exportPasswordInput = it },
                        label = { Text("Password (optional)") },
                        singleLine = true,
                        isError = tooShort,
                        supportingText = if (tooShort) {
                            { Text("Use at least $MIN_PASSWORD_LENGTH characters") }
                        } else null,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("backup_password_input")
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !tooShort,
                    onClick = {
                        exportPasswordForSave = exportPasswordInput
                        exportPasswordInput = ""
                        showExportDialog = false
                        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                        exportLauncher.launch("unitask-backup-$stamp.json")
                    }
                ) {
                    Text("Choose location")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) { Text("Cancel") }
            }
        )
    }

    val pendingRestoreUri = restoreUri
    if (pendingRestoreUri != null) {
        AlertDialog(
            onDismissRequest = {
                restoreUri = null
                restorePasswordInput = ""
                restorePasswordError = null
            },
            title = { Text("Password needed") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "This backup is encrypted. Enter the password you set when you saved it.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = restorePasswordInput,
                        onValueChange = {
                            restorePasswordInput = it
                            restorePasswordError = null
                        },
                        label = { Text("Password") },
                        singleLine = true,
                        isError = restorePasswordError != null,
                        supportingText = restorePasswordError?.let { message -> { Text(message) } },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("restore_password_input")
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = restorePasswordInput.isNotEmpty() && !isWorking,
                    onClick = { runRestore(pendingRestoreUri, restorePasswordInput) }
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        restoreUri = null
                        restorePasswordInput = ""
                        restorePasswordError = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (isWorking) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Please wait") },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text("Working on your backup...")
                }
            }
        )
    }

    val title = resultTitle
    if (title != null) {
        AlertDialog(
            onDismissRequest = { resultTitle = null },
            title = { Text(title) },
            text = { Text(resultMessage) },
            confirmButton = {
                TextButton(onClick = { resultTitle = null }) { Text("OK") }
            }
        )
    }
}

@Composable
private fun BackupRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}