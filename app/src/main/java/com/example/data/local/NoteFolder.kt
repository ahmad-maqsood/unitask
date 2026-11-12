package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

// A folder the user created to organise notes.
// Notes point at a folder with Note.folderId. There is deliberately no foreign key,
// so removing a folder can never delete the user's writing: the notes just become "unfiled".
@Entity(tableName = "note_folders")
data class NoteFolder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val colorHex: String = ""   // one of CourseColors, or empty for no colour
)
