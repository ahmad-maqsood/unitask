package com.example.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.Course
import com.example.data.local.LectureSlot
import com.example.data.local.LectureSlotWithCourse
import com.example.data.local.Note
import com.example.data.local.TaskItem
import com.example.data.local.TaskWithCourse
import com.example.data.local.isArchived
import com.example.ui.screens.RecentlyDeletedViewModel
import com.example.util.drive.DriveBackupManager
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
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
class SemesterArchiveBackupTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.getDatabase(context)
        runBlocking(Dispatchers.IO) { db.clearAllTables() }
    }

    @After
    fun tearDown() {
        runBlocking(Dispatchers.IO) { db.clearAllTables() }
    }

    @Test
    fun backupRoundTripKeepsArchiveSemesterNamesTasksSlotsAndNotes() = runBlocking {
        val courseDao = db.courseDao()
        val taskDao = db.taskDao()
        val slotDao = db.lectureSlotDao()
        val noteDao = db.noteDao()

        // 1. Create one active course and one archived course
        val activeCourseId = courseDao.insertCourse(
            Course(name = "Algorithms", code = "CS 301", colorHex = "#10B981", archivedSemester = "")
        )
        val archivedCourseId = courseDao.insertCourse(
            Course(name = "Data Structures", code = "CS 201", colorHex = "#4F46E5", archivedSemester = "Fall 2026")
        )

        // 2. Create tasks, slots and notes for both
        taskDao.insertTask(
            TaskItem(courseId = activeCourseId, title = "Active Task", dueDateEpochMillis = 1772946000000L)
        )
        taskDao.insertTask(
            TaskItem(courseId = archivedCourseId, title = "Archived Task", dueDateEpochMillis = 1772946000000L)
        )

        slotDao.insertSlot(
            LectureSlot(courseId = activeCourseId, dayOfWeek = 1, startHour = 9, startMinute = 0, endHour = 10, endMinute = 0, classroom = "Room A")
        )
        slotDao.insertSlot(
            LectureSlot(courseId = archivedCourseId, dayOfWeek = 2, startHour = 11, startMinute = 0, endHour = 12, endMinute = 0, classroom = "Room B")
        )

        noteDao.insert(Note(title = "Active Note", body = "Active note body", courseId = activeCourseId))
        noteDao.insert(Note(title = "Archived Note", body = "Archived note body", courseId = archivedCourseId))
        noteDao.insert(Note(title = "General Note", body = "No course attached", courseId = null))

        // 3. Build backup
        val built = LocalBackupManager.buildData(context, inlineImages = false)

        // Verify summary counts: active courses only
        assertEquals(1, built.counts.courses)
        assertEquals(2, built.counts.slots)
        assertEquals(2, built.counts.tasks)
        assertEquals(3, built.counts.notes)

        // Verify JSON includes archivedSemester on archived course and omits on active course
        val coursesArray = built.data.getJSONArray("courses")
        assertEquals(2, coursesArray.length())
        var foundArchivedCourse = false
        var foundActiveCourse = false
        for (i in 0 until coursesArray.length()) {
            val c = coursesArray.getJSONObject(i)
            if (c.getString("code") == "CS 201") {
                assertEquals("Fall 2026", c.optString("archivedSemester"))
                foundArchivedCourse = true
            } else if (c.getString("code") == "CS 301") {
                assertFalse("Blank archivedSemester must be omitted", c.has("archivedSemester"))
                foundActiveCourse = true
            }
        }
        assertTrue(foundArchivedCourse)
        assertTrue(foundActiveCourse)

        // 4. Wipe database to verify full restore
        withContext(Dispatchers.IO) { db.clearAllTables() }
        assertEquals(0, courseDao.getAllCoursesListIncludingArchived().size)

        // 5. Apply backup
        val counts = LocalBackupManager.applyBackupData(context, built.data, source = null)
        assertEquals(2, counts.courses)

        // 6. Verify restored data
        val restoredCourses = courseDao.getAllCoursesListIncludingArchived()
        assertEquals(2, restoredCourses.size)
        val restoredArchived = restoredCourses.first { it.code == "CS 201" }
        assertEquals("Fall 2026", restoredArchived.archivedSemester)
        assertTrue(restoredArchived.isArchived)

        val restoredActive = restoredCourses.first { it.code == "CS 301" }
        assertEquals("", restoredActive.archivedSemester)
        assertFalse(restoredActive.isArchived)

        // Normal query only sees the active course
        val activeOnly = courseDao.getAllCoursesList()
        assertEquals(1, activeOnly.size)
        assertEquals("CS 301", activeOnly[0].code)

        // Tasks round-trip correctly
        val restoredTasks = taskDao.getAllActiveTasksSyncIncludingArchived()
        assertEquals(2, restoredTasks.size)
        assertTrue(restoredTasks.any { it.title == "Archived Task" && it.courseId == restoredArchived.id })
        assertTrue(restoredTasks.any { it.title == "Active Task" && it.courseId == restoredActive.id })

        // Slots round-trip correctly
        val restoredSlots = slotDao.getAllSlotsSyncIncludingArchived()
        assertEquals(2, restoredSlots.size)
        assertTrue(restoredSlots.any { it.classroom == "Room B" && it.courseId == restoredArchived.id })
        assertTrue(restoredSlots.any { it.classroom == "Room A" && it.courseId == restoredActive.id })

        // Notes round-trip correctly
        val restoredNotes = noteDao.getActiveNotesSyncIncludingArchived()
        assertEquals(3, restoredNotes.size)
        assertTrue(restoredNotes.any { it.title == "Archived Note" && it.courseId == restoredArchived.id })
        assertTrue(restoredNotes.any { it.title == "Active Note" && it.courseId == restoredActive.id })
        assertTrue(restoredNotes.any { it.title == "General Note" && it.courseId == null })
    }

    @Test
    fun restoreOfOldBackupWithoutArchivedSemesterFieldGivesActiveCourses() = runBlocking {
        val oldBackupJson = JSONObject().apply {
            put("courses", JSONArray().apply {
                put(JSONObject().apply {
                    put("code", "MATH 101")
                    put("name", "Calculus")
                    put("instructor", "Prof. Smith")
                    put("colorHex", "#4F46E5")
                    put("location", "Room 101")
                })
            })
        }

        LocalBackupManager.applyBackupData(context, oldBackupJson, source = null)

        val course = db.courseDao().getCourseByCode("MATH 101")
        assertNotNull(course)
        assertEquals("", course!!.archivedSemester)
        assertFalse(course.isArchived)
    }

    @Test
    fun emptyCheckTreatsDatabaseWithOnlyArchivedCoursesAsNotEmpty() = runBlocking {
        // Initially empty
        assertTrue(DriveBackupManager.isDatabaseEmpty(context))

        // Insert archived course
        db.courseDao().insertCourse(
            Course(name = "Physics", code = "PHYS 101", archivedSemester = "Spring 2025")
        )

        // Must NOT be treated as empty
        assertFalse(DriveBackupManager.isDatabaseEmpty(context))
    }

    @Test
    fun disabledRestoreTextForTrashedTaskAndClassOfArchivedCourse() {
        val vm = RecentlyDeletedViewModel(ApplicationProvider.getApplicationContext())

        val archivedCourse = Course(
            id = 10L,
            name = "Literature",
            code = "LIT 101",
            archivedSemester = "Spring 2026"
        )
        val activeCourse = Course(
            id = 20L,
            name = "Chemistry",
            code = "CHEM 101",
            archivedSemester = ""
        )

        val trashedTask = TaskItem(
            id = 1L,
            courseId = 10L,
            title = "Essay 1",
            dueDateEpochMillis = 0L,
            isDeleted = true,
            deletedAt = 1000L
        )
        val trashedSlot = LectureSlot(
            id = 2L,
            courseId = 10L,
            dayOfWeek = 1,
            startHour = 9,
            startMinute = 0,
            endHour = 10,
            endMinute = 0,
            isDeleted = true,
            deletedAt = 1000L
        )

        // Trashed task with archived course has disabled-restore message
        val taskReason = vm.restoreDisabledReason(TaskWithCourse(task = trashedTask, course = archivedCourse))
        assertEquals("Its course is archived in Spring 2026. Restore the semester first.", taskReason)

        // Trashed class with archived course has disabled-restore message
        val classReason = vm.restoreDisabledReason(LectureSlotWithCourse(slot = trashedSlot, course = archivedCourse))
        assertEquals("Its course is archived in Spring 2026. Restore the semester first.", classReason)

        // Active course items have null reason (enabled)
        val activeTaskReason = vm.restoreDisabledReason(TaskWithCourse(task = trashedTask.copy(courseId = 20L), course = activeCourse))
        assertNull(activeTaskReason)

        val activeClassReason = vm.restoreDisabledReason(LectureSlotWithCourse(slot = trashedSlot.copy(courseId = 20L), course = activeCourse))
        assertNull(activeClassReason)
    }
}
