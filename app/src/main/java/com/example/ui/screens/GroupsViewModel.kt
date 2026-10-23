package com.example.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.groups.GroupDetail
import com.example.data.groups.GroupMember
import com.example.data.groups.GroupMembership
import com.example.data.groups.GroupRepository
import com.example.data.groups.GroupTask
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GroupsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GroupRepository(application)
    private val auth: FirebaseAuth = Firebase.auth

    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _selectedGroupId = MutableStateFlow<String?>(null)
    val selectedGroupId: StateFlow<String?> = _selectedGroupId.asStateFlow()

    private val _isCreateDialogOpen = MutableStateFlow(false)
    val isCreateDialogOpen: StateFlow<Boolean> = _isCreateDialogOpen.asStateFlow()

    private val _isJoinDialogOpen = MutableStateFlow(false)
    val isJoinDialogOpen: StateFlow<Boolean> = _isJoinDialogOpen.asStateFlow()

    private val _isAddEditTaskDialogOpen = MutableStateFlow(false)
    val isAddEditTaskDialogOpen: StateFlow<Boolean> = _isAddEditTaskDialogOpen.asStateFlow()

    private val _taskToEdit = MutableStateFlow<GroupTask?>(null)
    val taskToEdit: StateFlow<GroupTask?> = _taskToEdit.asStateFlow()

    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    private val _isActionLoading = MutableStateFlow(false)
    val isActionLoading: StateFlow<Boolean> = _isActionLoading.asStateFlow()

    private var authListener: FirebaseAuth.AuthStateListener? = null

    init {
        authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            _currentUser.value = firebaseAuth.currentUser
        }
        auth.addAuthStateListener(authListener!!)
    }

    override fun onCleared() {
        super.onCleared()
        authListener?.let { auth.removeAuthStateListener(it) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val myMemberships: StateFlow<List<GroupMembership>> = _currentUser.flatMapLatest { user ->
        if (user != null) {
            repository.observeMyGroupMemberships(user.uid)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedGroupDetail: StateFlow<GroupDetail?> = _selectedGroupId.flatMapLatest { groupId ->
        if (!groupId.isNullOrBlank()) {
            repository.observeGroup(groupId)
        } else {
            flowOf(null)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedGroupMembers: StateFlow<List<GroupMember>> = _selectedGroupId.flatMapLatest { groupId ->
        if (!groupId.isNullOrBlank()) {
            repository.observeGroupMembers(groupId)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedGroupTasks: StateFlow<List<GroupTask>> = _selectedGroupId.flatMapLatest { groupId ->
        if (!groupId.isNullOrBlank()) {
            repository.observeGroupTasks(groupId)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val myDoneTaskIds: StateFlow<Set<String>> = kotlinx.coroutines.flow.combine(
        _currentUser,
        _selectedGroupId
    ) { user, groupId ->
        Pair(user?.uid, groupId)
    }.flatMapLatest { (uid, groupId) ->
        if (!uid.isNullOrBlank() && !groupId.isNullOrBlank()) {
            repository.observeGroupDoneTasks(uid, groupId)
        } else {
            flowOf(emptySet())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun selectGroup(groupId: String) {
        _selectedGroupId.value = groupId
        // Verify if stale in background
        val user = _currentUser.value
        if (user != null) {
            viewModelScope.launch {
                val wasStale = repository.checkAndCleanStaleMembership(user.uid, groupId)
                if (wasStale && _selectedGroupId.value == groupId) {
                    _selectedGroupId.value = null
                    _snackbarMessage.emit("This group is no longer available.")
                }
            }
        }
    }

    fun clearSelectedGroup() {
        _selectedGroupId.value = null
    }

    fun openCreateDialog() {
        _isCreateDialogOpen.value = true
    }

    fun closeCreateDialog() {
        _isCreateDialogOpen.value = false
    }

    fun openJoinDialog() {
        _isJoinDialogOpen.value = true
    }

    fun closeJoinDialog() {
        _isJoinDialogOpen.value = false
    }

    fun openAddTaskDialog() {
        _taskToEdit.value = null
        _isAddEditTaskDialogOpen.value = true
    }

    fun openEditTaskDialog(task: GroupTask) {
        _taskToEdit.value = task
        _isAddEditTaskDialogOpen.value = true
    }

    fun closeAddEditTaskDialog() {
        _isAddEditTaskDialogOpen.value = false
        _taskToEdit.value = null
    }

    fun createGroup(name: String, username: String, onComplete: (Boolean, String?) -> Unit) {
        val user = _currentUser.value
        if (user == null) {
            onComplete(false, "You must be signed in to create a group.")
            return
        }

        viewModelScope.launch {
            _isActionLoading.value = true
            val result = repository.createGroup(
                name = name,
                username = username,
                uid = user.uid,
                rawDisplayName = user.displayName ?: "Student"
            )
            _isActionLoading.value = false
            result.onSuccess { (newGroupId, joinCode) ->
                _isCreateDialogOpen.value = false
                selectGroup(newGroupId)
                onComplete(true, joinCode)
            }.onFailure { error ->
                onComplete(false, error.message ?: "Failed to create group")
            }
        }
    }

    fun joinGroup(username: String, joinCode: String, onComplete: (Boolean, String?) -> Unit) {
        val user = _currentUser.value
        if (user == null) {
            onComplete(false, "You must be signed in to join a group.")
            return
        }

        viewModelScope.launch {
            _isActionLoading.value = true
            val result = repository.joinGroup(
                username = username,
                joinCode = joinCode,
                uid = user.uid,
                rawDisplayName = user.displayName ?: "Student"
            )
            _isActionLoading.value = false
            result.onSuccess { groupId ->
                _isJoinDialogOpen.value = false
                selectGroup(groupId)
                onComplete(true, null)
            }.onFailure { error ->
                onComplete(false, error.message ?: "Failed to join group")
            }
        }
    }

    fun toggleTaskDone(task: GroupTask) {
        val user = _currentUser.value ?: return
        val currentDone = myDoneTaskIds.value.contains(task.id)
        repository.setTaskDone(user.uid, task.groupId, task.id, !currentDone)
    }

    fun saveTask(
        title: String,
        description: String?,
        courseLabel: String?,
        dueDateEpochMillis: Long,
        dueTimeHour: Int,
        dueTimeMinute: Int,
        priority: String
    ) {
        val groupId = _selectedGroupId.value ?: return
        val user = _currentUser.value ?: return
        val existingTask = _taskToEdit.value

        closeAddEditTaskDialog()

        if (existingTask == null) {
            repository.createTask(
                groupId = groupId,
                title = title,
                description = description,
                courseLabel = courseLabel,
                dueDateEpochMillis = dueDateEpochMillis,
                dueTimeHour = dueTimeHour,
                dueTimeMinute = dueTimeMinute,
                priority = priority,
                uid = user.uid,
                rawDisplayName = user.displayName ?: "Student",
                onFailure = { e ->
                    viewModelScope.launch {
                        _snackbarMessage.emit("Failed to create task: ${e.message}")
                    }
                }
            )
        } else {
            repository.updateTask(
                groupId = groupId,
                taskId = existingTask.id,
                title = title,
                description = description,
                courseLabel = courseLabel,
                dueDateEpochMillis = dueDateEpochMillis,
                dueTimeHour = dueTimeHour,
                dueTimeMinute = dueTimeMinute,
                priority = priority,
                onFailure = { e ->
                    viewModelScope.launch {
                        _snackbarMessage.emit("Failed to update task: ${e.message}")
                    }
                }
            )
        }
    }

    fun deleteTask(task: GroupTask) {
        repository.deleteTask(task.groupId, task.id) { e ->
            viewModelScope.launch {
                _snackbarMessage.emit("Failed to delete task: ${e.message}")
            }
        }
    }

    
    fun setMemberRole(member: GroupMember, makeAdmin: Boolean) {
        val groupId = _selectedGroupId.value ?: return
        viewModelScope.launch {
            repository.setMemberRole(groupId, member.uid, if (makeAdmin) "admin" else "member")
                .onSuccess {
                    _snackbarMessage.emit(
                        if (makeAdmin) "${member.displayName} is now an admin"
                        else "${member.displayName} is no longer an admin"
                    )
                }
                .onFailure { e ->
                    _snackbarMessage.emit("Could not change role: ${e.message}")
                }
        }
    }

    fun removeMember(member: GroupMember) {
        val groupId = _selectedGroupId.value ?: return
        viewModelScope.launch {
            repository.removeMember(groupId, member.uid)
                .onSuccess { _snackbarMessage.emit("${member.displayName} was removed") }
                .onFailure { e -> _snackbarMessage.emit("Could not remove member: ${e.message}") }
        }
    }

    fun leaveCurrentGroup(onSuccess: () -> Unit) {
        val user = _currentUser.value ?: return
        val groupId = _selectedGroupId.value ?: return

        viewModelScope.launch {
            _isActionLoading.value = true
            val result = repository.leaveGroup(groupId, user.uid)
            _isActionLoading.value = false
            result.onSuccess {
                clearSelectedGroup()
                onSuccess()
            }.onFailure { e ->
                _snackbarMessage.emit("Failed to leave group: ${e.message}")
            }
        }
    }

    fun deleteCurrentGroup(username: String, onSuccess: () -> Unit) {
        val user = _currentUser.value ?: return
        val groupId = _selectedGroupId.value ?: return

        viewModelScope.launch {
            _isActionLoading.value = true
            val result = repository.deleteGroup(groupId, username, user.uid)
            _isActionLoading.value = false
            result.onSuccess {
                clearSelectedGroup()
                onSuccess()
            }.onFailure { e ->
                _snackbarMessage.emit("Failed to delete group: ${e.message}")
            }
        }
    }
}
