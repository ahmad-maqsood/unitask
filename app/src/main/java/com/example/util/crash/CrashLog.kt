package com.example.util.crash

import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Text helpers for the crash log. They use no Android classes, so plain unit tests can run them.
 *
 * The log is one text file holding the last few crashes. Every crash starts with a marker line,
 * so the file stays easy to read and easy to split again.
 */
object CrashLog {

    /** First line of every crash in the file. */
    const val MARKER = "==== UniTask crash ===="

    /** Only the newest crashes are kept. */
    const val MAX_ENTRIES = 5

    /** One stack trace is cut to this many characters. */
    const val MAX_TRACE_CHARS = 12_000

    /** The whole file is kept under this many characters. The newest crash is always kept. */
    const val MAX_FILE_CHARS = 60_000

    /** The short summary is small enough to fit in a prefilled form link. */
    const val SUMMARY_CHARS = 400

    /** Builds the text for one crash. */
    fun formatEntry(
        timeMillis: Long,
        appVersion: String,
        androidInfo: String,
        device: String,
        threadName: String,
        error: Throwable
    ): String {
        val text = StringBuilder()
        text.append(MARKER).append('\n')
        text.append("Time: ").append(formatTime(timeMillis)).append('\n')
        text.append("App: ").append(appVersion).append('\n')
        text.append("Android: ").append(androidInfo).append('\n')
        text.append("Device: ").append(device).append('\n')
        text.append("Thread: ").append(threadName).append('\n')
        text.append(stackTrace(error))
        return text.toString().trimEnd() + "\n"
    }

    /** The stack trace of [error] including its causes, cut to [MAX_TRACE_CHARS]. */
    fun stackTrace(error: Throwable): String {
        val writer = StringWriter()
        error.printStackTrace(PrintWriter(writer))
        val full = writer.toString()
        return if (full.length > MAX_TRACE_CHARS) {
            full.take(MAX_TRACE_CHARS) + "\n... (cut)"
        } else {
            full
        }
    }

    /** Splits the file text into single crashes. Text before the first marker is ignored. */
    fun splitEntries(log: String): List<String> {
        if (log.isBlank()) return emptyList()
        val entries = ArrayList<String>()
        var current: StringBuilder? = null
        for (line in log.lineSequence()) {
            if (line == MARKER) {
                current?.let { entries.add(it.toString().trimEnd() + "\n") }
                current = StringBuilder()
            }
            current?.append(line)?.append('\n')
        }
        current?.let { entries.add(it.toString().trimEnd() + "\n") }
        return entries
    }

    /** Adds [entry] to the end of [existing] and drops the oldest crashes when the file gets too big. */
    fun appendEntry(existing: String, entry: String): String {
        val newest = entry.trimEnd() + "\n"
        var entries = (splitEntries(existing) + newest).takeLast(MAX_ENTRIES)
        while (entries.size > 1 && entries.sumOf { it.length } > MAX_FILE_CHARS) {
            entries = entries.drop(1)
        }
        return entries.joinToString("")
    }

    /** The newest crash in the file, or null when there is none. */
    fun latestEntry(log: String): String? = splitEntries(log).lastOrNull()

    /**
     * One short line about a crash: the error and its first two stack frames. It is used to
     * prefill the problem form, so it stays short.
     */
    fun summaryOf(entry: String): String {
        val lines = entry.lines()
        val afterThread = lines.indexOfFirst { it.startsWith("Thread:") } + 1
        val trace = lines.drop(afterThread).filter { it.isNotBlank() }
        val head = trace.firstOrNull()?.trim().orEmpty()
        val frames = trace.drop(1)
            .map { it.trim() }
            .filter { it.startsWith("at ") }
            .take(2)
        return (listOf(head) + frames).filter { it.isNotEmpty() }.joinToString(" | ").take(SUMMARY_CHARS)
    }

    /** Time in UTC, so a log sent from another time zone is still easy to match with a date. */
    fun formatTime(timeMillis: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date(timeMillis))
    }
}
