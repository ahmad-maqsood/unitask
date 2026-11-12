package com.example.util.drive

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.Course
import com.example.data.local.TaskItem
import com.example.ui.screens.DriveBackupViewModel
import com.example.ui.screens.DriveMigrationDialog
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class FakeMigrationTestDriveAuth(
    var result: DriveAuthResult = DriveAuthResult.Granted("fake_token_123", "student@example.com")
) : DriveAuthApi {
    val clearedTokens = mutableListOf<String>()

    override suspend fun authorize(): DriveAuthResult = result

    override fun resultFromIntent(data: Intent?): DriveAuthResult = result

    override suspend fun clearToken(token: String) {
        clearedTokens.add(token)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h1600dp")
class DriveMigrationPromptTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private lateinit var prefs: DrivePrefs
    private lateinit var fakeAuth: FakeMigrationTestDriveAuth
    private lateinit var fakeStore: FakeDriveStore
    private lateinit var viewModel: DriveBackupViewModel

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        db = AppDatabase.getDatabase(app)
        runBlocking(Dispatchers.IO) { db.clearAllTables() }

        app.getSharedPreferences("drive_backup_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        prefs = DrivePrefs(app)
        fakeAuth = FakeMigrationTestDriveAuth()
        fakeStore = FakeDriveStore()
        viewModel = DriveBackupViewModel(app, fakeAuth, { fakeStore }, prefs)
    }

    @After
    fun tearDown() {
        db.close()
        AppDatabase::class.java.getDeclaredField("INSTANCE")
            .apply { isAccessible = true }
            .set(null, null)
        app.getSharedPreferences("drive_backup_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun truthTable_shouldPrompt() {
        val now = 100_000L

        // 1. No data
        assertFalse(
            DriveMigrationPrompt.shouldPrompt(
                hasData = false,
                usedCloudSync = true,
                driveConnected = false,
                promptCount = 0,
                nextPromptAt = 0L,
                now = now
            )
        )

        // 2. Never used cloud sync
        assertFalse(
            DriveMigrationPrompt.shouldPrompt(
                hasData = true,
                usedCloudSync = false,
                driveConnected = false,
                promptCount = 0,
                nextPromptAt = 0L,
                now = now
            )
        )

        // 3. Already connected to Drive
        assertFalse(
            DriveMigrationPrompt.shouldPrompt(
                hasData = true,
                usedCloudSync = true,
                driveConnected = true,
                promptCount = 0,
                nextPromptAt = 0L,
                now = now
            )
        )

        // 4. Max prompt count reached (3)
        assertFalse(
            DriveMigrationPrompt.shouldPrompt(
                hasData = true,
                usedCloudSync = true,
                driveConnected = false,
                promptCount = 3,
                nextPromptAt = 0L,
                now = now
            )
        )

        // 5. Exceeded max prompt count
        assertFalse(
            DriveMigrationPrompt.shouldPrompt(
                hasData = true,
                usedCloudSync = true,
                driveConnected = false,
                promptCount = 4,
                nextPromptAt = 0L,
                now = now
            )
        )

        // 6. Next prompt time in the future
        assertFalse(
            DriveMigrationPrompt.shouldPrompt(
                hasData = true,
                usedCloudSync = true,
                driveConnected = false,
                promptCount = 1,
                nextPromptAt = 100_001L,
                now = now
            )
        )

        // 7. Exactly at next prompt time
        assertTrue(
            DriveMigrationPrompt.shouldPrompt(
                hasData = true,
                usedCloudSync = true,
                driveConnected = false,
                promptCount = 1,
                nextPromptAt = now,
                now = now
            )
        )

        // 8. Qualifying user with count 0
        assertTrue(
            DriveMigrationPrompt.shouldPrompt(
                hasData = true,
                usedCloudSync = true,
                driveConnected = false,
                promptCount = 0,
                nextPromptAt = 0L,
                now = now
            )
        )

        // 9. Qualifying user with count 2 and past nextPromptAt
        assertTrue(
            DriveMigrationPrompt.shouldPrompt(
                hasData = true,
                usedCloudSync = true,
                driveConnected = false,
                promptCount = 2,
                nextPromptAt = 50_000L,
                now = now
            )
        )
    }

    @Test
    fun policy_afterLater() {
        val now = 10_000L
        val threeDaysMs = 3L * 24 * 60 * 60 * 1000L

        val (c1, t1) = DriveMigrationPrompt.afterLater(0, now)
        assertEquals(1, c1)
        assertEquals(now + threeDaysMs, t1)

        val (c2, t2) = DriveMigrationPrompt.afterLater(1, now + 500L)
        assertEquals(2, c2)
        assertEquals(now + 500L + threeDaysMs, t2)
    }

    @Test
    fun policy_afterNever() {
        assertEquals(3, DriveMigrationPrompt.afterNever(0))
        assertEquals(3, DriveMigrationPrompt.afterNever(1))
        assertEquals(3, DriveMigrationPrompt.afterNever(2))
    }

    @Test
    fun dialog_showsElementsForQualifyingUser() {
        composeRule.setContent {
            MyApplicationTheme {
                DriveMigrationDialog(
                    onDismiss = {},
                    viewModel = viewModel
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("drive_migration_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("Back up to Google Drive").assertIsDisplayed()
        composeRule.onNodeWithText(
            "UniTask no longer syncs your courses, tasks and notes to the online account. They stay on this phone. Back up to your own Google Drive so a lost or reset phone cannot take them with it."
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("drive_migration_backup_button").assertIsDisplayed()
        composeRule.onNodeWithTag("drive_migration_later_button").assertIsDisplayed()
        composeRule.onNodeWithText("Don't ask again").assertIsDisplayed()
    }

    @Test
    fun dialog_remindMeLater_updatesPrefsAndDismisses() {
        var dismissed = false
        val before = System.currentTimeMillis()
        val expectedMin = before + 3L * 24 * 60 * 60 * 1000L

        composeRule.setContent {
            MyApplicationTheme {
                DriveMigrationDialog(
                    onDismiss = { dismissed = true },
                    viewModel = viewModel
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("drive_migration_later_button").performClick()
        composeRule.waitForIdle()

        assertTrue(dismissed)
        assertEquals(1, prefs.migrationPromptCount)
        assertTrue(prefs.migrationNextPromptAt >= expectedMin)
    }

    @Test
    fun dialog_dontAskAgain_setsCountToMax() {
        var dismissed = false

        composeRule.setContent {
            MyApplicationTheme {
                DriveMigrationDialog(
                    onDismiss = { dismissed = true },
                    viewModel = viewModel
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Don't ask again").performClick()
        composeRule.waitForIdle()

        assertTrue(dismissed)
        assertEquals(3, prefs.migrationPromptCount)
    }

    @Test
    fun dialog_backUpNow_completesAndDoneSetsAutoEnabled() = runBlocking {
        val course = Course(name = "Algorithms", code = "CS 201", colorHex = "#123456")
        val courseId = db.courseDao().insertCourse(course)
        db.taskDao().insertTask(
            TaskItem(courseId = courseId, title = "Homework 1", dueDateEpochMillis = 5000L)
        )

        var dismissed = false
        composeRule.setContent {
            MyApplicationTheme {
                DriveMigrationDialog(
                    onDismiss = { dismissed = true },
                    viewModel = viewModel
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("drive_migration_backup_button").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("drive_migration_done_button").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Also back up automatically").assertIsDisplayed()
        composeRule.onNodeWithTag("drive_migration_auto_checkbox").assertIsDisplayed()

        composeRule.onNodeWithTag("drive_migration_done_button").performClick()
        composeRule.waitForIdle()

        assertTrue(dismissed)
        assertTrue(prefs.autoEnabled)
        assertEquals(3, prefs.migrationPromptCount)
    }
}
