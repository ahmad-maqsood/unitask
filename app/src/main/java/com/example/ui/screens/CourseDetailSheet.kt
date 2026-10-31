package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Schedule
import com.example.data.local.Course
import com.example.data.local.CourseWithTaskCount
import com.example.data.local.LectureSlot
import com.example.data.local.LectureSlotWithCourse
import com.example.data.local.TaskItem
import com.example.data.local.TaskWithCourse

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailSheet(
    courseWithCount: CourseWithTaskCount,
    tasks: List<TaskWithCourse>,
    slots: List<LectureSlotWithCourse> = emptyList(),
    onBack: () -> Unit,
    onEditCourse: (Course) -> Unit,
    onDeleteCourse: (Course) -> Unit,
    onAddTask: (courseId: Long) -> Unit,
    onAddSlot: (courseId: Long) -> Unit = {},
    onEditSlot: (LectureSlot) -> Unit = {},
    onDeleteSlot: (LectureSlot) -> Unit = {},
    onStatusCycle: (TaskItem) -> Unit,
    onEditTask: (TaskItem) -> Unit,
    onDeleteTask: (TaskItem) -> Unit,
    modifier: Modifier = Modifier,
    onChecklistToggle: ((TaskItem, Int) -> Unit)? = null
) {
    BackHandler { onBack() }

    val course = courseWithCount.course
    val courseColor = remember(course.colorHex) {
        try {
            Color(android.graphics.Color.parseColor(course.colorHex))
        } catch (_: Exception) {
            Color(0xFF4F46E5)
        }
    }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    if (showDeleteConfirm) {
        ConfirmDeleteDialog(
            title = "Delete course?",
            message = "${course.code} and all of its assignments, class slots and attached files will be deleted.",
            onConfirm = { onDeleteCourse(course) },
            onDismiss = { showDeleteConfirm = false }
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(course.code, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("course_detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to courses"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onEditCourse(course) },
                        modifier = Modifier.testTag("course_detail_edit_button")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Course")
                    }
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.testTag("course_detail_delete_button")
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Delete Course",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Course Header Card
            item(key = "course_hero_card") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Colored Header
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(courseColor)
                                .padding(20.dp)
                        ) {
                            Column {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.White.copy(alpha = 0.25f)
                                ) {
                                    Text(
                                        text = course.code,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = course.name,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        // Details & Progress
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp)
                        ) {
                            if (course.instructor.isNotBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = course.instructor,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (course.location.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Place,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = course.location,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Stats row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${courseWithCount.pendingTasksCount} pending tasks",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (courseWithCount.pendingTasksCount > 0) courseColor else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${courseWithCount.completedTasksCount}/${courseWithCount.totalTasksCount} done (${(courseWithCount.completionPercentage * 100).toInt()}%)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { courseWithCount.completionPercentage },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = courseColor,
                                trackColor = courseColor.copy(alpha = 0.2f),
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Add assignment button
                            Button(
                                onClick = { onAddTask(course.id) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("course_detail_add_task_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = courseColor)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.size(8.dp))
                                Text("Add Assignment to ${course.code}")
                            }
                        }
                    }
                }
            }

            // Lecture Schedule Section
            item(key = "course_schedule_section") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = courseColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Class Schedule (${slots.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            TextButton(
                                onClick = { onAddSlot(course.id) },
                                modifier = Modifier.testTag("course_detail_add_slot_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.size(4.dp))
                                Text("+ Add Slot", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        if (slots.isEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "No weekly lecture hours set for this course yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Spacer(modifier = Modifier.height(10.dp))
                            val sortedSlots = remember(slots) {
                                slots.sortedWith(
                                    compareBy<LectureSlotWithCourse> { it.slot.dayOfWeek }
                                        .thenBy { it.slot.startHour }
                                        .thenBy { it.slot.startMinute }
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                sortedSlots.forEach { slotWithCourse ->
                                    val slot = slotWithCourse.slot
                                    val dayName = when (slot.dayOfWeek) {
                                        1 -> "Mon"
                                        2 -> "Tue"
                                        3 -> "Wed"
                                        4 -> "Thu"
                                        5 -> "Fri"
                                        6 -> "Sat"
                                        7 -> "Sun"
                                        else -> "Day"
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = courseColor.copy(alpha = 0.2f),
                                                    contentColor = courseColor
                                                ) {
                                                    Text(
                                                        text = dayName,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }

                                                Column(modifier = Modifier.weight(1f, fill = false)) {
                                                    Text(
                                                        text = com.example.util.TimeFormatter.formatTimeRange(
                                                            slot.startHour, slot.startMinute,
                                                            slot.endHour, slot.endMinute
                                                        ),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    val loc = slot.classroom.ifBlank { course.location }
                                                    if (loc.isNotBlank()) {
                                                        Text(
                                                            text = loc,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }

                                            Row {
                                                IconButton(
                                                    onClick = { onEditSlot(slot) },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Edit,
                                                        contentDescription = "Edit slot",
                                                        modifier = Modifier.size(16.dp),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { onDeleteSlot(slot) },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.DeleteOutline,
                                                        contentDescription = "Delete slot",
                                                        modifier = Modifier.size(16.dp),
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
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

            // Section Header
            item(key = "assignments_header") {
                Text(
                    text = "Assignments (${tasks.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // Course tasks list
            if (tasks.isEmpty()) {
                item(key = "empty_assignments_card") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "No assignments for ${course.code} yet!",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap the button above to add homework, lab reports, or exams.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(tasks, key = { it.task.id }) { taskWithCourse ->
                    TaskCard(
                        taskWithCourse = taskWithCourse,
                        onStatusCycle = onStatusCycle,
                        onEditClick = onEditTask,
                        onDeleteClick = onDeleteTask,
                        onChecklistToggle = onChecklistToggle
                    )
                }
            }
        }
    }
}
