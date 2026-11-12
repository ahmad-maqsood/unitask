package com.example.ui.screens

import java.util.Calendar
import com.example.util.DateFormats
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.groups.GroupTaskEntry
import com.example.data.local.startOfLocalDay
import com.example.data.local.TaskItem
import com.example.data.local.TaskStatus
import com.example.data.local.TaskWithCourse
import com.example.data.local.deadlineMillis

@Composable
internal fun DeadlinesCalendarView(
    tasks: List<TaskWithCourse>,
    onStatusCycle: (TaskItem) -> Unit,
    onEditTask: (TaskItem) -> Unit,
    onDeleteTask: (TaskItem) -> Unit,
    groupEntries: Map<Long, GroupTaskEntry>,
    onSwipeToggleDone: ((TaskItem) -> Unit)? = null,
    onChecklistToggle: ((TaskItem, Int) -> Unit)? = null
) {
    val cal = remember { Calendar.getInstance() }
    var currentYear by remember { mutableIntStateOf(cal.get(Calendar.YEAR)) }
    var currentMonth by remember { mutableIntStateOf(cal.get(Calendar.MONTH)) }
    var selectedDayEpoch by remember { mutableStateOf(startOfLocalDay(System.currentTimeMillis())) }

    val displayMonth = remember(currentYear, currentMonth) {
        val c = Calendar.getInstance().apply {
            set(Calendar.YEAR, currentYear)
            set(Calendar.MONTH, currentMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        DateFormats.monthYear(c.timeInMillis)
    }

    val daysInMonth = remember(currentYear, currentMonth) {
        val c = Calendar.getInstance().apply {
            set(Calendar.YEAR, currentYear)
            set(Calendar.MONTH, currentMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val maxDays = c.getActualMaximum(Calendar.DAY_OF_MONTH)
        val firstDayOfWeek = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
        firstDayOfWeek to maxDays
    }

    val tasksByDay = remember(tasks) {
        val map = mutableMapOf<Long, MutableList<TaskWithCourse>>()
        for (item in tasks) {
                map.getOrPut(startOfLocalDay(item.task.dueDateEpochMillis)) { mutableListOf() }.add(item)
        }
        map
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = {
                    if (currentMonth == 0) {
                        currentMonth = 11
                        currentYear -= 1
                    } else {
                        currentMonth -= 1
                    }
                }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month")
                }

                Text(
                    text = displayMonth,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                IconButton(onClick = {
                    if (currentMonth == 11) {
                        currentMonth = 0
                        currentYear += 1
                    } else {
                        currentMonth += 1
                    }
                }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next month")
                }
            }

            val weekDayHeaders = listOf("M", "T", "W", "T", "F", "S", "S")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                weekDayHeaders.forEach { header ->
                    Text(
                        text = header,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(36.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))

            val (firstDayOffset, totalDays) = daysInMonth
            val totalCells = ((firstDayOffset + totalDays + 6) / 7) * 7

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (row in 0 until (totalCells / 7)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        for (col in 0..6) {
                            val cellIndex = row * 7 + col
                            val dayNumber = cellIndex - firstDayOffset + 1
                            if (dayNumber in 1..totalDays) {
                                val cellEpoch = startOfLocalDay(
                                    Calendar.getInstance().apply {
                                        set(Calendar.YEAR, currentYear)
                                        set(Calendar.MONTH, currentMonth)
                                        set(Calendar.DAY_OF_MONTH, dayNumber)
                                    }.timeInMillis
                                )
                                val isSelected = cellEpoch == selectedDayEpoch
                                val tasksOnDay = tasksByDay[cellEpoch] ?: emptyList()
                                val hasOverdue = tasksOnDay.any { it.task.deadlineMillis < System.currentTimeMillis() && it.task.status != com.example.data.local.TaskStatus.COMPLETED }

                                Surface(
                                    shape = CircleShape,
                                    color = when {
                                        isSelected -> MaterialTheme.colorScheme.primary
                                        tasksOnDay.isNotEmpty() -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                        else -> Color.Transparent
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .clickable { selectedDayEpoch = cellEpoch }
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = "$dayNumber",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = if (isSelected || tasksOnDay.isNotEmpty()) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (tasksOnDay.isNotEmpty()) {
                                            Box(
                                                modifier = Modifier
                                                    .size(4.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isSelected) Color.White else (if (hasOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary))
                                            )
                                        }
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.size(36.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    val tasksOnSelectedDay = remember(selectedDayEpoch, tasks) {
        tasksByDay[selectedDayEpoch] ?: emptyList()
    }

    Spacer(modifier = Modifier.height(10.dp))
    val selectedDateStr = remember(selectedDayEpoch) {
        DateFormats.longWeekdayMonthDay(selectedDayEpoch)
    }
    Text(
        text = "Due on $selectedDateStr (${tasksOnSelectedDay.size})",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )

    if (tasksOnSelectedDay.isEmpty()) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) {
            Text(
                text = "No deadlines due on this day.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            tasksOnSelectedDay.forEach { taskWithCourse ->
                val entry = groupEntries[taskWithCourse.task.id]
                if (onSwipeToggleDone != null) {
                    SwipeDoneTaskCard(
                        taskWithCourse = taskWithCourse,
                        onSwipeToggleDone = onSwipeToggleDone,
                        onStatusCycle = onStatusCycle,
                        onEditTask = onEditTask,
                        onDeleteTask = onDeleteTask,
                        groupEntry = entry,
                        onChecklistToggle = if (entry == null) onChecklistToggle else null
                    )
                } else {
                    TaskCard(
                        taskWithCourse = taskWithCourse,
                        onStatusCycle = onStatusCycle,
                        onEditClick = onEditTask,
                        onDeleteClick = onDeleteTask,
                        groupEntry = entry,
                        onChecklistToggle = if (entry == null) onChecklistToggle else null
                    )
                }
            }
        }
    }
}
