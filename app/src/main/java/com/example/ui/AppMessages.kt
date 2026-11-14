package com.example.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * One way to tell the person something short ("Left group", "Could not save the file").
 *
 * Messages show in the snackbar of the screen that is on top, so every screen looks and behaves the same:
 * the message stays long enough to read, a new one replaces the old one, and it can have an action button
 * such as "Undo". Call [show] from anywhere, including code that is not a screen.
 *
 * Each screen that has a snackbar adds [RegisterMessageHost]. If no screen is showing (for example the
 * app is closing), the message falls back to a plain Toast so it is never lost.
 *
 * Do NOT use this from inside a dialog or bottom sheet window: those draw above the snackbar, so the
 * message would be hidden. Dialogs keep using Toast, or their own snackbar host.
 */
object AppMessages {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val hosts = ArrayList<SnackbarHostState>()

    @Volatile
    private var appContext: Context? = null

    /** Call once when the app starts (in UniTaskApp). */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    internal fun register(host: SnackbarHostState) {
        hosts.remove(host)
        hosts.add(host)
    }

    internal fun unregister(host: SnackbarHostState) {
        hosts.remove(host)
    }

    /**
     * Shows [message]. [long] keeps it up for longer, for messages with details to read.
     * If [actionLabel] is set, [onAction] runs when the person taps it.
     */
    fun show(
        message: String,
        long: Boolean = false,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null
    ) {
        scope.launch {
            val host = hosts.lastOrNull()
            if (host == null) {
                appContext?.let {
                    Toast.makeText(it, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            // A new message replaces the one on screen instead of waiting in a queue.
            host.currentSnackbarData?.dismiss()
            val result = host.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                withDismissAction = actionLabel == null && long,
                duration = if (actionLabel != null || long) SnackbarDuration.Long else SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) onAction?.invoke()
        }
    }
}

/**
 * Put this next to a screen's SnackbarHostState so [AppMessages] can show messages there.
 * The screen that was added last (the one on top) is the one that shows them.
 */
@Composable
fun RegisterMessageHost(host: SnackbarHostState) {
    DisposableEffect(host) {
        AppMessages.register(host)
        onDispose { AppMessages.unregister(host) }
    }
}
