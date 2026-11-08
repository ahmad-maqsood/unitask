package com.example

import android.graphics.Bitmap
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertIsDisplayed
import com.example.data.local.AppDatabase
import com.example.data.local.Course
import com.example.data.local.LectureSlot
import com.example.data.local.Note
import com.example.data.local.Priority
import com.example.data.local.TaskItem
import com.example.data.local.TaskStatus
import com.example.ui.theme.AccentTheme
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.ThemePreference
import com.example.util.Announcements
import com.example.util.SupabaseProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar

/** Captures real UniTask screens using isolated fictional data on an Android emulator. */
@RunWith(AndroidJUnit4::class)
class ReadmeScreenshotTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    @After
    fun closeActivity() {
        scenario?.close()
    }

    @Test
    fun captureReadmeScreenshots() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args: Bundle = InstrumentationRegistry.getArguments()
        val email = requireNotNull(args.getString("demoEmail")) { "Missing demoEmail instrumentation argument" }
        val password = requireNotNull(args.getString("demoPassword")) { "Missing demoPassword instrumentation argument" }
        val groupId = requireNotNull(args.getString("groupId")) { "Missing groupId instrumentation argument" }

        // Sign in before MainActivity starts so its normal observers initialise with the demo user.
        SupabaseProvider.client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }

        val context = instrumentation.targetContext
        context.getSharedPreferences("unitask_settings", android.content.Context.MODE_PRIVATE)
            .edit()
            .putBoolean("notification_permission_prompted", true)
            .putBoolean("background_setup_prompted", true)
            .apply()
        com.example.util.drive.DrivePrefs(context).restoreOfferDismissed = true

        // Mark current announcements as already seen so the "Messages from UniTask"
        // pop-up and the bell badge do not appear in the README screenshots.
        Announcements.refresh(context)
        Announcements.markSeen(context, Announcements.unseen(context))

        val database = AppDatabase.getDatabase(context)
        database.clearAllTables()
        val noteId = seedLocalDemoData(database)

        val outputDir = File(context.filesDir, "readme-screenshots")
        outputDir.mkdirs()
        outputDir.listFiles()?.forEach { it.delete() }

        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForTag("tab_deadlines")
        capture("deadlines")

        // Theme variation: the same Deadlines screen in dark mode. ThemePreference is what the
        // Settings screen changes, so the screen redraws at once. The default look (system
        // theme, Indigo) is restored before the main screenshots continue.
        setTheme(ThemeMode.DARK, AccentTheme.INDIGO)
        capture("theme_dark")
        setTheme(ThemeMode.SYSTEM, AccentTheme.INDIGO)

        tap("tab_timetable")
        capture("timetable")

        tap("tab_courses")
        capture("courses")

        tap("tab_groups")
        waitForGroupCard(groupId, timeoutMillis = 30_000)
        capture("groups")

        tap("group_item_$groupId")
        waitForTag("group_tab_chat")
        tap("group_tab_chat")
        waitForTag("group_chat_list")
        waitForText("See you in the library!", timeoutMillis = 30_000)
        capture("group_chat")
        tap("group_detail_back_button")

        tap("tab_mytasks")
        capture("my_tasks")

        // Theme variation: the same screen in light mode with the Rose accent colour.
        setTheme(ThemeMode.LIGHT, AccentTheme.ROSE)
        capture("theme_accent")
        setTheme(ThemeMode.SYSTEM, AccentTheme.INDIGO)

        // Study timer: open it from the Tools sheet and show a 25 minute countdown.
        tap("top_bar_tools_button")
        tap("tools_tile_timer")
        waitForTag("timer_screen")
        tap("timer_mode_countdown")
        tap("timer_preset_25")
        waitForText("25:00")
        capture("timer")
        tap("timer_back_button")
        waitForTagToDisappear("timer_screen")

        // Global search: open it from the Tools sheet and search for "design". That word
        // matches a task, a note and a course, so all three result sections are shown.
        tap("top_bar_tools_button")
        tap("tools_tile_search")
        waitForTag("search_screen")
        composeRule.onNodeWithTag("search_input", useUnmergedTree = true).performTextInput("design")
        waitForText("Courses (")
        hideKeyboard()
        capture("global_search")
        tap("search_back_button")
        waitForTagToDisappear("search_screen")

        // Recently deleted: open it from the Tools sheet. It opens on the Tasks tab, which
        // lists the deleted demo tasks seeded in seedLocalDemoData().
        tap("top_bar_tools_button")
        tap("tools_tile_recently_deleted")
        waitForTag("recently_deleted_screen")
        waitForTag("tasks_trash_list")
        capture("recently_deleted")
        tap("recently_deleted_back_button")
        waitForTagToDisappear("recently_deleted_screen")

        openNotes()
        waitForTag("notes_list")
        capture("notes")
        tap("note_card_$noteId")
        waitForTag("note_preview_title")
        capture("note_preview")
        tap("note_mode_edit_button")
        waitForTag("note_body_input")
        capture("note_editor")
        // Use the visible navigation controls so the emulator keyboard cannot consume
        // a Back press, and wait for the asynchronous note save before leaving Notes.
        tap("note_editor_back_button")
        waitForTag("notes_list")
        tap("notes_back_button")
        waitForTagToDisappear("notes_list")
        waitForTag("top_bar_settings_button")
        tap("top_bar_settings_button")
        waitForTag("settings_section_general")
        capture("settings_menu")

        tap("settings_section_general")
        waitForTag("settings_back_button")
        capture("settings_general")
        tap("settings_back_button")

        tap("settings_section_notifications")
        waitForTag("settings_back_button")
        capture("settings_notifications")
        tap("settings_back_button")

        tap("settings_section_backup")
        waitForTag("settings_back_button")
        capture("settings_backup")
        tap("settings_back_button")

        tap("settings_section_about")
        waitForTag("settings_back_button")
        capture("settings_about")
    }

    private suspend fun seedLocalDemoData(database: AppDatabase): Long {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val algorithmsId = database.courseDao().insertCourse(
            Course(
                name = "Data Structures",
                code = "CS201",
                instructor = "Dr. Sarah Ahmed",
                colorHex = "#536DFE",
                location = "Room 204",
                isSynced = true
            )
        )
        val databasesId = database.courseDao().insertCourse(
            Course(
                name = "Database Systems",
                code = "CS305",
                instructor = "Mr. Usman Khan",
                colorHex = "#009688",
                location = "Lab 3",
                isSynced = true
            )
        )
        val designId = database.courseDao().insertCourse(
            Course(
                name = "Software Design",
                code = "SE210",
                instructor = "Ms. Ayesha Malik",
                colorHex = "#E8794A",
                location = "Room 108",
                isSynced = true
            )
        )

        fun dayOffset(days: Int): Long =
            (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, days) }.timeInMillis

        database.taskDao().insertTask(
            TaskItem(
                courseId = algorithmsId,
                title = "Linked-list implementation",
                description = "Complete insertion, deletion and traversal methods.",
                dueDateEpochMillis = dayOffset(1),
                dueTimeHour = 23,
                dueTimeMinute = 59,
                priority = Priority.HIGH,
                isSynced = true
            )
        )
        database.taskDao().insertTask(
            TaskItem(
                courseId = databasesId,
                title = "ER diagram submission",
                description = "Submit the library management system diagram.",
                dueDateEpochMillis = dayOffset(3),
                dueTimeHour = 17,
                priority = Priority.MEDIUM,
                isSynced = true
            )
        )
        database.taskDao().insertTask(
            TaskItem(
                courseId = designId,
                title = "Read Chapter 4",
                dueDateEpochMillis = dayOffset(-1),
                status = TaskStatus.COMPLETED,
                priority = Priority.LOW,
                isSynced = true
            )
        )

        database.lectureSlotDao().insertSlot(
            LectureSlot(
                courseId = algorithmsId,
                dayOfWeek = 1,
                startHour = 9,
                startMinute = 0,
                endHour = 10,
                endMinute = 30,
                classroom = "Room 204",
                instructor = "Dr. Sarah Ahmed",
                isSynced = true
            )
        )
        database.lectureSlotDao().insertSlot(
            LectureSlot(
                courseId = databasesId,
                dayOfWeek = 2,
                startHour = 11,
                startMinute = 0,
                endHour = 12,
                endMinute = 30,
                classroom = "Lab 3",
                instructor = "Mr. Usman Khan",
                isSynced = true
            )
        )
        database.lectureSlotDao().insertSlot(
            LectureSlot(
                courseId = designId,
                dayOfWeek = 4,
                startHour = 13,
                startMinute = 0,
                endHour = 14,
                endMinute = 30,
                classroom = "Room 108",
                instructor = "Ms. Ayesha Malik",
                isSynced = true
            )
        )

        val myTasksCourseId = database.courseDao().insertCourse(
            Course(
                name = "My tasks",
                code = "MYTASKS",
                colorHex = "#7C3AED",
                isSynced = true
            )
        )
        database.taskDao().insertTask(
            TaskItem(
                courseId = myTasksCourseId,
                title = "Prepare presentation slides",
                description = "Add the architecture diagram and references.",
                dueDateEpochMillis = dayOffset(2),
                dueTimeHour = 18,
                priority = Priority.HIGH,
                isSynced = true
            )
        )
        database.taskDao().insertTask(
            TaskItem(
                courseId = myTasksCourseId,
                title = "Buy stationery",
                dueDateEpochMillis = dayOffset(4),
                priority = Priority.LOW,
                isSynced = true
            )
        )

        val noteId = database.noteDao().insert(
            Note(
                title = "Week 6 study notes",
                body = "# Week 6\n\n- Review linked lists\n- Practise SQL joins\n- Prepare the design assignment\n\n**Remember:** revise before Friday.",
                courseId = algorithmsId,
                isPinned = true,
                isSynced = true
            )
        )
        database.noteDao().insert(
            Note(
                title = "Project ideas",
                body = "Ideas for the semester project.",
                courseId = designId,
                isSynced = true
            )
        )

        // Items already in "Recently deleted". They are soft-deleted (isDeleted = true), so
        // every other screen hides them. Deletion dates stay inside the 30 day trash window.
        val now = System.currentTimeMillis()
        fun daysAgo(days: Int): Long = now - days * 24L * 60L * 60L * 1000L

        database.taskDao().insertTask(
            TaskItem(
                courseId = databasesId,
                title = "Old ER diagram draft",
                description = "Replaced by the final version.",
                dueDateEpochMillis = dayOffset(-2),
                dueTimeHour = 17,
                priority = Priority.MEDIUM,
                isSynced = true,
                isDeleted = true,
                deletedAt = daysAgo(2)
            )
        )
        database.taskDao().insertTask(
            TaskItem(
                courseId = algorithmsId,
                title = "Stack and queue worksheet",
                dueDateEpochMillis = dayOffset(5),
                dueTimeHour = 14,
                priority = Priority.HIGH,
                isSynced = true,
                isDeleted = true,
                deletedAt = daysAgo(6)
            )
        )
        database.taskDao().insertTask(
            TaskItem(
                courseId = designId,
                title = "UML class diagram exercise",
                dueDateEpochMillis = dayOffset(7),
                dueTimeHour = 12,
                priority = Priority.LOW,
                isSynced = true,
                isDeleted = true,
                deletedAt = daysAgo(11)
            )
        )
        database.noteDao().insert(
            Note(
                title = "Old lecture summary",
                body = "Draft summary that the Week 6 notes replaced.",
                courseId = algorithmsId,
                isSynced = true,
                isDeleted = true,
                deletedAt = daysAgo(3)
            )
        )
        database.lectureSlotDao().insertSlot(
            LectureSlot(
                courseId = designId,
                dayOfWeek = 5,
                startHour = 10,
                startMinute = 0,
                endHour = 11,
                endMinute = 30,
                classroom = "Room 108",
                instructor = "Ms. Ayesha Malik",
                isSynced = true,
                isDeleted = true,
                deletedAt = daysAgo(8)
            )
        )
        return noteId
    }

    private fun openNotes() {
        tap("top_bar_tools_button")
        tap("tools_tile_notes")
    }

    private fun tap(tag: String) {
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed().performClick()
        composeRule.waitForIdle()
    }

    /**
     * Hides the on-screen keyboard so it does not cover the search results. It sends a system
     * Back press: an open keyboard takes it and hides itself. If no keyboard is open, the search
     * screen only drops focus and stays open. The check afterwards fails loudly if the search
     * screen was closed by mistake, instead of capturing the wrong screen.
     */
    private fun hideKeyboard() {
        val output = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent 4")
        // Read to the end so the command has finished before we continue.
        ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("search_screen", useUnmergedTree = true).assertIsDisplayed()
    }

    /** Changes the theme the way the Settings screen does. The screen redraws straight away. */
    private fun setTheme(mode: ThemeMode, accent: AccentTheme) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.runOnMainSync {
            ThemePreference.set(context, mode)
            ThemePreference.setAccent(context, accent)
        }
        composeRule.waitForIdle()
    }

    private fun waitForGroupCard(groupId: String, timeoutMillis: Long) {
        val tag = "group_item_$groupId"
        try {
            composeRule.waitUntil(timeoutMillis) {
                composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (error: Exception) {
            val noGroupsVisible = composeRule.onAllNodesWithText(
                "No groups yet", substring = true
            ).fetchSemanticsNodes().isNotEmpty()
            val reconnectingVisible = composeRule.onAllNodesWithText(
                "Reconnecting...", substring = true
            ).fetchSemanticsNodes().isNotEmpty()
            val screenState = when {
                noGroupsVisible -> "The Groups screen is showing 'No groups yet'."
                reconnectingVisible -> "The Groups screen is still showing 'Reconnecting...'."
                else -> "The expected group card is absent and neither known state is visible."
            }
            throw AssertionError(
                "Timed out waiting for demo group $groupId. $screenState " +
                    "The seed preflight passed, so inspect app authentication and the " +
                    "GroupRepository membership refresh path.",
                error
            )
        }
    }

    private fun waitForTag(tag: String, timeoutMillis: Long = 15_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForTagToDisappear(tag: String, timeoutMillis: Long = 15_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun waitForText(value: String, timeoutMillis: Long = 15_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithText(value, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun capture(name: String) {
        composeRule.waitForIdle()
        Thread.sleep(700)
        val bitmap: Bitmap = requireNotNull(
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        ) { "Could not capture screenshot: $name" }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.filesDir, "readme-screenshots")
        dir.mkdirs()
        FileOutputStream(File(dir, "$name.png")).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
