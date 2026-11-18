package com.example.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.R

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
        val context = LocalContext.current
        val info = state.latestBackupInfo
        val offlineText = state.offlineBackupText
        val details = when {
            info != null -> "${DriveBackupViewModel.formatBackupTime(context, info.exportedAt)}, ${DriveBackupViewModel.buildCountsText(context, info)}"
            !offlineText.isNullOrBlank() -> offlineText
            else -> ""
        }
        val older = state.olderBackupInfo
        val olderDetails = older?.let {
            "${DriveBackupViewModel.formatBackupTime(context, it.exportedAt)}, ${DriveBackupViewModel.buildCountsText(context, it)}"
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
        title = { Text(stringResource(R.string.drive_backup_restore_from_google_drive)) },
        text = { Text(stringResource(R.string.drive_restore_no_backup_found_in_this)) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_ok))
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
        title = { Text(stringResource(R.string.drive_backup_restore_from_google_drive)) },
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
                            stringResource(if (useOlder) R.string.drive_restore_use_the_latest_backup_instead else R.string.drive_restore_use_the_older_backup_instead)
                        )
                    }
                    if (!useOlder) {
                        Text(
                            stringResource(R.string.drive_restore_older_backup, olderDetails),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.common_restore))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
