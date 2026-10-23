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
interface TaskDao {
    @Transaction
    @Query("SELECT * FROM tasks WHERE isDeleted = 0 ORDER BY dueDateEpochMillis ASC, dueTimeHour ASC, dueTimeMinute ASC")
    fun getAllTasksWithCourse(): Flow<List<TaskWithCourse>>

    @Transaction
    @Query("SELECT * FROM tasks WHERE courseId = :courseId AND isDeleted = 0 ORDER BY dueDateEpochMillis ASC, dueTimeHour ASC, dueTimeMinute ASC")
    fun getTasksForCourse(courseId: Long): Flow<List<TaskWithCourse>>

    @Query("SELECT * FROM tasks WHERE id = :id AND isDeleted = 0")
    suspend fun getTaskById(id: Long): TaskItem?

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getAnyTaskById(id: Long): TaskItem?

    @Query("SELECT * FROM tasks WHERE courseId = :courseId AND isDeleted = 0")
    suspend fun getTasksForCourseSync(courseId: Long): List<TaskItem>

    @Query("SELECT * FROM tasks WHERE courseId = :courseId")
    suspend fun getAnyTasksForCourseSync(courseId: Long): List<TaskItem>

    @Query("SELECT * FROM tasks WHERE courseId = :courseId AND title = :title AND isDeleted = 0 LIMIT 1")
    suspend fun getTaskByTitleAndCourse(title: String, courseId: Long): TaskItem?

    @Query("SELECT * FROM tasks WHERE courseId = :courseId AND title = :title LIMIT 1")
    suspend fun getAnyTaskByTitleAndCourse(title: String, courseId: Long): TaskItem?

    @Query("SELECT * FROM tasks WHERE isSynced = 0 AND isDeleted = 0")
    suspend fun getUnsyncedTasks(): List<TaskItem>

    @Query("SELECT * FROM tasks WHERE isDeleted = 0")
    suspend fun getAllActiveTasksSync(): List<TaskItem>

    @Query("SELECT * FROM tasks WHERE cloudId = :cloudId LIMIT 1")
    suspend fun getTaskByCloudId(cloudId: String): TaskItem?
    
    // Only tasks that were never linked to a cloud document (cloudId is empty).
    @Query("SELECT * FROM tasks WHERE courseId = :courseId AND title = :title AND cloudId = '' LIMIT 1")
    suspend fun getUnlinkedTaskByTitleAndCourse(title: String, courseId: Long): TaskItem?

    @Query("UPDATE tasks SET cloudId = :cloudId WHERE id = :id")
    suspend fun setTaskCloudId(id: Long, cloudId: String)

    @Query("SELECT * FROM tasks WHERE isDeleted = 1")
    suspend fun getDeletedTasks(): List<TaskItem>

    @Query("UPDATE tasks SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markTasksSynced(ids: List<Long>)

    @Query("UPDATE tasks SET isSynced = 0 WHERE courseId = :courseId")
    suspend fun markTasksUnsyncedForCourse(courseId: Long)

    @Query("UPDATE tasks SET isSynced = 0")
    suspend fun markAllTasksUnsynced()

    @Query("UPDATE tasks SET isSynced = 1, cloudId = :cloudId WHERE id = :id")
    suspend fun markTaskSynced(id: Long, cloudId: String)

    @Query("UPDATE tasks SET isDeleted = 1, isSynced = 0 WHERE id = :id")
    suspend fun softDeleteTask(id: Long)

    @Query("UPDATE tasks SET isDeleted = 1, isSynced = 0 WHERE courseId = :courseId")
    suspend fun softDeleteTasksForCourse(courseId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskItem>): List<Long>

    @Update
    suspend fun updateTask(task: TaskItem)

    @Delete
    suspend fun deleteTask(task: TaskItem)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Long)

    @Query("DELETE FROM tasks WHERE courseId = :courseId")
    suspend fun deleteTasksForCourse(courseId: Long)

    @Query("DELETE FROM tasks WHERE courseId NOT IN (SELECT id FROM courses WHERE isDeleted = 0)")
    suspend fun deleteOrphanedTasks()

    @Query("UPDATE tasks SET status = :status, isSynced = 0 WHERE id = :taskId")
    suspend fun updateStatus(taskId: Long, status: TaskStatus)
}
