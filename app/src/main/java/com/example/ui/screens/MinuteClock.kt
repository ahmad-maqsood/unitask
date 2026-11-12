package com.example.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.util.minuteClock

/**
 * The current time, refreshed at the start of every minute while the screen is showing, and
 * straight away when the person comes back to the app. Reading it inside derivedStateOf lets a
 * list re-sort itself only when something actually moves to a different section.
 */
@Composable
fun rememberMinuteClock(): State<Long> {
    val clock = remember { minuteClock() }
    return clock.collectAsStateWithLifecycle(initialValue = System.currentTimeMillis())
}
