package com.example.data.groups

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class CachedGroupDetail(
    val uid: String = "",
    val id: String = "",
    val username: String = "",
    val name: String = "",
    val ownerId: String = "",
    val joinCode: String = ""
)

@Serializable
internal data class CachedGroupMember(
    val uid: String = "",
    val displayName: String = "",
    val role: String = "member",
    val joinedAtEpochMillis: Long? = null
)

@Serializable
internal data class CachedGroupMembers(
    val uid: String = "",
    val groupId: String = "",
    val members: List<CachedGroupMember> = emptyList()
)

/**
 * Keeps the last known details (name, owner, join code) and member list of each group on the phone,
 * so the group screen can open without internet. Tasks, courses and done marks are saved separately
 * by [GroupCacheRepository]. Everything is stored per signed-in user and is wiped on sign out together
 * with the rest of the group cache ([GroupRepository.clearCache]).
 */
internal class GroupOfflineCache(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("group_cache", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private fun detailKey(groupId: String) = "detail_$groupId"
    private fun membersKey(groupId: String) = "members_$groupId"

    fun saveDetail(uid: String, group: GroupDetail) {
        if (uid.isBlank() || group.id.isBlank()) return
        try {
            val payload = CachedGroupDetail(uid, group.id, group.username, group.name, group.ownerId, group.joinCode)
            prefs.edit().putString(detailKey(group.id), json.encodeToString(CachedGroupDetail.serializer(), payload)).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Could not save group details: ${e.message}")
        }
    }

    fun detail(uid: String, groupId: String): GroupDetail? {
        if (uid.isBlank() || groupId.isBlank()) return null
        return try {
            val text = prefs.getString(detailKey(groupId), null) ?: return null
            val cached = json.decodeFromString(CachedGroupDetail.serializer(), text)
            if (cached.uid != uid) return null
            GroupDetail(
                id = cached.id,
                username = cached.username,
                name = cached.name,
                ownerId = cached.ownerId,
                joinCode = cached.joinCode
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not read saved group details: ${e.message}")
            null
        }
    }

    fun saveMembers(uid: String, groupId: String, members: List<GroupMember>) {
        if (uid.isBlank() || groupId.isBlank()) return
        try {
            val payload = CachedGroupMembers(
                uid = uid,
                groupId = groupId,
                members = members.map {
                    CachedGroupMember(it.uid, it.displayName, it.role, it.joinedAt?.toDate()?.time)
                }
            )
            prefs.edit().putString(membersKey(groupId), json.encodeToString(CachedGroupMembers.serializer(), payload)).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Could not save group members: ${e.message}")
        }
    }

    fun members(uid: String, groupId: String): List<GroupMember>? {
        if (uid.isBlank() || groupId.isBlank()) return null
        return try {
            val text = prefs.getString(membersKey(groupId), null) ?: return null
            val cached = json.decodeFromString(CachedGroupMembers.serializer(), text)
            if (cached.uid != uid) return null
            cached.members.map {
                GroupMember(
                    uid = it.uid,
                    displayName = it.displayName,
                    role = it.role,
                    joinedAt = it.joinedAtEpochMillis?.let { millis -> Timestamp(millis) }
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read saved group members: ${e.message}")
            null
        }
    }

    /** Removes the saved copy of one group, for example after leaving or deleting it. */
    fun forget(groupId: String) {
        try {
            prefs.edit().remove(detailKey(groupId)).remove(membersKey(groupId)).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Could not remove saved group data: ${e.message}")
        }
    }

    private companion object {
        const val TAG = "GroupOfflineCache"
    }
}
