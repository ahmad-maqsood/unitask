package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.reminder.AnnouncementNotifier
import com.example.util.Announcement
import com.example.util.Announcements
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Everything the pop-up and the bell share:
 *  - [items]:  what the bell lists (active, not removed, newest first)
 *  - [unread]: how many of them were not shown in the pop-up yet (the badge number)
 *  - [popup]:  the messages to show in the pop-up right now (empty = no pop-up)
 */
@Stable
class AnnouncementsState(context: Context) {
    private val appContext: Context = context.applicationContext
    private var busy = false

    var items by mutableStateOf<List<Announcement>>(Announcements.bellItems(appContext))
        private set

    var unread by mutableIntStateOf(Announcements.unseen(appContext).size)
        private set

    var popup by mutableStateOf<List<Announcement>>(emptyList())
        private set

    /** Reads the saved lists again (cheap, no network). */
    fun reload() {
        items = Announcements.bellItems(appContext)
        unread = Announcements.unseen(appContext).size
    }

    /** Downloads the latest list, updates the bell, and opens the pop-up if something is new. Offline is fine. */
    suspend fun refreshFromServer() {
        if (busy) return
        busy = true
        try {
            Announcements.refresh(appContext)
            reload()
            val unseen = Announcements.unseen(appContext)
            if (unseen.isNotEmpty() && popup.isEmpty()) popup = unseen
        } finally {
            busy = false
        }
    }

    /** "Got it" on the pop-up: it will not pop up again, but stays in the bell. */
    fun dismissPopup() {
        val shown = popup
        Announcements.markSeen(appContext, shown)
        AnnouncementNotifier.cancel(appContext, shown)
        popup = emptyList()
        reload()
    }

    /** Opening the bell counts as reading everything in it. */
    fun onBellOpened() {
        reload()
        val current = items
        Announcements.markSeen(appContext, current)
        AnnouncementNotifier.cancel(appContext, current)
        reload()
    }

    fun remove(item: Announcement) = removeAll(listOf(item))

    fun removeAll(list: List<Announcement> = items) {
        Announcements.remove(appContext, list)
        AnnouncementNotifier.cancel(appContext, list)
        reload()
    }
}

/**
 * Refreshes the announcements every time the app opens or comes back to the front (and again after
 * sign-in, because some messages are for one person only). The background check does the same
 * every 15 minutes while the app is closed.
 */
@Composable
fun rememberAnnouncements(currentUserId: String?): AnnouncementsState {
    val context = LocalContext.current
    val state = remember { AnnouncementsState(context) }
    val scope = rememberCoroutineScope()

    LifecycleResumeEffect(currentUserId) {
        scope.launch { state.refreshFromServer() }
        onPauseOrDispose { }
    }
    return state
}

/** The pop-up. Shows each new message once. Nothing is drawn when there is nothing new. */
@Composable
fun AnnouncementHost(state: AnnouncementsState) {
    val shown = state.popup
    if (shown.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { state.dismissPopup() },
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
            confirmButton = { TextButton(onClick = { state.dismissPopup() }) { Text("Got it") } }
        )
    }
}

/** The bell in the top bar, next to the Settings gear. Opens a sheet with all active announcements. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnouncementBell(state: AnnouncementsState) {
    var showSheet by remember { mutableStateOf(false) }

    IconButton(
        onClick = {
            state.onBellOpened()
            showSheet = true
        },
        modifier = Modifier.testTag("top_bar_announcements_button")
    ) {
        BadgedBox(
            badge = {
                if (state.unread > 0) Badge { Text(state.unread.toString()) }
            }
        ) {
            Icon(Icons.Default.Notifications, contentDescription = "Announcements")
        }
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Announcements",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (state.items.isNotEmpty()) {
                        TextButton(onClick = { state.removeAll() }) { Text("Clear all") }
                    }
                }

                if (state.items.isEmpty()) {
                    Text(
                        "No announcements right now.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 480.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        state.items.forEach { item ->
                            AnnouncementRow(item = item, onRemove = { state.remove(item) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnnouncementRow(item: Announcement, onRemove: () -> Unit) {
    val meta = remember(item.id, item.createdAt, item.expiresAt) { announcementMeta(item) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(item.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(item.body, style = MaterialTheme.typography.bodyMedium)
                if (meta.isNotEmpty()) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "Remove announcement")
            }
        }
    }
}

private fun announcementMeta(item: Announcement): String {
    val format = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    val parts = mutableListOf<String>()
    item.createdAtMillis?.let { parts.add("Posted " + format.format(Date(it))) }
    item.expiresAtMillis?.let { parts.add("Expires " + format.format(Date(it))) }
    return parts.joinToString(" · ")
}