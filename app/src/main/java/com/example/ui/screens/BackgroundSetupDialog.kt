package com.example.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.util.BackgroundAccess

private enum class SetupStep {
    INTRO,
    WAITING_BATTERY
}

/**
 * Asks the person to turn off battery optimization for UniTask, so reminders arrive on time.
 *
 * It does not ask for auto-start. Many phones have no such setting, and with battery optimization
 * off it is rarely needed, so the dialog only mentions it as something to try if reminders still
 * arrive late.
 */
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

    // While we wait for the system battery screen: finish only after the app was really left
    // (ON_PAUSE) and the user is back (ON_RESUME).
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (currentStep == SetupStep.WAITING_BATTERY) {
                if (event == Lifecycle.Event.ON_PAUSE) {
                    leftApp = true
                } else if (event == Lifecycle.Event.ON_RESUME && leftApp) {
                    leftApp = false
                    dismissDialog()
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
                AlertDialog(
                    onDismissRequest = dismissDialog,
                    title = { Text(stringResource(R.string.background_setup_battery_optimization_is_already)) },
                    text = {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            Text(stringResource(R.string.background_setup_unitask_is_already_allowed_to))
                            AutostartTip()
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = dismissDialog,
                            modifier = Modifier.testTag("background_setup_ok_button")
                        ) {
                            Text(stringResource(R.string.common_ok))
                        }
                    }
                )
            } else {
                AlertDialog(
                    onDismissRequest = dismissDialog,
                    title = { Text(stringResource(R.string.background_setup_keep_alerts_on_time)) },
                    text = {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            Text(stringResource(R.string.background_setup_some_phones_stop_apps_in))
                            AutostartTip()
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (!BackgroundAccess.isIgnoringBatteryOptimizations(context)) {
                                    leftApp = false
                                    currentStep = SetupStep.WAITING_BATTERY
                                    val started = BackgroundAccess.requestIgnoreBatteryOptimizations(context)
                                    // No battery screen could be opened on this phone: do not get stuck.
                                    if (!started) dismissDialog()
                                } else {
                                    dismissDialog()
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
            // The lifecycle observer above finishes when the user comes back.
        }
    }
}

/** The short note about auto-start: only something to try if reminders still arrive late. */
@Composable
private fun AutostartTip() {
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.reminders_autostart_tip),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
