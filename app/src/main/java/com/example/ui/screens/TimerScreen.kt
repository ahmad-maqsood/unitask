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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
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

    // Keep the screen awake while the timer is actively counting.
    val isRunning = TimerLogic.isRunning(state)
    DisposableEffect(isRunning) {
        view.keepScreenOn = isRunning
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
        if (controller.markFinishedIfDue()) {
            CountdownNotifier.notifyFinished(context, controller.state.value.countdownTotalMillis)
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
            IconButton(
                onClick = { fullscreen = false },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .testTag("timer_exit_fullscreen_button")
            ) {
                Icon(
                    imageVector = Icons.Default.FullscreenExit,
                    contentDescription = "Exit full screen"
                )
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
                            label = { Text("Custom") },
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
        var inputMinutes by rememberSaveable {
            mutableStateOf((state.countdownTotalMillis / 60_000L).toString())
        }
        val parsed = inputMinutes.toIntOrNull()
        val isError = parsed == null || parsed !in TimerLogic.MIN_COUNTDOWN_MINUTES..TimerLogic.MAX_COUNTDOWN_MINUTES

        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            title = { Text("Custom duration") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = inputMinutes,
                        onValueChange = { newValue ->
                            if (newValue.all { it.isDigit() }) {
                                inputMinutes = newValue
                            }
                        },
                        label = { Text("Minutes (1 to 600)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = isError,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("timer_custom_input")
                    )
                    if (isError) {
                        Text(
                            text = "Enter a duration between 1 and 600 minutes",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (!isError && parsed != null) {
                            controller.setCountdownMinutes(parsed)
                            showCustomDialog = false
                        }
                    },
                    enabled = !isError,
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
                    if (controller.markFinishedIfDue()) {
                        CountdownNotifier.notifyFinished(context, controller.state.value.countdownTotalMillis)
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
