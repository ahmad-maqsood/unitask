package com.example.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.local.Course
import com.example.data.local.Priority
import com.example.data.local.ReminderOption
import com.example.data.local.TaskItem
import com.example.data.local.TaskStatus
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "SyncWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val user = Firebase.auth.currentUser ?: run {
            Log.d(TAG, "No Google user logged in; skipping cloud sync")
            return@withContext Result.success()
        }

        try {
            val databaseId = applicationContext.getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(databaseId)
            val localDb = AppDatabase.getDatabase(applicationContext)
            val courseDao = localDb.courseDao()
            val taskDao = localDb.taskDao()

            val userDocRef = db.collection("users").document(user.uid)
            val cloudCoursesRef = userDocRef.collection("courses")
            val cloudTasksRef = userDocRef.collection("tasks")

            // 0. Sync soft-deleted courses to Cloud Firestore (purge deleted items)
            val deletedCourses = courseDao.getDeletedCourses()
            for (course in deletedCourses) {
                Log.d(TAG, "Syncing soft-deleted course ${course.code} to Cloud Firestore")
                val tasks = taskDao.getAnyTasksForCourseSync(course.id)
                val success = FirestoreSyncManager.deleteCourseFromCloud(applicationContext, course, tasks)
                if (success) {
                    taskDao.deleteTasksForCourse(course.id)
                    courseDao.deleteCourseById(course.id)
                }
            }

            // 0.5. Sync soft-deleted tasks to Cloud Firestore
            val deletedTasks = taskDao.getDeletedTasks()
            for (task in deletedTasks) {
                val course = courseDao.getAnyCourseByIdSync(task.courseId)
                val success = FirestoreSyncManager.deleteTaskFromCloud(applicationContext, task, course?.code)
                if (success) {
                    taskDao.deleteTaskById(task.id)
                }
            }

            // 1. Push unsynced active courses from Room to Firestore
            val unsyncedCourses = courseDao.getUnsyncedCourses()
            for (course in unsyncedCourses) {
                val docId = if (course.cloudId.isNotBlank()) {
                    course.cloudId
                } else {
                    "course_${course.id}_${course.code.replace(Regex("[^A-Za-z0-9]"), "_")}"
                }

                val courseData = hashMapOf<String, Any>(
                    "userId" to user.uid,
                    "code" to course.code,
                    "name" to course.name,
                    "instructor" to course.instructor,
                    "colorHex" to course.colorHex,
                    "location" to course.location,
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                cloudCoursesRef.document(docId).set(courseData, SetOptions.merge()).await()
                courseDao.markCourseSynced(course.id, docId)
            }

            // 2. Push unsynced active tasks from Room to Firestore
            val unsyncedTasks = taskDao.getUnsyncedTasks()
            for (task in unsyncedTasks) {
                val course = courseDao.getCourseByIdSync(task.courseId)
                val docId = if (task.cloudId.isNotBlank()) {
                    task.cloudId
                } else {
                    "task_${task.id}"
                }

                val taskData = hashMapOf<String, Any>(
                    "userId" to user.uid,
                    "courseCode" to (course?.code ?: ""),
                    "title" to task.title,
                    "description" to task.description,
                    "dueDateEpochMillis" to task.dueDateEpochMillis,
                    "dueTimeHour" to task.dueTimeHour,
                    "dueTimeMinute" to task.dueTimeMinute,
                    "priority" to task.priority.name,
                    "status" to task.status.name,
                    "reminderOption" to task.reminderOption.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                cloudTasksRef.document(docId).set(taskData, SetOptions.merge()).await()
                taskDao.markTaskSynced(task.id, docId)
            }

            // 3. Pull down existing courses from Firestore into Room (guarded against deleted courses)
            val cloudCoursesSnapshot = cloudCoursesRef.get().await()
            for (doc in cloudCoursesSnapshot.documents) {
                val code = doc.getString("code") ?: continue
                val normCode = code.trim().uppercase()

                // SNAPSHOT & READ GUARD: Never re-insert deleted courses
                if (FirestoreSyncManager.isCourseDeletionPending(normCode, doc.id) || courseDao.isCourseDeletedByCode(normCode)) {
                    Log.d(TAG, "SyncWorker guard: Purging deleted course $normCode from cloud snapshot")
                    try { doc.reference.delete().await() } catch (_: Exception) {}
                    continue
                }

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
                    // Soft-deleted in local Room, purge cloud copy and never resurrect
                    try { doc.reference.delete().await() } catch (_: Exception) {}
                } else if (!existing.isSynced) {
                    courseDao.markCourseSynced(existing.id, doc.id)
                }
            }

            // 4. Pull down existing tasks from Firestore into Room (guarded against deleted courses/tasks)
            val cloudTasksSnapshot = cloudTasksRef.get().await()
            for (doc in cloudTasksSnapshot.documents) {
                val title = doc.getString("title") ?: continue
                val courseCode = (doc.getString("courseCode") ?: "").trim().uppercase()

                // SNAPSHOT & READ GUARD: If parent course or task is deleted/pending, do NOT resurrect
                if (courseCode.isNotBlank() && (FirestoreSyncManager.isCourseDeletionPending(courseCode) || courseDao.isCourseDeletedByCode(courseCode))) {
                    Log.d(TAG, "SyncWorker guard: Purging task $title belonging to deleted course $courseCode")
                    try { doc.reference.delete().await() } catch (_: Exception) {}
                    continue
                }

                if (FirestoreSyncManager.isTaskDeletionPending(doc.id)) {
                    Log.d(TAG, "SyncWorker guard: Purging deleted task ${doc.id}")
                    try { doc.reference.delete().await() } catch (_: Exception) {}
                    continue
                }

                val course = if (courseCode.isNotBlank()) courseDao.getCourseByCode(courseCode) else null
                val courseId = course?.id ?: continue

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
                    try { doc.reference.delete().await() } catch (_: Exception) {}
                }
            }

            Log.d(TAG, "Sync completed successfully for user ${user.uid}")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync error: ${e.message}", e)
            Result.retry()
        }
    }
}
