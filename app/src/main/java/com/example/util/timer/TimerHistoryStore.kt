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

    /**
     * Merges sessions into the saved list, for example from a backup. A session whose start time
     * is already saved is left alone. Returns how many sessions were really added.
     */
    fun addAll(sessions: List<TimerSession>): Int
    fun delete(startedAtMillis: Long)
    fun clear()
}

/** Used when no history is wanted, for example in tests that do not care about it. */
object NoTimerHistory : TimerHistoryHook {
    override val sessions: StateFlow<List<TimerSession>> =
        MutableStateFlow<List<TimerSession>>(emptyList()).asStateFlow()

    override fun add(session: TimerSession) {}
    override fun addAll(sessions: List<TimerSession>): Int = 0
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
    override fun addAll(sessions: List<TimerSession>): Int {
        val known = _sessions.value.map { it.startedAtMillis }.toHashSet()
        val fresh = sessions
            .filter { it.durationMillis > 0L && it.startedAtMillis > 0L && known.add(it.startedAtMillis) }
        if (fresh.isEmpty()) return 0
        val updated = (_sessions.value + fresh)
            .sortedByDescending { it.startedAtMillis }
            .take(TimerHistoryLogic.MAX_STORED_SESSIONS)
        save(updated)
        _sessions.value = updated
        // Sessions pushed out by the size limit were not really added.
        return fresh.count { f -> updated.any { it.startedAtMillis == f.startedAtMillis } }
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
                    sessionFromJson(array.getJSONObject(i))
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
            sessions.forEach { array.put(sessionToJson(it)) }
            prefs.edit().putString(KEY_SESSIONS, array.toString()).apply()
        } catch (_: Exception) {
            // Never throw on preference save failures.
        }
    }

    companion object {
        /** Also used by the backup, so the saved and the backed-up form never drift apart. */
        fun sessionToJson(s: TimerSession): JSONObject = JSONObject().apply {
            put(F_START, s.startedAtMillis)
            put(F_DURATION, s.durationMillis)
            put(F_MODE, s.mode.name)
            if (s.plannedMillis != null) put(F_PLANNED, s.plannedMillis)
            put(F_COMPLETED, s.completed)
        }

        fun sessionFromJson(o: JSONObject): TimerSession? {
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

        @Volatile
        private var INSTANCE: TimerHistoryStore? = null

        /** One shared store, so the timer screen and a backup restore see the same list. */
        fun get(context: Context): TimerHistoryStore {
            val appContext = context.applicationContext
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TimerHistoryStore(appContext).also { INSTANCE = it }
            }
        }

        internal fun setInstanceForTests(store: TimerHistoryStore?) {
            synchronized(this) {
                INSTANCE = store
            }
        }

        private const val PREFS_NAME = "tools_timer_history"
        private const val KEY_SESSIONS = "sessions"
        private const val F_START = "s"
        private const val F_DURATION = "d"
        private const val F_MODE = "m"
        private const val F_PLANNED = "p"
        private const val F_COMPLETED = "c"
    }
}
