package com.example.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.Course
import com.example.data.local.LectureSlot
import com.example.data.local.Note
import com.example.data.local.ReminderOption
import com.example.data.local.TaskItem
import com.example.data.local.TaskStatus
import com.example.data.local.isArchived
import com.example.reminder.ReminderScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SemesterArchiveRepositoryTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var scheduler: ReminderScheduler
    private lateinit var repo: AppRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.getDatabase(context)
        runBlocking {
            db.clearAllTables()
        }
        scheduler = ReminderScheduler(context)
        repo = AppRepository(
            context = context,
            courseDao = db.courseDao(),
            taskDao = db.taskDao(),
            taskAttachmentDao = db.taskAttachmentDao(),
            lectureSlotDao = db.lectureSlotDao(),
            reminderScheduler = scheduler,
            noteDao = db.noteDao(),
            database = db
        )
    }

    @After
    fun tearDown() {
        runBlocking {
            db.clearAllTables()
        }
    }

    @Test
    fun archiveSetsColumnAndCancelsTaskRemindersAndClassAlarms() = runBlocking {
        val courseId = repo.insertCourse(
            Course(name = "Data Structures", code = "CS 202", colorHex = "#4F46E5")
        )

        val taskId = repo.insertTask(
            TaskItem(
                courseId = courseId,
                title = "Lab 1",
                dueDateEpochMillis = System.currentTimeMillis() + 86400000L,
                dueTimeHour = 10,
                dueTimeMinute = 0,
                reminderOptions = setOf(ReminderOption.AT_DEADLINE)
            )
        )

        val slotId = repo.insertLectureSlot(
            LectureSlot(
                courseId = courseId,
                dayOfWeek = 1,
                startHour = 9,
                startMinute = 0,
                endHour = 10,
                endMinute = 30,
                classroom = "Hall A"
            )
        )

        // Before archive: task has reminder, class alarm is scheduled
        val taskBefore = db.taskDao().getAnyTaskById(taskId)!!
        assertNotNull(taskBefore.reminderEpochMillis)

        val prefs = context.getSharedPreferences("unitask_settings", Context.MODE_PRIVATE)
        val scheduledBefore = prefs.getStringSet("class_start_scheduled_slot_ids", emptySet()) ?: emptySet()
        assertTrue(scheduledBefore.contains(slotId.toString()))

        // Archive the course
        repo.archiveCourses(listOf(courseId), "Fall 2026")

        // Column is set and isArchived is true
        val courseAfter = db.courseDao().getCourseByIdSyncIncludingArchived(courseId)!!
        assertEquals("Fall 2026", courseAfter.archivedSemester)
        assertTrue(courseAfter.isArchived)
        assertNull(db.courseDao().getCourseByIdSync(courseId))

        // Task reminder is cancelled
        val taskAfter = db.taskDao().getAnyTaskById(taskId)!!
        assertNull(taskAfter.reminderEpochMillis)

        // Class alarm is cancelled
        val scheduledAfter = prefs.getStringSet("class_start_scheduled_slot_ids", emptySet()) ?: emptySet()
        assertFalse(scheduledAfter.contains(slotId.toString()))
    }

    @Test
    fun restoreReArmsTaskRemindersAndClassAlarms() = runBlocking {
        val courseId = repo.insertCourse(
            Course(name = "Algorithms", code = "CS 301", colorHex = "#4F46E5")
        )

        val taskId = repo.insertTask(
            TaskItem(
                courseId = courseId,
                title = "Homework 1",
                dueDateEpochMillis = System.currentTimeMillis() + 86400000L,
                dueTimeHour = 14,
                dueTimeMinute = 0,
                reminderOptions = setOf(ReminderOption.AT_DEADLINE)
            )
        )

        val slotId = repo.insertLectureSlot(
            LectureSlot(
                courseId = courseId,
                dayOfWeek = 2,
                startHour = 11,
                startMinute = 0,
                endHour = 12,
                endMinute = 30,
                classroom = "Room 205"
            )
        )

        repo.archiveCourses(listOf(courseId), "Fall 2026")
        val taskArchived = db.taskDao().getAnyTaskById(taskId)!!
        assertNull(taskArchived.reminderEpochMillis)

        // Restore the semester
        repo.restoreSemester("Fall 2026")

        // Column is cleared
        val courseRestored = db.courseDao().getCourseByIdSync(courseId)!!
        assertEquals("", courseRestored.archivedSemester)
        assertFalse(courseRestored.isArchived)

        // Task reminder is re-armed
        val taskRestored = db.taskDao().getAnyTaskById(taskId)!!
        assertNotNull(taskRestored.reminderEpochMillis)

        // Class alarm is re-armed
        val prefs = context.getSharedPreferences("unitask_settings", Context.MODE_PRIVATE)
        val scheduledRestored = prefs.getStringSet("class_start_scheduled_slot_ids", emptySet()) ?: emptySet()
        assertTrue(scheduledRestored.contains(slotId.toString()))
    }

    @Test
    fun mytasksCannotBeArchived() = runBlocking {
        val myTasksId = repo.insertCourse(
            Course(name = "My tasks", code = "MYTASKS", colorHex = "#000000")
        )

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repo.archiveCourses(listOf(myTasksId), "Fall 2026")
            }
        }
    }

    @Test
    fun blankAndOverLongNamesAreRejected() = runBlocking {
        val courseId = repo.insertCourse(
            Course(name = "Physics", code = "PHYS 101", colorHex = "#4F46E5")
        )

        // Blank name
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repo.archiveCourses(listOf(courseId), "")
            }
        }

        // Whitespace only
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repo.archiveCourses(listOf(courseId), "   ")
            }
        }

        // Over 40 characters
        val longName = "A".repeat(41)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repo.archiveCourses(listOf(courseId), longName)
            }
        }
    }

    @Test
    fun sameNameInDifferentCaseJoinsOneSemesterAndPreservesOriginalSpelling() = runBlocking {
        val course1Id = repo.insertCourse(
            Course(name = "Chemistry 1", code = "CHEM 101", colorHex = "#4F46E5")
        )
        val course2Id = repo.insertCourse(
            Course(name = "Chemistry 2", code = "CHEM 102", colorHex = "#4F46E5")
        )

        repo.archiveCourses(listOf(course1Id), "Fall 2026")
        repo.archiveCourses(listOf(course2Id), "fall 2026")

        val c1 = db.courseDao().getCourseByIdSyncIncludingArchived(course1Id)!!
        val c2 = db.courseDao().getCourseByIdSyncIncludingArchived(course2Id)!!
        assertEquals("Fall 2026", c1.archivedSemester)
        assertEquals("Fall 2026", c2.archivedSemester)

        val summaries = repo.observeArchivedSemesters().first()
        assertEquals(1, summaries.size)
        assertEquals("Fall 2026", summaries[0].name)
        assertEquals(2, summaries[0].courseCount)
        assertEquals(listOf("CHEM 101", "CHEM 102"), summaries[0].courseCodes)
    }

    @Test
    fun insertCourseThrowsForArchivedCode() = runBlocking {
        val courseId = repo.insertCourse(
            Course(name = "Biology", code = "BIO 100", colorHex = "#4F46E5")
        )
        repo.archiveCourses(listOf(courseId), "Spring 2026")

        // Exact match
        val ex1 = assertThrows(ArchivedCourseCodeException::class.java) {
            runBlocking {
                repo.insertCourse(Course(name = "New Biology", code = "BIO 100", colorHex = "#10B981"))
            }
        }
        assertEquals("Spring 2026", ex1.semesterName)

        // Case and space normalized match
        val ex2 = assertThrows(ArchivedCourseCodeException::class.java) {
            runBlocking {
                repo.insertCourse(Course(name = "New Biology 2", code = "bio100", colorHex = "#10B981"))
            }
        }
        assertEquals("Spring 2026", ex2.semesterName)
    }

    @Test
    fun deleteSemesterForeverDeletesCoursesAndAssociatedData() = runBlocking {
        val courseId = repo.insertCourse(
            Course(name = "Sociology", code = "SOC 101", colorHex = "#4F46E5")
        )
        val taskId = repo.insertTask(
            TaskItem(
                courseId = courseId,
                title = "Term Paper",
                dueDateEpochMillis = System.currentTimeMillis() + 86400000L
            )
        )
        val slotId = repo.insertLectureSlot(
            LectureSlot(
                courseId = courseId,
                dayOfWeek = 3,
                startHour = 10,
                startMinute = 0,
                endHour = 11,
                endMinute = 0,
                classroom = "Room 10"
            )
        )
        val noteId = db.noteDao().insert(
            Note(title = "Sociology Notes", body = "Reading list", courseId = courseId)
        )

        repo.archiveCourses(listOf(courseId), "Fall 2026")
        assertEquals(1, repo.observeArchivedSemesters().first().size)

        repo.deleteSemesterForever("Fall 2026")

        // Course is deleted
        assertNull(db.courseDao().getCourseByIdSync(courseId))
        // Task is deleted
        assertNull(db.taskDao().getAnyTaskById(taskId))
        // Slot is deleted
        assertNull(db.lectureSlotDao().getSlotById(slotId))
        // Note is detached from course
        val note = db.noteDao().getNoteById(noteId)
        assertNotNull(note)
        assertNull(note!!.courseId)

        // No more archived semesters
        assertTrue(repo.observeArchivedSemesters().first().isEmpty())
    }

    @Test
    fun undoArchiveRestoresOnlyGivenCourses() = runBlocking {
        val course1Id = repo.insertCourse(
            Course(name = "Operating Systems", code = "CS 301", colorHex = "#4F46E5")
        )
        val course2Id = repo.insertCourse(
            Course(name = "Networks", code = "CS 302", colorHex = "#10B981")
        )
        val course3Id = repo.insertCourse(
            Course(name = "Databases", code = "CS 303", colorHex = "#F59E0B")
        )

        val task1Id = repo.insertTask(
            TaskItem(
                courseId = course1Id,
                title = "Lab OS",
                dueDateEpochMillis = System.currentTimeMillis() + 86400000L,
                dueTimeHour = 10,
                dueTimeMinute = 0,
                reminderOptions = setOf(ReminderOption.AT_DEADLINE)
            )
        )
        val task2Id = repo.insertTask(
            TaskItem(
                courseId = course2Id,
                title = "Network Quiz",
                dueDateEpochMillis = System.currentTimeMillis() + 86400000L,
                dueTimeHour = 14,
                dueTimeMinute = 0,
                reminderOptions = setOf(ReminderOption.AT_DEADLINE)
            )
        )
        val task3Id = repo.insertTask(
            TaskItem(
                courseId = course3Id,
                title = "SQL Assignment",
                dueDateEpochMillis = System.currentTimeMillis() + 86400000L,
                dueTimeHour = 16,
                dueTimeMinute = 0,
                reminderOptions = setOf(ReminderOption.AT_DEADLINE)
            )
        )

        // Archive all 3 courses into Fall 2026
        repo.archiveCourses(listOf(course1Id, course2Id, course3Id), "Fall 2026")

        // Confirm all 3 are archived
        assertNull(db.courseDao().getCourseByIdSync(course1Id))
        assertNull(db.courseDao().getCourseByIdSync(course2Id))
        assertNull(db.courseDao().getCourseByIdSync(course3Id))
        assertNull(db.taskDao().getAnyTaskById(task1Id)!!.reminderEpochMillis)
        assertNull(db.taskDao().getAnyTaskById(task2Id)!!.reminderEpochMillis)
        assertNull(db.taskDao().getAnyTaskById(task3Id)!!.reminderEpochMillis)

        val summariesBeforeUndo = repo.observeArchivedSemesters().first()
        assertEquals(1, summariesBeforeUndo.size)
        assertEquals(3, summariesBeforeUndo[0].courseCount)

        // Undo archive only for course1 and course2
        repo.undoArchive(listOf(course1Id, course2Id))

        // course1 and course2 are active again
        val c1 = db.courseDao().getCourseByIdSync(course1Id)
        val c2 = db.courseDao().getCourseByIdSync(course2Id)
        assertNotNull(c1)
        assertNotNull(c2)
        assertEquals("", c1!!.archivedSemester)
        assertEquals("", c2!!.archivedSemester)

        // course3 is still archived
        assertNull(db.courseDao().getCourseByIdSync(course3Id))
        val c3 = db.courseDao().getCourseByIdSyncIncludingArchived(course3Id)
        assertNotNull(c3)
        assertEquals("Fall 2026", c3!!.archivedSemester)

        // Reminders re-armed only for course1 and course2 tasks
        assertNotNull(db.taskDao().getAnyTaskById(task1Id)!!.reminderEpochMillis)
        assertNotNull(db.taskDao().getAnyTaskById(task2Id)!!.reminderEpochMillis)
        assertNull(db.taskDao().getAnyTaskById(task3Id)!!.reminderEpochMillis)

        // Archived semester still exists, but with only course3 remaining
        val summariesAfterUndo = repo.observeArchivedSemesters().first()
        assertEquals(1, summariesAfterUndo.size)
        assertEquals(1, summariesAfterUndo[0].courseCount)
        assertEquals(listOf("CS 303"), summariesAfterUndo[0].courseCodes)
    }
}
