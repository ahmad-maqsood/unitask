package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun CreateGroupDialog(
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onCreate: (name: String, username: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.GroupAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Create a Group", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it.take(60)
                        errorMessage = null
                    },
                    label = { Text("Group Name *") },
                    placeholder = { Text("e.g. CS 301 Study Group") },
                    singleLine = true,
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_group_name_input")
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { input ->
                        val sanitized = input.lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == '_' }.take(20)
                        username = sanitized
                        errorMessage = null
                    },
                    label = { Text("Unique Username (@handle) *") },
                    placeholder = { Text("e.g. cs301_team") },
                    prefix = { Text("@") },
                    supportingText = { Text("3-20 chars: lowercase letters, digits, and _") },
                    singleLine = true,
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_group_username_input")
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedName = name.trim()
                    if (trimmedName.isEmpty()) {
                        errorMessage = "Group name is required"
                        return@Button
                    }
                    if (username.length < 3) {
                        errorMessage = "Username must be at least 3 characters"
                        return@Button
                    }
                    onCreate(trimmedName, username)
                },
                enabled = !isLoading && name.isNotBlank() && username.length >= 3,
                modifier = Modifier.testTag("create_group_submit_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Create")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun JoinGroupDialog(
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onJoin: (username: String, joinCode: String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var joinCode by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Group, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Join a Group", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { input ->
                        val sanitized = input.trim().lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == '_' }.take(20)
                        username = sanitized
                        errorMessage = null
                    },
                    label = { Text("Group Username *") },
                    placeholder = { Text("e.g. cs301_team") },
                    prefix = { Text("@") },
                    singleLine = true,
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("join_group_username_input")
                )

                OutlinedTextField(
                    value = joinCode,
                    onValueChange = { input ->
                        joinCode = input.trim().uppercase().take(16)
                        errorMessage = null
                    },
                    label = { Text("8-Character Join Code *") },
                    placeholder = { Text("e.g. ABC23XYZ") },
                    singleLine = true,
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("join_group_code_input")
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanUsername = username.trim().lowercase()
                    val cleanCode = joinCode.trim().uppercase()
                    if (cleanUsername.isEmpty()) {
                        errorMessage = "Group username is required"
                        return@Button
                    }
                    if (cleanCode.length < 8) {
                        errorMessage = "Join code must be 8 characters"
                        return@Button
                    }
                    onJoin(cleanUsername, cleanCode)
                },
                enabled = !isLoading && username.isNotBlank() && joinCode.length >= 8,
                modifier = Modifier.testTag("join_group_submit_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Join")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun GroupCreatedSuccessDialog(
    groupName: String,
    username: String,
    joinCode: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Group Created!", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Your group \"$groupName\" (@$username) is ready. Share this join code with your classmates:")

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = joinCode,
                            modifier = Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Join Code", joinCode)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Join code copied!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.testTag("copy_created_join_code_button")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy code", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copy")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_group_created_dialog_button")
            ) {
                Text("Open Group")
            }
        }
    )
}
