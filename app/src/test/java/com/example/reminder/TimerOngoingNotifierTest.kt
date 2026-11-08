package com.example.reminder

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.util.timer.TimerMode
import com.example.util.timer.TimerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        // Buttons are round icon buttons inside the custom view, not native text actions.
        assertNull(runningNotif.actions)
        val runningView = runningNotif.bigContentView.apply(context, FrameLayout(context))
        assertEquals(
            "Pause",
            runningView.findViewById<View>(R.id.timer_notif_primary).contentDescription.toString()
        )
        assertEquals(
            "Stop",
            runningView.findViewById<View>(R.id.timer_notif_stop).contentDescription.toString()
        )

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
        assertNull(pausedNotif.actions)
        val pausedView = pausedNotif.bigContentView.apply(context, FrameLayout(context))
        assertEquals(
            "Resume",
            pausedView.findViewById<View>(R.id.timer_notif_primary).contentDescription.toString()
        )
        assertEquals(
            "Stop",
            pausedView.findViewById<View>(R.id.timer_notif_stop).contentDescription.toString()
        )
    }
}
