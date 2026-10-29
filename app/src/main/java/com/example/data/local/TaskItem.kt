package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class Priority {
    LOW,
    MEDIUM,
    HIGH
}

// Old data may still contain IN_PROGRESS. Converters.toTaskStatus() and the sync
// code read unknown values as PENDING, so no data migration is needed.
enum class TaskStatus {
    PENDING,
    COMPLETED
}

// A task can have any number of these. An empty set means no reminder.
// Append new options at the end; do not reorder.
enum class ReminderOption(val label: String, val offsetMinutes: Long) {
    AT_DEADLINE("At deadline", 0),
    TWO_HOURS_BEFORE("2 hours before", 120),
    ONE_DAY_BEFORE("1 day before", 1440),
    ONE_HOUR_BEFORE("1 hour before", 60),
    TWO_DAYS_BEFORE("2 days before", 2880),
    TWELVE_HOURS_BEFORE("12 hours before", 720)
}

private const val NO_REMINDERS = "NONE"

/**
 * Stored as comma-separated enum names, e.g. "AT_DEADLINE,ONE_DAY_BEFORE", or "NONE".
 * A single old-style value such as "TWO_HOURS_BEFORE" decodes to a one-item set, so
 * existing local rows and cloud documents keep working.
 */
fun encodeReminderOptions(options: Set<ReminderOption>): String =
    if (options.isEmpty()) NO_REMINDERS
    else options.sortedBy { it.ordinal }.joinToString(",") { it.name }

fun decodeReminderOptions(value: String?): Set<ReminderOption> =
    value.orEmpty()
        .split(",")
        .mapNotNull { part -> ReminderOption.entries.firstOrNull { it.name == part.trim() } }
        .toSet()

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
    val dueTimeHour: Int = 12,
    val dueTimeMinute: Int = 0,
    val priority: Priority = Priority.MEDIUM,
    val status: TaskStatus = TaskStatus.PENDING,
    // Keeps the original column name and TEXT type so the Room schema is unchanged.
    @ColumnInfo(name = "reminderOption")
    val reminderOptions: Set<ReminderOption> = emptySet(),
    // Earliest upcoming reminder time, or null when there is none.
    val reminderEpochMillis: Long? = null,
    val isSynced: Boolean = false,
    val cloudId: String = "",
    val isDeleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val repeatRule: String = "NONE"
)
/**
 * The real deadline in milliseconds. `dueDateEpochMillis` keeps whatever clock time the date was
 * picked at, so never sort on it alone. Use this instead.
 */
fun computeDeadlineMillis(dueDateEpochMillis: Long, hour: Int, minute: Int): Long =
    java.util.Calendar.getInstance().apply {
        timeInMillis = dueDateEpochMillis
        set(java.util.Calendar.HOUR_OF_DAY, hour)
        set(java.util.Calendar.MINUTE, minute)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis

val TaskItem.deadlineMillis: Long
    get() = computeDeadlineMillis(dueDateEpochMillis, dueTimeHour, dueTimeMinute)