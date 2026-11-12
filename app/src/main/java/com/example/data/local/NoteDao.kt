package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query(
        "SELECT n.*, c.code AS courseCode, c.name AS courseName, c.colorHex AS courseColorHex " +
        "FROM notes n LEFT JOIN courses c ON c.id = n.courseId AND c.isDeleted = 0 " +
        "WHERE n.isDeleted = 0 AND (n.courseId IS NULL OR n.courseId NOT IN (SELECT id FROM courses WHERE archivedSemester != '')) " +
        "ORDER BY n.isPinned DESC, n.updatedAt DESC"
    )
    fun observeNotes(): Flow<List<NoteListItem>>

    @Query(
        "SELECT n.*, c.code AS courseCode, c.name AS courseName, c.colorHex AS courseColorHex " +
        "FROM notes n LEFT JOIN courses c ON c.id = n.courseId AND c.isDeleted = 0 " +
        "WHERE n.isDeleted = 0 " +
        "ORDER BY n.isPinned DESC, n.updatedAt DESC"
    )
    fun observeNotesIncludingArchived(): Flow<List<NoteListItem>>

    @Query(
        "SELECT n.*, c.code AS courseCode, c.name AS courseName, c.colorHex AS courseColorHex " +
        "FROM notes n LEFT JOIN courses c ON c.id = n.courseId AND c.isDeleted = 0 " +
        "WHERE n.isDeleted = 1 " +
        "ORDER BY n.deletedAt DESC"
    )
    fun observeDeletedNotes(): Flow<List<NoteListItem>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteById(id: Long): Note?

    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeNoteById(id: Long): Flow<Note?>

    @Query("SELECT * FROM notes")
    suspend fun getAllNotesSync(): List<Note>

    @Query(
        "SELECT * FROM notes " +
        "WHERE isDeleted = 0 AND (courseId IS NULL OR courseId NOT IN (SELECT id FROM courses WHERE archivedSemester != ''))"
    )
    suspend fun getActiveNotesSync(): List<Note>

    @Query("SELECT * FROM notes WHERE isDeleted = 0")
    suspend fun getActiveNotesSyncIncludingArchived(): List<Note>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: Note): Long

    @Update
    suspend fun update(note: Note)

    // Display setting only: does not touch updatedAt, so the note keeps its place in the list
    @Query("UPDATE notes SET keepScreenOn = :keepScreenOn WHERE id = :id")
    suspend fun setKeepScreenOn(id: Long, keepScreenOn: Boolean)

    @Query("UPDATE notes SET isDeleted = 1, deletedAt = :deletedAt, isSynced = 0 WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Long)

    @Query("UPDATE notes SET isDeleted = 0, deletedAt = 0, isSynced = 0 WHERE id = :id")
    suspend fun restore(id: Long)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteForever(id: Long)

    @Query("DELETE FROM notes WHERE isDeleted = 1 AND deletedAt > 0 AND deletedAt < :cutoff")
    suspend fun purgeDeletedBefore(cutoff: Long): Int

    @Query("UPDATE notes SET courseId = NULL, isSynced = 0 WHERE courseId IN (:courseIds)")
    suspend fun detachCourses(courseIds: List<Long>)

    @Query("UPDATE notes SET courseId = :newCourseId, isSynced = 0 WHERE courseId = :oldCourseId")
    suspend fun reassignCourse(oldCourseId: Long, newCourseId: Long)

    // Moving a note between folders is organising, not editing, so updatedAt is left alone
    // and the note keeps its place when sorted by "Last edited".
    @Query("UPDATE notes SET folderId = :folderId, isSynced = 0 WHERE id IN (:noteIds)")
    suspend fun moveNotesToFolder(noteIds: List<Long>, folderId: Long?)

    @Query("UPDATE notes SET folderId = NULL, isSynced = 0 WHERE folderId = :folderId")
    suspend fun detachFolder(folderId: Long)

    // ---- folders

    @Query("SELECT * FROM note_folders ORDER BY name COLLATE NOCASE ASC, id ASC")
    fun observeFolders(): Flow<List<NoteFolder>>

    @Query("SELECT * FROM note_folders ORDER BY name COLLATE NOCASE ASC, id ASC")
    suspend fun getAllFoldersSync(): List<NoteFolder>

    @Query("SELECT * FROM note_folders WHERE id = :id")
    suspend fun getFolderById(id: Long): NoteFolder?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: NoteFolder): Long

    @Query("UPDATE note_folders SET name = :name WHERE id = :id")
    suspend fun renameFolder(id: Long, name: String)

    @Query("DELETE FROM note_folders WHERE id = :id")
    suspend fun deleteFolder(id: Long)
}
