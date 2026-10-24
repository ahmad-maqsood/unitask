package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.util.UpdateChecker
import com.example.util.UpdateResult
import kotlinx.coroutines.launch

/**
 * Top-bar button. Checks GitHub once when the app opens (silently) and shows a dot on the icon
 * if a newer release exists. Tapping it opens a dialog, or runs a fresh check if none is known.
 */
@Composable
fun UpdateCheckButton() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<UpdateResult?>(null) }
    var isChecking by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }

    // Silent check when the app opens: only a found update is remembered.
    LaunchedEffect(Unit) {
        val r = UpdateChecker.check(context)
        if (r is UpdateResult.Available) result = r
    }

    val available = result as? UpdateResult.Available

    IconButton(
        onClick = {
            if (available != null) {
                showDialog = true
            } else if (!isChecking) {
                isChecking = true
                scope.launch {
                    result = UpdateChecker.check(context)
                    isChecking = false
                    showDialog = true
                }
            }
        },
        modifier = Modifier.testTag("top_bar_update_button")
    ) {
        if (isChecking) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            BadgedBox(badge = { if (available != null) Badge() }) {
                Icon(Icons.Default.SystemUpdate, contentDescription = "Check for updates")
            }
        }
    }

    if (showDialog) {
        val r = result
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text(
                    when (r) {
                        is UpdateResult.Available -> "Update available"
                        is UpdateResult.UpToDate -> "You're up to date"
                        else -> "Couldn't check for updates"
                    }
                )
            },
            text = {
                Text(
                    when (r) {
                        is UpdateResult.Available ->
                            "A new version (${r.latestVersion}) is available on GitHub. " +
                                "You have ${UpdateChecker.currentVersion(context)}."
                        is UpdateResult.UpToDate -> "You have the latest version (${r.currentVersion})."
                        is UpdateResult.Failed -> r.message
                        null -> ""
                    }
                )
            },
            confirmButton = {
                if (r is UpdateResult.Available) {
                    TextButton(onClick = {
                        showDialog = false
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.pageUrl)))
                        } catch (_: Exception) {
                        }
                    }) { Text("Open GitHub") }
                } else {
                    TextButton(onClick = { showDialog = false }) { Text("OK") }
                }
            },
            dismissButton = {
                if (r is UpdateResult.Available) {
                    TextButton(onClick = { showDialog = false }) { Text("Later") }
                }
            }
        )
    }
}
