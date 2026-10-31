package com.example

import com.example.sync.SupabaseSyncManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class PaginationTest {

    @Test
    fun testSinglePageFewerThanPageSize() = runBlocking {
        val totalItems = (1..50).map { "item_$it" }
        val requestedRanges = mutableListOf<Pair<Long, Long>>()

        val result = SupabaseSyncManager.fetchAllRows<String>(
            table = "tasks",
            userId = "user_123"
        ) { from, to ->
            requestedRanges.add(from to to)
            totalItems.drop(from.toInt()).take((to - from + 1).toInt())
        }

        assertEquals(1, requestedRanges.size)
        assertEquals(0L to 999L, requestedRanges[0])
        assertEquals(50, result.size)
        assertEquals(totalItems, result)
    }

    @Test
    fun testMultiplePagesFetchAllAndTerminate() = runBlocking {
        val totalItems = (1..2500).map { "task_$it" }
        val requestedRanges = mutableListOf<Pair<Long, Long>>()

        val result = SupabaseSyncManager.fetchAllRows<String>(
            table = "tasks",
            userId = "user_123"
        ) { from, to ->
            requestedRanges.add(from to to)
            totalItems.drop(from.toInt()).take((to - from + 1).toInt())
        }

        assertEquals(3, requestedRanges.size)
        assertEquals(0L to 999L, requestedRanges[0])
        assertEquals(1000L to 1999L, requestedRanges[1])
        assertEquals(2000L to 2999L, requestedRanges[2])
        assertEquals(2500, result.size)
        assertEquals("task_1", result.first())
        assertEquals("task_2500", result.last())
    }

    @Test
    fun testExactMultipleOfPageSizeTerminates() = runBlocking {
        val totalItems = (1..1000).map { "course_$it" }
        val requestedRanges = mutableListOf<Pair<Long, Long>>()

        val result = SupabaseSyncManager.fetchAllRows<String>(
            table = "courses",
            userId = "user_123"
        ) { from, to ->
            requestedRanges.add(from to to)
            totalItems.drop(from.toInt()).take((to - from + 1).toInt())
        }

        assertEquals(2, requestedRanges.size)
        assertEquals(0L to 999L, requestedRanges[0])
        assertEquals(1000L to 1999L, requestedRanges[1])
        assertEquals(1000, result.size)
    }

    @Test
    fun testEmptyTableReturnsEmptyList() = runBlocking {
        val requestedRanges = mutableListOf<Pair<Long, Long>>()

        val result = SupabaseSyncManager.fetchAllRows<String>(
            table = "slots",
            userId = "user_123"
        ) { from, to ->
            requestedRanges.add(from to to)
            emptyList()
        }

        assertEquals(1, requestedRanges.size)
        assertEquals(0L to 999L, requestedRanges[0])
        assertTrue(result.isEmpty())
    }

    @Test
    fun testPageFetchFailureThrowsExceptionWithoutSwallowing() = runBlocking {
        var pageCount = 0

        try {
            SupabaseSyncManager.fetchAllRows<String>(
                table = "tasks",
                userId = "user_123"
            ) { from, to ->
                pageCount++
                if (pageCount == 2) {
                    throw IOException("Network error on page 2")
                }
                List(1000) { "task_${from + it}" }
            }
            fail("Expected IOException to be thrown")
        } catch (e: IOException) {
            assertEquals("Network error on page 2", e.message)
            assertEquals(2, pageCount)
        }
    }

    @Test
    fun testCustomPageSizePagination() = runBlocking {
        val totalItems = (1..12).map { "item_$it" }
        val requestedRanges = mutableListOf<Pair<Long, Long>>()

        val result = SupabaseSyncManager.fetchAllRows<String>(
            table = "test",
            userId = "user_123",
            pageSize = 5L
        ) { from, to ->
            requestedRanges.add(from to to)
            totalItems.drop(from.toInt()).take((to - from + 1).toInt())
        }

        assertEquals(3, requestedRanges.size)
        assertEquals(0L to 4L, requestedRanges[0])
        assertEquals(5L to 9L, requestedRanges[1])
        assertEquals(10L to 14L, requestedRanges[2])
        assertEquals(12, result.size)
        assertEquals(totalItems, result)
    }
}

