package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.util.NoteShare
import kotlinx.coroutines.launch

/**
 * Full-screen viewer for Markdown files opened via 'Open with' or Share intent.
 * Renders a read-only preview and offers a 'Save to Notes' action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkdownFileViewer(
    state: MarkdownViewerState,
    onClose: () -> Unit,
    onSaveToNotes: suspend (displayName: String, text: String) -> Long,
    onOpenSavedNote: (noteId: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        onClose()
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }
    var isSaved by remember { mutableStateOf(false) }

    val barTitle = when (state) {
        is MarkdownViewerState.Content -> {
            val name = state.displayName
            if (name.contains('.')) name.substringBeforeLast('.') else name
        }
        is MarkdownViewerState.Loading -> "Opening file..."
        is MarkdownViewerState.Error -> "Unable to open"
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = barTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (state is MarkdownViewerState.Content) {
                        TextButton(
                            onClick = {
                                if (!isSaving && !isSaved) {
                                    isSaving = true
                                    coroutineScope.launch {
                                        try {
                                            val newId = onSaveToNotes(state.displayName, state.text)
                                            if (newId > 0L) {
                                                isSaved = true
                                                val result = snackbarHostState.showSnackbar(
                                                    message = "Saved to Notes",
                                                    actionLabel = "Open",
                                                    duration = SnackbarDuration.Short
                                                )
                                                if (result == SnackbarResult.ActionPerformed) {
                                                    onOpenSavedNote(newId)
                                                }
                                            } else {
                                                snackbarHostState.showSnackbar("Could not read that file")
                                            }
                                        } finally {
                                            isSaving = false
                                        }
                                    }
                                }
                            },
                            enabled = !isSaving && !isSaved
                        ) {
                            Text(
                                text = when {
                                    isSaved -> "Saved"
                                    isSaving -> "Saving..."
                                    else -> "Save to Notes"
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (state) {
                is MarkdownViewerState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is MarkdownViewerState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onClose) {
                            Text("Close")
                        }
                    }
                }
                is MarkdownViewerState.Content -> {
                    val (parsedTitle, parsedBody) = remember(state.displayName, state.text) {
                        NoteShare.noteFromImportedText(state.displayName, state.text)
                    }

                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (parsedTitle.isNotBlank()) {
                            Text(
                                text = parsedTitle,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }

                        MarkdownPreview(
                            markdown = parsedBody,
                            onToggleChecklist = {},
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }
                }
            }
        }
    }
}

