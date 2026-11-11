package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/** Smallest and largest note text size, as a multiple of the normal size. */
const val NOTE_TEXT_SCALE_MIN = 0.7f
const val NOTE_TEXT_SCALE_MAX = 2.5f

private const val NOTE_TEXT_SCALE_PREFS = "unitask_settings"
private const val NOTE_TEXT_SCALE_KEY = "note_text_scale"

fun loadNoteTextScale(context: Context): Float =
    context.getSharedPreferences(NOTE_TEXT_SCALE_PREFS, Context.MODE_PRIVATE)
        .getFloat(NOTE_TEXT_SCALE_KEY, 1f)
        .coerceIn(NOTE_TEXT_SCALE_MIN, NOTE_TEXT_SCALE_MAX)

fun saveNoteTextScale(context: Context, scale: Float) {
    context.getSharedPreferences(NOTE_TEXT_SCALE_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putFloat(NOTE_TEXT_SCALE_KEY, scale.coerceIn(NOTE_TEXT_SCALE_MIN, NOTE_TEXT_SCALE_MAX))
        .apply()
}

/**
 * Pinch with two fingers to change the text size: spread to make it bigger, pinch in to make it
 * smaller. [onZoom] gets the change of each step (above 1 is bigger). One finger is left alone, so
 * scrolling, tapping and selecting text work as before. While two fingers are down the touch is
 * used up here, so the list or text box underneath does not scroll at the same time.
 */
fun Modifier.pinchToZoomText(onZoom: (Float) -> Unit): Modifier = composed {
    val latestOnZoom = rememberUpdatedState(onZoom)
    Modifier.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.size > 1) {
                    val zoom = event.calculateZoom()
                    if (zoom != 1f) {
                        latestOnZoom.value(zoom)
                    }
                    event.changes.forEach { it.consume() }
                }
            } while (event.changes.any { it.pressed })
        }
    }
}

/** Makes every sp text size inside [content] [scale] times as big. dp sizes do not change. */
@Composable
fun WithNoteTextScale(scale: Float, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale * scale)
    ) {
        content()
    }
}
