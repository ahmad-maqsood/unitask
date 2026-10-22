package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class Priority {
    LOW,
    MEDIUM,
    HIGH
}

enum class TaskStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED
}

enum class ReminderOption(val label: String, val offsetMinutes: Long) {
    NONE("No reminder", 0),
    AT_DEADLINE("At deadline", 0),
    TWO_HOURS_BEFORE("2 hours before", 120),
    ONE_DAY_BEFORE("1 day before", 1440)
}

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = Course::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["courseId"])]
)
data class TaskItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val courseId: Long,
    val title: String,
    val description: String = "",
    val dueDateEpochMillis: Long,
    val dueTimeHour: Int = 23,
    val dueTimeMinute: Int = 59,
    val priority: Priority = Priority.MEDIUM,
    val status: TaskStatus = TaskStatus.PENDING,
    val reminderOption: ReminderOption = ReminderOption.NONE,
    val reminderEpochMillis: Long? = null,
    val isSynced: Boolean = false,
    val cloudId: String = "",
    val isDeleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
