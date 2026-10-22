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
    @Query("SELECT * FROM lecture_slots ORDER BY dayOfWeek ASC, startHour ASC, startMinute ASC")
    fun getAllSlotsWithCourse(): Flow<List<LectureSlotWithCourse>>

    @Transaction
    @Query("SELECT * FROM lecture_slots WHERE dayOfWeek = :dayOfWeek ORDER BY startHour ASC, startMinute ASC")
    fun getSlotsForDay(dayOfWeek: Int): Flow<List<LectureSlotWithCourse>>

    @Transaction
    @Query("SELECT * FROM lecture_slots WHERE courseId = :courseId ORDER BY dayOfWeek ASC, startHour ASC, startMinute ASC")
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
}
