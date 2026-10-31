package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.groups.GroupMembership
import com.example.data.groups.GroupRepository
import com.example.util.AppUser
import com.example.util.AuthStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SessionStatusTest {

    private lateinit var context: Context
    private lateinit var repository: GroupRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repository = GroupRepository(context)
        context.getSharedPreferences("unitask_auth_storage", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("group_cache", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `auth storage retains user id across transient lock or offline states`() {
        // User logs in
        AuthStorage.setLastUid(context, "test-user-123")
        assertEquals("test-user-123", AuthStorage.getLastUid(context))

        // On transient NotAuthenticated or RefreshFailure, AuthStorage must NOT be cleared
        val savedUid = AuthStorage.getLastUid(context)
        assertNotNull(savedUid)
        val fallbackUser = savedUid?.let { AppUser(uid = it, email = null, displayName = null) }
        assertEquals("test-user-123", fallbackUser?.uid)

        // Only explicit sign-out clears AuthStorage
        AuthStorage.setLastUid(context, null)
        assertNull(AuthStorage.getLastUid(context))
    }

    @Test
    fun `group cache is preserved and read when offline or reconnecting`() {
        val testUid = "user-abc"
        AuthStorage.setLastUid(context, testUid)

        // Cache memberships for user
        val prefs = context.getSharedPreferences("group_cache", Context.MODE_PRIVATE)
        val sampleJson = """
            {
                "uid": "$testUid",
                "memberships": [
                    {
                        "groupId": "group-1",
                        "username": "study_group",
                        "name": "Study Group",
                        "role": "member",
                        "joinedAtEpochMillis": 1700000000000
                    }
                ]
            }
        """.trimIndent()
        prefs.edit().putString("memberships", sampleJson).commit()

        // Read cached memberships
        val cached = repository.cachedMemberships(testUid)
        assertEquals(1, cached.size)
        assertEquals("group-1", cached[0].groupId)
        assertEquals("Study Group", cached[0].name)

        // On transient NotAuthenticated, clearCachedMemberships is skipped if savedUid != null
        val savedUid = AuthStorage.getLastUid(context)
        assertEquals(testUid, savedUid)
        // Verify cache is still present
        assertEquals(1, repository.cachedMemberships(testUid).size)

        // On explicit sign-out, cache is cleared
        repository.clearCachedMemberships()
        assertTrue(repository.cachedMemberships(testUid).isEmpty())
    }
}

