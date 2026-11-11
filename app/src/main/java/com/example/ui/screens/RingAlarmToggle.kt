package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight

/**
 * The "Ring alarm" switch used when adding or editing a task. When it is on, the task's
 * reminders ring like an alarm. The row says so, and warns when no reminder is picked,
 * because then nothing would ring.
 */
@Composable
fun RingAlarmToggleRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    hasReminders: Boolean,
    testTag: String,
    modifier: Modifier = Modifier,
    forGroup: Boolean = false
) {
    val needsReminder = checked && !hasReminders
    val description = when {
        needsReminder -> "Pick a reminder above, or the alarm will not ring."
        checked && forGroup -> "Rings like an alarm at each reminder time for everyone in the group."
        checked -> "Rings like an alarm at each reminder time until you open or dismiss it."
        else -> "Off. Reminders use the normal notification sound."
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (checked) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f) else Color.Transparent,
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Alarm,
                contentDescription = null,
                tint = if (checked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Ring alarm",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (needsReminder) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // The whole row is the switch, so the Switch itself does not need its own click.
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}
