package com.example.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * One notification the app showed, kept for the Notifications tab of the bell.
 *
 * [target] says where a tap should go: "" (nowhere), "tab:DEADLINES", "tab:TIMETABLE",
 * "tab:MYTASKS", "group:<groupId>" or "chat:<groupId>".
 */
data class AppNotification(
    val id: Long,
    val key: String,
    val kind: String,
    val title: String,
    val body: String,
    val target: String,
    val unread: Boolean
) {
    /** When it was made, in milliseconds. */
    val timeMillis: Long get() = id
}

/**
 * The notifications the app sent, saved on the phone only. Newest 100 items, nothing older than
 * 30 days. An item with the same [AppNotification.key] as an older one replaces it, so a group chat
 * has one line that shows its latest message.
 */
object NotificationHistory {
    private const val PREFS = "notification_history"
    private const val KEY_ITEMS = "items_json"
    private const val MAX_ITEMS = 100
    private const val MAX_AGE_MILLIS = 30L * 24 * 60 * 60 * 1000
    /**
     * Kinds that never appear in the bell. Class start alerts are only shown as phone
     * notifications. Items of these kinds saved by older versions are hidden too.
     */
    private val HIDDEN_KINDS = setOf("Class")
    private val LOCK = Any()

    /** Saves one notification. Safe to call from a background thread. Never throws. */
    fun add(
        context: Context,
        kind: String,
        title: String,
        body: String,
        target: String = "",
        key: String = "",
        nowMillis: Long = System.currentTimeMillis()
    ) {
        if (kind in HIDDEN_KINDS) return
        try {
            synchronized(LOCK) {
                val kept = prune(load(context), nowMillis).filter { key.isEmpty() || it.key != key }
                var id = nowMillis
                while (kept.any { it.id == id }) id++
                val all = (kept + AppNotification(id, key, kind, title, body, target, unread = true))
                    .sortedByDescending { it.id }
                    .take(MAX_ITEMS)
                save(context, all)
            }
        } catch (_: Exception) {
        }
    }

    /** Newest first. */
    fun items(context: Context): List<AppNotification> = synchronized(LOCK) {
        prune(load(context), System.currentTimeMillis()).sortedByDescending { it.id }
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

    private fun prune(items: List<AppNotification>, nowMillis: Long): List<AppNotification> =
        items.filter { nowMillis - it.id <= MAX_AGE_MILLIS && it.kind !in HIDDEN_KINDS }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun save(context: Context, items: List<AppNotification>) {
        try {
            val array = JSONArray()
            items.forEach {
                array.put(
                    JSONObject()
                        .put("id", it.id)
                        .put("key", it.key)
                        .put("kind", it.kind)
                        .put("title", it.title)
                        .put("body", it.body)
                        .put("target", it.target)
                        .put("unread", it.unread)
                )
            }
            prefs(context).edit().putString(KEY_ITEMS, array.toString()).apply()
        } catch (_: Exception) {
        }
    }

    private fun load(context: Context): List<AppNotification> = try {
        val text = prefs(context).getString(KEY_ITEMS, null)
        if (text.isNullOrBlank()) {
            emptyList()
        } else {
            val array = JSONArray(text)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                AppNotification(
                    id = o.getLong("id"),
                    key = o.optString("key"),
                    kind = o.optString("kind"),
                    title = o.optString("title"),
                    body = o.optString("body"),
                    target = o.optString("target"),
                    unread = o.optBoolean("unread", false)
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}
