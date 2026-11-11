package com.example.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Progress history must travel with the Drive backup and the file backup, and merge on restore. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CompletionLogBackupTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private val day = 24L * 60 * 60 * 1000

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.getDatabase(context)
        runBlocking(Dispatchers.IO) { db.clearAllTables() }
        CompletionLog.clear(context)
    }

    @After
    fun tearDown() {
        CompletionLog.clear(context)
        db.close()
        AppDatabase::class.java.getDeclaredField("INSTANCE")
            .apply { isAccessible = true }
            .set(null, null)
    }

    @Test
    fun backupDataContainsTheTickTimes() = runBlocking {
        val now = System.currentTimeMillis()
        CompletionLog.record(context, 1L, now)
        CompletionLog.record(context, 2L, now - day)

        val built = LocalBackupManager.buildData(context, inlineImages = true)

        val array = built.data.getJSONArray("completionLog")
        assertEquals(listOf(now, now - day), (0 until array.length()).map { array.getLong(it) })
    }

    @Test
    fun backupWithoutHistoryHasNoCompletionKey() = runBlocking {
        val built = LocalBackupManager.buildData(context, inlineImages = true)

        assertFalse(built.data.has("completionLog"))
    }

    @Test
    fun restoreOnAFreshPhoneBringsTheTicksBack() = runBlocking {
        val now = System.currentTimeMillis()
        CompletionLog.record(context, 1L, now)
        CompletionLog.record(context, 2L, now - day)
        val built = LocalBackupManager.buildData(context, inlineImages = true)

        CompletionLog.clear(context) // a new phone has no history
        LocalBackupManager.applyBackupData(context, built.data)

        assertEquals(listOf(now, now - day), CompletionLog.entries(context).map { it.completedAt })
    }

    @Test
    fun restoreMergesAndNeverDuplicatesLocalTicks() = runBlocking {
        val now = System.currentTimeMillis()
        val data = JSONObject().put("completionLog", JSONArray().put(now - day).put(now - 2 * day))
        CompletionLog.record(context, 9L, now - day) // already here, under a task number

        LocalBackupManager.applyBackupData(context, data)
        LocalBackupManager.applyBackupData(context, data) // restoring twice changes nothing

        assertEquals(
            listOf(now - day, now - 2 * day),
            CompletionLog.entries(context).map { it.completedAt }
        )
    }

    @Test
    fun damagedEntriesAreSkippedAndTheRestIsRestored() = runBlocking {
        val now = System.currentTimeMillis()
        val data = JSONObject().put(
            "completionLog",
            JSONArray().put(now - day).put("oops").put(-4).put(JSONObject().put("x", 1))
        )

        LocalBackupManager.applyBackupData(context, data)

        assertEquals(listOf(now - day), CompletionLog.entries(context).map { it.completedAt })
    }
}
