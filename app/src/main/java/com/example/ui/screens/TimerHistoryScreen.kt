package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.util.timer.TimerHistoryLogic
import com.example.util.timer.TimerMode
import com.example.util.timer.TimerSession
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * Study history: a summary of today and the last week, then every saved session grouped by day.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerHistoryScreen(
    sessions: List<TimerSession>,
    onClose: () -> Unit,
    onDeleteSession: (TimerSession) -> Unit,
    onClearAll: () -> Unit,
    zone: ZoneId = ZoneId.systemDefault()
) {
    BackHandler { onClose() }

    val today = LocalDate.now(zone)
    var sessionToDelete by remember { mutableStateOf<TimerSession?>(null) }
    var showClearConfirm by rememberSaveable { mutableStateOf(false) }

    val days = remember(sessions, today) { TimerHistoryLogic.lastDays(sessions, today, zone) }
    val grouped = remember(sessions) { TimerHistoryLogic.groupByDay(sessions, zone) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("timer_history_screen"),
        topBar = {
            TopAppBar(
                title = { Text("Study history", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("timer_history_back")
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (sessions.isNotEmpty()) {
                        IconButton(
                            onClick = { showClearConfirm = true },
                            modifier = Modifier.testTag("timer_history_clear")
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear all history")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 16.dp,
                vertical = 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SummaryCard(
                    todayMillis = days.last().millis,
                    weekMillis = days.sumOf { it.millis },
                    sessionCount = sessions.size,
                    averageMillis = if (sessions.isEmpty()) 0L else TimerHistoryLogic.totalMillis(sessions) / sessions.size
                )
            }
            item {
                WeekChartCard(days = days.map { it.date to it.millis }, today = today)
            }

            if (sessions.isEmpty()) {
                item { EmptyHistory() }
            } else {
                grouped.forEach { (day, daySessions) ->
                    item(key = "header_$day") {
                        DayHeader(
                            day = day,
                            today = today,
                            totalMillis = TimerHistoryLogic.totalMillis(daySessions)
                        )
                    }
                    items(daySessions, key = { it.startedAtMillis }) { session ->
                        SessionRow(
                            session = session,
                            zone = zone,
                            onDelete = { sessionToDelete = session }
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    sessionToDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = { Text("Delete this session?") },
            text = {
                Text("${TimerHistoryLogic.formatTotal(session.durationMillis)} will be removed from your history.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteSession(session)
                        sessionToDelete = null
                    },
                    modifier = Modifier.testTag("timer_history_delete_confirm")
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) { Text("Cancel") }
            }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear all history?") },
            text = { Text("Every saved study session will be removed. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAll()
                        showClearConfirm = false
                    },
                    modifier = Modifier.testTag("timer_history_clear_confirm")
                ) { Text("Clear all") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SummaryCard(
    todayMillis: Long,
    weekMillis: Long,
    sessionCount: Int,
    averageMillis: Long
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("timer_history_summary"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Studied today",
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = if (todayMillis == 0L) "0 min" else TimerHistoryLogic.formatTotal(todayMillis),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("timer_history_today_total")
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                SummaryStat(
                    label = "Last 7 days",
                    value = if (weekMillis == 0L) "0 min" else TimerHistoryLogic.formatTotal(weekMillis),
                    modifier = Modifier.weight(1f)
                )
                SummaryStat(
                    label = "Sessions",
                    value = sessionCount.toString(),
                    modifier = Modifier.weight(1f)
                )
                SummaryStat(
                    label = "Average",
                    value = if (sessionCount == 0) "-" else TimerHistoryLogic.formatTotal(averageMillis),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1
        )
    }
}

@Composable
private fun WeekChartCard(days: List<Pair<LocalDate, Long>>, today: LocalDate) {
    val maxMillis = days.maxOfOrNull { it.second } ?: 0L
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("timer_history_chart"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Last 7 days",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                days.forEach { (date, millis) ->
                    DayBar(
                        label = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        millis = millis,
                        fraction = if (maxMillis == 0L) 0f else millis.toFloat() / maxMillis.toFloat(),
                        isToday = date == today,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DayBar(
    label: String,
    millis: Long,
    fraction: Float,
    isToday: Boolean,
    modifier: Modifier = Modifier
) {
    val animated by animateFloatAsState(targetValue = fraction, label = "dayBar")
    val barColor = when {
        millis == 0L -> MaterialTheme.colorScheme.outlineVariant
        isToday -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    }
    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = TimerHistoryLogic.formatCompact(millis),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.62f)
                    .fillMaxHeight(animated.coerceIn(0.03f, 1f))
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                    .background(barColor)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun DayHeader(day: LocalDate, today: LocalDate, totalMillis: Long) {
    val title = when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> day.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = TimerHistoryLogic.formatTotal(totalMillis),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SessionRow(session: TimerSession, zone: ZoneId, onDelete: () -> Unit) {
    val startText = remember(session.startedAtMillis) {
        java.time.Instant.ofEpochMilli(session.startedAtMillis)
            .atZone(zone)
            .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    }
    val detail = if (session.mode == TimerMode.COUNTDOWN && session.plannedMillis != null) {
        "$startText · Countdown · goal ${TimerHistoryLogic.formatTotal(session.plannedMillis)}"
    } else {
        "$startText · Stopwatch"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("timer_history_session"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (session.mode == TimerMode.COUNTDOWN) Icons.Default.HourglassBottom else Icons.Default.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = TimerHistoryLogic.formatTotal(session.durationMillis),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (session.completed) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Completed",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete session",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyHistory() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp)
            .testTag("timer_history_empty"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "No study sessions yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Run the timer for at least 10 seconds, then tap Reset or let a countdown finish. The session will show up here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}
