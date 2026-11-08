package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.onAllNodesWithTag
import kotlinx.coroutines.withContext
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.Course
import com.example.data.local.Note
import com.example.data.local.TaskStatus
import com.example.data.local.TaskItem
import com.example.data.local.MY_TASKS_COURSE_CODE
import com.example.ui.screens.DeadlinesTab
import com.example.ui.screens.DriveBackupViewModel
import com.example.ui.screens.TaskSortOption
import com.example.ui.theme.MyApplicationTheme
import com.example.util.drive.DriveAuthApi
import com.example.util.drive.DriveAuthResult
import com.example.util.drive.DriveBackupManager
import com.example.util.drive.DrivePrefs
import com.example.util.drive.FakeDriveStore
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

class FakeOfferTestDriveAuth(
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
class DriveRestoreOfferCardTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private lateinit var prefs: DrivePrefs
    private lateinit var fakeAuth: FakeOfferTestDriveAuth
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
        fakeAuth = FakeOfferTestDriveAuth()
        fakeStore = FakeDriveStore()
        viewModel = DriveBackupViewModel(app, fakeAuth, { fakeStore }, prefs)
    }

    @After
    fun tearDown() {
        // Finish the activity to destroy the Compose composition and cancel Room Flow collectors.
        composeRule.activity.finish()
        composeRule.waitForIdle()

        db.close()
        AppDatabase::class.java.getDeclaredField("INSTANCE")
            .apply { isAccessible = true }
            .set(null, null)
        app.getSharedPreferences("drive_backup_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    // Changing this value rebuilds the Deadlines screen from scratch (simulates a recreated screen).
    private val screenKey = mutableStateOf(0)

    private fun setDeadlinesContent() {
        composeRule.setContent {
            MyApplicationTheme {
                key(screenKey.value) {
                    val tasks by db.taskDao().getAllTasksWithCourse().collectAsState(initial = emptyList())
                    val courses by db.courseDao().getAllCourses().collectAsState(initial = emptyList())
                    DeadlinesTab(
                        tasks = tasks,
                        courses = courses,
                        searchQuery = "",
                        onSearchChange = {},
                        statusFilter = null,
                        onStatusFilterSelect = {},
                        sourceFilter = null,
                        onSourceFilterSelect = {},
                        courseFilter = null,
                        onCourseFilterSelect = {},
                        sortOption = TaskSortOption.DUE_DATE_ASC,
                        onSortOptionSelect = {},
                        pendingCount = tasks.count { it.task.status != TaskStatus.COMPLETED },
                        completedCount = tasks.count { it.task.status == TaskStatus.COMPLETED },
                        onStatusCycle = {},
                        onEditTask = {},
                        onDeleteTask = {},
                        onAddTaskClick = {},
                        driveBackupViewModel = viewModel
                    )
                }
            }
        }
    }

    private fun waitForOfferCard(visible: Boolean) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("drive_restore_offer_card")
                .fetchSemanticsNodes().isNotEmpty() == visible
        }
    }

    @Test
    fun showsOnEmptyApp_andNotWhenCourseTaskOrNoteExists() = runBlocking<Unit> {
        setDeadlinesContent()
        composeRule.waitForIdle()

        // 1. Shows on completely empty app
        composeRule.onNodeWithTag("drive_restore_offer_card").assertIsDisplayed()
        composeRule.onNodeWithText("Had UniTask on another phone?").assertIsDisplayed()
        composeRule.onNodeWithTag("drive_offer_restore_button").assertIsDisplayed()
        composeRule.onNodeWithTag("drive_offer_dismiss_button").assertIsDisplayed()

        // 2. Only My tasks course exists: should still show
        val myTasksCourse = Course(name = "My tasks", code = MY_TASKS_COURSE_CODE, colorHex = "#888888")
        val myTasksId = db.courseDao().insertCourse(myTasksCourse)
        composeRule.waitForIdle()
        waitForOfferCard(true)
        composeRule.onNodeWithTag("drive_restore_offer_card").assertIsDisplayed()

        // 3. Regular course added: card must disappear
        val physicsCourse = Course(name = "Physics", code = "PHYS 101", colorHex = "#123456")
        val physicsId = db.courseDao().insertCourse(physicsCourse)
        composeRule.waitForIdle()
        waitForOfferCard(false)
        composeRule.onNodeWithTag("drive_restore_offer_card").assertDoesNotExist()

        // Remove regular course: card appears again
        db.courseDao().deleteCourse(physicsCourse.copy(id = physicsId))
        composeRule.waitForIdle()
        waitForOfferCard(true)
        composeRule.onNodeWithTag("drive_restore_offer_card").assertIsDisplayed()

        // 4. Task added: card must disappear
        val taskId = db.taskDao().insertTask(
            TaskItem(courseId = myTasksId, title = "Task 1", dueDateEpochMillis = 2000L)
        )
        composeRule.waitForIdle()
        waitForOfferCard(false)
        composeRule.onNodeWithTag("drive_restore_offer_card").assertDoesNotExist()

        // Remove task: card appears again
        db.taskDao().deleteTaskById(taskId)
        composeRule.waitForIdle()
        waitForOfferCard(true)
        composeRule.onNodeWithTag("drive_restore_offer_card").assertIsDisplayed()

        // 5. Note added: card must disappear
        val noteId = db.noteDao().insert(Note(title = "Shopping", body = "Milk", updatedAt = 2000L))
        composeRule.waitForIdle()
        waitForOfferCard(false)
        composeRule.onNodeWithTag("drive_restore_offer_card").assertDoesNotExist()

        // Remove note: card appears again
        db.noteDao().deleteForever(noteId)
        composeRule.waitForIdle()
        waitForOfferCard(true)
        composeRule.onNodeWithTag("drive_restore_offer_card").assertIsDisplayed()
    }

    @Test
    fun notNow_hidesCard_andRecreatedScreenStaysHidden() {
        setDeadlinesContent()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("drive_restore_offer_card").assertIsDisplayed()

        composeRule.onNodeWithTag("drive_offer_dismiss_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("drive_restore_offer_card").assertDoesNotExist()
        assertTrue(prefs.restoreOfferDismissed)

        // Recreate the screen (new composition): card must stay hidden
        composeRule.runOnIdle { screenKey.value += 1 }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("drive_restore_offer_card").assertDoesNotExist()
    }

    @Test
    fun restoreWithBackup_showsConfirmDialog_andRestoresData_cardDisappears() = runBlocking {
        // Prepare backup in fake store
        val courseDao = db.courseDao()
        val taskDao = db.taskDao()
        val cId = courseDao.insertCourse(Course(name = "Biology", code = "BIO 101", colorHex = "#4CAF50"))
        taskDao.insertTask(TaskItem(courseId = cId, title = "Read Chapter 1", dueDateEpochMillis = 5000L))

        viewModel.connect()
        composeRule.waitForIdle()
        viewModel.backUpNow(allowEmpty = false)
        composeRule.waitUntil(timeoutMillis = 10_000) { viewModel.uiState.value.latestBackupInfo != null }

        // Clear local database so it is a fresh phone
        withContext(Dispatchers.IO) { db.clearAllTables() }
        assertEquals(0, courseDao.getAllCoursesList().size)
        prefs.autoEnabled = false

        setDeadlinesContent()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("drive_restore_offer_card").assertIsDisplayed()

        // The button is disabled while the earlier connect/backup is still finishing,
        // and a click on a disabled button does nothing: wait until it is enabled.
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(hasTestTag("drive_offer_restore_button") and isEnabled())
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("drive_offer_restore_button").performClick()
        composeRule.waitForIdle()

        // Wait until the app has looked at Drive and decided to ask for confirmation.
        // On a timeout, report the app's real state so we can see what went wrong.
        try {
            composeRule.waitUntil(timeoutMillis = 10_000) {
                val s = viewModel.uiState.value
                s.showRestoreConfirm || s.showNoBackupDialog
            }
        } catch (e: Throwable) {
            throw AssertionError("Restore dialog never opened. State: ${viewModel.uiState.value}", e)
        }
        assertTrue("Wrong dialog. State: ${viewModel.uiState.value}", viewModel.uiState.value.showRestoreConfirm)

        // The dialog's Cancel button only exists while the dialog is open
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Cancel").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        // Confirm dialog shows the merge and settings sentences
        composeRule.onNodeWithText(
            "Items already on this phone are kept. Missing items are added. Items you deleted after this backup was made will come back. App settings from the backup replace the settings on this phone.",
            substring = true
        ).assertIsDisplayed()

        composeRule.onAllNodesWithText("Restore").onLast().performClick()
        composeRule.waitForIdle()

        // Restoring runs in the background: wait until the restored task shows up
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Read Chapter 1").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        // Restored items in DB and displayed in list
        assertEquals(1, courseDao.getAllCoursesList().size)
        composeRule.onNodeWithText("Read Chapter 1").assertIsDisplayed()

        // The offer card disappears by itself
        composeRule.onNodeWithTag("drive_restore_offer_card").assertDoesNotExist()

        // Daily backup was NOT turned on automatically
        assertFalse(prefs.autoEnabled)
    }

    @Test
    fun accountWithNoBackup_showsNoBackupFoundText_cardStays() = runBlocking<Unit> {
        fakeAuth.result = DriveAuthResult.Granted("token_empty", "empty@example.com")
        fakeStore.files.clear()

        setDeadlinesContent()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("drive_restore_offer_card").assertIsDisplayed()
        composeRule.onNodeWithTag("drive_offer_restore_button").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("No backup found in this Google account.")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onAllNodesWithText("No backup found in this Google account.").onLast().assertIsDisplayed()
        composeRule.onNodeWithTag("drive_restore_offer_card").assertIsDisplayed()
    }
}
