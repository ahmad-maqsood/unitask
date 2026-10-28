package com.example.data.local

import java.util.Calendar

const val MY_TASKS_COURSE_CODE = "MYTASKS"

fun Course.isMyTasksCourse(): Boolean =
    code.replace(" ", "").trim().uppercase() == MY_TASKS_COURSE_CODE

enum class RepeatRule(val label: String) {
    NONE("Does not repeat"),
    DAILY("Every day"),
    WEEKDAYS("Every weekday (Mon to Fri)"),
    WEEKLY("Every week"),
    BIWEEKLY("Every 2 weeks"),
    MONTHLY("Every month"),
    YEARLY("Every year")
}

fun decodeRepeatRule(value: String?): RepeatRule =
    try {
        RepeatRule.valueOf(value.orEmpty())
    } catch (_: Exception) {
        RepeatRule.NONE
    }

val TaskItem.repeat: RepeatRule
    get() = decodeRepeatRule(repeatRule)

fun nextOccurrenceMillis(
    rule: RepeatRule,
    deadlineMillis: Long,
    nowMillis: Long = System.currentTimeMillis()
): Long {
    if (rule == RepeatRule.NONE) return deadlineMillis

    val cal = Calendar.getInstance().apply {
        timeInMillis = deadlineMillis
    }

    fun step() {
        when (rule) {
            RepeatRule.NONE -> {}
            RepeatRule.DAILY -> cal.add(Calendar.DAY_OF_YEAR, 1)
            RepeatRule.WEEKDAYS -> {
                do {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                    val dow = cal.get(Calendar.DAY_OF_WEEK)
                } while (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY)
            }
            RepeatRule.WEEKLY -> cal.add(Calendar.DAY_OF_YEAR, 7)
            RepeatRule.BIWEEKLY -> cal.add(Calendar.DAY_OF_YEAR, 14)
            RepeatRule.MONTHLY -> cal.add(Calendar.MONTH, 1)
            RepeatRule.YEARLY -> cal.add(Calendar.YEAR, 1)
        }
    }

    step()
    while (cal.timeInMillis <= nowMillis) {
        step()
    }

    return cal.timeInMillis
}

suspend fun AppDatabase.ensureMyTasksCourse(): Long {
    val existing = courseDao().getAnyCourseByNormalizedCode(MY_TASKS_COURSE_CODE)
    if (existing != null) {
        if (existing.isDeleted) {
            val restored = existing.copy(isDeleted = false, isSynced = false)
            courseDao().updateCourse(restored)
        }
        return existing.id
    }
    val newCourse = Course(
        code = MY_TASKS_COURSE_CODE,
        name = "My tasks",
        instructor = "",
        colorHex = "#7C3AED",
        isSynced = false,
        isDeleted = false
    )
    return courseDao().insertCourse(newCourse)
}
