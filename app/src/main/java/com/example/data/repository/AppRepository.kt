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
import com.example.data.local.startOfLocalDay
import com.example.data.local.TaskItem
import com.example.data.local.TaskStatus
import com.example.data.local.TaskWithCourse
import com.example.data.local.RepeatRule
import com.example.data.local.deadlineMillis
import com.example.data.local.isMyTasksCourse
import com.example.data.local.nextOccurrenceMillis
import com.example.data.local.repeat
import com.example.data.local.resolveAnchorDay
import android.util.Log
import com.example.reminder.ReminderScheduler
import com.example.reminder.SnoozeStore
import com.example.data.local.AppDatabase
import com.example.reminder.ClassReminderScheduler
import com.example.sync.SupabaseSyncManager
import com.example.sync.SyncScheduler
import com.example.util.AttachmentManager
import com.example.util.currentSupabaseUid
import com.example.util.toggleChecklistLine
import com.example.util.uncheckAllChecklistItems
import java.util.Calendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
            SnoozeStore.clear(context, taskToUpdate.id, cancelAlarm = true)
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
            SnoozeStore.clear(context, taskId, cancelAlarm = true)
        } else {
            val updated = taskDao.getTaskById(taskId)
            if (updated != null) {
                val course = courseDao.getCourseByIdSync(updated.courseId)
                reminderScheduler.scheduleReminder(updated, course?.code ?: "Course")
            }
        }
        triggerSyncIfLoggedIn()
    }

    suspend fun toggleChecklistItem(taskId: Long, lineIndex: Int) = withContext(Dispatchers.IO) {
        val task = taskDao.getTaskById(taskId) ?: return@withContext
        val newDescription = toggleChecklistLine(task.description, lineIndex)
        if (newDescription != task.description) {
            taskDao.updateDescription(taskId, newDescription)
            triggerSyncIfLoggedIn()
        }
    }

    /**
     * Keeps repeating tasks moving. Safe to call as often as you like.
     *  1. A repeating task ticked Done stays Done for the rest of that day. From the next midnight it
     *     moves to its next date and becomes Pending again.
     *  2. A repeating task that was NOT done and is a whole cycle late (for example a daily task that
     *     is 2 days overdue) jumps to its next future date, so old missed copies do not stay forever.
     *     A task that is less than one cycle late stays Overdue, so you can still see it.
     * Returns true when at least one task was moved.
     */
    suspend fun rolloverCompletedRepeatingTasks(): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val startOfToday = startOfLocalDay(now)

        var changed = false
        for (task in taskDao.getAllActiveTasksSync()) {
            if (task.repeat == RepeatRule.NONE) {
                // "Delete when done": a finished one-time task goes away once the day it was finished is over.
                if (task.autoDeleteWhenDone && task.status == TaskStatus.COMPLETED) {
                    if (task.completedAtMillis <= 0L) {
                        // Ticked on another device, or before this update: count the day from now.
                        taskDao.updateTask(task.copy(completedAtMillis = now))
                    } else if (task.completedAtMillis < startOfToday) {
                        deleteTask(task)
                        changed = true
                    }
                }
                continue
            }

            // Monthly and yearly tasks keep the day they were set on (for example the 31st), so a
            // short month does not pull them to an earlier day for good. The day is saved on the task.
            val keepsDay = task.repeat == RepeatRule.MONTHLY || task.repeat == RepeatRule.YEARLY
            val anchorDay = if (keepsDay) resolveAnchorDay(task.repeatAnchorDay, task.deadlineMillis) else 0

            if (task.status == TaskStatus.COMPLETED) {
                if (task.completedAtMillis <= 0L) {
                    // Ticked on another device, or before this update: count the day from now.
                    taskDao.updateTask(task.copy(completedAtMillis = now))
                    continue
                }
                // Ticked today: leave it Done until the day is over.
                if (task.completedAtMillis >= startOfToday) continue
            } else {
                // Not done. Only move it when the date AFTER its current one has also passed.
                val following = nextOccurrenceMillis(task.repeat, task.deadlineMillis, task.deadlineMillis, anchorDay)
                if (following > now) continue
            }

            val next = nextOccurrenceMillis(task.repeat, task.deadlineMillis, now, anchorDay)
            val moved = task.copy(
                dueDateEpochMillis = next,
                status = TaskStatus.PENDING,
                completedAtMillis = 0L,
                repeatAnchorDay = anchorDay,
                // A new round starts with an empty checklist.
                description = uncheckAllChecklistItems(task.description),
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
        SnoozeStore.clear(context, task.id, cancelAlarm = true)
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

    /**
     * Undo for a deleted task. If the row is still on the phone it is restored as before. If the sync
     * already erased it, the task is saved again as a new task (new cloud id), so Undo always works.
     */
    suspend fun undoDeleteTask(task: TaskItem) = withContext(Dispatchers.IO) {
        if (taskDao.getAnyTaskById(task.id) != null) {
            restoreTask(task.id)
        } else {
            insertTask(task.copy(id = 0L, cloudId = "", isSynced = false, isDeleted = false))
        }
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
            // Safety net: WorkManager retries later, also when the internet comes back.
            SyncScheduler.scheduleSync(context)

            // Wait 2 seconds after the LAST change, then sync once.
            synchronized(syncLock) {
                pendingSync?.cancel()
                pendingSync = syncScope.launch {
                    delay(SYNC_DEBOUNCE_MS)
                    // From here on, a new edit must not stop a sync that has already started.
                    withContext(NonCancellable) {
                        try {
                            val db = AppDatabase.getDatabase(context)
                            SupabaseSyncManager.syncAllDataNow(context, db)
                        } catch (e: Exception) {
                            Log.w("AppRepository", "Eager sync exception: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("AppRepository", "Error scheduling sync: ${e.message}")
        }
    }

    private companion object {
        const val SYNC_DEBOUNCE_MS = 2_000L
        val syncLock = Any()
        val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var pendingSync: Job? = null
    }
}
