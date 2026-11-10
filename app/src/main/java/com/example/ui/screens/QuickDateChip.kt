package com.example.ui.screens

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import java.util.Calendar

/**
 * A small shortcut chip for the date and time rows of the task dialogs ("Tomorrow", "5:00 PM").
 * It has an outline so it looks tappable, and it lights up when it matches the value that is
 * currently chosen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuickDateChip(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text = label, style = MaterialTheme.typography.labelMedium, maxLines = 1) },
        modifier = modifier
    )
}

/** True when [millis] falls on the day that is [days] days after today (0 = today). */
internal fun isDaysFromToday(millis: Long?, days: Int): Boolean {
    if (millis == null) return false
    val target = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, days) }
    val chosen = Calendar.getInstance().apply { timeInMillis = millis }
    return chosen.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
        chosen.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
}
