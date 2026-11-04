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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.reminder.ReminderHealth

/** Settings > Notifications & reminders > Check reminders. */
@Composable
fun ReminderCheckDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var items by remember { mutableStateOf(ReminderHealth.check(context)) }
    var testSent by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Check reminders") },
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
                            contentDescription = if (item.ok) "OK" else "Needs attention",
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
                                    Text("Fix this")
                                }
                            }
                        }
                    }
                }
                Text(
                    text = if (testSent) {
                        "A test reminder will arrive in about 10 seconds. Lock your screen now to test it properly."
                    } else {
                        "Send a test reminder to see that it reaches this phone."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { items = ReminderHealth.check(context) }) {
                    Text("Check again")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                ReminderHealth.scheduleTest(context)
                testSent = true
            }) { Text("Send test reminder") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
