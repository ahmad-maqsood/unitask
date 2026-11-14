package com.example.ui.screens

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable

/** "Discard your changes?" question, shown when a full-screen add or edit screen is closed with unsaved input. */
@Composable
fun DiscardChangesDialog(
    onKeepEditing: () -> Unit,
    onDiscard: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onKeepEditing,
        title = { Text("Discard changes?") },
        text = { Text("What you entered has not been saved yet.") },
        confirmButton = {
            TextButton(onClick = onDiscard) {
                Text("Discard", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onKeepEditing) { Text("Keep editing") }
        }
    )
}

/**
 * Remembers what an add or edit screen looked like when it opened, and asks before closing it
 * if the input has changed since. Save does not go through this: it closes the screen itself.
 *
 * How to use it in a screen:
 *  1. Build [snapshot] from every field the person can change (one string, see [changeSnapshot]).
 *  2. Call `guard.request()` from the close button and from `onDismissRequest` (the back gesture).
 *  3. Call `guard.Render()` once so the question can appear.
 *
 * Both the opening snapshot and the "asking" flag survive rotation.
 */
class DiscardGuard internal constructor(
    private val startSnapshot: String,
    private val asking: MutableState<Boolean>
) {
    internal var snapshot: String = startSnapshot
    internal var onClose: () -> Unit = {}

    /** Closes right away when nothing changed, otherwise asks first. */
    fun request() {
        if (snapshot == startSnapshot) onClose() else asking.value = true
    }

    @Composable
    fun Render() {
        if (asking.value) {
            DiscardChangesDialog(
                onKeepEditing = { asking.value = false },
                onDiscard = {
                    asking.value = false
                    onClose()
                }
            )
        }
    }
}

@Composable
fun rememberDiscardGuard(snapshot: String, onClose: () -> Unit): DiscardGuard {
    val start = rememberSaveable { snapshot }
    val asking = rememberSaveable { mutableStateOf(false) }
    val guard = remember(start, asking) { DiscardGuard(start, asking) }
    guard.snapshot = snapshot
    guard.onClose = onClose
    return guard
}

/** Joins the values of every editable field into one string, so two snapshots can be compared. */
fun changeSnapshot(vararg fields: Any?): String = fields.joinToString("\u001F") { it.toString() }
