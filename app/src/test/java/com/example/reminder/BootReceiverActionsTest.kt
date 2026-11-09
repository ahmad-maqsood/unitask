package com.example.reminder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootReceiverActionsTest {

    @Test
    fun normalAndFastBootCountAsStart() {
        assertTrue(BootReceiver.isStartAction("android.intent.action.BOOT_COMPLETED"))
        assertTrue(BootReceiver.isStartAction("android.intent.action.MY_PACKAGE_REPLACED"))
        assertTrue(BootReceiver.isStartAction("android.intent.action.QUICKBOOT_POWERON"))
        assertTrue(BootReceiver.isStartAction("com.htc.intent.action.QUICKBOOT_POWERON"))
    }

    @Test
    fun clockChangesAndExactAlarmPermissionResetAlarmsButAreNotAStart() {
        val others = listOf(
            "android.intent.action.TIMEZONE_CHANGED",
            "android.intent.action.TIME_SET",
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
        )
        others.forEach {
            assertTrue("$it should reset the alarms", BootReceiver.handles(it))
            assertFalse("$it must not announce missed reminders", BootReceiver.isStartAction(it))
        }
    }

    @Test
    fun otherBroadcastsAreIgnored() {
        assertFalse(BootReceiver.handles(null))
        assertFalse(BootReceiver.handles("android.intent.action.AIRPLANE_MODE"))
    }

    @Test
    fun everyHandledActionIsInTheManifest() {
        // Gradle runs unit tests from the app folder, other tools from the project root.
        val manifest = listOf(
            java.io.File("src/main/AndroidManifest.xml"),
            java.io.File("app/src/main/AndroidManifest.xml")
        ).first { it.exists() }.readText()
        listOf(
            BootReceiver.ACTION_QUICKBOOT,
            BootReceiver.ACTION_HTC_QUICKBOOT,
            BootReceiver.ACTION_EXACT_ALARM_ALLOWED,
            "android.intent.action.BOOT_COMPLETED",
            "android.intent.action.MY_PACKAGE_REPLACED",
            "android.intent.action.TIMEZONE_CHANGED",
            "android.intent.action.TIME_SET"
        ).forEach { assertTrue("$it missing from the manifest", manifest.contains("\"$it\"")) }
    }
}
