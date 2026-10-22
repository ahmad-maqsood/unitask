package com.example.data.local

import androidx.room.Embedded
import androidx.room.Relation

data class LectureSlotWithCourse(
    @Embedded
    val slot: LectureSlot,
    @Relation(
        parentColumn = "courseId",
        entityColumn = "id"
    )
    val course: Course?
)
