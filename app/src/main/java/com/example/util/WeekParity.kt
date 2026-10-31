package com.example.util

import android.content.Context
import com.example.data.local.LectureSlot
import java.util.Calendar
import java.util.TimeZone

enum class WeekType {
    ALL,
    A,
    B
}

object WeekParity {
    private const val PREFS = "unitask_settings"
    const val KEY_ANCHOR_MILLIS = "week_a_anchor_millis"

    @Volatile
    var inMemoryAnchorMillis: Long? = null

    fun getWeekAAnchor(context: Context?): Long? {
        if (context == null) return inMemoryAnchorMillis
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return if (sp.contains(KEY_ANCHOR_MILLIS)) {
            val v = sp.getLong(KEY_ANCHOR_MILLIS, 0L)
            val res = if (v > 0L) v else null
            inMemoryAnchorMillis = res
            res
        } else {
            inMemoryAnchorMillis = null
            null
        }
    }

    fun setWeekAAnchor(context: Context?, anchorMillis: Long?) {
        inMemoryAnchorMillis = if (anchorMillis != null && anchorMillis > 0L) anchorMillis else null
        context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.let { editor ->
            if (anchorMillis == null || anchorMillis <= 0L) {
                editor.remove(KEY_ANCHOR_MILLIS)
            } else {
                editor.putLong(KEY_ANCHOR_MILLIS, anchorMillis)
            }
            editor.apply()
        }
    }

    fun isAnchorSet(context: Context?): Boolean = getWeekAAnchor(context) != null

    fun setThisWeekType(context: Context?, type: WeekType?, nowMillis: Long = System.currentTimeMillis()) {
        when (type) {
            WeekType.A -> setWeekAAnchor(context, nowMillis)
            WeekType.B -> setWeekAAnchor(context, nowMillis - 7 * 86_400_000L)
            else -> setWeekAAnchor(context, null)
        }
    }

    fun getMondayStartMillis(timeMillis: Long, timeZone: TimeZone = TimeZone.getDefault()): Long {
        val cal = Calendar.getInstance(timeZone).apply {
            this.timeInMillis = timeMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val daysSinceMonday = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - Calendar.MONDAY
        cal.add(Calendar.DAY_OF_YEAR, -daysSinceMonday)
        return cal.timeInMillis
    }
}

fun weekTypeOf(classroom: String): WeekType = when {
    classroom.contains("[Week A]") -> WeekType.A
    classroom.contains("[Week B]") -> WeekType.B
    else -> WeekType.ALL
}

fun weekTypeOf(slot: LectureSlot): WeekType = weekTypeOf(slot.classroom)

fun cleanClassroom(text: String): String =
    text.replace("[Week A]", "").replace("[Week B]", "").trim()

fun currentWeekType(
    atMillis: Long = System.currentTimeMillis(),
    anchorMillis: Long? = WeekParity.inMemoryAnchorMillis,
    timeZone: TimeZone = TimeZone.getDefault()
): WeekType? {
    if (anchorMillis == null || anchorMillis <= 0L) return null
    val targetMonday = WeekParity.getMondayStartMillis(atMillis, timeZone)
    val anchorMonday = WeekParity.getMondayStartMillis(anchorMillis, timeZone)
    val targetNoon = targetMonday + 12 * 3600_000L
    val anchorNoon = anchorMonday + 12 * 3600_000L
    val diffWeeks = Math.round((targetNoon - anchorNoon) / (7.0 * 86_400_000.0))
    val mod = Math.floorMod(diffWeeks.toLong(), 2L)
    return if (mod == 0L) WeekType.A else WeekType.B
}

fun currentWeekType(
    context: Context,
    atMillis: Long = System.currentTimeMillis(),
    timeZone: TimeZone = TimeZone.getDefault()
): WeekType? = currentWeekType(atMillis, WeekParity.getWeekAAnchor(context), timeZone)

fun isSlotActive(
    slot: LectureSlot,
    atMillis: Long = System.currentTimeMillis(),
    anchorMillis: Long? = WeekParity.inMemoryAnchorMillis,
    timeZone: TimeZone = TimeZone.getDefault()
): Boolean {
    if (anchorMillis == null || anchorMillis <= 0L) return true
    val slotType = weekTypeOf(slot)
    if (slotType == WeekType.ALL) return true
    return slotType == currentWeekType(atMillis, anchorMillis, timeZone)
}

fun isSlotActive(
    context: Context,
    slot: LectureSlot,
    atMillis: Long = System.currentTimeMillis(),
    timeZone: TimeZone = TimeZone.getDefault()
): Boolean = isSlotActive(slot, atMillis, WeekParity.getWeekAAnchor(context), timeZone)

