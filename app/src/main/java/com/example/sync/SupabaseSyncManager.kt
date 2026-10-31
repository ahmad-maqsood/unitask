package com.example.sync

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.Course
import com.example.data.local.LectureSlot
import com.example.data.local.Priority
import com.example.data.local.TaskItem
import com.example.data.local.TaskStatus
import com.example.data.local.decodeReminderOptions
import com.example.data.local.decodeRepeatRule
import com.example.data.local.encodeReminderOptions
import com.example.reminder.ClassReminderScheduler
import com.example.reminder.ReminderScheduler
import com.example.util.SupabaseProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

@Serializable
private data class CourseRow(
    @SerialName("user_id") val userId: String,
    @SerialName("doc_id") val docId: String,
    @SerialName("code") val code: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("instructor") val instructor: String? = "",
    @SerialName("color_hex") val colorHex: String? = "#4F46E5",
    @SerialName("location") val location: String? = ""
)

@Serializable
private data class TaskRow(
    @SerialName("user_id") val userId: String,
    @SerialName("doc_id") val docId: String,
    @SerialName("course_code") val courseCode: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("description") val description: String? = "",
    @SerialName("due_date_epoch_millis") val dueDateEpochMillis: Long = 0L,
    @SerialName("due_time_hour") val dueTimeHour: Int = 12,
    @SerialName("due_time_minute") val dueTimeMinute: Int = 0,
    @SerialName("priority") val priority: String? = "MEDIUM",
    @SerialName("status") val status: String? = "PENDING",
    @SerialName("reminder_option") val reminderOption: String? = "NONE",
    @SerialName("repeat_rule") val repeatRule: String? = "NONE"
)

@Serializable
private data class SlotRow(
    @SerialName("user_id") val userId: String,
    @SerialName("doc_id") val docId: String,
    @SerialName("course_code") val courseCode: String = "",
    @SerialName("day_of_week") val dayOfWeek: Int = 1,
    @SerialName("start_hour") val startHour: Int = 9,
    @SerialName("start_minute") val startMinute: Int = 0,
    @SerialName("end_hour") val endHour: Int = 10,
    @SerialName("end_minute") val endMinute: Int = 30,
    @SerialName("classroom") val classroom: String? = "",
    @SerialName("instructor") val instructor: String? = ""
)

@OptIn(FlowPreview::class)
object SupabaseSyncManager {

    private const val TAG = "SupabaseSyncManager"
    internal const val POSTGREST_PAGE_SIZE = 1000L

    internal suspend fun <T> fetchAllRows(
        table: String,
        userId: String,
        pageSize: Long = POSTGREST_PAGE_SIZE,
        fetchPage: suspend (from: Long, to: Long) -> List<T>
    ): List<T> {
        val allRows = mutableListOf<T>()
        var from = 0L
        while (true) {
            val to = from + pageSize - 1L
            val page = fetchPage(from, to)
            allRows.addAll(page)
            if (page.size < pageSize) {
                break
            }
            from += pageSize
        }
        return allRows
    }

