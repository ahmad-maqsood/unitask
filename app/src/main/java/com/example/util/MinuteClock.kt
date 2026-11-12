package com.example.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

private const val MILLIS_PER_MINUTE = 60_000L

/** How long until the next minute starts, so a clock can tick exactly on the minute. Always 1 to 60000. */
fun millisUntilNextMinute(now: Long): Long = MILLIS_PER_MINUTE - Math.floorMod(now, MILLIS_PER_MINUTE)

/**
 * Emits the current time straight away and again at the start of every minute.
 *
 * Lists that sort tasks into Overdue, Today and Tomorrow use this, so a task moves to the right
 * section when its time passes (or at midnight) even if nothing else in the app changed. The flow
 * only runs while something is collecting it.
 */
fun minuteClock(): Flow<Long> = flow {
    while (true) {
        val now = System.currentTimeMillis()
        emit(now)
        delay(millisUntilNextMinute(now))
    }
}
