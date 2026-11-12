package com.example.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier

/**
 * Shared restore dialogs used by both Settings (DriveBackupSection) and
 * the new-device restore prompt (DriveRestoreOfferCard).
 */
@Composable
fun DriveRestoreDialogs(viewModel: DriveBackupViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.showNoBackupDialog) {
        DriveNoBackupFoundDialog(onDismiss = { viewModel.dismissNoBackupDialog() })
    }

    if (state.showRestoreConfirm) {
        val info = state.latestBackupInfo
        val offlineText = state.offlineBackupText
        val details = when {
            info != null -> "${DriveBackupViewModel.formatBackupTime(info.exportedAt)}, ${DriveBackupViewModel.buildCountsText(info)}"
            !offlineText.isNullOrBlank() -> offlineText
            else -> ""
        }
        val older = state.olderBackupInfo
        val olderDetails = older?.let {
            "${DriveBackupViewModel.formatBackupTime(it.exportedAt)}, ${DriveBackupViewModel.buildCountsText(it)}"
        }
        DriveRestoreConfirmDialog(
            details = if (state.useOlderBackup && olderDetails != null) olderDetails else details,
            onConfirm = { viewModel.confirmRestore() },
            onDismiss = { viewModel.dismissRestoreConfirm() },
            olderDetails = olderDetails,
            useOlder = state.useOlderBackup,
            onUseOlderChange = { viewModel.setUseOlderBackup(it) }
        )
    }
}

@Composable
fun DriveNoBackupFoundDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore from Google Drive") },
        text = { Text("No backup found in this Google account.") },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

@Composable
fun DriveRestoreConfirmDialog(
    details: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    /** Details of the older backup, or null when Drive holds only one. */
    olderDetails: String? = null,
    useOlder: Boolean = false,
    onUseOlderChange: (Boolean) -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore from Google Drive") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    (if (details.isNotBlank()) {
                        (if (olderDetails != null) {
                            if (useOlder) "Older backup: " else "Latest backup: "
                        } else "") + "$details\n\n"
                    } else "") +
                        "Items already on this phone are kept. Missing items are added. " +
                        "Items you deleted after this backup was made will come back. " +
                        "App settings from the backup replace the settings on this phone."
                )
                if (olderDetails != null) {
                    TextButton(
                        onClick = { onUseOlderChange(!useOlder) },
                        modifier = Modifier.testTag("drive_restore_older_toggle")
                    ) {
                        Text(
                            if (useOlder) "Use the latest backup instead"
                            else "Use the older backup instead"
                        )
                    }
                    if (!useOlder) {
                        Text(
                            "Older backup: $olderDetails",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Restore")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
