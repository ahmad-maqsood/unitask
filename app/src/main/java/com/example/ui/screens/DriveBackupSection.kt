package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

@Composable
fun DriveBackupSection(
    showHeading: Boolean = true,
    viewModel: DriveBackupViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var showEmptyWarningDialog by remember { mutableStateOf(false) }
    var showDisconnectDialog by remember { mutableStateOf(false) }

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

    val isNotBusy = state.busy == null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("drive_section"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (showHeading) {
            Text(
                text = "Google Drive backup",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Text(
            text = "UniTask saves one backup in a hidden folder of your Google Drive that only this app can see. " +
                "It cannot see your other files. It includes courses, timetable, assignments with their attachments, " +
                "My tasks, notes with pictures and your app settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        when (val conn = state.connection) {
            is DriveConnectionState.NotConnected -> {
                Text(
                    text = "Google will ask you to allow UniTask to use its own data in your Drive. " +
                        "That is only the hidden backup folder, nothing else.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { viewModel.connect() },
                    enabled = isNotBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("drive_connect_button")
                ) {
                    Text("Connect Google Drive")
                }
            }

            is DriveConnectionState.NeedsReconnect -> {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Google Drive access was lost.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Button(
                        onClick = { viewModel.connect() },
                        enabled = isNotBusy,
                        modifier = Modifier.testTag("drive_connect_button")
                    ) {
                        Text("Reconnect")
                    }
                }
            }

            is DriveConnectionState.Connected -> {
                // Account row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Account",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = conn.email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Last backup row
                val lastBackupSubtitle = when {
                    state.latestBackupInfo != null -> {
                        val info = state.latestBackupInfo!!
                        "${DriveBackupViewModel.formatBackupTime(info.exportedAt)}, ${DriveBackupViewModel.buildCountsText(info)}"
                    }
                    !state.offlineBackupText.isNullOrBlank() -> state.offlineBackupText!!
                    else -> "No backup yet"
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Last backup",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = lastBackupSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Action buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                if (viewModel.isAppEmpty()) {
                                    showEmptyWarningDialog = true
                                } else {
                                    viewModel.backUpNow(allowEmpty = false)
                                }
                            }
                        },
                        enabled = isNotBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("drive_backup_now_button")
                    ) {
                        Text("Back up now")
                    }

                    OutlinedButton(
                        onClick = { viewModel.requestRestore() },
                        enabled = isNotBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("drive_restore_button")
                    ) {
                        Text("Restore from Google Drive")
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Switches
                SettingsSwitchRow(
                    title = "Back up every day",
                    subtitle = "Runs when you open the app, at most once a day, and only when something changed.",
                    checked = state.autoEnabled,
                    enabled = isNotBusy,
                    onCheckedChange = { viewModel.setAuto(it) },
                    testTag = "drive_auto_switch"
                )

                SettingsSwitchRow(
                    title = "Wi-Fi only",
                    subtitle = null,
                    checked = state.wifiOnly,
                    enabled = isNotBusy,
                    onCheckedChange = { viewModel.setWifiOnly(it) },
                    testTag = "drive_wifi_switch"
                )

                SettingsSwitchRow(
                    title = "Include attachments",
                    subtitle = "Attached files can be large. Pictures and documents of your assignments.",
                    checked = state.includeAttachments,
                    enabled = isNotBusy,
                    onCheckedChange = { viewModel.setIncludeAttachments(it) },
                    testTag = "drive_attachments_switch"
                )

                TextButton(
                    onClick = { showDisconnectDialog = true },
                    enabled = isNotBusy,
                    modifier = Modifier.testTag("drive_disconnect_button")
                ) {
                    Text("Disconnect")
                }
            }
        }

        // Busy indicator
        if (state.busy != null) {
            Row(
                modifier = Modifier.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(
                    text = state.busy?.progressText ?: state.busy?.label ?: "",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Result message
        val resultMessage = state.lastResultMessage
        if (!resultMessage.isNullOrBlank()) {
            Text(
                text = resultMessage,
                color = if (state.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("drive_result_text")
            )
        }
    }

    if (showEmptyWarningDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyWarningDialog = false },
            title = { Text("Back up now") },
            text = { Text("Your app has no data. Backing up now would replace your Drive backup with an empty one.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEmptyWarningDialog = false
                        viewModel.backUpNow(allowEmpty = true)
                    }
                ) {
                    Text("Back up anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyWarningDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    DriveRestoreDialogs(viewModel = viewModel)

    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text("Disconnect") },
            text = {
                Text("Your backup stays in your Google Drive. To delete it: Google Drive settings, Manage apps, UniTask, Delete hidden app data.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDisconnectDialog = false
                        viewModel.disconnect()
                    }
                ) {
                    Text("Disconnect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.testTag(testTag)
        )
    }
}
