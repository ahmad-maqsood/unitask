package com.example.util.timer

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * One finished study session: a run of the timer from its first Start until it was
 * reset or (for a countdown) completed. Pauses inside the run are not counted.
 *
 * [startedAtMillis] doubles as the session's identity, since two sessions can never start
 * in the same millisecond.
 */
data class TimerSession(
    val startedAtMillis: Long,
    val durationMillis: Long,
    val mode: TimerMode,
    /** The countdown goal, or null for a stopwatch session. */
    val plannedMillis: Long?,
    /** True when a countdown ran all the way to zero. */
    val completed: Boolean
)

data class DayTotal(val date: LocalDate, val millis: Long)

/** Pure helpers for turning a list of sessions into the numbers the history screen shows. */
object TimerHistoryLogic {

    /** Runs shorter than this are treated as accidental taps and are not saved. */
    const val MIN_SESSION_MILLIS = 10_000L

    /** Only the newest sessions are kept, so the saved list never grows without limit. */
    const val MAX_STORED_SESSIONS = 500

    fun dayOf(millis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun totalMillis(sessions: List<TimerSession>): Long = sessions.sumOf { it.durationMillis }

    fun totalForDay(sessions: List<TimerSession>, day: LocalDate, zone: ZoneId): Long =
        sessions
            .filter { dayOf(it.startedAtMillis, zone) == day }
            .sumOf { it.durationMillis }

    /** Totals for the [count] days ending on [today], oldest first. Days without study get 0. */
    fun lastDays(
        sessions: List<TimerSession>,
        today: LocalDate,
        zone: ZoneId,
        count: Int = 7
    ): List<DayTotal> {
        val totals = HashMap<LocalDate, Long>()
        for (s in sessions) {
            val day = dayOf(s.startedAtMillis, zone)
            totals[day] = (totals[day] ?: 0L) + s.durationMillis
        }
        return (count - 1 downTo 0).map { offset ->
            val day = today.minusDays(offset.toLong())
            DayTotal(day, totals[day] ?: 0L)
        }
    }

    /** Sessions grouped by the day they started, newest day first and newest session first. */
    fun groupByDay(
        sessions: List<TimerSession>,
        zone: ZoneId
    ): List<Pair<LocalDate, List<TimerSession>>> =
        sessions
            .sortedByDescending { it.startedAtMillis }
            .groupBy { dayOf(it.startedAtMillis, zone) }
            .toList()
            .sortedByDescending { it.first }

    /** "45 s", "25 min", "1 h" or "1 h 25 min". */
    fun formatTotal(millis: Long): String {
        val nonNegative = maxOf(0L, millis)
        if (nonNegative in 1 until 60_000L) return "${nonNegative / 1000L} s"
        return TimerLogic.formatDuration((nonNegative / 60_000L).toInt())
    }

    /** A short form for the narrow chart bars: "<1m", "25m", "1h" or "1h25m". Empty for zero. */
    fun formatCompact(millis: Long): String {
        if (millis <= 0L) return ""
        val totalMinutes = (millis / 60_000L).toInt()
        if (totalMinutes == 0) return "<1m"
        val (h, m) = TimerLogic.splitHoursAndMinutes(totalMinutes)
        return when {
            h > 0 && m > 0 -> "${h}h${m}m"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
    }
}
