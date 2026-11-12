package com.example.data.repository

import com.example.data.local.Note
import com.example.data.local.NoteFolder
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
        const val MAX_FOLDER_NAME = 60
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
        keepScreenOn: Boolean? = null, // null leaves an existing note's setting unchanged
        folderId: Long? = null // only used when a new note is created; an existing note keeps its folder
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
        // A folder that no longer exists must not leave the note stranded in a folder nobody can open
        val safeFolderId = folderId?.takeIf { noteDao.getFolderById(it) != null }
        if (id == 0L) {
            val newNote = Note(
                title = trimmedTitle,
                body = trimmedBody,
                courseId = courseId,
                folderId = safeFolderId,
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
                    folderId = safeFolderId,
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

    // ---- folders

    fun observeFolders(): Flow<List<NoteFolder>> {
        return noteDao.observeFolders().flowOn(Dispatchers.IO)
    }

    /**
     * Creates a folder and returns its id. Returns the id of the existing folder when one with the
     * same name (ignoring case) is already there, and 0 when the name is blank.
     */
    suspend fun createFolder(name: String): Long = withContext(Dispatchers.IO) {
        val clean = cleanFolderName(name)
        if (clean.isEmpty()) return@withContext 0L
        val existing = noteDao.getAllFoldersSync().firstOrNull { it.name.equals(clean, ignoreCase = true) }
        if (existing != null) return@withContext existing.id
        noteDao.insertFolder(NoteFolder(name = clean))
    }

    /** Returns false when the name is blank or another folder already uses it. */
    suspend fun renameFolder(id: Long, name: String): Boolean = withContext(Dispatchers.IO) {
        val clean = cleanFolderName(name)
        if (clean.isEmpty()) return@withContext false
        val clash = noteDao.getAllFoldersSync().any { it.id != id && it.name.equals(clean, ignoreCase = true) }
        if (clash) return@withContext false
        noteDao.renameFolder(id, clean)
        true
    }

    /** Deletes the folder only. Its notes are kept and become unfiled. */
    suspend fun deleteFolder(id: Long) = withContext(Dispatchers.IO) {
        noteDao.detachFolder(id)
        noteDao.deleteFolder(id)
    }

    /** Moves any number of notes into a folder, or out of all folders when folderId is null. */
    suspend fun moveNotesToFolder(noteIds: Collection<Long>, folderId: Long?) = withContext(Dispatchers.IO) {
        if (noteIds.isEmpty()) return@withContext
        if (folderId != null && noteDao.getFolderById(folderId) == null) return@withContext
        // Chunked to stay under SQLite's limit on bound variables
        noteIds.toList().chunked(500).forEach { noteDao.moveNotesToFolder(it, folderId) }
    }

    private fun cleanFolderName(name: String): String =
        name.trim().replace(Regex("\\s+"), " ").take(MAX_FOLDER_NAME)

    suspend fun setPinnedForNotes(ids: Collection<Long>, pinned: Boolean) = withContext(Dispatchers.IO) {
        for (id in ids) setPinned(id, pinned)
    }

    suspend fun moveNotesToTrash(ids: Collection<Long>) = withContext(Dispatchers.IO) {
        for (id in ids) moveToTrash(id)
    }

    suspend fun restoreNotesFromTrash(ids: Collection<Long>) = withContext(Dispatchers.IO) {
        for (id in ids) restoreFromTrash(id)
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
