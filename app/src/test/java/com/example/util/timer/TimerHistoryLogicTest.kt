package com.example.util.timer

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class TimerHistoryLogicTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val today: LocalDate = LocalDate.of(2026, 11, 9)

    private fun at(day: LocalDate, hour: Int): Long =
        day.atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun session(day: LocalDate, hour: Int, minutes: Long) = TimerSession(
        startedAtMillis = at(day, hour),
        durationMillis = minutes * 60_000L,
        mode = TimerMode.STOPWATCH,
        plannedMillis = null,
        completed = false
    )

    @Test
    fun totalsAddUpPerDay() {
        val sessions = listOf(
            session(today, 9, 25),
            session(today, 14, 35),
            session(today.minusDays(1), 10, 50)
        )
        assertEquals(60 * 60_000L, TimerHistoryLogic.totalForDay(sessions, today, zone))
        assertEquals(50 * 60_000L, TimerHistoryLogic.totalForDay(sessions, today.minusDays(1), zone))
        assertEquals(0L, TimerHistoryLogic.totalForDay(sessions, today.minusDays(2), zone))
        assertEquals(110 * 60_000L, TimerHistoryLogic.totalMillis(sessions))
    }

    @Test
    fun lastDaysReturnsSevenDaysOldestFirstWithZeroForEmptyDays() {
        val sessions = listOf(
            session(today, 9, 30),
            session(today.minusDays(3), 9, 20),
            session(today.minusDays(10), 9, 99) // outside the window
        )
        val days = TimerHistoryLogic.lastDays(sessions, today, zone)

        assertEquals(7, days.size)
        assertEquals(today.minusDays(6), days.first().date)
        assertEquals(today, days.last().date)
        assertEquals(30 * 60_000L, days.last().millis)
        assertEquals(20 * 60_000L, days[3].millis)
        assertEquals(0L, days[0].millis)
        assertEquals(50 * 60_000L, days.sumOf { it.millis })
    }

    @Test
    fun groupByDayListsNewestDayFirstAndNewestSessionFirst() {
        val a = session(today.minusDays(1), 8, 10)
        val b = session(today, 9, 10)
        val c = session(today, 15, 10)
        val groups = TimerHistoryLogic.groupByDay(listOf(a, b, c), zone)

        assertEquals(listOf(today, today.minusDays(1)), groups.map { it.first })
        assertEquals(listOf(c, b), groups[0].second)
        assertEquals(listOf(a), groups[1].second)
    }

    @Test
    fun sessionNearMidnightBelongsToTheDayItStarted() {
        val lateNight = TimerSession(
            startedAtMillis = today.atTime(23, 30).toInstant(ZoneOffset.UTC).toEpochMilli(),
            durationMillis = 90 * 60_000L,
            mode = TimerMode.STOPWATCH,
            plannedMillis = null,
            completed = false
        )
        assertEquals(90 * 60_000L, TimerHistoryLogic.totalForDay(listOf(lateNight), today, zone))
        assertEquals(0L, TimerHistoryLogic.totalForDay(listOf(lateNight), today.plusDays(1), zone))
    }

    @Test
    fun formatTotalReadsNaturally() {
        assertEquals("0 min", TimerHistoryLogic.formatTotal(0L))
        assertEquals("45 s", TimerHistoryLogic.formatTotal(45_000L))
        assertEquals("1 min", TimerHistoryLogic.formatTotal(60_000L))
        assertEquals("25 min", TimerHistoryLogic.formatTotal(25 * 60_000L))
        assertEquals("1 h", TimerHistoryLogic.formatTotal(60 * 60_000L))
        assertEquals("1 h 25 min", TimerHistoryLogic.formatTotal(85 * 60_000L))
    }

    @Test
    fun formatCompactFitsNarrowChartBars() {
        assertEquals("", TimerHistoryLogic.formatCompact(0L))
        assertEquals("<1m", TimerHistoryLogic.formatCompact(30_000L))
        assertEquals("25m", TimerHistoryLogic.formatCompact(25 * 60_000L))
        assertEquals("1h", TimerHistoryLogic.formatCompact(60 * 60_000L))
        assertEquals("1h25m", TimerHistoryLogic.formatCompact(85 * 60_000L))
    }
}
