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
import com.example.data.local.RepeatRule
import com.example.data.local.deadlineMillis
import com.example.data.local.isMyTasksCourse
import com.example.data.local.nextOccurrenceMillis
import com.example.data.local.repeat
import android.util.Log
import com.example.reminder.ReminderScheduler
import com.example.data.local.AppDatabase
import com.example.reminder.ClassReminderScheduler
import com.example.sync.SupabaseSyncManager
import com.example.sync.SyncScheduler
import com.example.util.AttachmentManager
import com.example.util.currentSupabaseUid
import java.util.Calendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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

    val allCourses: Flow<List<Course>> = courseDao.getAllCourses()
        .map { list ->
            list.filter { !it.isDeleted && !it.isMyTasksCourse() }
                .distinctBy { it.code.trim().replace("\\s+".toRegex(), "").uppercase() }
        }
        .flowOn(Dispatchers.IO)

    val allTasksWithCourse: Flow<List<TaskWithCourse>> = taskDao.getAllTasksWithCourse()
        .map { list ->
            list.filter { it.course != null && !it.course.isDeleted && !it.task.isDeleted }
        }
        .flowOn(Dispatchers.IO)

    val allLectureSlots: Flow<List<LectureSlotWithCourse>> = lectureSlotDao.getAllSlotsWithCourse()
        .map { list ->
            list.filter { it.course != null && !it.course.isDeleted }
        }
        .flowOn(Dispatchers.IO)

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
        val normCode = SupabaseSyncManager.normalizeCode(course.code)
        SupabaseSyncManager.clearPendingDeletionForCourse(normCode)

        // Check if an existing course already matches this normalized code
        val existing = courseDao.getCourseByNormalizedCode(normCode) ?: courseDao.getAnyCourseByNormalizedCode(normCode)
        if (existing != null) {
            val updated = existing.copy(
                code = course.code.trim().uppercase(),
                name = course.name.trim(),
                instructor = course.instructor.trim(),
                colorHex = course.colorHex,
                location = course.location.trim(),
                isSynced = false,
                isDeleted = false
            )
            courseDao.updateCourse(updated)
            triggerSyncIfLoggedIn()
            return@withContext existing.id
        }

        val courseToInsert = course.copy(code = course.code.trim().uppercase(), isSynced = false, isDeleted = false)
        val id = courseDao.insertCourse(courseToInsert)
        triggerSyncIfLoggedIn()
        id
    }

        /** Another (not deleted) course with the same code, ignoring spaces and capitals. */
    suspend fun findActiveCourseByCode(code: String, excludeCourseId: Long = 0L): Course? = withContext(Dispatchers.IO) {
        val normCode = SupabaseSyncManager.normalizeCode(code)
        courseDao.getAllCoursesMatchingNormalizedCode(normCode)
            .firstOrNull { !it.isDeleted && it.id != excludeCourseId }
    }

    suspend fun updateCourse(course: Course) = withContext(Dispatchers.IO) {
        val courseToUpdate = course.copy(code = course.code.trim().uppercase(), isSynced = false)
        courseDao.updateCourse(courseToUpdate)
        triggerSyncIfLoggedIn()
    }

    suspend fun deleteCourse(course: Course) = withContext(Dispatchers.IO) {
        val normCode = course.code.trim().replace("\\s+".toRegex(), "").uppercase()

        // 1. Find all local course entries that match this normalized code (in case of duplicates)
        val matchingCourses = courseDao.getAllCoursesMatchingNormalizedCode(normCode)
        val allIds = (matchingCourses.map { it.id } + course.id).distinct()

        // 2. Gather all tasks and slots across all matching course IDs
        val allTasks = mutableListOf<TaskItem>()
        val allSlots = mutableListOf<LectureSlot>()
        for (cId in allIds) {
            allTasks.addAll(taskDao.getTasksForCourseSync(cId))
            allTasks.addAll(taskDao.getAnyTasksForCourseSync(cId))
            allSlots.addAll(lectureSlotDao.getSlotsForCourseSync(cId))
        }

        // 3. Cancel reminders and remove attachment files for all tasks
        for (t in allTasks.distinctBy { it.id }) {
            reminderScheduler.cancelReminder(t.id)
            val attachments = taskAttachmentDao.getAttachmentsForTaskSync(t.id)
            for (att in attachments) {
                AttachmentManager.deleteAttachmentFile(context, att.relativePath)
            }
        }

        // 4. Soft-delete in Room (tasks, timetable slots and the course). The sync removes them from
        //    the cloud and only then purges the rows, so the delete survives being offline or the
        //    app being killed. Slots must be soft-deleted too, or they come back when a course with
        //    the same code is added again.
        for (cId in allIds) {
            taskDao.softDeleteTasksForCourse(cId)
            lectureSlotDao.softDeleteSlotsForCourse(cId)
            courseDao.softDeleteCourse(cId)
        }

        // 5. Record in pending deletion guards
        SupabaseSyncManager.recordPendingCourseDeletion(course.id, course.code, course.cloudId)
        SupabaseSyncManager.recordPendingCourseDeletion(course.id, normCode, course.cloudId)
        for (t in allTasks) {
            SupabaseSyncManager.recordPendingTaskDeletion(t.id, t.cloudId)
        }
        for (s in allSlots) {
            SupabaseSyncManager.recordPendingSlotDeletion(s.id, s.cloudId)
        }

        // 6. The cloud delete happens in the sync (step 0 of syncAllDataNow).
        //    Signed out and never uploaded: there is nothing to tell the cloud, so remove the rows
        //    now (foreign keys cascade to tasks, slots and attachments) instead of keeping them forever.
        if (!isUserLoggedIn()) {
            for (cId in allIds) {
                val row = courseDao.getAnyCourseByIdSync(cId) ?: continue
                if (row.cloudId.isBlank()) courseDao.deleteCourseById(cId)
            }
        }

        refreshClassAlarms()
        triggerSyncIfLoggedIn()
    }

    private fun isUserLoggedIn(): Boolean {
        return try {
            currentSupabaseUid() != null
        } catch (_: Exception) {
            false
        }
    }
    /** Re-arms the "class started" alarms after any change to slots or courses. */
    private suspend fun refreshClassAlarms() {
        try {
            ClassReminderScheduler(context).rescheduleAll(AppDatabase.getDatabase(context))
        } catch (e: Exception) {
            Log.w("AppRepository", "Could not refresh class alarms: ${e.message}")
        }
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
        // Remember when a task was ticked, so a repeating one can reset after midnight.
        val completedAt = when {
            task.status != TaskStatus.COMPLETED -> 0L
            task.completedAtMillis > 0L -> task.completedAtMillis
            else -> System.currentTimeMillis()
        }
        val taskToUpdate = task.copy(
            reminderEpochMillis = calculatedReminder,
            completedAtMillis = completedAt,
            isSynced = false
        )
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
        // A repeating task is NOT moved to its next date here. It stays Done for the rest of the
        // day and rolloverCompletedRepeatingTasks() moves it after midnight.
        val completedAt = if (newStatus == TaskStatus.COMPLETED) System.currentTimeMillis() else 0L
        taskDao.updateStatusAndCompletedAt(taskId, newStatus, completedAt)
        if (newStatus == TaskStatus.COMPLETED) {
            reminderScheduler.cancelReminder(taskId)
        } else {
            val updated = taskDao.getTaskById(taskId)
            if (updated != null) {
                val course = courseDao.getCourseByIdSync(updated.courseId)
                reminderScheduler.scheduleReminder(updated, course?.code ?: "Course")
            }
        }
        triggerSyncIfLoggedIn()
    }

    /**
     * A repeating task stays "Done" for the rest of the day it was ticked. From the next midnight
     * this moves it to its next date and makes it Pending again. Safe to call as often as you like.
     * Returns true when at least one task was moved.
     */
    suspend fun rolloverCompletedRepeatingTasks(): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        var changed = false
        for (task in taskDao.getAllActiveTasksSync()) {
            if (task.status != TaskStatus.COMPLETED || task.repeat == RepeatRule.NONE) continue

            if (task.completedAtMillis <= 0L) {
                // Ticked on another device, or before this update: count the day from now.
                taskDao.updateTask(task.copy(completedAtMillis = now))
                continue
            }
            // Ticked today: leave it Done until the day is over.
            if (task.completedAtMillis >= startOfToday) continue

            val next = nextOccurrenceMillis(task.repeat, task.deadlineMillis, now)
            val moved = task.copy(
                dueDateEpochMillis = next,
                status = TaskStatus.PENDING,
                completedAtMillis = 0L,
                isSynced = false
            )
            val finalTask = moved.copy(reminderEpochMillis = reminderScheduler.calculateReminderMillis(moved))
            taskDao.updateTask(finalTask)
            val course = courseDao.getCourseByIdSync(finalTask.courseId)
            reminderScheduler.scheduleReminder(finalTask, course?.code ?: "Course")
            changed = true
        }
        if (changed) triggerSyncIfLoggedIn()
        changed
    }

    suspend fun deleteTask(task: TaskItem) = withContext(Dispatchers.IO) {
        reminderScheduler.cancelReminder(task.id)

        // Soft-delete in Room first
        taskDao.softDeleteTask(task.id)
        SupabaseSyncManager.recordPendingTaskDeletion(task.id, task.cloudId)

        if (!isUserLoggedIn() && !task.isSynced && task.cloudId.isBlank()) {
            taskDao.deleteTaskById(task.id)
        }
        // When signed in, the sync removes it from the cloud and then purges the row.

        triggerSyncIfLoggedIn()
    }

    suspend fun restoreTask(taskId: Long) = withContext(Dispatchers.IO) {
        taskDao.restoreTask(taskId)
        val task = taskDao.getTaskById(taskId)
        if (task != null) {
            val course = courseDao.getCourseByIdSync(task.courseId)
            val courseCode = course?.code ?: "Course"
            reminderScheduler.scheduleReminder(task, courseCode)
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
        // A slot with the same course, day and time may have been deleted earlier in this session.
        // Its cloud id is still in the "pending deletion" guard, which would make the next sync
        // delete the new slot from the cloud and then from this phone.
        val slotCourse = courseDao.getAnyCourseByIdSync(slot.courseId)
        SupabaseSyncManager.clearPendingDeletionForSlot(
            SupabaseSyncManager.getSlotDocId(slot, slotCourse?.code ?: "")
        )
        val id = lectureSlotDao.insertSlot(slot.copy(isSynced = false))
        refreshClassAlarms()
        triggerSyncIfLoggedIn()
        id
    }

    suspend fun updateLectureSlot(slot: LectureSlot) = withContext(Dispatchers.IO) {
        lectureSlotDao.updateSlot(slot.copy(isSynced = false))
        refreshClassAlarms()
        triggerSyncIfLoggedIn()
    }

    suspend fun deleteLectureSlot(slot: LectureSlot) = withContext(Dispatchers.IO) {
        SupabaseSyncManager.recordPendingSlotDeletion(slot.id, slot.cloudId)
        if (!isUserLoggedIn() && !slot.isSynced && slot.cloudId.isBlank()) {
            lectureSlotDao.deleteSlot(slot)
        } else {
            // Soft-delete; the sync removes it from the cloud and then purges the row.
            lectureSlotDao.softDeleteSlot(slot.id)
        }
        refreshClassAlarms()
        triggerSyncIfLoggedIn()
    }

    fun triggerSyncIfLoggedIn() {
        if (!isUserLoggedIn()) return
        try {
            // Eager immediate background sync
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = com.example.data.local.AppDatabase.getDatabase(context)
                    SupabaseSyncManager.syncAllDataNow(context, db)
                } catch (e: Exception) {
                    Log.w("AppRepository", "Eager sync exception: ${e.message}")
                }
            }
            // WorkManager schedule for offline retry resilience
            SyncScheduler.scheduleSync(context)
        } catch (e: Exception) {
            Log.w("AppRepository", "Error scheduling sync: ${e.message}")
        }
    }
}
