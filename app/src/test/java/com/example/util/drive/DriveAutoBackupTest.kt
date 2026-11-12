package com.example.util.drive

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.work.NetworkType
import com.example.data.local.AppDatabase
import com.example.data.local.Course
import com.example.util.NotificationHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class FakeAutoBackupAuth(
    var result: DriveAuthResult = DriveAuthResult.Granted("fake_token_123", "student@example.com")
) : DriveAuthApi {
    val clearedTokens = mutableListOf<String>()
    var authorizeCalls = 0

    override suspend fun authorize(): DriveAuthResult {
        authorizeCalls++
        return result
    }

    override fun resultFromIntent(data: Intent?): DriveAuthResult = result

    override suspend fun clearToken(token: String) {
        clearedTokens.add(token)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DriveAutoBackupTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var prefs: DrivePrefs
    private lateinit var fakeAuth: FakeAutoBackupAuth
    private lateinit var fakeStore: FakeDriveStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = AppDatabase.getDatabase(context)
        runBlocking(Dispatchers.IO) { db.clearAllTables() }

        context.getSharedPreferences("drive_backup_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        NotificationHistory.clear(context)

        prefs = DrivePrefs(context)
        fakeAuth = FakeAutoBackupAuth()
        fakeStore = FakeDriveStore()
    }

    @After
    fun tearDown() {
        db.close()
        AppDatabase::class.java.getDeclaredField("INSTANCE")
            .apply { isAccessible = true }
            .set(null, null)
        context.getSharedPreferences("drive_backup_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        NotificationHistory.clear(context)
    }

    @Test
    fun shouldRun_truthTable() {
        val now = 1_000_000_000_000L
        val h23 = 23L * 3600 * 1000L
        val h25 = 25L * 3600 * 1000L
        val h5 = 5L * 3600 * 1000L
        val h7 = 7L * 3600 * 1000L

        // Not connected
        assertFalse(
            DriveAutoBackup.shouldRun(
                connected = false,
                autoEnabled = true,
                lastAttemptAt = now - h25,
                lastSuccessAt = 0L,
                consecutiveFailures = 0,
                now = now
            )
        )

        // Auto off
        assertFalse(
            DriveAutoBackup.shouldRun(
                connected = true,
                autoEnabled = false,
                lastAttemptAt = now - h25,
                lastSuccessAt = 0L,
                consecutiveFailures = 0,
                now = now
            )
        )

        // 23 hours old (less than 24 hours)
        assertFalse(
            DriveAutoBackup.shouldRun(
                connected = true,
                autoEnabled = true,
                lastAttemptAt = now - h23,
                lastSuccessAt = 0L,
                consecutiveFailures = 0,
                now = now
            )
        )

        // 25 hours old (greater than 24 hours)
        assertTrue(
            DriveAutoBackup.shouldRun(
                connected = true,
                autoEnabled = true,
                lastAttemptAt = now - h25,
                lastSuccessAt = 0L,
                consecutiveFailures = 0,
                now = now
            )
        )

        // Failed attempt after 5 hours (less than 6 hours)
        assertFalse(
            DriveAutoBackup.shouldRun(
                connected = true,
                autoEnabled = true,
                lastAttemptAt = now - h5,
                lastSuccessAt = 0L,
                consecutiveFailures = 1,
                now = now
            )
        )

        // Failed attempt after 7 hours (greater than 6 hours)
        assertTrue(
            DriveAutoBackup.shouldRun(
                connected = true,
                autoEnabled = true,
                lastAttemptAt = now - h7,
                lastSuccessAt = 0L,
                consecutiveFailures = 1,
                now = now
            )
        )

        // Clock moved back (lastAttemptAt in future by more than 1 minute)
        assertFalse(
            DriveAutoBackup.shouldRun(
                connected = true,
                autoEnabled = true,
                lastAttemptAt = now + 120_000L,
                lastSuccessAt = 0L,
                consecutiveFailures = 0,
                now = now
            )
        )
    }

    @Test
    fun runOnce_unchangedHash_makesNoNetworkOrAuthorizationCall() = runBlocking {
        db.courseDao().insertCourse(Course(name = "Math", code = "MATH 101", colorHex = "#123456"))

        val localHash = DriveBackupManager.computeLocalDataHash(context)
        prefs.lastHash = localHash

        fakeAuth.authorizeCalls = 0
        fakeStore.callLog.clear()

        val now = 2_000_000_000_000L
        val outcome = DriveAutoBackup.runOnce(context, fakeAuth, { fakeStore }, now)

        assertEquals(DriveAutoBackup.Outcome.Unchanged, outcome)
        assertEquals(0, fakeAuth.authorizeCalls)
        assertTrue(fakeStore.callLog.isEmpty())
        assertEquals(now, prefs.lastAttemptAt)
    }

    @Test
    fun runOnce_needsConsent_givesNeedsReconnect_setsLastError_doesNotCallStore() = runBlocking {
        db.courseDao().insertCourse(Course(name = "Math", code = "MATH 101", colorHex = "#123456"))
        prefs.lastHash = null

        val pending = PendingIntent.getActivity(
            context,
            0,
            Intent(),
            PendingIntent.FLAG_IMMUTABLE
        )
        fakeAuth.result = DriveAuthResult.NeedsConsent(pending)
        fakeStore.callLog.clear()

        val now = 2_000_000_000_000L
        val outcome = DriveAutoBackup.runOnce(context, fakeAuth, { fakeStore }, now)

        assertEquals(DriveAutoBackup.Outcome.NeedsReconnect, outcome)
        assertEquals("Google Drive access was lost. Reconnect in Settings.", prefs.lastError)
        assertTrue(fakeStore.callLog.isEmpty())
        assertEquals(1, prefs.consecutiveFailures)
    }

    @Test
    fun runOnce_networkFailure_givesRetry() = runBlocking {
        db.courseDao().insertCourse(Course(name = "Math", code = "MATH 101", colorHex = "#123456"))
        prefs.lastHash = null

        fakeAuth.result = DriveAuthResult.Granted("token_123", "student@example.com")
        fakeStore.defaultException = DriveException(DriveException.Kind.NETWORK, "Connection timed out")
        fakeStore.failOnMethod = "create"

        val now = 2_000_000_000_000L
        val outcome = DriveAutoBackup.runOnce(context, fakeAuth, { fakeStore }, now)

        assertEquals(DriveAutoBackup.Outcome.Retry, outcome)
        assertEquals(1, prefs.consecutiveFailures)
    }

    @Test
    fun runOnce_quotaFailure_givesFailed() = runBlocking {
        db.courseDao().insertCourse(Course(name = "Math", code = "MATH 101", colorHex = "#123456"))
        prefs.lastHash = null

        fakeAuth.result = DriveAuthResult.Granted("token_123", "student@example.com")
        fakeStore.defaultException = DriveException(DriveException.Kind.QUOTA, "Storage quota exceeded")
        fakeStore.failOnMethod = "create"

        val now = 2_000_000_000_000L
        val outcome = DriveAutoBackup.runOnce(context, fakeAuth, { fakeStore }, now)

        assertEquals(DriveAutoBackup.Outcome.Failed, outcome)
        assertEquals(1, prefs.consecutiveFailures)
    }

    @Test
    fun runOnce_success_updatesLastSuccessAtAndLastHash() = runBlocking {
        db.courseDao().insertCourse(Course(name = "Math", code = "MATH 101", colorHex = "#123456"))
        prefs.lastHash = null

        fakeAuth.result = DriveAuthResult.Granted("token_123", "student@example.com")
        
        val before = System.currentTimeMillis()
        val now = 2_000_000_000_000L
        val outcome = DriveAutoBackup.runOnce(context, fakeAuth, { fakeStore }, now)

        assertEquals(DriveAutoBackup.Outcome.Done, outcome)
        assertTrue(prefs.lastSuccessAt >= before)
        assertNotNull(prefs.lastHash)
        assertEquals(0, prefs.consecutiveFailures)
        assertFalse(prefs.failureNotified)
    }

    @Test
    fun runOnce_consecutiveFailures_notifications() = runBlocking {
        db.courseDao().insertCourse(Course(name = "Math", code = "MATH 101", colorHex = "#123456"))
        prefs.lastHash = null

        fakeAuth.result = DriveAuthResult.Granted("token_123", "student@example.com")
        fakeStore.defaultException = DriveException(DriveException.Kind.NETWORK, "Network down")
        fakeStore.failOnMethod = "create"

        val now = 2_000_000_000_000L
        val day = 24L * 60 * 60 * 1000L

        // Attempt 1 failure: starts the failure clock
        DriveAutoBackup.runOnce(context, fakeAuth, { fakeStore }, now)
        assertEquals(1, prefs.consecutiveFailures)
        assertFalse(prefs.failureNotified)
        assertEquals(0, NotificationHistory.items(context).size)

        // Attempt 2 failure
        DriveAutoBackup.runOnce(context, fakeAuth, { fakeStore }, now + day)
        assertEquals(2, prefs.consecutiveFailures)
        assertFalse(prefs.failureNotified)
        assertEquals(0, NotificationHistory.items(context).size)

        // Attempt 3 failure after only 2 days: 3 failures but not 3 days yet, so no notification
        DriveAutoBackup.runOnce(context, fakeAuth, { fakeStore }, now + 2 * day)
        assertEquals(3, prefs.consecutiveFailures)
        assertFalse(prefs.failureNotified)
        assertEquals(0, NotificationHistory.items(context).size)

        // Attempt 4 failure, 3 days after the first: triggers in-app bell notification
        DriveAutoBackup.runOnce(context, fakeAuth, { fakeStore }, now + 3 * day)
        assertEquals(4, prefs.consecutiveFailures)
        assertTrue(prefs.failureNotified)
        val items = NotificationHistory.items(context)
        assertEquals(1, items.size)
        assertEquals("Google Drive backup has not worked for 3 days. Open Settings, Backup & data.", items[0].body)

        // Attempt 5 failure: does not add another notification
        DriveAutoBackup.runOnce(context, fakeAuth, { fakeStore }, now + 4 * day)
        assertEquals(5, prefs.consecutiveFailures)
        assertEquals(1, NotificationHistory.items(context).size)
        // Subsequent success: resets consecutiveFailures, failureNotified and the failure clock
        fakeStore.defaultException = DriveException(DriveException.Kind.SERVER, "none")
        fakeStore.failOnMethod = null
        val successOutcome = DriveAutoBackup.runOnce(context, fakeAuth, { fakeStore }, now + 5 * day)
        assertEquals(DriveAutoBackup.Outcome.Done, successOutcome)
        assertEquals(0, prefs.consecutiveFailures)
        assertFalse(prefs.failureNotified)
        assertEquals(0L, prefs.firstFailureAt)
    }

    @Test
    fun constraints_andOnAppOpen() {
        val wifiConstraints = DriveAutoBackup.buildConstraints(wifiOnly = true)
        assertEquals(NetworkType.UNMETERED, wifiConstraints.requiredNetworkType)

        val anyConstraints = DriveAutoBackup.buildConstraints(wifiOnly = false)
        assertEquals(NetworkType.CONNECTED, anyConstraints.requiredNetworkType)

        // When auto is disabled, shouldRun returns false
        assertFalse(
            DriveAutoBackup.shouldRun(
                connected = true,
                autoEnabled = false,
                lastAttemptAt = 0L,
                lastSuccessAt = 0L,
                consecutiveFailures = 0,
                now = System.currentTimeMillis()
            )
        )

        // onAppOpen when auto is disabled executes cleanly without scheduling work
        prefs.autoEnabled = false
        DriveAutoBackup.onAppOpen(context)
    }

    @Test
    fun shouldRun_dirtyData_isDueRightAway_unlessRecentFailures() {
        val now = 1_000_000_000_000L
        val h1 = 1L * 3600 * 1000L
        val h5 = 5L * 3600 * 1000L
        val h7 = 7L * 3600 * 1000L

        // Changed data 1 hour after the last attempt: due, the 24 hour wait does not apply
        assertTrue(
            DriveAutoBackup.shouldRun(
                connected = true, autoEnabled = true,
                lastAttemptAt = now - h1, lastSuccessAt = now - h1,
                consecutiveFailures = 0, now = now, dirty = true
            )
        )
        // Same data, not changed: not due
        assertFalse(
            DriveAutoBackup.shouldRun(
                connected = true, autoEnabled = true,
                lastAttemptAt = now - h1, lastSuccessAt = now - h1,
                consecutiveFailures = 0, now = now, dirty = false
            )
        )
        // Changed data but the last run failed 5 hours ago: waits for the 6 hour retry window
        assertFalse(
            DriveAutoBackup.shouldRun(
                connected = true, autoEnabled = true,
                lastAttemptAt = now - h5, lastSuccessAt = 0L,
                consecutiveFailures = 1, now = now, dirty = true
            )
        )
        assertTrue(
            DriveAutoBackup.shouldRun(
                connected = true, autoEnabled = true,
                lastAttemptAt = now - h7, lastSuccessAt = 0L,
                consecutiveFailures = 1, now = now, dirty = true
            )
        )
        // Dirty never overrides "not connected" or "auto off"
        assertFalse(
            DriveAutoBackup.shouldRun(
                connected = false, autoEnabled = true,
                lastAttemptAt = 0L, lastSuccessAt = 0L,
                consecutiveFailures = 0, now = now, dirty = true
            )
        )
        assertFalse(
            DriveAutoBackup.shouldRun(
                connected = true, autoEnabled = false,
                lastAttemptAt = 0L, lastSuccessAt = 0L,
                consecutiveFailures = 0, now = now, dirty = true
            )
        )
    }

    @Test
    fun onDataChanged_marksDirty_onlyWhenAutoBackupIsOn() {
        prefs.connectedEmail = "student@example.com"
        prefs.autoEnabled = false
        DriveAutoBackup.onDataChanged(context)
        assertFalse(prefs.dirty)

        prefs.autoEnabled = true
        DriveAutoBackup.onDataChanged(context)
        assertTrue(prefs.dirty)
    }

    @Test
    fun runOnce_clearsDirty_onSuccess_andKeepsItOnFailure() = runBlocking {
        db.courseDao().insertCourse(Course(name = "Math", code = "MATH 101", colorHex = "#123456"))
        prefs.lastHash = null
        prefs.dirty = true

        fakeAuth.result = DriveAuthResult.Granted("token_123", "student@example.com")
        val now = 2_000_000_000_000L
        assertEquals(DriveAutoBackup.Outcome.Done, DriveAutoBackup.runOnce(context, fakeAuth, { fakeStore }, now))
        assertFalse(prefs.dirty)
        assertTrue(prefs.lastSafeAt > 0L)

        // A later change whose upload fails stays marked as changed for the retry
        db.courseDao().insertCourse(Course(name = "Physics", code = "PHY 101", colorHex = "#654321"))
        prefs.dirty = true
        fakeStore.defaultException = DriveException(DriveException.Kind.NETWORK, "Network down")
        fakeStore.failOnMethod = "upload"
        val outcome = DriveAutoBackup.runOnce(context, fakeAuth, { fakeStore }, now + 1000L)
        assertEquals(DriveAutoBackup.Outcome.Retry, outcome)
        assertTrue(prefs.dirty)
    }

    @Test
    fun runOnce_unchanged_refreshesLastSafeAt() = runBlocking {
        db.courseDao().insertCourse(Course(name = "Math", code = "MATH 101", colorHex = "#123456"))
        prefs.lastHash = DriveBackupManager.computeLocalDataHash(context)
        prefs.lastSafeAt = 0L

        val before = System.currentTimeMillis()
        val now = 2_000_000_000_000L
        assertEquals(DriveAutoBackup.Outcome.Unchanged, DriveAutoBackup.runOnce(context, fakeAuth, { fakeStore }, now))
        assertEquals(now, prefs.lastSafeAt)
        assertTrue(prefs.lastSafeAt > before)
    }
}
