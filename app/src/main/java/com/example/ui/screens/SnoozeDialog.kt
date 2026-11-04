package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.groups.GroupTaskEntry
import com.example.data.local.TaskWithCourse
import com.example.data.local.deadlineMillis
import com.example.reminder.SnoozeStore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** One time the user can snooze to. */
data class SnoozeChoice(val label: String, val atMillis: Long)

/**
 * The snooze times to offer. A time after the deadline is hidden (a reminder after the due time is
 * pointless), unless the task is already overdue, when every time is offered.
 */
fun snoozeChoices(now: Long, deadlineMillis: Long): List<SnoozeChoice> {
    val tomorrowMorning = Calendar.getInstance().apply {
        timeInMillis = now
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 8)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val all = listOf(
        SnoozeChoice("30 minutes", now + 30 * 60_000L),
        SnoozeChoice("1 hour", now + 60 * 60_000L),
        SnoozeChoice("3 hours", now + 3 * 60 * 60_000L),
        SnoozeChoice("Tomorrow, 8:00 AM", tomorrowMorning)
    )
    return if (deadlineMillis > now) all.filter { it.atMillis < deadlineMillis } else all
}

/** "Today, 6:30 PM", "Tomorrow, 8:00 AM" or "Nov 7, 8:00 AM". */
fun formatSnoozeTime(millis: Long): String {
    val target = Calendar.getInstance().apply { timeInMillis = millis }
    val today = Calendar.getInstance()
    val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
    fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(target.time)
    return when {
        sameDay(target, today) -> "Today, $time"
        sameDay(target, tomorrow) -> "Tomorrow, $time"
        else -> "${SimpleDateFormat("MMM d", Locale.getDefault()).format(target.time)}, $time"
    }
}

private fun buildSnoozeEntry(
    taskWithCourse: TaskWithCourse,
    groupEntry: GroupTaskEntry?,
    until: Long
): SnoozeStore.Entry {
    val task = taskWithCourse.task
    // A full date, not "Today": the reminder may ring days after it was made.
    val dueInfo = "Due " + SimpleDateFormat("EEE, MMM d 'at' h:mm a", Locale.getDefault())
        .format(Date(task.deadlineMillis))
    return SnoozeStore.Entry(
        key = task.id,
        until = until,
        taskId = if (groupEntry == null && task.id > 0L) task.id else 0L,
        title = task.title,
        courseCode = taskWithCourse.course?.code ?: groupEntry?.groupName.orEmpty(),
        dueInfo = dueInfo,
        priority = task.priority.name,
        groupId = groupEntry?.task?.groupId.orEmpty(),
        groupTaskId = groupEntry?.task?.id.orEmpty()
    )
}

/** Dialog opened from the task card menu. [currentUntil] is set when the task is already snoozed. */
@Composable
fun SnoozeDialog(
    taskWithCourse: TaskWithCourse,
    groupEntry: GroupTaskEntry?,
    currentUntil: Long?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val task = taskWithCourse.task
    val choices = snoozeChoices(System.currentTimeMillis(), task.deadlineMillis)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Snooze reminder") },
        text = {
            Column {
                Text(
                    text = if (currentUntil != null) {
                        "Snoozed until ${formatSnoozeTime(currentUntil)}. Pick a new time or clear it."
                    } else {
                        "Get one extra reminder for this task later. Your other reminders still ring."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (choices.isEmpty()) {
                    Text(
                        text = "The deadline is too close to snooze.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                choices.forEach { choice ->
                    TextButton(
                        onClick = {
                            SnoozeStore.set(context, buildSnoozeEntry(taskWithCourse, groupEntry, choice.atMillis))
                            Toast.makeText(
                                context,
                                "Snoozed until ${formatSnoozeTime(choice.atMillis)}",
                                Toast.LENGTH_SHORT
                            ).show()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(choice.label, modifier = Modifier.fillMaxWidth())
                    }
                }
                if (currentUntil != null) {
                    TextButton(
                        onClick = {
                            SnoozeStore.clear(context, task.id, cancelAlarm = true)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Clear snooze",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
