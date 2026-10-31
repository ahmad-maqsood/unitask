package com.example.util

import com.example.data.local.LectureSlotWithCourse

/**
 * Finds time conflicts between lecture slots on the same day.
 *
 * Rules:
 * - Compares slots with the same dayOfWeek only.
 * - Times overlap when aStart < bEnd && bStart < aEnd (minutes since midnight). Back-to-back classes are NOT conflicts.
 * - Slots whose end is not after start are ignored.
 * - Week types: ALL conflicts with ALL, A, and B; A conflicts with A; B conflicts with B; A and B do NOT conflict.
 * - Soft-deleted slots (isDeleted == true) are ignored.
 *
 * Returns a map from slot id to the distinct list of course codes (or names if code is blank) of overlapping slots.
 */
fun findConflicts(slots: List<LectureSlotWithCourse>): Map<Long, List<String>> {
    val validSlots = slots.filter {
        !it.slot.isDeleted &&
        it.slot.dayOfWeek in 1..7 &&
        (it.slot.endHour * 60 + it.slot.endMinute) > (it.slot.startHour * 60 + it.slot.startMinute)
    }

    val result = mutableMapOf<Long, MutableList<String>>()

    for (i in validSlots.indices) {
        val a = validSlots[i]
        val aStart = a.slot.startHour * 60 + a.slot.startMinute
        val aEnd = a.slot.endHour * 60 + a.slot.endMinute
        val aType = weekTypeOf(a.slot.classroom)

        for (j in validSlots.indices) {
            if (i == j) continue
            val b = validSlots[j]
            if (a.slot.id == b.slot.id) continue
            if (a.slot.dayOfWeek != b.slot.dayOfWeek) continue

            val bType = weekTypeOf(b.slot.classroom)
            if (aType != WeekType.ALL && bType != WeekType.ALL && aType != bType) {
                continue
            }

            val bStart = b.slot.startHour * 60 + b.slot.startMinute
            val bEnd = b.slot.endHour * 60 + b.slot.endMinute
            if (aStart < bEnd && bStart < aEnd) {
                val label = b.course?.code?.trim()?.ifEmpty { null }
                    ?: b.course?.name?.trim()?.ifEmpty { null }
                    ?: "Class"
                result.getOrPut(a.slot.id) { mutableListOf() }.add(label)
            }
        }
    }

    return result.mapValues { (_, names) -> names.distinct() }
}

