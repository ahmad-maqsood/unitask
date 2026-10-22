package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses WHERE isDeleted = 0 ORDER BY code ASC")
    fun getAllCourses(): Flow<List<Course>>

    @Query("SELECT * FROM courses WHERE isDeleted = 0 ORDER BY code ASC")
    suspend fun getAllCoursesList(): List<Course>

    @Query("SELECT * FROM courses WHERE id = :id AND isDeleted = 0")
    fun getCourseById(id: Long): Flow<Course?>

    @Query("SELECT * FROM courses WHERE id = :id AND isDeleted = 0")
    suspend fun getCourseByIdSync(id: Long): Course?

    @Query("SELECT * FROM courses WHERE id = :id")
    suspend fun getAnyCourseByIdSync(id: Long): Course?

    @Query("SELECT * FROM courses WHERE code = :code AND isDeleted = 0 LIMIT 1")
    suspend fun getCourseByCode(code: String): Course?

    @Query("SELECT * FROM courses WHERE code = :code LIMIT 1")
    suspend fun getAnyCourseByCode(code: String): Course?

    @Query("SELECT COUNT(*) > 0 FROM courses WHERE code = :code AND isDeleted = 1")
    suspend fun isCourseDeletedByCode(code: String): Boolean

    @Query("SELECT * FROM courses WHERE isSynced = 0 AND isDeleted = 0")
    suspend fun getUnsyncedCourses(): List<Course>

    @Query("SELECT * FROM courses WHERE isDeleted = 1")
    suspend fun getDeletedCourses(): List<Course>

    @Query("UPDATE courses SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markCoursesSynced(ids: List<Long>)

    @Query("UPDATE courses SET isSynced = 1, cloudId = :cloudId WHERE id = :id")
    suspend fun markCourseSynced(id: Long, cloudId: String)

    @Query("UPDATE courses SET isDeleted = 1, isSynced = 0 WHERE id = :id")
    suspend fun softDeleteCourse(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourse(course: Course): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourses(courses: List<Course>): List<Long>

    @Update
    suspend fun updateCourse(course: Course)

    @Delete
    suspend fun deleteCourse(course: Course)

    @Query("DELETE FROM courses WHERE id = :id")
    suspend fun deleteCourseById(id: Long)
}
