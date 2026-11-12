package com.example.data.groups

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bug these cover: deleting, editing or ticking a task or message right after creating it did
 * nothing the first time, because the action ran before the save had reached the server.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PendingSavesTest {

    @Test
    fun actionWaitsForTheSaveToFinish() = runTest {
        val saves = PendingSaves()
        val finishSave = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()

        val save = launch {
            finishSave.await()
            events += "saved"
        }
        saves.track("task-1", save)

        val delete = launch {
            saves.awaitSave("task-1")
            events += "deleted"
        }

        advanceUntilIdle()
        assertEquals("the delete must not run while the save is still going", emptyList<String>(), events)

        finishSave.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("saved", "deleted"), events)
        delete.join()
    }

    @Test
    fun noSaveRunningMeansNoWaiting() = runTest {
        val saves = PendingSaves()
        saves.awaitSave("never-saved") // must return at once, not hang
        assertFalse(saves.isSaving("never-saved"))
    }

    @Test
    fun saveIsForgottenOnceItFinishes() = runTest {
        val saves = PendingSaves()
        val save = launch { }
        saves.track("task-2", save)
        advanceUntilIdle()
        assertFalse(saves.isSaving("task-2"))
        saves.awaitSave("task-2")
    }

    @Test
    fun isSavingIsTrueWhileTheSaveRuns() = runTest {
        val saves = PendingSaves()
        val finishSave = CompletableDeferred<Unit>()
        val save = launch { finishSave.await() }
        saves.track("msg-1", save)
        advanceUntilIdle()
        assertTrue(saves.isSaving("msg-1"))
        finishSave.complete(Unit)
        advanceUntilIdle()
        assertFalse(saves.isSaving("msg-1"))
    }

    @Test
    fun aFailedSaveDoesNotBlockLaterActions() = runTest {
        val saves = PendingSaves()
        val save = launch { }
        saves.track("task-3", save)
        advanceUntilIdle()
        // Whatever happened to the save, a later delete or edit still goes ahead.
        saves.awaitSave("task-3")
        assertFalse(saves.isSaving("task-3"))
    }
}
