package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

@Composable
fun NotesHost(
    onClose: () -> Unit,
    viewModel: NotesViewModel = viewModel()
) {
    var editorTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    BackHandler(enabled = editorTarget == null) {
        onClose()
    }

    if (editorTarget == null) {
        NotesScreen(
            onOpenNote = { id -> editorTarget = id.toString() },
            onNewNote = { editorTarget = "new" },
            onClose = onClose,
            viewModel = viewModel,
            snackbarHostState = snackbarHostState
        )
    } else {
        val targetId = if (editorTarget == "new") null else editorTarget?.toLongOrNull()
        NoteEditorScreen(
            noteId = targetId,
            onClose = { editorTarget = null },
            onDeleted = { deletedId ->
                editorTarget = null
                coroutineScope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = "Note deleted",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.restoreFromTrash(deletedId)
                    }
                }
            }
        )
    }
}
