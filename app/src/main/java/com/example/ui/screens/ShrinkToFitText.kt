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
    // If the user has enlarged the system font, respect it and use an ellipsis instead of shrinking.
    val allowShrink = LocalDensity.current.fontScale <= 1.15f
    val initialSize = remember(style.fontSize) {
        if (style.fontSize.isSpecified && style.fontSize.isSp) style.fontSize else 11.sp
    }
    var fontSize by remember(text, initialSize, fontWeight) {
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
            if (allowShrink && result.didOverflowWidth) {
                val nextValue = fontSize.value - 0.5f
                if (nextValue >= minFontSize.value) {
                    fontSize = nextValue.sp
                }
            }
        }
    )
}
