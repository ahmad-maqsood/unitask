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
        NotificationHistory.add(context, "Deadline", "First", "a", nowMillis = 1_000_000_000_000L)
        NotificationHistory.add(context, "Class", "Second", "b", nowMillis = 1_000_000_001_000L)
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
            NotificationHistory.add(context, "Deadline", "T$i", "", nowMillis = now - 200_000L + i * 1_000L)
        }
        val items = NotificationHistory.items(context)
        assertEquals(100, items.size)
        assertEquals("T119", items.first().title)
        assertFalse(items.any { it.title == "T0" })
    }

    @Test
    fun dropsItemsOlderThan30Days() {
        val now = System.currentTimeMillis()
        NotificationHistory.add(context, "Deadline", "Old", "", nowMillis = now - 31 * day)
        NotificationHistory.add(context, "Deadline", "Fresh", "", nowMillis = now)
        assertEquals(listOf("Fresh"), NotificationHistory.items(context).map { it.title })
    }

    @Test
    fun removeAndClear() {
        val now = System.currentTimeMillis()
        NotificationHistory.add(context, "Deadline", "A", "", nowMillis = now - 1_000)
        NotificationHistory.add(context, "Deadline", "B", "", nowMillis = now)
        val first = NotificationHistory.items(context).first()
        NotificationHistory.remove(context, first.id)
        assertEquals(listOf("A"), NotificationHistory.items(context).map { it.title })

        NotificationHistory.clear(context)
        assertTrue(NotificationHistory.items(context).isEmpty())
    }
}
