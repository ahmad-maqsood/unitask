package com.example.util

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.example.ui.screens.archiveSummaryText
import com.example.ui.screens.deleteSemesterConfirmationMessage
import com.example.ui.screens.formatSemesterCounts
import com.example.util.timer.TimerHistoryLogic
import com.example.util.timer.TimerLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * The helpers that build sentences take an optional context. Without one they return English
 * (the plain unit tests use that). With one they must return the text in the app's language.
 * These tests run both languages through the context path.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalizedTextTest {

    private fun contextFor(language: String): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale(language))
        return base.createConfigurationContext(config)
    }

    private val arabicScript = Regex("""\p{InArabic}""")

    private fun assertUrdu(text: String) {
        assertTrue("Expected Urdu text but got: $text", arabicScript.containsMatchIn(text))
    }

    @Test
    fun englishContextMatchesTheEnglishDefaults() {
        val en = contextFor("en")
        // Counts above 1 so the singular/plural wording is the same in both paths.
        assertEquals(archiveSummaryText(3, 2, 5), archiveSummaryText(3, 2, 5, context = en))
        assertEquals(formatSemesterCounts(2, 3, 4, 5), formatSemesterCounts(2, 3, 4, 5, en))
        assertEquals(
            deleteSemesterConfirmationMessage("Fall 2026", 2, 3, 4, 5),
            deleteSemesterConfirmationMessage("Fall 2026", 2, 3, 4, 5, en)
        )
        assertEquals(
            formatCourseDeleteWarning(12, 3, 1),
            formatCourseDeleteWarning(12, 3, 1, en)
        )
        assertEquals(formatCourseDeleteWarning(0, 3, 0), formatCourseDeleteWarning(0, 3, 0, en))
        assertEquals(TimerLogic.formatDuration(90), TimerLogic.formatDuration(90, en))
        assertEquals(TimerLogic.formatDuration(120), TimerLogic.formatDuration(120, en))
        assertEquals(TimerLogic.formatDuration(25), TimerLogic.formatDuration(25, en))
        assertEquals(TimerHistoryLogic.formatTotal(45_000L), TimerHistoryLogic.formatTotal(45_000L, en))
    }

    @Test
    fun urduContextGivesUrduTextWithTheNumbers() {
        val ur = contextFor("ur")

        val summary = archiveSummaryText(3, 2, 5, existingName = "Fall 2026", context = ur)
        assertUrdu(summary)
        assertTrue(summary.contains("Fall 2026"))
        assertNotEquals(archiveSummaryText(3, 2, 5, existingName = "Fall 2026"), summary)

        val counts = formatSemesterCounts(2, 3, 4, 5, ur)
        assertUrdu(counts)
        assertTrue(listOf("2", "3", "4", "5").all { counts.contains(it) })

        val confirm = deleteSemesterConfirmationMessage("Fall 2026", 2, 3, 4, 5, ur)
        assertUrdu(confirm)
        assertTrue(confirm.contains("Fall 2026"))
    }

    @Test
    fun urduCourseDeleteWarningCoversEveryCombination() {
        val ur = contextFor("ur")
        // tasks, classes, attachments: every non-empty combination has its own text.
        val combos = listOf(
            Triple(12, 3, 1), Triple(12, 3, 0), Triple(12, 0, 1), Triple(12, 0, 0),
            Triple(0, 3, 0), Triple(0, 0, 1), Triple(0, 3, 1)
        )
        for ((tasks, classes, attachments) in combos) {
            val text = formatCourseDeleteWarning(tasks, classes, attachments, ur)
            assertUrdu(text)
            assertFalse("English left in: $text", text.contains("permanently deletes"))
            if (tasks > 0) assertTrue(text.contains("12"))
            if (classes > 0) assertTrue(text.contains("3"))
        }
        assertEquals("", formatCourseDeleteWarning(0, 0, 0, ur))
    }

    @Test
    fun urduDurationsUseUrduUnits() {
        val ur = contextFor("ur")
        for (minutes in listOf(25, 60, 90)) {
            val text = TimerLogic.formatDuration(minutes, ur)
            assertUrdu(text)
            assertFalse("English unit left in: $text", text.contains("min") || text.endsWith(" h"))
        }
        val seconds = TimerHistoryLogic.formatTotal(45_000L, ur)
        assertUrdu(seconds)
        assertTrue(seconds.contains("45"))
    }

    @Test
    fun backupReminderIsTranslated() {
        val ur = contextFor("ur")
        val never = BackupReminder.Reminder(neverBackedUp = true, daysAgo = 0)
        val old = BackupReminder.Reminder(neverBackedUp = false, daysAgo = 9)
        assertUrdu(never.messageFor(ur))
        assertUrdu(old.messageFor(ur))
        assertTrue(old.messageFor(ur).contains("9"))
        assertEquals(never.message, never.messageFor(contextFor("en")))
        assertEquals(old.message, old.messageFor(contextFor("en")))
    }
}
