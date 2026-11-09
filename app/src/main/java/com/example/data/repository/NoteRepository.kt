package com.example.data.repository

import com.example.data.local.Note
import com.example.data.local.NoteDao
import com.example.data.local.NoteListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class NoteRepository(private val noteDao: NoteDao) {

    companion object {
        const val MAX_TITLE = 150
        // Kept at 300,000 characters as a reasonable maximum note size.
        const val MAX_BODY = 300_000
        const val TRASH_DAYS = 30
    }

    fun observeNotes(): Flow<List<NoteListItem>> {
        return noteDao.observeNotes().flowOn(Dispatchers.IO)
    }

    fun observeDeletedNotes(): Flow<List<NoteListItem>> {
        return noteDao.observeDeletedNotes().flowOn(Dispatchers.IO)
    }
    
    fun observeNote(id: Long): Flow<Note?> {
        return noteDao.observeNoteById(id).flowOn(Dispatchers.IO)
    }
    
    suspend fun getNote(id: Long): Note? = withContext(Dispatchers.IO) {
        noteDao.getNoteById(id)
    }

    suspend fun saveNote(
        id: Long,
        title: String,
        body: String,
        courseId: Long? = null,
        keepScreenOn: Boolean? = null // null leaves an existing note's setting unchanged
    ): Long = withContext(Dispatchers.IO) {
        val trimmedTitle = title.take(MAX_TITLE)
        val trimmedBody = body.take(MAX_BODY)

        if (trimmedTitle.isBlank() && trimmedBody.isBlank()) {
            if (id == 0L) {
                return@withContext 0L
            } else {
                moveToTrash(id)
                return@withContext id
            }
        }

        val now = System.currentTimeMillis()
        if (id == 0L) {
            val newNote = Note(
                title = trimmedTitle,
                body = trimmedBody,
                courseId = courseId,
                createdAt = now,
                updatedAt = now,
                isSynced = false,
                keepScreenOn = keepScreenOn ?: false
            )
            return@withContext noteDao.insert(newNote)
        } else {
            val existing = noteDao.getNoteById(id)
            if (existing == null) {
                val newNote = Note(
                    title = trimmedTitle,
                    body = trimmedBody,
                    courseId = courseId,
                    createdAt = now,
                    updatedAt = now,
                    isSynced = false,
                    keepScreenOn = keepScreenOn ?: false
                )
                return@withContext noteDao.insert(newNote)
            }
            if (!existing.isDeleted &&
                existing.title == trimmedTitle &&
                existing.body == trimmedBody &&
                existing.courseId == courseId
            ) {
                return@withContext id
            }
            // The text is not blank here, so saving into a trashed note brings it back.
            // This covers: clear a note, pause (it goes to the trash), then type again.
            val updated = existing.copy(
                title = trimmedTitle,
                body = trimmedBody,
                courseId = courseId,
                updatedAt = now,
                isDeleted = false,
                deletedAt = 0L,
                isSynced = false,
                keepScreenOn = keepScreenOn ?: existing.keepScreenOn
            )
            noteDao.update(updated)
            return@withContext id
        }
    }

    suspend fun setPinned(id: Long, pinned: Boolean) = withContext(Dispatchers.IO) {
        val note = noteDao.getNoteById(id) ?: return@withContext
        if (note.isPinned == pinned) return@withContext
        val now = System.currentTimeMillis()
        noteDao.update(note.copy(isPinned = pinned, updatedAt = now, isSynced = false))
    }

    suspend fun setKeepScreenOn(id: Long, keepScreenOn: Boolean) = withContext(Dispatchers.IO) {
        noteDao.setKeepScreenOn(id, keepScreenOn)
    }

    suspend fun moveToTrash(id: Long) = withContext(Dispatchers.IO) {
        noteDao.softDelete(id, System.currentTimeMillis())
    }

    suspend fun restoreFromTrash(id: Long) = withContext(Dispatchers.IO) {
        noteDao.restore(id)
    }

    suspend fun deleteForever(id: Long) = withContext(Dispatchers.IO) {
        noteDao.deleteForever(id)
    }

    suspend fun emptyTrash() = withContext(Dispatchers.IO) {
        val deleted = noteDao.getAllNotesSync().filter { it.isDeleted }
        for (note in deleted) {
            deleteForever(note.id)
        }
    }

    suspend fun purgeOldTrash(now: Long = System.currentTimeMillis()): Int = withContext(Dispatchers.IO) {
        val cutoff = now - (TRASH_DAYS * 24L * 60L * 60L * 1000L)
        noteDao.purgeDeletedBefore(cutoff)
    }
}
