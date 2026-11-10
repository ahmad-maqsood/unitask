package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

/** Parses a course colour such as "#4F46E5". Returns null when it is missing or not a valid colour. */
fun parseCourseColorOrNull(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    return try {
        Color(android.graphics.Color.parseColor(hex.trim()))
    } catch (_: IllegalArgumentException) {
        null
    }
}

/**
 * The colour to draw for a course. When the stored colour is missing or invalid it falls back to the
 * current theme's primary colour, so the Ocean, Forest and Rose themes no longer show indigo.
 * This is the one place to change the fallback for every screen.
 */
@Composable
fun rememberCourseColor(hex: String?): Color {
    val fallback = MaterialTheme.colorScheme.primary
    return remember(hex, fallback) { parseCourseColorOrNull(hex) ?: fallback }
}
