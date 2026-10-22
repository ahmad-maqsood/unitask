package com.example.data.repository

import android.content.Context
import com.example.data.local.Course
import com.example.data.local.CourseDao
import com.example.data.local.CourseWithTaskCount
import com.example.data.local.LectureSlot
import com.example.data.local.LectureSlotDao
import com.example.data.local.LectureSlotWithCourse
import com.example.data.local.TaskAttachment
import com.example.data.local.TaskAttachmentDao
import com.example.data.local.TaskDao
import com.example.data.local.TaskItem
import com.example.data.local.TaskStatus
import com.example.data.local.TaskWithCourse
import android.util.Log
import com.example.reminder.ReminderScheduler
import com.example.sync.FirestoreSyncManager
import com.example.sync.SyncScheduler
import com.example.util.AttachmentManager
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppRepository(
    private val context: Context,
    private val courseDao: CourseDao,
    private val taskDao: TaskDao,
    private val taskAttachmentDao: TaskAttachmentDao,
    private val lectureSlotDao: LectureSlotDao,
    private val reminderScheduler: ReminderScheduler
) {

    val allCourses: Flow<List<Course>> = courseDao.getAllCourses().flowOn(Dispatchers.IO)

    val allTasksWithCourse: Flow<List<TaskWithCourse>> = taskDao.getAllTasksWithCourse().flowOn(Dispatchers.IO)

    val allLectureSlots: Flow<List<LectureSlotWithCourse>> = lectureSlotDao.getAllSlotsWithCourse().flowOn(Dispatchers.IO)

    val coursesWithTaskCounts: Flow<List<CourseWithTaskCount>> = combine(
        allCourses,
        allTasksWithCourse
    ) { courses, tasks ->
        courses.map { course ->
            val courseTasks = tasks.filter { it.task.courseId == course.id }
            val pendingCount = courseTasks.count { it.task.status != TaskStatus.COMPLETED }
            val completedCount = courseTasks.count { it.task.status == TaskStatus.COMPLETED }
            CourseWithTaskCount(
                course = course,
                pendingTasksCount = pendingCount,
                completedTasksCount = completedCount,
                totalTasksCount = courseTasks.size
            )
        }
    }.flowOn(Dispatchers.Default)

    fun getTasksForCourse(courseId: Long): Flow<List<TaskWithCourse>> {
        return taskDao.getTasksForCourse(courseId).flowOn(Dispatchers.IO)
    }

    fun getSlotsForCourse(courseId: Long): Flow<List<LectureSlotWithCourse>> {
        return lectureSlotDao.getSlotsForCourse(courseId).flowOn(Dispatchers.IO)
    }

    suspend fun insertCourse(course: Course): Long = withContext(Dispatchers.IO) {
        val normCode = course.code.trim().uppercase()
        FirestoreSyncManager.clearPendingDeletionForCourse(normCode)

        // If a previously soft-deleted course exists with the same code, purge it first
        val existingDeleted = courseDao.getAnyCourseByCode(normCode)
        if (existingDeleted != null && existingDeleted.isDeleted) {
            taskDao.deleteTasksForCourse(existingDeleted.id)
            lectureSlotDao.deleteSlotsForCourse(existingDeleted.id)
            courseDao.deleteCourseById(existingDeleted.id)
        }
        val courseToInsert = course.copy(code = normCode, isSynced = false, isDeleted = false)
        val id = courseDao.insertCourse(courseToInsert)
        triggerSyncIfLoggedIn()
        id
    }

    suspend fun updateCourse(course: Course) = withContext(Dispatchers.IO) {
        val courseToUpdate = course.copy(code = course.code.trim().uppercase(), isSynced = false)
        courseDao.updateCourse(courseToUpdate)
        triggerSyncIfLoggedIn()
    }

    suspend fun deleteCourse(course: Course) = withContext(Dispatchers.IO) {
        // 1. Cancel reminders for all tasks under this course and delete attachment files
        val tasks = taskDao.getTasksForCourseSync(course.id)
        for (t in tasks) {
            reminderScheduler.cancelReminder(t.id)
            val attachments = taskAttachmentDao.getAttachmentsForTaskSync(t.id)
            for (att in attachments) {
                AttachmentManager.deleteAttachmentFile(context, att.relativePath)
            }
        }

        // 2. Remove lecture slots for this course
        val slots = lectureSlotDao.getSlotsForCourseSync(course.id)
        lectureSlotDao.deleteSlotsForCourse(course.id)

        // 3. Soft-delete course and tasks in Room (ensures immediate disappearance from UI and guards against resurrection)
        courseDao.softDeleteCourse(course.id)
        taskDao.softDeleteTasksForCourse(course.id)

        // 4. Record in pending deletion guards
        FirestoreSyncManager.recordPendingCourseDeletion(course.id, course.code, course.cloudId)
        for (t in tasks) {
            FirestoreSyncManager.recordPendingTaskDeletion(t.id, t.cloudId)
        }
        for (s in slots) {
            FirestoreSyncManager.recordPendingSlotDeletion(s.id, s.cloudId)
        }

        // 5. If user is authenticated, immediately issue Cloud Firestore deletion
        val currentUser = Firebase.auth.currentUser
        if (currentUser != null) {
            try {
                val success = FirestoreSyncManager.deleteCourseFromCloud(context, course, tasks, slots)
                if (success) {
                    taskDao.deleteTasksForCourse(course.id)
                    courseDao.deleteCourseById(course.id)
                }
            } catch (e: Exception) {
                Log.e("AppRepository", "Error deleting course from cloud: ${e.message}", e)
            }
        } else {
            if (!course.isSynced && course.cloudId.isBlank()) {
                taskDao.deleteTasksForCourse(course.id)
                courseDao.deleteCourseById(course.id)
            }
        }

        triggerSyncIfLoggedIn()
    }

    suspend fun insertTask(task: TaskItem): Long = withContext(Dispatchers.IO) {
        val calculatedReminder = reminderScheduler.calculateReminderMillis(task)
        val taskToInsert = task.copy(reminderEpochMillis = calculatedReminder, isSynced = false, isDeleted = false)
        val id = taskDao.insertTask(taskToInsert)
        val savedTask = taskToInsert.copy(id = id)

        val course = courseDao.getCourseByIdSync(savedTask.courseId)
        val courseCode = course?.code ?: "Course"
        reminderScheduler.scheduleReminder(savedTask, courseCode)
        triggerSyncIfLoggedIn()
        id
    }

    suspend fun updateTask(task: TaskItem) = withContext(Dispatchers.IO) {
        val calculatedReminder = reminderScheduler.calculateReminderMillis(task)
        val taskToUpdate = task.copy(reminderEpochMillis = calculatedReminder, isSynced = false)
        taskDao.updateTask(taskToUpdate)

        if (taskToUpdate.status == TaskStatus.COMPLETED) {
            reminderScheduler.cancelReminder(taskToUpdate.id)
        } else {
            val course = courseDao.getCourseByIdSync(taskToUpdate.courseId)
            val courseCode = course?.code ?: "Course"
            reminderScheduler.scheduleReminder(taskToUpdate, courseCode)
        }
        triggerSyncIfLoggedIn()
    }

    suspend fun updateTaskStatus(taskId: Long, newStatus: TaskStatus) = withContext(Dispatchers.IO) {
        taskDao.updateStatus(taskId, newStatus)
        if (newStatus == TaskStatus.COMPLETED) {
            reminderScheduler.cancelReminder(taskId)
        } else {
            val task = taskDao.getTaskById(taskId)
            if (task != null) {
                val course = courseDao.getCourseByIdSync(task.courseId)
                reminderScheduler.scheduleReminder(task, course?.code ?: "Course")
            }
        }
        triggerSyncIfLoggedIn()
    }

    suspend fun deleteTask(task: TaskItem) = withContext(Dispatchers.IO) {
        reminderScheduler.cancelReminder(task.id)
        // Clean up any attachment files from disk
        val attachments = taskAttachmentDao.getAttachmentsForTaskSync(task.id)
        for (att in attachments) {
            AttachmentManager.deleteAttachmentFile(context, att.relativePath)
        }

        // Soft-delete in Room first
        taskDao.softDeleteTask(task.id)
        FirestoreSyncManager.recordPendingTaskDeletion(task.id, task.cloudId)

        val currentUser = Firebase.auth.currentUser
        if (currentUser != null) {
            val course = courseDao.getAnyCourseByIdSync(task.courseId)
            try {
                val success = FirestoreSyncManager.deleteTaskFromCloud(context, task, course?.code)
                if (success) {
                    taskDao.deleteTaskById(task.id)
                }
            } catch (e: Exception) {
                Log.e("AppRepository", "Error deleting task from cloud: ${e.message}", e)
            }
        } else {
            if (!task.isSynced && task.cloudId.isBlank()) {
                taskDao.deleteTaskById(task.id)
            }
        }

        triggerSyncIfLoggedIn()
    }

    // Attachments
    fun getAttachmentsForTask(taskId: Long): Flow<List<TaskAttachment>> {
        return taskAttachmentDao.getAttachmentsForTask(taskId).flowOn(Dispatchers.IO)
    }

    suspend fun insertAttachment(attachment: TaskAttachment): Long = withContext(Dispatchers.IO) {
        taskAttachmentDao.insertAttachment(attachment)
    }

    suspend fun deleteAttachment(attachment: TaskAttachment) = withContext(Dispatchers.IO) {
        AttachmentManager.deleteAttachmentFile(context, attachment.relativePath)
        taskAttachmentDao.deleteAttachment(attachment)
    }

    // Lecture slots
    suspend fun insertLectureSlot(slot: LectureSlot): Long = withContext(Dispatchers.IO) {
        val id = lectureSlotDao.insertSlot(slot.copy(isSynced = false))
        triggerSyncIfLoggedIn()
        id
    }

    suspend fun updateLectureSlot(slot: LectureSlot) = withContext(Dispatchers.IO) {
        lectureSlotDao.updateSlot(slot.copy(isSynced = false))
        triggerSyncIfLoggedIn()
    }

    suspend fun deleteLectureSlot(slot: LectureSlot) = withContext(Dispatchers.IO) {
        FirestoreSyncManager.recordPendingSlotDeletion(slot.id, slot.cloudId)
        val course = courseDao.getCourseByIdSync(slot.courseId) ?: courseDao.getAnyCourseByIdSync(slot.courseId)
        val currentUser = Firebase.auth.currentUser
        if (currentUser != null) {
            try {
                FirestoreSyncManager.deleteSlotFromCloud(context, slot, course?.code)
            } catch (e: Exception) {
                Log.w("AppRepository", "Error deleting slot from cloud: ${e.message}")
            }
        }
        lectureSlotDao.deleteSlot(slot)
        triggerSyncIfLoggedIn()
    }

    fun triggerSyncIfLoggedIn() {
        if (Firebase.auth.currentUser != null) {
            // Eager immediate background sync
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = com.example.data.local.AppDatabase.getDatabase(context)
                    FirestoreSyncManager.syncAllDataNow(context, db)
                } catch (e: Exception) {
                    Log.w("AppRepository", "Eager sync exception: ${e.message}")
                }
            }
            // WorkManager schedule for offline retry resilience
            SyncScheduler.scheduleSync(context)
        }
    }
}
