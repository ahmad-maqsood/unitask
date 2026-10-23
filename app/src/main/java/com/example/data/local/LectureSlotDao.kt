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
    @Query("SELECT * FROM lecture_slots WHERE isDeleted = 0 ORDER BY dayOfWeek ASC, startHour ASC, startMinute ASC")
    fun getAllSlotsWithCourse(): Flow<List<LectureSlotWithCourse>>

    @Transaction
    @Query("SELECT * FROM lecture_slots WHERE dayOfWeek = :dayOfWeek AND isDeleted = 0 ORDER BY startHour ASC, startMinute ASC")
    fun getSlotsForDay(dayOfWeek: Int): Flow<List<LectureSlotWithCourse>>

    @Transaction
    @Query("SELECT * FROM lecture_slots WHERE courseId = :courseId AND isDeleted = 0 ORDER BY dayOfWeek ASC, startHour ASC, startMinute ASC")
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

    @Query("SELECT * FROM lecture_slots")
    suspend fun getAllSlotsSync(): List<LectureSlot>

    @Query("SELECT * FROM lecture_slots WHERE courseId = :courseId")
    suspend fun getSlotsForCourseSync(courseId: Long): List<LectureSlot>

    @Query("SELECT * FROM lecture_slots WHERE courseId = :courseId AND dayOfWeek = :dayOfWeek AND startHour = :startHour AND startMinute = :startMinute LIMIT 1")
    suspend fun findMatchingSlot(courseId: Long, dayOfWeek: Int, startHour: Int, startMinute: Int): LectureSlot?

    @Query("SELECT * FROM lecture_slots WHERE cloudId = :cloudId LIMIT 1")
    suspend fun getSlotByCloudId(cloudId: String): LectureSlot?

    @Query("UPDATE lecture_slots SET isSynced = 1, cloudId = :cloudId WHERE id = :id")
    suspend fun markSlotSynced(id: Long, cloudId: String)

    @Query("UPDATE lecture_slots SET isSynced = 0 WHERE courseId = :courseId")
    suspend fun markSlotsUnsyncedForCourse(courseId: Long)

    
    @Query("UPDATE lecture_slots SET isSynced = 0")
    suspend fun markAllSlotsUnsynced()
    
    @Query("SELECT * FROM lecture_slots WHERE isDeleted = 1")
    suspend fun getDeletedSlots(): List<LectureSlot>

    @Query("UPDATE lecture_slots SET isDeleted = 1, isSynced = 0 WHERE id = :id")
    suspend fun softDeleteSlot(id: Long)
}
