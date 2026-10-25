package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.util.BackgroundAccess

private enum class SetupStep {
    INTRO,
    WAITING_BATTERY,
    AUTOSTART
}

@Composable
fun BackgroundSetupDialog(
    onDismiss: () -> Unit = {},
    onFinished: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var currentStep by remember { mutableStateOf(SetupStep.INTRO) }
    var leftApp by remember { mutableStateOf(false) }

    val latestOnDismiss by rememberUpdatedState(onDismiss)
    val latestOnFinished by rememberUpdatedState(onFinished)
    val dismissDialog: () -> Unit = {
        latestOnDismiss()
        latestOnFinished()
    }

    // What happens after the battery step: the auto-start step on phones that have one, else finish.
    val afterBatteryStep: () -> Unit = {
        if (BackgroundAccess.needsAutostartStep()) {
            currentStep = SetupStep.AUTOSTART
        } else {
            dismissDialog()
        }
    }

    // While we wait for the system battery screen: move on only after the app was really left
    // (ON_PAUSE) and the user is back (ON_RESUME).
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (currentStep == SetupStep.WAITING_BATTERY) {
                if (event == Lifecycle.Event.ON_PAUSE) {
                    leftApp = true
                } else if (event == Lifecycle.Event.ON_RESUME && leftApp) {
                    leftApp = false
                    afterBatteryStep()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when (currentStep) {
        SetupStep.INTRO -> {
            AlertDialog(
                onDismissRequest = dismissDialog,
                title = { Text("Keep alerts on time") },
                text = {
                    Text("Some phones stop apps in the background, so task reminders and group alerts can arrive late or never. Two quick settings fix this.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (!BackgroundAccess.isIgnoringBatteryOptimizations(context)) {
                                leftApp = false
                                currentStep = SetupStep.WAITING_BATTERY
                                val started = BackgroundAccess.requestIgnoreBatteryOptimizations(context)
                                // No battery screen could be opened on this phone: do not get stuck.
                                if (!started) afterBatteryStep()
                            } else {
                                afterBatteryStep()
                            }
                        },
                        modifier = Modifier.testTag("background_setup_start_button")
                    ) {
                        Text("Set up")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = dismissDialog,
                        modifier = Modifier.testTag("background_setup_not_now_button")
                    ) {
                        Text("Not now")
                    }
                }
            )
        }

        SetupStep.WAITING_BATTERY -> {
            // No dialog is shown while the system battery screen is open.
            // The lifecycle observer above moves on when the user comes back.
        }

        SetupStep.AUTOSTART -> {
            AlertDialog(
                onDismissRequest = dismissDialog,
                title = { Text("Allow auto-start") },
                text = { Text(BackgroundAccess.brandHint()) },
                confirmButton = {
                    Button(
                        onClick = {
                            val started = BackgroundAccess.openAutostartSettings(context)
                            BackgroundAccess.setAutostartStepDone(context, true)
                            if (!started) {
                                Toast.makeText(
                                    context,
                                    "Could not open that screen. Find UniTask in your phone's app settings.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            dismissDialog()
                        },
                        modifier = Modifier.testTag("background_setup_open_settings_button")
                    ) {
                        Text("Open settings")
                    }
                },
                dismissButton = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                try {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(BackgroundAccess.guideUrl())).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                } catch (_: Exception) {}
                            },
                            modifier = Modifier.testTag("background_setup_guide_button")
                        ) {
                            Text("Steps for my phone")
                        }
                        TextButton(
                            onClick = dismissDialog,
                            modifier = Modifier.testTag("background_setup_skip_button")
                        ) {
                            Text("Skip")
                        }
                    }
                }
            )
        }
    }
}