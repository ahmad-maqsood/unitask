package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.timer.TimerController
import com.example.util.timer.TimerLogic
import com.example.util.timer.TimerMode
import com.example.util.timer.TimerState

/**
 * Content of the full-screen timer: a watch-style dial next to large round controls.
 *
 * The dial itself is [TimerDial], shared with the normal timer screen.
 * In a tall window (split screen) the controls move under the dial.
 */
@Composable
fun FullscreenTimerContent(
    state: TimerState,
    now: Long,
    timeText: String,
    isIdle: Boolean,
    isRunning: Boolean,
    isPaused: Boolean,
    isFinished: Boolean,
    controller: TimerController,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val accent = if (isFinished) colors.tertiary else colors.primary
    val statusText = timerStatusLabel(state.mode, isRunning, isPaused, isFinished)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        val wide = maxWidth > maxHeight * 1.15f
        val dialSize = if (wide) minOf(maxHeight, maxWidth * 0.52f) else minOf(maxWidth, maxHeight * 0.6f)

        val dial: @Composable () -> Unit = {
            TimerDial(
                state = state,
                now = now,
                isRunning = isRunning,
                isPaused = isPaused,
                isFinished = isFinished,
                size = dialSize
            ) {
                // The clock sits inside the orbit ring, which is 0.66 of the dial radius.
                val innerWidth = dialSize.value * 0.56f
                val fontSize = (innerWidth * 0.95f / (timeText.length * 0.62f)).coerceIn(28f, 92f).sp
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                        text = statusText.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = if (isFinished) accent else colors.onSurfaceVariant
                    )
                }
            }
        }

        val panel: @Composable (Alignment.Horizontal) -> Unit = { align ->
            FullscreenTimerPanel(
                state = state,
                accent = accent,
                isIdle = isIdle,
                isRunning = isRunning,
                isFinished = isFinished,
                controller = controller,
                align = align
            )
        }

        if (wide) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(40.dp, Alignment.CenterHorizontally)
            ) {
                dial()
                panel(Alignment.Start)
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)
            ) {
                dial()
                panel(Alignment.CenterHorizontally)
            }
        }
    }
}

/** Mode pill, end time and the round controls that sit beside (or under) the dial. */
@Composable
private fun FullscreenTimerPanel(
    state: TimerState,
    accent: Color,
    isIdle: Boolean,
    isRunning: Boolean,
    isFinished: Boolean,
    controller: TimerController,
    align: Alignment.Horizontal
) {
    val colors = MaterialTheme.colorScheme
    val countdown = state.mode == TimerMode.COUNTDOWN
    val modeText = if (countdown) {
        "COUNTDOWN · " + TimerLogic.formatDuration((state.countdownTotalMillis / 60_000L).toInt())
    } else {
        "STOPWATCH"
    }
    val endMillis = TimerLogic.scheduledEndTimeMillis(state)

    Column(
        horizontalAlignment = align,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.widthIn(max = 280.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = accent.copy(alpha = 0.14f),
            contentColor = accent
        ) {
            Text(
                text = modeText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }

        if (endMillis != null) {
            val endText = remember(endMillis) {
                java.text.DateFormat
                    .getTimeInstance(java.text.DateFormat.SHORT)
                    .format(java.util.Date(endMillis))
            }
            Text(
                text = "Ends at $endText",
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.testTag("timer_end_time")
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val buttonColors = IconButtonDefaults.filledIconButtonColors(
                containerColor = accent,
                contentColor = if (isFinished) colors.onTertiary else colors.onPrimary
            )
            when {
                isFinished -> {
                    // Time is up: the one useful action is to start over.
                    FilledIconButton(
                        onClick = { controller.reset() },
                        shape = CircleShape,
                        colors = buttonColors,
                        modifier = Modifier
                            .size(88.dp)
                            .testTag("timer_reset_button")
                    ) {
                        Icon(Icons.Default.Replay, contentDescription = "Reset", modifier = Modifier.size(40.dp))
                    }
                }
                else -> {
                    if (!isIdle) {
                        FilledTonalIconButton(
                            onClick = { controller.reset() },
                            shape = CircleShape,
                            modifier = Modifier
                                .size(64.dp)
                                .testTag("timer_reset_button")
                        ) {
                            Icon(Icons.Default.Replay, contentDescription = "Reset", modifier = Modifier.size(28.dp))
                        }
                    }
                    val (icon, description, tag) = when {
                        isIdle -> Triple(Icons.Default.PlayArrow, "Start", "timer_start_button")
                        isRunning -> Triple(Icons.Default.Pause, "Pause", "timer_pause_button")
                        else -> Triple(Icons.Default.PlayArrow, "Resume", "timer_resume_button")
                    }
                    FilledIconButton(
                        onClick = { if (isRunning) controller.pause() else controller.start() },
                        shape = CircleShape,
                        colors = buttonColors,
                        modifier = Modifier
                            .size(88.dp)
                            .testTag(tag)
                    ) {
                        Icon(icon, contentDescription = description, modifier = Modifier.size(44.dp))
                    }
                }
            }
        }
    }
}
