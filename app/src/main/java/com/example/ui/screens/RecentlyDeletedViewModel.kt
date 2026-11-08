package com.example.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.LectureSlot
import com.example.data.local.isArchived
import com.example.data.local.LectureSlotWithCourse
import com.example.data.local.NoteListItem
import com.example.data.local.TaskItem
import com.example.data.local.TaskWithCourse
import com.example.data.repository.AppRepository
import com.example.data.repository.NoteRepository
import com.example.reminder.ReminderScheduler
import com.example.util.NoteImages
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for Recently deleted screen managing both soft-deleted tasks and soft-deleted notes.
 */
class RecentlyDeletedViewModel(
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

    val deletedTasks: StateFlow<List<TaskWithCourse>> = appRepository.observeDeletedTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val deletedNotes: StateFlow<List<NoteListItem>> = noteRepository.observeDeletedNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val deletedClasses: StateFlow<List<LectureSlotWithCourse>> = appRepository.observeDeletedLectureSlots()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun restoreTask(task: TaskItem) {
        viewModelScope.launch {
            appRepository.restoreTask(task.id)
        }
    }

    fun restoreTask(taskId: Long) {
        viewModelScope.launch {
            appRepository.restoreTask(taskId)
        }
    }

    fun deleteTaskForever(task: TaskItem) {
        viewModelScope.launch {
            appRepository.deleteTaskForever(task.id)
        }
    }

    fun deleteTaskForever(taskId: Long) {
        viewModelScope.launch {
            appRepository.deleteTaskForever(taskId)
        }
    }

    fun emptyTaskTrash() {
        viewModelScope.launch {
            appRepository.emptyTaskTrash()
        }
    }

    fun restoreNote(noteId: Long) {
        viewModelScope.launch {
            noteRepository.restoreFromTrash(noteId)
        }
    }

    fun deleteNoteForever(noteId: Long) {
        viewModelScope.launch {
            noteRepository.deleteForever(noteId)
            NoteImages.cleanupOrphans(getApplication(), database.noteDao())
        }
    }

    fun emptyNoteTrash() {
        viewModelScope.launch {
            noteRepository.emptyTrash()
            NoteImages.cleanupOrphans(getApplication(), database.noteDao())
        }
    }

    fun restoreClass(slot: LectureSlot) {
        viewModelScope.launch {
            appRepository.restoreLectureSlot(slot.id)
        }
    }

    fun restoreClass(slotId: Long) {
        viewModelScope.launch {
            appRepository.restoreLectureSlot(slotId)
        }
    }

    fun deleteClassForever(slotId: Long) {
        viewModelScope.launch {
            appRepository.deleteSlotForever(slotId)
        }
    }

    fun emptyClassTrash() {
        viewModelScope.launch {
            appRepository.emptyClassTrash()
        }
    }

    fun restoreDisabledReason(taskWithCourse: TaskWithCourse): String? {
        val course = taskWithCourse.course ?: return null
        return if (course.isArchived) {
            "Its course is archived in ${course.archivedSemester}. Restore the semester first."
        } else {
            null
        }
    }

    fun restoreDisabledReason(slotWithCourse: LectureSlotWithCourse): String? {
        val course = slotWithCourse.course ?: return null
        return if (course.isArchived) {
            "Its course is archived in ${course.archivedSemester}. Restore the semester first."
        } else {
            null
        }
    }
}
