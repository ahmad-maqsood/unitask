package com.example.ui.screens

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.Course
import com.example.data.local.NoteFolder
import com.example.data.local.NoteListItem
import com.example.data.local.isMyTasksCourse
import com.example.data.repository.NoteRepository

import com.example.util.NoteImages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotesViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    val repository = NoteRepository(database.noteDao())

    private val prefs = application.getSharedPreferences("notes_prefs", Context.MODE_PRIVATE)

    private fun loadSavedSort(): NotesSort {
        val saved = prefs.getString("sort", null)
        return NotesSort.entries.firstOrNull { it.name == saved } ?: NotesSort.UPDATED
    }

    private val _sort = MutableStateFlow(loadSavedSort())
    val sort: StateFlow<NotesSort> = _sort.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow<NotesFilter>(NotesFilter.All)
    val selectedFilter: StateFlow<NotesFilter> = _selectedFilter.asStateFlow()

    private val _folderFilter = MutableStateFlow<NotesFolderFilter>(NotesFolderFilter.AllFolders)
    val folderFilter: StateFlow<NotesFolderFilter> = _folderFilter.asStateFlow()

    val folders: StateFlow<List<NoteFolder>> = repository.observeFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Notes picked in selection mode, so many can be moved to a folder in one go
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    // How many notes sit in each folder (null key = unfiled), ignoring search and filters
    val folderCounts: StateFlow<Map<Long?, Int>> = repository.observeNotes()
        .map { all -> all.groupingBy { it.note.folderId }.eachCount() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val activeCourses: StateFlow<List<Course>> = database.courseDao().getAllCourses()
        .map { list -> list.filter { !it.isDeleted && !it.isMyTasksCourse() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notes: StateFlow<List<NoteListItem>> = combine(
        repository.observeNotes(),
        _searchQuery,
        _selectedFilter,
        _sort,
        _folderFilter
    ) { allNotes, query, filter, sortOrder, folder ->
        val filtered = filterNotes(allNotes, query, filter, folder)
        sortNotes(filtered, sortOrder)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val deletedNotes: StateFlow<List<NoteListItem>> = repository.observeDeletedNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setQuery(query: String) {
        _searchQuery.value = query
        clearSelection()
    }

    fun setFilter(filter: NotesFilter) {
        _selectedFilter.value = filter
        clearSelection()
    }

    fun setFolderFilter(filter: NotesFolderFilter) {
        _folderFilter.value = filter
        clearSelection()
    }

    /** Creates a folder. Calls onDone with its id (0 when the name was blank). */
    fun createFolder(name: String, onDone: (Long) -> Unit = {}) {
        viewModelScope.launch { onDone(repository.createFolder(name)) }
    }

    fun renameFolder(id: Long, name: String, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch { onDone(repository.renameFolder(id, name)) }
    }

    fun deleteFolder(id: Long) {
        // Back to the full list first, so the screen is never left showing a folder that is gone
        val current = _folderFilter.value
        if (current is NotesFolderFilter.Folder && current.folderId == id) {
            _folderFilter.value = NotesFolderFilter.AllFolders
        }
        viewModelScope.launch { repository.deleteFolder(id) }
    }

    fun moveNotesToFolder(noteIds: Collection<Long>, folderId: Long?) {
        viewModelScope.launch { repository.moveNotesToFolder(noteIds, folderId) }
    }

    // ---- selection mode

    fun setPinnedForNotes(ids: Collection<Long>, pinned: Boolean) {
        viewModelScope.launch { repository.setPinnedForNotes(ids, pinned) }
    }

    fun moveNotesToTrash(ids: Collection<Long>) {
        viewModelScope.launch { repository.moveNotesToTrash(ids) }
    }

    fun restoreNotesFromTrash(ids: Collection<Long>) {
        viewModelScope.launch { repository.restoreNotesFromTrash(ids) }
    }

    fun toggleSelected(id: Long) {
        _selectedIds.value = _selectedIds.value.let { if (id in it) it - id else it + id }
    }

    fun selectAllShown() {
        _selectedIds.value = notes.value.map { it.note.id }.toSet()
    }

    fun clearSelection() {
        if (_selectedIds.value.isNotEmpty()) _selectedIds.value = emptySet()
    }

    fun setSort(newSort: NotesSort) {
        if (_sort.value == newSort) return
        _sort.value = newSort
        prefs.edit().putString("sort", newSort.name).apply()
    }



    fun setPinned(id: Long, pinned: Boolean) {
        viewModelScope.launch {
            repository.setPinned(id, pinned)
        }
    }

    fun moveToTrash(id: Long) {
        viewModelScope.launch {
            repository.moveToTrash(id)
        }
    }

    fun restoreFromTrash(id: Long) {
        viewModelScope.launch {
            repository.restoreFromTrash(id)
        }
    }

    fun deleteForever(id: Long) {
        viewModelScope.launch {
            repository.deleteForever(id)
            NoteImages.cleanupOrphans(getApplication(), database.noteDao())
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            repository.emptyTrash()
            NoteImages.cleanupOrphans(getApplication(), database.noteDao())
        }
    }
}
