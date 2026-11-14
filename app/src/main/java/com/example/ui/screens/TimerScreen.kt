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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.example.util.AppTip
import com.example.util.timer.PomodoroAlertSettings
import com.example.util.timer.PomodoroLogic
import com.example.util.timer.PomodoroPhase
import com.example.util.timer.PomodoroSettings
import com.example.util.timer.TimerController
import com.example.util.timer.TimerHistoryLogic
import com.example.util.timer.TimerLogic
import com.example.util.timer.TimerMode
import com.example.reminder.CountdownNotifier
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId

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
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var showPomodoroDialog by rememberSaveable { mutableStateOf(false) }
    var pomodoroSettings by remember { mutableStateOf(controller.pomodoroSettings()) }
    var showAlertsDialog by rememberSaveable { mutableStateOf(false) }
    var alertSettings by remember { mutableStateOf(controller.pomodoroAlertSettings()) }
    val autoStartAt by controller.autoStartAtMillis.collectAsStateWithLifecycle()
    val sessions by controller.sessions.collectAsStateWithLifecycle()

    // When fullscreen, Back exits full screen; when not fullscreen, Back closes the screen.
    BackHandler(enabled = !fullscreen) {
        onClose()
    }
    BackHandler(enabled = fullscreen) {
        fullscreen = false
    }
    BackHandler(enabled = showHistory && !fullscreen) {
        showHistory = false
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
        CountdownNotifier.finishIfDue(context, controller)
        onPauseOrDispose { }
    }

    // Current wall-clock timestamp aligned to second boundaries while running.
    val now by rememberNow(running = isRunning, controller = controller)

    val isIdle = TimerLogic.isIdle(state)
    val isPaused = TimerLogic.isPaused(state)
    val isFinished = TimerLogic.isFinished(state, now)

    // Pomodoro auto-start: seconds until the next round starts by itself (null when none is waiting).
    val autoStartSecondsLeft by rememberAutoStartSecondsLeft(autoStartAt, controller)
    val autoStartText = autoStartSecondsLeft?.let {
        PomodoroLogic.autoStartLabel(state, pomodoroSettings, it)
    }
    val cancelAutoStart = {
        // Silence the "round done" alert too, then leave the next round for the user to start.
        CountdownNotifier.cancelFinished(context)
        controller.cancelAutoStart()
    }

    val timeText = remember(state, now) {
        if (state.mode == TimerMode.COUNTDOWN) {
            TimerLogic.formatClock(TimerLogic.remainingMillis(state, now), roundUp = true)
        } else {
            TimerLogic.formatClock(TimerLogic.elapsedMillis(state, now), roundUp = false)
        }
    }

    if (fullscreen) {
        // Full screen mode: a dial with the time, round controls, and the keep-awake and exit buttons.
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

            // Dial and round controls; see TimerFullscreen.kt.
            FullscreenTimerContent(
                state = state,
                now = now,
                timeText = timeText,
                isIdle = isIdle,
                isRunning = isRunning,
                isPaused = isPaused,
                isFinished = isFinished,
                controller = controller,
                autoStartText = autoStartText,
                onCancelAutoStart = cancelAutoStart
            )
        }
    } else if (showHistory) {
        TimerHistoryScreen(
            sessions = sessions,
            onClose = { showHistory = false },
            onDeleteSession = { controller.deleteSession(it.startedAtMillis) },
            onClearAll = { controller.clearHistory() }
        )
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
                        if (state.pomodoroPhase != null) {
                            IconButton(
                                onClick = { showAlertsDialog = true },
                                modifier = Modifier.testTag("timer_pomodoro_options_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Pomodoro options"
                                )
                            }
                        }
                        IconButton(
                            onClick = { showHistory = true },
                            modifier = Modifier.testTag("timer_history_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Study history"
                            )
                        }
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
                // First visit hint with a link to the Timer guide
                OneTimeTip(
                    tip = AppTip.TIMER,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                // Mode switch: Stopwatch, Countdown or Pomodoro. Disabled while the timer is in progress.
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    SegmentedButton(
                        selected = state.mode == TimerMode.STOPWATCH,
                        onClick = { controller.setMode(TimerMode.STOPWATCH) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                        icon = {}, // no tick: it took room from the title and cut it off
                        enabled = isIdle,
                        modifier = Modifier.testTag("timer_mode_stopwatch")
                    ) {
                        ShrinkToFitText(text = "Stopwatch", style = MaterialTheme.typography.labelLarge, minFontSize = 11.sp)
                    }
                    SegmentedButton(
                        selected = state.mode == TimerMode.COUNTDOWN && state.pomodoroPhase == null,
                        onClick = { controller.setMode(TimerMode.COUNTDOWN) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                        icon = {},
                        enabled = isIdle,
                        modifier = Modifier.testTag("timer_mode_countdown")
                    ) {
                        ShrinkToFitText(text = "Countdown", style = MaterialTheme.typography.labelLarge, minFontSize = 11.sp)
                    }
                    SegmentedButton(
                        selected = state.pomodoroPhase != null,
                        onClick = { controller.enablePomodoro() },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                        icon = {},
                        enabled = isIdle,
                        modifier = Modifier.testTag("timer_mode_pomodoro")
                    ) {
                        ShrinkToFitText(text = "Pomodoro", style = MaterialTheme.typography.labelLarge, minFontSize = 11.sp)
                    }
                }

                // The same watch-style dial as in full screen, with the clock inside it. The ring
                // shows how much of a countdown is left, or how far into the current minute a
                // stopwatch is. It is drawn from the same wall-clock time as the clock text, so it
                // needs no extra ticking.
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val ringSize = minOf(maxWidth, 300.dp).coerceAtLeast(200.dp)
                    // The clock sits inside the orbit ring, which is 0.66 of the dial radius.
                    // The text shrinks when it is longer (for example 1:30:00).
                    val innerWidth = ringSize.value * 0.56f
                    val fontSize = (innerWidth * 0.95f / (timeText.length * 0.62f)).coerceIn(28f, 76f).sp
                    val isPomodoro = state.pomodoroPhase != null
                    val isClockTappable = state.mode == TimerMode.COUNTDOWN && isIdle
                    val clockModifier = if (isClockTappable) {
                        Modifier
                            .testTag("timer_clock_edit")
                            .clickable(
                                onClickLabel = if (isPomodoro) "Change Pomodoro lengths" else "Change countdown length",
                                role = Role.Button
                            ) {
                                if (isPomodoro) showPomodoroDialog = true else showCustomDialog = true
                            }
                    } else {
                        Modifier
                    }

                    TimerDial(
                        state = state,
                        now = now,
                        isRunning = isRunning,
                        isPaused = isPaused,
                        isFinished = isFinished,
                        size = ringSize
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = clockModifier
                        ) {
                            Text(
                                text = timeText,
                                fontSize = fontSize,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                modifier = Modifier.testTag("timer_time_text")
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = timerStatusLabel(
                                    state.mode, isRunning, isPaused, isFinished, state.pomodoroPhase
                                ).uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                                color = if (isFinished) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }

                // When a countdown is running, say when it ends. The slot keeps its height when
                // empty so the screen does not jump on Start.
                val endMillis = TimerLogic.scheduledEndTimeMillis(state)
                Box(
                    modifier = Modifier.height(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (endMillis != null) {
                        val endText = remember(endMillis) {
                            com.example.util.DateFormats.clock(endMillis)
                        }
                        Text(
                            text = "Ends at $endText",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("timer_end_time")
                        )
                    }
                }

                // Pomodoro: the next round is about to start by itself; Cancel keeps it for the user.
                if (autoStartText != null) {
                    AutoStartBanner(
                        text = autoStartText,
                        onCancel = cancelAutoStart,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }

                // Pomodoro: which round we are in, the lengths (tap to change) and a Skip button.
                if (state.pomodoroPhase != null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Round ${state.pomodoroRound} of ${pomodoroSettings.roundsBeforeLongBreak}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("timer_pomodoro_round")
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = false,
                                onClick = { showPomodoroDialog = true },
                                label = {
                                    Text(
                                        "${pomodoroSettings.focusMinutes} / " +
                                            "${pomodoroSettings.shortBreakMinutes} / " +
                                            "${pomodoroSettings.longBreakMinutes} min"
                                    )
                                },
                                enabled = isIdle,
                                modifier = Modifier.testTag("timer_pomodoro_settings")
                            )
                            FilterChip(
                                selected = false,
                                onClick = {
                                    CountdownNotifier.cancelFinished(context)
                                    controller.skipPomodoroPhase()
                                },
                                label = { Text("Skip") },
                                modifier = Modifier.testTag("timer_pomodoro_skip")
                            )
                        }
                    }
                }

                // Preset duration chips for Countdown mode. Disabled unless idle.
                if (state.mode == TimerMode.COUNTDOWN && state.pomodoroPhase == null) {
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
                    onReset = { controller.reset() },
                    finishedLabel = if (isFinished) PomodoroLogic.nextActionLabel(state, pomodoroSettings) else null,
                    onFinishedStart = {
                        // Silence the "phase done" alert, then begin the next focus or break.
                        CountdownNotifier.cancelFinished(context)
                        controller.start()
                    },
                    large = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Quick look at today's study time; tapping it opens the full history.
                val zone = remember { ZoneId.systemDefault() }
                val todayMillis = remember(sessions) {
                    TimerHistoryLogic.totalForDay(sessions, LocalDate.now(zone), zone)
                }
                Surface(
                    onClick = { showHistory = true },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .testTag("timer_today_card")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Studied today",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (todayMillis == 0L) "0 min" else TimerHistoryLogic.formatTotal(todayMillis),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "History",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

    if (showAlertsDialog) {
        PomodoroOptionsDialog(
            settings = alertSettings,
            onChange = { updated ->
                controller.setPomodoroAlertSettings(updated)
                alertSettings = controller.pomodoroAlertSettings()
            },
            onDismiss = { showAlertsDialog = false }
        )
    }

    if (showPomodoroDialog) {
        PomodoroSettingsDialog(
            initial = pomodoroSettings,
            onDismiss = { showPomodoroDialog = false },
            onConfirm = { newSettings ->
                controller.setPomodoroSettings(newSettings)
                pomodoroSettings = controller.pomodoroSettings()
                showPomodoroDialog = false
            }
        )
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
                        listOf(5 to "+5 min", 10 to "+10 min", 30 to "+30 min", 60 to "+1 h")
                            .forEach { (delta, label) ->
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        val (newH, newM) = TimerLogic.splitHoursAndMinutes(
                                            TimerLogic.addMinutes(totalMinutes, delta)
                                        )
                                        hoursText = if (newH > 0) newH.toString() else ""
                                        minutesText = newM.toString()
                                    },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag("timer_quick_add_$delta")
                                )
                            }
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

/**
 * Options for how a Pomodoro round ends. Each switch applies at once, so they can be changed
 * while a round is running. Kept here on the timer page, not in the main settings.
 */
@Composable
private fun PomodoroOptionsDialog(
    settings: PomodoroAlertSettings,
    onChange: (PomodoroAlertSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val seconds = PomodoroLogic.AUTO_START_DELAY_MILLIS / 1000L
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pomodoro options") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PomodoroSwitchRow(
                    title = "Ring like an alarm",
                    description = if (settings.ringLikeAlarm) {
                        "Rings and vibrates when a round ends. Turn off to vibrate only."
                    } else {
                        "Vibrates only, with no sound, when a round ends."
                    },
                    checked = settings.ringLikeAlarm,
                    tag = "timer_option_ring",
                    onCheckedChange = { onChange(settings.copy(ringLikeAlarm = it)) }
                )
                PomodoroSwitchRow(
                    title = "Auto-start next round",
                    description = "Starts the next break or focus round $seconds seconds after one ends.",
                    checked = settings.autoStartNext,
                    tag = "timer_option_auto_start",
                    onCheckedChange = { onChange(settings.copy(autoStartNext = it)) }
                )
                PomodoroSwitchRow(
                    title = "Stop after the long break",
                    description = "Wait for you to start the next cycle instead of starting it automatically.",
                    checked = settings.stopAfterLongBreak,
                    enabled = settings.autoStartNext,
                    tag = "timer_option_stop_after_long",
                    onCheckedChange = { onChange(settings.copy(stopAfterLongBreak = it)) }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("timer_options_done_button")
            ) {
                Text("Done")
            }
        }
    )
}

/** One line of the options dialog: a title with a short explanation, and a switch. */
@Composable
private fun PomodoroSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    tag: String,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                }
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.6f)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.testTag(tag)
        )
    }
}

