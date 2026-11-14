package com.example.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.groups.GroupCourse
import com.example.data.groups.GroupDetail
import com.example.data.groups.GroupMember
import com.example.data.groups.GroupMembership
import com.example.Repositories
import com.example.data.groups.GroupTask
import com.example.reminder.GroupReminderScheduler
import com.example.util.AppUser
import com.example.util.AuthStorage
import com.example.util.SupabaseProvider
import com.example.util.currentAppUser
import com.example.util.toAppUser
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GroupsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = Repositories.group(application)
    private val _currentUser = MutableStateFlow<AppUser?>(
        currentAppUser() ?: AuthStorage.getLastUid(application)?.let { AppUser(uid = it, email = null, displayName = null) }
    )
    val currentUser: StateFlow<AppUser?> = _currentUser.asStateFlow()

    val sessionReady: StateFlow<Boolean> = SupabaseProvider.client.auth.sessionStatus
        .map { it !is SessionStatus.Initializing }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _selectedGroupId = MutableStateFlow<String?>(null)
    val selectedGroupId: StateFlow<String?> = _selectedGroupId.asStateFlow()

    private val _isCreateDialogOpen = MutableStateFlow(false)
    val isCreateDialogOpen: StateFlow<Boolean> = _isCreateDialogOpen.asStateFlow()

    private val _isJoinDialogOpen = MutableStateFlow(false)
    val isJoinDialogOpen: StateFlow<Boolean> = _isJoinDialogOpen.asStateFlow()
    private val _prefilledJoinInvite = MutableStateFlow<Pair<String, String>?>(null)
    val prefilledJoinInvite: StateFlow<Pair<String, String>?> = _prefilledJoinInvite.asStateFlow()

    private val _isAddEditTaskDialogOpen = MutableStateFlow(false)
    val isAddEditTaskDialogOpen: StateFlow<Boolean> = _isAddEditTaskDialogOpen.asStateFlow()

    private val _taskToEdit = MutableStateFlow<GroupTask?>(null)
    val taskToEdit: StateFlow<GroupTask?> = _taskToEdit.asStateFlow()

    private val _isAddEditCourseDialogOpen = MutableStateFlow(false)
    val isAddEditCourseDialogOpen: StateFlow<Boolean> = _isAddEditCourseDialogOpen.asStateFlow()

    private val _courseToEdit = MutableStateFlow<GroupCourse?>(null)
    val courseToEdit: StateFlow<GroupCourse?> = _courseToEdit.asStateFlow()

    private val _courseSaveError = MutableStateFlow<String?>(null)
    val courseSaveError: StateFlow<String?> = _courseSaveError.asStateFlow()

    fun clearCourseSaveError() {
        _courseSaveError.value = null
    }

    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    /** False while the phone has no working internet. Groups then show the last saved data. */
    val isOnline: StateFlow<Boolean> = repository.observeIsOnline()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val offlineMessage = "You're offline. Connect to the internet and try again."

    /**
     * Group changes (add, edit, delete, roles, join codes) must reach the server, so they cannot run offline.
     * Returns true when it is fine to go on. Otherwise it tells the person why nothing happened.
     */
    private fun requireOnline(): Boolean {
        if (isOnline.value) return true
        _snackbarMessage.tryEmit(offlineMessage)
        return false
    }

    private val _isActionLoading = MutableStateFlow(false)
    val isActionLoading: StateFlow<Boolean> = _isActionLoading.asStateFlow()

    init {
        viewModelScope.launch {
            SupabaseProvider.client.auth.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        val user = status.session.user
                        if (user != null) {
                            AuthStorage.setLastUid(application, user.id)
                            _currentUser.value = user.toAppUser()
                        }
                    }
                    is SessionStatus.NotAuthenticated -> {
                        val savedUid = AuthStorage.getLastUid(application)
                        if (savedUid == null) {
                            // Real explicit sign-out
                            _currentUser.value = null
                            repository.clearCachedMemberships()
                        } else if (_currentUser.value == null) {
                            // Transient status on lock/unlock or network transition: preserve last known user
                            _currentUser.value = currentAppUser() ?: AppUser(uid = savedUid, email = null, displayName = null)
                        }
                    }
                    is SessionStatus.RefreshFailure -> {
                        val savedUid = AuthStorage.getLastUid(application)
                        if (savedUid != null && _currentUser.value == null) {
                            _currentUser.value = currentAppUser() ?: AppUser(uid = savedUid, email = null, displayName = null)
                        }
                    }
                    is SessionStatus.Initializing -> {
                        val savedUid = AuthStorage.getLastUid(application)
                        if (savedUid != null && _currentUser.value == null) {
                            _currentUser.value = currentAppUser() ?: AppUser(uid = savedUid, email = null, displayName = null)
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val myMemberships: StateFlow<List<GroupMembership>> = _currentUser.flatMapLatest { user ->
        val uid = user?.uid ?: AuthStorage.getLastUid(application)
        if (uid != null) {
            repository.observeMyGroupMemberships(uid)
                .onStart { emit(repository.cachedMemberships(uid)) }
        } else {
            flowOf(emptyList())
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        (currentAppUser()?.uid ?: AuthStorage.getLastUid(application))?.let { repository.cachedMemberships(it) } ?: emptyList()
    )

    
    private val reminderScheduler = GroupReminderScheduler(application)

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
    private val allGroupTasks: Flow<List<GroupTask>> = _selectedGroupId.flatMapLatest { groupId ->
        if (!groupId.isNullOrBlank()) {
            repository.observeGroupTasks(groupId)
        } else {
            flowOf(emptyList())
        }
    }

    /** Tasks that were just deleted and can still be brought back with Undo. They are hidden from the list. */
    private val hiddenTaskIds = MutableStateFlow<Set<String>>(emptySet())

    val selectedGroupTasks: StateFlow<List<GroupTask>> = combine(allGroupTasks, hiddenTaskIds) { tasks, hidden ->
        if (hidden.isEmpty()) tasks else tasks.filter { it.id !in hidden }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedGroupCourses: StateFlow<List<GroupCourse>> = _selectedGroupId.flatMapLatest { groupId ->
        if (!groupId.isNullOrBlank()) {
            repository.observeGroupCourses(groupId)
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

    
    // Deadline alarms and "new task" notices for all groups are handled app-wide by
    // MainViewModel (see GroupTaskProcessor). Here we only cancel alarms right after you
    // delete a task or leave/delete a group, so nothing fires in the gap before the next update.

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

    /** Called when the app notices you are not a member of the open group any more (kicked or left elsewhere). */
    fun onRemovedFromGroup(groupId: String) {
        reminderScheduler.cancelGroup(groupId)
        if (_selectedGroupId.value == groupId) {
            _selectedGroupId.value = null
        }
        viewModelScope.launch {
            _snackbarMessage.emit("You are no longer a member of that group.")
        }
    }
    fun openCreateDialog() {
        _isCreateDialogOpen.value = true
    }

    fun closeCreateDialog() {
        _isCreateDialogOpen.value = false
    }

    fun openJoinDialog(username: String = "", joinCode: String = "") {
        _prefilledJoinInvite.value = if (username.isNotBlank() && joinCode.isNotBlank()) username to joinCode else null
        _isJoinDialogOpen.value = true
    }

    fun closeJoinDialog() {
        _isJoinDialogOpen.value = false
        _prefilledJoinInvite.value = null
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

    fun openAddCourseDialog() {
        _courseSaveError.value = null
        _courseToEdit.value = null
        _isAddEditCourseDialogOpen.value = true
    }

    fun openEditCourseDialog(course: GroupCourse) {
        _courseSaveError.value = null
        _courseToEdit.value = course
        _isAddEditCourseDialogOpen.value = true
    }

    fun closeAddEditCourseDialog() {
        _courseSaveError.value = null
        _isAddEditCourseDialogOpen.value = false
        _courseToEdit.value = null
    }

    fun saveCourse(code: String, name: String, instructor: String, colorHex: String) {
        if (!requireOnline()) return
        val groupId = _selectedGroupId.value ?: return
        val user = _currentUser.value ?: return
        val existingCourse = _courseToEdit.value

        val currentCourses = selectedGroupCourses.value
        val otherCourses = if (existingCourse != null) {
            currentCourses.filter { it.id != existingCourse.id }
        } else {
            currentCourses
        }

        val targetKey = normalizeCourseKey(code)
        val duplicateByCode = otherCourses.firstOrNull { normalizeCourseKey(it.code) == targetKey }
        if (duplicateByCode != null) {
            _courseSaveError.value = "This group already has a course with the code ${duplicateByCode.code} (${duplicateByCode.name})."
            return
        }

        val targetTrimmedName = name.trim()
        val duplicateByName = otherCourses.firstOrNull { it.name.trim().equals(targetTrimmedName, ignoreCase = true) }
        if (duplicateByName != null) {
            _courseSaveError.value = "This group already has a course named ${duplicateByName.name}."
            return
        }

        _courseSaveError.value = null
        closeAddEditCourseDialog()

        if (existingCourse == null) {
            repository.createGroupCourse(
                groupId = groupId,
                code = code,
                name = name,
                instructor = instructor,
                colorHex = colorHex,
                uid = user.uid,
                onFailure = { e ->
                    viewModelScope.launch {
                        val msg = if (e.message?.contains("This group already has a course with that code.") == true) {
                            "This group already has a course with that code."
                        } else {
                            "Failed to create course: ${e.message}"
                        }
                        _snackbarMessage.emit(msg)
                    }
                }
            )
        } else {
            repository.updateGroupCourse(
                groupId = groupId,
                courseId = existingCourse.id,
                code = code,
                name = name,
                instructor = instructor,
                colorHex = colorHex,
                onFailure = { e ->
                    viewModelScope.launch {
                        val msg = if (e.message?.contains("This group already has a course with that code.") == true) {
                            "This group already has a course with that code."
                        } else {
                            "Failed to update course: ${e.message}"
                        }
                        _snackbarMessage.emit(msg)
                    }
                }
            )
        }
    }

    fun deleteCourse(course: GroupCourse) {
        if (!requireOnline()) return
        repository.deleteGroupCourse(course.groupId, course.id) { e ->
            viewModelScope.launch {
                _snackbarMessage.emit("Failed to delete course: ${e.message}")
            }
        }
    }

    fun createGroup(name: String, username: String, onComplete: (Boolean, String?) -> Unit) {
        if (!isOnline.value) { onComplete(false, offlineMessage); return }
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
        if (!isOnline.value) { onComplete(false, offlineMessage); return }
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
        priority: String,
        reminderOptions: String,
        assignedTo: List<String>
    ) {
        if (!requireOnline()) return
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
                reminderOptions = reminderOptions,
                assignedTo = assignedTo,
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
                reminderOptions = reminderOptions,
                assignedTo = assignedTo,
                onFailure = { e ->
                    viewModelScope.launch {
                        _snackbarMessage.emit("Failed to update task: ${e.message}")
                    }
                }
            )
        }
    }

    /** A deleted task waiting out its Undo time: the task and the timer that will really delete it. */
    private class PendingDelete(val task: GroupTask, val timer: Job)

    private val pendingDeletes = HashMap<String, PendingDelete>()

    /**
     * Deletes a task, with a few seconds to change your mind. The task disappears from the list at once,
     * but it is only deleted on the server when [UNDO_WINDOW_MILLIS] has passed. Undo just shows it again.
     * (Creating the task again instead would give it a new id, lose everyone's done marks and tell the
     * whole group about a "new" task.)
     */
    fun deleteTask(task: GroupTask) {
        if (!requireOnline()) return
        if (pendingDeletes.containsKey(task.id)) return
        hiddenTaskIds.update { it + task.id }
        val timer = viewModelScope.launch {
            delay(UNDO_WINDOW_MILLIS)
            commitDelete(task.id)
        }
        pendingDeletes[task.id] = PendingDelete(task, timer)
        com.example.ui.AppMessages.show(
            message = "Task deleted",
            actionLabel = "Undo",
            onAction = { undoDelete(task.id) }
        )
    }

    private fun undoDelete(taskId: String) {
        // Too late (already deleted on the server) means there is nothing left to bring back.
        val pending = pendingDeletes.remove(taskId) ?: return
        pending.timer.cancel()
        hiddenTaskIds.update { it - taskId }
    }

    /** Deletes on the server for real. Safe to call twice: the second call finds nothing waiting. */
    private fun commitDelete(taskId: String) {
        val pending = pendingDeletes.remove(taskId) ?: return
        val task = pending.task
        reminderScheduler.cancel(task.groupId, task.id)
        repository.deleteTask(task.groupId, task.id) { e ->
            // The server refused: show the task again so the list matches what is really there.
            hiddenTaskIds.update { it - taskId }
            viewModelScope.launch {
                _snackbarMessage.emit("Failed to delete task: ${e.message}")
            }
        }
    }

    /** Closing the app must not undo a delete: whatever is still waiting is deleted now. */
    override fun onCleared() {
        pendingDeletes.keys.toList().forEach { commitDelete(it) }
        super.onCleared()
    }

    
    fun setMemberRole(member: GroupMember, makeAdmin: Boolean) {
        if (!requireOnline()) return
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

    fun transferOwnership(newOwnerUid: String) {
        if (!requireOnline()) return
        val groupId = _selectedGroupId.value ?: return
        viewModelScope.launch {
            repository.transferOwnership(groupId, newOwnerUid)
                .onSuccess {
                    _snackbarMessage.emit("Ownership transferred successfully")
                }
                .onFailure { e ->
                    _snackbarMessage.emit("Failed to transfer ownership: ${e.message}")
                }
        }
    }

    fun removeMember(member: GroupMember) {
        if (!requireOnline()) return
        val groupId = _selectedGroupId.value ?: return
        viewModelScope.launch {
            repository.removeMember(groupId, member.uid)
                .onSuccess { _snackbarMessage.emit("${member.displayName} was removed") }
                .onFailure { e -> _snackbarMessage.emit("Could not remove member: ${e.message}") }
        }
    }

    fun leaveCurrentGroup(onSuccess: () -> Unit) {
        if (!requireOnline()) return
        val user = _currentUser.value ?: return
        val groupId = _selectedGroupId.value ?: return

        viewModelScope.launch {
            _isActionLoading.value = true
            val result = repository.leaveGroup(groupId, user.uid)
            _isActionLoading.value = false
            result.onSuccess {
                clearSelectedGroup()
                reminderScheduler.cancelGroup(groupId)
                onSuccess()
            }.onFailure { e ->
                _snackbarMessage.emit("Failed to leave group: ${e.message}")
            }
        }
    }

    fun deleteCurrentGroup(username: String, onSuccess: () -> Unit) {
        if (!requireOnline()) return
        val user = _currentUser.value ?: return
        val groupId = _selectedGroupId.value ?: return

        viewModelScope.launch {
            _isActionLoading.value = true
            val result = repository.deleteGroup(groupId, username, user.uid)
            _isActionLoading.value = false
            result.onSuccess {
                clearSelectedGroup()
                reminderScheduler.cancelGroup(groupId)
                onSuccess()
            }.onFailure { e ->
                _snackbarMessage.emit("Failed to delete group: ${e.message}")
            }
        }
    }

    fun updateGroupProfile(newName: String, newUsername: String, onComplete: (Boolean, String?) -> Unit) {
        if (!isOnline.value) { onComplete(false, offlineMessage); return }
        val groupId = _selectedGroupId.value ?: return
        viewModelScope.launch {
            _isActionLoading.value = true
            val result = repository.updateGroupProfile(groupId, newName, newUsername)
            _isActionLoading.value = false
            result.onSuccess {
                _snackbarMessage.emit("Group settings updated successfully")
                onComplete(true, null)
            }.onFailure { e ->
                onComplete(false, e.message ?: "Failed to update group")
            }
        }
    }

    fun regenerateGroupCode(onComplete: (Boolean, String?) -> Unit) {
        if (!isOnline.value) { onComplete(false, offlineMessage); return }
        val groupId = _selectedGroupId.value ?: return
        viewModelScope.launch {
            _isActionLoading.value = true
            val result = repository.regenerateGroupCode(groupId)
            _isActionLoading.value = false
            result.onSuccess { newCode ->
                _snackbarMessage.emit("New join code: $newCode")
                onComplete(true, newCode)
            }.onFailure { e ->
                onComplete(false, e.message ?: "Failed to regenerate join code")
            }
        }
    }

    private companion object {
        /** How long a deleted task can be brought back. Matches how long the Undo message stays on screen. */
        const val UNDO_WINDOW_MILLIS = 10_000L
    }
}
