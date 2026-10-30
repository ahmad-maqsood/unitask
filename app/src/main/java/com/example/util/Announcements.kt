package com.example.util

import android.content.Context
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** One message sent from Supabase (table: announcements). */
@Serializable
data class Announcement(
    val id: Long = 0,
    val title: String = "",
    val body: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null
) {
    /** When it was posted, in milliseconds, or null if unknown. */
    val createdAtMillis: Long? get() = Announcements.parseTimestamp(createdAt)

    /** When it stops being shown, in milliseconds, or null if it never expires. */
    val expiresAtMillis: Long? get() = Announcements.parseTimestamp(expiresAt)
}

/**
 * Announcements from Supabase, kept on the phone in four small lists:
 *  - cache:    the last list the server gave us (this is what the bell shows, even offline)
 *  - seen:     already shown in the pop-up (so the pop-up appears only once)
 *  - notified: already shown as a system notification by the background check
 *  - removed:  the person pressed X in the bell
 */
object Announcements {
    private const val PREFS = "announcements"
    private const val KEY_SEEN = "seen_ids"
    private const val KEY_NOTIFIED = "notified_ids"
    private const val KEY_REMOVED = "removed_ids"
    private const val KEY_CACHE = "cache_json"
    private const val MAX_IDS = 200

    private val LOCK = Any()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Downloads the messages that are visible to this person right now and keeps them for the bell.
     * Returns true if the server was reached. When it returns false, the old saved list stays.
     */
    suspend fun refresh(context: Context): Boolean {
        return try {
            val rows = withTimeout(8_000L) {
                SupabaseProvider.client.postgrest["announcements"]
                    .select {
                        order("created_at", Order.DESCENDING)
                        limit(20)
                    }
                    .decodeList<Announcement>()
            }
            synchronized(LOCK) { saveCache(context, rows) }
            true
        } catch (e: TimeoutCancellationException) {
            false
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    /** What the bell shows: saved, not expired, not removed, newest first. */
    fun bellItems(context: Context): List<Announcement> = synchronized(LOCK) {
        val removed = ids(context, KEY_REMOVED)
        val now = System.currentTimeMillis()
        loadCache(context)
            .filter { it.id.toString() !in removed }
            .filter { val end = it.expiresAtMillis; end == null || end > now }
            .sortedWith(
                compareByDescending<Announcement> { it.createdAtMillis ?: 0L }
                    .thenByDescending { it.id }
            )
    }

    /** Bell items that have not been shown in the pop-up yet. */
    fun unseen(context: Context): List<Announcement> {
        val seen = synchronized(LOCK) { ids(context, KEY_SEEN) }
        return bellItems(context).filter { it.id.toString() !in seen }
    }

    /** Unseen items that the background check has not made a notification for yet. */
    fun toNotify(context: Context): List<Announcement> {
        val notified = synchronized(LOCK) { ids(context, KEY_NOTIFIED) }
        return unseen(context).filter { it.id.toString() !in notified }
    }

    /** Remember these messages as shown in the pop-up, so they are not popped up again. */
    fun markSeen(context: Context, items: List<Announcement>) =
        addIds(context, KEY_SEEN, items.map { it.id.toString() })

    /** Remember these messages as already notified. */
    fun markNotified(context: Context, items: List<Announcement>) =
        addIds(context, KEY_NOTIFIED, items.map { it.id.toString() })

    /** The person pressed X: take it out of the bell, and never pop it up either. */
    fun remove(context: Context, items: List<Announcement>) {
        val list = items.map { it.id.toString() }
        addIds(context, KEY_REMOVED, list)
        addIds(context, KEY_SEEN, list)
        addIds(context, KEY_NOTIFIED, list)
    }

    /** Reads times like 2026-10-30T12:34:56.123456+00:00. Works on every Android version we support. */
    fun parseTimestamp(value: String?): Long? {
        val text = value?.trim()?.replace(' ', 'T')
        if (text.isNullOrEmpty() || text.length < 19) return null
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            format.timeZone = TimeZone.getTimeZone("UTC")
            val base = format.parse(text.substring(0, 19))?.time ?: return null
            var i = 19
            // Skip the fraction of a second.
            if (i < text.length && text[i] == '.') {
                i++
                while (i < text.length && text[i].isDigit()) i++
            }
            // "Z" means UTC. "+05:00", "-0500" or "+05" mean an offset from UTC.
            var offsetMillis = 0L
            if (i < text.length && (text[i] == '+' || text[i] == '-')) {
                val sign = if (text[i] == '-') -1L else 1L
                val digits = text.substring(i + 1).replace(":", "")
                val hours = digits.take(2).toLongOrNull() ?: 0L
                val minutes = digits.drop(2).take(2).toLongOrNull() ?: 0L
                offsetMillis = sign * (hours * 60L + minutes) * 60_000L
            }
            base - offsetMillis
        } catch (_: Exception) {
            null
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun ids(context: Context, key: String): Set<String> = try {
        prefs(context).getStringSet(key, emptySet()) ?: emptySet()
    } catch (_: Exception) {
        emptySet()
    }

    private fun addIds(context: Context, key: String, newIds: Collection<String>) {
        synchronized(LOCK) {
            try {
                val all = ids(context, key) + newIds
                // Keep the list from growing forever.
                val trimmed = if (all.size > MAX_IDS) all.toList().takeLast(MAX_IDS).toSet() else all
                prefs(context).edit().putStringSet(key, trimmed).apply()
            } catch (_: Exception) {
            }
        }
    }

    private fun saveCache(context: Context, rows: List<Announcement>) {
        try {
            val text = json.encodeToString(ListSerializer(Announcement.serializer()), rows)
            prefs(context).edit().putString(KEY_CACHE, text).apply()
        } catch (_: Exception) {
        }
    }

    private fun loadCache(context: Context): List<Announcement> = try {
        val text = prefs(context).getString(KEY_CACHE, null)
        if (text.isNullOrBlank()) emptyList()
        else json.decodeFromString(ListSerializer(Announcement.serializer()), text)
    } catch (_: Exception) {
        emptyList()
    }
}