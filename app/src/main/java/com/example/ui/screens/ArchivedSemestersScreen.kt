package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.ArchivedSemesterSummary

/**
 * Full screen presenting archived semesters with options to restore or delete forever.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedSemestersScreen(
    semesters: List<ArchivedSemesterSummary>,
    onRestoreSemester: (String) -> Unit,
    onDeleteSemesterForever: (String) -> Unit,
    onClose: () -> Unit
) {
    var semesterToDelete by remember { mutableStateOf<ArchivedSemesterSummary?>(null) }

    BackHandler(enabled = true) {
        onClose()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("archived_semesters_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Archived semesters",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        if (semesters.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Archive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No archived semesters yet.",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(semesters, key = { it.name }) { semester ->
                    ArchivedSemesterCard(
                        semester = semester,
                        onRestore = { onRestoreSemester(semester.name) },
                        onDeleteForever = { semesterToDelete = semester }
                    )
                }
            }
        }
    }

    semesterToDelete?.let { target ->
        val confirmMsg = deleteSemesterConfirmationMessage(
            semesterName = target.name,
            courseCount = target.courseCount,
            taskCount = target.taskCount,
            classCount = target.classCount,
            noteCount = target.noteCount
        )
        AlertDialog(
            onDismissRequest = { semesterToDelete = null },
            title = {
                Text(
                    text = "Delete ${target.name} forever?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(confirmMsg)
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = target.name
                        semesterToDelete = null
                        onDeleteSemesterForever(name)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Delete forever")
                }
            },
            dismissButton = {
                TextButton(onClick = { semesterToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ArchivedSemesterCard(
    semester: ArchivedSemesterSummary,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Semester Name
            Text(
                text = semester.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Counts of courses, tasks, classes, notes
            val countsText = formatSemesterCounts(
                courseCount = semester.courseCount,
                taskCount = semester.taskCount,
                classCount = semester.classCount,
                noteCount = semester.noteCount
            )
            Text(
                text = countsText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Course Codes
            if (semester.courseCodes.isNotEmpty()) {
                val codesText = semester.courseCodes.joinToString(", ")
                Text(
                    text = codesText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Action buttons in a Column so they fit narrow screens safely
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = onRestore,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("archived_restore_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Restore")
                }
                TextButton(
                    onClick = onDeleteForever,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("archived_delete_button"),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete forever")
                }
            }
        }
    }
}
