package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import com.example.util.UpdateChecker
import com.example.util.UpdateResult

private const val PREFS = "update_prompt"
private const val KEY_LAST_PROMPTED = "last_prompted_version"

/**
 * Checks GitHub once when the app opens. If a newer release exists, shows a pop-up with a button
 * that opens the release page. Each version is announced only once, so "Later" is respected.
 * The Settings sheet still shows the update dot and the "Open GitHub to download" button.
 */
@Composable
fun UpdatePrompt() {
    val context = LocalContext.current
    var update by remember { mutableStateOf<UpdateResult.Available?>(null) }

    LaunchedEffect(Unit) {
        val result = UpdateChecker.check(context)
        if (result is UpdateResult.Available) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (prefs.getString(KEY_LAST_PROMPTED, null) != result.latestVersion) {
                prefs.edit().putString(KEY_LAST_PROMPTED, result.latestVersion).apply()
                update = result
            }
        }
    }

    update?.let { available ->
        AlertDialog(
            onDismissRequest = { update = null },
            title = { Text("Update available") },
            text = {
                Text(
                    "A new version (${available.latestVersion}) of UniTask is available. " +
                        "You have ${UpdateChecker.currentVersion(context)}."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        update = null
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(available.pageUrl))
                            )
                        } catch (_: Exception) {
                        }
                    },
                    modifier = Modifier.testTag("update_prompt_open_button")
                ) { Text("Open release page") }
            },
            dismissButton = {
                TextButton(onClick = { update = null }) { Text("Later") }
            }
        )
    }
}
