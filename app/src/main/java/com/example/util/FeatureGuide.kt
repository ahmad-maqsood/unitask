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
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("how_to") val howTo: String = ""
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
        AppFeature(
            "Deadlines & Urgency", "Track assignments and tasks grouped by urgency: Overdue, Today, Tomorrow, Upcoming, and Completed.", "assignment", 10,
            "1. Open the Deadlines tab.
2. Tap + Add Assignment.
3. Fill in the title, course, due date, time, priority and reminders.
4. Tasks group automatically by urgency when sorted by date."
        ),
        AppFeature(
            "Calendar View", "Toggle between list view and a monthly calendar view to visualize deadlines.", "calendar", 15,
            "1. Open the Deadlines tab.
2. Tap the List/Calendar view toggle icon in the top bar.
3. Navigate months with < and >.
4. Tap any date with a dot to see the deadlines due on that day."
        ),
        AppFeature(
            "Subtasks & Checklists", "Break tasks into manageable checklist items with progress tracking.", "assignment", 20,
            "1. In the task dialog, tap + Add Checklist Item.
2. Enter items using '- [ ] item' markdown format.
3. Save to see a subtask counter chip on the task card."
        ),
        AppFeature(
            "Batch Operations", "Select multiple tasks to mark them as done or delete them together.", "assignment", 25,
            "1. Open the Deadlines tab.
2. Tap the checkbox icon in the top bar to enter Batch Mode.
3. Tap individual tasks or 'Select All'.
4. Use the bottom bar to mark done or delete."
        ),
        AppFeature(
            "Complete from Notifications", "Mark tasks as done directly from lock screen or tray notifications.", "notifications", 30,
            "1. When a deadline reminder notification arrives, look at the action buttons.
2. Tap 'Mark as Done' on the notification without opening the app."
        ),
        AppFeature(
            "Timetable & Schedule", "Your weekly university lecture schedule organized by day.", "schedule", 40,
            "1. Open the Timetable tab.
2. Select a day of the week.
3. Tap + Add Class and enter course, time, classroom, and instructor."
        ),
        AppFeature(
            "Bi-Weekly Timetable (Week A/B)", "Support for classes that alternate weekly.", "schedule", 45,
            "1. When adding a class, select recurrence: Every Week, Week A, or Week B.
2. Use the filter chips below the days (All, Week A, Week B) to view your active week."
        ),
        AppFeature(
            "Break & Free Time Indicators", "Visual indicators show gaps between classes to help plan study sessions.", "schedule", 50,
            "1. Open the Timetable tab on days with multiple classes.
2. Breaks of 15+ minutes automatically show a coffee pill (e.g. '☕ 1h 30m free until next class')."
        ),
        AppFeature(
            "Export Timetable (.ics)", "Export your schedule directly into Google Calendar or Outlook.", "schedule", 55,
            "1. Open the Timetable tab.
2. Tap 'Export (.ics)' in the top bar.
3. Choose your calendar app from the share sheet to import all recurring lectures."
        ),
        AppFeature(
            "Timetable Conflict Alerts", "Automatic warnings before saving overlapping classes.", "schedule", 60,
            "1. If an added or edited lecture overlaps an existing class on that day, UniTask warns you.
2. Review the conflict warning dialog to avoid double booking."
        ),
        AppFeature(
            "Courses", "Keep your subjects, instructors, and colors in one place.", "school", 70,
            "1. Open the Courses tab.
2. Tap Add Course and enter code, name, and instructor.
3. All tasks and lecture slots inherit course color tags."
        ),
        AppFeature(
            "Study Groups & QR Codes", "Collaborate on shared tasks and chat with classmates.", "groups", 80,
            "1. Sign in with Google and open the Groups tab.
2. Tap the QR icon in group details to show your invite QR code and link.
3. To join, tap 'Join Group' and scan a QR screenshot or paste an invite link."
        ),
        AppFeature(
            "Group Settings & Ownership", "Edit group name, regenerate codes, or transfer ownership.", "groups", 85,
            "1. As owner, tap the Settings icon to edit name/handle or regenerate invite code.
2. In the Members tab, tap the three dots next to a member to transfer ownership."
        ),
        AppFeature(
            "Group Chat & Search", "Real-time messaging with search, replies, and date headers.", "chat", 100,
            "1. Open the Chat tab in any group.
2. Tap the search icon to filter messages by text or sender.
3. A sticky date header floats at the top while scrolling."
        ),
        AppFeature(
            "Offline Outbox & Chat History", "Send messages offline and scroll back through chat history.", "chat", 105,
            "1. Messages sent offline are queued in an outbox and sent automatically when reconnected.
2. Scroll to the top and tap 'Load earlier messages' to view older chat history."
        ),
        AppFeature(
            "Cloud Backup & Multi-Account", "Sign in with Google to back up your data across devices.", "backup", 120,
            "1. Open Settings -> Cloud Backup.
2. Sign in with Google to sync courses, timetable, and tasks.
3. Switching accounts cleanly isolates and clears stale local records."
        ),
        AppFeature(
            "Data Export & GDPR Purge", "Export JSON backup or permanently purge your cloud account.", "backup", 125,
            "1. Open Settings -> Cloud Backup.
2. Tap 'Export My Data (JSON)' to share a full backup.
3. Tap 'Delete Account & Cloud Data' to permanently erase cloud records and reset."
        ),
        AppFeature(
            "Announcements Bell", "Official university updates and exam tips, fully offline-ready.", "notifications", 130,
            "1. Tap the notification bell in the top bar to read official announcements.
2. All data is cached locally in encrypted Room database for 100% offline access."
        )
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