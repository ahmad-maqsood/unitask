package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.util.timer.TimerLogic
import com.example.util.timer.TimerMode
import com.example.util.timer.TimerState
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The watch-style dial shared by the normal and the full-screen timer, so both look the same.
 *
 * It has 60 minute ticks that light up as the ring fills, a gradient progress arc with a glowing
 * head, an inner orbit where a small dot circles once a minute (one step per second), and a soft
 * glow behind it that breathes while the timer runs or when time is up. The clock and the status
 * text go in [content], which is centred inside the orbit ring (0.66 of the dial radius).
 */
@Composable
internal fun TimerDial(
    state: TimerState,
    now: Long,
    isRunning: Boolean,
    isPaused: Boolean,
    isFinished: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val countdown = state.mode == TimerMode.COUNTDOWN
    val accent = if (isFinished) colors.tertiary else colors.primary
    // A paused timer is dimmed, so it reads as "on hold" from across the room.
    val dialAccent = if (isPaused && !isFinished) accent.copy(alpha = 0.55f) else accent
    val onSurface = colors.onSurface

    // Ring progress, glided between one-second steps and snapped when it wraps or resets.
    val ringTarget = if (isFinished) 1f else TimerLogic.ringFraction(state, now)
    var previousRing by remember { mutableFloatStateOf(ringTarget) }
    val ringGlide = isRunning && abs(ringTarget - previousRing) < 0.25f
    val progress by animateFloatAsState(
        targetValue = ringTarget,
        animationSpec = if (ringGlide) tween(durationMillis = 1000, easing = LinearEasing) else snap(),
        label = "dialRing"
    )
    SideEffect { previousRing = ringTarget }

    // Position of the orbit dot: the seconds shown on the clock, as a fraction of a minute.
    val secondsShown = if (countdown) {
        (TimerLogic.remainingMillis(state, now) + 999L) / 1000L
    } else {
        TimerLogic.elapsedMillis(state, now) / 1000L
    }
    val secTarget = (secondsShown % 60L).toFloat() / 60f
    var previousSec by remember { mutableFloatStateOf(secTarget) }
    val secGlide = isRunning && abs(secTarget - previousSec) < 0.5f
    val secProgress by animateFloatAsState(
        targetValue = secTarget,
        animationSpec = if (secGlide) tween(durationMillis = 1000, easing = LinearEasing) else snap(),
        label = "dialSeconds"
    )
    SideEffect { previousSec = secTarget }

    val glow = rememberPulse(active = isRunning || isFinished)

    Box(
        modifier = modifier
            .size(size)
            .testTag("timer_ring"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawDial(
                accent = dialAccent,
                onSurface = onSurface,
                progress = progress,
                secProgress = secProgress,
                glow = glow.value,
                running = isRunning,
                countdown = countdown
            )
        }
        content()
    }
}

/**
 * A slow 0..1 breathing value. The infinite animation only exists while [active], so an idle or
 * paused timer on a kept-awake screen does not redraw every frame.
 */
@Composable
private fun rememberPulse(active: Boolean): State<Float> {
    if (!active) return remember { mutableStateOf(0.35f) }
    val transition = rememberInfiniteTransition(label = "dialPulse")
    return transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dialPulseValue"
    )
}

