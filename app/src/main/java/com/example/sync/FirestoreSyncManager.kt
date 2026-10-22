package com.example.sync

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.local.Course
import com.example.data.local.Priority
import com.example.data.local.ReminderOption
import com.example.data.local.TaskItem
import com.example.data.local.TaskStatus
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

object FirestoreSyncManager {

    private const val TAG = "FirestoreSyncManager"

    // Guard sets for in-flight or confirmed deletions to prevent re-insertion/resurrection
    private val pendingDeletedCourseCodes: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val pendingDeletedCourseIds: MutableSet<Long> = ConcurrentHashMap.newKeySet()
    private val pendingDeletedCourseDocIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val pendingDeletedTaskIds: MutableSet<Long> = ConcurrentHashMap.newKeySet()
    private val pendingDeletedTaskDocIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private var coursesListenerRegistration: ListenerRegistration? = null
    private var tasksListenerRegistration: ListenerRegistration? = null

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

    fun recordPendingTaskDeletion(taskId: Long, cloudId: String) {
        if (taskId > 0) {
            pendingDeletedTaskIds.add(taskId)
        }
        if (cloudId.isNotBlank()) {
            pendingDeletedTaskDocIds.add(cloudId)
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

    /**
     * Immediately deletes a course, its subcollections (notes), and all associated tasks from Cloud Firestore.
     */
    suspend fun deleteCourseFromCloud(
        context: Context,
        course: Course,
        associatedTasks: List<TaskItem>
    ): Boolean = withContext(Dispatchers.IO) {
        val user = Firebase.auth.currentUser ?: run {
            Log.d(TAG, "No authenticated user logged in; leaving as soft delete for later sync")
            return@withContext false
        }

        recordPendingCourseDeletion(course.id, course.code, course.cloudId)
        for (task in associatedTasks) {
            recordPendingTaskDeletion(task.id, task.cloudId)
        }

        return@withContext try {
            val databaseId = context.getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(databaseId)
            val userDocRef = db.collection("users").document(user.uid)
            val coursesRef = userDocRef.collection("courses")
            val tasksRef = userDocRef.collection("tasks")

            // 1. Delete course document(s) by ID variants
            if (course.cloudId.isNotBlank()) {
                try { coursesRef.document(course.cloudId).delete().await() } catch (_: Exception) {}
            }
            val formattedDocId = "course_${course.id}_${course.code.replace(Regex("[^A-Za-z0-9]"), "_")}"
            try { coursesRef.document(formattedDocId).delete().await() } catch (_: Exception) {}
            try { coursesRef.document(course.id.toString()).delete().await() } catch (_: Exception) {}

            // 2. Query any documents matching course code to guarantee complete removal
            val coursesByCode = coursesRef.whereEqualTo("code", course.code).get().await()
            for (doc in coursesByCode.documents) {
                // Delete notes subcollection under course if any
                try {
                    val notesSnapshot = doc.reference.collection("notes").get().await()
                    for (noteDoc in notesSnapshot.documents) {
                        try { noteDoc.reference.delete().await() } catch (_: Exception) {}
                    }
                } catch (_: Exception) {}
                try { doc.reference.delete().await() } catch (_: Exception) {}
            }

            // 3. Delete any notes in root users/{userId}/notes matching courseCode
            try {
                val rootNotes = userDocRef.collection("notes").whereEqualTo("courseCode", course.code).get().await()
                for (noteDoc in rootNotes.documents) {
                    try { noteDoc.reference.delete().await() } catch (_: Exception) {}
                }
            } catch (_: Exception) {}

            // 4. Delete associated tasks from Firestore
            // A. By task IDs known locally
            for (task in associatedTasks) {
                if (task.cloudId.isNotBlank()) {
                    try { tasksRef.document(task.cloudId).delete().await() } catch (_: Exception) {}
                }
                try { tasksRef.document("task_${task.id}").delete().await() } catch (_: Exception) {}
                try { tasksRef.document(task.id.toString()).delete().await() } catch (_: Exception) {}
            }

            // B. By query on courseCode
            val tasksByCourseCode = tasksRef.whereEqualTo("courseCode", course.code).get().await()
            for (doc in tasksByCourseCode.documents) {
                try { doc.reference.delete().await() } catch (_: Exception) {}
            }

            Log.d(TAG, "Successfully deleted course ${course.code} and associated tasks from Cloud Firestore")
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
            try { tasksRef.document("task_${task.id}").delete().await() } catch (_: Exception) {}
            try { tasksRef.document(task.id.toString()).delete().await() } catch (_: Exception) {}

            // Query by title and courseCode as safeguard
            if (courseCode != null && task.title.isNotBlank()) {
                val match = tasksRef.whereEqualTo("title", task.title)
                    .whereEqualTo("courseCode", courseCode)
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
     * Starts real-time Firestore snapshot listeners guarded against resurrecting deleted courses/tasks.
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

            val courseDao = database.courseDao()
            val taskDao = database.taskDao()

            // 1. Courses real-time listener
            coursesListenerRegistration = coursesRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Courses snapshot listener error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                CoroutineScope(Dispatchers.IO).launch {
                    for (change in snapshot.documentChanges) {
                        val doc = change.document
                        val code = doc.getString("code") ?: continue
                        val normCode = code.trim().uppercase()

                        // SNAPSHOT LISTENER GUARD: Check if course is deleted or deletion is pending
                        if (isCourseDeletionPending(normCode, doc.id) || courseDao.isCourseDeletedByCode(normCode)) {
                            Log.d(TAG, "Snapshot guard: Ignoring deleted/pending course $normCode")
                            // Clean up document from cloud if it still exists
                            try { doc.reference.delete() } catch (_: Exception) {}
                            continue
                        }

                        when (change.type) {
                            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                val existing = courseDao.getAnyCourseByCode(normCode)
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
                                    // Soft-deleted locally, do not resurrect!
                                    try { doc.reference.delete() } catch (_: Exception) {}
                                } else {
                                    if (!existing.isSynced || existing.cloudId != doc.id) {
                                        courseDao.markCourseSynced(existing.id, doc.id)
                                    }
                                }
                            }
                            DocumentChange.Type.REMOVED -> {
                                val existing = courseDao.getCourseByCode(normCode)
                                if (existing != null) {
                                    taskDao.deleteTasksForCourse(existing.id)
                                    courseDao.deleteCourseById(existing.id)
                                }
                            }
                        }
                    }
                }
            }

            // 2. Tasks real-time listener
            tasksListenerRegistration = tasksRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Tasks snapshot listener error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                CoroutineScope(Dispatchers.IO).launch {
                    for (change in snapshot.documentChanges) {
                        val doc = change.document
                        val title = doc.getString("title") ?: continue
                        val courseCode = (doc.getString("courseCode") ?: "").trim().uppercase()

                        // SNAPSHOT LISTENER GUARD: Check if parent course or task is deleted/pending
                        if (courseCode.isNotBlank() && (isCourseDeletionPending(courseCode) || courseDao.isCourseDeletedByCode(courseCode))) {
                            Log.d(TAG, "Snapshot guard: Ignoring task for deleted course $courseCode")
                            try { doc.reference.delete() } catch (_: Exception) {}
                            continue
                        }

                        if (isTaskDeletionPending(doc.id)) {
                            Log.d(TAG, "Snapshot guard: Ignoring pending deleted task ${doc.id}")
                            try { doc.reference.delete() } catch (_: Exception) {}
                            continue
                        }

                        val course = if (courseCode.isNotBlank()) courseDao.getCourseByCode(courseCode) else null
                        val courseId = course?.id ?: continue

                        when (change.type) {
                            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                val existing = taskDao.getAnyTaskByTitleAndCourse(title, courseId)
                                if (existing == null) {
                                    val priorityStr = doc.getString("priority") ?: "MEDIUM"
                                    val statusStr = doc.getString("status") ?: "PENDING"
                                    val reminderStr = doc.getString("reminderOption") ?: "NONE"

                                    val newTask = TaskItem(
                                        courseId = courseId,
                                        title = title,
                                        description = doc.getString("description") ?: "",
                                        dueDateEpochMillis = doc.getLong("dueDateEpochMillis") ?: System.currentTimeMillis(),
                                        dueTimeHour = doc.getLong("dueTimeHour")?.toInt() ?: 23,
                                        dueTimeMinute = doc.getLong("dueTimeMinute")?.toInt() ?: 59,
                                        priority = try { Priority.valueOf(priorityStr) } catch (_: Exception) { Priority.MEDIUM },
                                        status = try { TaskStatus.valueOf(statusStr) } catch (_: Exception) { TaskStatus.PENDING },
                                        reminderOption = try { ReminderOption.valueOf(reminderStr) } catch (_: Exception) { ReminderOption.NONE },
                                        isSynced = true,
                                        cloudId = doc.id,
                                        isDeleted = false
                                    )
                                    taskDao.insertTask(newTask)
                                } else if (existing.isDeleted) {
                                    try { doc.reference.delete() } catch (_: Exception) {}
                                } else {
                                    if (!existing.isSynced || existing.cloudId != doc.id) {
                                        taskDao.markTaskSynced(existing.id, doc.id)
                                    }
                                }
                            }
                            DocumentChange.Type.REMOVED -> {
                                val existing = taskDao.getTaskByTitleAndCourse(title, courseId)
                                if (existing != null) {
                                    taskDao.deleteTaskById(existing.id)
                                }
                            }
                        }
                    }
                }
            }

            Log.d(TAG, "Real-time guarded Firestore sync started for user ${user.uid}")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting real-time sync: ${e.message}", e)
        }
    }

    fun stopRealtimeSync() {
        coursesListenerRegistration?.remove()
        coursesListenerRegistration = null
        tasksListenerRegistration?.remove()
        tasksListenerRegistration = null
        Log.d(TAG, "Real-time sync stopped")
    }
}
