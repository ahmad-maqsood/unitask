package com.example.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CompletionLogTest {

    private lateinit var context: Context
    private val day = 24L * 60 * 60 * 1000

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        CompletionLog.clear(context)
    }

    @Test
    fun theSameTaskAndTimeIsKeptOnce() {
        val now = System.currentTimeMillis()
        val entry = CompletionEntry(1L, now - 1_000L)
        val list = CompletionLog.withEntry(CompletionLog.withEntry(emptyList(), entry, now), entry, now)
        assertEquals(listOf(entry), list)
    }

    @Test
    fun aRepeatingTaskTickedOnTwoDaysKeepsBothTicks() {
        val now = System.currentTimeMillis()
        val first = CompletionEntry(1L, now - day)
        val second = CompletionEntry(1L, now)
        val list = CompletionLog.withEntry(CompletionLog.withEntry(emptyList(), first, now), second, now)
        assertEquals(listOf(second, first), list)
    }

    @Test
    fun withoutLatestRemovesOnlyTheNewestTickOfThatTask() {
        val now = System.currentTimeMillis()
        val older = CompletionEntry(1L, now - day)
        val newer = CompletionEntry(1L, now)
        val other = CompletionEntry(2L, now)
        val list = CompletionLog.withoutLatest(listOf(newer, older, other), taskId = 1L)
        assertEquals(listOf(older, other), list)
    }

    @Test
    fun entriesWithoutATimeAreIgnoredAndOldOnesDropped() {
        val now = System.currentTimeMillis()
        val old = CompletionEntry(1L, now - 401 * day)
        val noTime = CompletionEntry(2L, 0L)
        val fresh = CompletionEntry(3L, now)
        val list = CompletionLog.withEntry(CompletionLog.withEntry(listOf(old), noTime, now), fresh, now)
        assertEquals(listOf(fresh), list)
    }

    @Test
    fun restoredTimesSkipKnownTimesWhateverTheTaskAndDropBadOnes() {
        val now = System.currentTimeMillis()
        val known = CompletionEntry(5L, now - day)
        val list = CompletionLog.withRestoredTimes(
            listOf(known),
            times = listOf(now - day, now - 2 * day, now - 2 * day, 0L, -5L, now - 401 * day),
            nowMillis = now
        )
        assertEquals(listOf(known, CompletionEntry(0L, now - 2 * day)), list)
    }

    @Test
    fun forgetRemovesEveryEntryWithThatTimeAndFallsBackToTheTask() {
        val now = System.currentTimeMillis()
        CompletionLog.record(context, 1L, now)
        CompletionLog.restoreTimes(context, listOf(now - day))
        CompletionLog.record(context, 2L, now - 5_000L)

        // Known time: the entry goes, other entries stay.
        CompletionLog.forget(context, taskId = 1L, completedAt = now)
        assertEquals(listOf(now - 5_000L, now - day), CompletionLog.entries(context).map { it.completedAt })

        // Unknown time (0): the newest tick of the task goes.
        CompletionLog.forget(context, taskId = 2L, completedAt = 0L)
        assertEquals(listOf(now - day), CompletionLog.entries(context).map { it.completedAt })
    }

    @Test
    fun storeKeepsTicksNewestFirstAndForgetsAnUndoneOne() {
        val now = System.currentTimeMillis()
        CompletionLog.record(context, 1L, now - day)
        CompletionLog.record(context, 1L, now)
        CompletionLog.record(context, 2L, now - 5_000L)
        assertEquals(
            listOf(CompletionEntry(1L, now), CompletionEntry(2L, now - 5_000L), CompletionEntry(1L, now - day)),
            CompletionLog.entries(context)
        )

        CompletionLog.removeLatest(context, 1L)
        assertEquals(
            listOf(CompletionEntry(2L, now - 5_000L), CompletionEntry(1L, now - day)),
            CompletionLog.entries(context)
        )
    }
}
