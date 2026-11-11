package com.example.util

import android.content.Context
import org.json.JSONArray

/** One task that was ticked Done, and when. */
data class CompletionEntry(val taskId: Long, val completedAt: Long)

/**
 * A small history of ticked tasks, saved on the phone only.
 *
 * Why it exists: a repeating task is reset to Pending after midnight and its "ticked at" time is
 * cleared, and a "delete when done" task is removed. Without this history the Progress screen
 * would forget those completions the next day. Entries older than 400 days are dropped and at most
 * 5000 are kept, so it stays small.
 */
object CompletionLog {
    private const val PREFS = "completion_log"
    private const val KEY_ITEMS = "items_json"
    private const val MAX_ITEMS = 5000
    private const val MAX_AGE_MILLIS = 400L * 24 * 60 * 60 * 1000
    private val LOCK = Any()

    /** Adds an entry. The same task and time twice is kept once. Pure, so it is easy to test. */
    fun withEntry(
        list: List<CompletionEntry>,
        entry: CompletionEntry,
        nowMillis: Long
    ): List<CompletionEntry> {
        if (entry.completedAt <= 0L) return prune(list, nowMillis)
        val kept = prune(list, nowMillis).filter { it != entry }
        return (kept + entry).sortedByDescending { it.completedAt }.take(MAX_ITEMS)
    }

    /** Removes the newest entry of one task (the tick that was just undone). Pure. */
    fun withoutLatest(list: List<CompletionEntry>, taskId: Long): List<CompletionEntry> {
        val latest = list.filter { it.taskId == taskId }.maxByOrNull { it.completedAt } ?: return list
        return list.filter { it != latest }
    }

    private fun prune(list: List<CompletionEntry>, nowMillis: Long): List<CompletionEntry> =
        list.filter { nowMillis - it.completedAt <= MAX_AGE_MILLIS }

    /** Saves one tick. Safe to call from a background thread. Never throws. */
    fun record(
        context: Context,
        taskId: Long,
        completedAt: Long,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        try {
            synchronized(LOCK) {
                save(context, withEntry(load(context), CompletionEntry(taskId, completedAt), nowMillis))
            }
        } catch (_: Exception) {
        }
    }

    /** Forgets the newest tick of a task, because the user marked it not done. Never throws. */
    fun removeLatest(context: Context, taskId: Long) {
        try {
            synchronized(LOCK) {
                val current = load(context)
                val updated = withoutLatest(current, taskId)
                if (updated.size != current.size) save(context, updated)
            }
        } catch (_: Exception) {
        }
    }

    /** Every saved tick, newest first. */
    fun entries(context: Context): List<CompletionEntry> = synchronized(LOCK) {
        prune(load(context), System.currentTimeMillis()).sortedByDescending { it.completedAt }
    }

    fun clear(context: Context) {
        synchronized(LOCK) { save(context, emptyList()) }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun save(context: Context, items: List<CompletionEntry>) {
        try {
            val array = JSONArray()
            items.forEach { array.put(JSONArray().put(it.taskId).put(it.completedAt)) }
            prefs(context).edit().putString(KEY_ITEMS, array.toString()).apply()
        } catch (_: Exception) {
        }
    }

    private fun load(context: Context): List<CompletionEntry> = try {
        val text = prefs(context).getString(KEY_ITEMS, null)
        if (text.isNullOrBlank()) {
            emptyList()
        } else {
            val array = JSONArray(text)
            (0 until array.length()).mapNotNull { i ->
                val pair = array.optJSONArray(i) ?: return@mapNotNull null
                CompletionEntry(pair.optLong(0), pair.optLong(1))
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}
