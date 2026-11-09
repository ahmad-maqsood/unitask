package com.example.util

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.util.timer.TimerHistoryStore
import com.example.util.timer.TimerMode
import com.example.util.timer.TimerSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Study history must travel with the Drive backup and the file backup, and merge on restore. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TimerHistoryBackupTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var history: TimerHistoryStore
    private val createdFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.getDatabase(context)
        runBlocking(Dispatchers.IO) { db.clearAllTables() }
        // The history store is shared by the whole app; start every test from a clean one.
        TimerHistoryStore.setInstanceForTests(null)
        history = TimerHistoryStore.get(context)
        history.clear()
    }

    @After
    fun tearDown() {
        TimerHistoryStore.setInstanceForTests(null)
        db.close()
        AppDatabase::class.java.getDeclaredField("INSTANCE")
            .apply { isAccessible = true }
            .set(null, null)
        createdFiles.forEach { if (it.exists()) it.delete() }
    }

    private fun session(start: Long, minutes: Long = 25) = TimerSession(
        startedAtMillis = start,
        durationMillis = minutes * 60_000L,
        mode = TimerMode.COUNTDOWN,
        plannedMillis = 25 * 60_000L,
        completed = minutes == 25L
    )

    @Test
    fun backupDataContainsTheStudySessions() = runBlocking {
        history.add(session(1_000L))
        history.add(session(2_000L, minutes = 40))

        val built = LocalBackupManager.buildData(context, inlineImages = true)

        val array = built.data.getJSONArray("timerSessions")
        assertEquals(2, array.length())
        assertEquals(2, built.counts.sessions)
    }

    @Test
    fun backupWithoutHistoryHasNoSessionsKey() = runBlocking {
        val built = LocalBackupManager.buildData(context, inlineImages = true)

        assertFalse(built.data.has("timerSessions"))
        assertEquals(0, built.counts.sessions)
    }

    @Test
    fun restoreOnAFreshPhoneBringsTheSessionsBack() = runBlocking {
        history.add(session(1_000L))
        history.add(session(2_000L, minutes = 40))
        val built = LocalBackupManager.buildData(context, inlineImages = true)

        history.clear() // a new phone has no history
        val counts = LocalBackupManager.applyBackupData(context, built.data)

        assertEquals(2, counts.sessions)
        val restored = history.sessions.value
        assertEquals(listOf(2_000L, 1_000L), restored.map { it.startedAtMillis })
        assertEquals(40 * 60_000L, restored.first().durationMillis)
        assertEquals(TimerMode.COUNTDOWN, restored.first().mode)
        assertEquals(25 * 60_000L, restored.first().plannedMillis)
    }

    @Test
    fun restoreMergesAndNeverDuplicatesOrDropsLocalSessions() = runBlocking {
        history.add(session(1_000L))
        history.add(session(2_000L))
        val built = LocalBackupManager.buildData(context, inlineImages = true)

        // After the backup was made: a new session was studied here and an old one was deleted.
        history.add(session(3_000L))
        history.delete(1_000L)

        val counts = LocalBackupManager.applyBackupData(context, built.data)

        // Only the deleted one (1_000) was missing, so only it is added back.
        assertEquals(1, counts.sessions)
        assertEquals(listOf(3_000L, 2_000L, 1_000L), history.sessions.value.map { it.startedAtMillis })

        // Restoring the same backup again changes nothing.
        val again = LocalBackupManager.applyBackupData(context, built.data)
        assertEquals(0, again.sessions)
        assertEquals(3, history.sessions.value.size)
    }

    @Test
    fun existingSessionIsKeptWhenTheBackupHasTheSameStartTime() = runBlocking {
        history.add(session(1_000L, minutes = 10))
        val data = JSONObject().put(
            "timerSessions",
            JSONArray().put(TimerHistoryStore.sessionToJson(session(1_000L, minutes = 99)))
        )

        val counts = LocalBackupManager.applyBackupData(context, data)

        assertEquals(0, counts.sessions)
        assertEquals(10 * 60_000L, history.sessions.value.single().durationMillis)
    }

    @Test
    fun damagedSessionEntriesAreSkippedAndTheRestIsRestored() = runBlocking {
        val data = JSONObject().put(
            "timerSessions",
            JSONArray()
                .put(TimerHistoryStore.sessionToJson(session(5_000L)))
                .put(JSONObject().put("oops", 1))
                .put("not an object")
                .put(JSONObject().put("s", -4).put("d", 1000).put("m", "STOPWATCH"))
        )

        val counts = LocalBackupManager.applyBackupData(context, data)

        assertEquals(1, counts.sessions)
        assertEquals(5_000L, history.sessions.value.single().startedAtMillis)
    }

    @Test
    fun anOldBackupWithoutSessionsStillRestoresAndKeepsLocalHistory() = runBlocking {
        history.add(session(1_000L))

        val counts = LocalBackupManager.applyBackupData(context, JSONObject())

        assertEquals(0, counts.sessions)
        assertEquals(1, history.sessions.value.size)
    }

    @Test
    fun fileBackupRoundTripKeepsTheHistory() = runBlocking {
        history.add(session(1_000L))
        history.add(session(2_000L, minutes = 40))

        val file = File(context.cacheDir, "history-backup.json").also {
            if (it.exists()) it.delete()
            it.createNewFile()
            createdFiles.add(it)
        }
        val uri = Uri.fromFile(file)

        val export = LocalBackupManager.export(context, uri, password = null)
        assertTrue(export is BackupResult.Success)
        assertEquals(2, (export as BackupResult.Success).counts.sessions)

        history.clear()
        val restore = LocalBackupManager.restore(context, uri, password = null)

        assertTrue(restore is BackupResult.Success)
        assertEquals(2, (restore as BackupResult.Success).counts.sessions)
        assertEquals(listOf(2_000L, 1_000L), history.sessions.value.map { it.startedAtMillis })
    }
}
