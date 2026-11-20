package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.AppDatabase
import com.example.reminder.ReminderGuard
import com.example.reminder.ReminderHealth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Settings > Notifications & reminders > Check reminders. */
@Composable
fun ReminderCheckDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var items by remember { mutableStateOf(ReminderHealth.check(context)) }
    var testSent by remember { mutableStateOf(false) }

    // Checking also repairs: reminders that never rang are shown now and missing alarms are set again.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            runCatching { ReminderGuard.run(context, AppDatabase.getDatabase(context)) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reminder_check_check_reminders)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                items.forEach { item ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = if (item.ok) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = stringResource(if (item.ok) R.string.common_ok else R.string.reminder_check_needs_attention),
                            tint = if (item.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                item.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            if (!item.ok) {
                                Text(
                                    item.hint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(onClick = { ReminderHealth.openFix(context, item.check) }) {
                                    Text(stringResource(R.string.reminder_check_fix_this))
                                }
                            }
                        }
                    }
                }
                Text(
                    text = if (testSent) {
                        stringResource(R.string.reminder_check_test_sent)
                    } else {
                        stringResource(R.string.reminder_check_test_prompt)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { items = ReminderHealth.check(context) }) {
                    Text(stringResource(R.string.reminder_check_check_again))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                ReminderHealth.scheduleTest(context)
                testSent = true
            }) { Text(stringResource(R.string.reminder_check_send_test_reminder)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } }
    )
}
