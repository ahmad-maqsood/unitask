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
        const val MAX_BODY = 100_000
        const val TRASH_DAYS = 30
    }

    fun observeNotes(): Flow<List<NoteListItem>> {
        return noteDao.observeNotes().flowOn(Dispatchers.IO)
    }

    fun observeDeletedNotes(): Flow<List<NoteListItem>> {
        return noteDao.observeDeletedNotes().flowOn(Dispatchers.IO)
    }

    suspend fun getNote(id: Long): Note? = withContext(Dispatchers.IO) {
        noteDao.getNoteById(id)
    }

    suspend fun saveNote(id: Long, title: String, body: String, courseId: Long?): Long = withContext(Dispatchers.IO) {
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
                isSynced = false
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
                    isSynced = false
                )
                return@withContext noteDao.insert(newNote)
            }
            if (existing.title == trimmedTitle && existing.body == trimmedBody && existing.courseId == courseId) {
                return@withContext id
            }
            val updated = existing.copy(
                title = trimmedTitle,
                body = trimmedBody,
                courseId = courseId,
                updatedAt = now,
                isSynced = false
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

    suspend fun moveToTrash(id: Long) = withContext(Dispatchers.IO) {
        noteDao.softDelete(id, System.currentTimeMillis())
    }

    suspend fun restoreFromTrash(id: Long) = withContext(Dispatchers.IO) {
        noteDao.restore(id)
    }

    suspend fun deleteForever(id: Long) = withContext(Dispatchers.IO) {
        noteDao.deleteForever(id)
    }

    suspend fun purgeOldTrash(now: Long = System.currentTimeMillis()): Int = withContext(Dispatchers.IO) {
        val cutoff = now - (TRASH_DAYS * 24L * 60L * 60L * 1000L)
        noteDao.purgeDeletedBefore(cutoff)
    }
}

