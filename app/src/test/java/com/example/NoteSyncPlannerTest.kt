package com.example

import com.example.data.local.Note
import com.example.sync.CloudNote
import com.example.sync.CloudNoteHeader
import com.example.sync.docIdsNeedingBody
import com.example.sync.NoteSyncAction
import com.example.sync.planNoteSync
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteSyncPlannerTest {

    private val noCourseMapping: (Long?) -> String = { "" }

    // -------------------------------------------------------------------------
    // Rule a: Cloud row with no local match -> InsertLocal
    // -------------------------------------------------------------------------
    @Test
    fun ruleACloudRowWithNoLocalMatchProducesInsertLocal() {
        val cloudNote = CloudNote(
            docId = "cloud-1",
            title = "Cloud Note",
            body = "Body",
            courseCode = "CS101",
            isPinned = true,
            createdAtMillis = 1000L,
            updatedAtMillis = 2000L
        )
        val actions = planNoteSync(
            local = emptyList(),
            cloud = listOf(cloudNote),
            courseCodeOf = noCourseMapping
        )
        assertEquals(listOf(NoteSyncAction.InsertLocal(cloudNote)), actions)
    }

    // -------------------------------------------------------------------------
    // Rule b: Matched, local is deleted -> DeleteCloud
    // -------------------------------------------------------------------------
    @Test
    fun ruleBMatchedLocalIsDeletedProducesDeleteCloud() {
        val local = Note(
            id = 10L,
            title = "Deleted Local",
            body = "Body",
            cloudId = "cloud-1",
            isDeleted = true,
            isSynced = false
        )
        val cloud = CloudNote(
            docId = "cloud-1",
            title = "Cloud Note",
            body = "Body",
            courseCode = "",
            isPinned = false,
            createdAtMillis = 1000L,
            updatedAtMillis = 2000L
        )
        val actions = planNoteSync(
            local = listOf(local),
            cloud = listOf(cloud),
            courseCodeOf = noCourseMapping
        )
        assertEquals(listOf(NoteSyncAction.DeleteCloud(docId = "cloud-1", localId = 10L)), actions)
    }

    // -------------------------------------------------------------------------
    // Rule c: Matched, local not deleted, local isSynced = true
    // -------------------------------------------------------------------------
    @Test
    fun ruleCMatchedSyncedLocalCloudNewerProducesOverwriteLocal() {
        val local = Note(
            id = 10L,
            title = "Local Note",
            body = "Local Body",
            cloudId = "cloud-1",
            cloudUpdatedAt = 1500L,
            isDeleted = false,
            isSynced = true
        )
        val cloud = CloudNote(
            docId = "cloud-1",
            title = "Cloud Updated Note",
            body = "Newer Body",
            courseCode = "",
            isPinned = false,
            createdAtMillis = 1000L,
            updatedAtMillis = 2000L
        )
        val actions = planNoteSync(
            local = listOf(local),
            cloud = listOf(cloud),
            courseCodeOf = noCourseMapping
        )
        assertEquals(listOf(NoteSyncAction.OverwriteLocal(localId = 10L, cloud = cloud)), actions)
    }

    @Test
    fun ruleCMatchedSyncedLocalCloudOlderOrSameProducesNothing() {
        val local = Note(
            id = 10L,
            title = "Local Note",
            body = "Local Body",
            cloudId = "cloud-1",
            cloudUpdatedAt = 2000L,
            isDeleted = false,
            isSynced = true
        )
        val cloud = CloudNote(
            docId = "cloud-1",
            title = "Cloud Note",
            body = "Body",
            courseCode = "",
            isPinned = false,
            createdAtMillis = 1000L,
            updatedAtMillis = 2000L
        )
        val actions = planNoteSync(
            local = listOf(local),
            cloud = listOf(cloud),
            courseCodeOf = noCourseMapping
        )
        assertTrue(actions.isEmpty())
    }

    // -------------------------------------------------------------------------
    // Rule d: Matched, local not deleted, local isSynced = false (edited locally)
    // -------------------------------------------------------------------------
    @Test
    fun ruleDCloudUnchangedProducesPushLocal() {
        val local = Note(
            id = 10L,
            title = "Edited Locally",
            body = "New Local Content",
            cloudId = "cloud-1",
            cloudUpdatedAt = 1000L,
            updatedAt = 1200L,
            isDeleted = false,
            isSynced = false
        )
        val cloud = CloudNote(
            docId = "cloud-1",
            title = "Original Cloud",
            body = "Original Body",
            courseCode = "",
            isPinned = false,
            createdAtMillis = 500L,
            updatedAtMillis = 1000L
        )
        val actions = planNoteSync(
            local = listOf(local),
            cloud = listOf(cloud),
            courseCodeOf = noCourseMapping
        )
        assertEquals(listOf(NoteSyncAction.PushLocal(localId = 10L)), actions)
    }

    @Test
    fun ruleDIdenticalContentIsNotAConflictAndProducesOverwriteLocal() {
        val local = Note(
            id = 10L,
            title = "Same Title",
            body = "Same Body",
            courseId = 5L,
            isPinned = true,
            cloudId = "cloud-1",
            cloudUpdatedAt = 1000L,
            updatedAt = 1300L,
            isDeleted = false,
            isSynced = false
        )
        val cloud = CloudNote(
            docId = "cloud-1",
            title = "Same Title",
            body = "Same Body",
            courseCode = "MATH101",
            isPinned = true,
            createdAtMillis = 500L,
            updatedAtMillis = 2000L
        )
        val courseMapping: (Long?) -> String = { id -> if (id == 5L) "MATH101" else "" }
        val actions = planNoteSync(
            local = listOf(local),
            cloud = listOf(cloud),
            courseCodeOf = courseMapping
        )
        assertEquals(listOf(NoteSyncAction.OverwriteLocal(localId = 10L, cloud = cloud)), actions)
    }

    @Test
    fun ruleDConflictProducesCreateConflictCopyThenOverwriteLocalForSameNote() {
        val local = Note(
            id = 10L,
            title = "Local Edit",
            body = "Local Body",
            cloudId = "cloud-1",
            cloudUpdatedAt = 1000L,
            updatedAt = 1200L,
            isDeleted = false,
            isSynced = false
        )
        val cloud = CloudNote(
            docId = "cloud-1",
            title = "Cloud Edit",
            body = "Cloud Body",
            courseCode = "",
            isPinned = false,
            createdAtMillis = 500L,
            updatedAtMillis = 2000L
        )
        val actions = planNoteSync(
            local = listOf(local),
            cloud = listOf(cloud),
            courseCodeOf = noCourseMapping
        )
        val expected = listOf(
            NoteSyncAction.CreateConflictCopy(localId = 10L),
            NoteSyncAction.OverwriteLocal(localId = 10L, cloud = cloud)
        )
        assertEquals(expected, actions)
    }

    // -------------------------------------------------------------------------
    // Rule e: Local note with cloudId NOT in cloud list
    // -------------------------------------------------------------------------
    @Test
    fun ruleEDeletedUnsyncedNoteNotInCloudProducesDeleteCloud() {
        val local = Note(
            id = 10L,
            title = "Trashed Note",
            body = "Body",
            cloudId = "cloud-orphan",
            isDeleted = true,
            isSynced = false
        )
        val actions = planNoteSync(
            local = listOf(local),
            cloud = emptyList(),
            courseCodeOf = noCourseMapping
        )
        assertEquals(listOf(NoteSyncAction.DeleteCloud(docId = "cloud-orphan", localId = 10L)), actions)
    }

    @Test
    fun ruleEEmptyCloudGuardBlocksDeleteLocalWhenThreeOrMoreCandidates() {
        val local = listOf(
            Note(id = 1L, cloudId = "c1", isDeleted = false, isSynced = true),
            Note(id = 2L, cloudId = "c2", isDeleted = false, isSynced = true),
            Note(id = 3L, cloudId = "c3", isDeleted = false, isSynced = true)
        )
        val actions = planNoteSync(
            local = local,
            cloud = emptyList(),
            courseCodeOf = noCourseMapping
        )
        // Guard blocks deletion because cloud list is empty and 3 candidates exist
        assertTrue(actions.none { it is NoteSyncAction.DeleteLocal })
        assertTrue(actions.isEmpty())
    }

    @Test
    fun ruleEEmptyCloudGuardAllowsDeleteLocalWhenFewerThanThreeCandidates() {
        val local = listOf(
            Note(id = 1L, cloudId = "c1", isDeleted = false, isSynced = true),
            Note(id = 2L, cloudId = "c2", isDeleted = false, isSynced = true)
        )
        val actions = planNoteSync(
            local = local,
            cloud = emptyList(),
            courseCodeOf = noCourseMapping
        )
        val expected = listOf(
            NoteSyncAction.DeleteLocal(localId = 1L),
            NoteSyncAction.DeleteLocal(localId = 2L)
        )
        assertEquals(expected, actions)
    }

    @Test
    fun ruleERestoredNoteUnsyncedMissingInCloudIsPushed() {
        val local = Note(
            id = 10L,
            title = "Restored Note",
            body = "Content",
            cloudId = "cloud-prev",
            isDeleted = false,
            isSynced = false
        )
        val actions = planNoteSync(
            local = listOf(local),
            cloud = emptyList(),
            courseCodeOf = noCourseMapping
        )
        assertEquals(listOf(NoteSyncAction.PushLocal(localId = 10L)), actions)
    }

    // -------------------------------------------------------------------------
    // Rule f: Local note with cloudId ""
    // -------------------------------------------------------------------------
    @Test
    fun ruleFNewNoteWithEmptyCloudIdIsPushed() {
        val local = Note(
            id = 10L,
            title = "Brand New Note",
            body = "Content",
            cloudId = "",
            isDeleted = false,
            isSynced = false
        )
        val actions = planNoteSync(
            local = listOf(local),
            cloud = emptyList(),
            courseCodeOf = noCourseMapping
        )
        assertEquals(listOf(NoteSyncAction.PushLocal(localId = 10L)), actions)
    }

    @Test
    fun ruleFDeletedNoteWithEmptyCloudIdProducesNothing() {
        val local = Note(
            id = 10L,
            title = "Ephemeral Deleted",
            body = "Never synced",
            cloudId = "",
            isDeleted = true,
            isSynced = false
        )
        val actions = planNoteSync(
            local = listOf(local),
            cloud = emptyList(),
            courseCodeOf = noCourseMapping
        )
        assertTrue(actions.isEmpty())
    }

    // -------------------------------------------------------------------------
    // Ordering and non-contradiction tests
    // -------------------------------------------------------------------------
    @Test
    fun actionsAreOrderedProperly() {
        val conflictNote = Note(
            id = 1L,
            title = "Local Conflict",
            body = "Local",
            cloudId = "c1",
            cloudUpdatedAt = 100L,
            isDeleted = false,
            isSynced = false
        )
        val deletedNote = Note(
            id = 2L,
            title = "Local Delete",
            body = "Body",
            cloudId = "c2",
            isDeleted = false,
            isSynced = true
        )
        val localDeleteCloud = Note(
            id = 3L,
            title = "Delete on cloud",
            body = "Body",
            cloudId = "c3",
            isDeleted = true,
            isSynced = false
        )
        val newLocalNote = Note(
            id = 4L,
            title = "New local",
            body = "Body",
            cloudId = "",
            isDeleted = false,
            isSynced = false
        )

        val cloudConflict = CloudNote(
            docId = "c1",
            title = "Cloud Conflict",
            body = "Cloud",
            courseCode = "",
            isPinned = false,
            createdAtMillis = 50L,
            updatedAtMillis = 200L
        )
        val cloudInsert = CloudNote(
            docId = "c99",
            title = "Cloud Insert",
            body = "Insert",
            courseCode = "",
            isPinned = false,
            createdAtMillis = 50L,
            updatedAtMillis = 200L
        )
        val cloudDeleteMatched = CloudNote(
            docId = "c3",
            title = "To be deleted",
            body = "Body",
            courseCode = "",
            isPinned = false,
            createdAtMillis = 50L,
            updatedAtMillis = 50L
        )

        val localList = listOf(conflictNote, deletedNote, localDeleteCloud, newLocalNote)
        val cloudList = listOf(cloudConflict, cloudInsert, cloudDeleteMatched)

        val actions = planNoteSync(localList, cloudList, noCourseMapping)

        // Conflict copy must come first
        assertTrue(actions[0] is NoteSyncAction.CreateConflictCopy)

        // Then OverwriteLocal or InsertLocal
        val second = actions[1]
        assertTrue(second is NoteSyncAction.OverwriteLocal || second is NoteSyncAction.InsertLocal)

        // Local deletes after overwrites/inserts
        val deleteLocalIndex = actions.indexOfFirst { it is NoteSyncAction.DeleteLocal }
        val overwriteInsertLastIndex = actions.indexOfLast {
            it is NoteSyncAction.OverwriteLocal || it is NoteSyncAction.InsertLocal
        }
        assertTrue(deleteLocalIndex > overwriteInsertLastIndex)

        // Cloud deletes after local deletes
        val deleteCloudIndex = actions.indexOfFirst { it is NoteSyncAction.DeleteCloud }
        assertTrue(deleteCloudIndex > deleteLocalIndex)

        // Pushes last
        val pushIndex = actions.indexOfFirst { it is NoteSyncAction.PushLocal }
        assertTrue(pushIndex > deleteCloudIndex)
    }

    @Test
    fun noNoteAppearsInContradictoryActions() {
        val notes = listOf(
            Note(id = 1L, cloudId = "c1", isDeleted = false, isSynced = true, cloudUpdatedAt = 100L),
            Note(id = 2L, cloudId = "c2", isDeleted = true, isSynced = false),
            Note(id = 3L, cloudId = "c3", isDeleted = false, isSynced = false, cloudUpdatedAt = 100L),
            Note(id = 4L, cloudId = "c4", isDeleted = false, isSynced = true),
            Note(id = 5L, cloudId = "", isDeleted = false, isSynced = false)
        )
        val cloud = listOf(
            CloudNote("c1", "T1", "B1", "", false, 50L, 200L),
            CloudNote("c2", "T2", "B2", "", false, 50L, 50L),
            CloudNote("c3", "T3", "B3", "", false, 50L, 100L),
            CloudNote("c6", "T6", "B6", "", false, 50L, 50L)
        )

        val actions = planNoteSync(notes, cloud, noCourseMapping)

        // Group actions by target localId where applicable
        val localIdsInDelete = actions.filterIsInstance<NoteSyncAction.DeleteLocal>().map { it.localId }.toSet()
        val localIdsInPush = actions.filterIsInstance<NoteSyncAction.PushLocal>().map { it.localId }.toSet()
        val localIdsInOverwrite = actions.filterIsInstance<NoteSyncAction.OverwriteLocal>().map { it.localId }.toSet()

        // A note cannot be both deleted locally and pushed
        assertTrue(localIdsInDelete.intersect(localIdsInPush).isEmpty())
        // A note cannot be both deleted locally and overwritten
        assertTrue(localIdsInDelete.intersect(localIdsInOverwrite).isEmpty())
    }
    // -------------------------------------------------------------------------
    // Which cloud notes need their text downloaded
    // -------------------------------------------------------------------------
    @Test
    fun onlyNewOrChangedCloudNotesNeedTheirText() {
        val unchanged = Note(id = 1L, cloudId = "same", cloudUpdatedAt = 500L, isSynced = true)
        val changed = Note(id = 2L, cloudId = "newer", cloudUpdatedAt = 500L, isSynced = true)
        val headers = listOf(
            CloudNoteHeader("same", 500L),
            CloudNoteHeader("newer", 900L),
            CloudNoteHeader("brand-new", 100L)
        )
        assertEquals(
            listOf("newer", "brand-new"),
            docIdsNeedingBody(listOf(unchanged, changed), headers)
        )
    }
}
