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
        "WHERE n.isDeleted = 0 " +
        "ORDER BY n.isPinned DESC, n.updatedAt DESC"
    )
    fun observeNotes(): Flow<List<NoteListItem>>

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

    @Query("SELECT * FROM notes WHERE isDeleted = 0")
    suspend fun getActiveNotesSync(): List<Note>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: Note): Long

    @Update
    suspend fun update(note: Note)

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

    @Query("SELECT COUNT(*) FROM notes WHERE courseId = :courseId AND isDeleted = 0")
    suspend fun countActiveNotesForCourse(courseId: Long): Int
}
