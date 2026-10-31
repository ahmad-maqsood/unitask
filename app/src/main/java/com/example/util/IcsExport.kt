package com.example.util

import com.example.data.local.LectureSlotWithCourse
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone

private val DAY_CODES = mapOf(
    1 to "MO",
    2 to "TU",
    3 to "WE",
    4 to "TH",
    5 to "FR",
    6 to "SA",
    7 to "SU"
)

private val UTC_DATE_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.US)

/**
 * Folds a content line at 75 UTF-8 octets, as required by RFC 5545 section 3.1.
 * Continuation lines begin with a single space.
 * Multi-byte UTF-8 character sequences are never split across line folds.
 * The returned string always ends with CRLF ("\r\n").
 */
fun foldLine(line: String): String {
    val lineBytes = line.toByteArray(Charsets.UTF_8)
    if (lineBytes.size <= 75) {
        return line + "\r\n"
    }

    val sb = StringBuilder()
    var currentLineBytes = 0
    var isFirstLine = true
    val limit = 75

    var i = 0
    while (i < line.length) {
        val cp = line.codePointAt(i)
        val charCount = Character.charCount(cp)
        val charStr = line.substring(i, i + charCount)
        val charBytes = charStr.toByteArray(Charsets.UTF_8).size

        val prefixLen = if (isFirstLine) 0 else 1
        if (currentLineBytes + charBytes > limit && currentLineBytes > prefixLen) {
            sb.append("\r\n ")
            currentLineBytes = 1
            isFirstLine = false
        }

        sb.append(charStr)
        currentLineBytes += charBytes
        i += charCount
    }

    sb.append("\r\n")
    return sb.toString()
}

/**
 * Escapes characters for iCalendar text property values (RFC 5545 section 3.3.11).
 * Also strips carriage returns ("\r").
 */
fun escapeIcs(str: String): String {
    return str.replace("\r", "")
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\n", "\\n")
}

/**
 * Builds an RFC 5545 compliant iCalendar (.ics) string for the given lecture slots.
 * DTSTART and DTEND use UTC with trailing 'Z' for universal calendar app compatibility.
 */
fun buildTimetableIcs(
    slots: List<LectureSlotWithCourse>,
    weekAAnchorMillis: Long? = null,
    nowMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault()
): String {
    val sb = StringBuilder()
    sb.append(foldLine("BEGIN:VCALENDAR"))
    sb.append(foldLine("VERSION:2.0"))
    sb.append(foldLine("PRODID:-//UniTask//Timetable//EN"))
    sb.append(foldLine("CALSCALE:GREGORIAN"))
    sb.append(foldLine("METHOD:PUBLISH"))
    sb.append(foldLine("X-WR-CALNAME:UniTask Timetable"))

    val tz = TimeZone.getTimeZone(zoneId.id)
    val currentMondayMillis = WeekParity.getMondayStartMillis(nowMillis, tz)
    val currentMondayDate = Instant.ofEpochMilli(currentMondayMillis).atZone(zoneId).toLocalDate()

    val stampUtc = Instant.ofEpochMilli(nowMillis).atZone(ZoneOffset.UTC)
    val dtStampStr = stampUtc.format(UTC_DATE_TIME_FORMATTER)

    for (item in slots) {
        val slot = item.slot
        val course = item.course

        // Validate time values and skip invalid or non-advancing slots
        if (slot.dayOfWeek !in 1..7 ||
            slot.startHour !in 0..23 || slot.startMinute !in 0..59 ||
            slot.endHour !in 0..23 || slot.endMinute !in 0..59
        ) {
            continue
        }

        val startMinutes = slot.startHour * 60 + slot.startMinute
        val endMinutes = slot.endHour * 60 + slot.endMinute
        if (endMinutes <= startMinutes) {
            continue
        }

        val slotType = weekTypeOf(slot.classroom)
        val weekShiftDays = if (slotType != WeekType.ALL && weekAAnchorMillis != null && weekAAnchorMillis > 0L) {
            val currentMondayType = currentWeekType(currentMondayMillis, weekAAnchorMillis, tz)
            if (currentMondayType != null && currentMondayType != slotType) 7L else 0L
        } else {
            0L
        }

        val slotDate = currentMondayDate.plusDays(weekShiftDays).plusDays((slot.dayOfWeek - 1).toLong())
        val startZdt = ZonedDateTime.of(slotDate, LocalTime.of(slot.startHour, slot.startMinute), zoneId)
        val endZdt = ZonedDateTime.of(slotDate, LocalTime.of(slot.endHour, slot.endMinute), zoneId)

        val startUtc = startZdt.withZoneSameInstant(ZoneOffset.UTC)
        val endUtc = endZdt.withZoneSameInstant(ZoneOffset.UTC)
        if (!endUtc.isAfter(startUtc)) {
            continue
        }

        val dtStartStr = startUtc.format(UTC_DATE_TIME_FORMATTER)
        val dtEndStr = endUtc.format(UTC_DATE_TIME_FORMATTER)

        val byDay = DAY_CODES[slot.dayOfWeek] ?: "MO"
        val rrule = if (slotType == WeekType.A || slotType == WeekType.B) {
            "FREQ=WEEKLY;INTERVAL=2;WKST=MO;BYDAY=$byDay"
        } else {
            "FREQ=WEEKLY;WKST=MO;BYDAY=$byDay"
        }

        val title = course?.name?.ifBlank { course.code } ?: course?.code ?: "Lecture"
        val rawLocation = cleanClassroom(slot.classroom).ifBlank { course?.location.orEmpty() }
        val location = cleanClassroom(rawLocation)
        val instructor = slot.instructor.ifBlank { course?.instructor.orEmpty() }

        sb.append(foldLine("BEGIN:VEVENT"))
        sb.append(foldLine("UID:unitask_slot_${slot.id}@unitask"))
        sb.append(foldLine("DTSTAMP:$dtStampStr"))
        sb.append(foldLine("DTSTART:$dtStartStr"))
        sb.append(foldLine("DTEND:$dtEndStr"))
        sb.append(foldLine("RRULE:$rrule"))
        sb.append(foldLine("SUMMARY:${escapeIcs(title)}"))
        if (location.isNotBlank()) {
            sb.append(foldLine("LOCATION:${escapeIcs(location)}"))
        }
        if (instructor.isNotBlank()) {
            sb.append(foldLine("DESCRIPTION:${escapeIcs("Instructor: $instructor")}"))
        }
        sb.append(foldLine("END:VEVENT"))
    }

    sb.append(foldLine("END:VCALENDAR"))
    return sb.toString()
}

