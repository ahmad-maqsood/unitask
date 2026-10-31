package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import com.example.reminder.ClassReminderScheduler
import com.example.util.WeekParity
import com.example.util.WeekType
import com.example.util.buildTimetableIcs
import com.example.util.cleanClassroom
import com.example.util.weekTypeOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.Course
import com.example.data.local.LectureSlot
import com.example.data.local.LectureSlotWithCourse
import com.example.data.local.TaskWithCourse
import java.util.Calendar
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
@Composable
fun TimetableTab(
    allSlots: List<LectureSlotWithCourse>,
    allTasks: List<TaskWithCourse>,
    courses: List<Course>,
    onAddSlotClick: () -> Unit,
    onSelectedDayChange: (Int) -> Unit = {},
    onEditSlot: (LectureSlot) -> Unit,
    onDeleteSlot: (LectureSlot) -> Unit,
    onTaskClick: (TaskWithCourse) -> Unit,
    onRescheduleAllReminders: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Current system day of week (1 = Mon ... 7 = Sun)
    fun currentDayOfWeek(): Int {
        val cal = Calendar.getInstance()
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }
    }

    var todayDayOfWeek by remember { mutableIntStateOf(currentDayOfWeek()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000L)
            val updated = currentDayOfWeek()
            if (updated != todayDayOfWeek) {
                todayDayOfWeek = updated
            }
        }
    }

    var selectedDay by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(todayDayOfWeek) }
    val currentParity = remember { WeekParity.currentWeekType(context) }
    val defaultWeekFilter = when (currentParity) {
        WeekType.A -> "Week A"
        WeekType.B -> "Week B"
        else -> "All"
    }
    var selectedWeekFilter by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(defaultWeekFilter) }

    val hasAlternatingSlots = remember(allSlots) {
        allSlots.any { weekTypeOf(it.slot) != WeekType.ALL }
    }
    var isCardDismissed by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var currentAnchor by remember { mutableStateOf(WeekParity.getWeekAAnchor(context)) }
    
    // Tell the rest of the app which day is showing, so "Add class" starts on that day.
    androidx.compose.runtime.LaunchedEffect(selectedDay) { onSelectedDayChange(selectedDay) }

    // Slots for the selected day and week, sorted chronologically
    val daySlots = remember(allSlots, selectedDay, selectedWeekFilter) {
        allSlots.filter { it.slot.dayOfWeek == selectedDay }
            .filter { slotWithCourse ->
                val type = weekTypeOf(slotWithCourse.slot)
                when (selectedWeekFilter) {
                    "Week A" -> type == WeekType.ALL || type == WeekType.A
                    "Week B" -> type == WeekType.ALL || type == WeekType.B
                    else -> true
                }
            }
            .sortedWith(compareBy({ it.slot.startHour }, { it.slot.startMinute }))
    }

    val dayLabel by remember(selectedDay, todayDayOfWeek) {
        androidx.compose.runtime.derivedStateOf {
            DaysOfWeekLabels.find { it.first == selectedDay }?.second?.let {
                if (selectedDay == todayDayOfWeek) "$it (Today)" else it
            } ?: "Schedule"
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (hasAlternatingSlots && currentAnchor == null && !isCardDismissed) {
            item(key = "week_parity_prompt_card") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Which week is this week?",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            IconButton(
                                onClick = { isCardDismissed = true },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Set your schedule parity so bi-weekly classes and reminders appear on the right weeks.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    WeekParity.setThisWeekType(context, WeekType.A)
                                    currentAnchor = WeekParity.getWeekAAnchor(context)
                                    selectedWeekFilter = "Week A"
                                    onRescheduleAllReminders()
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            ClassReminderScheduler(context).rescheduleAll(com.example.data.local.AppDatabase.getDatabase(context))
                                        } catch (_: Exception) {}
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Week A")
                            }
                            Button(
                                onClick = {
                                    WeekParity.setThisWeekType(context, WeekType.B)
                                    currentAnchor = WeekParity.getWeekAAnchor(context)
                                    selectedWeekFilter = "Week B"
                                    onRescheduleAllReminders()
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            ClassReminderScheduler(context).rescheduleAll(com.example.data.local.AppDatabase.getDatabase(context))
                                        } catch (_: Exception) {}
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Week B")
                            }
                        }
                    }
                }
            }
        }

        // Day of Week Selector Bar
        item(key = "day_selector_bar") {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Text(
                            text = "Weekly Lecture Schedule",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = dayLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { exportTimetableToIcs(context, allSlots) },
                            modifier = Modifier.testTag("timetable_export_ics_button")
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Export schedule (.ics)",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        OutlinedButton(
                            onClick = onAddSlotClick,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("timetable_add_slot_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(4.dp))
                            Text("Add Class", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(DaysOfWeekLabels, key = { it.first }) { (dayInt, label) ->
                        val isSelected = selectedDay == dayInt
                        val isToday = dayInt == todayDayOfWeek

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = when {
                                isSelected -> MaterialTheme.colorScheme.primary
                                isToday -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            contentColor = when {
                                isSelected -> MaterialTheme.colorScheme.onPrimary
                                isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { selectedDay = dayInt }
                                .testTag("timetable_day_tab_$dayInt")
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium
                                )
                                if (isToday) {
                                    Text(
                                        text = "Today",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.85f
                                    )
                                }
                            }
                        }
                    }
                }

                // Bi-weekly schedule filter (All, Week A, Week B)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("All", "Week A", "Week B").forEach { w ->
                        FilterChip(
                            selected = selectedWeekFilter == w,
                            onClick = { selectedWeekFilter = w },
                            label = { Text(if (w == "All") "All Weeks" else w, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Slots or Empty state
        if (daySlots.isEmpty()) {
            item(key = "empty_timetable_card") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp)
                        .testTag("empty_timetable_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(60.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        val dayLabel = DaysOfWeekLabels.find { it.first == selectedDay }?.second ?: "this day"
                        Text(
                            text = "No classes scheduled for $dayLabel",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Enjoy your free time or add lectures to keep track of your weekly schedule.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = onAddSlotClick,
                            modifier = Modifier.testTag("timetable_empty_add_button")
                        ) {
                            Text("+ Add Class for $dayLabel")
                        }
                    }
                }

            }
        } else {
            daySlots.forEachIndexed { index, slotWithCourse ->
                item(key = "slot_${slotWithCourse.slot.id}") {
                    TimetableSlotCard(
                        slotWithCourse = slotWithCourse,
                        allTasks = allTasks,
                        onEditClick = { onEditSlot(slotWithCourse.slot) },
                        onDeleteClick = { onDeleteSlot(slotWithCourse.slot) },
                        onTaskClick = onTaskClick,
                        modifier = Modifier.animateItem()
                    )
                }

                if (index < daySlots.lastIndex) {
                    val nextSlot = daySlots[index + 1].slot
                    val currentEnd = slotWithCourse.slot.endHour * 60 + slotWithCourse.slot.endMinute
                    val nextStart = nextSlot.startHour * 60 + nextSlot.startMinute
                    val gap = nextStart - currentEnd
                    if (gap >= 15) {
                        item(key = "break_${slotWithCourse.slot.id}_${nextSlot.id}") {
                            TimetableBreakIndicator(gapMinutes = gap)
                        }
                    }
                }

            }
        }
    }
}

