package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * A small glowing dot for tabs that need attention (overdue tasks), used instead of a number.
 *
 * It has a steady soft glow around a glossy core, and a halo that slowly swells and fades. The
 * halo value is only read while drawing, so the animation redraws this small canvas and does not
 * recompose the navigation bar. The dot is only composed while something is overdue, so nothing
 * animates otherwise. Drawing outside the 10dp box is not clipped, which lets the glow spill out.
 */
@Composable
fun OverdueGlowDot(
    color: Color,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "overdueGlow")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "overdueGlowPulse"
    )
    Canvas(modifier = modifier.size(10.dp)) {
        val c = center
        val core = 3.5.dp.toPx()
        // Swelling halo that fades as it grows.
        drawCircle(
            color = color.copy(alpha = 0.40f * (1f - pulse)),
            radius = core * (1f + 2.4f * pulse),
            center = c
        )
        // Steady soft glow.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.55f), Color.Transparent),
                center = c,
                radius = core * 2.4f
            ),
            radius = core * 2.4f,
            center = c
        )
        // Core with a small highlight, so it reads as a lit bulb.
        drawCircle(color = color, radius = core, center = c)
        drawCircle(
            color = Color.White.copy(alpha = 0.55f),
            radius = core * 0.34f,
            center = Offset(c.x - core * 0.28f, c.y - core * 0.28f)
        )
    }
}
