package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskAttachmentDao {
    @Query("SELECT * FROM task_attachments WHERE taskId = :taskId")
    fun getAttachmentsForTask(taskId: Long): Flow<List<TaskAttachment>>

    @Query("SELECT * FROM task_attachments WHERE taskId = :taskId")
    suspend fun getAttachmentsForTaskSync(taskId: Long): List<TaskAttachment>

    @Query("SELECT * FROM task_attachments WHERE id = :id")
    suspend fun getAttachmentById(id: Long): TaskAttachment?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachment(attachment: TaskAttachment): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachments(attachments: List<TaskAttachment>): List<Long>

    @Delete
    suspend fun deleteAttachment(attachment: TaskAttachment)

    @Query("DELETE FROM task_attachments WHERE id = :id")
    suspend fun deleteAttachmentById(id: Long)

    @Query("DELETE FROM task_attachments WHERE taskId = :taskId")
    suspend fun deleteAttachmentsForTask(taskId: Long)
}
