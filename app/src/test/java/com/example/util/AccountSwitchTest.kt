package com.example.util

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.Course
import com.example.data.local.LectureSlot
import com.example.data.local.Note
import com.example.data.local.TaskItem
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AccountSwitchTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun shouldResetGroupState_returnsFalseWhenEitherUidIsNullOrBlank() {
        assertFalse(shouldResetGroupState(null, null))
        assertFalse(shouldResetGroupState(null, "user2"))
        assertFalse(shouldResetGroupState("user1", null))
        assertFalse(shouldResetGroupState("", "user2"))
        assertFalse(shouldResetGroupState("user1", ""))
        assertFalse(shouldResetGroupState("   ", "user2"))
        assertFalse(shouldResetGroupState("user1", "   "))
    }

    @Test
    fun shouldResetGroupState_returnsFalseWhenUidsAreEqual() {
        assertFalse(shouldResetGroupState("user1", "user1"))
    }

    @Test
    fun shouldResetGroupState_returnsTrueWhenBothAreNonBlankAndDifferent() {
        assertTrue(shouldResetGroupState("user1", "user2"))
        assertTrue(shouldResetGroupState("user-a", "user-b"))
    }

    @Test
    fun accountSwitch_doesNotDeletePersonalDataAndClearsGroupCache() = runBlocking {
        val courseId = db.courseDao().insertCourse(
            Course(name = "Software Engineering", code = "CS301", colorHex = "#4F46E5")
        )
        val taskId = db.taskDao().insertTask(
            TaskItem(courseId = courseId, title = "Design Doc", dueDateEpochMillis = 1700000000000L)
        )
        val slotId = db.lectureSlotDao().insertSlot(
            LectureSlot(courseId = courseId, dayOfWeek = 1, startHour = 9, startMinute = 0, endHour = 10, endMinute = 0)
        )
        val noteId = db.noteDao().insert(
            Note(title = "Sprint Notes", body = "Sprint goals and review")
        )

        // Seed group cache
        context.getSharedPreferences("group_cache", Context.MODE_PRIVATE)
            .edit()
            .putString("cached_group_json", "{\"id\":\"group-1\"}")
            .commit()

        assertFalse(context.getSharedPreferences("group_cache", Context.MODE_PRIVATE).all.isEmpty())

        // Run the account switch reset
        resetGroupStateOnAccountSwitch(context)

        // Assert all personal data is still intact
        assertNotNull(db.courseDao().getAnyCourseByIdSync(courseId))
        assertEquals("CS301", db.courseDao().getAnyCourseByIdSync(courseId)?.code)

        assertNotNull(db.taskDao().getAnyTaskById(taskId))
        assertEquals("Design Doc", db.taskDao().getAnyTaskById(taskId)?.title)

        assertNotNull(db.lectureSlotDao().getSlotById(slotId))
        assertEquals(1, db.lectureSlotDao().getSlotById(slotId)?.dayOfWeek)

        assertNotNull(db.noteDao().getNoteById(noteId))
        assertEquals("Sprint Notes", db.noteDao().getNoteById(noteId)?.title)

        // Assert group cache was cleared
        assertTrue(context.getSharedPreferences("group_cache", Context.MODE_PRIVATE).all.isEmpty())
    }

    @Test
    fun deleteAccountCleanup_preservesPersonalDataAndNoteImages() = runBlocking {
        val courseId = db.courseDao().insertCourse(
            Course(name = "Operating Systems", code = "CS401", colorHex = "#10B981")
        )
        val taskId = db.taskDao().insertTask(
            TaskItem(courseId = courseId, title = "Kernel Lab", dueDateEpochMillis = 1700000000000L)
        )
        val slotId = db.lectureSlotDao().insertSlot(
            LectureSlot(courseId = courseId, dayOfWeek = 2, startHour = 14, startMinute = 0, endHour = 15, endMinute = 30)
        )
        val noteId = db.noteDao().insert(
            Note(title = "OS Notes", body = "Process scheduling algorithms")
        )

        // Create a local note image file
        val imagesDir = File(context.filesDir, "note_images").apply { mkdirs() }
        val noteImageFile = File(imagesDir, "test_note_img_12345678.jpg").apply {
            writeText("dummy image content")
        }
        assertTrue(noteImageFile.exists())

        // Call delete-account cleanup
        clearGroupDataForDeletedAccount(context)

        // Assert all personal data remains untouched
        assertNotNull(db.courseDao().getAnyCourseByIdSync(courseId))
        assertNotNull(db.taskDao().getAnyTaskById(taskId))
        assertNotNull(db.lectureSlotDao().getSlotById(slotId))
        assertNotNull(db.noteDao().getNoteById(noteId))

        // Assert note image file is still preserved
        assertTrue(noteImageFile.exists())
    }
}
