package com.example

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.local.AppDatabase
import com.example.data.local.Course
import com.example.data.local.LectureSlot
import com.example.data.local.MY_TASKS_COURSE_CODE
import com.example.data.local.Note
import com.example.data.local.TaskAttachment
import com.example.data.local.TaskItem
import com.example.data.local.TaskStatus
import com.example.reminder.DailySummaryScheduler
import com.example.ui.theme.AccentTheme
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.ThemePreference
import com.example.util.Announcements
import com.example.util.BackupResult
import com.example.util.WeekParity
import com.example.util.drive.DriveBackupManager
import com.example.util.drive.DriveBackupResult
import com.example.util.drive.DrivePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

        context.getSharedPreferences("drive_backup_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()

        DrivePrefs(context).restoreOfferDismissed = true

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
            val attachmentsDir = File(context.filesDir, "attachments")
            if (attachmentsDir.exists()) {
                attachmentsDir.deleteRecursively()
            }
        }
    }

    @After
    fun tearDown() {
        scenario?.close()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("unitask_settings", Context.MODE_PRIVATE)
            .edit()
            .remove("theme_mode")
            .remove("theme_accent")
            .remove("daily_summary_enabled")
            .remove("daily_summary_hour")
            .remove("daily_summary_minute")
            .remove("week_a_anchor_millis")
            .apply()
        context.getSharedPreferences("drive_backup_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        ThemePreference.set(context, ThemeMode.SYSTEM)
        ThemePreference.setAccent(context, AccentTheme.INDIGO)
        WeekParity.setWeekAAnchor(context, null)
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

    private fun waitForTextToDisappear(value: String, timeoutMillis: Long = 10_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithText(value, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
    }

    /** Scrolls the Deadlines list until a node matching [matcher] is composed. */
    private fun scrollDeadlinesTo(matcher: androidx.compose.ui.test.SemanticsMatcher) {
        waitForTag("deadlines_list")
        composeRule.onNodeWithTag("deadlines_list", useUnmergedTree = true)
            .performScrollToNode(matcher)
        composeRule.waitForIdle()
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

    @Test
    fun my_tasks_quick_add_complete_and_delete() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForTag("tab_mytasks")

        tap("tab_mytasks")
        waitForTag("my_tasks_quick_add_input")

        type("my_tasks_quick_add_input", "Smoke my task")
        tap("my_tasks_quick_add_submit_button")
        waitForText("Smoke my task")

        val myTasksCourse = runBlocking(Dispatchers.IO) {
            db.courseDao().getCourseByCode(MY_TASKS_COURSE_CODE)
        }
        assertNotNull(myTasksCourse)

        val activeTasks = runBlocking(Dispatchers.IO) {
            db.taskDao().getTasksForCourseSync(myTasksCourse!!.id)
        }
        assertEquals(1, activeTasks.size)
        assertEquals("Smoke my task", activeTasks[0].title)

        val taskId = activeTasks[0].id
        tap("task_status_toggle_$taskId")
        waitForText("Done")
        // Completed tasks move into the Done section, which starts collapsed.
        composeRule.onNodeWithContentDescription("Expand Done section", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        waitForTag("task_item_$taskId")

        val completedInDb = runBlocking(Dispatchers.IO) {
            db.taskDao().getTaskById(taskId)
        }
        assertNotNull(completedInDb)
        assertEquals(TaskStatus.COMPLETED, completedInDb!!.status)

        tap("task_menu_$taskId")
        tap("task_delete_menu_item")
        tap("confirm_delete_button")
        waitForTagToDisappear("task_item_$taskId")

        val deletedInDb = runBlocking(Dispatchers.IO) {
            db.taskDao().getTaskById(taskId)
        }
        assertTrue(deletedInDb == null || deletedInDb.isDeleted)
    }

    @Test
    fun timetable_class_can_be_added() {
        val courseId = runBlocking(Dispatchers.IO) {
            db.courseDao().insertCourse(
                Course(name = "Timetable Course", code = "TT101", colorHex = "#FF5722", location = "Lab 3")
            )
        }

        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForTag("tab_timetable")

        tap("tab_timetable")
        waitForTag("timetable_add_slot_button")
        tap("timetable_add_slot_button")

        waitForTag("slot_course_picker")
        tap("slot_course_picker")
        waitForTag("slot_course_item_$courseId")
        tap("slot_course_item_$courseId")

        tap("day_chip_1")

        tap("slot_start_time_picker")
        waitForTag("time_picker_confirm_btn")
        tap("time_picker_confirm_btn")

        tap("slot_end_time_picker")
        waitForTag("time_picker_confirm_btn")
        tap("time_picker_confirm_btn")

        type("slot_classroom_input", "Room 101")
        tap("slot_save_button")

        tap("timetable_day_tab_1")
        waitForText("TT101")

        var slotId = 0L
        composeRule.waitUntil(10_000) {
            runBlocking(Dispatchers.IO) {
                val slots = db.lectureSlotDao().getSlotsForCourseSync(courseId).filter { !it.isDeleted }
                if (slots.isNotEmpty()) {
                    slotId = slots[0].id
                    true
                } else false
            }
        }
        assertTrue(slotId > 0L)
        waitForTag("timetable_slot_$slotId")

        tap("timetable_slot_menu_$slotId")
        waitForTag("timetable_slot_delete_item_$slotId")
        tap("timetable_slot_delete_item_$slotId")

        waitForTagToDisappear("timetable_slot_$slotId")
        val remaining = runBlocking(Dispatchers.IO) {
            db.lectureSlotDao().getSlotsForCourseSync(courseId).filter { !it.isDeleted }
        }
        assertTrue(remaining.isEmpty())
    }

    @Test
    fun deadlines_list_shows_tasks_and_search_filters() {
        val courseId = runBlocking(Dispatchers.IO) {
            db.courseDao().insertCourse(
                Course(name = "Search Course", code = "SRC201", colorHex = "#009688")
            )
        }
        val futureTime1 = System.currentTimeMillis() + 86_400_000L
        val futureTime2 = System.currentTimeMillis() + 2 * 86_400_000L
        runBlocking(Dispatchers.IO) {
            db.taskDao().insertTask(
                TaskItem(courseId = courseId, title = "Alpha report", dueDateEpochMillis = futureTime1)
            )
            db.taskDao().insertTask(
                TaskItem(courseId = courseId, title = "Beta quiz", dueDateEpochMillis = futureTime2)
            )
        }

        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForTag("tab_deadlines")

        tap("tab_deadlines")
        scrollDeadlinesTo(hasText("Alpha report", substring = true))
        waitForText("Alpha report")
        scrollDeadlinesTo(hasText("Beta quiz", substring = true))
        waitForText("Beta quiz")

        scrollDeadlinesTo(hasTestTag("task_search_input"))
        type("task_search_input", "Alpha")
        scrollDeadlinesTo(hasText("Alpha report", substring = true))
        waitForText("Alpha report")
        waitForTextToDisappear("Beta quiz")

        scrollDeadlinesTo(hasTestTag("task_search_input"))
        composeRule.onNodeWithTag("task_search_input", useUnmergedTree = true).performTextClearance()
        composeRule.waitForIdle()

        scrollDeadlinesTo(hasText("Alpha report", substring = true))
        waitForText("Alpha report")
        scrollDeadlinesTo(hasText("Beta quiz", substring = true))
        waitForText("Beta quiz")
    }

    @Test
    fun settings_survive_app_restart() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForTag("top_bar_settings_button")

        tap("top_bar_settings_button")
        waitForTag("settings_section_general")

        tap("settings_section_general")
        waitForTag("theme_chip_DARK")
        tap("theme_chip_DARK")
        tap("accent_chip_FOREST")
        tap("settings_back_button")

        waitForTag("settings_section_notifications")
        tap("settings_section_notifications")
        waitForTag("settings_daily_summary_row")
        tap("settings_daily_summary_row")
        waitForTag("settings_daily_summary_time_row")

        scenario?.close()
        scenario = null

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForTag("top_bar_settings_button")

        tap("top_bar_settings_button")
        waitForTag("settings_section_general")

        tap("settings_section_general")
        waitForTag("theme_chip_DARK")
        composeRule.onNodeWithTag("theme_chip_DARK", useUnmergedTree = true).assertIsSelected()
        composeRule.onNodeWithTag("accent_chip_FOREST", useUnmergedTree = true)
            .assertContentDescriptionEquals("Forest green, selected")
        tap("settings_back_button")

        waitForTag("settings_section_notifications")
        tap("settings_section_notifications")
        waitForTag("settings_daily_summary_time_row")
        composeRule.onNodeWithTag("settings_daily_summary_time_row", useUnmergedTree = true).assertIsDisplayed()

        assertEquals(ThemeMode.DARK, ThemePreference.mode)
        assertEquals(AccentTheme.FOREST, ThemePreference.accent)
        assertTrue(DailySummaryScheduler.isEnabled(context))
    }

    @Test
    fun drive_screens_and_prompts_open_safely() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = DrivePrefs(context)

        // (a) Empty database and restoreOfferDismissed = false shows drive_restore_offer_card
        prefs.restoreOfferDismissed = false

        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForTag("tab_deadlines")
        tap("tab_deadlines")

        waitForTag("drive_restore_offer_card")
        composeRule.onNodeWithTag("drive_restore_offer_card", useUnmergedTree = true).assertIsDisplayed()

        tap("drive_offer_dismiss_button")
        waitForTagToDisappear("drive_restore_offer_card")

        scenario?.close()
        scenario = null

        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForTag("tab_deadlines")
        tap("tab_deadlines")
        composeRule.waitForIdle()
        assertTrue(prefs.restoreOfferDismissed)
        assertTrue(
            composeRule.onAllNodesWithTag("drive_restore_offer_card", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        )

        // (b) Open Settings, Backup & data: assert drive_section, drive_connect_button, file backup buttons
        waitForTag("top_bar_settings_button")
        tap("top_bar_settings_button")
        waitForTag("settings_section_backup")
        tap("settings_section_backup")

        waitForTag("drive_section")
        composeRule.onNodeWithTag("drive_section", useUnmergedTree = true).assertIsDisplayed()
        waitForTag("drive_connect_button")
        composeRule.onNodeWithTag("drive_connect_button", useUnmergedTree = true).assertIsDisplayed()
        waitForTag("settings_local_backup_row")
        composeRule.onNodeWithTag("settings_local_backup_row", useUnmergedTree = true).assertIsDisplayed()
        waitForTag("settings_local_restore_row")
        composeRule.onNodeWithTag("settings_local_restore_row", useUnmergedTree = true).assertIsDisplayed()

        // Assert nothing crashes when scrolling the page
        composeRule.onNodeWithTag("settings_local_restore_row", useUnmergedTree = true).performTouchInput {
            swipeUp()
        }
        composeRule.waitForIdle()

        scenario?.close()
        scenario = null

        // (c) The migration prompt is only for users who signed in to cloud sync. The app
        // clears the saved cloud user whenever there is no Supabase session, which is
        // always the case on the emulator, so with data but no cloud user it must stay hidden.
        // The prompt rules themselves are covered by DriveMigrationPromptTest.
        runBlocking(Dispatchers.IO) {
            db.courseDao().insertCourse(
                Course(name = "Existing User Course", code = "EUC101", colorHex = "#123456")
            )
        }
        prefs.migrationPromptCount = 0
        prefs.migrationNextPromptAt = 0L

        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForTag("tab_deadlines")
        composeRule.waitForIdle()
        assertTrue(
            composeRule.onAllNodesWithTag("drive_migration_dialog", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        )
        assertEquals(0, prefs.migrationPromptCount)
    }

    @Test
    fun drive_backup_round_trip_keeps_everything() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fake = FakeDriveStore()

        // 1. Seed course and lecture slot
        val course = Course(
            name = "Roundtrip Course",
            code = "RTC101",
            colorHex = "#3F51B5",
            location = "Hall A",
            instructor = "Prof. Smith"
        )
        val courseId = db.courseDao().insertCourse(course)
        val slot = LectureSlot(
            courseId = courseId,
            dayOfWeek = 1,
            startHour = 9,
            startMinute = 0,
            endHour = 10,
            endMinute = 30,
            classroom = "Hall A",
            instructor = "Prof. Smith"
        )
        db.lectureSlotDao().insertSlot(slot)

        // 2. Two tasks (one done, one with a repeat rule)
        val taskDone = TaskItem(
            courseId = courseId,
            title = "Completed assignment",
            status = TaskStatus.COMPLETED,
            dueDateEpochMillis = 1750000000000L
        )
        val doneTaskId = db.taskDao().insertTask(taskDone)

        val taskRepeat = TaskItem(
            courseId = courseId,
            title = "Repeating homework",
            status = TaskStatus.PENDING,
            dueDateEpochMillis = 1760000000000L,
            repeatRule = "DAILY"
        )
        val repeatTaskId = db.taskDao().insertTask(taskRepeat)

        // 3. A note with a picture file in note_images
        val imagesDir = File(context.filesDir, "note_images").apply { mkdirs() }
        val imageName = "smoke_note_pic.jpg"
        val imageFile = File(imagesDir, imageName)
        val picBytes = byteArrayOf(10, 20, 30, 40, 50, 60, 70, 80)
        imageFile.writeBytes(picBytes)

        db.noteDao().insert(
            Note(
                title = "Note With Pic",
                body = "Class diagram: ![](unitask-image:$imageName)",
                courseId = courseId
            )
        )

        // 4. One attachment row with a real 300 KB file in filesDir/attachments
        val attachmentsDir = File(context.filesDir, "attachments").apply { mkdirs() }
        val attFile = File(attachmentsDir, "1001_large.bin")
        val attBytes = ByteArray(300 * 1024) { (it % 256).toByte() }
        attFile.writeBytes(attBytes)

        db.taskAttachmentDao().insertAttachment(
            TaskAttachment(
                taskId = repeatTaskId,
                fileName = "large.bin",
                fileSize = attBytes.size.toLong(),
                mimeType = "application/octet-stream",
                relativePath = "attachments/1001_large.bin"
            )
        )

        // 5. Change three settings
        ThemePreference.set(context, ThemeMode.DARK)
        ThemePreference.setAccent(context, AccentTheme.FOREST)
        DailySummaryScheduler.setEnabled(context, true)
        DailySummaryScheduler.setTime(context, 8, 45)
        val anchorMillis = 1710000000000L
        WeekParity.setWeekAAnchor(context, anchorMillis)

        DrivePrefs(context).includeAttachments = true

        // Call backUp and assert Success
        val backupResult = DriveBackupManager.backUp(context, fake, allowEmpty = false)
        assertTrue(backupResult is DriveBackupResult.Success)

        // Backing up again with nothing changed must not upload a new copy.
        val repeatBackup = DriveBackupManager.backUp(context, fake, allowEmpty = false, force = false)
        assertTrue("Expected Unchanged but was $repeatBackup", repeatBackup is DriveBackupResult.Unchanged)

        // Wipe everything
        db.clearAllTables()
        File(context.filesDir, "note_images").deleteRecursively()
        File(context.filesDir, "attachments").deleteRecursively()
        context.getSharedPreferences("unitask_settings", Context.MODE_PRIVATE)
            .edit()
            .remove("theme_mode")
            .remove("theme_accent")
            .remove("daily_summary_enabled")
            .remove("daily_summary_hour")
            .remove("daily_summary_minute")
            .remove("week_a_anchor_millis")
            .apply()
        ThemePreference.set(context, ThemeMode.SYSTEM)
        ThemePreference.setAccent(context, AccentTheme.INDIGO)
        WeekParity.setWeekAAnchor(context, null)

        // Call restore and assert Success
        val restoreResult = DriveBackupManager.restore(context, fake)
        assertTrue(restoreResult is BackupResult.Success)

        // Assert: course, slot, tasks, note, picture file, attachment, settings
        val restoredCourse = db.courseDao().getCourseByCode("RTC101")
        assertNotNull(restoredCourse)
        assertEquals("Roundtrip Course", restoredCourse!!.name)

        val restoredSlots = db.lectureSlotDao().getSlotsForCourseSync(restoredCourse.id).filter { !it.isDeleted }
        assertEquals(1, restoredSlots.size)
        assertEquals(1, restoredSlots[0].dayOfWeek)
        assertEquals("Hall A", restoredSlots[0].classroom)

        val restoredTasks = db.taskDao().getAnyTasksForCourseSync(restoredCourse.id)
        val rDone = restoredTasks.firstOrNull { it.title == "Completed assignment" }
        val rRepeat = restoredTasks.firstOrNull { it.title == "Repeating homework" }
        assertNotNull(rDone)
        assertEquals(TaskStatus.COMPLETED, rDone!!.status)
        assertNotNull(rRepeat)
        assertEquals("DAILY", rRepeat!!.repeatRule)

        val restoredNotes = db.noteDao().getAllNotesSync()
        val rNote = restoredNotes.firstOrNull { it.title == "Note With Pic" }
        assertNotNull(rNote)

        val restoredPicFile = File(File(context.filesDir, "note_images"), imageName)
        assertTrue(restoredPicFile.exists())
        assertTrue(picBytes.contentEquals(restoredPicFile.readBytes()))

        val restoredAtts = db.taskAttachmentDao().getAttachmentsForTaskSync(rRepeat.id)
        assertEquals(1, restoredAtts.size)
        assertEquals("large.bin", restoredAtts[0].fileName)
        val restoredAttFile = File(context.filesDir, restoredAtts[0].relativePath)
        assertTrue(restoredAttFile.exists())
        assertTrue(attBytes.contentEquals(restoredAttFile.readBytes()))

        ThemePreference.load(context)
        assertEquals(ThemeMode.DARK, ThemePreference.mode)
        assertEquals(AccentTheme.FOREST, ThemePreference.accent)
        assertTrue(DailySummaryScheduler.isEnabled(context))
        assertEquals(8, DailySummaryScheduler.hour(context))
        assertEquals(45, DailySummaryScheduler.minute(context))
        assertEquals(anchorMillis, WeekParity.getWeekAAnchor(context))

        // Launch activity and assert course code is visible on Courses tab
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForTag("tab_courses")
        tap("tab_courses")
        waitForText("RTC101")
        scenario?.close()
        scenario = null
    }
}
