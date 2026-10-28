package com.example.util

import android.content.Context
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable

/** One message sent from Supabase (table: announcements). */
@Serializable
data class Announcement(
    val id: Long = 0,
    val title: String = "",
    val body: String = ""
)

object Announcements {
    private const val PREFS = "announcements"
    private const val KEY_SEEN = "seen_ids"

    /** Messages for this person that they have not dismissed yet, newest first. Empty if offline. */
    suspend fun fetchUnseen(context: Context): List<Announcement> {
        return try {
            val rows = withTimeout(8_000L) {
                SupabaseProvider.client.postgrest["announcements"]
                    .select {
                        order("created_at", Order.DESCENDING)
                        limit(10)
                    }
                    .decodeList<Announcement>()
            }
            val seen = seenIds(context)
            rows.filter { it.id.toString() !in seen }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Remember these messages as read, so they are not shown again. */
    fun markSeen(context: Context, items: List<Announcement>) {
        try {
            val all = seenIds(context) + items.map { it.id.toString() }
            // Keep the list from growing forever.
            val trimmed = all.toList().takeLast(200).toSet()
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putStringSet(KEY_SEEN, trimmed).apply()
        } catch (_: Exception) {
        }
    }

    private fun seenIds(context: Context): Set<String> = try {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY_SEEN, emptySet()) ?: emptySet()
    } catch (_: Exception) {
        emptySet()
    }
}