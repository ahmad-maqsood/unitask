package com.example.util

import android.content.Context
import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One-time cleanup of old hidden rows that were soft-deleted while Supabase sync was active.
 * Hard deletes courses, tasks, and lecture slots marked isDeleted = 1, along with any
 * attachments and attachment files for those tasks.
 * Notes are deliberately untouched because their isDeleted flag represents the user's trash.
 */
object LocalTombstonePurge {

    private const val PREFS = "local_tombstone_purge"
    private const val KEY_PURGED = "tombstones_purged_v1"

    suspend fun purgeOnce(context: Context, db: AppDatabase) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_PURGED, false)) return@withContext

        val taskDao = db.taskDao()
        val courseDao = db.courseDao()
        val slotDao = db.lectureSlotDao()
        val attachmentDao = db.taskAttachmentDao()

        val deletedCourses = courseDao.getDeletedCourses()
        val deletedCourseIds = deletedCourses.map { it.id }.toSet()

        val deletedTasks = taskDao.getDeletedTasks().filter { it.deletedAt <= 0L }
        val courseTaskIds = deletedCourseIds.flatMap { courseId ->
            taskDao.getAnyTasksForCourseSync(courseId)
                .filter { it.deletedAt <= 0L }
                .map { it.id }
        }
        val allTaskIdsToDelete = (deletedTasks.map { it.id } + courseTaskIds).distinct()

        for (taskId in allTaskIdsToDelete) {
            val attachments = attachmentDao.getAttachmentsForTaskSync(taskId)
            for (att in attachments) {
                AttachmentManager.deleteAttachmentFile(app, att.relativePath)
            }
        }

        db.withTransaction {
            for (slot in slotDao.getDeletedSlots()) {
                slotDao.deleteSlot(slot)
            }
            for (courseId in deletedCourseIds) {
                slotDao.deleteSlotsForCourse(courseId)
            }

            for (taskId in allTaskIdsToDelete) {
                attachmentDao.deleteAttachmentsForTask(taskId)
                taskDao.deleteTaskById(taskId)
            }

            for (course in deletedCourses) {
                courseDao.deleteCourseById(course.id)
            }
        }

        prefs.edit().putBoolean(KEY_PURGED, true).apply()
    }
}
