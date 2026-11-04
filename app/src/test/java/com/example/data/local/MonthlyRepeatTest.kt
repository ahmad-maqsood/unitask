package com.example.data.local

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class MonthlyRepeatTest {

    private fun millis(year: Int, month1: Int, day: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month1 - 1, day, 12, 0, 0)
        }.timeInMillis

    private fun ymd(millis: Long): Triple<Int, Int, Int> {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        return Triple(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    /** Moves a task the way the app does: resolve the anchor, step, remember the anchor. */
    private fun move(rule: RepeatRule, due: Long, stored: Int): Pair<Long, Int> {
        val anchor = resolveAnchorDay(stored, due)
        val next = nextOccurrenceMillis(rule, due, due, anchor)
        return next to anchor
    }

    @Test
    fun jan31GoesToFeb28ThenBackToMar31() {
        val feb = nextOccurrenceMillis(RepeatRule.MONTHLY, millis(2027, 1, 31), millis(2027, 1, 31), 31)
        assertEquals(Triple(2027, 2, 28), ymd(feb))

        val mar = nextOccurrenceMillis(RepeatRule.MONTHLY, feb, feb, 31)
        assertEquals(Triple(2027, 3, 31), ymd(mar))

        val apr = nextOccurrenceMillis(RepeatRule.MONTHLY, mar, mar, 31)
        assertEquals(Triple(2027, 4, 30), ymd(apr))
    }

    @Test
    fun leapYearFebruaryUses29() {
        val feb = nextOccurrenceMillis(RepeatRule.MONTHLY, millis(2028, 1, 31), millis(2028, 1, 31), 31)
        assertEquals(Triple(2028, 2, 29), ymd(feb))
    }

    @Test
    fun withoutAnchorTheCurrentDayIsUsed() {
        val next = nextOccurrenceMillis(RepeatRule.MONTHLY, millis(2027, 1, 30), millis(2027, 1, 30))
        assertEquals(Triple(2027, 2, 28), ymd(next))
    }

    @Test
    fun normalDayIsUnchanged() {
        val next = nextOccurrenceMillis(RepeatRule.MONTHLY, millis(2027, 5, 15), millis(2027, 5, 15), 15)
        assertEquals(Triple(2027, 6, 15), ymd(next))
    }

    @Test
    fun catchUpAcrossSeveralMonthsKeepsTheAnchor() {
        // App not opened for months: Jan 31 task, "now" is mid-April.
        val next = nextOccurrenceMillis(RepeatRule.MONTHLY, millis(2027, 1, 31), millis(2027, 4, 15), 31)
        assertEquals(Triple(2027, 4, 30), ymd(next))
    }

    @Test
    fun feb29YearlyGoesToFeb28ThenBackToFeb29() {
        val y2029 = nextOccurrenceMillis(RepeatRule.YEARLY, millis(2028, 2, 29), millis(2028, 2, 29), 29)
        assertEquals(Triple(2029, 2, 28), ymd(y2029))

        val y2030 = nextOccurrenceMillis(RepeatRule.YEARLY, y2029, y2029, 29)
        assertEquals(Triple(2030, 2, 28), ymd(y2030))

        val y2032 = nextOccurrenceMillis(RepeatRule.YEARLY, millis(2031, 2, 28), millis(2031, 2, 28), 29)
        assertEquals(Triple(2032, 2, 29), ymd(y2032))
    }

    @Test
    fun normalYearlyDateIsUnchanged() {
        val next = nextOccurrenceMillis(RepeatRule.YEARLY, millis(2027, 6, 10), millis(2027, 6, 10), 10)
        assertEquals(Triple(2028, 6, 10), ymd(next))
    }

    @Test
    fun anchorIsKeptWhileTheDueDateFitsIt() {
        assertEquals(31, resolveAnchorDay(31, millis(2027, 2, 28)))
        assertEquals(31, resolveAnchorDay(31, millis(2027, 4, 30)))
        assertEquals(30, resolveAnchorDay(30, millis(2027, 2, 28)))
        assertEquals(29, resolveAnchorDay(29, millis(2029, 2, 28)))
    }

    @Test
    fun anchorIsIgnoredWhenTheDateWasChangedByHand() {
        // Saved day 31, but the user moved the task to the 15th or the 30th of a 31-day month.
        assertEquals(15, resolveAnchorDay(31, millis(2027, 3, 15)))
        assertEquals(30, resolveAnchorDay(31, millis(2027, 1, 30)))
    }

    @Test
    fun missingAnchorUsesTheDueDay() {
        assertEquals(28, resolveAnchorDay(0, millis(2027, 2, 28)))
        assertEquals(31, resolveAnchorDay(0, millis(2027, 1, 31)))
        assertEquals(12, resolveAnchorDay(99, millis(2027, 1, 12)))
    }

    @Test
    fun monthlyChainFromJan31NeverDrifts() {
        var due = millis(2027, 1, 31)
        var stored = 0
        val seen = mutableListOf<Triple<Int, Int, Int>>()
        repeat(13) {
            val (next, anchor) = move(RepeatRule.MONTHLY, due, stored)
            due = next
            stored = anchor
            seen.add(ymd(due))
        }
        assertEquals(
            listOf(
                Triple(2027, 2, 28), Triple(2027, 3, 31), Triple(2027, 4, 30), Triple(2027, 5, 31),
                Triple(2027, 6, 30), Triple(2027, 7, 31), Triple(2027, 8, 31), Triple(2027, 9, 30),
                Triple(2027, 10, 31), Triple(2027, 11, 30), Triple(2027, 12, 31), Triple(2028, 1, 31),
                Triple(2028, 2, 29)
            ),
            seen
        )
    }

    @Test
    fun yearlyChainFromFeb29ReturnsInTheNextLeapYear() {
        var due = millis(2028, 2, 29)
        var stored = 0
        val seen = mutableListOf<Triple<Int, Int, Int>>()
        repeat(5) {
            val (next, anchor) = move(RepeatRule.YEARLY, due, stored)
            due = next
            stored = anchor
            seen.add(ymd(due))
        }
        assertEquals(
            listOf(
                Triple(2029, 2, 28), Triple(2030, 2, 28), Triple(2031, 2, 28),
                Triple(2032, 2, 29), Triple(2033, 2, 28)
            ),
            seen
        )
    }
}
