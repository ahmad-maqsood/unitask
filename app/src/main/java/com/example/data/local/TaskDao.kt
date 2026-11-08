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
    @Query("SELECT * FROM tasks WHERE isDeleted = 0 AND courseId IN (SELECT id FROM courses WHERE isDeleted = 0 AND archivedSemester = '') ORDER BY dueDateEpochMillis ASC, dueTimeHour ASC, dueTimeMinute ASC")
    fun getAllTasksWithCourse(): Flow<List<TaskWithCourse>>

    @Transaction
    @Query("SELECT * FROM tasks WHERE isDeleted = 0 ORDER BY dueDateEpochMillis ASC, dueTimeHour ASC, dueTimeMinute ASC")
    fun getAllTasksWithCourseIncludingArchived(): Flow<List<TaskWithCourse>>

    @Transaction
    @Query("SELECT * FROM tasks WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    fun observeDeletedTasks(): Flow<List<TaskWithCourse>>

    @Transaction
    @Query("SELECT * FROM tasks WHERE courseId = :courseId AND isDeleted = 0 AND courseId IN (SELECT id FROM courses WHERE isDeleted = 0 AND archivedSemester = '') ORDER BY dueDateEpochMillis ASC, dueTimeHour ASC, dueTimeMinute ASC")
    fun getTasksForCourse(courseId: Long): Flow<List<TaskWithCourse>>

    @Transaction
    @Query("SELECT * FROM tasks WHERE courseId = :courseId AND isDeleted = 0 ORDER BY dueDateEpochMillis ASC, dueTimeHour ASC, dueTimeMinute ASC")
    fun getTasksForCourseIncludingArchived(courseId: Long): Flow<List<TaskWithCourse>>

    @Query("SELECT * FROM tasks WHERE id = :id AND isDeleted = 0 AND courseId IN (SELECT id FROM courses WHERE isDeleted = 0 AND archivedSemester = '')")
    suspend fun getTaskById(id: Long): TaskItem?

    @Query("SELECT * FROM tasks WHERE id = :id AND isDeleted = 0")
    suspend fun getTaskByIdIncludingArchived(id: Long): TaskItem?

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getAnyTaskById(id: Long): TaskItem?

    @Query("SELECT * FROM tasks WHERE courseId = :courseId AND isDeleted = 0")
    suspend fun getTasksForCourseSync(courseId: Long): List<TaskItem>

    @Query("SELECT * FROM tasks WHERE courseId = :courseId")
    suspend fun getAnyTasksForCourseSync(courseId: Long): List<TaskItem>

    @Query("SELECT * FROM tasks WHERE courseId = :courseId AND title = :title AND isDeleted = 0 AND courseId IN (SELECT id FROM courses WHERE isDeleted = 0 AND archivedSemester = '') LIMIT 1")
    suspend fun getTaskByTitleAndCourse(title: String, courseId: Long): TaskItem?

    @Query("SELECT * FROM tasks WHERE courseId = :courseId AND title = :title LIMIT 1")
    suspend fun getAnyTaskByTitleAndCourse(title: String, courseId: Long): TaskItem?

    @Query("SELECT * FROM tasks WHERE isDeleted = 0 AND courseId IN (SELECT id FROM courses WHERE isDeleted = 0 AND archivedSemester = '')")
    suspend fun getAllActiveTasksSync(): List<TaskItem>

    @Query("SELECT * FROM tasks WHERE isDeleted = 0")
    suspend fun getAllActiveTasksSyncIncludingArchived(): List<TaskItem>

    @Query("SELECT * FROM tasks WHERE isDeleted = 1")
    suspend fun getDeletedTasks(): List<TaskItem>

    @Query("SELECT * FROM tasks WHERE isDeleted = 1")
    suspend fun getDeletedTasksSync(): List<TaskItem>

    @Query("UPDATE tasks SET isDeleted = 1, deletedAt = :deletedAt, isSynced = 0 WHERE id = :id")
    suspend fun softDeleteTask(id: Long, deletedAt: Long)

    @Query("UPDATE tasks SET isDeleted = 0, deletedAt = 0, isSynced = 0 WHERE id = :id")
    suspend fun restoreTask(id: Long)

    @Query("DELETE FROM tasks WHERE isDeleted = 1 AND deletedAt > 0 AND deletedAt < :cutoff")
    suspend fun purgeDeletedBefore(cutoff: Long): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE courseId = :courseId")
    suspend fun countTasksForCourse(courseId: Long): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE courseId = :courseId AND isDeleted = 0")
    suspend fun countActiveTasksForCourse(courseId: Long): Int

    @Query("UPDATE tasks SET isDeleted = 1, isSynced = 0 WHERE courseId = :courseId")
    suspend fun softDeleteTasksForCourse(courseId: Long)

    @Query("UPDATE tasks SET courseId = :newCourseId WHERE courseId = :oldCourseId")
    suspend fun reassignTasksToCourse(oldCourseId: Long, newCourseId: Long)

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

    // Same as updateStatus, and also stores when the task was ticked (0 when it is not Done).
    @Query("UPDATE tasks SET status = :status, completedAtMillis = :completedAt, isSynced = 0 WHERE id = :taskId")
    suspend fun updateStatusAndCompletedAt(taskId: Long, status: TaskStatus, completedAt: Long)

    @Query("UPDATE tasks SET description = :description, isSynced = 0 WHERE id = :taskId")
    suspend fun updateDescription(taskId: Long, description: String)
}
