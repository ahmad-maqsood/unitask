package com.example.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.Course
import com.example.data.local.CourseWithTaskCount
import com.example.data.local.Priority
import com.example.data.local.ReminderOption
import com.example.data.local.TaskItem
import com.example.data.local.TaskStatus
import com.example.data.local.TaskWithCourse
import com.example.data.repository.AppRepository
import com.example.reminder.ReminderScheduler
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import com.example.data.local.LectureSlot
import com.example.data.local.LectureSlotWithCourse
import com.example.data.local.TaskAttachment
import com.example.util.SavedAttachmentInfo
import java.util.Calendar

enum class MainTab {
    DEADLINES,
    TIMETABLE,
    COURSES
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

    private val _currentUser = MutableStateFlow<FirebaseUser?>(Firebase.auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _isCloudBackupSheetOpen = MutableStateFlow(false)
    val isCloudBackupSheetOpen: StateFlow<Boolean> = _isCloudBackupSheetOpen.asStateFlow()

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

        // Schedule periodic sync
        com.example.sync.SyncScheduler.schedulePeriodicSync(application)

        // Listen for auth state changes and attach real-time guarded sync
        Firebase.auth.addAuthStateListener { auth: FirebaseAuth ->
            _currentUser.value = auth.currentUser
            if (auth.currentUser != null) {
                com.example.sync.FirestoreSyncManager.startRealtimeSync(application, database)
                com.example.sync.SyncScheduler.scheduleSync(application)
            } else {
                com.example.sync.FirestoreSyncManager.stopRealtimeSync()
            }
        }

        if (Firebase.auth.currentUser != null) {
            com.example.sync.FirestoreSyncManager.startRealtimeSync(application, database)
        }
    }

    override fun onCleared() {
        super.onCleared()
        com.example.sync.FirestoreSyncManager.stopRealtimeSync()
    }

    fun openCloudBackupSheet() {
        _isCloudBackupSheetOpen.value = true
    }

    fun closeCloudBackupSheet() {
        _isCloudBackupSheetOpen.value = false
    }

    /**
     * Pull to refresh. Local data is live from Room, so this re-evaluates the class schedule
     * and, when signed in, starts a cloud sync (which finishes in the background).
     */
    fun refreshData() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _clockTick.value = System.currentTimeMillis()
            if (Firebase.auth.currentUser != null) {
                com.example.sync.SyncScheduler.scheduleSync(getApplication())
            }
            delay(700L)
            _isRefreshing.value = false
        }
    }

    fun refreshAuth() {
        _currentUser.value = Firebase.auth.currentUser
        if (_currentUser.value != null) {
            com.example.sync.SyncScheduler.scheduleSync(getApplication())
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

    private val _selectedCourseDetailId = MutableStateFlow<Long?>(null)
    val selectedCourseDetailId: StateFlow<Long?> = _selectedCourseDetailId.asStateFlow()

    // Lecture Slot Dialog state
    private val _isAddEditSlotDialogOpen = MutableStateFlow(false)
    val isAddEditSlotDialogOpen: StateFlow<Boolean> = _isAddEditSlotDialogOpen.asStateFlow()

    private val _slotToEdit = MutableStateFlow<LectureSlot?>(null)
    val slotToEdit: StateFlow<LectureSlot?> = _slotToEdit.asStateFlow()

    private val _preselectedCourseIdForSlot = MutableStateFlow<Long?>(null)
    val preselectedCourseIdForSlot: StateFlow<Long?> = _preselectedCourseIdForSlot.asStateFlow()

    // Courses & Tasks streams
    val allCourses: StateFlow<List<Course>> = repository.allCourses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val coursesWithCounts: StateFlow<List<CourseWithTaskCount>> = repository.coursesWithTaskCounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTasksWithCourse: StateFlow<List<TaskWithCourse>> = repository.allTasksWithCourse
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allLectureSlots: StateFlow<List<LectureSlotWithCourse>> = repository.allLectureSlots
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
    val filteredTasks: StateFlow<List<TaskWithCourse>> = combine(
        repository.allTasksWithCourse,
        _searchQuery,
        _statusFilter,
        _courseFilter,
        _sortOption
    ) { tasks, query, status, courseId, sort ->
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

        // Course filter
        if (courseId != null) {
            list = list.filter { it.task.courseId == courseId }
        }

        // Sorting
        when (sort) {
            TaskSortOption.DUE_DATE_ASC -> list.sortedWith(
                compareBy<TaskWithCourse> { it.task.dueDateEpochMillis }
                    .thenBy { it.task.dueTimeHour }
                    .thenBy { it.task.dueTimeMinute }
            )
            TaskSortOption.DUE_DATE_DESC -> list.sortedWith(
                compareByDescending<TaskWithCourse> { it.task.dueDateEpochMillis }
                    .thenByDescending { it.task.dueTimeHour }
                    .thenByDescending { it.task.dueTimeMinute }
            )
            TaskSortOption.PRIORITY_HIGH_FIRST -> list.sortedWith(
                compareByDescending<TaskWithCourse> { it.task.priority.ordinal }
                    .thenBy { it.task.dueDateEpochMillis }
            )
            TaskSortOption.TITLE_ASC -> list.sortedBy { it.task.title.lowercase() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Quick stats
    val pendingCount: StateFlow<Int> = allTasksWithCourse.combine(allTasksWithCourse) { tasks, _ ->
        tasks.count { it.task.status != TaskStatus.COMPLETED }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val completedCount: StateFlow<Int> = allTasksWithCourse.combine(allTasksWithCourse) { tasks, _ ->
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
        viewModelScope.launch {
            val nextStatus = when (task.status) {
                TaskStatus.PENDING -> TaskStatus.IN_PROGRESS
                TaskStatus.IN_PROGRESS -> TaskStatus.COMPLETED
                TaskStatus.COMPLETED -> TaskStatus.PENDING
            }
            repository.updateTaskStatus(task.id, nextStatus)
        }
    }

    fun setTaskStatus(task: TaskItem, newStatus: TaskStatus) {
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
        reminderOption: ReminderOption,
        newAttachments: List<SavedAttachmentInfo> = emptyList()
    ) {
        viewModelScope.launch {
            val currentTask = _taskToEdit.value
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
                    reminderOption = reminderOption
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
                    reminderOption = reminderOption
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

            closeTaskDialog()
        }
    }

    fun deleteTask(task: TaskItem) {
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
        viewModelScope.launch {
            val currentSlot = _slotToEdit.value
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
            closeSlotDialog()
        }
    }

    fun deleteLectureSlot(slot: LectureSlot) {
        viewModelScope.launch {
            repository.deleteLectureSlot(slot)
        }
    }

    fun openAddCourseDialog() {
        _courseToEdit.value = null
        _isAddEditCourseDialogOpen.value = true
    }

    fun openEditCourseDialog(course: Course) {
        _courseToEdit.value = course
        _isAddEditCourseDialogOpen.value = true
    }

    fun closeCourseDialog() {
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
}
