package com.example.reminder

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.util.timer.PomodoroAlertSettings
import com.example.util.timer.PomodoroPhase
import com.example.util.timer.TimerController
import com.example.util.timer.TimerLogic
import com.example.util.timer.TimerMode
import com.example.util.timer.TimerState
import com.example.util.timer.TimerStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun finishedAlarmHasPublicVisibilityAndStopAction() {
        val controller = TimerController.get(context)
        controller.setMode(TimerMode.COUNTDOWN)
        controller.setCountdownMinutes(1)
        controller.start()

        currentTime = 61_000L
        val receiver = CountdownReceiver()
        receiver.onReceive(context, Intent())

        val notifications = shadowNotificationManager.allNotifications
        assertEquals(1, notifications.size)
        val notif = notifications[0]
        assertEquals(CountdownNotifier.CHANNEL_ID, notif.channelId)
        assertEquals(Notification.VISIBILITY_PUBLIC, notif.visibility)
        assertEquals(1, notif.actions.size)
        assertEquals("Stop", notif.actions[0].title.toString())

        // Stop action intent cancels the notification
        val actionReceiver = TimerActionReceiver()
        val stopIntent = Intent(context, TimerActionReceiver::class.java).apply {
            action = TimerActionReceiver.ACTION_STOP_COUNTDOWN
        }
        actionReceiver.onReceive(context, stopIntent)
        assertEquals(0, shadowNotificationManager.allNotifications.size)
    }

    @Test
    fun lateAlarmEndingMoreThanTwoMinutesAgoPostsSilencedNotification() {
        val controller = TimerController.get(context)
        controller.setMode(TimerMode.COUNTDOWN)
        controller.setCountdownMinutes(1) // due at 61_000L
        controller.start()

        // 3 minutes after due time (more than 2 minutes late)
        currentTime = 61_000L + 180_000L

        val receiver = CountdownReceiver()
        receiver.onReceive(context, Intent())

        val notifications = shadowNotificationManager.allNotifications
        assertEquals(1, notifications.size)
        val notif = notifications[0]
        assertEquals(CountdownNotifier.CHANNEL_ID_SILENT, notif.channelId)
    }
    
    @Test
    fun silenceAlarmDoesNotRepostAfterNotificationWasOpened() {
        CountdownNotifier.notifyFinished(context, 60_000L, ring = true)
        assertEquals(1, shadowNotificationManager.allNotifications.size)

        // Tapping the notification body removes it without running the Stop action.
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancelAll()

        val silence = Intent(context, TimerActionReceiver::class.java).apply {
            action = TimerActionReceiver.ACTION_SILENCE_COUNTDOWN
            putExtra(CountdownNotifier.EXTRA_TOTAL_MILLIS, 60_000L)
        }
        TimerActionReceiver().onReceive(context, silence)

        assertEquals(0, shadowNotificationManager.allNotifications.size)
    }

    // ---- Pomodoro: ring or vibrate only, and the auto-start note ----

    /** Starts a Pomodoro focus round and lets it run out through the alarm receiver. */
    private fun finishPomodoroFocusRound(controller: TimerController) {
        controller.enablePomodoro() // 25 minute focus round
        controller.start()
        currentTime += 25 * 60_000L
        CountdownReceiver().onReceive(context, Intent())
    }

    @Test
    fun pomodoroRoundEndRingsLikeAnAlarmByDefault() {
        val controller = TimerController.get(context)
        finishPomodoroFocusRound(controller)

        val notif = shadowNotificationManager.allNotifications.single()
        assertEquals(CountdownNotifier.CHANNEL_ID, notif.channelId)
        assertTrue(notif.flags and Notification.FLAG_INSISTENT != 0)
        assertEquals("Focus session done", notif.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(
            "You focused for 25 minutes. Time for a break.",
            notif.extras.getString(Notification.EXTRA_TEXT)
        )
    }

    @Test
    fun pomodoroRoundEndOnlyVibratesWhenRingingIsTurnedOff() {
        val controller = TimerController.get(context)
        controller.setPomodoroAlertSettings(PomodoroAlertSettings(ringLikeAlarm = false))
        finishPomodoroFocusRound(controller)

        val notif = shadowNotificationManager.allNotifications.single()
        assertEquals(CountdownNotifier.CHANNEL_ID_VIBRATE, notif.channelId)
        assertNull(notif.sound)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = nm.getNotificationChannel(CountdownNotifier.CHANNEL_ID_VIBRATE)
        assertTrue(channel.shouldVibrate())
        assertNull(channel.sound)
        // One burst of vibration, not an alarm that repeats until it is silenced.
        assertEquals(0, notif.flags and Notification.FLAG_INSISTENT)
        assertEquals(1, notif.actions.size)
    }

    @Test
    fun aPlainCountdownStillRingsWhateverThePomodoroOptionSays() {
        val controller = TimerController.get(context)
        controller.setPomodoroAlertSettings(PomodoroAlertSettings(ringLikeAlarm = false))
        controller.setMode(TimerMode.COUNTDOWN)
        controller.setCountdownMinutes(1)
        controller.start()
        currentTime = 61_000L

        CountdownReceiver().onReceive(context, Intent())

        val notif = shadowNotificationManager.allNotifications.single()
        assertEquals(CountdownNotifier.CHANNEL_ID, notif.channelId)
    }

    @Test
    fun aLateAlertStaysSilentEvenInVibrateOnlyMode() {
        val controller = TimerController.get(context)
        controller.setPomodoroAlertSettings(PomodoroAlertSettings(ringLikeAlarm = false))
        controller.enablePomodoro()
        controller.start()
        currentTime += 25 * 60_000L + 180_000L // 3 minutes late

        CountdownReceiver().onReceive(context, Intent())

        val notif = shadowNotificationManager.allNotifications.single()
        assertEquals(CountdownNotifier.CHANNEL_ID_SILENT, notif.channelId)
    }

    @Test
    fun theAlertSaysTheNextRoundStartsByItselfWhenAutoStartIsOn() {
        val controller = TimerController.get(context)
        controller.setPomodoroAlertSettings(PomodoroAlertSettings(autoStartNext = true))
        finishPomodoroFocusRound(controller)

        val notif = shadowNotificationManager.allNotifications.single()
        assertEquals(
            "You focused for 25 minutes. Your break starts in 10 seconds.",
            notif.extras.getString(Notification.EXTRA_TEXT)
        )
    }

    @Test
    fun autoStartAlarmStartsTheNextPomodoroRound() {
        val controller = TimerController.get(context)
        controller.setPomodoroAlertSettings(PomodoroAlertSettings(autoStartNext = true))
        finishPomodoroFocusRound(controller)
        assertTrue(controller.autoStartAtMillis.value != null)

        currentTime += 10_000L
        val autoStart = Intent(context, TimerActionReceiver::class.java).apply {
            action = TimerActionReceiver.ACTION_AUTO_START_NEXT
        }
        TimerActionReceiver().onReceive(context, autoStart)

        val s = controller.state.value
        assertEquals(PomodoroPhase.SHORT_BREAK, s.pomodoroPhase)
        assertTrue(TimerLogic.isRunning(s))
        assertNull(controller.autoStartAtMillis.value)
    }

    @Test
    fun autoStartAlarmDoesNothingAfterTheUserCancelledIt() {
        val controller = TimerController.get(context)
        controller.setPomodoroAlertSettings(PomodoroAlertSettings(autoStartNext = true))
        finishPomodoroFocusRound(controller)

        controller.cancelAutoStart()
        currentTime += 10_000L
        val autoStart = Intent(context, TimerActionReceiver::class.java).apply {
            action = TimerActionReceiver.ACTION_AUTO_START_NEXT
        }
        TimerActionReceiver().onReceive(context, autoStart)

        assertFalse(TimerLogic.isRunning(controller.state.value))
        assertEquals(PomodoroPhase.FOCUS, controller.state.value.pomodoroPhase)
    }

    @Test
    fun stopOnTheAlertAlsoCancelsAWaitingAutoStart() {
        val controller = TimerController.get(context)
        controller.setPomodoroAlertSettings(PomodoroAlertSettings(autoStartNext = true))
        finishPomodoroFocusRound(controller)
        assertTrue(controller.autoStartAtMillis.value != null)

        val stop = Intent(context, TimerActionReceiver::class.java).apply {
            action = TimerActionReceiver.ACTION_STOP_COUNTDOWN
        }
        TimerActionReceiver().onReceive(context, stop)

        assertNull(controller.autoStartAtMillis.value)
        assertEquals(0, shadowNotificationManager.allNotifications.size)
    }
}