/** Draws the glow, minute ticks, progress arc with head, and the seconds orbit. */
private fun DrawScope.drawDial(
    accent: Color,
    onSurface: Color,
    progress: Float,
    secProgress: Float,
    glow: Float,
    running: Boolean,
    countdown: Boolean
) {
    val c = center
    val r = size.minDimension / 2f

    // Ambient glow. It is drawn past the dial's bounds, which is not clipped.
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(accent.scaled(0.30f * glow), Color.Transparent),
            center = c,
            radius = r * 1.55f
        ),
        radius = r * 1.55f,
        center = c
    )
    // Soft face so the dial reads as one object.
    drawCircle(color = onSurface.copy(alpha = 0.05f), radius = r * 0.93f, center = c)

    // 60 minute ticks; the ones passed by the progress light up.
    for (i in 0 until 60) {
        val angle = Math.toRadians((i * 6 - 90).toDouble())
        val long = i % 5 == 0
        val outer = r * 0.99f
        val inner = outer - r * (if (long) 0.075f else 0.04f)
        val lit = progress > 0f && (i / 60f) < progress
        val tickColor = if (lit) {
            accent.scaled(if (long) 1f else 0.75f)
        } else {
            onSurface.copy(alpha = if (long) 0.32f else 0.14f)
        }
        val dx = cos(angle).toFloat()
        val dy = sin(angle).toFloat()
        drawLine(
            color = tickColor,
            start = Offset(c.x + dx * inner, c.y + dy * inner),
            end = Offset(c.x + dx * outer, c.y + dy * outer),
            strokeWidth = (if (long) 2.4.dp else 1.4.dp).toPx(),
            cap = StrokeCap.Round
        )
    }

    // Progress arc with a gradient that brightens toward its head.
    val arcRadius = r * 0.80f
    val arcStroke = r * 0.07f
    val arcTopLeft = Offset(c.x - arcRadius, c.y - arcRadius)
    val arcSize = Size(arcRadius * 2f, arcRadius * 2f)
    drawCircle(
        color = onSurface.copy(alpha = 0.08f),
        radius = arcRadius,
        center = c,
        style = Stroke(width = arcStroke)
    )
    if (progress > 0f) {
        val head = progress.coerceIn(0.02f, 1f)
        rotate(degrees = -90f, pivot = c) {
            drawArc(
                brush = Brush.sweepGradient(
                    colorStops = arrayOf(
                        0f to accent.scaled(0.25f),
                        head to accent,
                        1f to accent
                    ),
                    center = c
                ),
                startAngle = 0f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = arcStroke, cap = StrokeCap.Round)
            )
        }
        // Glowing head at the end of the arc.
        val headAngle = Math.toRadians((-90f + 360f * progress).toDouble())
        val headCenter = Offset(
            c.x + cos(headAngle).toFloat() * arcRadius,
            c.y + sin(headAngle).toFloat() * arcRadius
        )
        drawCircle(color = accent.scaled(0.30f), radius = arcStroke * 1.35f, center = headCenter)
        drawCircle(color = Color.White, radius = arcStroke * 0.42f, center = headCenter)
    }

    // Seconds orbit: a thin ring with a dot that takes one minute per lap. A countdown runs the
    // other way, like its arc, so the dot gets a short fading trail behind it.
    val orbitRadius = r * 0.66f
    drawCircle(
        color = onSurface.copy(alpha = 0.10f),
        radius = orbitRadius,
        center = c,
        style = Stroke(width = 1.2.dp.toPx())
    )
    val dotDegrees = -90f + 360f * secProgress
    if (running) {
        drawArc(
            color = accent.scaled(0.55f),
            startAngle = dotDegrees,
            sweepAngle = if (countdown) 50f else -50f,
            useCenter = false,
            topLeft = Offset(c.x - orbitRadius, c.y - orbitRadius),
            size = Size(orbitRadius * 2f, orbitRadius * 2f),
            style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        )
    }
    val dotAngle = Math.toRadians(dotDegrees.toDouble())
    val dotCenter = Offset(
        c.x + cos(dotAngle).toFloat() * orbitRadius,
        c.y + sin(dotAngle).toFloat() * orbitRadius
    )
    drawCircle(color = accent.scaled(0.28f), radius = r * 0.045f, center = dotCenter)
    drawCircle(
        color = accent.scaled(if (running) 1f else 0.5f),
        radius = r * 0.022f,
        center = dotCenter
    )
}

/** Multiplies the colour's alpha, so a dimmed (paused) accent stays dimmed in every element. */
private fun Color.scaled(factor: Float): Color = copy(alpha = alpha * factor)
