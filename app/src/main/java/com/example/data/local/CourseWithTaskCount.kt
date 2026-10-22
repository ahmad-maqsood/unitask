package com.example.data.local

data class CourseWithTaskCount(
    val course: Course,
    val pendingTasksCount: Int,
    val completedTasksCount: Int,
    val totalTasksCount: Int
) {
    val completionPercentage: Float
        get() = if (totalTasksCount > 0) completedTasksCount.toFloat() / totalTasksCount else 0f
}
