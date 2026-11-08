package com.example.data.repository

import android.content.Context
import com.example.data.local.Course
import com.example.data.local.CourseDao
import com.example.data.local.ArchivedSemesterSummary
import com.example.data.local.ArchiveCourseItem
import com.example.data.local.CourseWithTaskCount
import com.example.data.local.NoteListItem
import com.example.data.local.isArchived
import com.example.data.local.LectureSlot
import com.example.data.local.LectureSlotDao
import com.example.data.local.LectureSlotWithCourse
import com.example.data.local.NoteDao
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
import com.example.util.AttachmentManager
import com.example.util.DateFormats
import com.example.util.NotificationHistory
import com.example.util.normalizeCourseCode
import com.example.util.toggleChecklistLine
import com.example.util.uncheckAllChecklistItems
import java.util.Calendar
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
class ArchivedCourseCodeException(val semesterName: String) :
    IllegalStateException("Course code belongs to archived semester: $semesterName")

class AppRepository(
    private val context: Context,
    private val courseDao: CourseDao,
    private val taskDao: TaskDao,
    private val taskAttachmentDao: TaskAttachmentDao,
    private val lectureSlotDao: LectureSlotDao,
    private val reminderScheduler: ReminderScheduler,
    private val noteDao: NoteDao? = null,
    private val database: androidx.room.RoomDatabase? = null
) {

    private suspend fun <T> inTransaction(block: suspend () -> T): T =
        database?.withTransaction { block() } ?: block()

    val allCourses: Flow<List<Course>> = courseDao.getAllCourses()
        .map { list ->
            list.filter { !it.isDeleted && !it.isArchived && !it.isMyTasksCourse() }
                .distinctBy { it.code.trim().replace("\\s+".toRegex(), "").uppercase() }
        }
        .flowOn(Dispatchers.IO)

    val allTasksWithCourse: Flow<List<TaskWithCourse>> = taskDao.getAllTasksWithCourse()
        .map { list ->
            list.filter { it.course != null && !it.course.isDeleted && !it.course.isArchived && !it.task.isDeleted }
        }
        .flowOn(Dispatchers.IO)

    val allLectureSlots: Flow<List<LectureSlotWithCourse>> = lectureSlotDao.getAllSlotsWithCourse()
        .map { list ->
            list.filter { it.course != null && !it.course.isDeleted && !it.course.isArchived }
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
        val normCode = normalizeCourseCode(course.code)

        val archived = courseDao.findArchivedCourseByCode(normCode)
        if (archived != null) {
            throw ArchivedCourseCodeException(archived.archivedSemester)
        }

        // Check if an existing course already matches this normalized code
        val existing = courseDao.getCourseByNormalizedCode(normCode) ?: courseDao.getAnyCourseByNormalizedCode(normCode)
        if (existing != null) {
            if (existing.isArchived) {
                throw ArchivedCourseCodeException(existing.archivedSemester)
            }
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
            return@withContext existing.id
        }

        val courseToInsert = course.copy(code = course.code.trim().uppercase(), isSynced = false, isDeleted = false)
        val id = courseDao.insertCourse(courseToInsert)
        id
    }

    suspend fun archiveCourses(courseIds: List<Long>, semesterName: String) = withContext(Dispatchers.IO) {
        val trimmed = semesterName.trim()
        if (trimmed.isBlank()) {
            throw IllegalArgumentException("Semester name cannot be blank")
        }
        if (trimmed.length > 40) {
            throw IllegalArgumentException("Semester name cannot exceed 40 characters")
        }

        val existingArchived = courseDao.getArchivedCoursesSync()
        val matchingExistingName = existingArchived
            .map { it.archivedSemester }
            .firstOrNull { it.equals(trimmed, ignoreCase = true) }
        val finalSemesterName = matchingExistingName ?: trimmed

        val courses = courseIds.mapNotNull { courseDao.getCourseByIdSync(it) }
        if (courses.any { it.isMyTasksCourse() }) {
            throw IllegalArgumentException("The My tasks course cannot be archived")
        }

        inTransaction {
            courseDao.setArchivedSemester(courseIds, finalSemesterName)
        }

        for (cId in courseIds) {
            val tasks = taskDao.getTasksForCourseSync(cId).filter { !it.isDeleted }
            for (t in tasks) {
                reminderScheduler.cancelReminder(t.id)
                SnoozeStore.clear(context, t.id, cancelAlarm = true)
                taskDao.updateTask(t.copy(reminderEpochMillis = null, isSynced = false))
            }
        }

        refreshClassAlarms()
        com.example.widget.UniTaskWidget.refresh(context)
    }

    suspend fun restoreSemester(semesterName: String) = withContext(Dispatchers.IO) {
        val trimmed = semesterName.trim()
        val courses = courseDao.getCoursesForSemester(trimmed)

        inTransaction {
            courseDao.clearArchivedSemester(trimmed)
        }

        for (course in courses) {
            val tasks = taskDao.getTasksForCourseSync(course.id).filter { !it.isDeleted && it.status != TaskStatus.COMPLETED }
            for (task in tasks) {
                val calculatedReminder = reminderScheduler.calculateReminderMillis(task)
                val updatedTask = task.copy(reminderEpochMillis = calculatedReminder, isSynced = false)
                taskDao.updateTask(updatedTask)
                if (calculatedReminder != null) {
                    reminderScheduler.scheduleReminder(updatedTask, course.code)
                }
            }
        }

        refreshClassAlarms()
        com.example.widget.UniTaskWidget.refresh(context)
    }

    suspend fun undoArchive(courseIds: List<Long>) = withContext(Dispatchers.IO) {
        if (courseIds.isEmpty()) return@withContext
        val courses = courseIds.mapNotNull { courseDao.getCourseByIdSyncIncludingArchived(it) }

        inTransaction {
            courseDao.clearArchivedSemesterForCourses(courseIds)
        }

        for (course in courses) {
            val tasks = taskDao.getTasksForCourseSync(course.id).filter { !it.isDeleted && it.status != TaskStatus.COMPLETED }
            for (task in tasks) {
                val calculatedReminder = reminderScheduler.calculateReminderMillis(task)
                val updatedTask = task.copy(reminderEpochMillis = calculatedReminder, isSynced = false)
                taskDao.updateTask(updatedTask)
                if (calculatedReminder != null) {
                    reminderScheduler.scheduleReminder(updatedTask, course.code)
                }
            }
        }

        refreshClassAlarms()
        com.example.widget.UniTaskWidget.refresh(context)
    }

    suspend fun deleteSemesterForever(semesterName: String) = withContext(Dispatchers.IO) {
        val trimmed = semesterName.trim()
        val courses = courseDao.getCoursesForSemester(trimmed)
        for (course in courses) {
            deleteCourse(course)
        }
    }

    fun observeArchivedSemesters(): Flow<List<ArchivedSemesterSummary>> {
        val notesFlow: Flow<List<NoteListItem>> = noteDao?.observeNotesIncludingArchived() ?: kotlinx.coroutines.flow.flowOf(emptyList())
        return combine(
            courseDao.getAllCoursesIncludingArchived(),
            taskDao.getAllTasksWithCourseIncludingArchived(),
            lectureSlotDao.getAllSlotsWithCourseIncludingArchived(),
            notesFlow
        ) { allCourses, allTasks, allSlots, allNotes ->
            val archived = allCourses.filter { !it.isDeleted && it.archivedSemester.isNotBlank() }
            if (archived.isEmpty()) {
                emptyList()
            } else {
                val grouped = archived.groupBy { it.archivedSemester.trim().uppercase() }
                grouped.values.map { semesterCourses ->
                    val semesterName = semesterCourses.first().archivedSemester.trim()
                    val courseIds = semesterCourses.map { it.id }.toSet()
                    val courseCodes = semesterCourses.map { it.code }.distinct().sorted()
                    val taskCount = allTasks.count { it.task.courseId in courseIds && !it.task.isDeleted }
                    val classCount = allSlots.count { it.slot.courseId in courseIds && !it.slot.isDeleted }
                    val noteCount = allNotes.count { it.note.courseId in courseIds && !it.note.isDeleted }

                    ArchivedSemesterSummary(
                        name = semesterName,
                        courseCount = semesterCourses.size,
                        taskCount = taskCount,
                        classCount = classCount,
                        noteCount = noteCount,
                        courseCodes = courseCodes
                    )
                }.sortedBy { it.name }
            }
        }.flowOn(Dispatchers.Default)
    }

    fun observeActiveCoursesForArchive(): Flow<List<ArchiveCourseItem>> {
        val notesFlow: Flow<List<NoteListItem>> = noteDao?.observeNotes() ?: kotlinx.coroutines.flow.flowOf(emptyList())
        return combine(
            courseDao.getAllCourses(),
            taskDao.getAllTasksWithCourse(),
            lectureSlotDao.getAllSlotsWithCourse(),
            notesFlow
        ) { courses, tasks, slots, notes ->
            courses.filter { !it.isMyTasksCourse() }.map { course ->
                val taskCount = tasks.count { it.task.courseId == course.id && !it.task.isDeleted }
                val classCount = slots.count { it.slot.courseId == course.id && !it.slot.isDeleted }
                val noteCount = notes.count { it.note.courseId == course.id && !it.note.isDeleted }
                ArchiveCourseItem(
                    id = course.id,
                    code = course.code,
                    name = course.name,
                    taskCount = taskCount,
                    classCount = classCount,
                    noteCount = noteCount
                )
            }
        }.flowOn(Dispatchers.Default)
    }

        /** Another (not deleted) course with the same code, ignoring spaces and capitals. */
    suspend fun findActiveCourseByCode(code: String, excludeCourseId: Long = 0L): Course? = withContext(Dispatchers.IO) {
        val normCode = normalizeCourseCode(code)
        courseDao.getAllCoursesMatchingNormalizedCode(normCode)
            .firstOrNull { !it.isDeleted && !it.isArchived && it.id != excludeCourseId }
    }

    suspend fun updateCourse(course: Course) = withContext(Dispatchers.IO) {
        val courseToUpdate = course.copy(code = course.code.trim().uppercase(), isSynced = false)
        courseDao.updateCourse(courseToUpdate)
    }

    suspend fun deleteCourse(course: Course) = withContext(Dispatchers.IO) {
        val normCode = normalizeCourseCode(course.code)

        // 1. Find all local course entries that match this normalized code (in case of duplicates)
        val matchingCourses = courseDao.getAllCoursesMatchingNormalizedCode(normCode)
        val allIds = (matchingCourses.map { it.id } + course.id).distinct()

        // 2. Gather all tasks across all matching course IDs
        val allTasks = mutableListOf<TaskItem>()
        for (cId in allIds) {
            allTasks.addAll(taskDao.getTasksForCourseSync(cId))
            allTasks.addAll(taskDao.getAnyTasksForCourseSync(cId))
        }

        // 3. Cancel reminders and remove attachment files for all tasks
        for (t in allTasks.distinctBy { it.id }) {
            reminderScheduler.cancelReminder(t.id)
            val attachments = taskAttachmentDao.getAttachmentsForTaskSync(t.id)
            for (att in attachments) {
                AttachmentManager.deleteAttachmentFile(context, att.relativePath)
            }
        }

        // 4. In Room: remove the courses, tasks and timetable slots, and detach notes.
        inTransaction {
            // Detach notes tagged with deleted courses without deleting the user's notes.
            noteDao?.detachCourses(allIds)
            for (cId in allIds) {
                taskDao.deleteTasksForCourse(cId)
                lectureSlotDao.deleteSlotsForCourse(cId)
                courseDao.deleteCourseById(cId)
            }
        }

        refreshClassAlarms()
    }
    /** Re-arms the "class started" alarms after any change to slots or courses. */
    private suspend fun refreshClassAlarms() {
        try {
            ClassReminderScheduler(context).rescheduleAll(database ?: AppDatabase.getDatabase(context))
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
    }

    suspend fun toggleChecklistItem(taskId: Long, lineIndex: Int) = withContext(Dispatchers.IO) {
        val task = taskDao.getTaskById(taskId) ?: return@withContext
        val newDescription = toggleChecklistLine(task.description, lineIndex)
        if (newDescription != task.description) {
            taskDao.updateDescription(taskId, newDescription)
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
                // Not done. Daily and weekday tasks are replaced at midnight by today's copy, so
                // today's reminder is armed and rings. Other repeats only move once the date AFTER
                // the current one has also passed, so a missed weekly or monthly task stays visible.
                val replacedAtMidnight =
                    (task.repeat == RepeatRule.DAILY || task.repeat == RepeatRule.WEEKDAYS) &&
                        task.deadlineMillis < startOfToday
                if (!replacedAtMidnight) {
                    val following = nextOccurrenceMillis(task.repeat, task.deadlineMillis, task.deadlineMillis, anchorDay)
                    if (following > now) continue
                }
                // Keep a record of the missed copy, because the card will no longer show it.
                NotificationHistory.add(
                    context,
                    kind = "Missed task",
                    title = "Missed: ${task.title}",
                    body = "It was due ${"${DateFormats.shortDate(task.deadlineMillis)}, ${DateFormats.clock(task.deadlineMillis)}"} and was not marked done.",
                    target = "tab:MYTASKS",
                    key = "missed-task|${task.id}|${task.deadlineMillis}",
                    nowMillis = now
                )
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
        changed
    }

    fun observeDeletedTasks(): Flow<List<TaskWithCourse>> {
        return taskDao.observeDeletedTasks().flowOn(Dispatchers.IO)
    }

    suspend fun deleteTask(task: TaskItem) = withContext(Dispatchers.IO) {
        reminderScheduler.cancelReminder(task.id)
        SnoozeStore.clear(context, task.id, cancelAlarm = true)
        val now = System.currentTimeMillis()
        taskDao.softDeleteTask(task.id, now)
    }

    suspend fun restoreTask(taskId: Long) = withContext(Dispatchers.IO) {
        taskDao.restoreTask(taskId)
        val task = taskDao.getTaskById(taskId)
        if (task != null) {
            val calculatedReminder = reminderScheduler.calculateReminderMillis(task)
            val updatedTask = task.copy(reminderEpochMillis = calculatedReminder)
            taskDao.updateTask(updatedTask)
            if (updatedTask.status != TaskStatus.COMPLETED) {
                val course = courseDao.getCourseByIdSync(updatedTask.courseId)
                val courseCode = course?.code ?: "Course"
                reminderScheduler.scheduleReminder(updatedTask, courseCode)
            }
        }
    }

    suspend fun undoDeleteTask(task: TaskItem) = withContext(Dispatchers.IO) {
        restoreTask(task.id)
    }

    suspend fun deleteTaskForever(id: Long) = withContext(Dispatchers.IO) {
        reminderScheduler.cancelReminder(id)
        SnoozeStore.clear(context, id, cancelAlarm = true)
        val attachments = taskAttachmentDao.getAttachmentsForTaskSync(id)
        for (att in attachments) {
            AttachmentManager.deleteAttachmentFile(context, att.relativePath)
        }
        inTransaction {
            taskAttachmentDao.deleteAttachmentsForTask(id)
            taskDao.deleteTaskById(id)
        }
    }

    suspend fun emptyTaskTrash() = withContext(Dispatchers.IO) {
        val trashed = taskDao.getDeletedTasksSync()
        for (task in trashed) {
            deleteTaskForever(task.id)
        }
    }

    suspend fun purgeOldTaskTrash(now: Long = System.currentTimeMillis()): Int = withContext(Dispatchers.IO) {
        val cutoff = now - (NoteRepository.TRASH_DAYS * 24L * 60L * 60L * 1000L)
        val toPurge = taskDao.getDeletedTasksSync().filter { it.deletedAt > 0L && it.deletedAt < cutoff }
        for (task in toPurge) {
            val attachments = taskAttachmentDao.getAttachmentsForTaskSync(task.id)
            for (att in attachments) {
                AttachmentManager.deleteAttachmentFile(context, att.relativePath)
            }
            taskAttachmentDao.deleteAttachmentsForTask(task.id)
        }
        taskDao.purgeDeletedBefore(cutoff)
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
        refreshClassAlarms()
        id
    }

    suspend fun updateLectureSlot(slot: LectureSlot) = withContext(Dispatchers.IO) {
        lectureSlotDao.updateSlot(slot.copy(isSynced = false))
        refreshClassAlarms()
    }

    fun observeDeletedLectureSlots(): Flow<List<LectureSlotWithCourse>> {
        return lectureSlotDao.observeDeletedSlots().flowOn(Dispatchers.IO)
    }

    suspend fun deleteLectureSlot(slot: LectureSlot) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        lectureSlotDao.softDeleteSlot(slot.id, now)
        try {
            ClassReminderScheduler(context).cancelSlot(slot.id)
        } catch (e: Exception) {
            Log.w("AppRepository", "Could not cancel class slot alarm: ${e.message}")
        }
        refreshClassAlarms()
    }

    suspend fun restoreLectureSlot(id: Long) = withContext(Dispatchers.IO) {
        lectureSlotDao.restoreSlot(id)
        refreshClassAlarms()
    }

    suspend fun undoDeleteLectureSlot(slot: LectureSlot) = withContext(Dispatchers.IO) {
        restoreLectureSlot(slot.id)
    }

    suspend fun deleteSlotForever(id: Long) = withContext(Dispatchers.IO) {
        try {
            ClassReminderScheduler(context).cancelSlot(id)
        } catch (e: Exception) {
            Log.w("AppRepository", "Could not cancel class slot alarm: ${e.message}")
        }
        lectureSlotDao.deleteSlotById(id)
        refreshClassAlarms()
    }

    suspend fun emptyClassTrash() = withContext(Dispatchers.IO) {
        val trashed = lectureSlotDao.getDeletedSlotsSync()
        for (slot in trashed) {
            deleteSlotForever(slot.id)
        }
    }

    suspend fun purgeOldClassTrash(now: Long = System.currentTimeMillis()): Int = withContext(Dispatchers.IO) {
        val cutoff = now - (NoteRepository.TRASH_DAYS * 24L * 60L * 60L * 1000L)
        val toPurge = lectureSlotDao.getDeletedSlotsSync().filter { it.deletedAt > 0L && it.deletedAt < cutoff }
        for (slot in toPurge) {
            try {
                ClassReminderScheduler(context).cancelSlot(slot.id)
            } catch (e: Exception) {
                Log.w("AppRepository", "Could not cancel class slot alarm: ${e.message}")
            }
        }
        val purgedCount = lectureSlotDao.purgeDeletedBefore(cutoff)
        if (purgedCount > 0) {
            refreshClassAlarms()
        }
        purgedCount
    }
}
