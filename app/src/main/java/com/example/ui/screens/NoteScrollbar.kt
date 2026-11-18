package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A slim scrollbar for notes. It stays hidden, fades in while the content scrolls, fades out a
 * moment later, and can be dragged while it is showing. It only draws when the content is longer
 * than the screen, and it takes no touches while hidden.
 *
 * [thumbFraction] and [progress] are lambdas on purpose: the scroll position changes on every
 * frame while scrolling, so it is only read while drawing and not while building the screen.
 */
@Composable
private fun ScrollbarThumb(
    thumbFraction: () -> Float,
    progress: () -> Float,
    scrolling: Boolean,
    onDragToProgress: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var recentlyScrolled by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(scrolling) {
        if (scrolling) {
            recentlyScrolled = true
        } else {
            delay(1200)
            recentlyScrolled = false
        }
    }

    val currentFraction by rememberUpdatedState(thumbFraction)
    val currentProgress by rememberUpdatedState(progress)
    val currentOnDrag by rememberUpdatedState(onDragToProgress)
    // Only rebuilds when the answer changes (shown or hidden), not for every scroll position.
    val shouldShow by remember {
        derivedStateOf { (recentlyScrolled || dragging) && currentFraction() < 0.98f }
    }
    val alpha by animateFloatAsState(
        targetValue = if (shouldShow) 1f else 0f,
        animationSpec = tween(durationMillis = if (shouldShow) 120 else 400),
        label = "noteScrollbarAlpha"
    )
    val thumbColor = MaterialTheme.colorScheme.onSurface

    if (alpha <= 0.01f) return

    // Never smaller than this, so it stays easy to see and to grab on very long notes
    val minThumb = 36.dp

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(24.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        val track = size.height.toFloat()
                        val thumb = maxOf(track * currentFraction(), minThumb.toPx()).coerceAtMost(track)
                        val range = (track - thumb).coerceAtLeast(1f)
                        currentOnDrag(((change.position.y - thumb / 2f) / range).coerceIn(0f, 1f))
                    }
                )
            }
            .drawBehind {
                val track = size.height
                val thumb = maxOf(track * currentFraction(), minThumb.toPx()).coerceAtMost(track)
                val top = currentProgress().coerceIn(0f, 1f) * (track - thumb)
                val width = (if (dragging) 6.dp else 4.dp).toPx()
                val strength = if (dragging) 0.55f else 0.32f
                drawRoundRect(
                    color = thumbColor.copy(alpha = strength * alpha),
                    topLeft = Offset(size.width - width - 3.dp.toPx(), top),
                    size = Size(width, thumb),
                    cornerRadius = CornerRadius(width / 2f, width / 2f)
                )
            }
    )
}

/** Scrollbar for a plain scrolling column. [viewportPx] is the visible height in pixels. */
@Composable
fun BoxScope.ScrollStateScrollbar(state: ScrollState, viewportPx: Float) {
    ScrollbarThumb(
        thumbFraction = {
            val max = state.maxValue
            if (max <= 0 || viewportPx <= 0f) 1f else viewportPx / (viewportPx + max)
        },
        progress = {
            val max = state.maxValue
            if (max <= 0) 0f else state.value.toFloat() / max
        },
        scrolling = state.isScrollInProgress,
        onDragToProgress = { target ->
            state.dispatchRawDelta(target * state.maxValue - state.value)
        },
        modifier = Modifier.align(Alignment.CenterEnd)
    )
}

/**
 * Scrollbar for a lazy list whose items have different heights. The length of the whole list is
 * estimated from the items on screen, so the thumb can shift a little while scrolling.
 */
@Composable
fun BoxScope.LazyListScrollbar(state: LazyListState) {
    val scope = rememberCoroutineScope()

    // Pair(thumbFraction, progress) or null when there is nothing to measure yet. It is only read
    // by the lambdas below (while drawing), so scrolling does not rebuild this function.
    val metrics = remember(state) {
        derivedStateOf {
            val info = state.layoutInfo
            val visible = info.visibleItemsInfo
            if (visible.isEmpty() || info.totalItemsCount == 0) {
                null
            } else {
                val average = visible.sumOf { it.size }.toFloat() / visible.size
                val viewport = info.viewportSize.height.toFloat()
                val total = average * info.totalItemsCount
                val offset = state.firstVisibleItemIndex * average + state.firstVisibleItemScrollOffset
                if (total <= viewport || viewport <= 0f) {
                    1f to 0f
                } else {
                    (viewport / total) to (offset / (total - viewport)).coerceIn(0f, 1f)
                }
            }
        }
    }
    // With nothing to measure the thumb counts as "whole list visible", so it stays hidden.
    ScrollbarThumb(
        thumbFraction = { metrics.value?.first ?: 1f },
        progress = { metrics.value?.second ?: 0f },
        scrolling = state.isScrollInProgress,
        onDragToProgress = { target ->
            val info = state.layoutInfo
            val visible = info.visibleItemsInfo
            if (visible.isNotEmpty()) {
                val average = visible.sumOf { it.size }.toFloat() / visible.size
                val viewport = info.viewportSize.height.toFloat()
                val wanted = target * (average * info.totalItemsCount - viewport).coerceAtLeast(0f)
                val index = (wanted / average).toInt().coerceIn(0, info.totalItemsCount - 1)
                val inside = (wanted - index * average).toInt().coerceAtLeast(0)
                scope.launch { state.scrollToItem(index, inside) }
            }
        },
        modifier = Modifier.align(Alignment.CenterEnd)
    )
}
