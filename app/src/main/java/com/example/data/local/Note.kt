package com.example.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// A note written by the user.
// There is deliberately no foreign key to courses because deleting a course
// must never delete the user's writing.
@Entity(tableName = "notes", indices = [Index("courseId"), Index("updatedAt")])
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val body: String = "",
    val courseId: Long? = null,        // optional course tag, deliberately NO foreign key
    val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,    // soft delete, so Undo and "Recently deleted" work
    val deletedAt: Long = 0L,
    val isSynced: Boolean = false,     // reserved for cloud sync
    val cloudId: String = "",          // reserved for cloud sync
    val cloudUpdatedAt: Long = 0L,     // reserved: the cloud's updatedAt when we last synced
    val keepScreenOn: Boolean = false  // keep the screen awake while this note is open, off by default
)

data class NoteListItem(
    @Embedded val note: Note,
    val courseCode: String?,
    val courseName: String?,
    val courseColorHex: String?
)

