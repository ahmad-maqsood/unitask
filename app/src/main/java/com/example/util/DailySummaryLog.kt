package com.example.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One daily summary kept for the bell. [id] is the time it was made, in milliseconds. */
data class DailySummaryEntry(
    val id: Long,
    val dayKey: String,
    val title: String,
    val body: String,
    val unread: Boolean
)

/**
 * The last few daily summaries, kept on the phone so the bell can show the full text.
 * The notification itself only has room for a few lines. One entry per day: a new summary on the
 * same day replaces the old one.
 */
object DailySummaryLog {
    private const val PREFS = "daily_summary_log"
    private const val KEY_ITEMS = "items_json"
    private const val MAX_ITEMS = 7

    private val LOCK = Any()

    /** Saves today's summary. Safe to call from a background thread. */
    fun add(context: Context, title: String, body: String, nowMillis: Long = System.currentTimeMillis()) {
        synchronized(LOCK) {
            val day = dayKey(nowMillis)
            val kept = load(context).filter { it.dayKey != day }
            val all = (kept + DailySummaryEntry(nowMillis, day, title, body, unread = true))
                .sortedByDescending { it.id }
                .take(MAX_ITEMS)
            save(context, all)
        }
    }

    /** Newest first. */
    fun items(context: Context): List<DailySummaryEntry> = synchronized(LOCK) {
        load(context).sortedByDescending { it.id }
    }

    fun unreadCount(context: Context): Int = items(context).count { it.unread }

    fun markAllRead(context: Context) {
        synchronized(LOCK) {
            val all = load(context)
            if (all.any { it.unread }) save(context, all.map { it.copy(unread = false) })
        }
    }

    fun remove(context: Context, id: Long) {
        synchronized(LOCK) { save(context, load(context).filter { it.id != id }) }
    }

    fun clear(context: Context) {
        synchronized(LOCK) { save(context, emptyList()) }
    }

    private fun dayKey(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun save(context: Context, items: List<DailySummaryEntry>) {
        try {
            val array = JSONArray()
            items.forEach {
                array.put(
                    JSONObject()
                        .put("id", it.id)
                        .put("day", it.dayKey)
                        .put("title", it.title)
                        .put("body", it.body)
                        .put("unread", it.unread)
                )
            }
            prefs(context).edit().putString(KEY_ITEMS, array.toString()).apply()
        } catch (_: Exception) {
        }
    }

    private fun load(context: Context): List<DailySummaryEntry> = try {
        val text = prefs(context).getString(KEY_ITEMS, null)
        if (text.isNullOrBlank()) {
            emptyList()
        } else {
            val array = JSONArray(text)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                DailySummaryEntry(
                    id = o.getLong("id"),
                    dayKey = o.optString("day"),
                    title = o.optString("title"),
                    body = o.optString("body"),
                    unread = o.optBoolean("unread", false)
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}
