package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.util.drive.DriveMigrationPrompt
import com.example.util.drive.DrivePrefs

/**
 * Migration prompt displayed to existing cloud sync users offering
 * a seamless one-click switch to Google Drive backup.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DriveMigrationDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DriveBackupViewModel = viewModel()
) {
    val context = LocalContext.current
    val prefs = remember { DrivePrefs(context) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var backupStarted by remember { mutableStateOf(false) }
    var alsoDaily by remember { mutableStateOf(true) }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onConsentResult(result.data)
    }

    LaunchedEffect(state.consentRequest) {
        state.consentRequest?.let { pendingIntent ->
            consentLauncher.launch(IntentSenderRequest.Builder(pendingIntent).build())
            viewModel.onConsentLaunched()
        }
    }

    val backupSucceeded = backupStarted && state.busy == null && !state.isError && state.lastResultMessage != null

    val onLater = {
        val (newCount, nextAt) = DriveMigrationPrompt.afterLater(
            prefs.migrationPromptCount,
            System.currentTimeMillis()
        )
        prefs.migrationPromptCount = newCount
        prefs.migrationNextPromptAt = nextAt
        onDismiss()
    }

    val onNever = {
        prefs.migrationPromptCount = DriveMigrationPrompt.afterNever(prefs.migrationPromptCount)
        onDismiss()
    }

    val onDone = {
        viewModel.setAuto(alsoDaily)
        prefs.migrationPromptCount = DriveMigrationPrompt.afterNever()
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = {
            if (backupSucceeded) {
                onDone()
            } else if (state.busy == null) {
                onLater()
            }
        },
        modifier = modifier.testTag("drive_migration_dialog"),
        title = {
            Text(text = "Back up to Google Drive")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!backupSucceeded) {
                    Text(
                        text = "UniTask no longer syncs your courses, tasks and notes to the online account. They stay on this phone. Back up to your own Google Drive so a lost or reset phone cannot take them with it.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Text(
                        text = "UniTask can only open its own hidden backup folder. " +
                            "It cannot see or change any of your other Google Drive files.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (state.busy != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text(
                            text = state.busy?.progressText ?: state.busy?.label ?: "Backing up...",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else if (backupSucceeded) {
                    Text(
                        text = state.lastResultMessage ?: "Backup complete.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { alsoDaily = !alsoDaily }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = alsoDaily,
                            onCheckedChange = { alsoDaily = it },
                            modifier = Modifier.testTag("drive_migration_auto_checkbox")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Also back up automatically",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else if (backupStarted && state.isError) {
                    Text(
                        text = state.lastResultMessage ?: "Backup failed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            when {
                backupSucceeded -> {
                    Button(
                        onClick = onDone,
                        modifier = Modifier.testTag("drive_migration_done_button")
                    ) {
                        Text("Done")
                    }
                }
                state.busy != null -> {
                    // No action while busy
                }
                backupStarted && state.isError -> {
                    Button(
                        onClick = { viewModel.startMigrationBackup() },
                        modifier = Modifier.testTag("drive_migration_backup_button")
                    ) {
                        Text("Try again")
                    }
                }
                else -> {
                    Button(
                        onClick = {
                            backupStarted = true
                            viewModel.startMigrationBackup()
                        },
                        modifier = Modifier.testTag("drive_migration_backup_button")
                    ) {
                        Text("Back up now")
                    }
                }
            }
        },
        dismissButton = if (!backupSucceeded && state.busy == null) {
            {
                if (backupStarted && state.isError) {
                    TextButton(
                        onClick = onLater,
                        modifier = Modifier.testTag("drive_migration_later_button")
                    ) {
                        Text("Remind me later")
                    }
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onNever) {
                            Text("Don't ask again")
                        }
                        TextButton(
                            onClick = onLater,
                            modifier = Modifier.testTag("drive_migration_later_button")
                        ) {
                            Text("Remind me later")
                        }
                    }
                }
            }
        } else {
            null
        }
    )
}
