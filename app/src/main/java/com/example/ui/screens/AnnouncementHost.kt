package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.util.Announcement
import com.example.util.Announcements

/**
 * Checks for messages when the app opens (and again after sign-in, because some messages are
 * for one person only). Shows any new ones in a dialog. Does nothing if there are none or offline.
 */
@Composable
fun AnnouncementHost(currentUserId: String?) {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<List<Announcement>>(emptyList()) }

    LaunchedEffect(currentUserId) {
        val unseen = Announcements.fetchUnseen(context)
        if (unseen.isNotEmpty()) pending = unseen
    }

    if (pending.isNotEmpty()) {
        val shown = pending
        val dismiss = {
            Announcements.markSeen(context, shown)
            pending = emptyList()
        }
        AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(if (shown.size == 1) shown[0].title else "Messages from UniTask") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (shown.size == 1) {
                        Text(shown[0].body, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        shown.forEach { a ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(a.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text(a.body, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = dismiss) { Text("Got it") } }
        )
    }
}