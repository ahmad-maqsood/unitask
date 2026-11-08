package com.example.ui

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.Course
import com.example.data.local.Note
import com.example.data.local.TaskItem
import com.example.data.repository.AppRepository
import com.example.data.repository.NoteRepository
import com.example.reminder.ReminderScheduler
import com.example.ui.screens.RecentlyDeletedViewModel
import com.example.ui.screens.formatTabTitle
import com.example.ui.screens.formatTrashDaysLeft
import com.example.data.local.LectureSlot
import com.example.ui.screens.dayOfWeekName
import com.example.ui.screens.formatTrashClassTime
import com.example.ui.screens.formatTrashTaskDueDate
import kotlinx.coroutines.flow.first
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
class RecentlyDeletedLogicTest {

    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private lateinit var appRepo: AppRepository
    private lateinit var noteRepo: NoteRepository
    private lateinit var viewModel: RecentlyDeletedViewModel

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        appRepo = AppRepository(
            context = app,
            courseDao = db.courseDao(),
            taskDao = db.taskDao(),
            taskAttachmentDao = db.taskAttachmentDao(),
            lectureSlotDao = db.lectureSlotDao(),
            reminderScheduler = ReminderScheduler(app),
            noteDao = db.noteDao(),
            database = db
        )
        noteRepo = NoteRepository(db.noteDao())
        viewModel = RecentlyDeletedViewModel(app, appRepo, noteRepo)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testDayOfWeekName() {
        assertEquals("Monday", dayOfWeekName(1))
        assertEquals("Tuesday", dayOfWeekName(2))
        assertEquals("Wednesday", dayOfWeekName(3))
        assertEquals("Thursday", dayOfWeekName(4))
        assertEquals("Friday", dayOfWeekName(5))
        assertEquals("Saturday", dayOfWeekName(6))
        assertEquals("Sunday", dayOfWeekName(7))
        assertEquals("", dayOfWeekName(0))
        assertEquals("", dayOfWeekName(8))
    }

    @Test
    fun testFormatTrashClassTime() {
        assertEquals("Thursday, 10:00 to 11:30", formatTrashClassTime(4, 10, 0, 11, 30))
        assertEquals("Monday, 09:30 to 11:00", formatTrashClassTime(1, 9, 30, 11, 0))
        assertEquals("Friday, 14:05 to 15:50", formatTrashClassTime(5, 14, 5, 15, 50))
    }

    @Test
    fun testFormatTrashDaysLeft() {
        assertEquals("1 day left", formatTrashDaysLeft(1))
        assertEquals("30 days left", formatTrashDaysLeft(30))
        assertEquals("15 days left", formatTrashDaysLeft(15))
        assertEquals("0 days left", formatTrashDaysLeft(0))
    }

    @Test
    fun testFormatTabTitle() {
        assertEquals("Tasks (0)", formatTabTitle("Tasks", 0))
        assertEquals("Tasks (12)", formatTabTitle("Tasks", 12))
        assertEquals("Notes (3)", formatTabTitle("Notes", 3))
    }