/** "Short break starts in 7s" with a Cancel button, shown while the next round is about to start. */
@Composable
internal fun AutoStartBanner(
    text: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier
            .widthIn(max = 420.dp)
            .testTag("timer_auto_start_banner")
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("timer_auto_start_text")
            )
            TextButton(
                onClick = onCancel,
                modifier = Modifier.testTag("timer_auto_start_cancel")
            ) {
                Text("Cancel")
            }
        }
    }
}

/**
 * Whole seconds until a waiting auto-start fires, or null when none is waiting. When the wait runs
 * out while the screen is open it starts the round itself; the system alarm does the same when
 * the app is closed, and whichever comes first wins.
 */
@Composable
private fun rememberAutoStartSecondsLeft(
    autoStartAt: Long?,
    controller: TimerController
): State<Int?> {
    val secondsLeft = remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(autoStartAt) {
        if (autoStartAt == null) {
            secondsLeft.value = null
            return@LaunchedEffect
        }
        while (true) {
            val left = autoStartAt - controller.now()
            if (left <= 0L) {
                secondsLeft.value = null
                controller.autoStartIfDue()
                break
            }
            secondsLeft.value = ((left + 999L) / 1000L).toInt()
            delay(250L)
        }
    }
    return secondsLeft
}

/** Lets the user change the Pomodoro lengths and how many focus rounds come before the long break. */
@Composable
private fun PomodoroSettingsDialog(
    initial: PomodoroSettings,
    onDismiss: () -> Unit,
    onConfirm: (PomodoroSettings) -> Unit
) {
    var focusText by rememberSaveable { mutableStateOf(initial.focusMinutes.toString()) }
    var shortText by rememberSaveable { mutableStateOf(initial.shortBreakMinutes.toString()) }
    var longText by rememberSaveable { mutableStateOf(initial.longBreakMinutes.toString()) }
    var roundsText by rememberSaveable { mutableStateOf(initial.roundsBeforeLongBreak.toString()) }

    // Each is null while the typed text is empty or outside the allowed range.
    val focus = focusText.toIntOrNull()
        ?.takeIf { it in PomodoroSettings.MIN_MINUTES..PomodoroSettings.MAX_FOCUS_MINUTES }
    val shortBreak = shortText.toIntOrNull()
        ?.takeIf { it in PomodoroSettings.MIN_MINUTES..PomodoroSettings.MAX_BREAK_MINUTES }
    val longBreak = longText.toIntOrNull()
        ?.takeIf { it in PomodoroSettings.MIN_MINUTES..PomodoroSettings.MAX_BREAK_MINUTES }
    val rounds = roundsText.toIntOrNull()
        ?.takeIf { it in PomodoroSettings.MIN_ROUNDS..PomodoroSettings.MAX_ROUNDS }
    val canSave = focus != null && shortBreak != null && longBreak != null && rounds != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pomodoro") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PomodoroStepperRow(
                    label = "Focus (min)",
                    text = focusText,
                    isValid = focus != null,
                    tag = "timer_pomodoro_focus",
                    imeAction = ImeAction.Next,
                    onTextChange = { focusText = it },
                    onMinus = {
                        focusText = stepPomodoroText(
                            focusText, -5, PomodoroSettings.MIN_MINUTES, PomodoroSettings.MAX_FOCUS_MINUTES
                        )
                    },
                    onPlus = {
                        focusText = stepPomodoroText(
                            focusText, 5, PomodoroSettings.MIN_MINUTES, PomodoroSettings.MAX_FOCUS_MINUTES
                        )
                    }
                )
                PomodoroStepperRow(
                    label = "Short break (min)",
                    text = shortText,
                    isValid = shortBreak != null,
                    tag = "timer_pomodoro_short",
                    imeAction = ImeAction.Next,
                    onTextChange = { shortText = it },
                    onMinus = {
                        shortText = stepPomodoroText(
                            shortText, -1, PomodoroSettings.MIN_MINUTES, PomodoroSettings.MAX_BREAK_MINUTES
                        )
                    },
                    onPlus = {
                        shortText = stepPomodoroText(
                            shortText, 1, PomodoroSettings.MIN_MINUTES, PomodoroSettings.MAX_BREAK_MINUTES
                        )
                    }
                )
                PomodoroStepperRow(
                    label = "Long break (min)",
                    text = longText,
                    isValid = longBreak != null,
                    tag = "timer_pomodoro_long",
                    imeAction = ImeAction.Next,
                    onTextChange = { longText = it },
                    onMinus = {
                        longText = stepPomodoroText(
                            longText, -5, PomodoroSettings.MIN_MINUTES, PomodoroSettings.MAX_BREAK_MINUTES
                        )
                    },
                    onPlus = {
                        longText = stepPomodoroText(
                            longText, 5, PomodoroSettings.MIN_MINUTES, PomodoroSettings.MAX_BREAK_MINUTES
                        )
                    }
                )
                PomodoroStepperRow(
                    label = "Rounds before long break",
                    text = roundsText,
                    isValid = rounds != null,
                    tag = "timer_pomodoro_rounds",
                    imeAction = ImeAction.Done,
                    onTextChange = { roundsText = it },
                    onMinus = {
                        roundsText = stepPomodoroText(
                            roundsText, -1, PomodoroSettings.MIN_ROUNDS, PomodoroSettings.MAX_ROUNDS
                        )
                    },
                    onPlus = {
                        roundsText = stepPomodoroText(
                            roundsText, 1, PomodoroSettings.MIN_ROUNDS, PomodoroSettings.MAX_ROUNDS
                        )
                    }
                )
                if (!canSave) {
                    Text(
                        text = "Focus 1–${PomodoroSettings.MAX_FOCUS_MINUTES} min, breaks " +
                            "1–${PomodoroSettings.MAX_BREAK_MINUTES} min, rounds " +
                            "${PomodoroSettings.MIN_ROUNDS}–${PomodoroSettings.MAX_ROUNDS}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (focus != null && shortBreak != null && longBreak != null && rounds != null) {
                        onConfirm(
                            PomodoroSettings(
                                focusMinutes = focus,
                                shortBreakMinutes = shortBreak,
                                longBreakMinutes = longBreak,
                                roundsBeforeLongBreak = rounds
                            )
                        )
                    }
                },
                enabled = canSave,
                modifier = Modifier.testTag("timer_pomodoro_save_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("timer_pomodoro_cancel_button")
            ) {
                Text("Cancel")
            }
        }
    )
}

