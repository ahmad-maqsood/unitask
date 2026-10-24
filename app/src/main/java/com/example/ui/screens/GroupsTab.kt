package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.groups.GroupMembership
import kotlinx.coroutines.flow.collectLatest

@Composable
fun GroupsTab(
    onOpenCloudSheet: () -> Unit,
    modifier: Modifier = Modifier,
    // Set when the user taps a group task on the Deadlines tab: open that group.
    groupToOpen: String? = null,
    onGroupOpened: () -> Unit = {},
    viewModel: GroupsViewModel = viewModel()
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val memberships by viewModel.myMemberships.collectAsStateWithLifecycle()
    val selectedGroupId by viewModel.selectedGroupId.collectAsStateWithLifecycle()
    val selectedGroupDetail by viewModel.selectedGroupDetail.collectAsStateWithLifecycle()
    val selectedGroupMembers by viewModel.selectedGroupMembers.collectAsStateWithLifecycle()
    val selectedGroupTasks by viewModel.selectedGroupTasks.collectAsStateWithLifecycle()
    val selectedGroupMessages by viewModel.selectedGroupMessages.collectAsStateWithLifecycle()
    val myDoneTaskIds by viewModel.myDoneTaskIds.collectAsStateWithLifecycle()
    val isActionLoading by viewModel.isActionLoading.collectAsStateWithLifecycle()

    val isCreateDialogOpen by viewModel.isCreateDialogOpen.collectAsStateWithLifecycle()
    val isJoinDialogOpen by viewModel.isJoinDialogOpen.collectAsStateWithLifecycle()
    val isAddEditTaskDialogOpen by viewModel.isAddEditTaskDialogOpen.collectAsStateWithLifecycle()
    val taskToEdit by viewModel.taskToEdit.collectAsStateWithLifecycle()

    var createdGroupSuccessInfo by remember { mutableStateOf<Triple<String, String, String>?>(null) }

    LaunchedEffect(groupToOpen, currentUser) {
        if (groupToOpen != null && currentUser != null) {
            viewModel.selectGroup(groupToOpen)
            onGroupOpened()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // If user is not signed in
            if (currentUser == null) {
                NotSignedInState(onOpenCloudSheet = onOpenCloudSheet)
            } else if (selectedGroupId != null) {
                // Detail screen for selected group
                val activeMembership = memberships.find { it.groupId == selectedGroupId }
                GroupDetailScreen(
                    currentUid = currentUser?.uid ?: "",
                    groupDetail = selectedGroupDetail,
                    fallbackUsername = activeMembership?.username ?: "",
                    fallbackName = activeMembership?.name ?: "",
                    fallbackRole = activeMembership?.role ?: "member",
                    tasks = selectedGroupTasks,
                    members = selectedGroupMembers,
                    doneTaskIds = myDoneTaskIds,
                    chatMessages = selectedGroupMessages,
                    onSendMessage = { text -> viewModel.sendMessage(text) },
                    onDeleteMessage = { message -> viewModel.deleteMessage(message) },
                    isLoading = isActionLoading,
                    onBack = { viewModel.clearSelectedGroup() },
                    onAddTaskClick = { viewModel.openAddTaskDialog() },
                    onEditTaskClick = { task -> viewModel.openEditTaskDialog(task) },
                    onDeleteTaskClick = { task -> viewModel.deleteTask(task) },
                    onToggleTaskDone = { task -> viewModel.toggleTaskDone(task) },
                    onSetMemberRole = { member, makeAdmin -> viewModel.setMemberRole(member, makeAdmin) },
                    onRemoveMember = { member -> viewModel.removeMember(member) },
                    onLeaveGroupClick = {
                        viewModel.leaveCurrentGroup {
                            Toast.makeText(context, "Left group", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDeleteGroupClick = {
                        val username = selectedGroupDetail?.username ?: activeMembership?.username ?: ""
                        viewModel.deleteCurrentGroup(username) {
                            Toast.makeText(context, "Group deleted", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            } else {
                // Group list screen
                GroupsListScreen(
                    memberships = memberships,
                    onCreateGroupClick = { viewModel.openCreateDialog() },
                    onJoinGroupClick = { viewModel.openJoinDialog() },
                    onSelectGroup = { groupId -> viewModel.selectGroup(groupId) }
                )
            }

            // Dialogs
            if (isCreateDialogOpen) {
                CreateGroupDialog(
                    isLoading = isActionLoading,
                    onDismiss = { viewModel.closeCreateDialog() },
                    onCreate = { name, username ->
                        viewModel.createGroup(name, username) { success, resultOrMessage ->
                            if (success && resultOrMessage != null) {
                                createdGroupSuccessInfo = Triple(name, username, resultOrMessage)
                            } else {
                                Toast.makeText(context, resultOrMessage ?: "Failed to create group", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
            }

            if (isJoinDialogOpen) {
                JoinGroupDialog(
                    isLoading = isActionLoading,
                    onDismiss = { viewModel.closeJoinDialog() },
                    onJoin = { username, joinCode ->
                        viewModel.joinGroup(username, joinCode) { success, errorMessage ->
                            if (!success) {
                                Toast.makeText(context, errorMessage ?: "Failed to join group", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
            }

            createdGroupSuccessInfo?.let { (name, uname, code) ->
                GroupCreatedSuccessDialog(
                    groupName = name,
                    username = uname,
                    joinCode = code,
                    onDismiss = { createdGroupSuccessInfo = null }
                )
            }

            if (isAddEditTaskDialogOpen) {
                GroupTaskDialog(
                    taskToEdit = taskToEdit,
                    onDismiss = { viewModel.closeAddEditTaskDialog() },
                    onSave = { title, desc, course, dueMillis, hour, minute, priority, reminders ->
                        viewModel.saveTask(title, desc, course, dueMillis, hour, minute, priority, reminders)
                    }
                )
            }
        }
    }
}

@Composable
fun NotSignedInState(onOpenCloudSheet: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("groups_not_signed_in_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Groups,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Text(
                    text = "Sign in with Google to use groups",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Collaborate with classmates, share assignments, and track group deadlines in real time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Button(
                    onClick = onOpenCloudSheet,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("groups_sign_in_button")
                ) {
                    Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Sign in with Google")
                }
            }
        }
    }
}

@Composable
fun GroupsListScreen(
    memberships: List<GroupMembership>,
    onCreateGroupClick: () -> Unit,
    onJoinGroupClick: () -> Unit,
    onSelectGroup: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Action Buttons Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onCreateGroupClick,
                modifier = Modifier
                    .weight(1f)
                    .testTag("create_group_button")
            ) {
                Icon(Icons.Default.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Create a group", maxLines = 1)
            }

            FilledTonalButton(
                onClick = onJoinGroupClick,
                modifier = Modifier
                    .weight(1f)
                    .testTag("join_group_button")
            ) {
                Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Join a group", maxLines = 1)
            }
        }

        if (memberships.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Groups,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "No groups yet",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Create a new group for your study team or join an existing group with a join code.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(memberships, key = { it.groupId }) { group ->
                    GroupCardItem(
                        membership = group,
                        onClick = { onSelectGroup(group.groupId) }
                    )
                }
            }
        }
    }
}

@Composable
fun GroupCardItem(
    membership: GroupMembership,
    onClick: () -> Unit
) {
    val isOwner = membership.role == "owner"

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("group_item_${membership.groupId}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isOwner) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = null,
                        tint = if (isOwner) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = membership.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "@${membership.username}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (isOwner) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = if (isOwner) "Owner" else "Member",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isOwner) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
