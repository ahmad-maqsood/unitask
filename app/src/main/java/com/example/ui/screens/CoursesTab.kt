package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.Course
import com.example.data.local.CourseWithTaskCount

@Composable
fun CoursesTab(
    coursesWithCounts: List<CourseWithTaskCount>,
    onCourseClick: (Long) -> Unit,
    onEditCourse: (Course) -> Unit,
    onDeleteCourse: (Course) -> Unit,
    onAddCourseClick: () -> Unit,
    archivedSemestersCount: Int = 0,
    onArchiveCoursesClick: () -> Unit = {},
    onArchivedSemestersClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Enrolled Courses Header & Add Button
        item(key = "courses_header") {
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
                        text = "Enrolled Courses",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${coursesWithCounts.size} courses this semester",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    var showMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.testTag("courses_more_button")
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More course options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Archive courses...") },
                                onClick = {
                                    showMenu = false
                                    onArchiveCoursesClick()
                                }
                            )
                            val archivedLabel = if (archivedSemestersCount > 0) {
                                "Archived semesters ($archivedSemestersCount)"
                            } else {
                                "Archived semesters"
                            }
                            DropdownMenuItem(
                                text = { Text(archivedLabel) },
                                onClick = {
                                    showMenu = false
                                    onArchivedSemestersClick()
                                }
                            )
                        }
                    }
                }
            }
        }

        // List of Course Cards
        if (coursesWithCounts.isEmpty()) {
            item(key = "courses_empty") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp)
                        .testTag("empty_courses_card"),
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
                                    imageVector = Icons.Default.School,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Add your first course",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Add your university courses to organize your homework and assignments.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = onAddCourseClick,
                            modifier = Modifier.testTag("empty_state_add_course_btn")
                        ) {
                            Text("+ Add Your First Course")
                        }
                    }
                }
            }
        } else {
            items(coursesWithCounts, key = { it.course.id }) { item ->
                CourseCard(
                    courseWithCount = item,
                    onClick = { onCourseClick(item.course.id) },
                    onEditClick = onEditCourse,
                    onDeleteClick = onDeleteCourse
                )
            }
        }

        if (archivedSemestersCount > 0) {
            item(key = "archived_semesters_footer_row") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onArchivedSemestersClick() }
                        .testTag("archived_semesters_row"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.School,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Archived semesters ($archivedSemestersCount)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "View archived semesters",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
