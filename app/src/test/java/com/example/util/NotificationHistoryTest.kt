package com.example.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotificationHistoryTest {

    private lateinit var context: Context
    private val day = 24L * 60 * 60 * 1000

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        NotificationHistory.clear(context)
    }

    @Test
    fun newestFirstAndUnreadUntilMarked() {
        val now = System.currentTimeMillis()
        NotificationHistory.add(context, "Group update", "First", "a", nowMillis = now - 1_000L)
        NotificationHistory.add(context, "Group chat", "Second", "b", nowMillis = now)
        val items = NotificationHistory.items(context)
        assertEquals(listOf("Second", "First"), items.map { it.title })
        assertEquals(2, NotificationHistory.unreadCount(context))

        NotificationHistory.markAllRead(context)
        assertEquals(0, NotificationHistory.unreadCount(context))
    }

    @Test
    fun sameKeyReplacesOlderItemAndBecomesUnreadAgain() {
        val now = System.currentTimeMillis()
        NotificationHistory.add(context, "Group chat", "Group A", "Sam: hi", key = "chat|a", nowMillis = now - 5_000)
        NotificationHistory.markAllRead(context)
        NotificationHistory.add(context, "Group chat", "Group A", "Sam: hello again", key = "chat|a", nowMillis = now)

        val items = NotificationHistory.items(context)
        assertEquals(1, items.size)
        assertEquals("Sam: hello again", items[0].body)
        assertTrue(items[0].unread)
    }

    @Test
    fun keepsOnlyNewest100() {
        val now = System.currentTimeMillis()
        repeat(120) { i ->
            NotificationHistory.add(context, "Group update", "T$i", "", nowMillis = now - 200_000L + i * 1_000L)
        }
        val items = NotificationHistory.items(context)
        assertEquals(100, items.size)
        assertEquals("T119", items.first().title)
        assertFalse(items.any { it.title == "T0" })
    }

    @Test
    fun dropsItemsOlderThan30Days() {
        val now = System.currentTimeMillis()
        NotificationHistory.add(context, "Group update", "Old", "", nowMillis = now - 31 * day)
        NotificationHistory.add(context, "Group update", "Fresh", "", nowMillis = now)
        assertEquals(listOf("Fresh"), NotificationHistory.items(context).map { it.title })
    }

    @Test
    fun removeAndClear() {
        val now = System.currentTimeMillis()
        NotificationHistory.add(context, "Group update", "A", "", nowMillis = now - 1_000)
        NotificationHistory.add(context, "Group update", "B", "", nowMillis = now)
        val first = NotificationHistory.items(context).first()
        NotificationHistory.remove(context, first.id)
        assertEquals(listOf("A"), NotificationHistory.items(context).map { it.title })

        NotificationHistory.clear(context)
        assertTrue(NotificationHistory.items(context).isEmpty())
    }
    @Test
    fun classStartAlertsAreNotSaved() {
        val now = System.currentTimeMillis()
        NotificationHistory.add(
            context, "Class", "Class in 10 min: CS101", "Room 1",
            target = "tab:TIMETABLE", key = "n|1", nowMillis = now
        )
        assertTrue(NotificationHistory.items(context).isEmpty())
        assertEquals(0, NotificationHistory.unreadCount(context))
    }

    @Test
    fun classItemsSavedByAnOlderVersionAreHidden() {
        val now = System.currentTimeMillis()
        // Written the way the previous version stored it.
        context.getSharedPreferences("notification_history", Context.MODE_PRIVATE)
            .edit()
            .putString(
                "items_json",
                """[{"id":${now - 1_000},"key":"n|1","kind":"Class","title":"Class in 10 min: CS101","body":"","target":"tab:TIMETABLE","unread":true}]"""
            )
            .commit()

        assertTrue(NotificationHistory.items(context).isEmpty())
        assertEquals(0, NotificationHistory.unreadCount(context))

        // Other kinds still work next to it.
        NotificationHistory.add(context, "Group update", "Report due", "", nowMillis = now)
        assertEquals(listOf("Report due"), NotificationHistory.items(context).map { it.title })
    }

    @Test
    fun deadlineRemindersAreNotSaved() {
        val now = System.currentTimeMillis()
        NotificationHistory.add(context, "Deadline", "Report due", "", target = "tab:DEADLINES", key = "n|2", nowMillis = now)
        NotificationHistory.add(context, "Group deadline", "Lab due", "", target = "group:g1", key = "n|3", nowMillis = now)
        assertTrue(NotificationHistory.items(context).isEmpty())
        assertEquals(0, NotificationHistory.unreadCount(context))

        // The other kinds stay in the bell.
        NotificationHistory.add(context, "Daily summary", "Today in UniTask", "", key = "daily|1", nowMillis = now)
        NotificationHistory.add(context, "Missed reminder", "Missed", "", key = "missed|1", nowMillis = now + 1)
        NotificationHistory.add(context, "Group chat", "Group A", "Sam: hi", key = "chat|a", nowMillis = now + 2)
        assertEquals(3, NotificationHistory.items(context).size)
    }

    @Test
    fun deadlineRemindersSavedByAnOlderVersionAreHidden() {
        val now = System.currentTimeMillis()
        context.getSharedPreferences("notification_history", Context.MODE_PRIVATE)
            .edit()
            .putString(
                "items_json",
                """[{"id":${now - 2_000},"key":"n|2","kind":"Deadline","title":"Report due","body":"","target":"tab:DEADLINES","unread":true},""" +
                    """{"id":${now - 1_000},"key":"n|3","kind":"Group deadline","title":"Lab due","body":"","target":"group:g1","unread":true}]"""
            )
            .commit()

        assertTrue(NotificationHistory.items(context).isEmpty())
        assertEquals(0, NotificationHistory.unreadCount(context))
    }
}