/** One line of the Pomodoro dialog: a label, a number the user can type, and minus / plus buttons. */
@Composable
private fun PomodoroStepperRow(
    label: String,
    text: String,
    isValid: Boolean,
    tag: String,
    imeAction: ImeAction,
    onTextChange: (String) -> Unit,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onMinus, modifier = Modifier.testTag(tag + "_minus")) {
            Icon(Icons.Default.Remove, contentDescription = "Less $label")
        }
        OutlinedTextField(
            value = text,
            onValueChange = { newValue ->
                if (newValue.length <= 3 && newValue.all { it.isDigit() }) {
                    onTextChange(newValue)
                }
            },
            singleLine = true,
            isError = !isValid,
            textStyle = MaterialTheme.typography.titleMedium.copy(
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = imeAction
            ),
            modifier = Modifier
                .width(76.dp)
                .testTag(tag + "_value")
        )
        IconButton(onClick = onPlus, modifier = Modifier.testTag(tag + "_plus")) {
            Icon(Icons.Default.Add, contentDescription = "More $label")
        }
    }
}

/** One minus / plus step on a typed value. Empty or out-of-range text counts as the lowest allowed value. */
private fun stepPomodoroText(text: String, delta: Int, min: Int, max: Int): String {
    val base = text.toIntOrNull()?.coerceIn(min, max) ?: min
    return (base + delta).coerceIn(min, max).toString()
}

