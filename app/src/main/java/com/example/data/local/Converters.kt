package com.example.data.local

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromPriority(priority: Priority): String = priority.name

    @TypeConverter
    fun toPriority(value: String): Priority = try {
        Priority.valueOf(value)
    } catch (_: Exception) {
        Priority.MEDIUM
    }

    @TypeConverter
    fun fromTaskStatus(status: TaskStatus): String = status.name

    // Unknown values, including the removed IN_PROGRESS, read as PENDING.
    @TypeConverter
    fun toTaskStatus(value: String): TaskStatus = try {
        TaskStatus.valueOf(value)
    } catch (_: Exception) {
        TaskStatus.PENDING
    }

    @TypeConverter
    fun fromReminderOptions(options: Set<ReminderOption>): String = encodeReminderOptions(options)

    @TypeConverter
    fun toReminderOptions(value: String): Set<ReminderOption> = decodeReminderOptions(value)
}
