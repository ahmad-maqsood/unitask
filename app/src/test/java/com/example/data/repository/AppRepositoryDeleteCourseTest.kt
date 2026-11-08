package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.Course
import com.example.data.local.LectureSlot
import com.example.data.local.Note
import com.example.data.local.TaskItem
import com.example.reminder.ReminderScheduler
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppRepositoryDeleteCourseTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: AppRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = AppRepository(
            context = context,
            courseDao = db.courseDao(),
            taskDao = db.taskDao(),
            taskAttachmentDao = db.taskAttachmentDao(),
            lectureSlotDao = db.lectureSlotDao(),
            reminderScheduler = ReminderScheduler(context),
            noteDao = db.noteDao(),
            database = db
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testDeleteCourseRemovesCourseTasksAndSlotsAndDetachesNotes() = runBlocking {
        val course = Course(
            name = "Software Engineering",
            code = "CS 301",
            colorHex = "#4F46E5",
            cloudId = "course-cloud-1"
        )
        val courseId = db.courseDao().insertCourse(course)
        val savedCourse = course.copy(id = courseId)

        // Insert a task for this course
        db.taskDao().insertTask(
            TaskItem(
                courseId = courseId,
                title = "Assignment 1",
                dueDateEpochMillis = System.currentTimeMillis() + 86400000L,
                cloudId = "task-cloud-1"
            )
        )

        // Insert a timetable lecture slot for this course
        db.lectureSlotDao().insertSlot(
            LectureSlot(
                courseId = courseId,
                dayOfWeek = 1,
                startHour = 9,
                startMinute = 0,
                endHour = 10,
                endMinute = 30,
                classroom = "Room 101",
                cloudId = "slot-cloud-1"
            )
        )

        // Insert a note tagged with this course
        val noteId = db.noteDao().insert(
            Note(
                title = "Lecture Notes",
                body = "Course introduction and syllabus",
                courseId = courseId
            )
        )

        // Verify initial state
        val initialNote = db.noteDao().getNoteById(noteId)
        assertNotNull(initialNote)
        assertEquals(courseId, initialNote?.courseId)

        // Execute deleteCourse
        repo.deleteCourse(savedCourse)

        // Course, tasks and slots must be removed
        val updatedCourse = db.courseDao().getAnyCourseByIdSync(courseId)
        assertNull(updatedCourse)

        val tasks = db.taskDao().getAnyTasksForCourseSync(courseId)
        assertTrue(tasks.isEmpty())

        val slots = db.lectureSlotDao().getSlotsForCourseSync(courseId)
        assertTrue(slots.isEmpty())

        // Note must still exist and not be deleted, but courseId must be detached (null)
        val updatedNote = db.noteDao().getNoteById(noteId)
        assertNotNull(updatedNote)
        assertFalse(updatedNote?.isDeleted ?: true)
        assertNull(updatedNote?.courseId)
    }
}
