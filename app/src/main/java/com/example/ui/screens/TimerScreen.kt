package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.util.timer.TimerController
import com.example.util.timer.TimerLogic
import com.example.util.timer.TimerMode
import com.example.reminder.CountdownNotifier
import kotlinx.coroutines.delay

/**
 * Full-screen timer supporting a stopwatch (count up) and a countdown (count down).
 * State is managed by TimerController and backed by SharedPreferences so it survives app restarts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(
    onClose: () -> Unit,
    controller: TimerController = TimerController.get(LocalContext.current)
) {
    val state by controller.state.collectAsStateWithLifecycle()
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var keepAwakeInFullscreen by rememberSaveable { mutableStateOf(controller.keepScreenOnInFullscreen()) }
    var showCustomDialog by rememberSaveable { mutableStateOf(false) }

    // When fullscreen, Back exits full screen; when not fullscreen, Back closes the screen.
    BackHandler(enabled = !fullscreen) {
        onClose()
    }
    BackHandler(enabled = fullscreen) {
        fullscreen = false
    }

    val context = LocalContext.current
    val view = LocalView.current

    // Keep the screen awake while the timer is counting, and in full screen when the user
    // left the "keep screen on" toggle on. Cleared again when this screen is left.
    val isRunning = TimerLogic.isRunning(state)
    val keepScreenOn = isRunning || (fullscreen && keepAwakeInFullscreen)
    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose {
            view.keepScreenOn = false
        }
    }


    // Manage system bars and sensor landscape orientation while in full-screen mode.
    DisposableEffect(fullscreen) {
        if (!fullscreen) return@DisposableEffect onDispose { }

        val activity = context.findActivity()
        val window = activity?.window
        val insetsController = if (window != null) WindowCompat.getInsetsController(window, view) else null

        insetsController?.let { c ->
            c.hide(WindowInsetsCompat.Type.systemBars())
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        onDispose {
            // Restore system bars and orientation on every exit path: exit button, Back,
            // closing the timer screen, or when the screen leaves composition.
            // This ensures landscape orientation and hidden bars never stay stuck.
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // When returning to the app, check if a running countdown completed while paused or in the background.
    LifecycleResumeEffect(Unit) {
        val stateBefore = controller.state.value
        val ring = !TimerLogic.isAlarmLate(stateBefore, controller.now())
        if (controller.markFinishedIfDue()) {
            CountdownNotifier.notifyFinished(
                context,
                controller.state.value.countdownTotalMillis,
                ring = ring
            )
        }
        onPauseOrDispose { }
    }

    // Current wall-clock timestamp aligned to second boundaries while running.
    val now by rememberNow(running = isRunning, controller = controller)

    val isIdle = TimerLogic.isIdle(state)
    val isPaused = TimerLogic.isPaused(state)
    val isFinished = TimerLogic.isFinished(state, now)

    val timeText = remember(state, now) {
        if (state.mode == TimerMode.COUNTDOWN) {
            TimerLogic.formatClock(TimerLogic.remainingMillis(state, now), roundUp = true)
        } else {
            TimerLogic.formatClock(TimerLogic.elapsedMillis(state, now), roundUp = false)
        }
    }

    if (fullscreen) {
        // Full screen mode: shows only the time, Time is up text, compact controls, and exit button.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.displayCutout)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .testTag("timer_screen")
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                IconButton(
                    onClick = {
                        keepAwakeInFullscreen = !keepAwakeInFullscreen
                        controller.setKeepScreenOnInFullscreen(keepAwakeInFullscreen)
                    },
                    modifier = Modifier.testTag("timer_keep_awake_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = if (keepAwakeInFullscreen) "Keep screen on: on" else "Keep screen on: off",
                        tint = if (keepAwakeInFullscreen) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        }
                    )
                }
                IconButton(
                    onClick = { fullscreen = false },
                    modifier = Modifier.testTag("timer_exit_fullscreen_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FullscreenExit,
                        contentDescription = "Exit full screen"
                    )
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                val fontSize = minOf(maxWidth.value * 0.25f, maxHeight.value * 0.45f).coerceIn(48f, 160f).sp
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = timeText,
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        modifier = Modifier.testTag("timer_time_text")
                    )
                    if (state.mode == TimerMode.COUNTDOWN && isFinished) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Time is up",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TimerControlButtons(
                        isIdle = isIdle,
                        isRunning = isRunning,
                        isPaused = isPaused,
                        onStart = { controller.start() },
                        onPause = { controller.pause() },
                        onResume = { controller.start() },
                        onReset = { controller.reset() }
                    )
                }
            }
        }
    } else {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .testTag("timer_screen"),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Timer",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.testTag("timer_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { fullscreen = true },
                            modifier = Modifier.testTag("timer_fullscreen_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "Full screen"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Mode switch: Stopwatch vs Countdown. Disabled while the timer is in progress.
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    SegmentedButton(
                        selected = state.mode == TimerMode.STOPWATCH,
                        onClick = { controller.setMode(TimerMode.STOPWATCH) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        enabled = isIdle,
                        modifier = Modifier.testTag("timer_mode_stopwatch")
                    ) {
                        Text("Stopwatch")
                    }
                    SegmentedButton(
                        selected = state.mode == TimerMode.COUNTDOWN,
                        onClick = { controller.setMode(TimerMode.COUNTDOWN) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        enabled = isIdle,
                        modifier = Modifier.testTag("timer_mode_countdown")
                    ) {
                        Text("Countdown")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Large monospace clock display.
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val fontSize = (maxWidth.value * 0.22f).coerceIn(48f, 96f).sp
                    val isClockTappable = state.mode == TimerMode.COUNTDOWN && isIdle
                    val clockModifier = if (state.mode == TimerMode.COUNTDOWN) {
                        Modifier
                            .testTag("timer_clock_edit")
                            .semantics { contentDescription = "Change countdown length" }
                            .clickable(enabled = isClockTappable) {
                                showCustomDialog = true
                            }
                    } else {
                        Modifier
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = clockModifier
                    ) {
                        Text(
                            text = timeText,
                            fontSize = fontSize,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            modifier = Modifier.testTag("timer_time_text")
                        )
                        if (state.mode == TimerMode.COUNTDOWN && isFinished) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Time is up",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Preset duration chips for Countdown mode. Disabled unless idle.
                if (state.mode == TimerMode.COUNTDOWN) {
                    val presetMinutes = listOf(15, 25, 45, 60)
                    val isCustomSelected = state.countdownTotalMillis !in presetMinutes.map { it * 60_000L }
                    val currentTotalMinutes = (state.countdownTotalMillis / 60_000L).toInt()
                    val customLabel = if (isCustomSelected) TimerLogic.formatDuration(currentTotalMinutes) else "Custom"

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        presetMinutes.forEach { minutes ->
                            FilterChip(
                                selected = state.countdownTotalMillis == minutes * 60_000L,
                                onClick = { controller.setCountdownMinutes(minutes) },
                                label = { Text("$minutes min") },
                                enabled = isIdle,
                                modifier = Modifier.testTag("timer_preset_$minutes")
                            )
                        }

                        FilterChip(
                            selected = isCustomSelected,
                            onClick = { showCustomDialog = true },
                            label = { Text(customLabel) },
                            enabled = isIdle,
                            modifier = Modifier.testTag("timer_preset_custom")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action controls: Start, Pause, Resume, and Reset.
                TimerControlButtons(
                    isIdle = isIdle,
                    isRunning = isRunning,
                    isPaused = isPaused,
                    onStart = { controller.start() },
                    onPause = { controller.pause() },
                    onResume = { controller.start() },
                    onReset = { controller.reset() }
                )
            }
        }
    }

    if (showCustomDialog) {
        val initialTotal = (state.countdownTotalMillis / 60_000L).toInt()
        val (initH, initM) = TimerLogic.splitHoursAndMinutes(initialTotal)
        var hoursText by rememberSaveable {
            mutableStateOf(if (initH > 0) initH.toString() else "")
        }
        var minutesText by rememberSaveable {
            mutableStateOf(initM.toString())
        }
        val minutesFocusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            minutesFocusRequester.requestFocus()
        }

        val h = hoursText.toIntOrNull() ?: 0
        val m = minutesText.toIntOrNull() ?: 0
        val totalMinutes = TimerLogic.combineHoursAndMinutes(h, m)
        val isValid = totalMinutes in TimerLogic.MIN_COUNTDOWN_MINUTES..TimerLogic.MAX_COUNTDOWN_MINUTES

        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            title = { Text("Countdown length") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = hoursText,
                            onValueChange = { newValue ->
                                if (newValue.length <= 2 && newValue.all { it.isDigit() }) {
                                    hoursText = newValue
                                }
                            },
                            label = { Text("Hours") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("timer_custom_hours_input")
                        )
                        OutlinedTextField(
                            value = minutesText,
                            onValueChange = { newValue ->
                                if (newValue.length <= 2 && newValue.all { it.isDigit() }) {
                                    minutesText = newValue
                                }
                            },
                            label = { Text("Minutes") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (isValid) {
                                        controller.setCountdownMinutes(totalMinutes)
                                        showCustomDialog = false
                                    }
                                }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(minutesFocusRequester)
                                .testTag("timer_custom_input")
                        )
                    }

                    Text(
                        text = "Total: ${TimerLogic.formatDuration(totalMinutes)}",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    if (!isValid) {
                        Text(
                            text = "Choose between 1 minute and 10 hours",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = false,
                            onClick = {
                                val newTotal = TimerLogic.addMinutes(totalMinutes, 5)
                                val (newH, newM) = TimerLogic.splitHoursAndMinutes(newTotal)
                                hoursText = if (newH > 0) newH.toString() else ""
                                minutesText = newM.toString()
                            },
                            label = { Text("+5 min") },
                            modifier = Modifier.testTag("timer_quick_add_5")
                        )
                        FilterChip(
                            selected = false,
                            onClick = {
                                val newTotal = TimerLogic.addMinutes(totalMinutes, 10)
                                val (newH, newM) = TimerLogic.splitHoursAndMinutes(newTotal)
                                hoursText = if (newH > 0) newH.toString() else ""
                                minutesText = newM.toString()
                            },
                            label = { Text("+10 min") },
                            modifier = Modifier.testTag("timer_quick_add_10")
                        )
                        FilterChip(
                            selected = false,
                            onClick = {
                                val newTotal = TimerLogic.addMinutes(totalMinutes, 30)
                                val (newH, newM) = TimerLogic.splitHoursAndMinutes(newTotal)
                                hoursText = if (newH > 0) newH.toString() else ""
                                minutesText = newM.toString()
                            },
                            label = { Text("+30 min") },
                            modifier = Modifier.testTag("timer_quick_add_30")
                        )
                        FilterChip(
                            selected = false,
                            onClick = {
                                val newTotal = TimerLogic.addMinutes(totalMinutes, 60)
                                val (newH, newM) = TimerLogic.splitHoursAndMinutes(newTotal)
                                hoursText = if (newH > 0) newH.toString() else ""
                                minutesText = newM.toString()
                            },
                            label = { Text("+1 h") },
                            modifier = Modifier.testTag("timer_quick_add_60")
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (isValid) {
                            controller.setCountdownMinutes(totalMinutes)
                            showCustomDialog = false
                        }
                    },
                    enabled = isValid,
                    modifier = Modifier.testTag("timer_custom_set_button")
                ) {
                    Text("Set")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCustomDialog = false },
                    modifier = Modifier.testTag("timer_custom_cancel_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun TimerControlButtons(
    isIdle: Boolean,
    isRunning: Boolean,
    isPaused: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!isIdle) {
            OutlinedButton(
                onClick = onReset,
                modifier = Modifier
                    .defaultMinSize(minWidth = 120.dp)
                    .height(52.dp)
                    .testTag("timer_reset_button")
            ) {
                Text("Reset")
            }
        }

        when {
            isIdle -> {
                Button(
                    onClick = onStart,
                    modifier = Modifier
                        .defaultMinSize(minWidth = 140.dp)
                        .height(52.dp)
                        .testTag("timer_start_button")
                ) {
                    Text("Start")
                }
            }
            isRunning -> {
                Button(
                    onClick = onPause,
                    modifier = Modifier
                        .defaultMinSize(minWidth = 140.dp)
                        .height(52.dp)
                        .testTag("timer_pause_button")
                ) {
                    Text("Pause")
                }
            }
            isPaused -> {
                Button(
                    onClick = onResume,
                    modifier = Modifier
                        .defaultMinSize(minWidth = 140.dp)
                        .height(52.dp)
                        .testTag("timer_resume_button")
                ) {
                    Text("Resume")
                }
            }
        }
    }
}

/**
 * Returns a State holding wall-clock timestamps aligned to second boundaries while running.
 */
@Composable
private fun rememberNow(running: Boolean, controller: TimerController): State<Long> {
    val context = LocalContext.current
    val nowState = remember { mutableStateOf(controller.now()) }
    LaunchedEffect(running) {
        if (running) {
            while (true) {
                val current = controller.now()
                nowState.value = current
                val state = controller.state.value
                if (state.mode == TimerMode.COUNTDOWN && TimerLogic.remainingMillis(state, current) == 0L) {
                    val ring = !TimerLogic.isAlarmLate(state, current)
                    if (controller.markFinishedIfDue()) {
                        CountdownNotifier.notifyFinished(
                            context,
                            controller.state.value.countdownTotalMillis,
                            ring = ring
                        )
                    }
                }
                val millisIntoSecond = (current % 1000L).toInt()
                val delayMillis = (1000 - millisIntoSecond + 20).toLong()
                delay(delayMillis)
            }
        } else {
            nowState.value = controller.now()
        }
    }
    return nowState
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
