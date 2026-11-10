package com.example.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * The two haptic feels used across the app. This Compose version only offers two types, so:
 * [tick] is a light tap for toggles and checkboxes, [firm] is a stronger buzz for a swipe
 * that completes an action. Both follow the phone's own touch feedback setting.
 */
class AppHaptics(private val haptic: HapticFeedback) {
    fun tick() = haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

    fun firm() = haptic.performHapticFeedback(HapticFeedbackType.LongPress)
}

@Composable
fun rememberAppHaptics(): AppHaptics {
    val haptic = LocalHapticFeedback.current
    return remember(haptic) { AppHaptics(haptic) }
}
