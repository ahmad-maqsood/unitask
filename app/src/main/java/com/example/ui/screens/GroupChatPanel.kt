package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.groups.GroupMessage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * The Chat tab of a group: the message list and the box to type in.
 * Long-press a message to delete it (your own, or any message if you are the owner or an admin).
 */
@Composable
fun GroupChatPanel(
    messages: List<GroupMessage>,
    currentUid: String,
    canModerate: Boolean,
    onSend: (String) -> Unit,
    onDelete: (GroupMessage) -> Unit,
    lastReadAtOpenMillis: Long? = null,
    onMarkRead: (Long) -> Unit = {}
) {
    var draft by rememberSaveable { mutableStateOf("") }
    var messageToDelete by remember { mutableStateOf<GroupMessage?>(null) }
    val listState = rememberLazyListState()

    // Decide the divider once when messages is non-empty AND lastReadAtOpenMillis != null, then freeze it.
    var frozenDividerId by remember { mutableStateOf<String?>(null) }
    var dividerEvaluated by remember { mutableStateOf(false) }

    if (!dividerEvaluated && messages.isNotEmpty() && lastReadAtOpenMillis != null) {
        frozenDividerId = messages.firstOrNull {
            it.senderId != currentUid && it.createdAtMillis > lastReadAtOpenMillis
        }?.id
        dividerEvaluated = true
    }

    // Mark as read while the panel is on screen and the app is in the foreground (RESUMED).
    // Must wait until the divider has been decided.
    val lifecycleOwner = LocalLifecycleOwner.current
    var isResumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            isResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val newestMessageMillis = messages.lastOrNull()?.createdAtMillis
    LaunchedEffect(newestMessageMillis, isResumed, dividerEvaluated) {
        if (dividerEvaluated && isResumed) {
            if (newestMessageMillis != null && newestMessageMillis > 0L) {
                onMarkRead(newestMessageMillis)
            }
        }
    }

    // The list is drawn upside down (newest message at index 0, at the bottom). This keeps the newest
    // messages visible when the keyboard opens and the list gets shorter.
    val newestFirst = remember(messages) { messages.reversed() }

    // A new message scrolls the list down only if you sent it, or you were already at (or next to)
    // the bottom. If you are reading older messages, the list stays where it is.
    LaunchedEffect(messages.lastOrNull()?.id) {
        val newest = messages.lastOrNull() ?: return@LaunchedEffect
        val nearBottom = listState.firstVisibleItemIndex <= 1
        if (newest.senderId == currentUid || nearBottom) {
            listState.animateScrollToItem(0)
        }
    }

    messageToDelete?.let { message ->
        ConfirmDeleteDialog(
            title = "Delete message?",
            message = if (message.senderId == currentUid) {
                "This message will be deleted for everyone in the group."
            } else {
                "${message.senderName}'s message will be deleted for everyone in the group."
            },
            onConfirm = { onDelete(message) },
            onDismiss = { messageToDelete = null }
        )
    }

    // Lifts the typing box above the keyboard. The navigation bar height is subtracted because the
    // screen around this already leaves room for it.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars))
    ) {
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No messages yet. Say hello to your group!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("group_chat_list"),
                reverseLayout = true,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(newestFirst, key = { _, message -> message.id }) { index, message ->
                    // The list is newest first, so the older neighbour is the next index.
                    val older = newestFirst.getOrNull(index + 1)
                    val isMine = message.senderId == currentUid
                    Column {
                        if (older == null || !isSameDay(older.createdAtMillis, message.createdAtMillis)) {
                            ChatDayLabel(message.createdAtMillis)
                        }
                        if (message.id == frozenDividerId) {
                            NewMessagesDivider()
                        }
                        ChatBubble(
                            message = message,
                            isMine = isMine,
                            canDelete = isMine || canModerate,
                            onLongPress = { messageToDelete = message }
                        )
                    }
                }
            }
        }

        Surface(tonalElevation = 3.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(500) },
                    placeholder = { Text("Message") },
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("group_chat_input")
                )
                IconButton(
                    onClick = {
                        val text = draft.trim()
                        if (text.isNotEmpty()) {
                            onSend(text)
                            draft = ""
                        }
                    },
                    enabled = draft.isNotBlank(),
                    modifier = Modifier.testTag("group_chat_send")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (draft.isNotBlank()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatBubble(
    message: GroupMessage,
    isMine: Boolean,
    canDelete: Boolean,
    onLongPress: () -> Unit
) {
    val timeText = remember(message.createdAtMillis) {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(message.createdAtMillis))
    }
    // A bubble is at most 80% of the available width, so it fits small phones and tablets alike.
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
    val maxBubbleWidth = maxWidth * 0.8f
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isMine) 16.dp else 4.dp,
                bottomEnd = if (isMine) 4.dp else 16.dp
            ),
            color = if (isMine) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier
                .widthIn(max = maxBubbleWidth)
                .combinedClickable(
                    onClick = {},
                    onLongClick = { if (canDelete) onLongPress() }
                )
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (!isMine) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
    }
}

@Composable
private fun ChatDayLabel(millis: Long) {
    val label = remember(millis) {
        SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(millis))
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun isSameDay(a: Long, b: Long): Boolean {
    val first = Calendar.getInstance().apply { timeInMillis = a }
    val second = Calendar.getInstance().apply { timeInMillis = b }
    return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
        first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
}

@Composable
private fun NewMessagesDivider() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
            thickness = 1.dp
        )
        Text(
            text = "New messages",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
            thickness = 1.dp
        )
    }
}