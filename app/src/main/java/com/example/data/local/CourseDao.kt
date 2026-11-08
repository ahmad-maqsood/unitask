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
    @Query("SELECT * FROM courses WHERE isDeleted = 0 AND archivedSemester = '' ORDER BY code ASC")
    fun getAllCourses(): Flow<List<Course>>

    @Query("SELECT * FROM courses WHERE isDeleted = 0 ORDER BY code ASC")
    fun getAllCoursesIncludingArchived(): Flow<List<Course>>

    @Query("SELECT * FROM courses WHERE isDeleted = 0 AND archivedSemester = '' ORDER BY code ASC")
    suspend fun getAllCoursesList(): List<Course>

    @Query("SELECT * FROM courses WHERE isDeleted = 0 ORDER BY code ASC")
    suspend fun getAllCoursesListIncludingArchived(): List<Course>

    @Query("SELECT * FROM courses WHERE id = :id AND isDeleted = 0 AND archivedSemester = ''")
    fun getCourseById(id: Long): Flow<Course?>

    @Query("SELECT * FROM courses WHERE id = :id AND isDeleted = 0")
    fun getCourseByIdIncludingArchived(id: Long): Flow<Course?>

    @Query("SELECT * FROM courses WHERE id = :id AND isDeleted = 0 AND archivedSemester = ''")
    suspend fun getCourseByIdSync(id: Long): Course?

    @Query("SELECT * FROM courses WHERE id = :id AND isDeleted = 0")
    suspend fun getCourseByIdSyncIncludingArchived(id: Long): Course?

    @Query("SELECT * FROM courses WHERE id = :id")
    suspend fun getAnyCourseByIdSync(id: Long): Course?

    @Query("SELECT * FROM courses WHERE UPPER(TRIM(code)) = UPPER(TRIM(:code)) AND isDeleted = 0 AND archivedSemester = '' LIMIT 1")
    suspend fun getCourseByCode(code: String): Course?

    @Query("SELECT * FROM courses WHERE UPPER(TRIM(code)) = UPPER(TRIM(:code)) LIMIT 1")
    suspend fun getAnyCourseByCode(code: String): Course?

    @Query("SELECT * FROM courses WHERE REPLACE(UPPER(TRIM(code)), ' ', '') = REPLACE(UPPER(TRIM(:code)), ' ', '') AND isDeleted = 0 AND archivedSemester = '' LIMIT 1")
    suspend fun getCourseByNormalizedCode(code: String): Course?

    @Query("SELECT * FROM courses WHERE REPLACE(UPPER(TRIM(code)), ' ', '') = REPLACE(UPPER(TRIM(:code)), ' ', '') LIMIT 1")
    suspend fun getAnyCourseByNormalizedCode(code: String): Course?

    @Query("SELECT * FROM courses WHERE REPLACE(UPPER(TRIM(code)), ' ', '') = REPLACE(UPPER(TRIM(:code)), ' ', '')")
    suspend fun getAllCoursesMatchingNormalizedCode(code: String): List<Course>

    @Query("SELECT * FROM courses WHERE cloudId = :cloudId LIMIT 1")
    suspend fun getCourseByCloudId(cloudId: String): Course?

    @Query("SELECT COUNT(*) > 0 FROM courses WHERE REPLACE(UPPER(TRIM(code)), ' ', '') = REPLACE(UPPER(TRIM(:code)), ' ', '') AND isDeleted = 1")
    suspend fun isCourseDeletedByCode(code: String): Boolean

    @Query("SELECT * FROM courses WHERE isSynced = 0 AND isDeleted = 0")
    suspend fun getUnsyncedCourses(): List<Course>

    @Query("SELECT * FROM courses WHERE isDeleted = 1")
    suspend fun getDeletedCourses(): List<Course>

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

    @Query("SELECT * FROM courses WHERE REPLACE(UPPER(TRIM(code)), ' ', '') = REPLACE(UPPER(TRIM(:code)), ' ', '') AND isDeleted = 0 AND archivedSemester != '' LIMIT 1")
    suspend fun findArchivedCourseByCode(code: String): Course?

    @Query("SELECT * FROM courses WHERE isDeleted = 0 AND archivedSemester != '' ORDER BY archivedSemester ASC, code ASC")
    suspend fun getArchivedCoursesSync(): List<Course>

    @Query("SELECT * FROM courses WHERE isDeleted = 0 AND archivedSemester != '' ORDER BY archivedSemester ASC, code ASC")
    fun observeArchivedCourses(): Flow<List<Course>>

    @Query("SELECT * FROM courses WHERE UPPER(TRIM(archivedSemester)) = UPPER(TRIM(:semesterName)) AND isDeleted = 0")
    suspend fun getCoursesForSemester(semesterName: String): List<Course>

    @Query("UPDATE courses SET archivedSemester = :semesterName, isSynced = 0 WHERE id IN (:courseIds)")
    suspend fun setArchivedSemester(courseIds: List<Long>, semesterName: String)

    @Query("UPDATE courses SET archivedSemester = '', isSynced = 0 WHERE UPPER(TRIM(archivedSemester)) = UPPER(TRIM(:semesterName))")
    suspend fun clearArchivedSemester(semesterName: String)

    @Query("UPDATE courses SET archivedSemester = '', isSynced = 0 WHERE id IN (:courseIds)")
    suspend fun clearArchivedSemesterForCourses(courseIds: List<Long>)
}
