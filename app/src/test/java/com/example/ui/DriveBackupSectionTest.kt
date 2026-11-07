package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.Course
import com.example.data.local.TaskItem
import com.example.ui.screens.DriveBackupSection
import com.example.ui.screens.DriveBackupViewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.util.drive.DriveAuthApi
import com.example.util.drive.DriveAuthResult
import com.example.util.drive.DriveException
import com.example.util.drive.DrivePrefs
import com.example.util.drive.DriveStore
import com.example.util.drive.FakeDriveStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class FakeTestDriveAuth(
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
class DriveBackupSectionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private lateinit var prefs: DrivePrefs
    private lateinit var fakeAuth: FakeTestDriveAuth
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
        fakeAuth = FakeTestDriveAuth()
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

    private fun setUiContent() {
        composeRule.setContent {
            MyApplicationTheme {
                DriveBackupSection(viewModel = viewModel)
            }
        }
    }

    private fun waitForResultText() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("drive_result_text").fetchSemanticsNodes().isNotEmpty()
        }
    }
    
    private fun waitForConnectedUi() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("drive_backup_now_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    @Test
    fun notConnected_showsConnectButton() {
        setUiContent()
        composeRule.onNodeWithTag("drive_connect_button").assertIsDisplayed()
        composeRule.onNodeWithText("Connect Google Drive").assertIsDisplayed()
    }

    @Test
    fun afterGrantedAuth_showsConnectedRowsWithEmail() {
        setUiContent()
        composeRule.onNodeWithTag("drive_connect_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("drive_connect_button").performClick()
        composeRule.waitForIdle()
        waitForConnectedUi()

        composeRule.onNodeWithText("student@example.com").assertIsDisplayed()
        composeRule.onNodeWithText("student@example.com").assertIsDisplayed()
        composeRule.onNodeWithTag("drive_backup_now_button").assertIsDisplayed()
        composeRule.onNodeWithTag("drive_restore_button").assertIsDisplayed()
        composeRule.onNodeWithTag("drive_auto_switch").assertIsDisplayed()
        composeRule.onNodeWithTag("drive_wifi_switch").assertIsDisplayed()
        composeRule.onNodeWithTag("drive_attachments_switch").assertIsDisplayed()
        composeRule.onNodeWithTag("drive_disconnect_button").assertIsDisplayed()
    }

    @Test
    fun backupNowOnEmptyApp_showsWarning_andCancelWritesNothing() = runBlocking {
        viewModel.connect()
        setUiContent()
        composeRule.waitForIdle()
        waitForConnectedUi()

        composeRule.onNodeWithTag("drive_backup_now_button").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Your app has no data.", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Your app has no data. Backing up now would replace your Drive backup with an empty one.")
            .assertIsDisplayed()

        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()

        assertTrue(fakeStore.files.isEmpty())
    }

    @Test
    fun backupNowWithData_showsSuccessTextWithCounts() = runBlocking<Unit> {
        val courseDao = db.courseDao()
        val taskDao = db.taskDao()
        val cId = courseDao.insertCourse(Course(name = "Math", code = "MATH 101", colorHex = "#123456"))
        taskDao.insertTask(TaskItem(courseId = cId, title = "Task 1", dueDateEpochMillis = 1000L))

        viewModel.connect()
        setUiContent()
        composeRule.waitForIdle()
        waitForConnectedUi()

        composeRule.onNodeWithTag("drive_backup_now_button").performClick()
        composeRule.waitForIdle()
        
        waitForResultText()
        composeRule.onNodeWithTag("drive_result_text").assertIsDisplayed()
        composeRule.onNodeWithText("Backup complete: 1 courses, 1 tasks, 0 notes.").assertIsDisplayed()
    }

    @Test
    fun restoreWithNoBackup_showsNoBackupFoundText() = runBlocking<Unit> {
        viewModel.connect()
        setUiContent()
        composeRule.waitForIdle()
        waitForConnectedUi()

        composeRule.onNodeWithTag("drive_restore_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("No backup found in this Google account.").assertIsDisplayed()
        composeRule.onNodeWithText("OK").performClick()
    }

    @Test
    fun restoreWithBackup_showsConfirmDialog_andRestores() = runBlocking<Unit> {
        val courseDao = db.courseDao()
        val taskDao = db.taskDao()
        val cId = courseDao.insertCourse(Course(name = "Math", code = "MATH 101", colorHex = "#123456"))
        taskDao.insertTask(TaskItem(courseId = cId, title = "Task 1", dueDateEpochMillis = 1000L))

        // First do backup to populate Drive
        viewModel.connect()
        composeRule.waitForIdle()
        waitForConnectedUi()
        viewModel.backUpNow(allowEmpty = false)
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 10_000) { viewModel.uiState.value.latestBackupInfo != null }

        // Clear local database to simulate fresh app
        withContext(Dispatchers.IO) { db.clearAllTables() }
        assertEquals(0, courseDao.getAllCoursesList().size)

        setUiContent()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("drive_restore_button").performClick()
        composeRule.waitForIdle()

        // Confirm dialog shows the merge and settings sentences
        composeRule.onNodeWithText(
            "Items already on this phone are kept. Missing items are added. Items you deleted after this backup was made will come back. App settings from the backup replace the settings on this phone.",
            substring = true
        ).assertIsDisplayed()

        composeRule.onNodeWithText("Restore").performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Restored", substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Restored items in DB
        assertEquals(1, courseDao.getAllCoursesList().size)
        waitForResultText()
        composeRule.onNodeWithTag("drive_result_text").assertIsDisplayed()
    }

    @Test
    fun switches_defaultValues_andSave() {
        viewModel.connect()
        setUiContent()
        composeRule.waitForIdle()
        waitForConnectedUi()

        // Defaults
        composeRule.onNodeWithTag("drive_auto_switch").assertIsOff()
        composeRule.onNodeWithTag("drive_wifi_switch").assertIsOn()
        composeRule.onNodeWithTag("drive_attachments_switch").assertIsOn()

        // Toggle auto
        composeRule.onNodeWithTag("drive_auto_switch").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("drive_auto_switch").assertIsOn()
        assertTrue(prefs.autoEnabled)

        // Toggle wifi
        composeRule.onNodeWithTag("drive_wifi_switch").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("drive_wifi_switch").assertIsOff()
        assertFalse(prefs.wifiOnly)

        // Toggle attachments
        composeRule.onNodeWithTag("drive_attachments_switch").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("drive_attachments_switch").assertIsOff()
        assertFalse(prefs.includeAttachments)
    }

    @Test
    fun disconnect_asksForConfirmation_thenShowsNotConnected() {
        viewModel.connect()
        setUiContent()
        composeRule.waitForIdle()
        waitForConnectedUi()

        composeRule.onNodeWithTag("drive_disconnect_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Your backup stays in your Google Drive. To delete it: Google Drive settings, Manage apps, UniTask, Delete hidden app data.")
            .assertIsDisplayed()

        composeRule.onAllNodesWithText("Disconnect").onLast().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("drive_connect_button").assertIsDisplayed()
        assertFalse(prefs.isConnected)
    }

    @Test
    fun authFailure_showsReconnect() = runBlocking<Unit> {
        val courseDao = db.courseDao()
        val cId = courseDao.insertCourse(Course(name = "Math", code = "MATH 101", colorHex = "#123456"))
        db.taskDao().insertTask(TaskItem(courseId = cId, title = "Task 1", dueDateEpochMillis = 1000L))

        viewModel.connect()
        setUiContent()
        composeRule.waitForIdle()
        waitForConnectedUi()

        fakeStore.defaultException = DriveException(DriveException.Kind.AUTH, "Auth token expired")
        fakeStore.failOnMethod = "create"

        composeRule.onNodeWithTag("drive_backup_now_button").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Reconnect").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Google Drive access was lost.").assertIsDisplayed()
        composeRule.onNodeWithText("Reconnect").assertIsDisplayed()
    }

    @Test
    fun quotaFailure_showsYourGoogleDriveIsFull() = runBlocking<Unit> {
        val courseDao = db.courseDao()
        val taskDao = db.taskDao()
        val cId = courseDao.insertCourse(Course(name = "Math", code = "MATH 101", colorHex = "#123456"))
        taskDao.insertTask(TaskItem(courseId = cId, title = "Task 1", dueDateEpochMillis = 1000L))

        viewModel.connect()
        fakeStore.defaultException = DriveException(DriveException.Kind.QUOTA, "Storage quota exceeded")
        fakeStore.failOnMethod = "create"

        setUiContent()
        composeRule.waitForIdle()
        waitForConnectedUi()

        composeRule.onNodeWithTag("drive_backup_now_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("drive_result_text").assertIsDisplayed()
        composeRule.onNodeWithText("Your Google Drive is full.").assertIsDisplayed()
    }

    @Test
    fun progressText_appearsWhileStorePausedMidUpload() = runBlocking {
        val courseDao = db.courseDao()
        val taskDao = db.taskDao()
        val cId = courseDao.insertCourse(Course(name = "Math", code = "MATH 101", colorHex = "#123456"))
        taskDao.insertTask(TaskItem(courseId = cId, title = "Task 1", dueDateEpochMillis = 1000L))

        viewModel.connect()

        val observedProgress = CompletableDeferred<String?>()
        fakeStore.onUpload = {
            observedProgress.complete(viewModel.uiState.value.busy?.progressText)
        }

        viewModel.backUpNow(allowEmpty = false)
        val progress = observedProgress.await()
        assertNotNull(progress)
        assertTrue(progress!!.startsWith("Backing up ("))
    }
}
