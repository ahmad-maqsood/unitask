package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import com.example.data.local.TaskItem
import com.example.data.local.TaskStatus
import com.example.data.local.TaskWithCourse

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeadlinesTab(
    tasks: List<TaskWithCourse>,
    courses: List<Course>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    statusFilter: TaskStatus?,
    onStatusFilterSelect: (TaskStatus?) -> Unit,
    courseFilter: Long?,
    onCourseFilterSelect: (Long?) -> Unit,
    sortOption: TaskSortOption,
    onSortOptionSelect: (TaskSortOption) -> Unit,
    pendingCount: Int,
    completedCount: Int,
    onStatusCycle: (TaskItem) -> Unit,
    onEditTask: (TaskItem) -> Unit,
    onDeleteTask: (TaskItem) -> Unit,
    onAddTaskClick: () -> Unit,
    highlightedClassInfo: List<HighlightedClassInfo> = emptyList(),
    onHighlightedClassTaskClick: ((TaskWithCourse) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    val pendingCountStr by remember(pendingCount) { derivedStateOf { pendingCount.toString() } }
    val completedCountStr by remember(completedCount) { derivedStateOf { completedCount.toString() } }
    val coursesCountStr by remember(courses.size) { derivedStateOf { courses.size.toString() } }
    val allTasksLabel by remember(tasks.size) { derivedStateOf { "All (${tasks.size})" } }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Highlighted classes for the active day. We intentionally show the full daily schedule
        // instead of a single next class and hide classes that started more than 20 minutes ago.
        if (highlightedClassInfo.isNotEmpty()) {
            item(key = "highlighted_classes_header") {
                Text(
                    text = "Class Schedule",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            item(key = "highlighted_classes_row") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(
                        items = highlightedClassInfo,
                        key = { it.slotWithCourse.slot.id }
                    ) { classInfo ->
                        CurrentNextClassCard(
                            classInfo = classInfo,
                            onTaskClick = { taskWithCourse ->
                                onHighlightedClassTaskClick?.invoke(taskWithCourse) ?: onEditTask(taskWithCourse.task)
                            },
                            modifier = Modifier.fillParentMaxWidth(0.9f)
                        )
                    }
                }
            }
        }

        // Quick Stats Summary Card
        item(key = "summary_stats_card") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("summary_stats_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatItem(
                        count = pendingCountStr,
                        label = "Pending Tasks",
                        color = MaterialTheme.colorScheme.primary
                    )
                    Box(
                        modifier = Modifier
                            .size(width = 1.dp, height = 36.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    StatItem(
                        count = completedCountStr,
                        label = "Done",
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Box(
                        modifier = Modifier
                            .size(width = 1.dp, height = 36.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    StatItem(
                        count = coursesCountStr,
                        label = "Enrolled Courses",
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        // Search Bar & Sort Button
        item(key = "search_and_sort_bar") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("task_search_input"),
                    placeholder = { Text("Search assignments or courses...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search icon"
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // Sort Dropdown Button
                Box {
                    OutlinedCard(
                        modifier = Modifier
                            .clickable { sortMenuExpanded = true }
                            .testTag("sort_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Box(
                            modifier = Modifier.padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sort,
                                contentDescription = "Sort tasks",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false }
                    ) {
                        TaskSortOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = option.label,
                                        fontWeight = if (option == sortOption) FontWeight.Bold else FontWeight.Normal,
                                        color = if (option == sortOption) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    onSortOptionSelect(option)
                                    sortMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // Status Filter Chips
        item(key = "status_filter_row") {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item(key = "status_filter_all") {
                    FilterChip(
                        selected = statusFilter == null,
                        onClick = { onStatusFilterSelect(null) },
                        label = { Text(allTasksLabel) },
                        modifier = Modifier.testTag("filter_status_all")
                    )
                }
                item(key = "status_filter_pending") {
                    FilterChip(
                        selected = statusFilter == TaskStatus.PENDING,
                        onClick = { onStatusFilterSelect(TaskStatus.PENDING) },
                        label = { Text("Pending") },
                        modifier = Modifier.testTag("filter_status_pending")
                    )
                }
                item(key = "status_filter_completed") {
                    FilterChip(
                        selected = statusFilter == TaskStatus.COMPLETED,
                        onClick = { onStatusFilterSelect(TaskStatus.COMPLETED) },
                        label = { Text("Done") },
                        modifier = Modifier.testTag("filter_status_completed")
                    )
                }
            }
        }

        // Course Filter Chips (if more than 1 course)
        if (courses.size > 1) {
            item(key = "course_filter_row") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item(key = "course_filter_all_item") {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (courseFilter == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (courseFilter == null) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onCourseFilterSelect(null) }
                                .testTag("filter_course_all")
                        ) {
                            Text(
                                text = "All Courses",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    items(courses, key = { it.id }) { course ->
                        val isSelected = courseFilter == course.id
                        val courseColor = remember(course.colorHex) {
                            try {
                                Color(android.graphics.Color.parseColor(course.colorHex))
                            } catch (_: Exception) {
                                Color(0xFF4F46E5)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) courseColor else courseColor.copy(alpha = 0.12f),
                            contentColor = if (isSelected) Color.White else courseColor,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onCourseFilterSelect(course.id) }
                                .testTag("filter_course_${course.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) Color.White else courseColor)
                                )
                                Text(
                                    text = course.code,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Tasks Header
        item(key = "tasks_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Upcoming Deadlines",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = sortOption.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Tasks List or Empty State
        if (tasks.isEmpty()) {
            item(key = "empty_tasks_card") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .testTag("empty_tasks_card"),
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
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Assignment,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = if (searchQuery.isNotEmpty() || statusFilter != null || courseFilter != null)
                                "No assignments match your filters"
                            else
                                "No assignments yet!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (searchQuery.isNotEmpty() || statusFilter != null || courseFilter != null)
                                "Try resetting search or filters to see more assignments."
                            else
                                "Stay on top of your studies by adding your first assignment.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = onAddTaskClick,
                            modifier = Modifier.testTag("empty_state_add_task_button")
                        ) {
                            Text("+ Add Assignment")
                        }
                    }
                }
            }
        } else {
            items(tasks, key = { it.task.id }) { taskWithCourse ->
                TaskCard(
                    taskWithCourse = taskWithCourse,
                    onStatusCycle = onStatusCycle,
                    onEditClick = onEditTask,
                    onDeleteClick = onDeleteTask
                )
            }
        }
    }
}

@Composable
private fun StatItem(count: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
