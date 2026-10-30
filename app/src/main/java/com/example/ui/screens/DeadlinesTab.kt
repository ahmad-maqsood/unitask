package com.example.ui.screens

import java.util.Calendar
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.groups.GroupTaskEntry
import com.example.data.local.Course
import com.example.data.local.TaskItem
import com.example.data.local.TaskStatus
import com.example.data.local.TaskWithCourse
import com.example.data.local.deadlineMillis

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeadlinesTab(
    tasks: List<TaskWithCourse>,
    courses: List<Course>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    statusFilter: TaskStatus?,
    onStatusFilterSelect: (TaskStatus?) -> Unit,
    sourceFilter: TaskSourceFilter?,
    onSourceFilterSelect: (TaskSourceFilter?) -> Unit,
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
    // Tasks that come from groups, keyed by the (negative) id they have in [tasks].
    groupEntries: Map<Long, GroupTaskEntry> = emptyMap(),
    isLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var isCalendarView by rememberSaveable { mutableStateOf(false) }
    var isBatchMode by remember { mutableStateOf(false) }
    val selectedTaskIds = remember { mutableStateListOf<Long>() }

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
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .size(width = 1.dp, height = 36.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    StatItem(
                        count = completedCountStr,
                        label = "Done",
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .size(width = 1.dp, height = 36.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    StatItem(
                        count = coursesCountStr,
                        label = "Enrolled Courses",
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
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
                    placeholder = {
                        Text(
                            text = "Search tasks or courses",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
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
                item(key = "source_filter_group") {
                    FilterChip(
                        selected = sourceFilter == TaskSourceFilter.GROUP,
                        onClick = { onSourceFilterSelect(TaskSourceFilter.GROUP) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Groups,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text("Group") },
                        modifier = Modifier.testTag("filter_source_group")
                    )
                }
                item(key = "source_filter_personal") {
                    FilterChip(
                        selected = sourceFilter == TaskSourceFilter.PERSONAL,
                        onClick = { onSourceFilterSelect(TaskSourceFilter.PERSONAL) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text("Personal") },
                        modifier = Modifier.testTag("filter_source_personal")
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
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isCalendarView) "Deadlines Calendar" else "Upcoming Deadlines",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { isCalendarView = !isCalendarView },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isCalendarView) Icons.Default.ViewList else Icons.Default.CalendarMonth,
                                contentDescription = if (isCalendarView) "Switch to List View" else "Switch to Calendar View",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        if (!isCalendarView) {
                            IconButton(
                                onClick = {
                                    isBatchMode = !isBatchMode
                                    if (!isBatchMode) selectedTaskIds.clear()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Checklist,
                                    contentDescription = "Batch select",
                                    tint = if (isBatchMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        if (!isCalendarView && !isBatchMode) {
                            Text(
                                text = sortOption.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Batch Mode Action Bar
                if (isBatchMode && !isCalendarView) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${selectedTaskIds.size} selected",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    onClick = {
                                        if (selectedTaskIds.size == tasks.size) {
                                            selectedTaskIds.clear()
                                        } else {
                                            selectedTaskIds.clear()
                                            selectedTaskIds.addAll(tasks.map { it.task.id })
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        if (selectedTaskIds.size == tasks.size) "Deselect" else "All",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }

                                TextButton(
                                    onClick = {
                                        tasks.filter { it.task.id in selectedTaskIds }.forEach {
                                            onStatusCycle(it.task)
                                        }
                                        selectedTaskIds.clear()
                                        isBatchMode = false
                                    },
                                    enabled = selectedTaskIds.isNotEmpty(),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("Done", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }

                                TextButton(
                                    onClick = {
                                        tasks.filter { it.task.id in selectedTaskIds }.forEach {
                                            onDeleteTask(it.task)
                                        }
                                        selectedTaskIds.clear()
                                        isBatchMode = false
                                    },
                                    enabled = selectedTaskIds.isNotEmpty(),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        "Delete",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        selectedTaskIds.clear()
                                        isBatchMode = false
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close batch mode", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isCalendarView) {
            item(key = "calendar_view_container") {
                DeadlinesCalendarView(
                    tasks = tasks,
                    onStatusCycle = onStatusCycle,
                    onEditTask = onEditTask,
                    onDeleteTask = onDeleteTask,
                    groupEntries = groupEntries
                )
            }
        } else if (tasks.isEmpty() && isLoading) {
            // Still reading the database: show nothing instead of the empty message.
        } else if (tasks.isEmpty()) {
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
                            text = if (searchQuery.isNotEmpty() || statusFilter != null || courseFilter != null || sourceFilter != null)
                                "No assignments match your filters"
                            else
                                "No assignments yet!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (searchQuery.isNotEmpty() || statusFilter != null || courseFilter != null || sourceFilter != null)
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
        } else if (sortOption == TaskSortOption.DUE_DATE_ASC && searchQuery.isBlank()) {
            val sections = groupDeadlines(tasks)

            if (sections.overdue.isNotEmpty()) {
                item(key = "section_header_overdue") {
                    SectionHeader(
                        title = "Overdue",
                        count = sections.overdue.size,
                        isOverdue = true
                    )
                }
                items(sections.overdue, key = { it.task.id }, contentType = { "task" }) { taskWithCourse ->
                    SelectableTaskCard(
                        taskWithCourse = taskWithCourse,
                        isBatchMode = isBatchMode,
                        isSelected = taskWithCourse.task.id in selectedTaskIds,
                        onToggleSelect = {
                            if (taskWithCourse.task.id in selectedTaskIds) selectedTaskIds.remove(taskWithCourse.task.id)
                            else selectedTaskIds.add(taskWithCourse.task.id)
                        },
                        onStatusCycle = onStatusCycle,
                        onEditTask = onEditTask,
                        onDeleteTask = onDeleteTask,
                        modifier = Modifier.animateItem(),
                        groupEntry = groupEntries[taskWithCourse.task.id]
                    )
                }
            }

            if (sections.today.isNotEmpty()) {
                item(key = "section_header_today") {
                    SectionHeader(
                        title = "Due Today",
                        count = sections.today.size
                    )
                }
                items(sections.today, key = { it.task.id }, contentType = { "task" }) { taskWithCourse ->
                    SelectableTaskCard(
                        taskWithCourse = taskWithCourse,
                        isBatchMode = isBatchMode,
                        isSelected = taskWithCourse.task.id in selectedTaskIds,
                        onToggleSelect = {
                            if (taskWithCourse.task.id in selectedTaskIds) selectedTaskIds.remove(taskWithCourse.task.id)
                            else selectedTaskIds.add(taskWithCourse.task.id)
                        },
                        onStatusCycle = onStatusCycle,
                        onEditTask = onEditTask,
                        onDeleteTask = onDeleteTask,
                        modifier = Modifier.animateItem(),
                        groupEntry = groupEntries[taskWithCourse.task.id]
                    )
                }
            }

            if (sections.tomorrow.isNotEmpty()) {
                item(key = "section_header_tomorrow") {
                    SectionHeader(
                        title = "Due Tomorrow",
                        count = sections.tomorrow.size
                    )
                }
                items(sections.tomorrow, key = { it.task.id }, contentType = { "task" }) { taskWithCourse ->
                    SelectableTaskCard(
                        taskWithCourse = taskWithCourse,
                        isBatchMode = isBatchMode,
                        isSelected = taskWithCourse.task.id in selectedTaskIds,
                        onToggleSelect = {
                            if (taskWithCourse.task.id in selectedTaskIds) selectedTaskIds.remove(taskWithCourse.task.id)
                            else selectedTaskIds.add(taskWithCourse.task.id)
                        },
                        onStatusCycle = onStatusCycle,
                        onEditTask = onEditTask,
                        onDeleteTask = onDeleteTask,
                        modifier = Modifier.animateItem(),
                        groupEntry = groupEntries[taskWithCourse.task.id]
                    )
                }
            }

            if (sections.later.isNotEmpty()) {
                item(key = "section_header_later") {
                    SectionHeader(
                        title = "Upcoming",
                        count = sections.later.size
                    )
                }
                items(sections.later, key = { it.task.id }, contentType = { "task" }) { taskWithCourse ->
                    SelectableTaskCard(
                        taskWithCourse = taskWithCourse,
                        isBatchMode = isBatchMode,
                        isSelected = taskWithCourse.task.id in selectedTaskIds,
                        onToggleSelect = {
                            if (taskWithCourse.task.id in selectedTaskIds) selectedTaskIds.remove(taskWithCourse.task.id)
                            else selectedTaskIds.add(taskWithCourse.task.id)
                        },
                        onStatusCycle = onStatusCycle,
                        onEditTask = onEditTask,
                        onDeleteTask = onDeleteTask,
                        modifier = Modifier.animateItem(),
                        groupEntry = groupEntries[taskWithCourse.task.id]
                    )
                }
            }

            if (sections.done.isNotEmpty()) {
                item(key = "section_header_done") {
                    SectionHeader(
                        title = "Completed",
                        count = sections.done.size
                    )
                }
                items(sections.done, key = { it.task.id }, contentType = { "task" }) { taskWithCourse ->
                    SelectableTaskCard(
                        taskWithCourse = taskWithCourse,
                        isBatchMode = isBatchMode,
                        isSelected = taskWithCourse.task.id in selectedTaskIds,
                        onToggleSelect = {
                            if (taskWithCourse.task.id in selectedTaskIds) selectedTaskIds.remove(taskWithCourse.task.id)
                            else selectedTaskIds.add(taskWithCourse.task.id)
                        },
                        onStatusCycle = onStatusCycle,
                        onEditTask = onEditTask,
                        onDeleteTask = onDeleteTask,
                        modifier = Modifier.animateItem(),
                        groupEntry = groupEntries[taskWithCourse.task.id]
                    )
                }
            }
        } else {
            items(tasks, key = { it.task.id }, contentType = { "task" }) { taskWithCourse ->
                SelectableTaskCard(
                    taskWithCourse = taskWithCourse,
                    isBatchMode = isBatchMode,
                    isSelected = taskWithCourse.task.id in selectedTaskIds,
                    onToggleSelect = {
                        if (taskWithCourse.task.id in selectedTaskIds) selectedTaskIds.remove(taskWithCourse.task.id)
                        else selectedTaskIds.add(taskWithCourse.task.id)
                    },
                    onStatusCycle = onStatusCycle,
                    onEditTask = onEditTask,
                    onDeleteTask = onDeleteTask,
                    modifier = Modifier.animateItem(),
                    groupEntry = groupEntries[taskWithCourse.task.id]
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    isOverdue: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            shape = CircleShape,
            color = if (isOverdue) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isOverdue) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
            )
        }
    }
}

private data class DeadlineSections(
    val overdue: List<TaskWithCourse>,
    val today: List<TaskWithCourse>,
    val tomorrow: List<TaskWithCourse>,
    val later: List<TaskWithCourse>,
    val done: List<TaskWithCourse>
)

private fun groupDeadlines(tasks: List<TaskWithCourse>, nowMillis: Long = System.currentTimeMillis()): DeadlineSections {
    val calNow = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val currentYear = calNow.get(Calendar.YEAR)
    val currentDayOfYear = calNow.get(Calendar.DAY_OF_YEAR)

    val calTomorrow = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        add(Calendar.DAY_OF_YEAR, 1)
    }
    val tomorrowYear = calTomorrow.get(Calendar.YEAR)
    val tomorrowDayOfYear = calTomorrow.get(Calendar.DAY_OF_YEAR)

    val overdue = mutableListOf<TaskWithCourse>()
    val today = mutableListOf<TaskWithCourse>()
    val tomorrow = mutableListOf<TaskWithCourse>()
    val later = mutableListOf<TaskWithCourse>()
    val done = mutableListOf<TaskWithCourse>()

    for (item in tasks) {
        val task = item.task
        if (task.status == TaskStatus.COMPLETED) {
            done.add(item)
            continue
        }

        val taskDeadline = task.deadlineMillis
        if (taskDeadline < nowMillis) {
            overdue.add(item)
        } else {
            val taskCal = Calendar.getInstance().apply { timeInMillis = task.dueDateEpochMillis }
            val taskYear = taskCal.get(Calendar.YEAR)
            val taskDayOfYear = taskCal.get(Calendar.DAY_OF_YEAR)

            if (taskYear == currentYear && taskDayOfYear == currentDayOfYear) {
                today.add(item)
            } else if (taskYear == tomorrowYear && taskDayOfYear == tomorrowDayOfYear) {
                tomorrow.add(item)
            } else {
                later.add(item)
            }
        }
    }

    return DeadlineSections(
        overdue = overdue.sortedBy { it.task.deadlineMillis },
        today = today.sortedBy { it.task.deadlineMillis },
        tomorrow = tomorrow.sortedBy { it.task.deadlineMillis },
        later = later.sortedBy { it.task.deadlineMillis },
        done = done.sortedByDescending { it.task.deadlineMillis }
    )
}

@Composable
private fun StatItem(
    count: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = count,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SelectableTaskCard(
    taskWithCourse: TaskWithCourse,
    isBatchMode: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onStatusCycle: (TaskItem) -> Unit,
    onEditTask: (TaskItem) -> Unit,
    onDeleteTask: (TaskItem) -> Unit,
    modifier: Modifier = Modifier,
    groupEntry: GroupTaskEntry? = null
) {
    if (isBatchMode) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() },
                modifier = Modifier.padding(end = 4.dp)
            )
            Box(modifier = Modifier.weight(1f).clickable { onToggleSelect() }) {
                TaskCard(
                    taskWithCourse = taskWithCourse,
                    onStatusCycle = onStatusCycle,
                    onEditClick = onEditTask,
                    onDeleteClick = onDeleteTask,
                    groupEntry = groupEntry
                )
            }
        }
    } else {
        TaskCard(
            taskWithCourse = taskWithCourse,
            onStatusCycle = onStatusCycle,
            onEditClick = onEditTask,
            onDeleteClick = onDeleteTask,
            modifier = modifier,
            groupEntry = groupEntry
        )
    }
}

@Composable
private fun DeadlinesCalendarView(
    tasks: List<TaskWithCourse>,
    onStatusCycle: (TaskItem) -> Unit,
    onEditTask: (TaskItem) -> Unit,
    onDeleteTask: (TaskItem) -> Unit,
    groupEntries: Map<Long, GroupTaskEntry>
) {
    val cal = remember { Calendar.getInstance() }
    var currentYear by remember { mutableIntStateOf(cal.get(Calendar.YEAR)) }
    var currentMonth by remember { mutableIntStateOf(cal.get(Calendar.MONTH)) }
    var selectedDayEpoch by remember {
        val todayCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        mutableStateOf(todayCal.timeInMillis)
    }

    val monthFormatter = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val displayMonth = remember(currentYear, currentMonth) {
        val c = Calendar.getInstance().apply {
            set(Calendar.YEAR, currentYear)
            set(Calendar.MONTH, currentMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        monthFormatter.format(c.time)
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
            val c = Calendar.getInstance().apply {
                timeInMillis = item.task.dueDateEpochMillis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            map.getOrPut(c.timeInMillis) { mutableListOf() }.add(item)
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
                                val cellCal = Calendar.getInstance().apply {
                                    set(Calendar.YEAR, currentYear)
                                    set(Calendar.MONTH, currentMonth)
                                    set(Calendar.DAY_OF_MONTH, dayNumber)
                                    set(Calendar.HOUR_OF_DAY, 0)
                                    set(Calendar.MINUTE, 0)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }
                                val cellEpoch = cellCal.timeInMillis
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
        SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date(selectedDayEpoch))
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
                TaskCard(
                    taskWithCourse = taskWithCourse,
                    onStatusCycle = onStatusCycle,
                    onEditClick = onEditTask,
                    onDeleteClick = onDeleteTask,
                    groupEntry = groupEntries[taskWithCourse.task.id]
                )
            }
        }
    }
}
