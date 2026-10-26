package com.example.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.groups.ChatTail
import com.example.data.groups.GroupChatInfo
import com.example.data.groups.GroupChatTracker
import com.example.data.groups.GroupRepository
import com.example.data.groups.GroupTaskEntry
import com.example.data.groups.GroupsState
import com.example.data.local.AppDatabase
import com.example.data.local.Course
import com.example.data.local.CourseWithTaskCount
import com.example.data.local.Priority
import com.example.data.local.ReminderOption
import com.example.data.local.TaskItem
import com.example.data.local.TaskStatus
import com.example.data.local.TaskWithCourse
import com.example.data.repository.AppRepository
import com.example.reminder.GroupChatNotifier
import com.example.reminder.GroupReminderScheduler
import com.example.reminder.GroupTaskProcessor
import com.example.reminder.ReminderScheduler
import com.example.reminder.ClassReminderScheduler
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.example.util.safeAuth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import com.example.data.local.LectureSlot
import com.example.data.local.LectureSlotWithCourse
import com.example.data.local.TaskAttachment
import com.example.util.SavedAttachmentInfo
import java.util.Calendar
import com.example.data.local.deadlineMillis
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import com.example.sync.FirestoreSyncManager

enum class MainTab {
    DEADLINES,
    TIMETABLE,
    COURSES,
    GROUPS
}

enum class TaskSortOption(val label: String) {
    DUE_DATE_ASC("Due Date (Earliest)"),
    DUE_DATE_DESC("Due Date (Latest)"),
    PRIORITY_HIGH_FIRST("Priority (High to Low)"),
    TITLE_ASC("Title (A-Z)")
}

