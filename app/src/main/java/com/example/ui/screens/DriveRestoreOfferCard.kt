package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.util.drive.DrivePrefs

/**
 * Offer card shown at the top of the Deadlines tab empty state on fresh installs,
 * prompting the user to restore their previous Google Drive backup.
 *
 * Known limitation: the account is the one Google's consent screen picked.
 */
@Composable
fun DriveRestoreOfferCard(
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: DriveBackupViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("drive_restore_offer_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Had UniTask on another phone?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = "Restore your courses, tasks, notes, attachments and settings from your Google Drive backup.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "UniTask can only open its own hidden backup folder. " +
                    "It cannot see or change any of your other Google Drive files.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            if (state.busy != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(
                        text = state.busy?.progressText ?: state.busy?.label ?: "",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            val resultMessage = state.lastResultMessage
            if (!resultMessage.isNullOrBlank()) {
                Text(
                    text = resultMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("drive_result_text")
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Button(
                    onClick = { viewModel.requestRestore() },
                    enabled = isNotBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("drive_offer_restore_button")
                ) {
                    Text("Restore from Google Drive")
                }

                TextButton(
                    onClick = {
                        DrivePrefs(context).restoreOfferDismissed = true
                        onDismiss()
                    },
                    enabled = isNotBusy,
                    modifier = Modifier
                        .align(Alignment.End)
                        .testTag("drive_offer_dismiss_button")
                ) {
                    Text("Not now")
                }
            }
        }
    }

    DriveRestoreDialogs(viewModel = viewModel)
}
