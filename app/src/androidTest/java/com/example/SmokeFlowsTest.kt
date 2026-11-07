package com.example

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.local.AppDatabase
import com.example.data.local.Course
import com.example.data.local.Note
import com.example.util.Announcements
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real end-to-end smoke test driving core app workflows on an Android emulator. */
@RunWith(AndroidJUnit4::class)
class SmokeFlowsTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("unitask_settings", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("notification_permission_prompted", true)
            .putBoolean("background_setup_prompted", true)
            .apply()

        com.example.util.drive.DrivePrefs(context).restoreOfferDismissed = true

        try {
            runBlocking(Dispatchers.IO) {
                Announcements.refresh(context)
            }
            Announcements.markSeen(context, Announcements.unseen(context))
        } catch (_: Exception) {
            // Ignore network failures for announcements during smoke tests
        }

        db = AppDatabase.getDatabase(context)
        runBlocking(Dispatchers.IO) {
            db.clearAllTables()
            val imagesDir = File(context.filesDir, "note_images")
            if (imagesDir.exists()) {
                imagesDir.deleteRecursively()
            }
        }
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun waitForTag(tag: String, timeoutMillis: Long = 10_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForTagToDisappear(tag: String, timeoutMillis: Long = 10_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun waitForText(value: String, timeoutMillis: Long = 10_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithText(value, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun tap(tag: String) {
        waitForTag(tag)
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed().performClick()
        composeRule.waitForIdle()
    }

    private fun tapText(value: String) {
        waitForText(value)
        composeRule.onNodeWithText(value, substring = true, useUnmergedTree = true).assertIsDisplayed().performClick()
        composeRule.waitForIdle()
    }

    private fun type(tag: String, text: String) {
        waitForTag(tag)
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).performTextInput(text)
        composeRule.waitForIdle()
    }

    private fun openNotes() {
        tap("top_bar_tools_button")
        tap("tools_tile_notes")
    }

    private fun getActiveTaskId(title: String): Long {
        var id = 0L
        composeRule.waitUntil(10_000) {
            runBlocking(Dispatchers.IO) {
                val task = db.taskDao().getAllActiveTasksSync().firstOrNull { it.title == title }
                if (task != null) {
                    id = task.id
                    true
                } else false
            }
        }
        return id
    }

    private fun getCourseId(code: String): Long {
        var id = 0L
        composeRule.waitUntil(10_000) {
            runBlocking(Dispatchers.IO) {
                val course = db.courseDao().getCourseByCode(code)
                if (course != null) {
                    id = course.id
                    true
                } else false
            }
        }
        return id
    }

    @Test
    fun notes_new_note_after_another_starts_empty() {
        val noteId = runBlocking(Dispatchers.IO) {
            db.noteDao().insert(Note(title = "Seed Title", body = "Seed body text"))
        }

        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForTag("top_bar_tools_button")
        openNotes()

        waitForTag("notes_list")
        tap("note_card_$noteId")

        waitForTag("note_preview_title")
        tap("note_editor_back_button")
        waitForTag("notes_list")

        tap("notes_fab_new_note")
        waitForTag("note_title_input")
        waitForTag("note_body_input")

        val titleText = composeRule.onNodeWithTag("note_title_input", useUnmergedTree = true)
            .fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text ?: ""
        val bodyText = composeRule.onNodeWithTag("note_body_input", useUnmergedTree = true)
            .fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text ?: ""
        assertTrue(titleText.isEmpty())
        assertTrue(bodyText.isEmpty())

        type("note_title_input", "Brand New Note")
        type("note_body_input", "Content of brand new note")
        tap("note_editor_back_button")

        waitForTag("notes_list")
        waitForText("Brand New Note")

        tapText("Brand New Note")
        waitForTag("note_preview_title")
        waitForText("Brand New Note")
        waitForText("Content of brand new note")

        tap("note_editor_back_button")
        waitForTag("notes_list")
        tap("notes_back_button")
        waitForTagToDisappear("notes_list")
    }

    @Test
    fun course_and_task_can_be_added_completed_and_deleted() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForTag("tab_courses")

        // 1. Add course
        tap("tab_courses")
        tap("courses_tab_add_course_button")
        type("course_code_input", "SMK101")
        type("course_name_input", "Smoke Test Course")
        tap("course_save_button")
        waitForText("SMK101")

        // 2. Add task under it
        val courseId = getCourseId("SMK101")
        tap("course_card_$courseId")
        waitForTag("course_detail_add_task_button")
        tap("course_detail_add_task_button")
        type("task_title_input", "Smoke Assignment")
        tap("task_save_button")
        waitForText("Smoke Assignment")

        // 3. Mark task done
        val taskId = getActiveTaskId("Smoke Assignment")
        tap("task_status_toggle_$taskId")
        waitForText("Done")

        // 4. Delete the task
        tap("task_menu_$taskId")
        tap("task_delete_menu_item")
        tap("confirm_delete_button")
        waitForTagToDisappear("task_item_$taskId")

        // 5. Delete the course
        tap("course_detail_back_button")
        waitForText("SMK101")
        tap("course_menu_$courseId")
        tap("course_delete_menu_item")
        tap("confirm_delete_button")
        waitForTagToDisappear("course_card_$courseId")
    }

    @Test
    fun deleting_a_course_keeps_its_notes() {
        val (courseId, noteId) = runBlocking(Dispatchers.IO) {
            val cId = db.courseDao().insertCourse(
                Course(name = "Preserved Course", code = "PRSV201", colorHex = "#536DFE")
            )
            val nId = db.noteDao().insert(
                Note(title = "Course Linked Note", body = "Important lecture details", courseId = cId)
            )
            cId to nId
        }

        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForTag("tab_courses")

        tap("tab_courses")
        waitForText("PRSV201")
        tap("course_menu_$courseId")
        tap("course_delete_menu_item")
        tap("confirm_delete_button")
        waitForTagToDisappear("course_card_$courseId")

        openNotes()
        waitForTag("notes_list")
        waitForText("Course Linked Note")
        tap("note_card_$noteId")
        waitForText("Important lecture details")
        tap("note_editor_back_button")
        waitForTag("notes_list")
        tap("notes_back_button")
    }

    @Test
    fun settings_sections_open_and_return() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForTag("top_bar_settings_button")

        tap("top_bar_settings_button")
        waitForTag("settings_section_general")

        tap("settings_section_general")
        waitForTag("settings_back_button")
        tap("settings_back_button")

        tap("settings_section_notifications")
        waitForTag("settings_back_button")
        tap("settings_back_button")

        tap("settings_section_backup")
        waitForTag("settings_back_button")
        tap("settings_back_button")

        tap("settings_section_about")
        waitForTag("settings_back_button")
        tap("settings_back_button")

        waitForTag("settings_section_general")
        composeRule.onNodeWithTag("settings_section_general", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun all_main_tabs_open_without_crashing() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForTag("tab_deadlines")

        tap("tab_deadlines")
        waitForTag("task_search_input")
        composeRule.onNodeWithTag("task_search_input", useUnmergedTree = true).assertIsDisplayed()

        tap("tab_timetable")
        waitForTag("timetable_export_ics_button")
        composeRule.onNodeWithTag("timetable_export_ics_button", useUnmergedTree = true).assertIsDisplayed()

        tap("tab_courses")
        waitForTag("courses_tab_add_course_button")
        composeRule.onNodeWithTag("courses_tab_add_course_button", useUnmergedTree = true).assertIsDisplayed()

        tap("tab_groups")
        waitForTag("groups_not_signed_in_card")
        composeRule.onNodeWithTag("groups_not_signed_in_card", useUnmergedTree = true).assertIsDisplayed()

        tap("tab_mytasks")
        waitForTag("my_tasks_quick_add_input")
        composeRule.onNodeWithTag("my_tasks_quick_add_input", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun timer_flow() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForTag("top_bar_tools_button")
        tap("top_bar_tools_button")
        waitForTag("tools_tile_timer")
        tap("tools_tile_timer")
        waitForTag("timer_screen")
        tap("timer_start_button")
        waitForTag("timer_pause_button")
        tap("timer_pause_button")
        waitForTag("timer_resume_button")
        tap("timer_reset_button")
        waitForTag("timer_start_button")
        tap("timer_mode_countdown")
        waitForTag("timer_preset_15")
        tap("timer_preset_15")
        waitForText("15:00")
    }
}
