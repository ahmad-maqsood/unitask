package com.example.reminder

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.util.timer.TimerMode
import com.example.util.timer.TimerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNotificationManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TimerOngoingNotifierTest {

    private lateinit var context: Context
    private lateinit var shadowNotificationManager: ShadowNotificationManager
    private lateinit var notifier: TimerOngoingNotifier

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        Shadows.shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowNotificationManager = Shadows.shadowOf(nm)
        nm.cancelAll()
        notifier = TimerOngoingNotifier(context)
    }

    @Test
    fun ongoingNotificationHasPublicVisibilityAndActionsWhenRunningAndPaused() {
        val runningState = TimerState(
            mode = TimerMode.STOPWATCH,
            runningSinceMillis = 10_000L,
            accumulatedMillis = 0L
        )
        val now = 15_000L
        notifier.update(runningState, now)

        val notifications = shadowNotificationManager.allNotifications
        assertEquals(1, notifications.size)
        val runningNotif = notifications.last()
        assertEquals(Notification.VISIBILITY_PUBLIC, runningNotif.visibility)
        assertNotNull(runningNotif.actions)
        assertEquals(2, runningNotif.actions.size)
        assertEquals("Pause", runningNotif.actions[0].title.toString())
        assertEquals("Stop", runningNotif.actions[1].title.toString())

        val pausedState = TimerState(
            mode = TimerMode.STOPWATCH,
            runningSinceMillis = null,
            accumulatedMillis = 5_000L
        )
        notifier.update(pausedState, now)

        val updatedNotifications = shadowNotificationManager.allNotifications
        assertEquals(1, updatedNotifications.size)
        val pausedNotif = updatedNotifications.last()
        assertEquals(Notification.VISIBILITY_PUBLIC, pausedNotif.visibility)
        assertNotNull(pausedNotif.actions)
        assertEquals(2, pausedNotif.actions.size)
        assertEquals("Resume", pausedNotif.actions[0].title.toString())
        assertEquals("Stop", pausedNotif.actions[1].title.toString())
    }
}
