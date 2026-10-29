package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ReminderOption

/** Short names for the chips. The caption above the grid says "before the deadline". */
private fun ReminderOption.chipLabel(): String = when (this) {
    ReminderOption.AT_DEADLINE -> "At deadline"
    ReminderOption.ONE_HOUR_BEFORE -> "1 hour"
    ReminderOption.TWO_HOURS_BEFORE -> "2 hours"
    ReminderOption.TWELVE_HOURS_BEFORE -> "12 hours"
    ReminderOption.ONE_DAY_BEFORE -> "1 day"
    ReminderOption.TWO_DAYS_BEFORE -> "2 days"
}

/**
 * The reminder picker used by every task dialog: a fixed grid of two equal columns.
 * Every chip always has the same width and shows an icon (+ when off, a tick when on),
 * so tapping a chip never changes its size and nothing jumps to the next line.
 *
 * [testTagPrefix] keeps the old test tags, for example "reminder_chip" gives "reminder_chip_AT_DEADLINE".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderChipGrid(
    selected: Set<ReminderOption>,
    onSelectedChange: (Set<ReminderOption>) -> Unit,
    testTagPrefix: String,
    modifier: Modifier = Modifier
) {
    val rows = remember { ReminderOption.entries.sortedBy { it.offsetMinutes }.chunked(2) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Alert me before the deadline:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { rowOptions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowOptions.forEach { opt ->
                        val isOn = opt in selected
                        FilterChip(
                            selected = isOn,
                            onClick = {
                                onSelectedChange(if (isOn) selected - opt else selected + opt)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isOn) Icons.Default.Check else Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = opt.chipLabel(),
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .testTag("${testTagPrefix}_${opt.name}")
                        )
                    }
                    // Keeps the last chip the same width if the number of options is ever odd.
                    if (rowOptions.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}