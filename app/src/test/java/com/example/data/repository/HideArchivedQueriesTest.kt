package com.example.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.Course
import com.example.data.local.LectureSlot
import com.example.data.local.Note
import com.example.data.local.ReminderOption
import com.example.data.local.TaskItem
import com.example.data.local.isArchived
import com.example.reminder.ClassReminderScheduler
import com.example.reminder.MissedReminders
import com.example.reminder.ReminderScheduler
import com.example.util.buildTimetableIcs
import com.example.widget.TodayOverviewLoader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
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
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HideArchivedQueriesTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repo: AppRepository
    private lateinit var reminderScheduler: ReminderScheduler

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.getDatabase(context)
        runBlocking(Dispatchers.IO) {
            db.clearAllTables()
        }
        reminderScheduler = ReminderScheduler(context)
        repo = AppRepository(
            context = context,
            courseDao = db.courseDao(),
            taskDao = db.taskDao(),
            taskAttachmentDao = db.taskAttachmentDao(),
            lectureSlotDao = db.lectureSlotDao(),
            reminderScheduler = reminderScheduler,
            noteDao = db.noteDao(),
            database = db
        )
    }

    @After
    fun tearDown() {
        runBlocking(Dispatchers.IO) {
            db.clearAllTables()
        }
    }

    @Test
    fun archivedCoursesAndAssociatedDataAreHiddenFromNormalQueriesAndVisibleInIncludingArchived() = runBlocking {
        // 1. Create active course
        val activeCourseId = repo.insertCourse(
            Course(name = "Active Biology", code = "BIO 101", colorHex = "#10B981")
        )
        val activeTaskId = repo.insertTask(
            TaskItem(
                courseId = activeCourseId,
                title = "Active Lab",
                dueDateEpochMillis = System.currentTimeMillis() + 86400000L
            )
        )
        val activeSlotId = repo.insertLectureSlot(
            LectureSlot(
                courseId = activeCourseId,
                dayOfWeek = 1,
                startHour = 9,
                startMinute = 0,
                endHour = 10,
                endMinute = 0,
                classroom = "Lab 1"
            )
        )
        val activeNoteId = db.noteDao().insert(
            Note(title = "Active Note", body = "Active notes body", courseId = activeCourseId)
        )

        // 2. Create archived course
        val archivedCourseId = repo.insertCourse(
            Course(name = "Archived Physics", code = "PHYS 101", colorHex = "#3B82F6")
        )
        val archivedTaskId = repo.insertTask(
            TaskItem(
                courseId = archivedCourseId,
                title = "Archived Problem Set",
                dueDateEpochMillis = System.currentTimeMillis() + 86400000L
            )
        )
        val archivedSlotId = repo.insertLectureSlot(
            LectureSlot(
                courseId = archivedCourseId,
                dayOfWeek = 2,
                startHour = 11,
                startMinute = 0,
                endHour = 12,
                endMinute = 0,
                classroom = "Hall 2"
            )
        )
        val archivedNoteId = db.noteDao().insert(
            Note(title = "Archived Note", body = "Archived notes body", courseId = archivedCourseId)
        )

        // Archive the second course
        repo.archiveCourses(listOf(archivedCourseId), "Fall 2026")

        // 3. Normal queries hide archived course and data
        val activeCourses = db.courseDao().getAllCourses().first()
        assertEquals(1, activeCourses.size)
        assertEquals(activeCourseId, activeCourses[0].id)

        val activeCoursesList = db.courseDao().getAllCoursesList()
        assertEquals(1, activeCoursesList.size)
        assertEquals(activeCourseId, activeCoursesList[0].id)

        assertNull(db.courseDao().getCourseById(archivedCourseId).first())
        assertNull(db.courseDao().getCourseByIdSync(archivedCourseId))
        assertNull(db.courseDao().getCourseByCode("PHYS 101"))
        assertNull(db.courseDao().getCourseByNormalizedCode("PHYS101"))

        val activeTasks = db.taskDao().getAllTasksWithCourse().first()
        assertEquals(1, activeTasks.size)
        assertEquals(activeTaskId, activeTasks[0].task.id)

        val activeTasksSync = db.taskDao().getAllActiveTasksSync()
        assertEquals(1, activeTasksSync.size)
        assertEquals(activeTaskId, activeTasksSync[0].id)

        assertTrue(db.taskDao().getTasksForCourse(archivedCourseId).first().isEmpty())
        assertNull(db.taskDao().getTaskById(archivedTaskId))

        val activeSlots = db.lectureSlotDao().getAllSlotsWithCourse().first()
        assertEquals(1, activeSlots.size)
        assertEquals(activeSlotId, activeSlots[0].slot.id)

        val activeSlotsSync = db.lectureSlotDao().getAllSlotsSync()
        assertEquals(1, activeSlotsSync.size)
        assertEquals(activeSlotId, activeSlotsSync[0].id)

        assertTrue(db.lectureSlotDao().getSlotsForCourse(archivedCourseId).first().isEmpty())
        assertTrue(db.lectureSlotDao().getSlotsForDay(2).first().isEmpty())

        val activeNotes = db.noteDao().observeNotes().first()
        assertEquals(1, activeNotes.size)
        assertEquals(activeNoteId, activeNotes[0].note.id)

        val activeNotesSync = db.noteDao().getActiveNotesSync()
        assertEquals(1, activeNotesSync.size)
        assertEquals(activeNoteId, activeNotesSync[0].id)

        // AppRepository flows also hide archived data
        val repoCourses = repo.allCourses.first()
        assertEquals(1, repoCourses.size)
        assertEquals(activeCourseId, repoCourses[0].id)

        val repoTasks = repo.allTasksWithCourse.first()
        assertEquals(1, repoTasks.size)
        assertEquals(activeTaskId, repoTasks[0].task.id)

        val repoSlots = repo.allLectureSlots.first()
        assertEquals(1, repoSlots.size)
        assertEquals(activeSlotId, repoSlots[0].slot.id)

        val repoCoursesWithCounts = repo.coursesWithTaskCounts.first()
        assertEquals(1, repoCoursesWithCounts.size)
        assertEquals(activeCourseId, repoCoursesWithCounts[0].course.id)

        // 4. IncludingArchived queries show all data
        val allCoursesInc = db.courseDao().getAllCoursesIncludingArchived().first()
        assertEquals(2, allCoursesInc.size)

        val allCoursesListInc = db.courseDao().getAllCoursesListIncludingArchived()
        assertEquals(2, allCoursesListInc.size)

        assertNotNull(db.courseDao().getCourseByIdIncludingArchived(archivedCourseId).first())
        assertNotNull(db.courseDao().getCourseByIdSyncIncludingArchived(archivedCourseId))

        val allTasksInc = db.taskDao().getAllTasksWithCourseIncludingArchived().first()
        assertEquals(2, allTasksInc.size)

        val allTasksSyncInc = db.taskDao().getAllActiveTasksSyncIncludingArchived()
        assertEquals(2, allTasksSyncInc.size)

        val archivedTasksForCourse = db.taskDao().getTasksForCourseIncludingArchived(archivedCourseId).first()
        assertEquals(1, archivedTasksForCourse.size)
        assertEquals(archivedTaskId, archivedTasksForCourse[0].task.id)

        assertNotNull(db.taskDao().getTaskByIdIncludingArchived(archivedTaskId))

        val allSlotsInc = db.lectureSlotDao().getAllSlotsWithCourseIncludingArchived().first()
        assertEquals(2, allSlotsInc.size)

        val allSlotsSyncInc = db.lectureSlotDao().getAllSlotsSyncIncludingArchived()
        assertEquals(2, allSlotsSyncInc.size)

        val allNotesInc = db.noteDao().observeNotesIncludingArchived().first()
        assertEquals(2, allNotesInc.size)

        val allNotesSyncInc = db.noteDao().getActiveNotesSyncIncludingArchived()
        assertEquals(2, allNotesSyncInc.size)
    }

    @Test
    fun noteWithNoCourseStaysVisibleInNormalQueries() = runBlocking {
        val standaloneNoteId = db.noteDao().insert(
            Note(title = "Shopping List", body = "Milk, Eggs, Bread", courseId = null)
        )

        val notes = db.noteDao().observeNotes().first()
        assertEquals(1, notes.size)
        assertEquals(standaloneNoteId, notes[0].note.id)
        assertNull(notes[0].courseCode)
        assertNull(notes[0].courseName)

        val syncNotes = db.noteDao().getActiveNotesSync()
        assertEquals(1, syncNotes.size)
        assertEquals(standaloneNoteId, syncNotes[0].id)
    }

    @Test
    fun remindersOnBootAndClassAlarmsIgnoreArchivedCourses() = runBlocking {
        val activeCourseId = repo.insertCourse(
            Course(name = "Active Calculus", code = "MATH 201", colorHex = "#6366F1")
        )
        val activeTaskId = repo.insertTask(
            TaskItem(
                courseId = activeCourseId,
                title = "Calc Homework",
                dueDateEpochMillis = System.currentTimeMillis() + 86400000L,
                dueTimeHour = 15,
                dueTimeMinute = 0,
                reminderOptions = setOf(ReminderOption.AT_DEADLINE)
            )
        )
        val activeSlotId = repo.insertLectureSlot(
            LectureSlot(
                courseId = activeCourseId,
                dayOfWeek = 3,
                startHour = 10,
                startMinute = 0,
                endHour = 11,
                endMinute = 0,
                classroom = "Hall 1"
            )
        )

        val archivedCourseId = repo.insertCourse(
            Course(name = "Archived Literature", code = "ENG 101", colorHex = "#EC4899")
        )
        val archivedTaskId = repo.insertTask(
            TaskItem(
                courseId = archivedCourseId,
                title = "Lit Essay",
                dueDateEpochMillis = System.currentTimeMillis() + 86400000L,
                dueTimeHour = 15,
                dueTimeMinute = 0,
                reminderOptions = setOf(ReminderOption.AT_DEADLINE)
            )
        )
        val archivedSlotId = repo.insertLectureSlot(
            LectureSlot(
                courseId = archivedCourseId,
                dayOfWeek = 4,
                startHour = 13,
                startMinute = 0,
                endHour = 14,
                endMinute = 0,
                classroom = "Hall 2"
            )
        )

        repo.archiveCourses(listOf(archivedCourseId), "Fall 2026")

        // Re-simulate boot rescheduling
        reminderScheduler.rescheduleAll(db)
        ClassReminderScheduler(context).rescheduleAll(db)

        val prefs = context.getSharedPreferences("unitask_settings", Context.MODE_PRIVATE)
        val scheduledSlotIds = prefs.getStringSet("class_start_scheduled_slot_ids", emptySet()) ?: emptySet()
        assertTrue(scheduledSlotIds.contains(activeSlotId.toString()))
        assertFalse(scheduledSlotIds.contains(archivedSlotId.toString()))

        // Task reminder of active task has reminderEpochMillis set, archived task is null
        val activeTask = db.taskDao().getTaskById(activeTaskId)!!
        assertNotNull(activeTask.reminderEpochMillis)
        assertNull(db.taskDao().getTaskById(archivedTaskId))

        // MissedReminders ignoring archived tasks
        MissedReminders.notifyMissed(context, db)
    }

    @Test
    fun dailySummaryAndIcsExportIgnoreArchivedCourses() = runBlocking {
        val activeCourseId = repo.insertCourse(
            Course(name = "Operating Systems", code = "CS 301", colorHex = "#10B981")
        )
        val activeSlot = LectureSlot(
            courseId = activeCourseId,
            dayOfWeek = 1,
            startHour = 8,
            startMinute = 30,
            endHour = 10,
            endMinute = 0,
            classroom = "CS Lab"
        )
        repo.insertLectureSlot(activeSlot)

        val archivedCourseId = repo.insertCourse(
            Course(name = "Archived History", code = "HIST 101", colorHex = "#EF4444")
        )
        val archivedSlot = LectureSlot(
            courseId = archivedCourseId,
            dayOfWeek = 1,
            startHour = 10,
            startMinute = 30,
            endHour = 12,
            endMinute = 0,
            classroom = "History Hall"
        )
        repo.insertLectureSlot(archivedSlot)

        repo.archiveCourses(listOf(archivedCourseId), "Spring 2026")

        // 1. ICS Export test
        val slotsWithCourse = db.lectureSlotDao().getAllSlotsWithCourseIncludingArchived().first()
        val icsString = buildTimetableIcs(slotsWithCourse)
        assertTrue(icsString.contains("Operating Systems"))
        assertFalse(icsString.contains("Archived History"))

        // 2. Daily summary logic test
        val activeCoursesMap = db.courseDao().getAllCoursesList().associateBy { it.id }
        assertTrue(activeCoursesMap.containsKey(activeCourseId))
        assertFalse(activeCoursesMap.containsKey(archivedCourseId))

        val summaryClasses = db.lectureSlotDao().getAllSlotsSync()
            .filter { it.courseId in activeCoursesMap }
        assertTrue(summaryClasses.any { it.courseId == activeCourseId })
        assertFalse(summaryClasses.any { it.courseId == archivedCourseId })

        // 3. TodayOverview test
        val overview = TodayOverviewLoader.load(context)
        assertTrue(overview.classes.any { it.code == "CS 301" })
        assertFalse(overview.classes.any { it.code == "HIST 101" })
    }
}
