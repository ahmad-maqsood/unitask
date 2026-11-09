package com.example.util

import android.util.Log
import kotlinx.coroutines.CancellationException

/**
 * Runs [block] and never throws. A failure is written to Logcat (so it can be found later) and
 * null is returned. Coroutine cancellation is passed on so cancelled work still stops.
 *
 * Use this for best-effort work (setting alarms, cleanup) instead of an empty catch block.
 */
inline fun <T> runLogged(what: String, block: () -> T): T? {
    return try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("UniTask", "$what failed", e)
        null
    }
}
