package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.produceState
import androidx.compose.ui.res.stringResource
import com.example.R
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
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
    val context = LocalContext.current
    val resources = LocalResources.current
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
                Text(stringResource(R.string.create_join_group_create_a_group), fontWeight = FontWeight.Bold)
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
                    label = { Text(stringResource(R.string.create_join_group_group_name)) },
                    placeholder = { Text(stringResource(R.string.create_join_group_e_g_cs_301_study)) },
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
                    label = { Text(stringResource(R.string.create_join_group_unique_username_handle)) },
                    placeholder = { Text(stringResource(R.string.create_join_group_e_g_cs301_team)) },
                    prefix = { Text("@") },
                    supportingText = { Text(stringResource(R.string.create_join_group_3_30_chars_lowercase_letters)) },
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
                        errorMessage = resources.getString(R.string.group_error_name_required)
                        return@Button
                    }
                    if (username.length < 3) {
                        errorMessage = resources.getString(R.string.group_error_username_short)
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
                    Text(stringResource(R.string.common_create))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text(stringResource(R.string.common_cancel))
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
    val resources = LocalResources.current
    val coroutineScope = rememberCoroutineScope()
    var username by rememberSaveable { mutableStateOf(initialUsername) }
    var joinCode by rememberSaveable { mutableStateOf(initialJoinCode) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var showScanner by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(initialUsername, initialJoinCode) {
        if (initialUsername.isNotBlank()) username = initialUsername
        if (initialJoinCode.isNotBlank()) joinCode = initialJoinCode
    }

    if (showScanner) {
        QrScannerDialog(
            onResult = { rawValue ->
                showScanner = false
                val parsed = com.example.util.QrCodeUtil.parseGroupInvite(rawValue)
                if (parsed != null) {
                    username = parsed.first
                    joinCode = parsed.second
                    errorMessage = null
                    Toast.makeText(context, resources.getString(R.string.toast_qr_code_detected_filled), Toast.LENGTH_SHORT).show()
                } else {
                    errorMessage = resources.getString(R.string.group_error_qr_invalid)
                }
            },
            onDismiss = { showScanner = false }
        )
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val decodedText = com.example.util.QrCodeUtil.decodeQrFromUri(context, uri)
                    if (decodedText != null) {
                        val parsed = com.example.util.QrCodeUtil.parseGroupInvite(decodedText)
                        if (parsed != null) {
                            username = parsed.first
                            joinCode = parsed.second
                            Toast.makeText(context, resources.getString(R.string.toast_qr_code_detected_filled), Toast.LENGTH_SHORT).show()
                        } else {
                            errorMessage = resources.getString(R.string.group_error_qr_invalid)
                        }
                    } else {
                        errorMessage = resources.getString(R.string.group_error_qr_not_found)
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    errorMessage = resources.getString(R.string.group_error_qr_read_failed, e.message.orEmpty())
                }
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
                Text(stringResource(R.string.create_join_group_join_a_group), fontWeight = FontWeight.Bold)
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
                        onClick = { showScanner = true },
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.create_join_group_scan_with_camera), style = MaterialTheme.typography.labelSmall)
                    }

                    OutlinedButton(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.create_join_group_from_image), style = MaterialTheme.typography.labelSmall)
                    }
                }

                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
                        if (!clip.isNullOrBlank()) {
                            val parsed = com.example.util.QrCodeUtil.parseGroupInvite(clip)
                            if (parsed != null) {
                                username = parsed.first
                                joinCode = parsed.second
                                Toast.makeText(context, resources.getString(R.string.toast_invite_filled_from_clipboard), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, resources.getString(R.string.toast_no_valid_group_invite_in), Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, resources.getString(R.string.toast_clipboard_is_empty), Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.create_join_group_paste_link), style = MaterialTheme.typography.labelSmall)
                }

                OutlinedTextField(
                    value = username,
                    onValueChange = { input ->
                        val sanitized = input.trim().lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == '_' }.take(30)
                        username = sanitized
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.create_join_group_group_username)) },
                    placeholder = { Text(stringResource(R.string.create_join_group_e_g_cs301_team)) },
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
                    label = { Text(stringResource(R.string.create_join_group_8_character_join_code)) },
                    placeholder = { Text(stringResource(R.string.create_join_group_e_g_abc23xyz)) },
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
                        errorMessage = resources.getString(R.string.group_error_group_username_required)
                        return@Button
                    }
                    if (cleanCode.length < 8) {
                        errorMessage = resources.getString(R.string.group_error_join_code_length)
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
                    Text(stringResource(R.string.common_join))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text(stringResource(R.string.common_cancel))
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
    val uiResources = androidx.compose.ui.platform.LocalResources.current
    val resources = LocalResources.current
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
                Text(stringResource(R.string.create_join_group_group_qr_code), fontWeight = FontWeight.Bold)
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
                                contentDescription = stringResource(R.string.create_join_group_group_qr_code),
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (!hasGenerated) {
                            CircularProgressIndicator(modifier = Modifier.size(36.dp))
                        } else {
                            Text(
                                text = stringResource(R.string.create_join_group_could_not_generate_qr_code),
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.create_join_group_join_code),
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
                                val clip = ClipData.newPlainText(uiResources.getString(R.string.create_join_group_join_code), joinCode)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, resources.getString(R.string.toast_code_copied), Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.create_join_group_copy_join_code))
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Join my study group '$groupName' on UniTask!\nUsername: @$username\nJoin Code: $joinCode\nInvite Link: ${com.example.util.QrCodeUtil.buildGroupInviteWebLink(username, joinCode)}"
                            )
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share Group Invite"))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.create_join_group_share_invite))
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
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
    val uiResources = androidx.compose.ui.platform.LocalResources.current
    val resources = LocalResources.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.create_join_group_group_created), fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.create_join_group_your_group_is_ready_share, groupName, username))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // The code gets its own line so all of it shows, and the Copy button sits below it.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = joinCode,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.testTag("created_join_code_text")
                        )
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText(uiResources.getString(R.string.create_join_group_join_code), joinCode)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, resources.getString(R.string.toast_join_code_copied), Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.testTag("copy_created_join_code_button")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.create_join_group_copy_code), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.create_join_group_copy))
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
                Text(stringResource(R.string.create_join_group_open_group))
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
    val uiResources = androidx.compose.ui.platform.LocalResources.current
    val resources = LocalResources.current
    var name by rememberSaveable { mutableStateOf(initialName) }
    var username by rememberSaveable { mutableStateOf(initialUsername) }
    var showRegenConfirm by remember { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    if (showRegenConfirm) {
        AlertDialog(
            onDismissRequest = { showRegenConfirm = false },
            title = { Text(stringResource(R.string.create_join_group_regenerate_join_code)) },
            text = {
                Text(stringResource(R.string.create_join_group_the_current_join_code_will, currentJoinCode))
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRegenConfirm = false
                        onRegenerateCode()
                    }
                ) {
                    Text(stringResource(R.string.create_join_group_regenerate))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRegenConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
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
                Text(stringResource(R.string.create_join_group_group_settings), fontWeight = FontWeight.Bold)
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
                    label = { Text(stringResource(R.string.create_join_group_group_name)) },
                    placeholder = { Text(stringResource(R.string.create_join_group_e_g_cs_301_study)) },
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
                    label = { Text(stringResource(R.string.create_join_group_unique_username_handle)) },
                    placeholder = { Text(stringResource(R.string.create_join_group_e_g_cs301_team)) },
                    prefix = { Text("@") },
                    supportingText = { Text(stringResource(R.string.create_join_group_3_30_chars_lowercase_letters)) },
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
                            text = stringResource(R.string.create_join_group_group_join_code_password),
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
                        // The two buttons are stacked, each one full width. Side by side they only got
                        // about 100dp each inside the dialog, which cut the "New code" label in half.
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (currentJoinCode.isNotBlank()) {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText(uiResources.getString(R.string.create_join_group_join_code), currentJoinCode)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, resources.getString(R.string.toast_join_code_copied), Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = currentJoinCode.isNotBlank() && !isLoading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("edit_group_copy_code_button")
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.create_join_group_copy_code),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Button(
                                onClick = { showRegenConfirm = true },
                                enabled = !isLoading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("edit_group_new_code_button")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.create_join_group_new_code),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.create_join_group_regenerating_creates_a_new_8),
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
                        errorMessage = uiResources.getString(R.string.group_error_name_required)
                        return@Button
                    }
                    if (username.length < 3) {
                        errorMessage = uiResources.getString(R.string.group_error_username_short)
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
                    Text(stringResource(R.string.create_join_group_save_changes))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
