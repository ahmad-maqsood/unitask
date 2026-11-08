package com.example.data.repository

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.Course
import com.example.data.local.LectureSlot
import com.example.data.local.LectureSlotWithCourse
import com.example.reminder.ClassReminderScheduler
import com.example.reminder.ClassStartReceiver
import com.example.reminder.ReminderScheduler
import com.example.util.LocalBackupManager
import com.example.util.LocalTombstonePurge
import com.example.util.findConflicts
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
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ClassTrashRepositoryTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var scheduler: ReminderScheduler
    private lateinit var repo: AppRepository
    private var courseId: Long = 0L

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
        courseId = runBlocking {
            db.courseDao().insertCourse(Course(name = "Math", code = "MATH 101", colorHex = "#4F46E5"))
        }
        context.getSharedPreferences("local_tombstone_purge", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context.getSharedPreferences("unitask_settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @After
    fun tearDown() {
        runBlocking {
            db.clearAllTables()
        }
        context.getSharedPreferences("local_tombstone_purge", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context.getSharedPreferences("unitask_settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun deleteMovesSlotToTrashAndCancelsAlarms() = runBlocking {
        val slot = LectureSlot(
            courseId = courseId,
            dayOfWeek = 1,
            startHour = 9,
            startMinute = 0,
            endHour = 10,
            endMinute = 0,
            classroom = "Room 101"
        )
        val id = repo.insertLectureSlot(slot)
        val inserted = db.lectureSlotDao().getSlotById(id)!!

        // Before delete: scheduled alarms pref contains id
        val prefs = context.getSharedPreferences("unitask_settings", Context.MODE_PRIVATE)
        val scheduledBefore = prefs.getStringSet("class_start_scheduled_slot_ids", emptySet()) ?: emptySet()
        assertTrue(scheduledBefore.contains(id.toString()))

        repo.deleteLectureSlot(inserted)

        // Timetable queries exclude the soft-deleted slot
        assertTrue(db.lectureSlotDao().getAllSlotsWithCourse().first().none { it.slot.id == id })
        assertTrue(db.lectureSlotDao().getSlotsForDay(1).first().none { it.slot.id == id })
        assertTrue(db.lectureSlotDao().getSlotsForCourse(courseId).first().none { it.slot.id == id })
        assertTrue(repo.allLectureSlots.first().none { it.slot.id == id })

        // Trashed queries include it
        val trashed = db.lectureSlotDao().getDeletedSlotsSync().firstOrNull { it.id == id }
        assertNotNull(trashed)
        assertTrue(trashed!!.isDeleted)
        assertTrue(trashed.deletedAt > 0L)

        // Alarms are cancelled
        val scheduledAfter = prefs.getStringSet("class_start_scheduled_slot_ids", emptySet()) ?: emptySet()
        assertFalse(scheduledAfter.contains(id.toString()))
    }

    @Test
    fun restoreSlotReArmsAlarmsAndRestoresTimetable() = runBlocking {
        val slot = LectureSlot(
            courseId = courseId,
            dayOfWeek = 2,
            startHour = 14,
            startMinute = 0,
            endHour = 15,
            endMinute = 30,
            classroom = "Hall B"
        )
        val id = repo.insertLectureSlot(slot)
        val inserted = db.lectureSlotDao().getSlotById(id)!!
        repo.deleteLectureSlot(inserted)

        assertEquals(1, db.lectureSlotDao().getDeletedSlotsSync().size)

        repo.restoreLectureSlot(id)

        val restored = db.lectureSlotDao().getSlotById(id)
        assertNotNull(restored)
        assertFalse(restored!!.isDeleted)
        assertEquals(0L, restored.deletedAt)

        // Active timetable queries see it again
        assertTrue(db.lectureSlotDao().getAllSlotsWithCourse().first().any { it.slot.id == id })
        assertTrue(db.lectureSlotDao().getSlotsForDay(2).first().any { it.slot.id == id })
        assertTrue(repo.allLectureSlots.first().any { it.slot.id == id })

        // Alarms re-armed
        val prefs = context.getSharedPreferences("unitask_settings", Context.MODE_PRIVATE)
        val scheduled = prefs.getStringSet("class_start_scheduled_slot_ids", emptySet()) ?: emptySet()
        assertTrue(scheduled.contains(id.toString()))
    }

    @Test
    fun purgeRemovesOnlyItemsOlderThan30Days() = runBlocking {
        val now = 100_000_000_000L
        val thirtyDaysMs = NoteRepository.TRASH_DAYS * 24L * 60L * 60L * 1000L

        // Old trashed slot (35 days old)
        val oldSlotId = db.lectureSlotDao().insertSlot(
            LectureSlot(
                courseId = courseId,
                dayOfWeek = 1,
                startHour = 9,
                startMinute = 0,
                endHour = 10,
                endMinute = 0,
                isDeleted = true,
                deletedAt = now - thirtyDaysMs - 5 * 86400_000L
            )
        )

        // Recent trashed slot (10 days old)
        val recentSlotId = db.lectureSlotDao().insertSlot(
            LectureSlot(
                courseId = courseId,
                dayOfWeek = 2,
                startHour = 11,
                startMinute = 0,
                endHour = 12,
                endMinute = 0,
                isDeleted = true,
                deletedAt = now - 10 * 86400_000L
            )
        )

        // Active slot
        val activeSlotId = db.lectureSlotDao().insertSlot(
            LectureSlot(
                courseId = courseId,
                dayOfWeek = 3,
                startHour = 14,
                startMinute = 0,
                endHour = 15,
                endMinute = 0,
                isDeleted = false,
                deletedAt = 0L
            )
        )

        val purgedCount = repo.purgeOldClassTrash(now)
        assertEquals(1, purgedCount)

        // Old slot removed forever
        assertNull(db.lectureSlotDao().getSlotById(oldSlotId))

        // Recent slot and active slot preserved
        assertNotNull(db.lectureSlotDao().getSlotById(recentSlotId))
        assertNotNull(db.lectureSlotDao().getSlotById(activeSlotId))
    }

    @Test
    fun deleteSlotForeverAndEmptyClassTrash() = runBlocking {
        val slot1Id = db.lectureSlotDao().insertSlot(
            LectureSlot(courseId = courseId, dayOfWeek = 1, startHour = 9, startMinute = 0, endHour = 10, endMinute = 0, isDeleted = true, deletedAt = 1000L)
        )
        val slot2Id = db.lectureSlotDao().insertSlot(
            LectureSlot(courseId = courseId, dayOfWeek = 2, startHour = 10, startMinute = 0, endHour = 11, endMinute = 0, isDeleted = true, deletedAt = 2000L)
        )

        assertEquals(2, db.lectureSlotDao().getDeletedSlotsSync().size)

        repo.deleteSlotForever(slot1Id)
        assertNull(db.lectureSlotDao().getSlotById(slot1Id))
        assertEquals(1, db.lectureSlotDao().getDeletedSlotsSync().size)

        repo.emptyClassTrash()
        assertNull(db.lectureSlotDao().getSlotById(slot2Id))
        assertEquals(0, db.lectureSlotDao().getDeletedSlotsSync().size)
    }

    @Test
    fun backupExportExcludesTrashedSlots() = runBlocking {
        // Active slot
        val activeSlot = LectureSlot(
            courseId = courseId,
            dayOfWeek = 1,
            startHour = 9,
            startMinute = 0,
            endHour = 10,
            endMinute = 0,
            classroom = "Active Room"
        )
        repo.insertLectureSlot(activeSlot)

        // Trashed slot
        val trashedSlot = LectureSlot(
            courseId = courseId,
            dayOfWeek = 2,
            startHour = 11,
            startMinute = 0,
            endHour = 12,
            endMinute = 0,
            classroom = "Trashed Room"
        )
        val trashedId = repo.insertLectureSlot(trashedSlot)
        repo.deleteLectureSlot(db.lectureSlotDao().getSlotById(trashedId)!!)

        val built = LocalBackupManager.buildData(context, inlineImages = false)
        val slotsArray = built.data.getJSONArray("slots")
        assertEquals(1, slotsArray.length())
        assertEquals("Active Room", slotsArray.getJSONObject(0).getString("classroom"))
        assertEquals(1, built.counts.slots)
    }

    @Test
    fun conflictsIgnoreTrashedSlots() = runBlocking {
        val course = db.courseDao().getCourseByIdSync(courseId)!!

        val activeSlot = LectureSlot(
            id = 1L,
            courseId = courseId,
            dayOfWeek = 1,
            startHour = 10,
            startMinute = 0,
            endHour = 11,
            endMinute = 0,
            classroom = "Room A"
        )

        val overlappingTrashedSlot = LectureSlot(
            id = 2L,
            courseId = courseId,
            dayOfWeek = 1,
            startHour = 10,
            startMinute = 30,
            endHour = 11,
            endMinute = 30,
            classroom = "Room B",
            isDeleted = true,
            deletedAt = System.currentTimeMillis()
        )

        val slotsWithCourse = listOf(
            LectureSlotWithCourse(activeSlot, course),
            LectureSlotWithCourse(overlappingTrashedSlot, course)
        )

        val conflicts = findConflicts(slotsWithCourse)
        // Soft-deleted overlapping slot is ignored; no conflict reported for activeSlot
        assertTrue(conflicts[activeSlot.id].isNullOrEmpty())
    }

    @Test
    fun purgeOnceKeepsTrashedSlotsWithDeletedAt() = runBlocking {
        // Old sync-deleted slot (isDeleted = true, deletedAt = 0)
        val syncDeletedId = db.lectureSlotDao().insertSlot(
            LectureSlot(
                courseId = courseId,
                dayOfWeek = 1,
                startHour = 9,
                startMinute = 0,
                endHour = 10,
                endMinute = 0,
                isDeleted = true,
                deletedAt = 0L
            )
        )

        // User-trashed slot (isDeleted = true, deletedAt > 0)
        val userTrashedId = db.lectureSlotDao().insertSlot(
            LectureSlot(
                courseId = courseId,
                dayOfWeek = 2,
                startHour = 11,
                startMinute = 0,
                endHour = 12,
                endMinute = 0,
                isDeleted = true,
                deletedAt = System.currentTimeMillis()
            )
        )

        LocalTombstonePurge.purgeOnce(context, db)

        // Sync-deleted slot is hard-purged
        assertNull(db.lectureSlotDao().getSlotById(syncDeletedId))

        // User-trashed slot is preserved
        assertNotNull(db.lectureSlotDao().getSlotById(userTrashedId))
    }

    @Test
    fun classStartReceiverAndSchedulerDoNothingForTrashedSlot() = runBlocking {
        val trashedSlotId = db.lectureSlotDao().insertSlot(
            LectureSlot(
                courseId = courseId,
                dayOfWeek = 1,
                startHour = 9,
                startMinute = 0,
                endHour = 10,
                endMinute = 0,
                isDeleted = true,
                deletedAt = System.currentTimeMillis()
            )
        )
        val trashedSlot = db.lectureSlotDao().getSlotById(trashedSlotId)!!

        // Scheduler scheduleSlot skips trashed slot
        val scheduler = ClassReminderScheduler(context)
        scheduler.scheduleSlot(trashedSlot)

        val prefs = context.getSharedPreferences("unitask_settings", Context.MODE_PRIVATE)
        val scheduled = prefs.getStringSet("class_start_scheduled_slot_ids", emptySet()) ?: emptySet()
        assertFalse(scheduled.contains(trashedSlotId.toString()))

        // ClassStartReceiver ignores trashed slot and posts no notifications
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNm = Shadows.shadowOf(nm)
        val receiver = ClassStartReceiver()

        val intentStart = Intent(context, ClassStartReceiver::class.java).apply {
            action = ClassReminderScheduler.ACTION_CLASS_START
            putExtra(ClassStartReceiver.EXTRA_SLOT_ID, trashedSlotId)
            putExtra(ClassStartReceiver.EXTRA_START_MILLIS, System.currentTimeMillis())
        }
        receiver.onReceive(context, intentStart)
        Thread.sleep(150)

        val intentSoon = Intent(context, ClassStartReceiver::class.java).apply {
            action = ClassReminderScheduler.ACTION_CLASS_SOON
            putExtra(ClassStartReceiver.EXTRA_SLOT_ID, trashedSlotId)
            putExtra(ClassStartReceiver.EXTRA_START_MILLIS, System.currentTimeMillis() + 600_000L)
        }
        receiver.onReceive(context, intentSoon)
        Thread.sleep(150)

        assertEquals(0, shadowNm.allNotifications.size)
    }
}
