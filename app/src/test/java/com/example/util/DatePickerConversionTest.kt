package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class DatePickerConversionTest {

    private fun buildUtcMidnight(year: Int, month: Int, day: Int): Long {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun assertRoundTrip(zone: TimeZone, year: Int, month: Int, day: Int, hour: Int, minute: Int) {
        val localCal = Calendar.getInstance(zone).apply {
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val localMillis = localCal.timeInMillis
        val pickerUtc = localMillisToPickerUtcMillis(localMillis, zone)
        val expectedUtc = buildUtcMidnight(year, month, day)
        assertEquals("Picker UTC midnight mismatch for zone ${zone.id}", expectedUtc, pickerUtc)

        val restoredMillis = applyPickedDate(localMillis, pickerUtc, zone)
        assertEquals("Round-trip millis mismatch for zone ${zone.id}", localMillis, restoredMillis)
    }

    @Test
    fun testRoundTripInVariousTimeZones() {
        val zones = listOf(
            TimeZone.getTimeZone("UTC"),
            TimeZone.getTimeZone("Asia/Karachi"),
            TimeZone.getTimeZone("America/Los_Angeles"),
            TimeZone.getTimeZone("GMT+13:00"),
            TimeZone.getTimeZone("Pacific/Tongatapu")
        )

        for (zone in zones) {
            // Midday
            assertRoundTrip(zone, 2026, Calendar.JUNE, 15, 14, 30)
            // Early morning (in UTC+ zones, local morning might be previous day in UTC)
            assertRoundTrip(zone, 2026, Calendar.JUNE, 15, 1, 15)
            // Late night (in UTC- zones, local night might be next day in UTC)
            assertRoundTrip(zone, 2026, Calendar.JUNE, 15, 23, 45)
        }
    }

    @Test
    fun testTimeOfDayIsKeptByApplyPickedDate() {
        val zone = TimeZone.getTimeZone("Asia/Karachi")
        val initialCal = Calendar.getInstance(zone).apply {
            clear()
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.APRIL)
            set(Calendar.DAY_OF_MONTH, 10)
            set(Calendar.HOUR_OF_DAY, 17)
            set(Calendar.MINUTE, 45)
            set(Calendar.SECOND, 30)
            set(Calendar.MILLISECOND, 123)
        }
        val currentMillis = initialCal.timeInMillis

        // User picks May 20, 2026
        val pickedUtc = buildUtcMidnight(2026, Calendar.MAY, 20)
        val updatedMillis = applyPickedDate(currentMillis, pickedUtc, zone)

        val resultCal = Calendar.getInstance(zone).apply {
            timeInMillis = updatedMillis
        }

        // Date fields must be updated
        assertEquals(2026, resultCal.get(Calendar.YEAR))
        assertEquals(Calendar.MAY, resultCal.get(Calendar.MONTH))
        assertEquals(20, resultCal.get(Calendar.DAY_OF_MONTH))

        // Time fields must be preserved
        assertEquals(17, resultCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(45, resultCal.get(Calendar.MINUTE))
        assertEquals(30, resultCal.get(Calendar.SECOND))
        assertEquals(123, resultCal.get(Calendar.MILLISECOND))
    }

    @Test
    fun testDstChangeDayInAmericaNewYork() {
        val nyZone = TimeZone.getTimeZone("America/New_York")

        // 1. Spring forward: March 8, 2026
        val beforeSpringCal = Calendar.getInstance(nyZone).apply {
            clear()
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.MARCH)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 30)
        }
        val pickedSpringUtc = buildUtcMidnight(2026, Calendar.MARCH, 8)
        val updatedSpringMillis = applyPickedDate(beforeSpringCal.timeInMillis, pickedSpringUtc, nyZone)

        val springResultCal = Calendar.getInstance(nyZone).apply {
            timeInMillis = updatedSpringMillis
        }
        assertEquals(2026, springResultCal.get(Calendar.YEAR))
        assertEquals(Calendar.MARCH, springResultCal.get(Calendar.MONTH))
        assertEquals(8, springResultCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(14, springResultCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, springResultCal.get(Calendar.MINUTE))

        // Round trip on March 8, 2026
        assertRoundTrip(nyZone, 2026, Calendar.MARCH, 8, 14, 30)

        // 2. Fall back: November 1, 2026
        val beforeFallCal = Calendar.getInstance(nyZone).apply {
            clear()
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.OCTOBER)
            set(Calendar.DAY_OF_MONTH, 25)
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 30)
        }
        val pickedFallUtc = buildUtcMidnight(2026, Calendar.NOVEMBER, 1)
        val updatedFallMillis = applyPickedDate(beforeFallCal.timeInMillis, pickedFallUtc, nyZone)

        val fallResultCal = Calendar.getInstance(nyZone).apply {
            timeInMillis = updatedFallMillis
        }
        assertEquals(2026, fallResultCal.get(Calendar.YEAR))
        assertEquals(Calendar.NOVEMBER, fallResultCal.get(Calendar.MONTH))
        assertEquals(1, fallResultCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(14, fallResultCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, fallResultCal.get(Calendar.MINUTE))

        // Round trip on November 1, 2026
        assertRoundTrip(nyZone, 2026, Calendar.NOVEMBER, 1, 14, 30)
    }

    @Test
    fun testMonthEndAndLeapDay() {
        val zone = TimeZone.getTimeZone("UTC")

        // 1. Month end: Jan 31 -> Feb 28 (non-leap year 2026)
        val jan31Cal = Calendar.getInstance(zone).apply {
            clear()
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 31)
            set(Calendar.HOUR_OF_DAY, 15)
            set(Calendar.MINUTE, 30)
        }
        val feb28Utc = buildUtcMidnight(2026, Calendar.FEBRUARY, 28)
        val feb28Millis = applyPickedDate(jan31Cal.timeInMillis, feb28Utc, zone)

        val feb28Cal = Calendar.getInstance(zone).apply {
            timeInMillis = feb28Millis
        }
        assertEquals(2026, feb28Cal.get(Calendar.YEAR))
        assertEquals(Calendar.FEBRUARY, feb28Cal.get(Calendar.MONTH))
        assertEquals(28, feb28Cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(15, feb28Cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, feb28Cal.get(Calendar.MINUTE))

        // 2. Month end: March 31 -> April 30
        val mar31Cal = Calendar.getInstance(zone).apply {
            clear()
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.MARCH)
            set(Calendar.DAY_OF_MONTH, 31)
            set(Calendar.HOUR_OF_DAY, 11)
            set(Calendar.MINUTE, 0)
        }
        val apr30Utc = buildUtcMidnight(2026, Calendar.APRIL, 30)
        val apr30Millis = applyPickedDate(mar31Cal.timeInMillis, apr30Utc, zone)

        val apr30Cal = Calendar.getInstance(zone).apply {
            timeInMillis = apr30Millis
        }
        assertEquals(2026, apr30Cal.get(Calendar.YEAR))
        assertEquals(Calendar.APRIL, apr30Cal.get(Calendar.MONTH))
        assertEquals(30, apr30Cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(11, apr30Cal.get(Calendar.HOUR_OF_DAY))

        // 3. Leap day: Jan 31 -> Feb 29 (leap year 2024)
        val leapJanCal = Calendar.getInstance(zone).apply {
            clear()
            set(Calendar.YEAR, 2024)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 31)
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 15)
        }
        val leapUtc = buildUtcMidnight(2024, Calendar.FEBRUARY, 29)
        val leapMillis = applyPickedDate(leapJanCal.timeInMillis, leapUtc, zone)

        val leapCal = Calendar.getInstance(zone).apply {
            timeInMillis = leapMillis
        }
        assertEquals(2024, leapCal.get(Calendar.YEAR))
        assertEquals(Calendar.FEBRUARY, leapCal.get(Calendar.MONTH))
        assertEquals(29, leapCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(10, leapCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(15, leapCal.get(Calendar.MINUTE))

        // Leap day round-trip
        assertRoundTrip(zone, 2024, Calendar.FEBRUARY, 29, 10, 15)
    }
}
