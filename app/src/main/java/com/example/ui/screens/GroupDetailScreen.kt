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
import com.example.data.groups.GroupDetail
import com.example.data.groups.GroupMember
import com.example.data.groups.GroupTask
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    currentUid: String,
    groupDetail: GroupDetail?,
    fallbackUsername: String,
    fallbackName: String,
    fallbackRole: String,
    tasks: List<GroupTask>,
    members: List<GroupMember>,
    doneTaskIds: Set<String>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onAddTaskClick: () -> Unit,
    onEditTaskClick: (GroupTask) -> Unit,
    onDeleteTaskClick: (GroupTask) -> Unit,
    onToggleTaskDone: (GroupTask) -> Unit,
    onSetMemberRole: (GroupMember, Boolean) -> Unit,
    onRemoveMember: (GroupMember) -> Unit,
    onLeaveGroupClick: () -> Unit,
    onDeleteGroupClick: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    var selectedSectionTab by remember { mutableIntStateOf(0) } // 0: Tasks, 1: Members
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
        tasks.sortedWith(
            compareBy<GroupTask> { it.dueDateEpochMillis }
                .thenBy { it.dueTimeHour }
                .thenBy { it.dueTimeMinute }
        )
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Join Code banner card
            if (joinCode.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
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
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Join Code", joinCode)
                                    clipboard.setPrimaryClip(clip)
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
                                            "Join my UniTask group @$effectiveUsername with code $joinCode"
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

            // Tab bar for Tasks vs Members
            TabRow(
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
                    text = { Text("Members (${members.size})") },
                   
                    modifier = Modifier.testTag("group_tab_members")
                )
            }

            if (selectedSectionTab == 0) {
                // Tasks list
                if (sortedTasks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
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
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(sortedTasks, key = { it.id }) { task ->
                            val isDone = doneTaskIds.contains(task.id)
                            val canModify = task.createdBy == currentUid || isStaff

                            GroupTaskRow(
                                task = task,
                                isDone = isDone,
                                canModify = canModify,
                                onToggleDone = { onToggleTaskDone(task) },
                                onEdit = { onEditTaskClick(task) },
                                onDelete = { onDeleteTaskClick(task) }
                            )
                        }
                        item {
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
                                            fontWeight = FontWeight.SemiBold
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
    isDone: Boolean,
    canModify: Boolean,
    onToggleDone: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormatter = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
    val dueDateStr = dateFormatter.format(Date(task.dueDateEpochMillis))
    val timeStr = String.format(Locale.getDefault(), "%02d:%02d", task.dueTimeHour, task.dueTimeMinute)

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
        modifier = Modifier
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
                        if (!task.courseLabel.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    text = task.courseLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
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
                                color = priorityColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (canModify) {
                    Row {
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("group_task_edit_${task.id}")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit task", modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = onDelete,
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
            }
        }
    }
}
