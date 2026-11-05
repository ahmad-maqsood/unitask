package com.example.ui.screens

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.Course
import com.example.data.local.NoteListItem
import com.example.data.local.isMyTasksCourse
import com.example.data.repository.NoteRepository
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

    val activeCourses: StateFlow<List<Course>> = database.courseDao().getAllCourses()
        .map { list -> list.filter { !it.isDeleted && !it.isMyTasksCourse() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notes: StateFlow<List<NoteListItem>> = combine(
        repository.observeNotes(),
        _searchQuery,
        _selectedFilter,
        _sort
    ) { allNotes, query, filter, sortOrder ->
        val filtered = filterNotes(allNotes, query, filter)
        sortNotes(filtered, sortOrder)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val deletedNotes: StateFlow<List<NoteListItem>> = repository.observeDeletedNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: NotesFilter) {
        _selectedFilter.value = filter
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
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            repository.emptyTrash()
        }
    }
}
