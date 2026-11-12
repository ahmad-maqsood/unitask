package com.example.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.util.CompletionLog
import com.example.util.ProgressLogic
import com.example.util.ProgressSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Feeds the Progress screen. It reads every saved task, including archived semesters. */
class ProgressViewModel(application: Application) : AndroidViewModel(application) {

    private val taskDao = AppDatabase.getDatabase(application).taskDao()

    /** Null until the first numbers are ready. */
    val summary: StateFlow<ProgressSummary?> = taskDao.getAllTasksWithCourseIncludingArchived()
        .map { tasks ->
            ProgressLogic.compute(
                tasks,
                System.currentTimeMillis(),
                loggedCompletions = CompletionLog.entries(getApplication<Application>())
            )
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
