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
}
