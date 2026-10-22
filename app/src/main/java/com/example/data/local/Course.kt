package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "courses")
data class Course(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val code: String,
    val instructor: String,
    val colorHex: String,
    val location: String = "",
    val isSynced: Boolean = false,
    val cloudId: String = "",
    val isDeleted: Boolean = false
)
