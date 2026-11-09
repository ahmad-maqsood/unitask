package com.example.util.timer

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Where the timer controller saves finished sessions. */
interface TimerHistoryHook {
    /** Newest session first. */
    val sessions: StateFlow<List<TimerSession>>
    fun add(session: TimerSession)
    fun delete(startedAtMillis: Long)
    fun clear()
}

/** Used when no history is wanted, for example in tests that do not care about it. */
object NoTimerHistory : TimerHistoryHook {
    override val sessions: StateFlow<List<TimerSession>> =
        MutableStateFlow<List<TimerSession>>(emptyList()).asStateFlow()

    override fun add(session: TimerSession) {}
    override fun delete(startedAtMillis: Long) {}
    override fun clear() {}
}

/**
 * Saves the study history in SharedPreferences as a small JSON list, next to the timer's own
 * saved state. Keeping it out of the Room database means no database migration is needed.
 */
class TimerHistoryStore(context: Context) : TimerHistoryHook {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _sessions = MutableStateFlow(load())
    override val sessions: StateFlow<List<TimerSession>> = _sessions.asStateFlow()

    @Synchronized
    override fun add(session: TimerSession) {
        val updated = (listOf(session) + _sessions.value.filter { it.startedAtMillis != session.startedAtMillis })
            .sortedByDescending { it.startedAtMillis }
            .take(TimerHistoryLogic.MAX_STORED_SESSIONS)
        save(updated)
        _sessions.value = updated
    }

    @Synchronized
    override fun delete(startedAtMillis: Long) {
        val updated = _sessions.value.filter { it.startedAtMillis != startedAtMillis }
        if (updated.size == _sessions.value.size) return
        save(updated)
        _sessions.value = updated
    }

    @Synchronized
    override fun clear() {
        save(emptyList())
        _sessions.value = emptyList()
    }

    private fun load(): List<TimerSession> {
        return try {
            val raw = prefs.getString(KEY_SESSIONS, null) ?: return emptyList()
            val array = JSONArray(raw)
            val result = ArrayList<TimerSession>(array.length())
            for (i in 0 until array.length()) {
                // One damaged entry must not hide the rest of the history.
                val session = try {
                    fromJson(array.getJSONObject(i))
                } catch (_: Exception) {
                    null
                }
                if (session != null) result.add(session)
            }
            result.sortedByDescending { it.startedAtMillis }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun save(sessions: List<TimerSession>) {
        try {
            val array = JSONArray()
            sessions.forEach { array.put(toJson(it)) }
            prefs.edit().putString(KEY_SESSIONS, array.toString()).apply()
        } catch (_: Exception) {
            // Never throw on preference save failures.
        }
    }

    private fun toJson(s: TimerSession): JSONObject = JSONObject().apply {
        put(F_START, s.startedAtMillis)
        put(F_DURATION, s.durationMillis)
        put(F_MODE, s.mode.name)
        if (s.plannedMillis != null) put(F_PLANNED, s.plannedMillis)
        put(F_COMPLETED, s.completed)
    }

    private fun fromJson(o: JSONObject): TimerSession? {
        val start = o.getLong(F_START)
        val duration = o.getLong(F_DURATION)
        if (start <= 0L || duration <= 0L) return null
        val mode = try {
            TimerMode.valueOf(o.getString(F_MODE))
        } catch (_: IllegalArgumentException) {
            TimerMode.STOPWATCH
        }
        return TimerSession(
            startedAtMillis = start,
            durationMillis = duration,
            mode = mode,
            plannedMillis = if (o.has(F_PLANNED)) o.getLong(F_PLANNED) else null,
            completed = o.optBoolean(F_COMPLETED, false)
        )
    }

    companion object {
        private const val PREFS_NAME = "tools_timer_history"
        private const val KEY_SESSIONS = "sessions"
        private const val F_START = "s"
        private const val F_DURATION = "d"
        private const val F_MODE = "m"
        private const val F_PLANNED = "p"
        private const val F_COMPLETED = "c"
    }
}
