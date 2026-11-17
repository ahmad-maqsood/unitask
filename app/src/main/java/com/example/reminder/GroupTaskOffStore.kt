package com.example.reminder

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Group tasks the person has turned off on THIS phone.
 *
 * A turned-off group task stays in the group and on the Deadlines tab, but it has no reminders on
 * this phone, no snooze and it does not count as overdue. Nothing changes for the other members:
 * the choice is personal, like marking a task done, and it is never sent to the server. That is why
 * every member can use it, not only the ones who may edit the task.
 *
 * It lives in SharedPreferences, not in the database, because group tasks are not database rows
 * (same reason as [SnoozeStore]). A key is "groupId|taskId".
 */
object GroupTaskOffStore {

    private const val PREFS = "group_tasks_off"
    private const val KEY_SET = "keys"

    private val state = MutableStateFlow<Set<String>>(emptySet())

    @Volatile
    private var loaded = false

    fun keyOf(groupId: String, taskId: String): String = "$groupId|$taskId"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** A copy: the set that SharedPreferences returns must never be changed. */
    private fun read(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_SET, null)?.toSet().orEmpty()

    private fun write(context: Context, keys: Set<String>) {
        prefs(context).edit { putStringSet(KEY_SET, HashSet(keys)) }
        state.value = keys
        loaded = true
    }

    /** Every turned-off key right now, for code that is not a UI. */
    fun keys(context: Context): Set<String> = read(context)

    /** The turned-off keys as a flow. Collect it in the UI so cards update at once. */
    fun keysFlow(context: Context): StateFlow<Set<String>> {
        if (!loaded) {
            state.value = read(context)
            loaded = true
        }
        return state
    }

    fun isOff(context: Context, groupId: String, taskId: String): Boolean =
        keyOf(groupId, taskId) in read(context)

    /**
     * Turns a group task off or on. Off also removes its alarms and any snooze right away.
     * On: the caller must run the group processor again so the alarms come back (the main screen
     * does that by itself, because it follows [keysFlow]).
     */
    fun setOff(context: Context, groupId: String, taskId: String, off: Boolean) {
        val app = context.applicationContext
        synchronized(this) {
            val updated = read(app).toMutableSet()
            val changed = if (off) updated.add(keyOf(groupId, taskId)) else updated.remove(keyOf(groupId, taskId))
            if (changed) write(app, updated)
        }
        if (off) GroupReminderScheduler(app).cancel(groupId, taskId)
    }

    /** Forgets turned-off tasks of one group that no longer exist (deleted by a member). */
    fun retainTasks(context: Context, groupId: String, existingTaskIds: Set<String>) {
        val app = context.applicationContext
        synchronized(this) {
            val current = read(app)
            val updated = current.filterNot { key ->
                key.substringBefore('|') == groupId && key.substringAfter('|') !in existingTaskIds
            }.toSet()
            if (updated.size != current.size) write(app, updated)
        }
    }

    /** Forgets everything about groups the person is no longer in. */
    fun retainGroups(context: Context, keepGroupIds: Set<String>) {
        val app = context.applicationContext
        synchronized(this) {
            val current = read(app)
            val updated = current.filter { it.substringBefore('|') in keepGroupIds }.toSet()
            if (updated.size != current.size) write(app, updated)
        }
    }

    /** Signed out: forget everything. */
    fun clearAll(context: Context) {
        val app = context.applicationContext
        synchronized(this) {
            if (read(app).isNotEmpty()) write(app, emptySet())
        }
    }
}
