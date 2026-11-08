package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "courses")
data class Course(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val code: String,
    val instructor: String = "",
    val colorHex: String = "#4F46E5",
    val location: String = "",
    val isSynced: Boolean = false,
    val cloudId: String = "",
    val isDeleted: Boolean = false,
    val archivedSemester: String = ""
)

val Course.isArchived: Boolean
    get() = archivedSemester.isNotBlank()

/**
 * Summary of an archived semester grouping its course, task, class and note counts.
 */
data class ArchivedSemesterSummary(
    val name: String,
    val courseCount: Int,
    val taskCount: Int,
    val classCount: Int,
    val noteCount: Int,
    val courseCodes: List<String>
)
