package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.util.GetStartedStep

/** One line of the card: a normal step, or the phone settings joined into one line. */
private data class GetStartedRow(
    val tag: String,
    val title: String,
    val hint: String,
    val isDone: Boolean,
    /** The step to start when this line is tapped (for the settings line, the next setting still off). */
    val target: GetStartedStep
)

private fun buildRows(steps: List<GetStartedStep>, done: Set<GetStartedStep>): List<GetStartedRow> {
    val rows = steps.filter { !it.isSystem }.map { step ->
        GetStartedRow(
            tag = "get_started_step_${step.name}",
            title = step.title,
            hint = step.hint,
            isDone = step in done,
            target = step
        )
    }.toMutableList()

    // Notifications, battery and auto-start are shown as one "Make reminders reliable" line at the
    // bottom. Tapping it walks through the settings one at a time.
    val system = steps.filter { it.isSystem }
    if (system.isNotEmpty()) {
        val left = system.filter { it !in done }
        rows += GetStartedRow(
            tag = "get_started_step_SYSTEM",
            title = "Make reminders reliable",
            hint = if (left.isEmpty()) "All set" else "${system.size - left.size} of ${system.size} phone settings on",
            isDone = left.isEmpty(),
            target = left.firstOrNull() ?: system.first()
        )
    }
    return rows
}

/**
 * The "Get started" card at the top of Deadlines. It starts small: a progress line and just the next
 * thing to do. Tap the arrow to see every step. Steps tick themselves.
 */
@Composable
fun GetStartedCard(
    done: Set<GetStartedStep>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    steps: List<GetStartedStep> = GetStartedStep.entries,
    onStepClick: ((GetStartedStep) -> Unit)? = null
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val rows = buildRows(steps, done)
    val doneCount = rows.count { it.isDone }
    val visibleRows = if (expanded) rows else rows.filter { !it.isDone }.take(1)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("get_started_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        )
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { expanded = !expanded }
                        .testTag("get_started_toggle")
                ) {
                    Text(
                        text = "Get started",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$doneCount of ${rows.size} done",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Show fewer steps" else "Show all steps",
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("get_started_dismiss")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss Get started",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { if (rows.isEmpty()) 0f else doneCount / rows.size.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 12.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )
            Spacer(modifier = Modifier.height(12.dp))
            Column(
                modifier = Modifier.padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                visibleRows.forEach { row ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .then(
                                if (!row.isDone && onStepClick != null) Modifier.clickable { onStepClick(row.target) } else Modifier
                            )
                            .testTag(row.tag)
                    ) {
                        Icon(
                            imageVector = if (row.isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = if (row.isDone) "Done" else "Not done yet",
                            tint = if (row.isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = row.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                textDecoration = if (row.isDone) TextDecoration.LineThrough else null,
                                color = if (row.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                            )
                            if (!row.isDone) {
                                Text(
                                    text = if (onStepClick != null && !row.target.isSystem) "Tap to start" else row.hint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
