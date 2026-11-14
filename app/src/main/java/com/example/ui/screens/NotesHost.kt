package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
    // A new number every time the editor opens, so each open gets a fresh editor state
    var editorSession by rememberSaveable { mutableIntStateOf(0) }
    // When a new note is started inside a folder, it is filed into that folder
    var newNoteFolderId by rememberSaveable { mutableStateOf<Long?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    com.example.ui.RegisterMessageHost(snackbarHostState)
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context.applicationContext)
            NoteImages.cleanupOrphans(context.applicationContext, db.noteDao())
        }
    }

    BackHandler(enabled = editorTarget == null) {
        onClose()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (editorTarget != null) {
            val targetId = if (editorTarget == "new") null else editorTarget?.toLongOrNull()
            NoteEditorScreen(
                noteId = targetId,
                viewModelSession = editorSession,
                newNoteFolderId = newNoteFolderId,
                onClose = { editorTarget = null },
                onDeleted = { deletedId ->
                    editorTarget = null
                    coroutineScope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = "Note moved to Recently deleted",
                            actionLabel = "Undo",
                            duration = SnackbarDuration.Short
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.restoreFromTrash(deletedId)
                        }
                    }
                }
            )
            // The notes list draws this snackbar, but it is not on screen while the editor is open.
            // Show it here too, so messages from the editor ("Saved to your device") are not hidden.
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                    // Keeps it above the formatting toolbar.
                    .padding(bottom = 56.dp)
            )
        } else {
            NotesScreen(
                onOpenNote = { id ->
                    editorSession = NoteEditorSessions.next()
                    newNoteFolderId = null
                    editorTarget = id.toString()
                },
                onNewNote = {
                    editorSession = NoteEditorSessions.next()
                    newNoteFolderId = (viewModel.folderFilter.value as? NotesFolderFilter.Folder)?.folderId
                    editorTarget = "new"
                },
                onClose = onClose,
                viewModel = viewModel,
                snackbarHostState = snackbarHostState
            )
        }
    }
}
