package com.example.ui.screens

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/**
 * Single-line text that shrinks font size down to [minFontSize] before falling back
 * to ellipsis, keeping short navigation and top-bar labels readable on narrow screens.
 */
@Composable
fun ShrinkToFitText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    fontWeight: FontWeight? = null,
    color: Color = Color.Unspecified,
    maxLines: Int = 1,
    minFontSize: TextUnit = 10.sp,
    textAlign: TextAlign? = null
) {
    // With an enlarged system font, text used to be cut with an ellipsis ("Deadlin…"). Now it
    // shrinks too, but the smallest size is lowered in step with the font scale, so the text on
    // screen is never smaller than it would be at a 1.15x font scale.
    val fontScale = LocalDensity.current.fontScale
    val floorSp = if (fontScale > 1.15f) minFontSize.value * 1.15f / fontScale else minFontSize.value
    val initialSize = remember(style.fontSize) {
        if (style.fontSize.isSpecified && style.fontSize.isSp) style.fontSize else 11.sp
    }
    var fontSize by remember(text, initialSize, fontWeight, floorSp) {
        mutableStateOf(initialSize)
    }

    Text(
        text = text,
        modifier = modifier,
        style = style.copy(fontSize = fontSize),
        fontWeight = fontWeight,
        color = color,
        maxLines = maxLines,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
        onTextLayout = { result ->
            // With TextOverflow.Ellipsis the layout cuts the text itself and does not report
            // didOverflowWidth, which left labels like "Medi…" and "Deadlin…" at full size.
            // So the cut-off lines are checked too.
            val clipped = result.didOverflowWidth ||
                (0 until result.lineCount).any { result.isLineEllipsized(it) }
            if (clipped) {
                val nextValue = fontSize.value - 0.5f
                if (nextValue >= floorSp) {
                    fontSize = nextValue.sp
                }
            }
        }
    )
}
