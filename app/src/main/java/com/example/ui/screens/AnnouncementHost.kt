package com.example.ui.screens

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.util.AppNotification
import com.example.util.NotificationHistory
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Everything the pop-up and the bell share:
 *  - [items]:          the Announcements tab (active, not removed, newest first)
 *  - [notifications]:  the Notifications tab (what the app told you, newest first)
 *  - [unread]:         both unread counts added together (the number on the bell)
 *  - [popup]:          the announcements to show in the pop-up right now (empty = no pop-up)
 */
@Stable
class AnnouncementsState(context: Context) {
    private val appContext: Context = context.applicationContext
    private var busy = false

    var items by mutableStateOf<List<Announcement>>(Announcements.bellItems(appContext))
        private set

    var notifications by mutableStateOf<List<AppNotification>>(NotificationHistory.items(appContext))
        private set

    var unreadAnnouncements by mutableIntStateOf(Announcements.unseen(appContext).size)
        private set

    var unreadNotifications by mutableIntStateOf(NotificationHistory.unreadCount(appContext))
        private set

    /** The number on the bell. */
    val unread: Int get() = unreadAnnouncements + unreadNotifications

    var popup by mutableStateOf<List<Announcement>>(emptyList())
        private set

    /** Reads the saved lists again (cheap, no network). */
    fun reload() {
        items = Announcements.bellItems(appContext)
        notifications = NotificationHistory.items(appContext)
        unreadAnnouncements = Announcements.unseen(appContext).size
        unreadNotifications = NotificationHistory.unreadCount(appContext)
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

    /** Looking at the Notifications tab counts as reading everything in it. */
    fun markNotificationsRead() {
        NotificationHistory.markAllRead(appContext)
        reload()
    }

    /** Looking at the Announcements tab counts as reading everything in it. */
    fun markAnnouncementsRead() {
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

    fun removeNotification(item: AppNotification) {
        NotificationHistory.remove(appContext, item.id)
        reload()
    }

    fun clearNotifications() {
        NotificationHistory.clear(appContext)
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

private const val TAB_NOTIFICATIONS = 0
private const val TAB_ANNOUNCEMENTS = 1

/**
 * The bell in the top bar, next to the Settings gear. Opens a sheet with two tabs:
 * Notifications (what the app told you) and Announcements (messages from UniTask).
 * [onOpenTarget] is called with a notification's target when the person taps it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnouncementBell(
    state: AnnouncementsState,
    openRequest: Boolean = false,
    onOpenRequestHandled: () -> Unit = {},
    onOpenTarget: (String) -> Unit = {}
) {
    var showSheet by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(TAB_NOTIFICATIONS) }
    // Notifications that were unread when the bell was opened, so they can be marked "New".
    var newIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    IconButton(
        onClick = {
            state.reload()
            newIds = state.notifications.filter { it.unread }.map { it.id }.toSet()
            // A new announcement is rare and important, so it gets the first look.
            selectedTab = if (state.unreadAnnouncements > 0) TAB_ANNOUNCEMENTS else TAB_NOTIFICATIONS
            showSheet = true
        },
        modifier = Modifier.testTag("top_bar_announcements_button")
    ) {
        BadgedBox(
            badge = {
                if (state.unread > 0) {
                    val hasAnnouncement = state.unreadAnnouncements > 0
                    Badge(
                        containerColor = if (hasAnnouncement) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        contentColor = if (hasAnnouncement) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
                    ) { Text(state.unread.toString()) }
                }
            }
        ) {
            Icon(Icons.Default.Notifications, contentDescription = "Notifications and announcements")
        }
    }

    // Whatever tab is on screen counts as read.
    LaunchedEffect(showSheet, selectedTab) {
        if (showSheet) {
            if (selectedTab == TAB_NOTIFICATIONS) state.markNotificationsRead() else state.markAnnouncementsRead()
        }
    }

    // Opened from outside (the daily summary notification): show the Notifications tab once.
    LaunchedEffect(openRequest) {
        if (openRequest) {
            state.reload()
            newIds = state.notifications.filter { it.unread }.map { it.id }.toSet()
            selectedTab = TAB_NOTIFICATIONS
            showSheet = true
            onOpenRequestHandled()
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
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == TAB_NOTIFICATIONS,
                        onClick = { selectedTab = TAB_NOTIFICATIONS },
                        modifier = Modifier.testTag("bell_tab_notifications"),
                        text = { TabLabel("Notifications", state.unreadNotifications) }
                    )
                    Tab(
                        selected = selectedTab == TAB_ANNOUNCEMENTS,
                        onClick = { selectedTab = TAB_ANNOUNCEMENTS },
                        modifier = Modifier.testTag("bell_tab_announcements"),
                        text = { TabLabel("Announcements", state.unreadAnnouncements, urgent = true) }
                    )
                }

                if (selectedTab == TAB_NOTIFICATIONS) {
                    if (state.notifications.isEmpty()) {
                        EmptyText("No notifications yet. Reminders and updates from UniTask will be kept here for 30 days.")
                    } else {
                        ClearAllRow { state.clearNotifications() }
                        Column(
                            modifier = Modifier
                                .heightIn(max = 480.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            state.notifications.forEach { item ->
                                NotificationRow(
                                    item = item,
                                    isNew = item.id in newIds,
                                    onOpen = {
                                        showSheet = false
                                        onOpenTarget(item.target)
                                    },
                                    onRemove = { state.removeNotification(item) }
                                )
                            }
                        }
                    }
                } else {
                    if (state.items.isEmpty()) {
                        EmptyText("No announcements right now.")
                    } else {
                        ClearAllRow { state.removeAll() }
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
}

@Composable
private fun TabLabel(text: String, unread: Int, urgent: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, maxLines = 1)
        if (unread > 0) {
            Badge(
                containerColor = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                contentColor = if (urgent) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(start = 6.dp)
            ) { Text(unread.toString()) }
        }
    }
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 12.dp)
    )
}

@Composable
private fun ClearAllRow(onClear: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        TextButton(onClick = onClear) { Text("Clear all") }
    }
}

/** One saved notification. Full text, so a long daily summary is easy to read. Tap to open the screen it is about. */
@Composable
private fun NotificationRow(
    item: AppNotification,
    isNew: Boolean,
    onOpen: () -> Unit,
    onRemove: () -> Unit
) {
    val whenText = remember(item.id) {
        DateUtils.getRelativeTimeSpanString(
            item.timeMillis,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        ).toString()
    }
    val isSummary = item.kind == "Daily summary"
    val container = if (isSummary) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val content = if (isSummary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val tappable = item.target.isNotBlank()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = tappable, onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                if (item.body.isNotBlank()) {
                    Text(item.body, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    buildString {
                        if (isNew) append("New • ")
                        append(item.kind)
                        append(" • ")
                        append(whenText)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isNew) FontWeight.Bold else FontWeight.Normal,
                    color = if (isNew) MaterialTheme.colorScheme.primary else content.copy(alpha = 0.7f)
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "Remove notification")
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
