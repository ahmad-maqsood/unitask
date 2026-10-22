package com.example.data.local

import androidx.room.Embedded
import androidx.room.Relation

data class TaskWithCourse(
    @Embedded
    val task: TaskItem,
    @Relation(
        parentColumn = "courseId",
        entityColumn = "id"
    )
    val course: Course?,
    @Relation(
        parentColumn = "id",
        entityColumn = "taskId"
    )
    val attachments: List<TaskAttachment> = emptyList()
)
