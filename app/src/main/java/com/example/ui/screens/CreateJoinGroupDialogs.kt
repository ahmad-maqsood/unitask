package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Image
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CreateGroupDialog(
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onCreate: (name: String, username: String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }

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
                        val sanitized = input.lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == '_' }.take(30)
                        username = sanitized
                        errorMessage = null
                    },
                    label = { Text("Unique Username (@handle) *") },
                    placeholder = { Text("e.g. cs301_team") },
                    prefix = { Text("@") },
                    supportingText = { Text("3-30 chars: lowercase letters, digits, and _") },
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
    onJoin: (username: String, joinCode: String) -> Unit,
    initialUsername: String = "",
    initialJoinCode: String = ""
) {
    val context = LocalContext.current
    var username by rememberSaveable { mutableStateOf(initialUsername) }
    var joinCode by rememberSaveable { mutableStateOf(initialJoinCode) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(initialUsername, initialJoinCode) {
        if (initialUsername.isNotBlank()) username = initialUsername
        if (initialJoinCode.isNotBlank()) joinCode = initialJoinCode
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bitmap != null) {
                    val decodedText = com.example.util.QrCodeUtil.decodeQrFromBitmap(bitmap)
                    if (decodedText != null) {
                        val parsed = com.example.util.QrCodeUtil.parseGroupInvite(decodedText)
                        if (parsed != null) {
                            username = parsed.first
                            joinCode = parsed.second
                            Toast.makeText(context, "QR code detected & filled!", Toast.LENGTH_SHORT).show()
                        } else {
                            errorMessage = "QR code found, but invalid invite format."
                        }
                    } else {
                        errorMessage = "Could not detect a QR code in that image."
                    }
                }
            } catch (e: Exception) {
                errorMessage = "Failed to read QR image: ${e.message}"
            }
        }
    }

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
                // QR Scanner and Clipboard paste shortcuts
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            try {
                                val options = GmsBarcodeScannerOptions.Builder()
                                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                                    .build()
                                val scanner = GmsBarcodeScanning.getClient(context, options)
                                scanner.startScan()
                                    .addOnSuccessListener { barcode ->
                                        val rawValue = barcode.rawValue
                                        if (!rawValue.isNullOrBlank()) {
                                            val parsed = com.example.util.QrCodeUtil.parseGroupInvite(rawValue)
                                            if (parsed != null) {
                                                username = parsed.first
                                                joinCode = parsed.second
                                                Toast.makeText(context, "QR code detected & filled!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                errorMessage = "QR code found, but invalid invite format."
                                            }
                                        }
                                    }
                                    .addOnCanceledListener {
                                        // Silent
                                    }
                                    .addOnFailureListener {
                                        Toast.makeText(
                                            context,
                                            "Camera scan is not available on this phone. Use From image.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                            } catch (_: Exception) {
                                Toast.makeText(
                                    context,
                                    "Camera scan is not available on this phone. Use From image.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Scan with camera", style = MaterialTheme.typography.labelSmall)
                    }

                    OutlinedButton(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("From image", style = MaterialTheme.typography.labelSmall)
                    }
                }

                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                        if (!clip.isNullOrBlank()) {
                            val parsed = com.example.util.QrCodeUtil.parseGroupInvite(clip)
                            if (parsed != null) {
                                username = parsed.first
                                joinCode = parsed.second
                                Toast.makeText(context, "Invite filled from clipboard!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "No valid group invite in clipboard", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Paste Link", style = MaterialTheme.typography.labelSmall)
                }

                OutlinedTextField(
                    value = username,
                    onValueChange = { input ->
                        val sanitized = input.trim().lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == '_' }.take(30)
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
                    val cleanUsername = username.trim().lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == '_' }.take(30)
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
fun GroupQrCodeDialog(
    groupName: String,
    username: String,
    joinCode: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val inviteUri = remember(username, joinCode) {
        com.example.util.QrCodeUtil.buildGroupInviteUri(username, joinCode)
    }
    var hasGenerated by remember(inviteUri) { mutableStateOf(false) }
    val qrBitmap by produceState<Bitmap?>(initialValue = null, inviteUri) {
        hasGenerated = false
        value = withContext(Dispatchers.Default) {
            com.example.util.QrCodeUtil.generateQrBitmap(inviteUri, 600)
        }
        hasGenerated = true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.QrCode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Group QR Code", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = groupName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                if (username.isNotBlank()) {
                    Text(
                        text = "@$username",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    modifier = Modifier
                        .size(220.dp)
                        .padding(8.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        val currentQr = qrBitmap
                        if (currentQr != null) {
                            Image(
                                bitmap = currentQr.asImageBitmap(),
                                contentDescription = "Group QR Code",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (!hasGenerated) {
                            CircularProgressIndicator(modifier = Modifier.size(36.dp))
                        } else {
                            Text(
                                text = "Could not generate QR code",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Join Code",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = joinCode,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Join Code", joinCode)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Code copied!", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy join code")
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Join my study group '$groupName' on UniTask!\nUsername: @$username\nJoin Code: $joinCode\nInvite Link: $inviteUri"
                            )
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share Group Invite"))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Share Invite")
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
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

@Composable
fun EditGroupDialog(
    initialName: String,
    initialUsername: String,
    currentJoinCode: String,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSaveProfile: (newName: String, newUsername: String) -> Unit,
    onRegenerateCode: () -> Unit
) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf(initialName) }
    var username by rememberSaveable { mutableStateOf(initialUsername) }
    var showRegenConfirm by remember { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    if (showRegenConfirm) {
        AlertDialog(
            onDismissRequest = { showRegenConfirm = false },
            title = { Text("Regenerate Join Code?") },
            text = {
                Text("The current join code ($currentJoinCode) will immediately stop working. Anyone trying to join with the old code will be rejected.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRegenConfirm = false
                        onRegenerateCode()
                    }
                ) {
                    Text("Regenerate")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRegenConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Group Settings", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
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
                        .testTag("edit_group_name_input")
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { input ->
                        val sanitized = input.lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == '_' }.take(30)
                        username = sanitized
                        errorMessage = null
                    },
                    label = { Text("Unique Username (@handle) *") },
                    placeholder = { Text("e.g. cs301_team") },
                    prefix = { Text("@") },
                    supportingText = { Text("3-30 chars: lowercase letters, digits, and _") },
                    singleLine = true,
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_group_username_input")
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Group Join Code / Password",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        SelectionContainer {
                            Text(
                                text = currentJoinCode.ifBlank { "--------" },
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 2.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (currentJoinCode.isNotBlank()) {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Join Code", currentJoinCode)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Join code copied!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = currentJoinCode.isNotBlank() && !isLoading,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy code", modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Copy",
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Button(
                                onClick = { showRegenConfirm = true },
                                enabled = !isLoading,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Regenerate code", modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "New code",
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Text(
                            text = "Regenerating creates a new 8-character code and invalidates the old one immediately.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

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
                    onSaveProfile(trimmedName, username)
                },
                enabled = !isLoading && name.isNotBlank() && username.length >= 3,
                modifier = Modifier.testTag("edit_group_submit_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Save Changes")
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
