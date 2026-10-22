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

    @TypeConverter
    fun toTaskStatus(value: String): TaskStatus = try {
        TaskStatus.valueOf(value)
    } catch (_: Exception) {
        TaskStatus.PENDING
    }

    @TypeConverter
    fun fromReminderOption(option: ReminderOption): String = option.name

    @TypeConverter
    fun toReminderOption(value: String): ReminderOption = try {
        ReminderOption.valueOf(value)
    } catch (_: Exception) {
        ReminderOption.NONE
    }
}
