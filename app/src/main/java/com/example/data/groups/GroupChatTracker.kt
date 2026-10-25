package com.example.data.groups

import android.content.Context
import android.content.SharedPreferences

data class GroupChatInfo(
    val groupId: String,
    val unreadCount: Int,          // messages from OTHER people newer than lastRead
    val lastMessage: GroupMessage?, // newest message of the group, any sender, or null
    val lastReadMillis: Long        // 0 when unknown
)

class GroupChatTracker(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("group_chat_read", Context.MODE_PRIVATE)

    fun lastRead(groupId: String): Long? {
        return if (prefs.contains(groupId)) {
            prefs.getLong(groupId, 0L)
        } else {
            null
        }
    }

    fun markRead(groupId: String, upToMillis: Long) {
        if (groupId.isBlank()) return
        val current = lastRead(groupId) ?: 0L
        if (upToMillis > current) {
            prefs.edit().putLong(groupId, upToMillis).apply()
        }
    }

    fun setBaseline(groupId: String, newestMillis: Long) {
        if (groupId.isBlank()) return
        if (!prefs.contains(groupId)) {
            prefs.edit().putLong(groupId, newestMillis).apply()
        }
    }

    fun retainOnly(groupIds: Set<String>) {
        val allKeys = prefs.all.keys
        val toRemove = allKeys.filter { it !in groupIds }
        if (toRemove.isNotEmpty()) {
            val editor = prefs.edit()
            toRemove.forEach { editor.remove(it) }
            editor.apply()
        }
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
