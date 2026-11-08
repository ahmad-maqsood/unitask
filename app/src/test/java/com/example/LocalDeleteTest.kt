package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.Course
import com.example.data.local.CourseDao
import com.example.data.local.LectureSlot
import com.example.data.local.LectureSlotDao
import com.example.data.local.Note
import com.example.data.local.NoteDao
import com.example.data.local.TaskAttachment
import com.example.data.local.TaskAttachmentDao
import com.example.data.local.TaskDao
import com.example.data.local.TaskItem
import com.example.data.repository.AppRepository
import com.example.reminder.ReminderScheduler
import com.example.util.LocalTombstonePurge
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalDeleteTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repo: AppRepository
    private lateinit var courseDao: CourseDao
    private lateinit var taskDao: TaskDao
    private lateinit var lectureSlotDao: LectureSlotDao
    private lateinit var taskAttachmentDao: TaskAttachmentDao
    private lateinit var noteDao: NoteDao

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("local_tombstone_purge", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        courseDao = db.courseDao()
        taskDao = db.taskDao()
        lectureSlotDao = db.lectureSlotDao()
        taskAttachmentDao = db.taskAttachmentDao()
        noteDao = db.noteDao()

        repo = AppRepository(
            context = context,
            courseDao = courseDao,
            taskDao = taskDao,
            taskAttachmentDao = taskAttachmentDao,
            lectureSlotDao = lectureSlotDao,
            reminderScheduler = ReminderScheduler(context),
            noteDao = noteDao,
            database = db
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testDeleteCourseLeavesNoRowOfCourseTasksOrSlots() = runBlocking {
        val course = Course(
            name = "Operating Systems",
            code = "CS 401",
            colorHex = "#4F46E5",
            cloudId = "cloud-course-1"
        )
        val courseId = courseDao.insertCourse(course)
        val savedCourse = course.copy(id = courseId)

        val taskId = taskDao.insertTask(
            TaskItem(
                courseId = courseId,
                title = "Kernel Project",
                dueDateEpochMillis = System.currentTimeMillis() + 86400000L,
                cloudId = "cloud-task-1"
            )
        )

        val slotId = lectureSlotDao.insertSlot(
            LectureSlot(
                courseId = courseId,
                dayOfWeek = 1,
                startHour = 10,
                startMinute = 0,
                endHour = 11,
                endMinute = 30,
                cloudId = "cloud-slot-1"
            )
        )

        repo.deleteCourse(savedCourse)

        assertNull(courseDao.getAnyCourseByIdSync(courseId))
        assertNull(taskDao.getAnyTaskById(taskId))
        assertNull(lectureSlotDao.getSlotById(slotId))
    }

    @Test
    fun testPurgeOnceRemovesOldHiddenRowsKeepsVisibleRowsAndDoesNothingSecondTime() = runBlocking {
        // Active rows
        val activeCourseId = courseDao.insertCourse(Course(name = "Active", code = "ACT101", isDeleted = false))
        val activeTaskId = taskDao.insertTask(TaskItem(courseId = activeCourseId, title = "Active Task", dueDateEpochMillis = 1000L, isDeleted = false))
        val activeSlotId = lectureSlotDao.insertSlot(LectureSlot(courseId = activeCourseId, dayOfWeek = 1, startHour = 9, startMinute = 0, endHour = 10, endMinute = 0, isDeleted = false))

        // Trashed note (isDeleted = true, must remain in trash)
        val trashedNoteId = noteDao.insert(Note(title = "Trashed Note", isDeleted = true, deletedAt = System.currentTimeMillis()))

        // Soft-deleted rows that need purging
        val deletedCourseId = courseDao.insertCourse(Course(name = "Deleted Course", code = "DEL101", isDeleted = true))
        val deletedTaskId = taskDao.insertTask(TaskItem(courseId = activeCourseId, title = "Deleted Task", dueDateEpochMillis = 2000L, isDeleted = true))
        val deletedSlotId = lectureSlotDao.insertSlot(LectureSlot(courseId = activeCourseId, dayOfWeek = 2, startHour = 14, startMinute = 0, endHour = 15, endMinute = 0, isDeleted = true))

        // Create an attachment file and record for the deleted task
        val attFile = File(context.filesDir, "attachments/test_att.txt")
        attFile.parentFile?.mkdirs()
        attFile.writeText("sample attachment data")
        val attId = taskAttachmentDao.insertAttachment(
            TaskAttachment(
                taskId = deletedTaskId,
                fileName = "test_att.txt",
                relativePath = "attachments/test_att.txt",
                fileSize = 22L,
                mimeType = "text/plain"
            )
        )

        // First purge run: removes hidden rows and cleans attachment files
        LocalTombstonePurge.purgeOnce(context, db)

        assertNull(courseDao.getAnyCourseByIdSync(deletedCourseId))
        assertNull(taskDao.getAnyTaskById(deletedTaskId))
        assertNull(lectureSlotDao.getSlotById(deletedSlotId))
        assertNull(taskAttachmentDao.getAttachmentById(attId))
        assertFalse(attFile.exists())

        // Active rows and trashed note must stay intact
        assertNotNull(courseDao.getAnyCourseByIdSync(activeCourseId))
        assertNotNull(taskDao.getAnyTaskById(activeTaskId))
        assertNotNull(lectureSlotDao.getSlotById(activeSlotId))
        assertNotNull(noteDao.getNoteById(trashedNoteId))

        // Insert another soft-deleted course to verify the second call does nothing
        val newDeletedCourseId = courseDao.insertCourse(Course(name = "New Deleted Course", code = "DEL102", isDeleted = true))

        LocalTombstonePurge.purgeOnce(context, db)
        assertNotNull(courseDao.getAnyCourseByIdSync(newDeletedCourseId))
    }

    @Test
    fun testPurgeDeletedBeforePurgesOldTrashedNoteAndKeepsRecentOne() = runBlocking {
        val now = System.currentTimeMillis()
        val oneDayMillis = 24L * 60L * 60L * 1000L
        val thirtyDaysMillis = 30L * oneDayMillis
        val cutoff = now - thirtyDaysMillis

        val oldTrashedNoteId = noteDao.insert(
            Note(
                title = "Old Trashed",
                isDeleted = true,
                deletedAt = cutoff - 1000L,
                cloudId = "cloud-note-old",
                isSynced = false
            )
        )

        val recentTrashedNoteId = noteDao.insert(
            Note(
                title = "Recent Trashed",
                isDeleted = true,
                deletedAt = now - oneDayMillis,
                cloudId = "cloud-note-recent",
                isSynced = false
            )
        )

        val purgedCount = noteDao.purgeDeletedBefore(cutoff)
        assertEquals(1, purgedCount)

        assertNull(noteDao.getNoteById(oldTrashedNoteId))
        assertNotNull(noteDao.getNoteById(recentTrashedNoteId))
    }
}
