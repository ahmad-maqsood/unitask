package com.example.util.timer

/**
 * Keeps an ongoing "timer is running" notification in step with the timer state.
 * The controller calls this after every state change.
 */
interface TimerOngoingHook {
    /** Shows or refreshes the notification for a running or paused timer, or removes it when idle. */
    fun update(state: TimerState, now: Long)

    /** Removes the notification. */
    fun cancel()
}

object NoTimerOngoing : TimerOngoingHook {
    override fun update(state: TimerState, now: Long) {}
    override fun cancel() {}
}