/** The short line shown under the clock inside the ring. */
internal fun timerStatusLabel(
    mode: TimerMode,
    isRunning: Boolean,
    isPaused: Boolean,
    isFinished: Boolean,
    pomodoroPhase: PomodoroPhase? = null
): String = when {
    // Pomodoro: "Focus", "Short break", "Long break", "Paused", "Focus done" or "Break over".
    pomodoroPhase != null -> PomodoroLogic.statusLabel(pomodoroPhase, isPaused, isFinished)
    isFinished -> "Time is up"
    isRunning -> if (mode == TimerMode.COUNTDOWN) "Focus time" else "Studying"
    isPaused -> "Paused"
    mode == TimerMode.COUNTDOWN -> "Tap to change"
    else -> "Ready"
}

@Composable
private fun TimerControlButtons(
    isIdle: Boolean,
    isRunning: Boolean,
    isPaused: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit,
    // Pomodoro: text of the main button once a phase has finished ("Start break"); null otherwise.
    finishedLabel: String? = null,
    onFinishedStart: () -> Unit = {},
    // The normal screen uses taller buttons; full screen keeps the compact size.
    large: Boolean = false
) {
    val buttonHeight = if (large) 64.dp else 52.dp
    val labelStyle = if (large) {
        MaterialTheme.typography.titleMedium
    } else {
        MaterialTheme.typography.labelLarge
    }
    // When Reset is shown next to the main button, the main button gets more of the row.
    val mainWeight = if (!isIdle) 1.5f else 1f
    // Tighter side padding so labels such as "Reset" stay on one line with a large system font.
    val buttonPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)

    Row(
        // Buttons share the row, so they fit narrow phones; the cap keeps them sensible in landscape.
        modifier = Modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!isIdle) {
            OutlinedButton(
                onClick = onReset,
                modifier = Modifier
                    .weight(1f)
                    .height(buttonHeight)
                    .testTag("timer_reset_button"),
                contentPadding = buttonPadding
            ) {
                Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(6.dp))
                ShrinkToFitText(text = "Reset", style = labelStyle, minFontSize = 11.sp)
            }
        }

        when {
            isIdle -> {
                Button(
                    onClick = onStart,
                    modifier = Modifier
                        .weight(mainWeight)
                        .height(buttonHeight)
                        .testTag("timer_start_button"),
                    contentPadding = buttonPadding
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(26.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    ShrinkToFitText(text = "Start", style = labelStyle, minFontSize = 11.sp)
                }
            }
            isRunning -> {
                Button(
                    onClick = onPause,
                    modifier = Modifier
                        .weight(mainWeight)
                        .height(buttonHeight)
                        .testTag("timer_pause_button"),
                    contentPadding = buttonPadding
                ) {
                    Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(26.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    ShrinkToFitText(text = "Pause", style = labelStyle, minFontSize = 11.sp)
                }
            }
            isPaused -> {
                Button(
                    onClick = onResume,
                    modifier = Modifier
                        .weight(mainWeight)
                        .height(buttonHeight)
                        .testTag("timer_resume_button"),
                    contentPadding = buttonPadding
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(26.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    ShrinkToFitText(text = "Resume", style = labelStyle, minFontSize = 11.sp)
                }
            }
            finishedLabel != null -> {
                Button(
                    onClick = onFinishedStart,
                    modifier = Modifier
                        .weight(mainWeight)
                        .height(buttonHeight)
                        .testTag("timer_next_phase_button"),
                    contentPadding = buttonPadding
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(26.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    ShrinkToFitText(text = finishedLabel, style = labelStyle, minFontSize = 11.sp)
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
                    CountdownNotifier.finishIfDue(context, controller)
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
