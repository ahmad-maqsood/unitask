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
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
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
                text = stringResource(R.string.drive_backup_google_drive_backup),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Text(
            text = stringResource(R.string.drive_backup_unitask_saves_one_backup_in),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        when (val conn = state.connection) {
            is DriveConnectionState.NotConnected -> {
                Text(
                    text = stringResource(R.string.drive_backup_google_will_ask_you_to),
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
                    Text(stringResource(R.string.drive_backup_connect_google_drive))
                }
            }

            is DriveConnectionState.NeedsReconnect -> {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.drive_backup_google_drive_access_was_lost),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Button(
                        onClick = { viewModel.connect() },
                        enabled = isNotBusy,
                        modifier = Modifier.testTag("drive_connect_button")
                    ) {
                        Text(stringResource(R.string.drive_backup_reconnect))
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
                            text = stringResource(R.string.drive_backup_account),
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
                val latestInfo = state.latestBackupInfo
                val offlineText = state.offlineBackupText
                val lastBackupSubtitle = when {
                    latestInfo != null ->
                        "${DriveBackupViewModel.formatBackupTime(latestInfo.exportedAt)}, ${DriveBackupViewModel.buildCountsText(LocalContext.current, latestInfo)}"
                    !offlineText.isNullOrBlank() -> offlineText
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
                            text = stringResource(R.string.drive_backup_last_backup),
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
                        Text(stringResource(R.string.backup_reminder_card_back_up_now))
                    }

                    OutlinedButton(
                        onClick = { viewModel.requestRestore() },
                        enabled = isNotBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("drive_restore_button")
                    ) {
                        Text(stringResource(R.string.drive_backup_restore_from_google_drive))
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Switches
                SettingsSwitchRow(
                    title = stringResource(R.string.drive_backup_back_up_automatically),
                    subtitle = stringResource(R.string.drive_backup_backs_up_shortly_after_you),
                    checked = state.autoEnabled,
                    enabled = isNotBusy,
                    onCheckedChange = { viewModel.setAuto(it) },
                    testTag = "drive_auto_switch"
                )

                SettingsSwitchRow(
                    title = stringResource(R.string.drive_backup_wi_fi_only),
                    subtitle = null,
                    checked = state.wifiOnly,
                    enabled = isNotBusy,
                    onCheckedChange = { viewModel.setWifiOnly(it) },
                    testTag = "drive_wifi_switch"
                )

                SettingsSwitchRow(
                    title = stringResource(R.string.drive_backup_include_attachments),
                    subtitle = stringResource(R.string.drive_backup_attached_files_can_be_large),
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
                    Text(stringResource(R.string.drive_backup_disconnect))
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
            title = { Text(stringResource(R.string.backup_reminder_card_back_up_now)) },
            text = { Text(stringResource(R.string.drive_backup_your_app_has_no_data)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEmptyWarningDialog = false
                        viewModel.backUpNow(allowEmpty = true)
                    }
                ) {
                    Text(stringResource(R.string.drive_backup_back_up_anyway))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyWarningDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    DriveRestoreDialogs(viewModel = viewModel)

    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text(stringResource(R.string.drive_backup_disconnect)) },
            text = {
                Text(stringResource(R.string.drive_backup_your_backup_stays_in_your))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDisconnectDialog = false
                        viewModel.disconnect()
                    }
                ) {
                    Text(stringResource(R.string.drive_backup_disconnect))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
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
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .padding(vertical = 4.dp)
            .testTag(testTag),
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
        // The whole row is the switch, so the Switch itself does not need its own click.
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
