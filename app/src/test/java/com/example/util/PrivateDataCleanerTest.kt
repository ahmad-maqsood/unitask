package com.example.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.reminder.SnoozeStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PrivateDataCleanerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        NotificationHistory.clear(context)
        DailySummaryLog.clear(context)
        val noteImagesDir = File(context.filesDir, "note_images")
        if (noteImagesDir.exists()) {
            noteImagesDir.deleteRecursively()
        }
        val outsideFile = File(context.filesDir, "outside_file.txt")
        if (outsideFile.exists()) {
            outsideFile.delete()
        }
    }

    @After
    fun tearDown() {
        val outsideFile = File(context.filesDir, "outside_file.txt")
        if (outsideFile.exists()) {
            outsideFile.delete()
        }
        val noteImagesDir = File(context.filesDir, "note_images")
        if (noteImagesDir.exists()) {
            noteImagesDir.deleteRecursively()
        }
        NotificationHistory.clear(context)
        DailySummaryLog.clear(context)
        SnoozeStore.entries(context).forEach { SnoozeStore.clear(context, it.key, cancelAlarm = true) }
    }

    @Test
    fun clearPersonalFilesCleansNoteImagesAndLogsWhileKeepingFolderAndOutsideFiles() {
        val noteImagesDir = File(context.filesDir, "note_images")
        noteImagesDir.mkdirs()

        val dummyFile1 = File(noteImagesDir, "image1.jpg")
        dummyFile1.writeText("dummy image 1")
        val dummyFile2 = File(noteImagesDir, "image2.jpg")
        dummyFile2.writeText("dummy image 2")

        val outsideFile = File(context.filesDir, "outside_file.txt")
        outsideFile.writeText("keep me")

        NotificationHistory.add(context, "alert", "Notification Title", "Notification Body")
        DailySummaryLog.add(context, "Daily Summary Title", "Daily Summary Body")

        PrivateDataCleaner.clearPersonalFiles(context)

        assertTrue(noteImagesDir.exists())
        assertTrue(noteImagesDir.isDirectory)
        val files = noteImagesDir.listFiles()
        assertTrue(files != null && files.isEmpty())

        assertTrue(NotificationHistory.items(context).isEmpty())
        assertTrue(DailySummaryLog.items(context).isEmpty())

        assertTrue(outsideFile.exists())
        assertEquals("keep me", outsideFile.readText())
    }

    @Test
    fun clearPersonalFilesAlsoClearsSnoozeEntries() {
        val entry = SnoozeStore.Entry(
            key = 100L,
            until = System.currentTimeMillis() + 60_000L,
            taskId = 1L,
            title = "Lab Report",
            courseCode = "CS101",
            dueInfo = "Tomorrow",
            priority = "MEDIUM"
        )
        SnoozeStore.set(context, entry)
        assertTrue(SnoozeStore.entries(context).isNotEmpty())

        PrivateDataCleaner.clearPersonalFiles(context)

        assertTrue(SnoozeStore.entries(context).isEmpty())
    }
}
