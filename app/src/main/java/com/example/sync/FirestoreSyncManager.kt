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
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object FirestoreSyncManager {

    private const val TAG = "FirestoreSyncManager"

    private val isSyncingNow = AtomicBoolean(false)

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

    fun recordPendingCourseDeletion(courseId: Long, code: String, cloudId: String) {
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
        if (code.isNotBlank()) {
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
        val normCode = code.trim().uppercase()
        if (normCode.isNotBlank() && pendingDeletedCourseCodes.contains(normCode)) {
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
            val sanitized = code.trim().uppercase().replace(Regex("[^A-Z0-9]"), "_")
            "course_$sanitized"
        }
    }

    fun getTaskDocId(task: TaskItem, courseCode: String = ""): String {
        return if (task.cloudId.isNotBlank()) {
            task.cloudId
        } else {
            val sanitizedCourse = courseCode.trim().uppercase().replace(Regex("[^A-Z0-9]"), "_")
            val sanitizedTitle = task.title.trim().lowercase().replace(Regex("[^a-z0-9]"), "_").take(24)
            "task_${sanitizedCourse}_${sanitizedTitle}_${task.id}"
        }
    }

    fun getSlotDocId(slot: LectureSlot, courseCode: String = ""): String {
        return if (slot.cloudId.isNotBlank()) {
            slot.cloudId
        } else {
            val sanitizedCourse = courseCode.trim().uppercase().replace(Regex("[^A-Z0-9]"), "_")
            "slot_${sanitizedCourse}_d${slot.dayOfWeek}_${slot.startHour}_${slot.startMinute}_${slot.endHour}_${slot.endMinute}"
        }
    }

    /**
     * Immediately deletes a course, its associated tasks, and timetable slots from Cloud Firestore.
     */
    suspend fun deleteCourseFromCloud(
        context: Context,
        course: Course,
        associatedTasks: List<TaskItem> = emptyList(),
        associatedSlots: List<LectureSlot> = emptyList()
    ): Boolean = withContext(Dispatchers.IO) {
        val user = Firebase.auth.currentUser ?: return@withContext false

        recordPendingCourseDeletion(course.id, course.code, course.cloudId)
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

            val normCode = course.code.trim().uppercase()
            val formattedDocId = getCourseDocId(normCode, course.cloudId)

            // 1. Delete course doc
            try { coursesRef.document(formattedDocId).delete().await() } catch (_: Exception) {}
            if (course.cloudId.isNotBlank() && course.cloudId != formattedDocId) {
                try { coursesRef.document(course.cloudId).delete().await() } catch (_: Exception) {}
            }
            try { coursesRef.document(course.id.toString()).delete().await() } catch (_: Exception) {}

            // 2. Query any documents matching course code
            val coursesByCode = coursesRef.whereEqualTo("code", normCode).get().await()
            for (doc in coursesByCode.documents) {
                try { doc.reference.delete().await() } catch (_: Exception) {}
            }

            // 3. Delete associated tasks
            for (task in associatedTasks) {
                if (task.cloudId.isNotBlank()) {
                    try { tasksRef.document(task.cloudId).delete().await() } catch (_: Exception) {}
                }
                val taskDocId = getTaskDocId(task, normCode)
                try { tasksRef.document(taskDocId).delete().await() } catch (_: Exception) {}
            }
            val tasksByCourseCode = tasksRef.whereEqualTo("courseCode", normCode).get().await()
            for (doc in tasksByCourseCode.documents) {
                try { doc.reference.delete().await() } catch (_: Exception) {}
            }

            // 4. Delete associated timetable slots
            for (slot in associatedSlots) {
                if (slot.cloudId.isNotBlank()) {
                    try { slotsRef.document(slot.cloudId).delete().await() } catch (_: Exception) {}
                }
                val slotDocId = getSlotDocId(slot, normCode)
                try { slotsRef.document(slotDocId).delete().await() } catch (_: Exception) {}
            }
            val slotsByCourseCode = slotsRef.whereEqualTo("courseCode", normCode).get().await()
            for (doc in slotsByCourseCode.documents) {
                try { doc.reference.delete().await() } catch (_: Exception) {}
            }

            Log.d(TAG, "Successfully deleted course $normCode, tasks, and slots from Firestore")
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
        val user = Firebase.auth.currentUser ?: return@withContext false
        recordPendingTaskDeletion(task.id, task.cloudId)

        return@withContext try {
            val databaseId = context.getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(databaseId)
            val tasksRef = db.collection("users").document(user.uid).collection("tasks")

            if (task.cloudId.isNotBlank()) {
                try { tasksRef.document(task.cloudId).delete().await() } catch (_: Exception) {}
            }
            val docId = getTaskDocId(task, courseCode ?: "")
            try { tasksRef.document(docId).delete().await() } catch (_: Exception) {}

            if (!courseCode.isNullOrBlank() && task.title.isNotBlank()) {
                val match = tasksRef.whereEqualTo("title", task.title)
                    .whereEqualTo("courseCode", courseCode.trim().uppercase())
                    .get().await()
                for (doc in match.documents) {
                    try { doc.reference.delete().await() } catch (_: Exception) {}
                }
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
        val user = Firebase.auth.currentUser ?: return@withContext false
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
     * Guarantees all local courses are uploaded and deduplicated properly on download.
     */
    suspend fun syncAllDataNow(context: Context, database: AppDatabase): Boolean = withContext(Dispatchers.IO) {
        val user = Firebase.auth.currentUser ?: run {
            Log.d(TAG, "syncAllDataNow: User not signed in, skipping sync")
            return@withContext false
        }

        if (!isSyncingNow.compareAndSet(false, true)) {
            Log.d(TAG, "syncAllDataNow: Sync already in progress, skipping redundant trigger")
            return@withContext true
        }

        try {
            val databaseId = context.getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(databaseId)
            val userDocRef = db.collection("users").document(user.uid)
            val cloudCoursesRef = userDocRef.collection("courses")
            val cloudTasksRef = userDocRef.collection("tasks")
            val cloudSlotsRef = userDocRef.collection("slots")

            val courseDao = database.courseDao()
            val taskDao = database.taskDao()
            val slotDao = database.lectureSlotDao()

            // 0. Purge soft-deleted items
            val deletedCourses = courseDao.getDeletedCourses()
            for (c in deletedCourses) {
                val tasks = taskDao.getAnyTasksForCourseSync(c.id)
                val slots = slotDao.getSlotsForCourseSync(c.id)
                deleteCourseFromCloud(context, c, tasks, slots)
                taskDao.deleteTasksForCourse(c.id)
                slotDao.deleteSlotsForCourse(c.id)
                courseDao.deleteCourseById(c.id)
            }

            val deletedTasks = taskDao.getDeletedTasks()
            for (t in deletedTasks) {
                val c = courseDao.getAnyCourseByIdSync(t.courseId)
                deleteTaskFromCloud(context, t, c?.code)
                taskDao.deleteTaskById(t.id)
            }

            // 1. Push ALL active courses to Cloud Firestore (ensures all 7+ courses are backed up)
            val allCourses = courseDao.getAllCoursesList()
            for (course in allCourses) {
                val normCode = course.code.trim().uppercase()
                val docId = getCourseDocId(normCode, course.cloudId)
                val courseData = hashMapOf<String, Any>(
                    "userId" to user.uid,
                    "code" to normCode,
                    "name" to course.name.trim(),
                    "instructor" to course.instructor.trim(),
                    "colorHex" to course.colorHex,
                    "location" to course.location.trim(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                cloudCoursesRef.document(docId).set(courseData, SetOptions.merge()).await()
                courseDao.markCourseSynced(course.id, docId)
            }

            // 2. Push ALL active tasks to Cloud Firestore
            val allTasks = taskDao.getAllActiveTasksSync()
            for (task in allTasks) {
                val course = courseDao.getCourseByIdSync(task.courseId) ?: courseDao.getAnyCourseByIdSync(task.courseId)
                val normCode = course?.code?.trim()?.uppercase() ?: ""
                val docId = getTaskDocId(task, normCode)
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

            // 3. Push ALL timetable lecture slots to Cloud Firestore
            val allSlots = slotDao.getAllSlotsSync()
            for (slot in allSlots) {
                val course = courseDao.getCourseByIdSync(slot.courseId) ?: courseDao.getAnyCourseByIdSync(slot.courseId)
                val normCode = course?.code?.trim()?.uppercase() ?: ""
                if (normCode.isNotBlank()) {
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
            }

            // 4. Pull down courses from Cloud Firestore and upsert (prevent duplicates)
            val cloudCoursesSnapshot = cloudCoursesRef.get().await()
            for (doc in cloudCoursesSnapshot.documents) {
                val code = doc.getString("code") ?: continue
                val normCode = code.trim().uppercase()

                if (isCourseDeletionPending(normCode, doc.id) || courseDao.isCourseDeletedByCode(normCode)) {
                    try { doc.reference.delete().await() } catch (_: Exception) {}
                    continue
                }

                val existing = courseDao.getAnyCourseByCode(normCode) ?: courseDao.getCourseByCloudId(doc.id)
                if (existing == null) {
                    val newCourse = Course(
                        code = normCode,
                        name = doc.getString("name") ?: normCode,
                        instructor = doc.getString("instructor") ?: "",
                        colorHex = doc.getString("colorHex") ?: "#4F46E5",
                        location = doc.getString("location") ?: "",
                        isSynced = true,
                        cloudId = doc.id,
                        isDeleted = false
                    )
                    courseDao.insertCourse(newCourse)
                } else if (existing.isDeleted) {
                    try { doc.reference.delete().await() } catch (_: Exception) {}
                } else {
                    // Update existing record with cloud info, deduplicating
                    val updated = existing.copy(
                        name = doc.getString("name") ?: existing.name,
                        instructor = doc.getString("instructor") ?: existing.instructor,
                        colorHex = doc.getString("colorHex") ?: existing.colorHex,
                        location = doc.getString("location") ?: existing.location,
                        isSynced = true,
                        cloudId = doc.id
                    )
                    courseDao.updateCourse(updated)
                }
            }

            // 5. Pull down tasks from Cloud Firestore and upsert (prevent duplicates)
            val cloudTasksSnapshot = cloudTasksRef.get().await()
            for (doc in cloudTasksSnapshot.documents) {
                val title = doc.getString("title") ?: continue
                val courseCode = (doc.getString("courseCode") ?: "").trim().uppercase()

                if (courseCode.isNotBlank() && (isCourseDeletionPending(courseCode) || courseDao.isCourseDeletedByCode(courseCode))) {
                    try { doc.reference.delete().await() } catch (_: Exception) {}
                    continue
                }

                if (isTaskDeletionPending(doc.id)) {
                    try { doc.reference.delete().await() } catch (_: Exception) {}
                    continue
                }

                val course = if (courseCode.isNotBlank()) courseDao.getCourseByCode(courseCode) else null
                val courseId = course?.id ?: continue

                val existing = taskDao.getTaskByCloudId(doc.id) ?: taskDao.getAnyTaskByTitleAndCourse(title, courseId)
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

            // 6. Pull down timetable slots from Cloud Firestore and upsert (prevent duplicates)
            val cloudSlotsSnapshot = cloudSlotsRef.get().await()
            for (doc in cloudSlotsSnapshot.documents) {
                val courseCode = (doc.getString("courseCode") ?: "").trim().uppercase()
                if (courseCode.isNotBlank() && (isCourseDeletionPending(courseCode) || courseDao.isCourseDeletedByCode(courseCode))) {
                    try { doc.reference.delete().await() } catch (_: Exception) {}
                    continue
                }

                if (isSlotDeletionPending(doc.id)) {
                    try { doc.reference.delete().await() } catch (_: Exception) {}
                    continue
                }

                val course = if (courseCode.isNotBlank()) courseDao.getCourseByCode(courseCode) else null
                val courseId = course?.id ?: continue

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

            Log.d(TAG, "syncAllDataNow: Completed successfully for user ${user.uid}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "syncAllDataNow: Error syncing data: ${e.message}", e)
            false
        } finally {
            isSyncingNow.set(false)
        }
    }

    /**
     * Starts real-time Firestore snapshot listeners guarded against resurrecting deleted courses/tasks/slots.
     */
    fun startRealtimeSync(context: Context, database: AppDatabase) {
        val user = Firebase.auth.currentUser ?: return
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

            // 1. Courses listener
            coursesListenerRegistration = coursesRef.addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                CoroutineScope(Dispatchers.IO).launch {
                    for (change in snapshot.documentChanges) {
                        val doc = change.document
                        val code = doc.getString("code") ?: continue
                        val normCode = code.trim().uppercase()

                        if (isCourseDeletionPending(normCode, doc.id) || courseDao.isCourseDeletedByCode(normCode)) {
                            try { doc.reference.delete() } catch (_: Exception) {}
                            continue
                        }

                        when (change.type) {
                            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                val existing = courseDao.getAnyCourseByCode(normCode) ?: courseDao.getCourseByCloudId(doc.id)
                                if (existing == null) {
                                    val newCourse = Course(
                                        code = normCode,
                                        name = doc.getString("name") ?: normCode,
                                        instructor = doc.getString("instructor") ?: "",
                                        colorHex = doc.getString("colorHex") ?: "#4F46E5",
                                        location = doc.getString("location") ?: "",
                                        isSynced = true,
                                        cloudId = doc.id,
                                        isDeleted = false
                                    )
                                    courseDao.insertCourse(newCourse)
                                } else if (existing.isDeleted) {
                                    try { doc.reference.delete() } catch (_: Exception) {}
                                } else {
                                    val updated = existing.copy(
                                        name = doc.getString("name") ?: existing.name,
                                        instructor = doc.getString("instructor") ?: existing.instructor,
                                        colorHex = doc.getString("colorHex") ?: existing.colorHex,
                                        location = doc.getString("location") ?: existing.location,
                                        isSynced = true,
                                        cloudId = doc.id
                                    )
                                    courseDao.updateCourse(updated)
                                }
                            }
                            DocumentChange.Type.REMOVED -> {
                                val existing = courseDao.getCourseByCode(normCode)
                                if (existing != null) {
                                    taskDao.deleteTasksForCourse(existing.id)
                                    slotDao.deleteSlotsForCourse(existing.id)
                                    courseDao.deleteCourseById(existing.id)
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
                    for (change in snapshot.documentChanges) {
                        val doc = change.document
                        val title = doc.getString("title") ?: continue
                        val courseCode = (doc.getString("courseCode") ?: "").trim().uppercase()

                        if (courseCode.isNotBlank() && (isCourseDeletionPending(courseCode) || courseDao.isCourseDeletedByCode(courseCode))) {
                            try { doc.reference.delete() } catch (_: Exception) {}
                            continue
                        }
                        if (isTaskDeletionPending(doc.id)) {
                            try { doc.reference.delete() } catch (_: Exception) {}
                            continue
                        }

                        val course = if (courseCode.isNotBlank()) courseDao.getCourseByCode(courseCode) else null
                        val courseId = course?.id ?: continue

                        when (change.type) {
                            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                val existing = taskDao.getTaskByCloudId(doc.id) ?: taskDao.getAnyTaskByTitleAndCourse(title, courseId)
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
                                    try { doc.reference.delete() } catch (_: Exception) {}
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
                            DocumentChange.Type.REMOVED -> {
                                val existing = taskDao.getTaskByCloudId(doc.id) ?: taskDao.getTaskByTitleAndCourse(title, courseId)
                                if (existing != null) {
                                    taskDao.deleteTaskById(existing.id)
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
                    for (change in snapshot.documentChanges) {
                        val doc = change.document
                        val courseCode = (doc.getString("courseCode") ?: "").trim().uppercase()

                        if (courseCode.isNotBlank() && (isCourseDeletionPending(courseCode) || courseDao.isCourseDeletedByCode(courseCode))) {
                            try { doc.reference.delete() } catch (_: Exception) {}
                            continue
                        }
                        if (isSlotDeletionPending(doc.id)) {
                            try { doc.reference.delete() } catch (_: Exception) {}
                            continue
                        }

                        val course = if (courseCode.isNotBlank()) courseDao.getCourseByCode(courseCode) else null
                        val courseId = course?.id ?: continue

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