@Composable
private fun TimetableBreakIndicator(gapMinutes: Int) {
    val durationText = if (gapMinutes >= 60) {
        val h = gapMinutes / 60
        val m = gapMinutes % 60
        if (m > 0) "${h}h ${m}m free" else "${h}h free"
    } else {
        "${gapMinutes}m free"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(text = "☕", style = MaterialTheme.typography.labelSmall)
                Text(
                    text = "$durationText until next class",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

private fun exportTimetableToIcs(context: Context, slots: List<LectureSlotWithCourse>) {
    if (slots.isEmpty()) {
        Toast.makeText(context, "No classes to export", Toast.LENGTH_SHORT).show()
        return
    }

    val anchorMillis = WeekParity.getWeekAAnchor(context)
    val hasAlternating = slots.any { weekTypeOf(it.slot) != WeekType.ALL }
    if (hasAlternating && anchorMillis == null) {
        Toast.makeText(
            context,
            "Week A anchor is not set, so alternating classes may be offset by a week",
            Toast.LENGTH_LONG
        ).show()
    }

    val icsContent = buildTimetableIcs(slots, anchorMillis)

    val file = java.io.File(context.cacheDir, "unitask_timetable.ics")
    val uri = try {
        file.writeText(icsContent, Charsets.UTF_8)
        androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    } catch (_: Exception) {
        null
    }

    if (uri != null) {
        // Primary action: Intent.ACTION_VIEW with setDataAndType(uri, "text/calendar")
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "text/calendar")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(viewIntent)
            return
        } catch (_: ActivityNotFoundException) {
            // Fallback: ACTION_SEND chooser with type "text/calendar"
            try {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/calendar"
                    clipData = ClipData.newRawUri("", uri)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(sendIntent, "Export Timetable"))
                return
            } catch (_: Exception) {
                // Fall through to last fallback
            }
        }
    }

    // Last fallback: plain-text share with a Toast explaining that no calendar app could open the file.
    Toast.makeText(context, "No calendar app could open the file", Toast.LENGTH_LONG).show()
    try {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, icsContent)
        }
        context.startActivity(Intent.createChooser(sendIntent, "Export Timetable"))
    } catch (_: Exception) {
        // Nothing more we can do
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimetableSlotCard(
    slotWithCourse: LectureSlotWithCourse,
    allTasks: List<TaskWithCourse>,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onTaskClick: (TaskWithCourse) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val slot = slotWithCourse.slot
    val course = slotWithCourse.course
    var showMenu by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }

    val courseColor = remember(course?.colorHex) {
        try {
            Color(android.graphics.Color.parseColor(course?.colorHex ?: "#4F46E5"))
        } catch (_: Exception) {
            Color(0xFF4F46E5)
        }
    }

    val pendingCourseTasks = remember(allTasks, slot.courseId) {
        allTasks.filter { it.task.courseId == slot.courseId && it.task.status != com.example.data.local.TaskStatus.COMPLETED }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("timetable_slot_${slot.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
               Row(
            modifier = Modifier
                .fillMaxWidth()
                // Left colour bar, as tall as the card whatever it contains.
                .drawBehind {
                    drawRect(color = courseColor, size = Size(6.dp.toPx(), size.height))
                }
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 20.dp, top = 14.dp, end = 14.dp, bottom = 14.dp)
            ) {
                // Time Range and Dropdown Menu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = courseColor.copy(alpha = 0.15f),
                            contentColor = courseColor
                        ) {
                            Text(
                                text = course?.code ?: "Class",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.size(4.dp))
                            Text(
                                text = com.example.util.TimeFormatter.formatTimeRange(
                                    slot.startHour, slot.startMinute,
                                    slot.endHour, slot.endMinute
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Slot actions")
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit Class Slot") },
                                onClick = {
                                    showMenu = false
                                    onEditClick()
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Slot", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    onDeleteClick()
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Course Name
                Text(
                    text = course?.name ?: "Lecture",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Classroom and Instructor
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val classroom = slot.classroom.ifBlank { course?.location ?: "" }
                    if (classroom.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = classroom,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    val instructor = slot.instructor.ifBlank { course?.instructor ?: "" }
                    if (instructor.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = instructor,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Expandable Section: Pending Assignments
                if (pendingCourseTasks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { isExpanded = !isExpanded }
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Assignment, contentDescription = null, tint = courseColor, modifier = Modifier.size(14.dp))
                                    Text(
                                        text = "${pendingCourseTasks.size} pending assignment${if (pendingCourseTasks.size > 1) "s" else ""}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            AnimatedVisibility(visible = isExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    pendingCourseTasks.forEach { t ->
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { onTaskClick(t) }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = t.task.title,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 1
                                                )
                                                PriorityBadge(priority = t.task.priority)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }


    }
}

        }
    }

