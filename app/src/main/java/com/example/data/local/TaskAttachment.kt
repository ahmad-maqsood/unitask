package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "task_attachments",
    foreignKeys = [
        ForeignKey(
            entity = TaskItem::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["taskId"])]
)
data class TaskAttachment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val taskId: Long,
    val fileName: String,
    val relativePath: String,
    val fileSize: Long,
    val mimeType: String,
    val createdAt: Long = System.currentTimeMillis()
)
