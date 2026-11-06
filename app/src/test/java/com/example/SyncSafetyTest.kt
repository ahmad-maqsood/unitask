package com.example

import com.example.sync.SupabaseSyncManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncSafetyTest {

    @Test
    fun emptyCloudWithManySyncedRowsLooksLikeAGlitch() {
        assertTrue(SupabaseSyncManager.looksLikeEmptyCloudGlitch(cloudCount = 0, localSyncedCount = 3))
        assertTrue(SupabaseSyncManager.looksLikeEmptyCloudGlitch(cloudCount = 0, localSyncedCount = 40))
    }

    @Test
    fun aFewLocalRowsMayStillBeDeleted() {
        assertFalse(SupabaseSyncManager.looksLikeEmptyCloudGlitch(cloudCount = 0, localSyncedCount = 2))
        assertFalse(SupabaseSyncManager.looksLikeEmptyCloudGlitch(cloudCount = 0, localSyncedCount = 0))
    }

    @Test
    fun aCloudListWithRowsIsNeverAGlitch() {
        assertFalse(SupabaseSyncManager.looksLikeEmptyCloudGlitch(cloudCount = 1, localSyncedCount = 50))
    }

    @Test
    fun nullOrDifferentSessionUserCannotBeTrustedForEmptyCloudList() {
        assertFalse(SupabaseSyncManager.canTrustEmptyCloudList(sessionUserId = null, expectedUserId = "user1"))
        assertFalse(SupabaseSyncManager.canTrustEmptyCloudList(sessionUserId = "user2", expectedUserId = "user1"))
    }

    @Test
    fun matchingSessionUserCanBeTrustedForEmptyCloudList() {
        assertTrue(SupabaseSyncManager.canTrustEmptyCloudList(sessionUserId = "user1", expectedUserId = "user1"))
    }

    @Test
    fun blankIdsOnlyMatchWhenIdenticalAndNonNull() {
        assertFalse(SupabaseSyncManager.canTrustEmptyCloudList(sessionUserId = null, expectedUserId = ""))
        assertFalse(SupabaseSyncManager.canTrustEmptyCloudList(sessionUserId = "", expectedUserId = "user1"))
        assertFalse(SupabaseSyncManager.canTrustEmptyCloudList(sessionUserId = "user1", expectedUserId = ""))
        assertTrue(SupabaseSyncManager.canTrustEmptyCloudList(sessionUserId = "", expectedUserId = ""))
        assertFalse(SupabaseSyncManager.canTrustEmptyCloudList(sessionUserId = " ", expectedUserId = ""))
    }
}
