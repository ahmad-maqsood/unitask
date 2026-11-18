package com.example.reminder

import android.content.Context
import androidx.core.content.edit
import com.example.data.local.LectureSlot
import com.example.util.WeekParity
import com.example.util.isSlotActive
import java.util.Calendar
import java.util.TimeZone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Single dates on which a weekly class is cancelled on THIS phone (the lecturer is away, a holiday).
 *
 * The class itself stays in the timetable and keeps its weekly schedule. Only that one date is
 * skipped: no start reminder, not in today's classes, the daily summary or the widget.
 * The choice is personal and never sent anywhere, like turning a class off.
 *
 * A key is "slotId|yyyy-MM-dd" in the phone's time zone. It lives in SharedPreferences, so no
 * database change is needed. Dates older than [KEEP_DAYS] are dropped.
 */
object CancelledClassStore {

    private const val PREFS = "cancelled_classes"
    private const val KEY_SET = "keys"
    private const val KEEP_DAYS = 60

    private val state = MutableStateFlow<Set<String>>(emptySet())

    @Volatile
    private var loaded = false

    /** "yyyy-MM-dd" of [millis] in [timeZone]. Pure, so it can be tested. */
    fun dateKey(millis: Long, timeZone: TimeZone = TimeZone.getDefault()): String {
        val cal = Calendar.getInstance(timeZone).apply { timeInMillis = millis }
        return "%04d-%02d-%02d".format(
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun keyOf(slotId: Long, dayMillis: Long, timeZone: TimeZone = TimeZone.getDefault()): String =
        "$slotId|${dateKey(dayMillis, timeZone)}"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** A copy: the set that SharedPreferences returns must never be changed. */
    private fun read(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_SET, null)?.toSet().orEmpty()

    private fun write(context: Context, keys: Set<String>) {
        prefs(context).edit { putStringSet(KEY_SET, HashSet(keys)) }
        state.value = keys
        loaded = true
    }

    /** Keys as a flow, so cards and the class schedule update at once. */
    fun keysFlow(context: Context): StateFlow<Set<String>> {
        if (!loaded) {
            state.value = read(context)
            loaded = true
        }
        return state
    }

    fun isCancelled(context: Context, slotId: Long, dayMillis: Long, timeZone: TimeZone = TimeZone.getDefault()): Boolean =
        keyOf(slotId, dayMillis, timeZone) in read(context)

    /** Cancels or brings back the class on the date of [dayMillis]. Callers refresh the alarms after. */
    fun setCancelled(context: Context, slotId: Long, dayMillis: Long, cancelled: Boolean) {
        val key = keyOf(slotId, dayMillis)
        val current = read(context)
        val next = if (cancelled) current + key else current - key
        write(context, prune(next))
    }

    /** Drops dates older than [KEEP_DAYS] days. Pure, so it can be tested. */
    fun prune(keys: Set<String>, nowMillis: Long = System.currentTimeMillis(), timeZone: TimeZone = TimeZone.getDefault()): Set<String> {
        val oldest = dateKey(nowMillis - KEEP_DAYS * 86_400_000L, timeZone)
        return keys.filter { it.substringAfter('|', "") >= oldest }.toSet()
    }

    /**
     * The next date (start of its class) after [afterMillis] on which this weekly class really takes
     * place by its timetable and week A/B, ignoring any cancellation. Null if none in the next 3 weeks.
     */
    fun nextOccurrenceMillis(context: Context, slot: LectureSlot, afterMillis: Long = System.currentTimeMillis()): Long? {
        val anchor = WeekParity.getWeekAAnchor(context)
        val wantedDay = if (slot.dayOfWeek == 7) Calendar.SUNDAY else slot.dayOfWeek + 1
        val cal = Calendar.getInstance().apply {
            timeInMillis = afterMillis
            set(Calendar.HOUR_OF_DAY, slot.endHour)
            set(Calendar.MINUTE, slot.endMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // Up to 3 weeks of days; the first matching weekday whose class has not ended yet.
        repeat(22) {
            if (cal.get(Calendar.DAY_OF_WEEK) == wantedDay && cal.timeInMillis > afterMillis) {
                val start = (cal.clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, slot.startHour)
                    set(Calendar.MINUTE, slot.startMinute)
                }.timeInMillis
                // Same week check the rest of the app uses, but without the cancelled-dates check.
                if (isSlotActive(slot, start, anchor)) return start
            }
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return null
    }
}
