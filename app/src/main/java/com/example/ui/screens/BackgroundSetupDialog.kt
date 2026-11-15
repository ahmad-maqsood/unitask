package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.util.BackgroundAccess

private enum class SetupStep {
    INTRO,
    WAITING_BATTERY,
    AUTOSTART
}

@Composable
fun BackgroundSetupDialog(
    onDismiss: () -> Unit = {},
    onFinished: () -> Unit = {},
    reviewMode: Boolean = false
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
    // In review mode (opened from Settings) the step is shown again even if it was done before.
    val afterBatteryStep: () -> Unit = {
        if (BackgroundAccess.needsAutostartStep() &&
            (reviewMode || !BackgroundAccess.isAutostartStepDone(context))
        ) {
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
            val batteryAlreadyOff = reviewMode && BackgroundAccess.isIgnoringBatteryOptimizations(context)

            if (batteryAlreadyOff) {
                // Opened from Settings and battery restrictions are already off: just inform.
                val hasAutostart = BackgroundAccess.needsAutostartStep()
                AlertDialog(
                    onDismissRequest = dismissDialog,
                    title = { Text(stringResource(R.string.background_setup_battery_optimization_is_already)) },
                    text = {
                        Text(
                            stringResource(if (hasAutostart) R.string.background_setup_unitask_is_already_allowed_to else R.string.background_setup_unitask_is_already_allowed_to_2),
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        )
                    },
                    confirmButton = {
                        if (hasAutostart) {
                            Button(
                                onClick = { currentStep = SetupStep.AUTOSTART },
                                modifier = Modifier.testTag("background_setup_check_autostart_button")
                            ) {
                                Text(stringResource(R.string.background_setup_check_auto_start))
                            }
                        } else {
                            Button(
                                onClick = dismissDialog,
                                modifier = Modifier.testTag("background_setup_ok_button")
                            ) {
                                Text(stringResource(R.string.common_ok))
                            }
                        }
                    },
                    dismissButton = {
                        if (hasAutostart) {
                            TextButton(
                                onClick = dismissDialog,
                                modifier = Modifier.testTag("background_setup_close_button")
                            ) {
                                Text(stringResource(R.string.common_close))
                            }
                        }
                    }
                )
            } else {
                AlertDialog(
                    onDismissRequest = dismissDialog,
                    title = { Text(stringResource(R.string.background_setup_keep_alerts_on_time)) },
                    text = {
                        Text(stringResource(R.string.background_setup_some_phones_stop_apps_in), modifier = Modifier.verticalScroll(rememberScrollState()))
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
                            Text(stringResource(R.string.background_setup_set_up))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = dismissDialog,
                            modifier = Modifier.testTag("background_setup_not_now_button")
                        ) {
                            Text(stringResource(R.string.background_setup_not_now))
                        }
                    }
                )
            }
        }

        SetupStep.WAITING_BATTERY -> {
            // No dialog is shown while the system battery screen is open.
            // The lifecycle observer above moves on when the user comes back.
        }

        SetupStep.AUTOSTART -> {
            AlertDialog(
                onDismissRequest = dismissDialog,
                title = { Text(stringResource(R.string.background_setup_allow_auto_start)) },
                text = {
                    Text(
                        text = BackgroundAccess.brandHint(),
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val started = BackgroundAccess.openAutostartSettings(context)
                            BackgroundAccess.setAutostartStepDone(context, true)
                            if (!started) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.toast_could_not_open_that_screen),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            dismissDialog()
                        },
                        modifier = Modifier.testTag("background_setup_open_settings_button")
                    ) {
                        Text(stringResource(R.string.onboarding_open_settings))
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
                                } catch (e: Exception) { Log.w("UniTask", "Could not open the background guide link", e) }
                            },
                            modifier = Modifier.testTag("background_setup_guide_button")
                        ) {
                            Text(
                                text = stringResource(R.string.background_setup_steps_for_my_phone),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(
                            onClick = dismissDialog,
                            modifier = Modifier.testTag("background_setup_skip_button")
                        ) {
                            Text(stringResource(R.string.onboarding_skip))
                        }
                    }
                }
            )
        }
    }
}
