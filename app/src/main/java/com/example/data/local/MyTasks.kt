package com.example.data.local

import java.util.Calendar
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

/**
 * The day of the month a monthly or yearly repeating task should keep returning to.
 * [storedAnchor] is the day saved on the task (0 = nothing saved yet). It is trusted only while the
 * due date still fits it: the due day must be the saved day, or the last day of the month when that
 * month is shorter. Otherwise the user moved the date by hand, and the due day becomes the new anchor.
 */
fun resolveAnchorDay(storedAnchor: Int, deadlineMillis: Long): Int {
    val cal = Calendar.getInstance().apply { timeInMillis = deadlineMillis }
    val dueDay = cal.get(Calendar.DAY_OF_MONTH)
    if (storedAnchor !in 1..31) return dueDay
    val expectedDay = minOf(storedAnchor, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
    return if (expectedDay == dueDay) storedAnchor else dueDay
}

fun nextOccurrenceMillis(
    rule: RepeatRule,
    deadlineMillis: Long,
    nowMillis: Long = System.currentTimeMillis(),
    anchorDay: Int = 0
): Long {
    if (rule == RepeatRule.NONE) return deadlineMillis

    val cal = Calendar.getInstance().apply {
        timeInMillis = deadlineMillis
    }
    // The day of the month a monthly or yearly task returns to (0 = use the current due day).
    val anchor = if (anchorDay in 1..31) anchorDay else cal.get(Calendar.DAY_OF_MONTH)

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
            RepeatRule.MONTHLY, RepeatRule.YEARLY -> {
                // Go to day 1 first so the move can never overflow into the next month,
                // then back to the remembered day (or the last day of a shorter month).
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.add(if (rule == RepeatRule.MONTHLY) Calendar.MONTH else Calendar.YEAR, 1)
                cal.set(
                    Calendar.DAY_OF_MONTH,
                    minOf(anchor, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                )
            }
        }
    }

    step()
    while (cal.timeInMillis <= nowMillis) {
        step()
    }

    return cal.timeInMillis
}

private val ensureMyTasksCourseMutex = Mutex()

suspend fun AppDatabase.ensureMyTasksCourse(): Long = ensureMyTasksCourseMutex.withLock {
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
