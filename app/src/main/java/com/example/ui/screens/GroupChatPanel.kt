package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.derivedStateOf
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Spacer
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.data.groups.GroupMessage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The Chat tab of a group: the message list and the box to type in.
 * Swipe a message to reply to it.
 * Long-press a message to delete it (your own, or any message if you are the owner or an admin).
 */
@Composable
fun GroupChatPanel(
    messages: List<GroupMessage>,
    currentUid: String,
    canModerate: Boolean,
    onSend: (String, GroupMessage?) -> Unit,
    onDelete: (GroupMessage) -> Unit,
    lastReadAtOpenMillis: Long? = null,
    onMarkRead: (Long) -> Unit = {},
    onLoadEarlier: (() -> Unit)? = null
) {
    var draft by rememberSaveable { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    var replyingTo by remember { mutableStateOf<GroupMessage?>(null) }
    var messageToDelete by remember { mutableStateOf<GroupMessage?>(null) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

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

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = isSearchActive || searchQuery.isNotEmpty()) {
        searchQuery = ""
        isSearchActive = false
    }

    val filteredMessages = remember(messages, searchQuery, isSearchActive) {
        if (isSearchActive && searchQuery.isNotBlank()) {
            messages.filter {
                it.text.contains(searchQuery, ignoreCase = true) ||
                it.senderName.contains(searchQuery, ignoreCase = true)
            }
        } else {
            messages
        }
    }

    // The list is drawn upside down (newest message at index 0, at the bottom). This keeps the newest
    // messages visible when the keyboard opens and the list gets shorter.
    val newestFirst = remember(filteredMessages) { filteredMessages.distinctBy { it.id }.reversed() }

    val topVisibleDateText by remember {
        derivedStateOf {
            val visible = listState.layoutInfo.visibleItemsInfo
            if (visible.isEmpty()) null
            else {
                val topIndex = visible.lastOrNull()?.index ?: 0
                newestFirst.getOrNull(topIndex)?.createdAtMillis?.let { formatDayLabel(it) }
            }
        }
    }

    // A new message scrolls the list down only if you sent it, or you were already at (or next to)
    // the bottom. If you are reading older messages, the list stays where it is.
    // When new messages arrive the list keeps the message you were looking at in place, which pushes
    // the new ones just below the screen. So we count how many arrived and allow for them when we
    // check "were you at the bottom". scrollToItem is used instead of animateScrollToItem because
    // it always lands on the newest message, even when the list thinks it is already at item 0.
    var lastSeenNewestId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(messages.lastOrNull()?.id) {
        val newest = messages.lastOrNull() ?: return@LaunchedEffect
        val previousNewestId = lastSeenNewestId
        lastSeenNewestId = newest.id
        val addedCount = if (previousNewestId == null) {
            0
        } else {
            val previousPos = messages.indexOfLast { it.id == previousNewestId }
            if (previousPos < 0) 0 else messages.size - 1 - previousPos
        }
        val nearBottom = listState.firstVisibleItemIndex <= addedCount + 1
        if (newest.senderId == currentUid || nearBottom) {
            listState.scrollToItem(0)
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
        if (messages.size >= 5 || isSearchActive) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search messages",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.foundation.text.BasicTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            isSearchActive = it.isNotBlank()
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search messages...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                            innerTextField()
                        }
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                searchQuery = ""
                                isSearchActive = false
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
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
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
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
                                onLongPress = { messageToDelete = message },
                                onSwipeReply = { replyingTo = message },
                                onQuoteClick = { targetId ->
                                    val targetIndex = newestFirst.indexOfFirst { it.id == targetId }
                                    if (targetIndex >= 0) {
                                        coroutineScope.launch {
                                            listState.animateScrollToItem(targetIndex)
                                        }
                                    } else {
                                        android.widget.Toast.makeText(
                                            context,
                                            "Original message is outside recent history",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                        }
                    }

                    if (onLoadEarlier != null && newestFirst.size >= 50) {
                        item(key = "load_earlier_messages_btn") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                TextButton(
                                    onClick = onLoadEarlier,
                                    modifier = Modifier.testTag("load_earlier_messages_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Load earlier messages",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                }

                // Sticky floating date header pill
                androidx.compose.animation.AnimatedVisibility(
                    visible = topVisibleDateText != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                        shadowElevation = 3.dp,
                        tonalElevation = 3.dp
                    ) {
                        Text(
                            text = topVisibleDateText ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        Surface(tonalElevation = 3.dp) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (replyingTo != null) {
                    val target = replyingTo!!
                    val replyName = if (target.senderId == currentUid) "You" else target.senderName
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, top = 8.dp, end = 8.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(1.5.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Replying to $replyName",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = target.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(
                            onClick = { replyingTo = null },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel reply",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

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
                        supportingText = if (draft.length >= 400) {
                            {
                                Text(
                                    text = "${draft.length}/500",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (draft.length >= 490) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else null,
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
                                onSend(text, replyingTo)
                                draft = ""
                                replyingTo = null
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatBubble(
    message: GroupMessage,
    isMine: Boolean,
    canDelete: Boolean,
    onLongPress: () -> Unit,
    onSwipeReply: () -> Unit,
    onQuoteClick: ((String) -> Unit)? = null
) {
    val density = LocalDensity.current
    val maxDragPx = with(density) { 72.dp.toPx() }
    val triggerPx = with(density) { 56.dp.toPx() }
    val offsetX = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

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
            Box(
                contentAlignment = Alignment.CenterStart
            ) {
                // Reply arrow icon revealed when dragging to the right
                val dragProgress = (offsetX.value / triggerPx).coerceIn(0f, 1f)
                if (dragProgress > 0.05f) {
                    Box(
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .alpha(dragProgress)
                            .scale(0.6f + 0.4f * dragProgress)
                            .size(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Reply,
                            contentDescription = "Reply",
                            tint = if (offsetX.value >= triggerPx) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

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
                        .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                        .pointerInput(message.id) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    if (offsetX.value >= triggerPx) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onSwipeReply()
                                    }
                                    coroutineScope.launch {
                                        offsetX.animateTo(0f)
                                    }
                                },
                                onDragCancel = {
                                    coroutineScope.launch {
                                        offsetX.animateTo(0f)
                                    }
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    val newOffset = (offsetX.value + dragAmount).coerceIn(0f, maxDragPx)
                                    if (dragAmount > 0 || offsetX.value > 0f) {
                                        change.consume()
                                        coroutineScope.launch {
                                            offsetX.snapTo(newOffset)
                                        }
                                    }
                                }
                            )
                        }
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

                        // Quote block inside bubble when replying to a message
                        if (!message.replyToText.isNullOrBlank()) {
                            val quoteBgColor = if (isMine) {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.08f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
                            }
                            val quoteAuthor = if (message.replyToName.isNullOrBlank()) "Student" else message.replyToName

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = quoteBgColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp)
                                    .then(
                                        if (onQuoteClick != null && !message.replyToId.isNullOrBlank()) {
                                            Modifier.clickable { onQuoteClick(message.replyToId) }
                                        } else Modifier
                                    )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .height(32.dp)
                                            .clip(RoundedCornerShape(1.5.dp))
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = quoteAuthor,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = message.replyToText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
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

private fun formatDayLabel(millis: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    val today = Calendar.getInstance()
    return when {
        isSameDay(millis, today.timeInMillis) -> "Today"
        today.get(Calendar.DAY_OF_YEAR) - cal.get(Calendar.DAY_OF_YEAR) == 1 &&
            today.get(Calendar.YEAR) == cal.get(Calendar.YEAR) -> "Yesterday"
        else -> SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(millis))
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