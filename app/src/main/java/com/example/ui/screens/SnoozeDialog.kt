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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.groups.GroupTaskEntry
import com.example.data.local.TaskWithCourse
import com.example.data.local.deadlineMillis
import com.example.reminder.SnoozeStore
import com.example.util.DateFormats
import com.example.util.DueText
import java.util.Calendar

/** One time the user can snooze to. */
data class SnoozeChoice(
    val label: String,
    val atMillis: Long,
    /** String resource for [label] in the app language, with [labelArg] filled in when it has a placeholder. */
    @androidx.annotation.StringRes val labelRes: Int = 0,
    val labelArg: String? = null
) {
    fun localizedLabel(context: android.content.Context): String = when {
        labelRes == 0 -> label
        labelArg != null -> context.getString(labelRes, labelArg)
        else -> context.getString(labelRes)
    }
}

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
        SnoozeChoice("30 minutes", now + 30 * 60_000L, R.string.snooze_choice_30_minutes),
        SnoozeChoice("1 hour", now + 60 * 60_000L, R.string.snooze_choice_1_hour),
        SnoozeChoice("3 hours", now + 3 * 60 * 60_000L, R.string.snooze_choice_3_hours),
        SnoozeChoice(
            "Tomorrow, ${com.example.util.TimeFormatter.format(8, 0)}",
            tomorrowMorning,
            R.string.snooze_time_tomorrow,
            com.example.util.TimeFormatter.format(8, 0)
        )
    )
    return if (deadlineMillis > now) all.filter { it.atMillis < deadlineMillis } else all
}

/** "Today, 6:30 PM", "Tomorrow, 8:00 AM" or "Nov 7, 8:00 AM". */
fun formatSnoozeTime(context: android.content.Context, millis: Long): String {
    val target = Calendar.getInstance().apply { timeInMillis = millis }
    val today = Calendar.getInstance()
    val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
    fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    val time = DateFormats.clock(millis)
    return when {
        sameDay(target, today) -> context.getString(R.string.snooze_time_today, time)
        sameDay(target, tomorrow) -> context.getString(R.string.snooze_time_tomorrow, time)
        else -> context.getString(R.string.snooze_time_date, DateFormats.monthDay(millis), time)
    }
}

private fun buildSnoozeEntry(
    context: android.content.Context,
    taskWithCourse: TaskWithCourse,
    groupEntry: GroupTaskEntry?,
    until: Long
): SnoozeStore.Entry {
    val task = taskWithCourse.task
    // A full date, not "Today": the reminder may ring days after it was made.
    val dueInfo = DueText.dueAt(context, task.deadlineMillis)
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
        title = { Text(stringResource(R.string.snooze_snooze_reminder)) },
        text = {
            Column {
                Text(
                    text = if (currentUntil != null) {
                        stringResource(R.string.snooze_dialog_current, formatSnoozeTime(context, currentUntil))
                    } else {
                        stringResource(R.string.snooze_dialog_hint)
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (choices.isEmpty()) {
                    Text(
                        text = stringResource(R.string.snooze_the_deadline_is_too_close),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                choices.forEach { choice ->
                    TextButton(
                        onClick = {
                            SnoozeStore.set(context, buildSnoozeEntry(context, taskWithCourse, groupEntry, choice.atMillis))
                            Toast.makeText(
                                context,
                                context.getString(R.string.task_card_snoozed_until, formatSnoozeTime(context, choice.atMillis)),
                                Toast.LENGTH_SHORT
                            ).show()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(choice.localizedLabel(context), modifier = Modifier.fillMaxWidth())
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
                            text = stringResource(R.string.snooze_clear_snooze),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}
