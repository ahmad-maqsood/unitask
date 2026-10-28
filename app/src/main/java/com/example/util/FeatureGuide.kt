package com.example.util

import android.content.Context
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** One line in the "App features" list. Rows live in the Supabase table app_features. */
@Serializable
data class AppFeature(
    val title: String = "",
    val description: String = "",
    val icon: String = "star",
    @SerialName("sort_order") val sortOrder: Int = 0
)

/**
 * Loads the feature list. Order of trust: fresh from Supabase, else the copy saved on the phone,
 * else the short built-in list (so the screen is never empty, even offline on first run).
 */
object FeatureGuide {
    private const val PREFS = "feature_guide"
    private const val KEY = "cached_json"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val builtIn = listOf(
        AppFeature("Deadlines", "Track assignments and tasks with due dates, priorities and reminders.", "assignment", 10),
        AppFeature("Timetable", "Your weekly class schedule.", "schedule", 20),
        AppFeature("Courses", "Keep your courses and lecture slots in one place.", "school", 30),
        AppFeature("Groups", "Share tasks and chat with your classmates.", "groups", 40),
        AppFeature("Cloud backup", "Sign in with Google to back up your data.", "backup", 50)
    )

    /** What to show instantly: the saved copy if there is one, else the built-in list. */
    fun cachedOrDefault(context: Context): List<AppFeature> {
        return try {
            val text = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            val saved = if (text != null) json.decodeFromString<List<AppFeature>>(text) else emptyList()
            saved.ifEmpty { builtIn }
        } catch (_: Exception) {
            builtIn
        }
    }

    /** Fetches the latest list and saves it. Returns null if offline, slow, or the table is empty. */
    suspend fun refresh(context: Context): List<AppFeature>? {
        return try {
            val rows = withTimeout(8_000L) {
                SupabaseProvider.client.postgrest["app_features"]
                    .select {
                        filter { eq("enabled", true) }
                        order("sort_order", Order.ASCENDING)
                    }
                    .decodeList<AppFeature>()
            }
            if (rows.isEmpty()) return null
            try {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY, json.encodeToString(rows)).apply()
            } catch (_: Exception) {
            }
            rows
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
}