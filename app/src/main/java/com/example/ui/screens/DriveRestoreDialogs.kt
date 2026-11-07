package com.example.ui.screens

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

/**
 * Shared restore dialogs used by both Settings (DriveBackupSection) and
 * the new-device restore prompt (DriveRestoreOfferCard).
 */
@Composable
fun DriveRestoreDialogs(viewModel: DriveBackupViewModel) {
    val state by viewModel.uiState.collectAsState()

    if (state.showNoBackupDialog) {
        DriveNoBackupFoundDialog(onDismiss = { viewModel.dismissNoBackupDialog() })
    }

    if (state.showRestoreConfirm) {
        val details = when {
            state.latestBackupInfo != null -> {
                val info = state.latestBackupInfo!!
                "${DriveBackupViewModel.formatBackupTime(info.exportedAt)}, ${DriveBackupViewModel.buildCountsText(info)}"
            }
            !state.offlineBackupText.isNullOrBlank() -> state.offlineBackupText!!
            else -> ""
        }
        DriveRestoreConfirmDialog(
            details = details,
            onConfirm = { viewModel.confirmRestore() },
            onDismiss = { viewModel.dismissRestoreConfirm() }
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
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore from Google Drive") },
        text = {
            Text(
                (if (details.isNotBlank()) "$details\n\n" else "") +
                    "Items already on this phone are kept. Missing items are added. " +
                    "Items you deleted after this backup was made will come back. " +
                    "App settings from the backup replace the settings on this phone."
            )
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
