package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.AppDatabase
import com.example.util.NoteImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun NotesHost(
    onClose: () -> Unit,
    viewModel: NotesViewModel = viewModel()
) {
    val context = LocalContext.current
    var editorTarget by rememberSaveable { mutableStateOf<String?>(null) }
    // A new number every time the editor opens, so each open gets a fresh editor ViewModel
    var editorSession by rememberSaveable { mutableIntStateOf(0) }
    var inTrash by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context.applicationContext)
            NoteImages.cleanupOrphans(context.applicationContext, db.noteDao())
        }
    }

    BackHandler(enabled = editorTarget == null && !inTrash) {
        onClose()
    }

    BackHandler(enabled = editorTarget == null && inTrash) {
        inTrash = false
    }

    if (editorTarget != null) {
        val targetId = if (editorTarget == "new") null else editorTarget?.toLongOrNull()
        NoteEditorScreen(
            noteId = targetId,
            viewModelSession = editorSession,
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
    } else if (inTrash) {
        NoteTrashScreen(
            onClose = { inTrash = false },
            viewModel = viewModel
        )
    } else {
        NotesScreen(
            onOpenNote = { id ->
                editorSession++
                editorTarget = id.toString()
            },
            onNewNote = {
                editorSession++
                editorTarget = "new"
            },
            onOpenTrash = { inTrash = true },
            onClose = onClose,
            viewModel = viewModel,
            snackbarHostState = snackbarHostState
        )
    }
}
