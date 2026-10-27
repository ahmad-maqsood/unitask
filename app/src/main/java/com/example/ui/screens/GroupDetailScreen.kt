package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.groups.GroupCourse
import com.example.data.groups.GroupDetail
import com.example.data.groups.GroupMember
import com.example.data.groups.GroupMessage
import com.example.data.groups.GroupTask
import androidx.compose.material.icons.filled.School
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.data.local.computeDeadlineMillis

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    currentUid: String,
    groupDetail: GroupDetail?,
    fallbackUsername: String,
    fallbackName: String,
    fallbackRole: String,
    tasks: List<GroupTask>,
    courses: List<GroupCourse> = emptyList(),
    members: List<GroupMember>,
    doneTaskIds: Set<String>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onAddTaskClick: () -> Unit,
    onEditTaskClick: (GroupTask) -> Unit,
    onDeleteTaskClick: (GroupTask) -> Unit,
    onToggleTaskDone: (GroupTask) -> Unit,
    onAddCourseClick: () -> Unit = {},
    onEditCourseClick: (GroupCourse) -> Unit = {},
    onDeleteCourseClick: (GroupCourse) -> Unit = {},
    onSetMemberRole: (GroupMember, Boolean) -> Unit,
    onRemoveMember: (GroupMember) -> Unit,
    onLeaveGroupClick: () -> Unit,
    onDeleteGroupClick: () -> Unit,
    chatMessages: List<GroupMessage>,
    onSendMessage: (String) -> Unit,
    onDeleteMessage: (GroupMessage) -> Unit,
    chatUnreadCount: Int = 0,
    chatLastReadMillis: Long? = null,
    onChatRead: (Long) -> Unit = {},
    startOnChat: Boolean = false
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    var selectedSectionTab by remember(groupDetail?.id ?: fallbackUsername, startOnChat) {
        mutableIntStateOf(if (startOnChat) 3 else 0)
    } // 0: Tasks, 1: Courses, 2: Members, 3: Chat
    var showLeaveConfirmDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val effectiveName = groupDetail?.name?.ifBlank { null } ?: fallbackName.ifBlank { "Group" }
    val effectiveUsername = groupDetail?.username?.ifBlank { null } ?: fallbackUsername
    val isOwner = groupDetail?.ownerId == currentUid || fallbackRole == "owner"
    val joinCode = groupDetail?.joinCode ?: ""

    val myRole = members.find { it.uid == currentUid }?.role ?: fallbackRole
    val isAdmin = myRole == "admin"
    val isStaff = isOwner || isAdmin
    var memberToRemove by remember { mutableStateOf<GroupMember?>(null) }
    val sortedMembers = remember(members) {
        members.sortedWith(
            compareBy<GroupMember> {
                when (it.role) {
                    "owner" -> 0
                    "admin" -> 1
                    else -> 2
                }
            }.thenBy { it.displayName.lowercase() }
        )
    }
    // Sort tasks: due date and time ascending
        val sortedTasks = remember(tasks) {
        tasks.map { it to computeDeadlineMillis(it.dueDateEpochMillis, it.dueTimeHour, it.dueTimeMinute) }
            .sortedBy { it.second }
            .map { it.first }
    }


    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Column {
                        Text(
                            text = effectiveName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (effectiveUsername.isNotBlank()) {
                            Text(
                                text = "@$effectiveUsername",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("group_detail_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isOwner) {
                        IconButton(
                            onClick = { showDeleteConfirmDialog = true },
                            modifier = Modifier.testTag("group_delete_button")
                        ) {
                            Icon(
                                Icons.Default.DeleteForever,
                                contentDescription = "Delete Group",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        IconButton(
                            onClick = { showLeaveConfirmDialog = true },
                            modifier = Modifier.testTag("group_leave_button")
                        ) {
                            Icon(
                                Icons.Default.ExitToApp,
                                contentDescription = "Leave Group",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (selectedSectionTab == 0) {
                FloatingActionButton(
                    onClick = onAddTaskClick,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("group_add_task_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Task")
                }
            } else if (selectedSectionTab == 1) {
                FloatingActionButton(
                    onClick = onAddCourseClick,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("group_add_course_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Course")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {


            // Tab bar for Tasks, Courses, Members, Chat
            ScrollableTabRow(
                edgePadding = 8.dp,
                selectedTabIndex = selectedSectionTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedSectionTab == 0,
                    onClick = { selectedSectionTab = 0 },
                    text = { Text("Tasks (${sortedTasks.size})") },
                    modifier = Modifier.testTag("group_tab_tasks")
                )
                Tab(
                    selected = selectedSectionTab == 1,
                    onClick = { selectedSectionTab = 1 },
                    text = { Text("Courses (${courses.size})") },
                    modifier = Modifier.testTag("group_tab_courses")
                )
                Tab(
                    selected = selectedSectionTab == 2,
                    onClick = { selectedSectionTab = 2 },
                    text = { Text("Members (${members.size})") },
                    modifier = Modifier.testTag("group_tab_members")
                )
                Tab(
                    selected = selectedSectionTab == 3,
                    onClick = { selectedSectionTab = 3 },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Chat")
                            if (chatUnreadCount > 0 && selectedSectionTab != 3) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError,
                                    modifier = Modifier.defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = if (chatUnreadCount >= 50) "50+" else "$chatUnreadCount",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier.testTag("group_tab_chat")
                )

            }

            if (selectedSectionTab == 3) {
                GroupChatPanel(
                    messages = chatMessages,
                    currentUid = currentUid,
                    canModerate = isStaff,
                    onSend = onSendMessage,
                    onDelete = onDeleteMessage,
                    lastReadAtOpenMillis = chatLastReadMillis,
                    onMarkRead = onChatRead
                )
            } else if (selectedSectionTab == 1) {
                GroupCoursesSection(
                    courses = courses,
                    tasks = sortedTasks,
                    currentUid = currentUid,
                    isStaff = isStaff,
                    joinCode = joinCode,
                    effectiveUsername = effectiveUsername,
                    onAddCourse = onAddCourseClick,
                    onEditCourse = onEditCourseClick,
                    onDeleteCourse = onDeleteCourseClick
                )
            } else if (selectedSectionTab == 0) {
                // Tasks list. The join code card is the first item, so it scrolls away with the list.
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (joinCode.isNotBlank()) {
                        item(key = "join_code") {
                            JoinCodeCard(joinCode = joinCode, username = effectiveUsername)
                        }
                    }
                    if (sortedTasks.isEmpty()) {
                        item(key = "empty") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp, horizontal = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Assignment,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Text(
                                    text = "No tasks in this group yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Tap the + button to add the first assignment or milestone.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(sortedTasks, key = { it.id }) { task ->
                            val isDone = doneTaskIds.contains(task.id)
                            val canModify = task.createdBy == currentUid || isStaff
                            // "CS-509 • Theory of Automata" when the tag matches one of the group's courses.
                            val courseText = run {
                                val label = task.courseLabel?.trim().orEmpty()
                                if (label.isEmpty()) {
                                    null
                                } else {
                                    val key = normalizeCourseKey(label)
                                    val matched = courses.find { key.isNotEmpty() && normalizeCourseKey(it.code) == key }
                                        ?: courses.find { it.name.trim().equals(label, ignoreCase = true) }
                                    if (matched != null) "${matched.code} • ${matched.name}" else label
                                }
                            }
                            GroupTaskRow(
                                modifier = Modifier.animateItem(),
                                task = task,
                                members = members,
                                currentUid = currentUid,
                                isDone = isDone,
                                canModify = canModify,
                                courseText = courseText,
                                onToggleDone = { onToggleTaskDone(task) },
                                onEdit = { onEditTaskClick(task) },
                                onDelete = { onDeleteTaskClick(task) }
                            )
                        }
                        item(key = "bottom_space") {
                            Spacer(Modifier.height(72.dp))
                        }
                    }
                }
            } else {
                // Members list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (joinCode.isNotBlank()) {
                        item(key = "join_code") {
                            JoinCodeCard(joinCode = joinCode, username = effectiveUsername)
                        }
                    }
                        items(sortedMembers, key = { it.uid }) { member ->
                        val isMemberOwner = member.role == "owner"
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isMemberOwner) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.secondaryContainer
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isMemberOwner) Icons.Default.Star else Icons.Default.Person,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = if (isMemberOwner) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.secondary
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = member.displayName,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (member.uid == currentUid) {
                                            Text(
                                                text = "(You)",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }

                                                               val roleLabel = when (member.role) {
                                    "owner" -> "Owner"
                                    "admin" -> "Admin"
                                    else -> null
                                }
                                if (roleLabel != null) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isMemberOwner) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.tertiaryContainer
                                    ) {
                                        Text(
                                            text = roleLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isMemberOwner) MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                // Owner manages everyone except themselves; admins can only remove plain members.
                                val canManage = member.uid != currentUid && !isMemberOwner &&
                                    (isOwner || (isAdmin && member.role == "member"))
                                if (canManage) {
                                    var menuOpen by remember { mutableStateOf(false) }
                                    Box {
                                        IconButton(
                                            onClick = { menuOpen = true },
                                            modifier = Modifier
                                                .size(32.dp)
                                                .testTag("member_menu_${member.uid}")
                                        ) {
                                            Icon(Icons.Default.MoreVert, contentDescription = "Member options")
                                        }
                                        DropdownMenu(
                                            expanded = menuOpen,
                                            onDismissRequest = { menuOpen = false }
                                        ) {
                                            if (isOwner) {
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(if (member.role == "admin") "Remove admin role" else "Make admin")
                                                    },
                                                    onClick = {
                                                        menuOpen = false
                                                        onSetMemberRole(member, member.role != "admin")
                                                    }
                                                )
                                            }
                                            DropdownMenuItem(
                                                text = {
                                                    Text("Remove from group", color = MaterialTheme.colorScheme.error)
                                                },
                                                onClick = {
                                                    menuOpen = false
                                                    memberToRemove = member
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Spacer(Modifier.height(72.dp))
                    }
                }
            }
        }
    }

    if (showLeaveConfirmDialog) {
        AlertDialog(
            onDismissRequest = { if (!isLoading) showLeaveConfirmDialog = false },
            title = { Text("Leave Group?") },
            text = { Text("Are you sure you want to leave \"$effectiveName\"? You will need the join code to re-join.") },
            confirmButton = {
                Button(
                    onClick = {
                        showLeaveConfirmDialog = false
                        onLeaveGroupClick()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_leave_group_button")
                ) {
                    Text("Leave")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { if (!isLoading) showDeleteConfirmDialog = false },
            title = { Text("Delete Group?") },
            text = { Text("Are you sure you want to delete \"$effectiveName\"? This permanently removes the group, all tasks, and memberships for all students.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeleteGroupClick()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_group_button")
                ) {
                    Text("Delete for Everyone")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    memberToRemove?.let { target ->
        AlertDialog(
            onDismissRequest = { memberToRemove = null },
            title = { Text("Remove member?") },
            text = { Text("Remove ${target.displayName} from \"$effectiveName\"? They can join again with the join code.") },
            confirmButton = {
                Button(
                    onClick = {
                        memberToRemove = null
                        onRemoveMember(target)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_remove_member_button")
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToRemove = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun GroupTaskRow(
    task: GroupTask,
    members: List<GroupMember>,
    currentUid: String,
    isDone: Boolean,
    canModify: Boolean,
    courseText: String? = null,
    onToggleDone: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
        var showDeleteConfirm by remember { mutableStateOf(false) }
    if (showDeleteConfirm) {
        ConfirmDeleteDialog(
            title = "Delete task?",
            message = "\"${task.title}\" will be deleted for everyone in the group.",
            onConfirm = onDelete,
            onDismiss = { showDeleteConfirm = false }
        )
    }
    val dueDateStr = remember(task.dueDateEpochMillis) {
        SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(task.dueDateEpochMillis))
    }
    val timeStr = remember(task.dueTimeHour, task.dueTimeMinute) {
        com.example.util.TimeFormatter.format12Hour(task.dueTimeHour, task.dueTimeMinute)
    }

    val priorityColor = when (task.priority) {
        "HIGH" -> MaterialTheme.colorScheme.error
        "LOW" -> Color(0xFF10B981)
        else -> Color(0xFFF59E0B)
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isDone) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDone) 0.dp else 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("group_task_card_${task.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Personal Done Checkbox
                IconButton(
                    onClick = onToggleDone,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("group_task_done_checkbox_${task.id}")
                ) {
                    Icon(
                        imageVector = if (isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = if (isDone) "Mark not done" else "Mark done",
                        tint = if (isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Chips take the free width. A long course name shrinks and wraps instead of
                        // pushing the priority chip and the buttons out of the card.
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (!task.courseLabel.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    Text(
                                        text = courseText ?: task.courseLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = priorityColor.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = task.priority,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    color = priorityColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (canModify) {
                            IconButton(
                                onClick = onEdit,
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("group_task_edit_${task.id}")
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit task", modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = { showDeleteConfirm = true },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("group_task_delete_${task.id}")
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete task",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None,
                        color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )

                    if (!task.description.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = task.description,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                        Text(
                        text = "Due: $dueDateStr at $timeStr",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (task.createdByName.isNotBlank()) {
                        Text(
                            text = "Added by ${task.createdByName}",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    val tagText = remember(task.assignedTo, members) {
                        if (task.assignedTo.isEmpty()) {
                            "@all"
                        } else {
                            task.assignedTo.joinToString(", ") { uid ->
                                "@" + (members.find { it.uid == uid }?.displayName?.takeIf { it.isNotBlank() } ?: "Member")
                            }
                        }
                    }
                    val notOnMyDeadlines = task.assignedTo.isNotEmpty() && currentUid !in task.assignedTo
                    Text(
                        text = "Tagged: $tagText" + if (notOnMyDeadlines) " (not on your Deadlines)" else "",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun JoinCodeCard(
    joinCode: String,
    username: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Group Join Code",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
                Text(
                    text = joinCode,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Join Code", joinCode))
                        Toast.makeText(context, "Join code copied!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.testTag("copy_join_code_button")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy join code")
                }

                IconButton(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Join my UniTask group @$username with code $joinCode"
                            )
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Group Join Code"))
                    },
                    modifier = Modifier.testTag("share_join_code_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share join code")
                }
            }
        }
    }
}

@Composable
private fun GroupCoursesSection(
    courses: List<GroupCourse>,
    tasks: List<GroupTask>,
    currentUid: String,
    isStaff: Boolean,
    joinCode: String,
    effectiveUsername: String,
    onAddCourse: () -> Unit,
    onEditCourse: (GroupCourse) -> Unit,
    onDeleteCourse: (GroupCourse) -> Unit
) {
    var courseToDelete by remember { mutableStateOf<GroupCourse?>(null) }
    courseToDelete?.let { c ->
        ConfirmDeleteDialog(
            title = "Delete Course?",
            message = "Remove ${c.code} (${c.name}) from group courses? Existing assignments will keep their label.",
            onConfirm = {
                onDeleteCourse(c)
                courseToDelete = null
            },
            onDismiss = { courseToDelete = null }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (joinCode.isNotBlank()) {
            item(key = "join_code") {
                JoinCodeCard(joinCode = joinCode, username = effectiveUsername)
            }
        }

        item(key = "courses_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Group Courses",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                OutlinedButton(
                    onClick = onAddCourse,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("group_add_course_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.size(4.dp))
                    Text("Add Course", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        if (courses.isEmpty()) {
            item(key = "empty_courses") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .testTag("empty_group_courses_card"),
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
                            text = "No courses added yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Add courses for your class group (e.g. CS-503, MATH-240) so members can select them when creating assignments.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = onAddCourse,
                            modifier = Modifier.testTag("group_empty_add_course_btn")
                        ) {
                            Text("+ Add First Course")
                        }
                    }
                }
            }
        } else {
            items(courses, key = { it.id }) { course ->
                val courseKey = normalizeCourseKey(course.code)
                val taskCount = tasks.count { t ->
                    val label = t.courseLabel?.trim().orEmpty()
                    label.isNotEmpty() && (
                        (courseKey.isNotEmpty() && normalizeCourseKey(label) == courseKey) ||
                        label.equals(course.name.trim(), ignoreCase = true)
                    )
                }
                val canManage = isStaff || course.createdBy == currentUid

                GroupCourseCard(
                    course = course,
                    taskCount = taskCount,
                    canManage = canManage,
                    onEditClick = { onEditCourse(course) },
                    onDeleteClick = { courseToDelete = course }
                )
            }
            item(key = "bottom_space") {
                Spacer(Modifier.height(72.dp))
            }
        }
    }
}

@Composable
private fun GroupCourseCard(
    course: GroupCourse,
    taskCount: Int,
    canManage: Boolean,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val courseColor = remember(course.colorHex) {
        try {
            Color(android.graphics.Color.parseColor(course.colorHex))
        } catch (_: Exception) {
            Color(0xFF4F46E5)
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("group_course_card_${course.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(courseColor)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.25f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = course.code.take(2).uppercase(),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = course.code,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (course.instructor.isNotBlank()) {
                                Text(
                                    text = course.instructor,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    if (canManage) {
                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("group_course_menu_${course.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Course options",
                                    tint = Color.White
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Edit Course") },
                                    onClick = {
                                        showMenu = false
                                        onEditClick()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Edit, contentDescription = null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete Course", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showMenu = false
                                        onDeleteClick()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = course.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = courseColor.copy(alpha = 0.12f),
                    contentColor = courseColor
                ) {
                    Text(
                        text = "$taskCount assignment${if (taskCount != 1) "s" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}