    private suspend inline fun <reified T : Any> fetchAllRows(
        table: String,
        userId: String
    ): List<T> {
        return fetchAllRows(table, userId, POSTGREST_PAGE_SIZE) { from, to ->
            SupabaseProvider.client.postgrest[table]
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                    order("doc_id", Order.ASCENDING)
                    range(from, to)
                }.decodeList<T>()
        }
    }

    private val isSyncingNow = AtomicBoolean(false)
    private val syncMutex = Mutex()

    // Guard sets for in-flight or confirmed deletions to prevent re-insertion/resurrection
    private val pendingDeletedCourseCodes: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val pendingDeletedCourseIds: MutableSet<Long> = ConcurrentHashMap.newKeySet()
    private val pendingDeletedCourseDocIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val pendingDeletedTaskIds: MutableSet<Long> = ConcurrentHashMap.newKeySet()
    private val pendingDeletedTaskDocIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val pendingDeletedSlotIds: MutableSet<Long> = ConcurrentHashMap.newKeySet()
    private val pendingDeletedSlotDocIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private var realtimeChannel: RealtimeChannel? = null
    private var realtimeScope: CoroutineScope? = null

    fun normalizeCode(code: String): String {
        return code.trim().replace("\\s+".toRegex(), "").uppercase()
    }

    fun recordPendingCourseDeletion(courseId: Long, code: String, cloudId: String) {
        val norm = normalizeCode(code)
        if (norm.isNotBlank()) {
            pendingDeletedCourseCodes.add(norm)
        }
        if (code.isNotBlank()) {
            pendingDeletedCourseCodes.add(code.trim().uppercase())
        }
        if (courseId > 0) {
            pendingDeletedCourseIds.add(courseId)
        }
        if (cloudId.isNotBlank()) {
            pendingDeletedCourseDocIds.add(cloudId)
        }
    }

    fun clearPendingDeletionForCourse(code: String) {
        val norm = normalizeCode(code)
        if (norm.isNotBlank()) {
            pendingDeletedCourseCodes.remove(norm)
            pendingDeletedCourseCodes.remove(code.trim().uppercase())
            // The cloud document id of a course is built from its code. If it stays in the guard,
            // the next sync deletes the freshly re-added course from the cloud, and the sync after
            // that removes it (and its tasks) from this phone as "deleted on another device".
            pendingDeletedCourseDocIds.remove(getCourseDocId(norm))
        }
    }

    /** Same idea for timetable slots, whose cloud id is built from course code, day and times. */
    fun clearPendingDeletionForSlot(docId: String) {
        if (docId.isNotBlank()) pendingDeletedSlotDocIds.remove(docId)
    }

    fun recordPendingTaskDeletion(taskId: Long, cloudId: String) {
        if (taskId > 0) {
            pendingDeletedTaskIds.add(taskId)
        }
        if (cloudId.isNotBlank()) {
            pendingDeletedTaskDocIds.add(cloudId)
        }
    }

    fun clearPendingTaskDeletion(taskId: Long, cloudId: String) {
        if (taskId > 0) {
            pendingDeletedTaskIds.remove(taskId)
        }
        if (cloudId.isNotBlank()) {
            pendingDeletedTaskDocIds.remove(cloudId)
        }
    }

    fun recordPendingSlotDeletion(slotId: Long, cloudId: String) {
        if (slotId > 0) {
            pendingDeletedSlotIds.add(slotId)
        }
        if (cloudId.isNotBlank()) {
            pendingDeletedSlotDocIds.add(cloudId)
        }
    }

    fun isCourseDeletionPending(code: String, docId: String = ""): Boolean {
        val norm = normalizeCode(code)
        if (norm.isNotBlank() && (pendingDeletedCourseCodes.contains(norm) || pendingDeletedCourseCodes.contains(code.trim().uppercase()))) {
            return true
        }
        if (docId.isNotBlank() && pendingDeletedCourseDocIds.contains(docId)) {
            return true
        }
        return false
    }

    fun isTaskDeletionPending(docId: String, taskId: Long? = null): Boolean {
        if (docId.isNotBlank() && pendingDeletedTaskDocIds.contains(docId)) {
            return true
        }
        if (taskId != null && pendingDeletedTaskIds.contains(taskId)) {
            return true
        }
        return false
    }

    fun isSlotDeletionPending(docId: String, slotId: Long? = null): Boolean {
        if (docId.isNotBlank() && pendingDeletedSlotDocIds.contains(docId)) {
            return true
        }
        if (slotId != null && pendingDeletedSlotIds.contains(slotId)) {
            return true
        }
        return false
    }

    fun getCourseDocId(code: String, cloudId: String = ""): String {
        return if (cloudId.isNotBlank()) {
            cloudId
        } else {
            val sanitized = normalizeCode(code).replace(Regex("[^A-Z0-9]"), "_")
            "course_$sanitized"
        }
    }

    fun getTaskDocId(task: TaskItem, courseCode: String = ""): String {
        // A task keeps its cloud id forever. New tasks get a random one, so two tasks with the
        // same title (or created on two devices) can never share a document.
        return if (task.cloudId.isNotBlank()) task.cloudId else "task_" + java.util.UUID.randomUUID().toString()
    }

    fun getSlotDocId(slot: LectureSlot, courseCode: String = ""): String {
        return if (slot.cloudId.isNotBlank()) {
            slot.cloudId
        } else {
            "slot_" + java.util.UUID.randomUUID().toString()
        }
    }

    private fun getCurrentUserId(): String? {
        return try {
            SupabaseProvider.client.auth.currentUserOrNull()?.id
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Immediately deletes a course, all its associated tasks, and timetable slots from Supabase.
     */
    suspend fun deleteCourseFromCloud(
        context: Context,
        course: Course,
        associatedTasks: List<TaskItem> = emptyList(),
        associatedSlots: List<LectureSlot> = emptyList()
    ): Boolean = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId() ?: return@withContext false

        val normCode = normalizeCode(course.code)
        recordPendingCourseDeletion(course.id, course.code, course.cloudId)
        recordPendingCourseDeletion(course.id, normCode, course.cloudId)
        for (task in associatedTasks) {
            recordPendingTaskDeletion(task.id, task.cloudId)
        }
        for (slot in associatedSlots) {
            recordPendingSlotDeletion(slot.id, slot.cloudId)
        }

        return@withContext try {
            val canonicalDocId = getCourseDocId(normCode, course.cloudId)

            // 1. Delete course row(s)
            try {
                SupabaseProvider.client.postgrest["courses"].delete {
                    filter {
                        eq("user_id", userId)
                        eq("doc_id", canonicalDocId)
                    }
                }
            } catch (e: Exception) { Log.w(TAG, "Cloud delete failed: ${e.message}"); throw e }

            if (course.cloudId.isNotBlank() && course.cloudId != canonicalDocId) {
                try {
                    SupabaseProvider.client.postgrest["courses"].delete {
                        filter {
                            eq("user_id", userId)
                            eq("doc_id", course.cloudId)
                        }
                    }
                } catch (e: Exception) { Log.w(TAG, "Cloud delete failed: ${e.message}"); throw e }
            }

            try {
                SupabaseProvider.client.postgrest["courses"].delete {
                    filter {
                        eq("user_id", userId)
                        eq("doc_id", course.id.toString())
                    }
                }
            } catch (e: Exception) { Log.w(TAG, "Cloud delete failed: ${e.message}"); throw e }

            // Delete any course matching normalized code
            try {
                SupabaseProvider.client.postgrest["courses"].delete {
                    filter {
                        eq("user_id", userId)
                        eq("code", normCode)
                    }
                }
            } catch (e: Exception) { Log.w(TAG, "Cloud delete failed: ${e.message}"); throw e }

            // 2. Delete ALL tasks associated with this course in Supabase
            try {
                SupabaseProvider.client.postgrest["tasks"].delete {
                    filter {
                        eq("user_id", userId)
                        eq("course_code", normCode)
                    }
                }
            } catch (e: Exception) { Log.w(TAG, "Cloud delete failed: ${e.message}"); throw e }

            for (task in associatedTasks) {
                if (task.cloudId.isNotBlank()) {
                    try {
                        SupabaseProvider.client.postgrest["tasks"].delete {
                            filter {
                                eq("user_id", userId)
                                eq("doc_id", task.cloudId)
                            }
                        }
                    } catch (e: Exception) { Log.w(TAG, "Cloud delete failed: ${e.message}"); throw e }
                }
            }

            // 3. Delete ALL timetable slots associated with this course in Supabase
            try {
                SupabaseProvider.client.postgrest["slots"].delete {
                    filter {
                        eq("user_id", userId)
                        eq("course_code", normCode)
                    }
                }
            } catch (e: Exception) { Log.w(TAG, "Cloud delete failed: ${e.message}"); throw e }

            for (slot in associatedSlots) {
                if (slot.cloudId.isNotBlank()) {
                    try {
                        SupabaseProvider.client.postgrest["slots"].delete {
                            filter {
                                eq("user_id", userId)
                                eq("doc_id", slot.cloudId)
                            }
                        }
                    } catch (e: Exception) { Log.w(TAG, "Cloud delete failed: ${e.message}"); throw e }
                }
            }

            Log.d(TAG, "Successfully purged course $normCode, its tasks, and timetable slots from Supabase")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete course ${course.code} from Supabase: ${e.message}", e)
            false
        }
    }

    /**
     * Immediately deletes an individual task from Supabase.
     */
    suspend fun deleteTaskFromCloud(
        context: Context,
        task: TaskItem,
        courseCode: String?
    ): Boolean = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId() ?: return@withContext false
        recordPendingTaskDeletion(task.id, task.cloudId)

        return@withContext try {
            if (task.cloudId.isNotBlank()) {
                try {
                    SupabaseProvider.client.postgrest["tasks"].delete {
                        filter {
                            eq("user_id", userId)
                            eq("doc_id", task.cloudId)
                        }
                    }
                } catch (e: Exception) { Log.w(TAG, "Cloud delete failed: ${e.message}"); throw e }
            }

            Log.d(TAG, "Successfully deleted task ${task.title} from Supabase")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete task from Supabase: ${e.message}", e)
            false
        }
    }

    /**
     * Immediately deletes a lecture slot from Supabase.
     */
    suspend fun deleteSlotFromCloud(
        context: Context,
        slot: LectureSlot,
        courseCode: String?
    ): Boolean = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId() ?: return@withContext false
        recordPendingSlotDeletion(slot.id, slot.cloudId)

        return@withContext try {
            if (slot.cloudId.isNotBlank()) {
                try {
                    SupabaseProvider.client.postgrest["slots"].delete {
                        filter {
                            eq("user_id", userId)
                            eq("doc_id", slot.cloudId)
                        }
                    }
                } catch (e: Exception) { Log.w(TAG, "Cloud delete failed: ${e.message}"); throw e }
            }
            val docId = getSlotDocId(slot, courseCode ?: "")
            try {
                SupabaseProvider.client.postgrest["slots"].delete {
                    filter {
                        eq("user_id", userId)
                        eq("doc_id", docId)
                    }
                }
            } catch (e: Exception) { Log.w(TAG, "Cloud delete failed: ${e.message}"); throw e }

            Log.d(TAG, "Successfully deleted timetable slot from Supabase")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete slot from Supabase: ${e.message}", e)
            false
        }
    }

    private const val SYNC_TIMEOUT_MS = 45_000L
    private val rerunRequested = AtomicBoolean(false)

    /**
     * Public entry point. If a sync is already running, we remember that another one was
     * requested and run one more pass when the current one ends, so no edit is left behind.
     */
    suspend fun syncAllDataNow(context: Context, database: AppDatabase): Boolean = withContext(Dispatchers.IO) {
        if (getCurrentUserId() == null) {
            Log.d(TAG, "syncAllDataNow: User not signed in, skipping sync")
            return@withContext false
        }

        if (!isSyncingNow.compareAndSet(false, true)) {
            rerunRequested.set(true)
            return@withContext true
        }

        try {
            var ok: Boolean
            do {
                rerunRequested.set(false)
                ok = runSyncPass(context, database)
            } while (ok && rerunRequested.get())
            if (ok) {
                com.example.util.AuthStorage.setLastSyncedTimestamp(context, System.currentTimeMillis())
            }
            ok
        } finally {
            isSyncingNow.set(false)
        }
    }

    private suspend fun runSyncPass(context: Context, database: AppDatabase): Boolean {
        val userId = getCurrentUserId() ?: return false
        return try {
            withTimeout(SYNC_TIMEOUT_MS) {
                syncMutex.withLock {
                    val courseDao = database.courseDao()
                    val taskDao = database.taskDao()
                    val slotDao = database.lectureSlotDao()

                    // Handle local soft-deleted courses
                    val deletedCourses = courseDao.getDeletedCourses()
                    for (c in deletedCourses) {
                        val tasks = taskDao.getAnyTasksForCourseSync(c.id)
                        val slots = slotDao.getSlotsForCourseSync(c.id)
                        val cloudOk = deleteCourseFromCloud(context, c, tasks, slots)
                        kotlinx.coroutines.yield()
                        if (!cloudOk) continue
                        taskDao.deleteTasksForCourse(c.id)
                        slotDao.deleteSlotsForCourse(c.id)
                        courseDao.deleteCourseById(c.id)
                    }

                    // Handle local soft-deleted tasks
                    val deletedTasks = taskDao.getDeletedTasks()
                    for (t in deletedTasks) {
                        val c = courseDao.getAnyCourseByIdSync(t.courseId)
                        val cloudOk = deleteTaskFromCloud(context, t, c?.code)
                        kotlinx.coroutines.yield()
                        if (cloudOk) taskDao.deleteTaskById(t.id)
                    }

                    // Handle local soft-deleted slots
                    val deletedSlots = slotDao.getDeletedSlots()
                    for (s in deletedSlots) {
                        val c = courseDao.getAnyCourseByIdSync(s.courseId)
                        val cloudOk = deleteSlotFromCloud(context, s, c?.code)
                        kotlinx.coroutines.yield()
                        if (cloudOk) slotDao.deleteSlotById(s.id)
                    }

                    taskDao.deleteOrphanedTasks()
                    slotDao.deleteOrphanedSlots()

                    // 1. Push courses changed on this device to Supabase with canonical doc IDs
                    val allCourses = courseDao.getAllCoursesList().filter { !it.isDeleted && !it.isSynced }
                    if (allCourses.isNotEmpty()) {
                        val courseRows = allCourses.map { course ->
                            val normCode = normalizeCode(course.code)
                            val canonicalDocId = getCourseDocId(normCode)
                            CourseRow(
                                userId = userId,
                                docId = canonicalDocId,
                                code = normCode,
                                name = course.name.trim(),
                                instructor = course.instructor.trim(),
                                colorHex = course.colorHex,
                                location = course.location.trim()
                            )
                        }
                        SupabaseProvider.client.postgrest["courses"].upsert(courseRows) {
                            onConflict = "user_id,doc_id"
                        }
                        for (course in allCourses) {
                            val normCode = normalizeCode(course.code)
                            val canonicalDocId = getCourseDocId(normCode)
                            val oldDocId = course.cloudId
                            if (oldDocId.isNotBlank() && oldDocId != canonicalDocId) {
                                try {
                                    SupabaseProvider.client.postgrest["courses"].delete {
                                        filter {
                                            eq("user_id", userId)
                                            eq("doc_id", oldDocId)
                                        }
                                    }
                                } catch (e: Exception) { Log.w(TAG, "Cloud delete failed: ${e.message}") }
                                taskDao.markTasksUnsyncedForCourse(course.id)
                                slotDao.markSlotsUnsyncedForCourse(course.id)
                            }
                            courseDao.markCourseSynced(course.id, canonicalDocId)
                        }
                    }

                    // 2. Push tasks changed on this device to Supabase
                    val allTasks = taskDao.getAllActiveTasksSync().filter { !it.isDeleted && !it.isSynced }
                    val tasksToPush = mutableListOf<Pair<TaskItem, TaskRow>>()
                    for (task in allTasks) {
                        val course = courseDao.getCourseByIdSync(task.courseId) ?: courseDao.getAnyCourseByIdSync(task.courseId)
                        if (course == null || course.isDeleted) {
                            taskDao.deleteTaskById(task.id)
                            continue
                        }
                        val normCode = normalizeCode(course.code)
                        val docId = getTaskDocId(task, normCode)
                        if (task.cloudId.isBlank()) taskDao.setTaskCloudId(task.id, docId)
                        val taskRow = TaskRow(
                            userId = userId,
                            docId = docId,
                            courseCode = normCode,
                            title = task.title.trim(),
                            description = task.description.trim(),
                            dueDateEpochMillis = task.dueDateEpochMillis,
                            dueTimeHour = task.dueTimeHour,
                            dueTimeMinute = task.dueTimeMinute,
                            priority = task.priority.name,
                            status = task.status.name,
                            reminderOption = encodeReminderOptions(task.reminderOptions),
                            repeatRule = task.repeatRule
                        )
                        tasksToPush.add(task to taskRow)
                    }
                    if (tasksToPush.isNotEmpty()) {
                        SupabaseProvider.client.postgrest["tasks"].upsert(tasksToPush.map { it.second }) {
                            onConflict = "user_id,doc_id"
                        }
                        for ((task, row) in tasksToPush) {
                            taskDao.markTaskSynced(task.id, row.docId)
                        }
                    }

                    // 3. Push timetable lecture slots changed on this device to Supabase
                    val allSlots = slotDao.getAllSlotsSync().filter { !it.isDeleted && !it.isSynced }
                    val slotsToPush = mutableListOf<Pair<LectureSlot, SlotRow>>()
                    for (slot in allSlots) {
                        val course = courseDao.getCourseByIdSync(slot.courseId) ?: courseDao.getAnyCourseByIdSync(slot.courseId)
                        if (course == null || course.isDeleted) {
                            slotDao.deleteSlotById(slot.id)
                            continue
                        }
                        val normCode = normalizeCode(course.code)
                        val docId = getSlotDocId(slot, normCode)
                        val slotRow = SlotRow(
                            userId = userId,
                            docId = docId,
                            courseCode = normCode,
                            dayOfWeek = slot.dayOfWeek,
                            startHour = slot.startHour,
                            startMinute = slot.startMinute,
                            endHour = slot.endHour,
                            endMinute = slot.endMinute,
                            classroom = slot.classroom.trim(),
                            instructor = slot.instructor.trim()
                        )
                        slotsToPush.add(slot to slotRow)
                    }
                    if (slotsToPush.isNotEmpty()) {
                        SupabaseProvider.client.postgrest["slots"].upsert(slotsToPush.map { it.second }) {
                            onConflict = "user_id,doc_id"
                        }
                        for ((slot, row) in slotsToPush) {
                            slotDao.markSlotSynced(slot.id, row.docId)
                        }
                    }

                    // 4. Pull & merge courses
                    pullAndMergeCourses(context, database, userId)

                    // 5. Pull & merge tasks
                    pullAndMergeTasks(context, database, userId)

                    // 6. Pull & merge slots
                    pullAndMergeSlots(context, database, userId)

                    taskDao.deleteOrphanedTasks()
                    slotDao.deleteOrphanedSlots()

                    // Cloud changes may have added or changed tasks: make sure their alarms exist.
                    ReminderScheduler(context).rescheduleAll(database)
                    ClassReminderScheduler(context).rescheduleAll(database)
                }
            }

            Log.d(TAG, "syncAllDataNow: Completed successfully for user $userId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "syncAllDataNow: Error syncing data: ${e.message}", e)
            false
        }
    }

    private suspend fun pullAndMergeCourses(
        context: Context,
        database: AppDatabase,
        userId: String
    ): Boolean {
        val courseDao = database.courseDao()
        val taskDao = database.taskDao()
        val slotDao = database.lectureSlotDao()

        val cloudCourses = try {
            fetchAllRows<CourseRow>("courses", userId)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch courses from Supabase: ${e.message}")
            return false
        }

        val seenCourseCodes = mutableSetOf<String>()

        for (row in cloudCourses) {
            val rawCode = row.code
            if (rawCode.isBlank()) continue
            val normCode = normalizeCode(rawCode)
            val canonicalDocId = getCourseDocId(normCode)

            if (isCourseDeletionPending(normCode, row.docId) || courseDao.isCourseDeletedByCode(normCode)) {
                try {
                    SupabaseProvider.client.postgrest["courses"].delete {
                        filter {
                            eq("user_id", userId)
                            eq("doc_id", row.docId)
                        }
                    }
                } catch (e: Exception) { Log.w(TAG, "Cloud delete failed: ${e.message}") }
                continue
            }

            // Duplicate document in cloud for same course: purge it!
            if (seenCourseCodes.contains(normCode)) {
                Log.d(TAG, "Purging duplicate cloud course document: ${row.docId}")
                try {
                    SupabaseProvider.client.postgrest["courses"].delete {
                        filter {
                            eq("user_id", userId)
                            eq("doc_id", row.docId)
                        }
                    }
                } catch (e: Exception) { Log.w(TAG, "Cloud delete failed: ${e.message}") }
                continue
            }
            seenCourseCodes.add(normCode)

            // Normalize document ID in Supabase if legacy format was used
            if (row.docId != canonicalDocId) {
                try {
                    val normalizedRow = row.copy(docId = canonicalDocId, code = normCode)
                    SupabaseProvider.client.postgrest["courses"].upsert(normalizedRow) {
                        onConflict = "user_id,doc_id"
                    }
                    SupabaseProvider.client.postgrest["courses"].delete {
                        filter {
                            eq("user_id", userId)
                            eq("doc_id", row.docId)
                        }
                    }
                } catch (e: Exception) { Log.w(TAG, "Cloud migration failed: ${e.message}") }
            }

            val existing = courseDao.getCourseByNormalizedCode(normCode)
                ?: courseDao.getCourseByCloudId(row.docId)
                ?: courseDao.getCourseByCloudId(canonicalDocId)

            if (existing == null) {
                val newCourse = Course(
                    code = normCode,
                    name = row.name.ifBlank { normCode },
                    instructor = row.instructor?.trim().orEmpty(),
                    colorHex = row.colorHex?.ifBlank { "#4F46E5" } ?: "#4F46E5",
                    location = row.location?.trim().orEmpty(),
                    isSynced = true,
                    cloudId = canonicalDocId,
                    isDeleted = false
                )
                courseDao.insertCourse(newCourse)
            } else if (existing.isDeleted) {
                try {
                    SupabaseProvider.client.postgrest["courses"].delete {
                        filter {
                            eq("user_id", userId)
                            eq("doc_id", row.docId)
                        }
                    }
                } catch (e: Exception) { Log.w(TAG, "Cloud delete failed: ${e.message}") }
            } else if (!existing.isSynced) {
                // Local edit not uploaded yet: keep it. The next sync pushes it.
            } else {
                val updated = existing.copy(
                    code = normCode,
                    name = row.name.ifBlank { existing.name },
                    instructor = row.instructor?.trim() ?: existing.instructor,
                    colorHex = row.colorHex ?: existing.colorHex,
                    location = row.location?.trim() ?: existing.location,
                    isSynced = true,
                    cloudId = canonicalDocId
                )
                courseDao.updateCourse(updated)

                // Deduplicate local Room entries if multiple rows existed
                deduplicateCourse(database, existing.id, normCode)
            }
        }

        // Reconcile courses deleted on another device:
        val reminderScheduler = ReminderScheduler(context)
        val cloudCourseIds = cloudCourses
            .flatMap { listOf(it.docId, getCourseDocId(normalizeCode(it.code))) }
            .toSet()
        for (c in courseDao.getAllCoursesList()) {
            if (c.isDeleted || !c.isSynced || c.cloudId.isBlank() || c.cloudId in cloudCourseIds) continue
            for (t in taskDao.getAnyTasksForCourseSync(c.id)) reminderScheduler.cancelReminder(t.id)
            taskDao.deleteTasksForCourse(c.id)
            slotDao.deleteSlotsForCourse(c.id)
            courseDao.deleteCourseById(c.id)
        }
        return true
    }

    suspend fun deduplicateCourse(
        database: AppDatabase,
        existingId: Long,
        normCode: String
    ) {
        val courseDao = database.courseDao()
        val taskDao = database.taskDao()
        val slotDao = database.lectureSlotDao()

        val matching = courseDao.getAllCoursesMatchingNormalizedCode(normCode)
        var reassignedAny = false
        for (m in matching) {
            if (m.id != existingId) {
                taskDao.reassignTasksToCourse(oldCourseId = m.id, newCourseId = existingId)
                slotDao.reassignSlotsToCourse(oldCourseId = m.id, newCourseId = existingId)
                courseDao.deleteCourseById(m.id)
                reassignedAny = true
            }
        }
        if (reassignedAny) {
            val tasks = taskDao.getAnyTasksForCourseSync(existingId).sortedBy { it.isDeleted }
            val seenCloudIds = mutableSetOf<String>()
            for (t in tasks) {
                if (t.cloudId.isNotBlank()) {
                    if (!seenCloudIds.add(t.cloudId)) {
                        taskDao.deleteTaskById(t.id)
                    }
                }
            }

            val slots = slotDao.getSlotsForCourseSync(existingId).sortedBy { it.isDeleted }
            val seenSlotTimes = mutableSetOf<Triple<Int, Int, Int>>()
            for (s in slots) {
                val key = Triple(s.dayOfWeek, s.startHour, s.startMinute)
                if (!seenSlotTimes.add(key)) {
                    slotDao.deleteSlotById(s.id)
                }
            }
        }
    }

    private suspend fun pullAndMergeTasks(
        context: Context,
        database: AppDatabase,
        userId: String
    ) {
        val courseDao = database.courseDao()
        val taskDao = database.taskDao()
        val reminderScheduler = ReminderScheduler(context.applicationContext)

        val cloudTasks = try {
            fetchAllRows<TaskRow>("tasks", userId)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch tasks from Supabase: ${e.message}")
            return
        }

        for (row in cloudTasks) {
            val title = row.title.trim()
            if (title.isBlank()) continue
            val courseCode = normalizeCode(row.courseCode)

            if (courseCode.isNotBlank() && (isCourseDeletionPending(courseCode) || courseDao.isCourseDeletedByCode(courseCode))) {
                try {
                    SupabaseProvider.client.postgrest["tasks"].delete {
                        filter {
                            eq("user_id", userId)
                            eq("doc_id", row.docId)
                        }
                    }
                } catch (e: Exception) { Log.w(TAG, "Cloud delete failed: ${e.message}") }
                continue
            }

            if (isTaskDeletionPending(row.docId)) {
                try {
                    SupabaseProvider.client.postgrest["tasks"].delete {
                        filter {
                            eq("user_id", userId)
                            eq("doc_id", row.docId)
                        }
                    }
                } catch (e: Exception) { Log.w(TAG, "Cloud delete failed: ${e.message}") }
                continue
            }

            var course = if (courseCode.isNotBlank()) courseDao.getCourseByNormalizedCode(courseCode) else null
            var coursesFetched = true
            if (course == null) {
                // If course is not found locally, try pulling courses first in case it was just added
                coursesFetched = pullAndMergeCourses(context, database, userId)
                course = if (courseCode.isNotBlank()) courseDao.getCourseByNormalizedCode(courseCode) else null
            }

            if (course == null) {
                if (!coursesFetched) {
                    Log.w(TAG, "Course fetch failed; skipping task ${row.docId} for course $courseCode")
                } else {
                    Log.w(TAG, "Parent course not found for cloud task ${row.docId} ($courseCode); skipping")
                }
                continue
            }
            val courseId = course.id

            val existing = taskDao.getTaskByCloudId(row.docId) ?: taskDao.getUnlinkedTaskByTitleAndCourse(title, courseId)
            val parsedPriority = try { Priority.valueOf(row.priority ?: "MEDIUM") } catch (_: Exception) { Priority.MEDIUM }
            val parsedStatus = try { TaskStatus.valueOf(row.status ?: "PENDING") } catch (_: Exception) { TaskStatus.PENDING }
            val reminderStr = row.reminderOption ?: "NONE"
            val repeatStr = decodeRepeatRule(row.repeatRule).name
            val desc = row.description?.trim().orEmpty()
            val dueEpoch = if (row.dueDateEpochMillis > 0) row.dueDateEpochMillis else System.currentTimeMillis()
            val dueH = row.dueTimeHour
            val dueM = row.dueTimeMinute

            if (existing == null) {
                val newTask = TaskItem(
                    courseId = courseId,
                    title = title,
                    description = desc,
                    dueDateEpochMillis = dueEpoch,
                    dueTimeHour = dueH,
                    dueTimeMinute = dueM,
                    priority = parsedPriority,
                    status = parsedStatus,
                    reminderOptions = decodeReminderOptions(reminderStr),
                    repeatRule = repeatStr,
                    isSynced = true,
                    cloudId = row.docId,
                    isDeleted = false
                )
                val newId = taskDao.insertTask(newTask)
                if (parsedStatus != TaskStatus.COMPLETED) {
                    reminderScheduler.scheduleReminder(newTask.copy(id = newId), course.code)
                }
            } else if (existing.isDeleted) {
                try {
                    SupabaseProvider.client.postgrest["tasks"].delete {
                        filter {
                            eq("user_id", userId)
                            eq("doc_id", row.docId)
                        }
                    }
                } catch (e: Exception) { Log.w(TAG, "Cloud delete failed: ${e.message}") }
            } else if (!existing.isSynced) {
                // Local edit not uploaded yet: keep it. The next sync pushes it.
            } else {
                val updated = existing.copy(
                    courseId = courseId,
                    title = title,
                    description = desc,
                    dueDateEpochMillis = dueEpoch,
                    dueTimeHour = dueH,
                    dueTimeMinute = dueM,
                    priority = parsedPriority,
                    status = parsedStatus,
                    reminderOptions = decodeReminderOptions(reminderStr),
                    repeatRule = repeatStr,
                    isSynced = true,
                    cloudId = row.docId
                )
                taskDao.updateTask(updated)
                if (parsedStatus == TaskStatus.COMPLETED) {
                    reminderScheduler.cancelReminder(updated.id)
                } else {
                    reminderScheduler.scheduleReminder(updated, course.code)
                }
            }
        }

        // Reconcile tasks deleted on another device
        val cloudTaskIds = cloudTasks.map { it.docId }.toSet()
        for (t in taskDao.getAllActiveTasksSync()) {
            if (!t.isSynced || t.cloudId.isBlank() || t.cloudId in cloudTaskIds) continue
            reminderScheduler.cancelReminder(t.id)
            taskDao.deleteTaskById(t.id)
        }

        taskDao.deleteOrphanedTasks()
        reminderScheduler.rescheduleAll(database)
    }

    private suspend fun pullAndMergeSlots(
        context: Context,
        database: AppDatabase,
        userId: String
    ) {
        val courseDao = database.courseDao()
        val slotDao = database.lectureSlotDao()

        val cloudSlots = try {
            fetchAllRows<SlotRow>("slots", userId)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch slots from Supabase: ${e.message}")
            return
        }

        for (row in cloudSlots) {
            val courseCode = normalizeCode(row.courseCode)
            if (courseCode.isNotBlank() && (isCourseDeletionPending(courseCode) || courseDao.isCourseDeletedByCode(courseCode))) {
                try {
                    SupabaseProvider.client.postgrest["slots"].delete {
                        filter {
                            eq("user_id", userId)
                            eq("doc_id", row.docId)
                        }
                    }
                } catch (e: Exception) { Log.w(TAG, "Cloud delete failed: ${e.message}") }
                continue
            }

            if (isSlotDeletionPending(row.docId)) {
                try {
                    SupabaseProvider.client.postgrest["slots"].delete {
                        filter {
                            eq("user_id", userId)
                            eq("doc_id", row.docId)
                        }
                    }
                } catch (e: Exception) { Log.w(TAG, "Cloud delete failed: ${e.message}") }
                continue
            }

            var course = if (courseCode.isNotBlank()) courseDao.getCourseByNormalizedCode(courseCode) else null
            var coursesFetched = true
            if (course == null) {
                coursesFetched = pullAndMergeCourses(context, database, userId)
                course = if (courseCode.isNotBlank()) courseDao.getCourseByNormalizedCode(courseCode) else null
            }

            if (course == null) {
                if (!coursesFetched) {
                    Log.w(TAG, "Course fetch failed; skipping slot ${row.docId} for course $courseCode")
                } else {
                    Log.w(TAG, "Parent course not found for cloud slot ${row.docId} ($courseCode); skipping")
                }
                continue
            }
            val courseId = course.id

            val dayOfWeek = row.dayOfWeek
            val startHour = row.startHour
            val startMinute = row.startMinute
            val endHour = row.endHour
            val endMinute = row.endMinute
            val classroom = row.classroom?.trim().orEmpty()
            val instructor = row.instructor?.trim().orEmpty()

            val existing = slotDao.getSlotByCloudId(row.docId) ?: slotDao.findMatchingSlot(courseId, dayOfWeek, startHour, startMinute)
            if (existing == null) {
                val newSlot = LectureSlot(
                    courseId = courseId,
                    dayOfWeek = dayOfWeek,
                    startHour = startHour,
                    startMinute = startMinute,
                    endHour = endHour,
                    endMinute = endMinute,
                    classroom = classroom,
                    instructor = instructor,
                    isSynced = true,
                    cloudId = row.docId
                )
                slotDao.insertSlot(newSlot)
            } else if (!existing.isSynced) {
                // Local edit not uploaded yet: keep it. The next sync pushes it.
            } else {
                val updated = existing.copy(
                    courseId = courseId,
                    dayOfWeek = dayOfWeek,
                    startHour = startHour,
                    startMinute = startMinute,
                    endHour = endHour,
                    endMinute = endMinute,
                    classroom = classroom,
                    instructor = instructor,
                    isSynced = true,
                    cloudId = row.docId
                )
                slotDao.updateSlot(updated)
            }
        }

        // Reconcile slots deleted on another device
        val cloudSlotIds = cloudSlots.map { it.docId }.toSet()
        for (s in slotDao.getAllSlotsSync()) {
            if (s.isDeleted || !s.isSynced || s.cloudId.isBlank() || s.cloudId in cloudSlotIds) continue
            slotDao.deleteSlotById(s.id)
        }

        slotDao.deleteOrphanedSlots()

        try {
            ClassReminderScheduler(context.applicationContext).rescheduleAll(database)
        } catch (e: Exception) {
            Log.w(TAG, "Could not refresh class alarms: ${e.message}")
        }
    }

    /**
     * Starts real-time Supabase snapshot listeners guarded against resurrecting deleted courses/tasks/slots.
     */
    fun startRealtimeSync(context: Context, database: AppDatabase) {
        stopRealtimeSync()

        val scope = CoroutineScope(Dispatchers.IO)
        realtimeScope = scope

        scope.launch {
            try {
                // Wait until the Supabase sign-in is ready (it loads a moment after app start
                // and after a fresh Google sign-in).
                SupabaseProvider.client.auth.sessionStatus.first { it is SessionStatus.Authenticated }
                val userId = getCurrentUserId() ?: return@launch
                syncAllDataNow(context, database)

                val channel = SupabaseProvider.client.channel("user-data-$userId")
                realtimeChannel = channel

                val coursesFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "courses"
                }
                val tasksFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "tasks"
                }
                val slotsFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "slots"
                }

                channel.subscribe()

                launch {
                    coursesFlow
                        .debounce(500L)
                        .collect {
                            syncMutex.withLock {
                                pullAndMergeCourses(context, database, userId)
                            }
                        }
                }

                launch {
                    tasksFlow
                        .debounce(500L)
                        .collect {
                            syncMutex.withLock {
                                pullAndMergeTasks(context, database, userId)
                            }
                        }
                }

                launch {
                    slotsFlow
                        .debounce(500L)
                        .collect {
                            syncMutex.withLock {
                                pullAndMergeSlots(context, database, userId)
                            }
                        }
                }

                Log.d(TAG, "Real-time guarded Supabase sync (courses, tasks, slots) started for user $userId")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting real-time sync: ${e.message}", e)
            }
        }
    }

    fun stopRealtimeSync() {
        val scope = realtimeScope
        val channel = realtimeChannel
        realtimeScope = null
        realtimeChannel = null

        scope?.cancel()

        if (channel != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    channel.unsubscribe()
                    SupabaseProvider.client.realtime.removeChannel(channel)
                } catch (e: Exception) {
                    Log.w(TAG, "Error stopping realtime channel: ${e.message}")
                }
            }
        }
        Log.d(TAG, "Real-time sync stopped")
    }
}