// Classes that started more than this many minutes ago are hidden from the schedule.
private const val STALE_CLASS_MINUTES = 20

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AppRepository

    // Ticks every minute so the class schedule drops finished classes without a data change.
    private val _clockTick = MutableStateFlow(System.currentTimeMillis())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                delay(60_000L)
                _clockTick.value = System.currentTimeMillis()
            }
        }
    }

    private val _currentUser = MutableStateFlow<FirebaseUser?>(safeAuth()?.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _isCloudBackupSheetOpen = MutableStateFlow(false)
    val isCloudBackupSheetOpen: StateFlow<Boolean> = _isCloudBackupSheetOpen.asStateFlow()
    private lateinit var authStateListener: FirebaseAuth.AuthStateListener
    init {
        val database = AppDatabase.getDatabase(application)
        val scheduler = ReminderScheduler(application)
        repository = AppRepository(
            context = application,
            courseDao = database.courseDao(),
            taskDao = database.taskDao(),
            taskAttachmentDao = database.taskAttachmentDao(),
            lectureSlotDao = database.lectureSlotDao(),
            reminderScheduler = scheduler
        )
        // Alarms can be lost (force stop, battery cleaners): set the class-start,
        // personal task, and group task alarms again every time the app opens.
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ClassReminderScheduler(application).rescheduleAll(database)
            } catch (_: Exception) {
            }
            try {
                ReminderScheduler(application).rescheduleAll(database)
            } catch (_: Exception) {
            }
            try {
                GroupReminderScheduler(application).rescheduleAll()
            } catch (_: Exception) {
            }
            try {
                com.example.reminder.DailySummaryScheduler(application).reschedule()
            } catch (_: Exception) {
            }
        }
        // Schedule periodic sync
        com.example.sync.SyncScheduler.schedulePeriodicSync(application)
        // Group deadline alarms and "new group task" notices while the app is closed
        com.example.sync.SyncScheduler.scheduleGroupSync(application)

        // Listen for auth state changes and attach real-time guarded sync
        authStateListener = FirebaseAuth.AuthStateListener { auth: FirebaseAuth ->
            _currentUser.value = auth.currentUser
            if (auth.currentUser != null) {
                com.example.sync.FirestoreSyncManager.startRealtimeSync(application, database)
                viewModelScope.launch(Dispatchers.IO) {
                    com.example.sync.FirestoreSyncManager.syncAllDataNow(application, database)
                }
                com.example.sync.SyncScheduler.scheduleSync(application)
            } else {
                com.example.sync.FirestoreSyncManager.stopRealtimeSync()
                                // Signed out: mark everything as not yet backed up, so the next account starts
                // from a full upload instead of treating old cloud ids as deleted elsewhere.
                viewModelScope.launch(Dispatchers.IO) {
                    database.courseDao().markAllCoursesUnsynced()
                    database.taskDao().markAllTasksUnsynced()
                    database.lectureSlotDao().markAllSlotsUnsynced()
                }
            }
        }
        safeAuth()?.addAuthStateListener(authStateListener)


    }

    override fun onCleared() {
        super.onCleared()
        com.example.sync.FirestoreSyncManager.stopRealtimeSync()
        safeAuth()?.removeAuthStateListener(authStateListener)
    }

    fun openCloudBackupSheet() {
        _isCloudBackupSheetOpen.value = true
    }

    fun closeCloudBackupSheet() {
        _isCloudBackupSheetOpen.value = false
    }

    /**
     * Pull to refresh. Local data is live from Room, so this re-evaluates the class schedule
     * and, when signed in, starts a cloud sync.
     */
    fun refreshData() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _clockTick.value = System.currentTimeMillis()
            if (safeAuth()?.currentUser != null) {
                val database = AppDatabase.getDatabase(getApplication())
                viewModelScope.launch(Dispatchers.IO) {
                    com.example.sync.FirestoreSyncManager.syncAllDataNow(getApplication(), database)
                }
                com.example.sync.SyncScheduler.scheduleSync(getApplication())
            }
            delay(500L)
            _isRefreshing.value = false
        }
    }

    fun refreshAuth() {
        _currentUser.value = safeAuth()?.currentUser
        if (_currentUser.value != null) {
            val database = AppDatabase.getDatabase(getApplication())
            com.example.sync.FirestoreSyncManager.startRealtimeSync(getApplication(), database)
            viewModelScope.launch(Dispatchers.IO) {
                com.example.sync.FirestoreSyncManager.syncAllDataNow(getApplication(), database)
            }
            com.example.sync.SyncScheduler.scheduleSync(getApplication())
        }
    }
        /** Sets every personal and group reminder again, for example after exact alarms were allowed. */
    fun rescheduleAllReminders() {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            ReminderScheduler(app).rescheduleAll(AppDatabase.getDatabase(app))
            com.example.reminder.GroupReminderScheduler(app).rescheduleAll()
            ClassReminderScheduler(app).rescheduleAll(AppDatabase.getDatabase(app))
        }
    }

    // Tab state
    private val _currentTab = MutableStateFlow(MainTab.DEADLINES)
    val currentTab: StateFlow<MainTab> = _currentTab.asStateFlow()

    // Filter and Sort states
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _statusFilter = MutableStateFlow<TaskStatus?>(null)
    val statusFilter: StateFlow<TaskStatus?> = _statusFilter.asStateFlow()

    private val _courseFilter = MutableStateFlow<Long?>(null)
    val courseFilter: StateFlow<Long?> = _courseFilter.asStateFlow()

    private val _sortOption = MutableStateFlow(TaskSortOption.DUE_DATE_ASC)
    val sortOption: StateFlow<TaskSortOption> = _sortOption.asStateFlow()

    // Dialog & Detail states
    private val _isAddEditTaskDialogOpen = MutableStateFlow(false)
    val isAddEditTaskDialogOpen: StateFlow<Boolean> = _isAddEditTaskDialogOpen.asStateFlow()

    private val _taskToEdit = MutableStateFlow<TaskItem?>(null)
    val taskToEdit: StateFlow<TaskItem?> = _taskToEdit.asStateFlow()

    private val _taskAttachmentsToEdit = MutableStateFlow<List<TaskAttachment>>(emptyList())
    val taskAttachmentsToEdit: StateFlow<List<TaskAttachment>> = _taskAttachmentsToEdit.asStateFlow()

    private val _preselectedCourseId = MutableStateFlow<Long?>(null)
    val preselectedCourseId: StateFlow<Long?> = _preselectedCourseId.asStateFlow()

    private val _isAddEditCourseDialogOpen = MutableStateFlow(false)
    val isAddEditCourseDialogOpen: StateFlow<Boolean> = _isAddEditCourseDialogOpen.asStateFlow()

    private val _courseToEdit = MutableStateFlow<Course?>(null)
    val courseToEdit: StateFlow<Course?> = _courseToEdit.asStateFlow()
    
    // Set when the course being saved has the same code as another course.
    private val _courseSaveError = MutableStateFlow<String?>(null)
    val courseSaveError: StateFlow<String?> = _courseSaveError.asStateFlow()


    private val _selectedCourseDetailId = MutableStateFlow<Long?>(null)
    val selectedCourseDetailId: StateFlow<Long?> = _selectedCourseDetailId.asStateFlow()

    // Lecture Slot Dialog state
    private val _isAddEditSlotDialogOpen = MutableStateFlow(false)
    val isAddEditSlotDialogOpen: StateFlow<Boolean> = _isAddEditSlotDialogOpen.asStateFlow()

    private val _slotToEdit = MutableStateFlow<LectureSlot?>(null)
    val slotToEdit: StateFlow<LectureSlot?> = _slotToEdit.asStateFlow()

    private val _preselectedCourseIdForSlot = MutableStateFlow<Long?>(null)
    val preselectedCourseIdForSlot: StateFlow<Long?> = _preselectedCourseIdForSlot.asStateFlow()
    
    // Day (1 = Monday ... 7 = Sunday) shown on the Timetable tab. New classes start on this day.
    private val _timetableSelectedDay = MutableStateFlow((java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7 + 1)
    private val _preselectedDayForSlot = MutableStateFlow(1)
    val preselectedDayForSlot: StateFlow<Int> = _preselectedDayForSlot.asStateFlow()

    fun setTimetableSelectedDay(day: Int) {
        _timetableSelectedDay.value = day
    }


    // Courses & Tasks streams
    val allCourses: StateFlow<List<Course>> = repository.allCourses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val coursesWithCounts: StateFlow<List<CourseWithTaskCount>> = repository.coursesWithTaskCounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTasksWithCourse: StateFlow<List<TaskWithCourse>> = repository.allTasksWithCourse
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // False until Room has answered once, so the Deadlines tab does not flash its empty message.
    val tasksLoaded: StateFlow<Boolean> = repository.allTasksWithCourse
        .map { true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val allLectureSlots: StateFlow<List<LectureSlotWithCourse>> = repository.allLectureSlots
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---- Group tasks shown as personal tasks on the Deadlines tab ----
    // This lives here (not in GroupsViewModel) because MainViewModel starts with the app, so group
    // alarms and notifications work without ever opening the Groups tab.
    private val groupRepository = GroupRepository(application)
    private val groupTaskProcessor = GroupTaskProcessor(application)

    // null until the first answer arrives, so "still loading" is never mistaken for "no groups".
    @OptIn(ExperimentalCoroutinesApi::class)
    private val groupsState: StateFlow<GroupsState?> = _currentUser.flatMapLatest { user ->
        if (user == null) flowOf(GroupsState(emptySet(), emptyList()))
        else groupRepository.observeGroupsState(user.uid)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            groupsState.filterNotNull().collect { state ->
                val uid = _currentUser.value?.uid
                if (uid == null) groupTaskProcessor.clearAll()
                else groupTaskProcessor.process(uid, state)
            }
        }
    }

    private val groupEntries: StateFlow<List<GroupTaskEntry>> = groupsState.map { state ->
        state?.loaded.orEmpty().flatMap { snapshot ->
            snapshot.tasks.map { GroupTaskEntry(it, snapshot.groupName, it.id in snapshot.done, snapshot.courses) }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Lookup used by the Deadlines tab to show "added by" and to route clicks of group tasks. */
    val groupEntryByItemId: StateFlow<Map<Long, GroupTaskEntry>> = groupEntries.map { list ->
        list.associateBy { groupTaskItemId(it.task.groupId, it.task.id) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    // Personal tasks plus group tasks: everything the Deadlines tab lists.
    private val allDeadlineTasks: StateFlow<List<TaskWithCourse>> = combine(
        repository.allTasksWithCourse,
        groupEntries
    ) { personal, groups ->
        personal + groups.map { it.toTaskWithCourse() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Set when a group task is tapped on the Deadlines tab; the Groups tab opens that group.
    private val _groupToOpen = MutableStateFlow<String?>(null)
    val groupToOpen: StateFlow<String?> = _groupToOpen.asStateFlow()

    private val _groupChatToOpen = MutableStateFlow<String?>(null)
    val groupChatToOpen: StateFlow<String?> = _groupChatToOpen.asStateFlow()

    fun openGroup(groupId: String, openChat: Boolean = false) {
        _groupToOpen.value = groupId
        if (openChat) {
            _groupChatToOpen.value = groupId
        }
        _currentTab.value = MainTab.GROUPS
    }

    fun onGroupOpened() {
        _groupToOpen.value = null
        _groupChatToOpen.value = null
    }

    // Class schedule shown on the Deadlines tab. Re-evaluated every minute and on pull to refresh.
    // Today's classes that have not ended and started no more than STALE_CLASS_MINUTES ago are
    // shown. When nothing is left today, the full schedule of the next day with classes is shown.
    val highlightedClassInfo: StateFlow<List<HighlightedClassInfo>> = combine(
        repository.allLectureSlots,
        repository.allTasksWithCourse,
        _clockTick
    ) { slots, tasks, _ ->
        buildClassSchedule(slots, tasks)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun buildClassSchedule(
        slots: List<LectureSlotWithCourse>,
        tasks: List<TaskWithCourse>
    ): List<HighlightedClassInfo> {
        if (slots.isEmpty()) return emptyList()

        val dayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        val cal = Calendar.getInstance()
        val todayDay = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }
        val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        fun startOf(s: LectureSlotWithCourse) = s.slot.startHour * 60 + s.slot.startMinute
        fun endOf(s: LectureSlotWithCourse) = s.slot.endHour * 60 + s.slot.endMinute
        fun pendingFor(s: LectureSlotWithCourse) =
            tasks.filter { it.task.courseId == s.slot.courseId && it.task.status != TaskStatus.COMPLETED }
        fun slotsOn(day: Int) = slots.filter { it.slot.dayOfWeek == day }.sortedBy { startOf(it) }
        fun startTimeText(s: LectureSlotWithCourse) =
            com.example.util.TimeFormatter.format12Hour(s.slot.startHour, s.slot.startMinute)

        val visibleToday = slotsOn(todayDay).filter {
            currentMinutes < endOf(it) && currentMinutes - startOf(it) <= STALE_CLASS_MINUTES
        }
        if (visibleToday.isNotEmpty()) {
            return visibleToday.map { s ->
                if (currentMinutes >= startOf(s)) {
                    HighlightedClassInfo(
                        slotWithCourse = s,
                        isActiveNow = true,
                        timeLabel = "Ends in ${endOf(s) - currentMinutes} min",
                        pendingTasks = pendingFor(s)
                    )
                } else {
                    val minUntil = startOf(s) - currentMinutes
                    HighlightedClassInfo(
                        slotWithCourse = s,
                        isActiveNow = false,
                        timeLabel = if (minUntil < 60) "Starts in $minUntil min" else "Starts at ${startTimeText(s)}",
                        pendingTasks = pendingFor(s)
                    )
                }
            }
        }

        // Nothing left today: show the next day that has classes (offset 7 is the same weekday next week).
        for (dayOffset in 1..7) {
            val day = ((todayDay - 1 + dayOffset) % 7) + 1
            val daySlots = slotsOn(day)
            if (daySlots.isNotEmpty()) {
                return daySlots.map { s ->
                    HighlightedClassInfo(
                        slotWithCourse = s,
                        isActiveNow = false,
                        timeLabel = "${dayNames[day - 1]} at ${startTimeText(s)}",
                        pendingTasks = pendingFor(s)
                    )
                }
            }
        }
        return emptyList()
    }

    // Filtered & Sorted Tasks
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    val filteredTasks: StateFlow<List<TaskWithCourse>> = combine(
        allDeadlineTasks,
        _searchQuery.debounce { if (it.isEmpty()) 0L else 200L },
        _statusFilter,
        _courseFilter,
        _sortOption
    ) { tasks, query, status, courseId, sort ->
        val selectedCourse = courseId?.let { cid -> allCourses.value.find { it.id == cid } }
        var list = tasks

        // Search query filter
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            list = list.filter {
                it.task.title.lowercase().contains(q) ||
                        it.task.description.lowercase().contains(q) ||
                        it.course?.name?.lowercase()?.contains(q) == true ||
                        it.course?.code?.lowercase()?.contains(q) == true
            }
        }

        // Status filter
        if (status != null) {
            list = list.filter { it.task.status == status }
        }

        // Course filter: matches personal tasks as well as group tasks belonging to the same course
        if (courseId != null) {
            val normSelectedCode = selectedCourse?.code?.let { normalizeCourseKey(it) }?.takeIf { it.isNotEmpty() }
            val normSelectedName = selectedCourse?.name?.trim()?.lowercase()

            list = list.filter { twc ->
                // Direct match on personal task course id
                if (twc.task.courseId == courseId) return@filter true

                // Match by normalized course code (e.g. CS-503 vs CS503)
                if (normSelectedCode != null) {
                    val taskCourseCode = twc.course?.code?.let { normalizeCourseKey(it) }
                    if (taskCourseCode == normSelectedCode) return@filter true
                }

                // Match by course name if available
                if (!normSelectedName.isNullOrBlank()) {
                    val taskCourseName = twc.course?.name?.trim()?.lowercase()
                    if (taskCourseName == normSelectedName) return@filter true
                }

                false
            }
        }

        // Sorting
                // Sort by the real deadline (date plus chosen time), worked out once per task.
        val withDeadline = list.map { it to it.task.deadlineMillis }
        when (sort) {
            TaskSortOption.DUE_DATE_ASC -> withDeadline.sortedBy { it.second }.map { it.first }
            TaskSortOption.DUE_DATE_DESC -> withDeadline.sortedByDescending { it.second }.map { it.first }
            TaskSortOption.PRIORITY_HIGH_FIRST -> withDeadline
                .sortedWith(
                    compareByDescending<Pair<TaskWithCourse, Long>> { it.first.task.priority.ordinal }
                        .thenBy { it.second }
                )
                .map { it.first }
            TaskSortOption.TITLE_ASC -> list.sortedBy { it.task.title.lowercase() }
        }

       }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Quick stats
    val pendingCount: StateFlow<Int> = allDeadlineTasks.map { tasks ->
        tasks.count { it.task.status != TaskStatus.COMPLETED }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val completedCount: StateFlow<Int> = allDeadlineTasks.map { tasks ->
        tasks.count { it.task.status == TaskStatus.COMPLETED }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Actions
    fun setTab(tab: MainTab) {
        _currentTab.value = tab
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onStatusFilterSelect(status: TaskStatus?) {
        _statusFilter.value = if (_statusFilter.value == status) null else status
    }

    fun onCourseFilterSelect(courseId: Long?) {
        _courseFilter.value = if (_courseFilter.value == courseId) null else courseId
    }

    fun onSortOptionSelect(sort: TaskSortOption) {
        _sortOption.value = sort
    }

    fun cycleTaskStatus(task: TaskItem) {
        if (isGroupTaskItem(task)) {
            // Group tasks: "done" is your own mark in the cloud, other members are not affected.
            val entry = groupEntryByItemId.value[task.id] ?: return
            val uid = _currentUser.value?.uid ?: return
            groupRepository.setTaskDone(uid, entry.task.groupId, entry.task.id, !entry.isDone)
            return
        }
        viewModelScope.launch {
            val nextStatus = when (task.status) {
                TaskStatus.PENDING -> TaskStatus.COMPLETED
                TaskStatus.COMPLETED -> TaskStatus.PENDING
            }
            repository.updateTaskStatus(task.id, nextStatus)
        }
    }

    fun setTaskStatus(task: TaskItem, newStatus: TaskStatus) {
        if (isGroupTaskItem(task)) {
            val entry = groupEntryByItemId.value[task.id] ?: return
            val uid = _currentUser.value?.uid ?: return
            groupRepository.setTaskDone(uid, entry.task.groupId, entry.task.id, newStatus == TaskStatus.COMPLETED)
            return
        }
        viewModelScope.launch {
            repository.updateTaskStatus(task.id, newStatus)
        }
    }

    fun openAddTaskDialog(courseId: Long? = null) {
        _taskToEdit.value = null
        _taskAttachmentsToEdit.value = emptyList()
        _preselectedCourseId.value = courseId
        _isAddEditTaskDialogOpen.value = true
    }

    fun openEditTaskDialog(task: TaskItem) {
        if (isGroupTaskItem(task)) {
            // Group tasks are edited inside their group.
            groupEntryByItemId.value[task.id]?.let { openGroup(it.task.groupId) }
            return
        }
        _taskToEdit.value = task
        _taskAttachmentsToEdit.value = allTasksWithCourse.value.find { it.task.id == task.id }?.attachments ?: emptyList()
        _preselectedCourseId.value = task.courseId
        _isAddEditTaskDialogOpen.value = true
    }

    fun closeTaskDialog() {
        _isAddEditTaskDialogOpen.value = false
        _taskToEdit.value = null
        _taskAttachmentsToEdit.value = emptyList()
        _preselectedCourseId.value = null
    }

    fun saveTask(
        title: String,
        description: String,
        courseId: Long,
        dueDateMillis: Long,
        dueHour: Int,
        dueMinute: Int,
        priority: Priority,
        status: TaskStatus,
        reminderOptions: Set<ReminderOption>,
        newAttachments: List<SavedAttachmentInfo> = emptyList()
    ) {
                // Take what we need and close the dialog first, so a second tap cannot save twice.
        val currentTask = _taskToEdit.value
        closeTaskDialog()
        viewModelScope.launch {
            val targetTaskId: Long
            if (currentTask != null) {
                val updated = currentTask.copy(
                    title = title.trim(),
                    description = description.trim(),
                    courseId = courseId,
                    dueDateEpochMillis = dueDateMillis,
                    dueTimeHour = dueHour,
                    dueTimeMinute = dueMinute,
                    priority = priority,
                    status = status,
                    reminderOptions = reminderOptions
                )
                repository.updateTask(updated)
                targetTaskId = currentTask.id
            } else {
                val newTask = TaskItem(
                    title = title.trim(),
                    description = description.trim(),
                    courseId = courseId,
                    dueDateEpochMillis = dueDateMillis,
                    dueTimeHour = dueHour,
                    dueTimeMinute = dueMinute,
                    priority = priority,
                    status = status,
                    reminderOptions = reminderOptions
                )
                targetTaskId = repository.insertTask(newTask)
            }

            // Insert newly attached files
            for (att in newAttachments) {
                val attachmentEntity = TaskAttachment(
                    taskId = targetTaskId,
                    fileName = att.fileName,
                    relativePath = att.relativePath,
                    fileSize = att.fileSize,
                    mimeType = att.mimeType
                )
                repository.insertAttachment(attachmentEntity)
            }

        }
    }

    fun deleteTask(task: TaskItem) {
        if (isGroupTaskItem(task)) return
        viewModelScope.launch {
            repository.deleteTask(task)
        }
    }

    fun deleteTaskAttachment(attachment: TaskAttachment) {
        viewModelScope.launch {
            repository.deleteAttachment(attachment)
            _taskAttachmentsToEdit.value = _taskAttachmentsToEdit.value.filter { it.id != attachment.id }
        }
    }

    // Lecture Slot CRUD
    fun openAddSlotDialog(courseId: Long? = null) {
        _slotToEdit.value = null
        _preselectedCourseIdForSlot.value = courseId
        _preselectedDayForSlot.value = _timetableSelectedDay.value
        _isAddEditSlotDialogOpen.value = true
    }

    fun openEditSlotDialog(slot: LectureSlot) {
        _slotToEdit.value = slot
        _preselectedCourseIdForSlot.value = slot.courseId
        _isAddEditSlotDialogOpen.value = true
    }

    fun closeSlotDialog() {
        _isAddEditSlotDialogOpen.value = false
        _slotToEdit.value = null
        _preselectedCourseIdForSlot.value = null
    }

    fun saveLectureSlot(
        courseId: Long,
        dayOfWeek: Int,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        classroom: String,
        instructor: String
    ) {
                // Take what we need and close the dialog first, so a second tap cannot save twice.
        val currentSlot = _slotToEdit.value
        closeSlotDialog()
        viewModelScope.launch {
            if (currentSlot != null) {
                val updated = currentSlot.copy(
                    courseId = courseId,
                    dayOfWeek = dayOfWeek,
                    startHour = startHour,
                    startMinute = startMinute,
                    endHour = endHour,
                    endMinute = endMinute,
                    classroom = classroom.trim(),
                    instructor = instructor.trim()
                )
                repository.updateLectureSlot(updated)
            } else {
                val newSlot = LectureSlot(
                    courseId = courseId,
                    dayOfWeek = dayOfWeek,
                    startHour = startHour,
                    startMinute = startMinute,
                    endHour = endHour,
                    endMinute = endMinute,
                    classroom = classroom.trim(),
                    instructor = instructor.trim()
                )
                repository.insertLectureSlot(newSlot)
            }
        }
    }

    fun deleteLectureSlot(slot: LectureSlot) {
        viewModelScope.launch {
            repository.deleteLectureSlot(slot)
        }
    }

    fun openAddCourseDialog() {
        _courseSaveError.value = null
        _courseToEdit.value = null
        _isAddEditCourseDialogOpen.value = true
    }

    fun openEditCourseDialog(course: Course) {
        _courseSaveError.value = null
        _courseToEdit.value = course
        _isAddEditCourseDialogOpen.value = true
    }

    fun closeCourseDialog() {
        _courseSaveError.value = null
        _isAddEditCourseDialogOpen.value = false
        _courseToEdit.value = null
    }


    fun saveCourse(
        name: String,
        code: String,
        instructor: String,
        colorHex: String,
        location: String
    ) {
        viewModelScope.launch {
            val currentCourse = _courseToEdit.value
            // Refuse a code that already belongs to another course (instead of overwriting it).
            val duplicate = repository.findActiveCourseByCode(code, currentCourse?.id ?: 0L)
            if (duplicate != null) {
                _courseSaveError.value = "A course with this code already exists: ${duplicate.code} (${duplicate.name})"
                return@launch
            }
            if (currentCourse != null) {
                val updated = currentCourse.copy(
                    name = name.trim(),
                    code = code.trim().uppercase(),
                    instructor = instructor.trim(),
                    colorHex = colorHex,
                    location = location.trim()
                )
                repository.updateCourse(updated)
            } else {
                val newCourse = Course(
                    name = name.trim(),
                    code = code.trim().uppercase(),
                    instructor = instructor.trim(),
                    colorHex = colorHex,
                    location = location.trim()
                )
                repository.insertCourse(newCourse)
            }
            closeCourseDialog()
        }
    }

    fun clearCourseSaveError() {
        _courseSaveError.value = null
    }


    fun deleteCourse(course: Course) {
        viewModelScope.launch {
            if (_selectedCourseDetailId.value == course.id) {
                _selectedCourseDetailId.value = null
            }
            if (_courseFilter.value == course.id) {
                _courseFilter.value = null
            }
            repository.deleteCourse(course)
        }
    }

    fun openCourseDetail(courseId: Long) {
        _selectedCourseDetailId.value = courseId
    }

    fun closeCourseDetail() {
        _selectedCourseDetailId.value = null
    }

    // -------------------------------------------------------------------------
    // Group chat unread message tracking (in-app, phone-local) and notifications
    // -------------------------------------------------------------------------
    private val groupChatTracker = GroupChatTracker(application)
    private val groupChatNotifier = GroupChatNotifier(application)
    private val _chatReadVersion = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val groupChatInfo: StateFlow<Map<String, GroupChatInfo>> = _currentUser.flatMapLatest { user ->
        if (user == null) {
            groupChatTracker.clearAll()
            groupChatNotifier.clearAll()
            flowOf(emptyMap<String, GroupChatInfo>())
        } else {
            val myUid = user.uid
            groupRepository.observeMyGroupMemberships(myUid).flatMapLatest { memberships ->
                val currentGroupIds = memberships.map { it.groupId }.toSet()
                if (memberships.isNotEmpty()) {
                    groupChatTracker.retainOnly(currentGroupIds)
                }
                if (memberships.isEmpty()) {
                    flowOf(emptyMap<String, GroupChatInfo>())
                } else {
                    val flows = memberships.map { m ->
                        val gid = m.groupId
                        val groupName = m.name.ifBlank { m.username }.ifBlank { "Group" }
                        combine(
                            groupRepository.observeGroupChatTail(gid)
                                .onStart { emit(ChatTail(emptyList(), fromServer = false)) }
                                .catch { emit(ChatTail(emptyList(), fromServer = false)) },
                            _chatReadVersion
                        ) { tail, _ ->
                            groupChatNotifier.onMessages(myUid, gid, groupName, tail.messages, tail.fromServer)

                            val storedLastRead = groupChatTracker.lastRead(gid)
                            val effectiveLastRead: Long?
                            val unreadCount: Int

                            if (storedLastRead == null) {
                                if (tail.fromServer) {
                                    val newestMillis = tail.messages.lastOrNull()?.createdAtMillis ?: 0L
                                    groupChatTracker.setBaseline(gid, newestMillis)
                                    effectiveLastRead = newestMillis
                                } else {
                                    effectiveLastRead = null
                                }
                                unreadCount = 0
                            } else {
                                effectiveLastRead = storedLastRead
                                unreadCount = tail.messages.count { msg ->
                                    msg.senderId != myUid && msg.createdAtMillis > storedLastRead
                                }
                            }

                            GroupChatInfo(
                                groupId = gid,
                                unreadCount = unreadCount,
                                lastMessage = tail.messages.lastOrNull(),
                                lastReadMillis = effectiveLastRead
                            )
                        }
                    }
                    combine(flows) { array ->
                        array.associateBy { it.groupId }
                    }
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val totalUnreadMessages: StateFlow<Int> = groupChatInfo.map { map ->
        map.values.sumOf { it.unreadCount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    // Keeps groupChatInfo active for the whole life of the ViewModel, so chat notifications also work
    // while the app is in the background. This block MUST stay below the groupChatInfo declaration.
    init {
        viewModelScope.launch {
            groupChatInfo.collect { }
        }
    }

    fun markGroupChatRead(groupId: String, upToMillis: Long) {
        if (groupId.isBlank()) return
        groupChatTracker.markRead(groupId, upToMillis)
        _chatReadVersion.value++
        groupChatNotifier.cancel(groupId)
    }
}
