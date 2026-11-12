package com.example.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.Course
import com.example.data.local.NoteListItem
import com.example.data.local.TaskWithCourse
import com.example.data.local.isMyTasksCourse
import com.example.data.repository.AppRepository
import com.example.data.repository.NoteRepository
import com.example.reminder.ReminderScheduler
import com.example.util.FeatureGuide
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Encapsulates items retrieved from archived semesters for search.
 */
data class SearchArchivedData(
    val courses: List<ArchivedItem<Course>>,
    val tasks: List<ArchivedItem<TaskWithCourse>>,
    val notes: List<ArchivedItem<NoteListItem>>
)

/**
 * Combined active search data.
 */
data class SearchActiveData(
    val tasks: List<TaskWithCourse>,
    val notes: List<NoteListItem>,
    val courses: List<Course>,
    val archived: SearchArchivedData
)

/**
 * ViewModel for global search across tasks, notes, and courses.
 * Computes debounced search results on Dispatchers.Default.
 */
@OptIn(FlowPreview::class)
class SearchViewModel(
    application: Application,
    private val appRepository: AppRepository,
    private val noteRepository: NoteRepository
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application = application,
        appRepository = AppRepository(
            context = application,
            courseDao = AppDatabase.getDatabase(application).courseDao(),
            taskDao = AppDatabase.getDatabase(application).taskDao(),
            taskAttachmentDao = AppDatabase.getDatabase(application).taskAttachmentDao(),
            lectureSlotDao = AppDatabase.getDatabase(application).lectureSlotDao(),
            reminderScheduler = ReminderScheduler(application),
            noteDao = AppDatabase.getDatabase(application).noteDao(),
            database = AppDatabase.getDatabase(application)
        ),
        noteRepository = NoteRepository(AppDatabase.getDatabase(application).noteDao())
    )

    private val database: AppDatabase = AppDatabase.getDatabase(application)
    private val courseDao = database.courseDao()
    private val taskDao = database.taskDao()
    private val noteDao = database.noteDao()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _includeArchived = MutableStateFlow(false)
    val includeArchived: StateFlow<Boolean> = _includeArchived.asStateFlow()

    private val _includeGuides = MutableStateFlow(false)
    val includeGuides: StateFlow<Boolean> = _includeGuides.asStateFlow()

    private val _groupTasks = MutableStateFlow<List<TaskWithCourse>>(emptyList())

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun setIncludeArchived(include: Boolean) {
        _includeArchived.value = include
    }

    fun setIncludeGuides(include: Boolean) {
        _includeGuides.value = include
    }

    /** Clears the query and both switches so the next visit starts fresh. */
    fun resetSearch() {
        _searchQuery.value = ""
        _includeArchived.value = false
        _includeGuides.value = false
    }

    fun setGroupTasks(tasks: List<TaskWithCourse>) {
        _groupTasks.value = tasks
    }


    // Active tasks combine repository tasks with visible group tasks.
    private val activeTasksFlow: Flow<List<TaskWithCourse>> = combine(
        appRepository.allTasksWithCourse,
        _groupTasks
    ) { personal, groups ->
        personal + groups
    }

    // Archived data. Only loaded while "Include archived" is on.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val archivedDataFlow: Flow<SearchArchivedData> = _includeArchived.flatMapLatest { include ->
        if (!include) {
            flowOf(SearchArchivedData(emptyList(), emptyList(), emptyList()))
        } else {
            combine(
                courseDao.getAllCoursesIncludingArchived(),
                taskDao.getAllTasksWithCourseIncludingArchived(),
                noteDao.observeNotesIncludingArchived()
            ) { courses, tasks, notes ->
                val archivedCourses = courses.filter { !it.isDeleted && it.archivedSemester.isNotBlank() && !it.isMyTasksCourse() }
                val courseSemesterMap = archivedCourses.associate { it.id to it.archivedSemester }

                val archivedCourseItems = archivedCourses.map { ArchivedItem(it, it.archivedSemester) }
                val archivedTaskItems = tasks.filter {
                    !it.task.isDeleted && it.course != null && !it.course.isDeleted && it.course.archivedSemester.isNotBlank()
                }.mapNotNull { item ->
                    val course = item.course ?: return@mapNotNull null
                    ArchivedItem(item, course.archivedSemester)
                }
                val archivedNoteItems = notes.filter {
                    !it.note.isDeleted && it.note.courseId in courseSemesterMap.keys
                }.map {
                    ArchivedItem(it, courseSemesterMap[it.note.courseId] ?: "")
                }

                SearchArchivedData(
                    courses = archivedCourseItems,
                    tasks = archivedTaskItems,
                    notes = archivedNoteItems
                )
            }
        }
    }

    // Active data flow combining active tasks, notes, courses and archived data.
    private val searchDataFlow: Flow<SearchActiveData> = combine(
        activeTasksFlow,
        noteDao.observeNotes(),
        appRepository.allCourses,
        archivedDataFlow
    ) { tasks, notes, courses, archived ->
        SearchActiveData(
            tasks = tasks,
            notes = notes,
            courses = courses,
            archived = archived
        )
    }

    private data class SearchParams(
        val query: String,
        val includeArchived: Boolean,
        val includeGuides: Boolean
    )

    // Search query debounced by 200 ms, paired with the two include toggles.
    private val searchParamsFlow: Flow<SearchParams> = combine(
        _searchQuery.debounce(200L),
        _includeArchived,
        _includeGuides
    ) { query, includeArchived, includeGuides ->
        SearchParams(query, includeArchived, includeGuides)
    }

    val results: StateFlow<SearchResults> = combine(
        searchParamsFlow,
        searchDataFlow
    ) { params, data ->
        searchEverything(
            query = params.query,
            tasks = data.tasks,
            notes = data.notes,
            courses = data.courses,
            archivedTasks = if (params.includeArchived) data.archived.tasks else emptyList(),
            archivedNotes = if (params.includeArchived) data.archived.notes else emptyList(),
            archivedCourses = if (params.includeArchived) data.archived.courses else emptyList(),
            settingsEntries = allSettingsSearchEntries,
            guideEntries = if (params.includeGuides) FeatureGuide.all else emptyList()
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SearchResults(emptyList(), emptyList(), emptyList())
        )
}
