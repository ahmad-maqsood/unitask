package com.example.sync

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.local.Course
import com.example.data.local.LectureSlot
import com.example.data.local.Priority
import com.example.data.local.TaskItem
import com.example.data.local.TaskStatus
import com.example.data.local.decodeReminderOptions
import com.example.data.local.encodeReminderOptions
import com.example.reminder.ReminderScheduler
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object FirestoreSyncManager {

    private const val TAG = "FirestoreSyncManager"

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

    private var coursesListenerRegistration: ListenerRegistration? = null
    private var tasksListenerRegistration: ListenerRegistration? = null
    private var slotsListenerRegistration: ListenerRegistration? = null

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
        }
    }

    fun recordPendingTaskDeletion(taskId: Long, cloudId: String) {
        if (taskId > 0) {
            pendingDeletedTaskIds.add(taskId)
        }
        if (cloudId.isNotBlank()) {
            pendingDeletedTaskDocIds.add(cloudId)
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
            val sanitizedCourse = normalizeCode(courseCode).replace(Regex("[^A-Z0-9]"), "_")
            "slot_${sanitizedCourse}_d${slot.dayOfWeek}_${slot.startHour}_${slot.startMinute}_${slot.endHour}_${slot.endMinute}"
        }
    }

    private fun getCurrentUser(): com.google.firebase.auth.FirebaseUser? {
        return try {
            Firebase.auth.currentUser
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Immediately deletes a course, all its associated tasks, and timetable slots from Cloud Firestore.
     */
    suspend fun deleteCourseFromCloud(
        context: Context,
        course: Course,
        associatedTasks: List<TaskItem> = emptyList(),
        associatedSlots: List<LectureSlot> = emptyList()
    ): Boolean = withContext(Dispatchers.IO) {
        val user = getCurrentUser() ?: return@withContext false

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
            val databaseId = context.getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(databaseId)
            val userDocRef = db.collection("users").document(user.uid)
            val coursesRef = userDocRef.collection("courses")
            val tasksRef = userDocRef.collection("tasks")
            val slotsRef = userDocRef.collection("slots")

            val canonicalDocId = getCourseDocId(normCode, course.cloudId)

            // 1. Delete course document(s)
            try { coursesRef.document(canonicalDocId).delete().await() } catch (_: Exception) {}
            if (course.cloudId.isNotBlank() && course.cloudId != canonicalDocId) {
                try { coursesRef.document(course.cloudId).delete().await() } catch (_: Exception) {}
            }
            try { coursesRef.document(course.id.toString()).delete().await() } catch (_: Exception) {}

            // Delete any course matching normalized code
            val coursesSnapshot = coursesRef.get().await()
            for (doc in coursesSnapshot.documents) {
                val c = normalizeCode(doc.getString("code") ?: "")
                if (c == normCode) {
                    try { doc.reference.delete().await() } catch (_: Exception) {}
                }
            }

            // 2. Delete ALL tasks associated with this course in Cloud Firestore
            val allTasksSnapshot = tasksRef.get().await()
            for (doc in allTasksSnapshot.documents) {
                val taskCourseCode = normalizeCode(doc.getString("courseCode") ?: "")
                if (taskCourseCode == normCode) {
                    try { doc.reference.delete().await() } catch (_: Exception) {}
                }
            }
            for (task in associatedTasks) {
                if (task.cloudId.isNotBlank()) {
                    try { tasksRef.document(task.cloudId).delete().await() } catch (_: Exception) {}
                }
            }

            // 3. Delete ALL timetable slots associated with this course in Cloud Firestore
            val allSlotsSnapshot = slotsRef.get().await()
            for (doc in allSlotsSnapshot.documents) {
                val slotCourseCode = normalizeCode(doc.getString("courseCode") ?: "")
                if (slotCourseCode == normCode) {
                    try { doc.reference.delete().await() } catch (_: Exception) {}
                }
            }
            for (slot in associatedSlots) {
                if (slot.cloudId.isNotBlank()) {
                    try { slotsRef.document(slot.cloudId).delete().await() } catch (_: Exception) {}
                }
            }

            Log.d(TAG, "Successfully purged course $normCode, its tasks, and timetable slots from Firestore")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete course ${course.code} from Firestore: ${e.message}", e)
            false
        }
    }

    /**
     * Immediately deletes an individual task from Cloud Firestore.
     */
    suspend fun deleteTaskFromCloud(
        context: Context,
        task: TaskItem,
        courseCode: String?
    ): Boolean = withContext(Dispatchers.IO) {
        val user = getCurrentUser() ?: return@withContext false
        recordPendingTaskDeletion(task.id, task.cloudId)

        return@withContext try {
            val databaseId = context.getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(databaseId)
            val tasksRef = db.collection("users").document(user.uid).collection("tasks")

            if (task.cloudId.isNotBlank()) {
                try { tasksRef.document(task.cloudId).delete().await() } catch (_: Exception) {}
            }
     

            Log.d(TAG, "Successfully deleted task ${task.title} from Cloud Firestore")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete task from Firestore: ${e.message}", e)
            false
        }
    }

    /**
     * Immediately deletes a lecture slot from Cloud Firestore.
     */
    suspend fun deleteSlotFromCloud(
        context: Context,
        slot: LectureSlot,
        courseCode: String?
    ): Boolean = withContext(Dispatchers.IO) {
        val user = getCurrentUser() ?: return@withContext false
        recordPendingSlotDeletion(slot.id, slot.cloudId)

        return@withContext try {
            val databaseId = context.getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(databaseId)
            val slotsRef = db.collection("users").document(user.uid).collection("slots")

            if (slot.cloudId.isNotBlank()) {
                try { slotsRef.document(slot.cloudId).delete().await() } catch (_: Exception) {}
            }
            val docId = getSlotDocId(slot, courseCode ?: "")
            try { slotsRef.document(docId).delete().await() } catch (_: Exception) {}

            Log.d(TAG, "Successfully deleted timetable slot from Cloud Firestore")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete slot from Firestore: ${e.message}", e)
            false
        }
    }

    /**
     * Eager, comprehensive, bidirectional synchronization of ALL courses, tasks, and timetable slots.
     * Guarantees:
     * 1. All local active courses are uploaded (preventing missed courses).
     * 2. All timetable lecture slots are backed up to Cloud Firestore.
     * 3. Cloud duplicate documents are purged.
     * 4. Local duplicates are consolidated so each course exists exactly once.
     * 5. Orphaned tasks and slots are cleaned up.
     */
   
    private const val SYNC_TIMEOUT_MS = 45_000L
    private val rerunRequested = AtomicBoolean(false)

    /**
     * Public entry point. If a sync is already running, we remember that another one was
     * requested and run one more pass when the current one ends, so no edit is left behind.
     */
    suspend fun syncAllDataNow(context: Context, database: AppDatabase): Boolean = withContext(Dispatchers.IO) {
        if (getCurrentUser() == null) {
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
            ok
        } finally {
            isSyncingNow.set(false)
        }
    }

    private suspend fun runSyncPass(context: Context, database: AppDatabase): Boolean {
        val user = getCurrentUser() ?: return false
        return try {
            withTimeout(SYNC_TIMEOUT_MS) {
            syncMutex.withLock {
                val databaseId = context.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val userDocRef = db.collection("users").document(user.uid)
                val cloudCoursesRef = userDocRef.collection("courses")
                val cloudTasksRef = userDocRef.collection("tasks")
                val cloudSlotsRef = userDocRef.collection("slots")

                val courseDao = database.courseDao()
                val taskDao = database.taskDao()
                val slotDao = database.lectureSlotDao()

                               val deletedCourses = courseDao.getDeletedCourses()
                for (c in deletedCourses) {
                    val tasks = taskDao.getAnyTasksForCourseSync(c.id)
                    val slots = slotDao.getSlotsForCourseSync(c.id)
                    val cloudOk = deleteCourseFromCloud(context, c, tasks, slots)
                    // Throws if this sync timed out, so we never purge on an unconfirmed delete.
                    kotlinx.coroutines.yield()
                    if (!cloudOk) continue
                    taskDao.deleteTasksForCourse(c.id)
                    slotDao.deleteSlotsForCourse(c.id)
                    courseDao.deleteCourseById(c.id)
                }

                val deletedTasks = taskDao.getDeletedTasks()
                for (t in deletedTasks) {
                    val c = courseDao.getAnyCourseByIdSync(t.courseId)
                    val cloudOk = deleteTaskFromCloud(context, t, c?.code)
                    kotlinx.coroutines.yield()
                    if (cloudOk) taskDao.deleteTaskById(t.id)
                }

                val deletedSlots = slotDao.getDeletedSlots()
                for (s in deletedSlots) {
                    val c = courseDao.getAnyCourseByIdSync(s.courseId)
                    val cloudOk = deleteSlotFromCloud(context, s, c?.code)
                    kotlinx.coroutines.yield()
                    if (cloudOk) slotDao.deleteSlotById(s.id)
                }

                taskDao.deleteOrphanedTasks()
                slotDao.deleteOrphanedSlots()

                // 1. Push courses changed on this device to Cloud Firestore with canonical doc IDs
                val allCourses = courseDao.getAllCoursesList().filter { !it.isDeleted && !it.isSynced }
                for (course in allCourses) {
                    val normCode = normalizeCode(course.code)
                    val canonicalDocId = getCourseDocId(normCode)
                    val courseData = hashMapOf<String, Any>(
                        "userId" to user.uid,
                        "code" to normCode,
                        "name" to course.name.trim(),
                        "instructor" to course.instructor.trim(),
                        "colorHex" to course.colorHex,
                        "location" to course.location.trim(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    cloudCoursesRef.document(canonicalDocId).set(courseData, SetOptions.merge()).await()
                    // Course code was renamed: the old cloud document is now a stale duplicate, and
                    // this course's tasks and slots must be re-pushed so they carry the new code.
                    val oldDocId = course.cloudId
                    if (oldDocId.isNotBlank() && oldDocId != canonicalDocId) {
                        try { cloudCoursesRef.document(oldDocId).delete().await() } catch (_: Exception) {}
                        taskDao.markTasksUnsyncedForCourse(course.id)
                        slotDao.markSlotsUnsyncedForCourse(course.id)
                    }
                    courseDao.markCourseSynced(course.id, canonicalDocId)
                }

                // 2. Push tasks changed on this device to Cloud Firestore
                val allTasks = taskDao.getAllActiveTasksSync().filter { !it.isDeleted && !it.isSynced }
                for (task in allTasks) {
                    val course = courseDao.getCourseByIdSync(task.courseId) ?: courseDao.getAnyCourseByIdSync(task.courseId)
                    if (course == null || course.isDeleted) {
                        taskDao.deleteTaskById(task.id)
                        continue
                    }
                    val normCode = normalizeCode(course.code)
                    val docId = getTaskDocId(task, normCode)
                                        // Store the id locally BEFORE writing, so a timeout between the write and the
                    // bookkeeping can never create a second cloud document for the same task.
                    if (task.cloudId.isBlank()) taskDao.setTaskCloudId(task.id, docId)
                    val taskData = hashMapOf<String, Any>(
                        "userId" to user.uid,
                        "courseCode" to normCode,
                        "title" to task.title.trim(),
                        "description" to task.description.trim(),
                        "dueDateEpochMillis" to task.dueDateEpochMillis,
                        "dueTimeHour" to task.dueTimeHour,
                        "dueTimeMinute" to task.dueTimeMinute,
                        "priority" to task.priority.name,
                        "status" to task.status.name,
                        "reminderOption" to encodeReminderOptions(task.reminderOptions),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    cloudTasksRef.document(docId).set(taskData, SetOptions.merge()).await()
                    taskDao.markTaskSynced(task.id, docId)
                }

                // 3. Push timetable lecture slots changed on this device to Cloud Firestore
                val allSlots = slotDao.getAllSlotsSync().filter { !it.isDeleted && !it.isSynced }
                for (slot in allSlots) {
                    val course = courseDao.getCourseByIdSync(slot.courseId) ?: courseDao.getAnyCourseByIdSync(slot.courseId)
                    if (course == null || course.isDeleted) {
                        slotDao.deleteSlotById(slot.id)
                        continue
                    }
                    val normCode = normalizeCode(course.code)
                    val docId = getSlotDocId(slot, normCode)
                    val slotData = hashMapOf<String, Any>(
                        "userId" to user.uid,
                        "courseCode" to normCode,
                        "dayOfWeek" to slot.dayOfWeek,
                        "startHour" to slot.startHour,
                        "startMinute" to slot.startMinute,
                        "endHour" to slot.endHour,
                        "endMinute" to slot.endMinute,
                        "classroom" to slot.classroom.trim(),
                        "instructor" to slot.instructor.trim(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    cloudSlotsRef.document(docId).set(slotData, SetOptions.merge()).await()
                    slotDao.markSlotSynced(slot.id, docId)
                }

                // 4. Pull down courses from Cloud Firestore & purge duplicates
                val cloudCoursesSnapshot = cloudCoursesRef.get().await()
                val seenCourseCodes = mutableSetOf<String>()

                for (doc in cloudCoursesSnapshot.documents) {
                    val rawCode = doc.getString("code") ?: continue
                    val normCode = normalizeCode(rawCode)
                    val canonicalDocId = getCourseDocId(normCode)

                    if (isCourseDeletionPending(normCode, doc.id) || courseDao.isCourseDeletedByCode(normCode)) {
                        try { doc.reference.delete().await() } catch (_: Exception) {}
                        continue
                    }

                    // Duplicate document in Firestore for same course: purge it!
                    if (seenCourseCodes.contains(normCode)) {
                        Log.d(TAG, "Purging duplicate cloud course document: ${doc.id}")
                        try { doc.reference.delete().await() } catch (_: Exception) {}
                        continue
                    }
                    seenCourseCodes.add(normCode)

                    // Normalize document ID in Firestore if legacy format was used
                    if (doc.id != canonicalDocId) {
                        try {
                            val dataToCopy = doc.data ?: emptyMap<String, Any>()
                            cloudCoursesRef.document(canonicalDocId).set(dataToCopy, SetOptions.merge()).await()
                            doc.reference.delete().await()
                        } catch (_: Exception) {}
                    }

                    val existing = courseDao.getCourseByNormalizedCode(normCode) ?: courseDao.getCourseByCloudId(doc.id) ?: courseDao.getCourseByCloudId(canonicalDocId)
                    if (existing == null) {
                        val newCourse = Course(
                            code = normCode,
                            name = doc.getString("name") ?: normCode,
                            instructor = doc.getString("instructor") ?: "",
                            colorHex = doc.getString("colorHex") ?: "#4F46E5",
                            location = doc.getString("location") ?: "",
                            isSynced = true,
                            cloudId = canonicalDocId,
                            isDeleted = false
                        )
                        courseDao.insertCourse(newCourse)
                    } else if (existing.isDeleted) {
                        try { doc.reference.delete().await() } catch (_: Exception) {}
                    } else if (!existing.isSynced) {
                        // Local edit not uploaded yet: keep it. The next sync pushes it.
                    } else {
                        val updated = existing.copy(
                            code = normCode,
                            name = doc.getString("name") ?: existing.name,
                            instructor = doc.getString("instructor") ?: existing.instructor,
                            colorHex = doc.getString("colorHex") ?: existing.colorHex,
                            location = doc.getString("location") ?: existing.location,
                            isSynced = true,
                            cloudId = canonicalDocId
                        )
                        courseDao.updateCourse(updated)

                        // Deduplicate local Room entries if multiple rows existed
                        val matching = courseDao.getAllCoursesMatchingNormalizedCode(normCode)
                        for (m in matching) {
                            if (m.id != existing.id) {
                                taskDao.deleteTasksForCourse(m.id)
                                slotDao.deleteSlotsForCourse(m.id)
                                courseDao.deleteCourseById(m.id)
                            }
                        }
                    }
                }

                // 5. Pull down tasks from Cloud Firestore & purge tasks for deleted courses
                val cloudTasksSnapshot = cloudTasksRef.get().await()
                for (doc in cloudTasksSnapshot.documents) {
                    val title = doc.getString("title") ?: continue
                    val courseCode = normalizeCode(doc.getString("courseCode") ?: "")

                    if (courseCode.isNotBlank() && (isCourseDeletionPending(courseCode) || courseDao.isCourseDeletedByCode(courseCode))) {
                        try { doc.reference.delete().await() } catch (_: Exception) {}
                        continue
                    }

                    if (isTaskDeletionPending(doc.id)) {
                        try { doc.reference.delete().await() } catch (_: Exception) {}
                        continue
                    }

                    val course = if (courseCode.isNotBlank()) courseDao.getCourseByNormalizedCode(courseCode) else null
                    if (course == null) {
                        // Orphaned task in cloud: delete it
                        try { doc.reference.delete().await() } catch (_: Exception) {}
                        continue
                    }
                    val courseId = course.id

                    val existing = taskDao.getTaskByCloudId(doc.id) ?: taskDao.getUnlinkedTaskByTitleAndCourse(title, courseId)
                    val priorityStr = doc.getString("priority") ?: "MEDIUM"
                    val statusStr = doc.getString("status") ?: "PENDING"
                    val reminderStr = doc.getString("reminderOption") ?: "NONE"
                    val desc = doc.getString("description") ?: ""
                    val dueEpoch = doc.getLong("dueDateEpochMillis") ?: System.currentTimeMillis()
                    val dueH = doc.getLong("dueTimeHour")?.toInt() ?: 23
                    val dueM = doc.getLong("dueTimeMinute")?.toInt() ?: 59

                    val parsedPriority = try { Priority.valueOf(priorityStr) } catch (_: Exception) { Priority.MEDIUM }
                    val parsedStatus = try { TaskStatus.valueOf(statusStr) } catch (_: Exception) { TaskStatus.PENDING }

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
                            isSynced = true,
                            cloudId = doc.id,
                            isDeleted = false
                        )
                        taskDao.insertTask(newTask)
                    } else if (existing.isDeleted) {
                        try { doc.reference.delete().await() } catch (_: Exception) {}
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
                            isSynced = true,
                            cloudId = doc.id
                        )
                        taskDao.updateTask(updated)
                    }
                }

                // 6. Pull down timetable slots from Cloud Firestore & purge slots for deleted courses
                val cloudSlotsSnapshot = cloudSlotsRef.get().await()
                for (doc in cloudSlotsSnapshot.documents) {
                    val courseCode = normalizeCode(doc.getString("courseCode") ?: "")
                    if (courseCode.isNotBlank() && (isCourseDeletionPending(courseCode) || courseDao.isCourseDeletedByCode(courseCode))) {
                        try { doc.reference.delete().await() } catch (_: Exception) {}
                        continue
                    }

                    if (isSlotDeletionPending(doc.id)) {
                        try { doc.reference.delete().await() } catch (_: Exception) {}
                        continue
                    }

                    val course = if (courseCode.isNotBlank()) courseDao.getCourseByNormalizedCode(courseCode) else null
                    if (course == null) {
                        try { doc.reference.delete().await() } catch (_: Exception) {}
                        continue
                    }
                    val courseId = course.id

                    val dayOfWeek = doc.getLong("dayOfWeek")?.toInt() ?: 1
                    val startHour = doc.getLong("startHour")?.toInt() ?: 9
                    val startMinute = doc.getLong("startMinute")?.toInt() ?: 0
                    val endHour = doc.getLong("endHour")?.toInt() ?: 10
                    val endMinute = doc.getLong("endMinute")?.toInt() ?: 30
                    val classroom = doc.getString("classroom") ?: ""
                    val instructor = doc.getString("instructor") ?: ""

                    val existing = slotDao.getSlotByCloudId(doc.id) ?: slotDao.findMatchingSlot(courseId, dayOfWeek, startHour, startMinute)
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
                            cloudId = doc.id
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
                            cloudId = doc.id
                        )
                        slotDao.updateSlot(updated)
                    }
                }
                
                // Deletes made on another device: a row that is already synced here (isSynced) but no
                // longer exists in the cloud was deleted elsewhere, so remove it here too.
                // Order matters: tasks and slots first, so a renamed course's children are moved to
                // the new course (matched by cloudId above) before the old course row is removed.
                val reminderScheduler = ReminderScheduler(context)
                if (!cloudTasksSnapshot.metadata.isFromCache) {
                    val cloudTaskIds = cloudTasksSnapshot.documents.map { it.id }.toSet()
                    for (t in taskDao.getAllActiveTasksSync()) {
                        if (!t.isSynced || t.cloudId.isBlank() || t.cloudId in cloudTaskIds) continue
                        reminderScheduler.cancelReminder(t.id)
                        taskDao.deleteTaskById(t.id)
                    }
                }
                if (!cloudSlotsSnapshot.metadata.isFromCache) {
                    val cloudSlotIds = cloudSlotsSnapshot.documents.map { it.id }.toSet()
                    for (s in slotDao.getAllSlotsSync()) {
                        if (s.isDeleted || !s.isSynced || s.cloudId.isBlank() || s.cloudId in cloudSlotIds) continue
                        slotDao.deleteSlotById(s.id)
                    }
                }
                if (!cloudCoursesSnapshot.metadata.isFromCache) {
                    val cloudCourseIds = cloudCoursesSnapshot.documents
                        .flatMap { listOf(it.id, getCourseDocId(normalizeCode(it.getString("code") ?: ""))) }
                        .toSet()
                    for (c in courseDao.getAllCoursesList()) {
                        if (c.isDeleted || !c.isSynced || c.cloudId.isBlank() || c.cloudId in cloudCourseIds) continue
                        for (t in taskDao.getAnyTasksForCourseSync(c.id)) reminderScheduler.cancelReminder(t.id)
                        taskDao.deleteTasksForCourse(c.id)
                        slotDao.deleteSlotsForCourse(c.id)
                        courseDao.deleteCourseById(c.id)
                    }
                }

                taskDao.deleteOrphanedTasks()
                slotDao.deleteOrphanedSlots()
                
                // Cloud changes may have added or changed tasks: make sure their alarms exist.
                reminderScheduler.rescheduleAll(database)
                }
            }

            Log.d(TAG, "syncAllDataNow: Completed successfully for user ${user.uid}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "syncAllDataNow: Error syncing data: ${e.message}", e)
            false
        }
    }

    /**
     * Starts real-time Firestore snapshot listeners guarded against resurrecting deleted courses/tasks/slots.
     */
    fun startRealtimeSync(context: Context, database: AppDatabase) {
        val user = getCurrentUser() ?: return
        stopRealtimeSync()

        try {
            val databaseId = context.getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(databaseId)
            val userDocRef = db.collection("users").document(user.uid)
            val coursesRef = userDocRef.collection("courses")
            val tasksRef = userDocRef.collection("tasks")
            val slotsRef = userDocRef.collection("slots")

            val courseDao = database.courseDao()
            val taskDao = database.taskDao()
            val slotDao = database.lectureSlotDao()
            val reminderScheduler = ReminderScheduler(context.applicationContext)

            // 1. Courses listener
            coursesListenerRegistration = coursesRef.addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                CoroutineScope(Dispatchers.IO).launch {
                    syncMutex.withLock {
                        for (change in snapshot.documentChanges) {
                            val doc = change.document
                            val rawCode = doc.getString("code") ?: continue
                            val normCode = normalizeCode(rawCode)
                            val canonicalDocId = getCourseDocId(normCode)

                            if (isCourseDeletionPending(normCode, doc.id) || courseDao.isCourseDeletedByCode(normCode)) {
                                try { doc.reference.delete() } catch (_: Exception) {}
                                continue
                            }

                            when (change.type) {
                                DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                    val existing = courseDao.getCourseByNormalizedCode(normCode) ?: courseDao.getCourseByCloudId(doc.id) ?: courseDao.getCourseByCloudId(canonicalDocId)
                                    if (existing == null) {
                                        val newCourse = Course(
                                            code = normCode,
                                            name = doc.getString("name") ?: normCode,
                                            instructor = doc.getString("instructor") ?: "",
                                            colorHex = doc.getString("colorHex") ?: "#4F46E5",
                                            location = doc.getString("location") ?: "",
                                            isSynced = true,
                                            cloudId = canonicalDocId,
                                            isDeleted = false
                                        )
                                        courseDao.insertCourse(newCourse)
                                    } else if (existing.isDeleted) {
                                        try { doc.reference.delete() } catch (_: Exception) {}
                                    } else if (!existing.isSynced) {
                                        // Local edit not uploaded yet: keep it. The next sync pushes it.
                                    } else {
                                        val updated = existing.copy(
                                            code = normCode,
                                            name = doc.getString("name") ?: existing.name,
                                            instructor = doc.getString("instructor") ?: existing.instructor,
                                            colorHex = doc.getString("colorHex") ?: existing.colorHex,
                                            location = doc.getString("location") ?: existing.location,
                                            isSynced = true,
                                            cloudId = canonicalDocId
                                        )
                                        courseDao.updateCourse(updated)
                                    }
                                }
                                DocumentChange.Type.REMOVED -> {
                                    // Do not delete by code here: the document may be a duplicate or the old copy of a
                                    // renamed course. A full sync removes only courses that are really gone from the cloud.
                                    CoroutineScope(Dispatchers.IO).launch { syncAllDataNow(context, database) }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Tasks listener
            tasksListenerRegistration = tasksRef.addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                CoroutineScope(Dispatchers.IO).launch {
                    syncMutex.withLock {
                        for (change in snapshot.documentChanges) {
                            val doc = change.document
                            val title = doc.getString("title") ?: continue
                            val courseCode = normalizeCode(doc.getString("courseCode") ?: "")

                            if (courseCode.isNotBlank() && (isCourseDeletionPending(courseCode) || courseDao.isCourseDeletedByCode(courseCode))) {
                                try { doc.reference.delete() } catch (_: Exception) {}
                                continue
                            }
                            if (isTaskDeletionPending(doc.id)) {
                                try { doc.reference.delete() } catch (_: Exception) {}
                                continue
                            }

                            val course = if (courseCode.isNotBlank()) courseDao.getCourseByNormalizedCode(courseCode) else null
                                if (course == null) {
                                // The course may simply not have arrived yet (the three listeners are independent).
                                // Never delete here; a full sync pulls courses first and cleans up real orphans.
                                if (change.type != DocumentChange.Type.REMOVED) {
                                    CoroutineScope(Dispatchers.IO).launch { syncAllDataNow(context, database) }
                                }
                                continue
                            }
                            val courseId = course.id

                            when (change.type) {
                                DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                    val existing = taskDao.getTaskByCloudId(doc.id) ?: taskDao.getUnlinkedTaskByTitleAndCourse(title, courseId)
                                    val priorityStr = doc.getString("priority") ?: "MEDIUM"
                                    val statusStr = doc.getString("status") ?: "PENDING"
                                    val reminderStr = doc.getString("reminderOption") ?: "NONE"
                                    val desc = doc.getString("description") ?: ""
                                    val dueEpoch = doc.getLong("dueDateEpochMillis") ?: System.currentTimeMillis()
                                    val dueH = doc.getLong("dueTimeHour")?.toInt() ?: 23
                                    val dueM = doc.getLong("dueTimeMinute")?.toInt() ?: 59

                                    val parsedPriority = try { Priority.valueOf(priorityStr) } catch (_: Exception) { Priority.MEDIUM }
                                    val parsedStatus = try { TaskStatus.valueOf(statusStr) } catch (_: Exception) { TaskStatus.PENDING }

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
                                            isSynced = true,
                                            cloudId = doc.id,
                                            isDeleted = false
                                        )
                                        val newId = taskDao.insertTask(newTask)
                                        if (parsedStatus != TaskStatus.COMPLETED) {
                                            reminderScheduler.scheduleReminder(newTask.copy(id = newId), course.code)
                                        }

                                    } else if (existing.isDeleted) {
                                        try { doc.reference.delete() } catch (_: Exception) {}
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
                                            isSynced = true,
                                            cloudId = doc.id
                                        )
                                        taskDao.updateTask(updated)
                                        if (parsedStatus == TaskStatus.COMPLETED) {
                                            reminderScheduler.cancelReminder(updated.id)
                                        } else {
                                            reminderScheduler.scheduleReminder(updated, course.code)
                                        }

                                    }
                                }
                                DocumentChange.Type.REMOVED -> {
                                    val existing = taskDao.getTaskByCloudId(doc.id)
                                    if (existing != null) {
                                         reminderScheduler.cancelReminder(existing.id)
                                        taskDao.deleteTaskById(existing.id)

                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Timetable Slots listener
            slotsListenerRegistration = slotsRef.addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                CoroutineScope(Dispatchers.IO).launch {
                    syncMutex.withLock {
                        for (change in snapshot.documentChanges) {
                            val doc = change.document
                            val courseCode = normalizeCode(doc.getString("courseCode") ?: "")

                            if (courseCode.isNotBlank() && (isCourseDeletionPending(courseCode) || courseDao.isCourseDeletedByCode(courseCode))) {
                                try { doc.reference.delete() } catch (_: Exception) {}
                                continue
                            }
                            if (isSlotDeletionPending(doc.id)) {
                                try { doc.reference.delete() } catch (_: Exception) {}
                                continue
                            }

                            val course = if (courseCode.isNotBlank()) courseDao.getCourseByNormalizedCode(courseCode) else null
                                if (course == null) {
                                // The course may simply not have arrived yet (the three listeners are independent).
                                // Never delete here; a full sync pulls courses first and cleans up real orphans.
                                if (change.type != DocumentChange.Type.REMOVED) {
                                    CoroutineScope(Dispatchers.IO).launch { syncAllDataNow(context, database) }
                                }
                                continue
                            }
                            val courseId = course.id

                            val dayOfWeek = doc.getLong("dayOfWeek")?.toInt() ?: 1
                            val startHour = doc.getLong("startHour")?.toInt() ?: 9
                            val startMinute = doc.getLong("startMinute")?.toInt() ?: 0
                            val endHour = doc.getLong("endHour")?.toInt() ?: 10
                            val endMinute = doc.getLong("endMinute")?.toInt() ?: 30
                            val classroom = doc.getString("classroom") ?: ""
                            val instructor = doc.getString("instructor") ?: ""

                            when (change.type) {
                                DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                    val existing = slotDao.getSlotByCloudId(doc.id) ?: slotDao.findMatchingSlot(courseId, dayOfWeek, startHour, startMinute)
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
                                            cloudId = doc.id
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
                                            cloudId = doc.id
                                        )
                                        slotDao.updateSlot(updated)
                                    }
                                }
                                DocumentChange.Type.REMOVED -> {
                                    val existing = slotDao.getSlotByCloudId(doc.id) ?: slotDao.findMatchingSlot(courseId, dayOfWeek, startHour, startMinute)
                                    if (existing != null) {
                                        slotDao.deleteSlotById(existing.id)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Log.d(TAG, "Real-time guarded Firestore sync (courses, tasks, slots) started for user ${user.uid}")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting real-time sync: ${e.message}", e)
        }
    }

    fun stopRealtimeSync() {
        coursesListenerRegistration?.remove()
        coursesListenerRegistration = null
        tasksListenerRegistration?.remove()
        tasksListenerRegistration = null
        slotsListenerRegistration?.remove()
        slotsListenerRegistration = null
        Log.d(TAG, "Real-time sync stopped")
    }
}
