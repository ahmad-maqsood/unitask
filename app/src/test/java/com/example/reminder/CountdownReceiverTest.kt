package com.example.reminder

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.util.timer.TimerController
import com.example.util.timer.TimerLogic
import com.example.util.timer.TimerMode
import com.example.util.timer.TimerState
import com.example.util.timer.TimerStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNotificationManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CountdownReceiverTest {

    private lateinit var context: Context
    private lateinit var store: TimerStore
    private lateinit var shadowNotificationManager: ShadowNotificationManager
    private var currentTime = 1_000L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        Shadows.shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowNotificationManager = Shadows.shadowOf(nm)
        nm.cancelAll()

        store = TimerStore(context)
        store.save(TimerState())
        currentTime = 1_000L

        val controller = TimerController(
            store = store,
            clock = { currentTime }
        )
        TimerController.setInstanceForTests(controller)
    }

    @After
    fun tearDown() {
        TimerController.setInstanceForTests(null)
    }

    @Test
    fun staleAlarmWhenTimerIsIdlePostsNoNotification() {
        val controller = TimerController.get(context)
        assertTrue(TimerLogic.isIdle(controller.state.value))

        val receiver = CountdownReceiver()
        receiver.onReceive(context, Intent())

        assertEquals(0, shadowNotificationManager.allNotifications.size)
        assertTrue(TimerLogic.isIdle(controller.state.value))
    }

    @Test
    fun validAlarmOnRunningCountdownDueMarksFinishedAndPostsExactlyOneNotification() {
        val controller = TimerController.get(context)
        controller.setMode(TimerMode.COUNTDOWN)
        controller.setCountdownMinutes(1) // 60_000 ms -> end is 61_000L
        controller.start()

        // Advance to due time
        currentTime = 61_000L

        val receiver = CountdownReceiver()
        receiver.onReceive(context, Intent())

        // Timer is now finished and not running
        assertFalse(TimerLogic.isRunning(controller.state.value))
        assertTrue(TimerLogic.isFinished(controller.state.value, currentTime))

        // Exactly one notification posted
        val notifications = shadowNotificationManager.allNotifications
        assertEquals(1, notifications.size)
        val notif = notifications[0]
        assertEquals("Countdown finished", notif.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Your 1 minute countdown is done.", notif.extras.getString(Notification.EXTRA_TEXT))
    }

    @Test
    fun sameAlarmDeliveredTwicePostsOnlyOneNotification() {
        val controller = TimerController.get(context)
        controller.setMode(TimerMode.COUNTDOWN)
        controller.setCountdownMinutes(5) // 300_000 ms
        controller.start()

        currentTime = 1_000L + 300_000L

        val receiver = CountdownReceiver()
        receiver.onReceive(context, Intent())
        assertEquals(1, shadowNotificationManager.allNotifications.size)

        // Second delivery of the same alarm
        receiver.onReceive(context, Intent())
        assertEquals(1, shadowNotificationManager.allNotifications.size)
    }

    @Test
    fun alarmArrivingMoreThanToleranceEarlyDoesNothing() {
        val controller = TimerController.get(context)
        controller.setMode(TimerMode.COUNTDOWN)
        controller.setCountdownMinutes(1) // due at 61_000L
        controller.start()

        // Arrives at 55_000L (6s early, tolerance is 2000 ms)
        currentTime = 55_000L

        val receiver = CountdownReceiver()
        receiver.onReceive(context, Intent())

        // Still running, no notification
        assertTrue(TimerLogic.isRunning(controller.state.value))
        assertEquals(0, shadowNotificationManager.allNotifications.size)
    }

    @Test
    fun alarmArrivingWithinToleranceEarlyMarksFinishedAndPostsNotification() {
        val controller = TimerController.get(context)
        controller.setMode(TimerMode.COUNTDOWN)
        controller.setCountdownMinutes(1) // due at 61_000L
        controller.start()

        // Arrives at 59_500L (1.5s early, within 2000 ms tolerance)
        currentTime = 59_500L

        val receiver = CountdownReceiver()
        receiver.onReceive(context, Intent())

        assertFalse(TimerLogic.isRunning(controller.state.value))
        assertEquals(1, shadowNotificationManager.allNotifications.size)
    }
}
