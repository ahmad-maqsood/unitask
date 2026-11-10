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
import androidx.compose.ui.unit.sp
import com.example.data.local.ReminderOption
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight

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
                                ShrinkToFitText(
                                    text = opt.chipLabel(),
                                    style = MaterialTheme.typography.labelMedium,
                                    minFontSize = 10.sp,
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

/** Text for the "Remind me" row: "Off", one label, or the earliest label plus how many more. */
fun reminderSummary(options: Set<ReminderOption>): String {
    if (options.isEmpty()) return "Off"
    val sorted = options.sortedBy { it.offsetMinutes }
    val first = sorted.first().label
    return if (sorted.size == 1) first else "$first +${sorted.size - 1} more"
}

/**
 * One compact row, "Remind me: 2 hours before", that opens a small picker with the full [ReminderChipGrid].
 * Used by every task dialog so they stay short. Several reminders can still be selected.
 *
 * [chipTagPrefix] is the same prefix the grid always used, so existing test tags keep working.
 * [pickerNote] is an optional extra line under the grid, for example who receives group reminders.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderPickerRow(
    selected: Set<ReminderOption>,
    onSelectedChange: (Set<ReminderOption>) -> Unit,
    chipTagPrefix: String,
    rowTestTag: String,
    clearTestTag: String,
    doneTestTag: String,
    modifier: Modifier = Modifier,
    pickerNote: String? = null
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }

    OutlinedCard(
        onClick = { showPicker = true },
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag(rowTestTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Remind me",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = reminderSummary(selected),
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected.isEmpty()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("Remind me", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    ReminderChipGrid(
                        selected = selected,
                        onSelectedChange = onSelectedChange,
                        testTagPrefix = chipTagPrefix
                    )
                    if (pickerNote != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = pickerNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showPicker = false },
                    modifier = Modifier.testTag(doneTestTag)
                ) {
                    Text("Done")
                }
            },
            dismissButton = {
                if (selected.isNotEmpty()) {
                    TextButton(
                        onClick = { onSelectedChange(emptySet()) },
                        modifier = Modifier.testTag(clearTestTag)
                    ) {
                        Text("Clear all")
                    }
                }
            }
        )
    }
}