    @Test
    fun testFormatTrashTaskDueDate() {
        assertEquals("", formatTrashTaskDueDate(0L, 10, 0))
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 4, 14, 30, 0)
        }
        val formatted = formatTrashTaskDueDate(cal.timeInMillis, 14, 30)
        assertTrue(formatted.startsWith("Due "))
        assertTrue(formatted.contains("Nov 4"))
    }

    @Test
    fun testViewModelTaskActions() = runBlocking {
        val courseId = db.courseDao().insertCourse(Course(name = "Math", code = "MATH 101", colorHex = "#123456"))
        val now = System.currentTimeMillis()
        val taskId = db.taskDao().insertTask(
            TaskItem(
                courseId = courseId,
                title = "Trashed Task",
                dueDateEpochMillis = now + 100000L,
                isDeleted = true,
                deletedAt = now
            )
        )

        // Verify task appears in deletedTasks
        val deleted = viewModel.deletedTasks.first { it.isNotEmpty() }
        assertEquals(1, deleted.size)
        assertEquals(taskId, deleted.first().task.id)

        // Restore task
        viewModel.restoreTask(taskId)
        val afterRestore = viewModel.deletedTasks.first { it.isEmpty() }
        assertTrue(afterRestore.isEmpty())
        val restored = db.taskDao().getTaskById(taskId)
        assertNotNull(restored)
        assertFalse(restored!!.isDeleted)

        // Soft delete again and test deleteTaskForever
        db.taskDao().softDeleteTask(taskId, now)
        val backInTrash = viewModel.deletedTasks.first { it.isNotEmpty() }
        assertEquals(1, backInTrash.size)

        viewModel.deleteTaskForever(taskId)
        val afterDeleteForever = viewModel.deletedTasks.first { it.isEmpty() }
        assertTrue(afterDeleteForever.isEmpty())
        assertNull(db.taskDao().getAnyTaskById(taskId))
    }

    @Test
    fun testViewModelEmptyTaskTrash() = runBlocking {
        val courseId = db.courseDao().insertCourse(Course(name = "Physics", code = "PHYS 101", colorHex = "#123456"))
        val now = System.currentTimeMillis()
        val id1 = db.taskDao().insertTask(TaskItem(courseId = courseId, title = "T1", dueDateEpochMillis = now, isDeleted = true, deletedAt = now))
        val id2 = db.taskDao().insertTask(TaskItem(courseId = courseId, title = "T2", dueDateEpochMillis = now, isDeleted = true, deletedAt = now))

        assertEquals(2, viewModel.deletedTasks.first { it.size == 2 }.size)

        viewModel.emptyTaskTrash()
        assertTrue(viewModel.deletedTasks.first { it.isEmpty() }.isEmpty())
        assertNull(db.taskDao().getAnyTaskById(id1))
        assertNull(db.taskDao().getAnyTaskById(id2))
    }

    @Test
    fun testViewModelNoteActions() = runBlocking {
        val now = System.currentTimeMillis()
        val noteId = db.noteDao().insert(
            Note(
                title = "Trashed Note",
                body = "Note body",
                isDeleted = true,
                deletedAt = now
            )
        )

        val deleted = viewModel.deletedNotes.first { it.isNotEmpty() }
        assertEquals(1, deleted.size)
        assertEquals(noteId, deleted.first().note.id)

        // Restore note
        viewModel.restoreNote(noteId)
        assertTrue(viewModel.deletedNotes.first { it.isEmpty() }.isEmpty())
        val restored = db.noteDao().getNoteById(noteId)
        assertNotNull(restored)
        assertFalse(restored!!.isDeleted)

        // Soft delete again and test deleteNoteForever
        db.noteDao().softDelete(noteId, now)
        assertEquals(1, viewModel.deletedNotes.first { it.isNotEmpty() }.size)

        viewModel.deleteNoteForever(noteId)
        assertTrue(viewModel.deletedNotes.first { it.isEmpty() }.isEmpty())
        assertNull(db.noteDao().getNoteById(noteId))
    }

    @Test
    fun testViewModelEmptyNoteTrash() = runBlocking {
        val now = System.currentTimeMillis()
        val n1 = db.noteDao().insert(Note(title = "N1", body = "B1", isDeleted = true, deletedAt = now))
        val n2 = db.noteDao().insert(Note(title = "N2", body = "B2", isDeleted = true, deletedAt = now))

        assertEquals(2, viewModel.deletedNotes.first { it.size == 2 }.size)

        viewModel.emptyNoteTrash()
        assertTrue(viewModel.deletedNotes.first { it.isEmpty() }.isEmpty())
        assertNull(db.noteDao().getNoteById(n1))
        assertNull(db.noteDao().getNoteById(n2))
    }

    @Test
    fun testViewModelClassActions() = runBlocking {
        val courseId = db.courseDao().insertCourse(Course(name = "Biology", code = "BIO 101", colorHex = "#123456"))
        val now = System.currentTimeMillis()
        val slotId = db.lectureSlotDao().insertSlot(
            LectureSlot(
                courseId = courseId,
                dayOfWeek = 4,
                startHour = 10,
                startMinute = 0,
                endHour = 11,
                endMinute = 30,
                classroom = "Lab 3",
                isDeleted = true,
                deletedAt = now
            )
        )

        // Verify slot appears in deletedClasses
        val deleted = viewModel.deletedClasses.first { it.isNotEmpty() }
        assertEquals(1, deleted.size)
        assertEquals(slotId, deleted.first().slot.id)

        // Restore class
        viewModel.restoreClass(slotId)
        val afterRestore = viewModel.deletedClasses.first { it.isEmpty() }
        assertTrue(afterRestore.isEmpty())
        val restored = db.lectureSlotDao().getSlotById(slotId)
        assertNotNull(restored)
        assertFalse(restored!!.isDeleted)
        assertEquals(0L, restored.deletedAt)

        // Soft delete again and test deleteClassForever
        db.lectureSlotDao().softDeleteSlot(slotId, now)
        val backInTrash = viewModel.deletedClasses.first { it.isNotEmpty() }
        assertEquals(1, backInTrash.size)

        viewModel.deleteClassForever(slotId)
        val afterDeleteForever = viewModel.deletedClasses.first { it.isEmpty() }
        assertTrue(afterDeleteForever.isEmpty())
        assertNull(db.lectureSlotDao().getSlotById(slotId))
    }

    @Test
    fun testViewModelEmptyClassTrash() = runBlocking {
        val courseId = db.courseDao().insertCourse(Course(name = "Chemistry", code = "CHEM 101", colorHex = "#123456"))
        val now = System.currentTimeMillis()
        val s1 = db.lectureSlotDao().insertSlot(
            LectureSlot(courseId = courseId, dayOfWeek = 1, startHour = 9, startMinute = 0, endHour = 10, endMinute = 0, isDeleted = true, deletedAt = now)
        )
        val s2 = db.lectureSlotDao().insertSlot(
            LectureSlot(courseId = courseId, dayOfWeek = 3, startHour = 14, startMinute = 0, endHour = 15, endMinute = 0, isDeleted = true, deletedAt = now)
        )

        assertEquals(2, viewModel.deletedClasses.first { it.size == 2 }.size)

        viewModel.emptyClassTrash()
        assertTrue(viewModel.deletedClasses.first { it.isEmpty() }.isEmpty())
        assertNull(db.lectureSlotDao().getSlotById(s1))
        assertNull(db.lectureSlotDao().getSlotById(s2))
    }
}
