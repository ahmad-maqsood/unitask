package com.example

import android.graphics.Bitmap
import android.os.Bundle
import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.waitUntil
import com.example.data.local.AppDatabase
import com.example.data.local.Course
import com.example.data.local.LectureSlot
import com.example.data.local.Note
import com.example.data.local.Priority
import com.example.data.local.TaskItem
import com.example.data.local.TaskStatus
import com.example.data.local.ensureMyTasksCourse
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

        val database = AppDatabase.getDatabase(context)
        database.clearAllTables()
        seedLocalDemoData(database)

        val outputDir = File(context.getExternalFilesDir(null), "readme-screenshots")
        outputDir.mkdirs()
        outputDir.listFiles()?.forEach { it.delete() }

        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForTag("tab_deadlines")
        capture("deadlines")

        tap("tab_timetable")
        capture("timetable")

        tap("tab_courses")
        capture("courses")

        tap("tab_groups")
        waitForTag("group_item_$groupId", timeoutMillis = 30_000)
        capture("groups")

        tap("group_item_$groupId")
        waitForTag("group_tab_chat")
        tap("group_tab_chat")
        waitForText("See you in the library!", timeoutMillis = 30_000)
        capture("group_chat")
        tap("group_detail_back_button")

        tap("tab_mytasks")
        capture("my_tasks")

        tap("top_bar_notes_button")
        waitForTag("notes_list")
        capture("notes")

        pressBack()
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

    private suspend fun seedLocalDemoData(database: AppDatabase) {
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
                location = "Room 204"
            )
        )
        val databasesId = database.courseDao().insertCourse(
            Course(
                name = "Database Systems",
                code = "CS305",
                instructor = "Mr. Usman Khan",
                colorHex = "#009688",
                location = "Lab 3"
            )
        )
        val designId = database.courseDao().insertCourse(
            Course(
                name = "Software Design",
                code = "SE210",
                instructor = "Ms. Ayesha Malik",
                colorHex = "#E8794A",
                location = "Room 108"
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
                priority = Priority.HIGH
            )
        )
        database.taskDao().insertTask(
            TaskItem(
                courseId = databasesId,
                title = "ER diagram submission",
                description = "Submit the library management system diagram.",
                dueDateEpochMillis = dayOffset(3),
                dueTimeHour = 17,
                priority = Priority.MEDIUM
            )
        )
        database.taskDao().insertTask(
            TaskItem(
                courseId = designId,
                title = "Read Chapter 4",
                dueDateEpochMillis = dayOffset(-1),
                status = TaskStatus.COMPLETED,
                priority = Priority.LOW
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
                instructor = "Dr. Sarah Ahmed"
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
                instructor = "Mr. Usman Khan"
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
                instructor = "Ms. Ayesha Malik"
            )
        )

        val myTasksCourseId = database.ensureMyTasksCourse()
        database.taskDao().insertTask(
            TaskItem(
                courseId = myTasksCourseId,
                title = "Prepare presentation slides",
                description = "Add the architecture diagram and references.",
                dueDateEpochMillis = dayOffset(2),
                dueTimeHour = 18,
                priority = Priority.HIGH
            )
        )
        database.taskDao().insertTask(
            TaskItem(
                courseId = myTasksCourseId,
                title = "Buy stationery",
                dueDateEpochMillis = dayOffset(4),
                priority = Priority.LOW
            )
        )

        database.noteDao().insert(
            Note(
                title = "Week 6 study notes",
                body = "# Week 6\n\n- Review linked lists\n- Practise SQL joins\n- Prepare the design assignment\n\n**Remember:** revise before Friday.",
                courseId = algorithmsId,
                isPinned = true
            )
        )
        database.noteDao().insert(
            Note(
                title = "Project ideas",
                body = "Ideas for the semester project.",
                courseId = designId
            )
        )
    }

    private fun tap(tag: String) {
        composeRule.onNodeWithTag(tag).assertExists().performClick()
        composeRule.waitForIdle()
    }

    private fun waitForTag(tag: String, timeoutMillis: Long = 15_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForText(value: String, timeoutMillis: Long = 15_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithText(value, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun pressBack() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeRule.waitForIdle()
    }

    private fun capture(name: String) {
        composeRule.waitForIdle()
        Thread.sleep(700)
        val bitmap: Bitmap = requireNotNull(
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        ) { "Could not capture screenshot: $name" }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.getExternalFilesDir(null), "readme-screenshots")
        dir.mkdirs()
        FileOutputStream(File(dir, "$name.png")).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
