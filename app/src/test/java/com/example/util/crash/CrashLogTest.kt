package com.example.util.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashLogTest {

    private fun entry(message: String, thread: String = "main", time: Long = 0L): String =
        CrashLog.formatEntry(
            timeMillis = time,
            appVersion = "4.1.0",
            androidInfo = "Android 14 (API 34)",
            device = "Google Pixel",
            threadName = thread,
            error = IllegalStateException(message)
        )

    @Test
    fun entryStartsWithMarkerAndHasDeviceDetailsAndStackTrace() {
        val text = entry("boom", thread = "worker-1")
        val lines = text.lines()

        assertEquals(CrashLog.MARKER, lines[0])
        assertTrue(text.contains("App: 4.1.0"))
        assertTrue(text.contains("Android: Android 14 (API 34)"))
        assertTrue(text.contains("Device: Google Pixel"))
        assertTrue(text.contains("Thread: worker-1"))
        assertTrue(text.contains("java.lang.IllegalStateException: boom"))
        assertTrue(text.endsWith("\n"))
    }

    @Test
    fun timeIsWrittenInUtc() {
        assertEquals("1970-01-01 00:00:00 UTC", CrashLog.formatTime(0L))
        assertEquals("2026-11-12 10:15:30 UTC", CrashLog.formatTime(1_794_478_530_000L))
    }

    @Test
    fun veryLongStackTraceIsCut() {
        val text = CrashLog.stackTrace(IllegalStateException("a".repeat(50_000)))

        assertTrue(text.endsWith("... (cut)"))
        assertTrue(text.length <= CrashLog.MAX_TRACE_CHARS + 20)
    }

    @Test
    fun appendKeepsOnlyTheNewestEntries() {
        var log = ""
        for (i in 1..7) {
            log = CrashLog.appendEntry(log, entry("boom $i"))
        }
        val entries = CrashLog.splitEntries(log)

        assertEquals(CrashLog.MAX_ENTRIES, entries.size)
        assertTrue(entries.first().contains("boom 3"))
        assertTrue(entries.last().contains("boom 7"))
    }

    @Test
    fun appendDropsTheOldestEntriesWhenTheFileIsTooBig() {
        fun bigEntry(label: String) =
            CrashLog.MARKER + "\nThread: $label\n" + "x".repeat(25_000) + "\n"

        var log = ""
        for (label in listOf("one", "two", "three")) {
            log = CrashLog.appendEntry(log, bigEntry(label))
        }
        val entries = CrashLog.splitEntries(log)

        assertEquals(2, entries.size)
        assertTrue(entries.first().contains("Thread: two"))
        assertTrue(entries.last().contains("Thread: three"))
        assertTrue(log.length <= CrashLog.MAX_FILE_CHARS)
    }

    @Test
    fun aSingleHugeEntryIsStillKept() {
        val huge = CrashLog.MARKER + "\nThread: main\n" + "y".repeat(100_000) + "\n"
        val log = CrashLog.appendEntry("", huge)

        assertEquals(1, CrashLog.splitEntries(log).size)
    }

    @Test
    fun splitIgnoresEmptyTextAndTextBeforeTheFirstMarker() {
        assertTrue(CrashLog.splitEntries("").isEmpty())
        assertTrue(CrashLog.splitEntries("   \n").isEmpty())

        val log = "stray line\n" + entry("boom")
        val entries = CrashLog.splitEntries(log)

        assertEquals(1, entries.size)
        assertTrue(entries[0].startsWith(CrashLog.MARKER))
    }

    @Test
    fun latestEntryIsTheNewestOneOrNull() {
        assertNull(CrashLog.latestEntry(""))

        var log = ""
        log = CrashLog.appendEntry(log, entry("first"))
        log = CrashLog.appendEntry(log, entry("second"))

        assertTrue(CrashLog.latestEntry(log)!!.contains("second"))
    }

    @Test
    fun summaryHasTheErrorAndAtMostTwoStackFrames() {
        val summary = CrashLog.summaryOf(entry("boom"))
        val parts = summary.split(" | ")

        assertTrue(summary.startsWith("java.lang.IllegalStateException: boom"))
        assertTrue(parts.size in 2..3)
        assertTrue(parts.drop(1).all { it.startsWith("at ") })
        assertTrue(summary.length <= CrashLog.SUMMARY_CHARS)
    }

    @Test
    fun summaryIsCutToTheLimit() {
        val summary = CrashLog.summaryOf(entry("z".repeat(2_000)))

        assertEquals(CrashLog.SUMMARY_CHARS, summary.length)
    }
}
