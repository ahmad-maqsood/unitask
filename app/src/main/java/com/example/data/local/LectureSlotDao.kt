package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LectureSlotDao {
    @Transaction
    @Query("SELECT * FROM lecture_slots WHERE isDeleted = 0 AND courseId IN (SELECT id FROM courses WHERE isDeleted = 0 AND archivedSemester = '') ORDER BY dayOfWeek ASC, startHour ASC, startMinute ASC")
    fun getAllSlotsWithCourse(): Flow<List<LectureSlotWithCourse>>

    @Transaction
    @Query("SELECT * FROM lecture_slots WHERE isDeleted = 0 ORDER BY dayOfWeek ASC, startHour ASC, startMinute ASC")
    fun getAllSlotsWithCourseIncludingArchived(): Flow<List<LectureSlotWithCourse>>

    @Transaction
    @Query("SELECT * FROM lecture_slots WHERE dayOfWeek = :dayOfWeek AND isDeleted = 0 AND courseId IN (SELECT id FROM courses WHERE isDeleted = 0 AND archivedSemester = '') ORDER BY startHour ASC, startMinute ASC")
    fun getSlotsForDay(dayOfWeek: Int): Flow<List<LectureSlotWithCourse>>

    @Transaction
    @Query("SELECT * FROM lecture_slots WHERE courseId = :courseId AND isDeleted = 0 AND courseId IN (SELECT id FROM courses WHERE isDeleted = 0 AND archivedSemester = '') ORDER BY dayOfWeek ASC, startHour ASC, startMinute ASC")
    fun getSlotsForCourse(courseId: Long): Flow<List<LectureSlotWithCourse>>

    @Query("SELECT * FROM lecture_slots WHERE id = :id")
    suspend fun getSlotById(id: Long): LectureSlot?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSlot(slot: LectureSlot): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSlots(slots: List<LectureSlot>): List<Long>

    @Update
    suspend fun updateSlot(slot: LectureSlot)

    @Delete
    suspend fun deleteSlot(slot: LectureSlot)

    @Query("DELETE FROM lecture_slots WHERE id = :id")
    suspend fun deleteSlotById(id: Long)

    @Query("DELETE FROM lecture_slots WHERE courseId = :courseId")
    suspend fun deleteSlotsForCourse(courseId: Long)

    @Query("DELETE FROM lecture_slots WHERE courseId NOT IN (SELECT id FROM courses WHERE isDeleted = 0)")
    suspend fun deleteOrphanedSlots()

    @Query("SELECT * FROM lecture_slots WHERE isDeleted = 0 AND courseId IN (SELECT id FROM courses WHERE isDeleted = 0 AND archivedSemester = '')")
    suspend fun getAllSlotsSync(): List<LectureSlot>

    @Query("SELECT * FROM lecture_slots")
    suspend fun getAllSlotsSyncIncludingArchived(): List<LectureSlot>

    @Query("SELECT * FROM lecture_slots WHERE courseId = :courseId")
    suspend fun getSlotsForCourseSync(courseId: Long): List<LectureSlot>

    @Query("SELECT COUNT(*) FROM lecture_slots WHERE courseId = :courseId")
    suspend fun countSlotsForCourse(courseId: Long): Int

    @Query("SELECT COUNT(*) FROM lecture_slots WHERE courseId = :courseId AND isDeleted = 0")
    suspend fun countActiveSlotsForCourse(courseId: Long): Int

    @Query("SELECT * FROM lecture_slots WHERE courseId = :courseId AND dayOfWeek = :dayOfWeek AND startHour = :startHour AND startMinute = :startMinute LIMIT 1")
    suspend fun findMatchingSlot(courseId: Long, dayOfWeek: Int, startHour: Int, startMinute: Int): LectureSlot?

    @Query("SELECT * FROM lecture_slots WHERE isDeleted = 1")
    suspend fun getDeletedSlots(): List<LectureSlot>

    @Transaction
    @Query("SELECT * FROM lecture_slots WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    fun observeDeletedSlots(): Flow<List<LectureSlotWithCourse>>

    @Query("SELECT * FROM lecture_slots WHERE isDeleted = 1")
    suspend fun getDeletedSlotsSync(): List<LectureSlot>

    @Query("UPDATE lecture_slots SET isDeleted = 1, deletedAt = :deletedAt, isSynced = 0 WHERE id = :id")
    suspend fun softDeleteSlot(id: Long, deletedAt: Long)

    @Query("UPDATE lecture_slots SET isDeleted = 0, deletedAt = 0, isSynced = 0 WHERE id = :id")
    suspend fun restoreSlot(id: Long)

    @Query("DELETE FROM lecture_slots WHERE isDeleted = 1 AND deletedAt > 0 AND deletedAt < :cutoff")
    suspend fun purgeDeletedBefore(cutoff: Long): Int

    @Query("UPDATE lecture_slots SET courseId = :newCourseId WHERE courseId = :oldCourseId")
    suspend fun reassignSlotsToCourse(oldCourseId: Long, newCourseId: Long)
}
