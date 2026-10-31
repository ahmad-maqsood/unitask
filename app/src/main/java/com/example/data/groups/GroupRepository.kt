package com.example.data.groups

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.R
import com.example.data.local.computeDeadlineMillis
import com.example.reminder.GroupTaskProcessor
import com.example.util.SupabaseProvider
import com.example.util.currentSupabaseUid
import com.example.util.AppVisibility
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.security.SecureRandom
import java.util.Date

private const val TAG = "GroupRepository"

private fun parseSupabaseTimestamp(s: String?): Timestamp? {
    if (s.isNullOrBlank()) return null
    return try {
        val millis = com.example.util.Announcements.parseTimestamp(s)
        if (millis != null) {
            Timestamp(Date(millis))
        } else {
            Timestamp(Date.from(java.time.OffsetDateTime.parse(s).toInstant()))
        }
    } catch (_: Throwable) {
        null
    }
}

@Serializable
private data class CreateGroupResult(
    @SerialName("group_id") val groupId: String,
    @SerialName("join_code") val joinCode: String
)

@Serializable
private data class EmbeddedGroup(
    val username: String? = null,
    val name: String? = null
)

@Serializable
private data class GroupMembershipRow(
    @SerialName("group_id") val groupId: String,
    val role: String? = "member",
    @SerialName("joined_at") val joinedAt: String? = null,
    val groups: EmbeddedGroup? = null
)

@Serializable
private data class CachedMembership(
    val groupId: String,
    val username: String,
    val name: String,
    val role: String,
    val joinedAtEpochMillis: Long? = null
)

@Serializable
private data class CachedMembershipsPayload(
    val uid: String,
    val memberships: List<CachedMembership>
)

@Serializable
private data class GroupDetailRow(
    val id: String,
    val username: String? = null,
    val name: String? = null,
    @SerialName("owner_id") val ownerId: String? = null,
    @SerialName("join_code") val joinCode: String? = null
)

@Serializable
private data class GroupMemberRow(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String? = null,
    val role: String? = "member",
    @SerialName("joined_at") val joinedAt: String? = null
)

@Serializable
private data class UpdateRolePayload(
    val role: String
)

@Serializable
private data class UpdateGroupProfilePayload(
    val name: String,
    val username: String
)

@Serializable
private data class UpdateGroupCodePayload(
    @SerialName("join_code") val joinCode: String
)

@Serializable
private data class GroupTaskRow(
    val id: String = "",
    @SerialName("group_id") val groupId: String = "",
    val title: String = "",
    val description: String? = null,
    @SerialName("course_label") val courseLabel: String? = null,
    @SerialName("due_date_epoch_millis") val dueDateEpochMillis: Long = 0L,
    @SerialName("due_time_hour") val dueTimeHour: Int? = 12,
    @SerialName("due_time_minute") val dueTimeMinute: Int? = 0,
    val priority: String? = "MEDIUM",
    @SerialName("reminder_options") val reminderOptions: String? = null,
    @SerialName("assigned_to") val assignedTo: List<String> = emptyList(),
    @SerialName("created_by") val createdBy: String = "",
    @SerialName("created_by_name") val createdByName: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

private fun GroupTaskRow.toGroupTask(): GroupTask = GroupTask(
    id = id,
    groupId = groupId,
    title = title,
    description = description,
    courseLabel = courseLabel,
    dueDateEpochMillis = dueDateEpochMillis,
    dueTimeHour = dueTimeHour ?: 12,
    dueTimeMinute = dueTimeMinute ?: 0,
    priority = priority ?: "MEDIUM",
    createdBy = createdBy,
    createdByName = createdByName,
    createdAt = parseSupabaseTimestamp(createdAt),
    updatedAt = parseSupabaseTimestamp(updatedAt),
    reminderOptions = reminderOptions,
    assignedTo = assignedTo
)

@Serializable
private data class CreateGroupTaskPayload(
    @SerialName("group_id") val groupId: String,
    val title: String,
    val description: String? = null,
    @SerialName("course_label") val courseLabel: String? = null,
    @SerialName("due_date_epoch_millis") val dueDateEpochMillis: Long,
    @SerialName("due_time_hour") val dueTimeHour: Int,
    @SerialName("due_time_minute") val dueTimeMinute: Int,
    val priority: String,
    @SerialName("reminder_options") val reminderOptions: String? = null,
    @SerialName("assigned_to") val assignedTo: List<String>,
    @SerialName("created_by") val createdBy: String,
    @SerialName("created_by_name") val createdByName: String
)

@Serializable
private data class UpdateGroupTaskPayload(
    val title: String,
    val description: String? = null,
    @SerialName("course_label") val courseLabel: String? = null,
    @SerialName("due_date_epoch_millis") val dueDateEpochMillis: Long,
    @SerialName("due_time_hour") val dueTimeHour: Int,
    @SerialName("due_time_minute") val dueTimeMinute: Int,
    val priority: String,
    @SerialName("reminder_options") val reminderOptions: String? = null,
    @SerialName("assigned_to") val assignedTo: List<String>
)

@Serializable
private data class GroupCourseRow(
    val id: String = "",
    @SerialName("group_id") val groupId: String = "",
    val code: String = "",
    val name: String = "",
    val instructor: String = "",
    @SerialName("color_hex") val colorHex: String? = "#4F46E5",
    @SerialName("created_by") val createdBy: String = "",
    @SerialName("created_at") val createdAt: String? = null
)

private fun GroupCourseRow.toGroupCourse(): GroupCourse = GroupCourse(
    id = id,
    groupId = groupId,
    code = code,
    name = name,
    instructor = instructor,
    colorHex = colorHex?.ifBlank { "#4F46E5" } ?: "#4F46E5",
    createdBy = createdBy,
    createdAt = parseSupabaseTimestamp(createdAt)
)

@Serializable
private data class CreateGroupCoursePayload(
    @SerialName("group_id") val groupId: String,
    val code: String,
    val name: String,
    val instructor: String,
    @SerialName("color_hex") val colorHex: String,
    @SerialName("created_by") val createdBy: String
)

@Serializable
private data class UpdateGroupCoursePayload(
    val code: String,
    val name: String,
    val instructor: String,
    @SerialName("color_hex") val colorHex: String
)

@Serializable
private data class GroupDoneRow(
    @SerialName("user_id") val userId: String = "",
    @SerialName("group_id") val groupId: String = "",
    @SerialName("task_id") val taskId: String = ""
)

@Serializable
private data class GroupMessageRow(
    val id: Long? = null,
    @SerialName("group_id") val groupId: String = "",
    @SerialName("sender_id") val senderId: String? = null,
    @SerialName("sender_name") val senderName: String? = null,
    val text: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("reply_to_id") val replyToId: Long? = null,
    @SerialName("reply_to_name") val replyToName: String? = null,
    @SerialName("reply_to_text") val replyToText: String? = null
)

private fun GroupMessageRow.toGroupMessage(): GroupMessage {
    val createdMillis = try {
        if (!createdAt.isNullOrBlank()) {
            com.example.util.Announcements.parseTimestamp(createdAt)
                ?: java.time.OffsetDateTime.parse(createdAt).toInstant().toEpochMilli()
        } else {
            System.currentTimeMillis()
        }
    } catch (_: Throwable) {
        System.currentTimeMillis()
    }
    return GroupMessage(
        id = id?.toString().orEmpty(),
        groupId = groupId,
        text = text,
        senderId = senderId.orEmpty(),
        senderName = senderName?.ifBlank { "Student" } ?: "Student",
        createdAtMillis = createdMillis,
        replyToId = replyToId?.toString(),
        replyToName = replyToName,
        replyToText = replyToText
    )
}

@Serializable
private data class CreateGroupMessagePayload(
    @SerialName("group_id") val groupId: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("sender_name") val senderName: String,
    val text: String,
    @SerialName("reply_to_id") val replyToId: Long? = null,
    @SerialName("reply_to_name") val replyToName: String? = null,
    @SerialName("reply_to_text") val replyToText: String? = null
)

data class GroupMembership(
    val groupId: String = "",
    val username: String = "",
    val name: String = "",
    val role: String = "member",
    val joinedAt: Timestamp? = null
)

data class GroupDetail(
    val id: String = "",
    val username: String = "",
    val name: String = "",
    val ownerId: String = "",
    val joinCode: String = ""
)

data class GroupMember(
    val uid: String = "",
    val displayName: String = "",
    val role: String = "member",
    val joinedAt: Timestamp? = null
)

data class GroupTask(
    val id: String = "",
    val groupId: String = "",
    val title: String = "",
    val description: String? = null,
    val courseLabel: String? = null,
    val dueDateEpochMillis: Long = 0L,
    val dueTimeHour: Int = 12,
    val dueTimeMinute: Int = 0,
    val priority: String = "MEDIUM",
    val createdBy: String = "",
    val createdByName: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
    val reminderOptions: String? = null,
    // Uids of the tagged members. Empty means @all (every member). Tasks made before
    // tagging existed have no such field, so they read as @all too.
    val assignedTo: List<String> = emptyList()
)

/** True when this task belongs on [uid]'s Deadlines tab: it is tagged @all or tags this user. */
fun GroupTask.isForUser(uid: String): Boolean = assignedTo.isEmpty() || uid in assignedTo

/** One chat message in a group. */
data class GroupMessage(
    val id: String = "",
    val groupId: String = "",
    val text: String = "",
    val senderId: String = "",
    val senderName: String = "Student",
    val createdAtMillis: Long = 0L,
    val replyToId: String? = null,
    val replyToName: String? = null,
    val replyToText: String? = null
)

data class ChatTail(val messages: List<GroupMessage>, val fromServer: Boolean)

data class GroupCourse(
    val id: String = "",
    val groupId: String = "",
    val code: String = "",
    val name: String = "",
    val instructor: String = "",
    val colorHex: String = "#4F46E5",
    val createdBy: String = "",
    val createdAt: Timestamp? = null
)

/** One group as seen by this user: its tasks and which of them this user marked done. */
data class GroupSnapshot(
    val groupId: String,
    val groupName: String,
    val tasks: List<GroupTask>,
    val done: Set<String>,
    // Every task of the group, including ones that do not tag you. Used only for the
    // "new task" notification; Deadlines and alarms use [tasks].
    val allTasks: List<GroupTask> = tasks,
    val courses: List<GroupCourse> = emptyList()
)

/**
 * [groupIds] is every group the user belongs to. [loaded] holds only the groups whose data has
 * arrived, so a group that is still loading is never mistaken for a group with no tasks.
 */
data class GroupsState(
    val groupIds: Set<String>,
    val loaded: List<GroupSnapshot>,
    val isFromCache: Boolean = false
)

/** A group task together with what the Deadlines tab needs to show it as a personal task. */
data class GroupTaskEntry(
    val task: GroupTask,
    val groupName: String,
    val isDone: Boolean,
    val groupCourses: List<GroupCourse> = emptyList()
)


@Serializable
data class PendingOutboxMessage(
    val localId: String,
    val groupId: String,
    val uid: String,
    val senderName: String,
    val text: String,
    val replyToId: Long? = null,
    val replyToName: String? = null,
    val replyToText: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis()
)

class GroupRepository(private val context: Context) {

    private fun effectiveUid(): String? =
        currentSupabaseUid() ?: com.example.util.AuthStorage.getLastUid(context)

    private val outboxPrefs by lazy {
        context.getSharedPreferences("chat_outbox_queue", Context.MODE_PRIVATE)
    }

    private val chatPageLimits = java.util.concurrent.ConcurrentHashMap<String, Int>()

    fun loadEarlierMessages(groupId: String) {
        val current = chatPageLimits[groupId] ?: 50
        chatPageLimits[groupId] = current + 50
        localChanges.tryEmit(Unit)
    }

    private fun getQueuedOutboxMessages(groupId: String): List<PendingOutboxMessage> {
        return try {
            val raw = outboxPrefs.getString("outbox_$groupId", null) ?: return emptyList()
            json.decodeFromString<List<PendingOutboxMessage>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveQueuedOutboxMessages(groupId: String, list: List<PendingOutboxMessage>) {
        try {
            if (list.isEmpty()) {
                outboxPrefs.edit().remove("outbox_$groupId").apply()
            } else {
                outboxPrefs.edit().putString("outbox_$groupId", json.encodeToString(list)).apply()
            }
        } catch (e: Exception) {
            Log.w(TAG, "saveQueuedOutboxMessages failed: ${e.message}")
        }
    }

    // Note: A crash between the server insert and saveQueuedOutboxMessages can still produce one duplicate, because messages have no client side id on the server.
    fun flushChatOutbox(groupId: String) {
        val mutex = outboxMutexes.computeIfAbsent(groupId) { Mutex() }
        CoroutineScope(Dispatchers.IO).launch {
            mutex.withLock {
                val queued = getQueuedOutboxMessages(groupId)
                if (queued.isEmpty()) return@withLock
                for (item in queued) {
                    if (inFlightSends.contains(item.localId)) continue
                    try {
                        val payload = CreateGroupMessagePayload(
                            groupId = item.groupId,
                            senderId = item.uid,
                            senderName = item.senderName,
                            text = item.text,
                            replyToId = item.replyToId,
                            replyToName = item.replyToName,
                            replyToText = item.replyToText
                        )
                        val result = SupabaseProvider.client.postgrest["group_messages"]
                            .insert(payload) { select() }
                        val savedId = runCatching { result.decodeSingle<GroupMessageRow>().id?.toString() }.getOrNull()
                        val updatedQueued = getQueuedOutboxMessages(groupId).filter { it.localId != item.localId }
                        saveQueuedOutboxMessages(groupId, updatedQueued)
                        if (savedId != null) {
                            pendingMessages.update { list ->
                                list.map { if (it.id == item.localId) it.copy(id = savedId) else it }
                            }
                        }
                        localChanges.tryEmit(Unit)
                    } catch (e: Exception) {
                        Log.w(TAG, "flushChatOutbox message retry failed: ${e.message}")
                        break
                    }
                }
            }
        }
    }




    companion object {
        private val outboxMutexes = ConcurrentHashMap<String, Mutex>()
        private val inFlightSends = ConcurrentHashMap.newKeySet<String>()
        private const val JOIN_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private const val TIMEOUT_MILLIS = 15000L
        private const val POLL_MILLIS = 30_000L
        private const val FAST_POLL_MILLIS = 15_000L
        /** How often an open chat re-reads, as a safety net in case realtime misses an event. */
        private const val CHAT_POLL_MILLIS = 6_000L
        /** Fires when THIS phone changes group data, so open screens re-read at once. */
        val localChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
        /** Tasks added on this phone that the server has not confirmed yet. */
        private val pendingTasks = MutableStateFlow<List<GroupTask>>(emptyList())

        /** Done ticks changed on this phone that the server has not confirmed yet (task id to Pair(wanted isDone, timestamp)). */
        private val pendingDone = MutableStateFlow<Map<String, Pair<Boolean, Long>>>(emptyMap())
        private const val PENDING_DONE_MAX_AGE_MILLIS = 10_000L

        /** Messages sent on this phone that the server has not confirmed yet. */
        private val pendingMessages = MutableStateFlow<List<GroupMessage>>(emptyList())

        private const val PENDING_MAX_AGE_MILLIS = 60_000L
        private val json = Json { ignoreUnknownKeys = true }

        fun generateJoinCode(): String {
            val random = SecureRandom()
            val sb = java.lang.StringBuilder(8)
            for (i in 0 until 8) {
                sb.append(JOIN_CODE_CHARS[random.nextInt(JOIN_CODE_CHARS.length)])
            }
            return sb.toString()
        }

        fun clearCache(context: Context) {
            try {
                context.getSharedPreferences("group_cache", Context.MODE_PRIVATE).edit().clear().apply()
                context.getSharedPreferences("chat_outbox_queue", Context.MODE_PRIVATE).edit().clear().apply()
                context.getSharedPreferences("group_pending_done", Context.MODE_PRIVATE).edit().clear().apply()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear group_cache: ${e.message}")
            }
        }
    }

    private fun isGoodConnection(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true
        }
    }

    private fun isUnmeteredConnection(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        } catch (_: Exception) {
            false
        }
    }

    /** Safety net: re-read while screen is watching and app is foreground. Never poll in background. */
    private fun pollTicker(): Flow<Unit> = flow {
        while (true) {
            val interval = if (isUnmeteredConnection()) FAST_POLL_MILLIS else POLL_MILLIS
            delay(interval)
            if (AppVisibility.isForeground && isGoodConnection()) {
                emit(Unit)
            }
        }
    }

    /** Server tasks plus tasks added on this phone that the server has not shown yet. */
    private fun withPendingTasks(groupId: String, server: List<GroupTask>, pending: List<GroupTask>): List<GroupTask> {
        val now = System.currentTimeMillis()
        val mine = pending.filter { it.groupId == groupId }
        val matched = mine.filter { p ->
            server.any { s -> s.createdBy == p.createdBy && s.title == p.title && s.dueDateEpochMillis == p.dueDateEpochMillis }
        }
        val extra = mine.filter { p ->
            p !in matched && now - (p.createdAt?.toDate()?.time ?: 0L) < PENDING_MAX_AGE_MILLIS
        }
        return server + extra
    }

    /** Server messages plus messages sent on this phone that the server has not confirmed yet. */
    private fun withPendingMessages(
        groupId: String,
        server: List<GroupMessage>,
        pending: List<GroupMessage>
    ): List<GroupMessage> {
        val now = System.currentTimeMillis()
        val serverIds = server.map { it.id }.toSet()
        val extra = pending.filter { p ->
            p.groupId == groupId &&
                now - p.createdAtMillis < PENDING_MAX_AGE_MILLIS &&
                p.id !in serverIds &&
                // Only a message still in flight (no server id yet) falls back to the text match.
                !(p.id.startsWith("local-") && server.any { s ->
                    s.senderId == p.senderId &&
                    s.text == p.text &&
                    s.createdAtMillis >= p.createdAtMillis - 120_000L
                })
        }
        return (server + extra).sortedBy { it.createdAtMillis }
    }

    /**
     * Observes the signed-in user's group memberships using Supabase.
     */
    @OptIn(FlowPreview::class)
    fun observeMyGroupMemberships(uid: String): Flow<List<GroupMembership>> = callbackFlow {
        val myUid = currentSupabaseUid() ?: uid.takeIf { it.isNotBlank() } ?: com.example.util.AuthStorage.getLastUid(context)
        if (myUid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val cached = cachedMemberships(myUid)
        if (cached.isNotEmpty()) {
            trySend(cached)
        }

        suspend fun queryAndEmit() {
            try {
                val rows = SupabaseProvider.client.postgrest["group_members"]
                    .select(Columns.raw("group_id, role, joined_at, groups(username, name)")) {
                        filter {
                            eq("user_id", myUid)
                        }
                    }.decodeList<GroupMembershipRow>()
                val list = rows.map { row ->
                    val gName = row.groups?.name?.ifBlank { row.groups.username.orEmpty() } ?: row.groups?.username.orEmpty()
                    GroupMembership(
                        groupId = row.groupId,
                        username = row.groups?.username.orEmpty(),
                        name = gName.ifBlank { "Group" },
                        role = row.role ?: "member",
                        joinedAt = parseSupabaseTimestamp(row.joinedAt)
                    )
                }
                trySend(list)
                try {
                    val cachedList = list.map { m ->
                        CachedMembership(
                            groupId = m.groupId,
                            username = m.username,
                            name = m.name,
                            role = m.role,
                            joinedAtEpochMillis = m.joinedAt?.toDate()?.time
                        )
                    }
                    val payload = CachedMembershipsPayload(uid = myUid, memberships = cachedList)
                    val jsonStr = json.encodeToString(CachedMembershipsPayload.serializer(), payload)
                    context.getSharedPreferences("group_cache", Context.MODE_PRIVATE)
                        .edit()
                        .putString("memberships", jsonStr)
                        .apply()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to cache memberships: ${e.message}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "observeMyGroupMemberships select failed: ${e.message}")
                val fallback = cachedMemberships(myUid)
                if (fallback.isNotEmpty()) {
                    trySend(fallback)
                }
            }
        }

        launch { queryAndEmit() }

        val channelName = "my-memberships-$myUid-${System.nanoTime()}"
        val channel = SupabaseProvider.client.channel(channelName)

        val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "group_members"
            filter("user_id", FilterOperator.EQ, myUid)
        }

        val job = launch {
            launch {
                try {
                    channel.subscribe()
                } catch (e: Exception) {
                    Log.w(TAG, "Realtime subscribe failed: ${e.message}")
                }
                // Anything that changed while connecting was missed: read once more.
                queryAndEmit()
            }
            try {
                merge(changeFlow.map { }, localChanges, pollTicker())
                    .debounce(100L)
                    .collect { queryAndEmit() }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Refresh loop stopped: ${e.message}")
            }
        }

        awaitClose {
            job.cancel()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    channel.unsubscribe()
                    SupabaseProvider.client.realtime.removeChannel(channel)
                } catch (e: Exception) {
                    Log.w(TAG, "Error cleaning up channel $channelName: ${e.message}")
                }
            }
        }
    }

    /**
     * Reads cached memberships from SharedPreferences for [uid] immediately.
     */
    fun cachedMemberships(uid: String): List<GroupMembership> {
        if (uid.isBlank()) return emptyList()
        return try {
            val prefs = context.getSharedPreferences("group_cache", Context.MODE_PRIVATE)
            val jsonStr = prefs.getString("memberships", null) ?: return emptyList()
            val payload = json.decodeFromString(CachedMembershipsPayload.serializer(), jsonStr)
            if (payload.uid != uid) return emptyList()
            payload.memberships.map { c ->
                GroupMembership(
                    groupId = c.groupId,
                    username = c.username,
                    name = c.name,
                    role = c.role,
                    joinedAt = c.joinedAtEpochMillis?.let { Timestamp(Date(it)) }
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read cached memberships: ${e.message}")
            emptyList()
        }
    }

    fun clearCachedMemberships() {
        clearCache(context)
    }

    /**
     * Observes a specific group's main metadata document using Supabase.
     */
    @OptIn(FlowPreview::class)
    fun observeGroup(groupId: String): Flow<GroupDetail?> = callbackFlow {
        if (groupId.isBlank() || effectiveUid() == null) {
            trySend(null)
            close()
            return@callbackFlow
        }

        suspend fun queryAndEmit() {
            try {
                val rows = SupabaseProvider.client.postgrest["groups"]
                    .select {
                        filter {
                            eq("id", groupId)
                        }
                    }.decodeList<GroupDetailRow>()
                val group = rows.firstOrNull()?.let { row ->
                    GroupDetail(
                        id = row.id,
                        username = row.username.orEmpty(),
                        name = row.name.orEmpty(),
                        ownerId = row.ownerId.orEmpty(),
                        joinCode = row.joinCode.orEmpty()
                    )
                }
                trySend(group)
            } catch (e: Exception) {
                Log.w(TAG, "observeGroup select failed for $groupId: ${e.message}")
            }
        }

        launch { queryAndEmit() }

        val channelName = "group-$groupId-${System.nanoTime()}"
        val channel = SupabaseProvider.client.channel(channelName)

        val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "groups"
            filter("id", FilterOperator.EQ, groupId)
        }

        val job = launch {
            launch {
                try {
                    channel.subscribe()
                } catch (e: Exception) {
                    Log.w(TAG, "Realtime subscribe failed: ${e.message}")
                }
                // Anything that changed while connecting was missed: read once more.
                queryAndEmit()
            }
            try {
                merge(changeFlow.map { }, localChanges, pollTicker())
                    .debounce(100L)
                    .collect { queryAndEmit() }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Refresh loop stopped: ${e.message}")
            }
        }

        awaitClose {
            job.cancel()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    channel.unsubscribe()
                    SupabaseProvider.client.realtime.removeChannel(channel)
                } catch (e: Exception) {
                    Log.w(TAG, "Error cleaning up channel $channelName: ${e.message}")
                }
            }
        }
    }

    /**
     * Observes the list of members for a group using Supabase.
     */
    @OptIn(FlowPreview::class)
    fun observeGroupMembers(groupId: String): Flow<List<GroupMember>> = callbackFlow {
        if (groupId.isBlank() || effectiveUid() == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        suspend fun queryAndEmit() {
            try {
                val rows = SupabaseProvider.client.postgrest["group_members"]
                    .select {
                        filter {
                            eq("group_id", groupId)
                        }
                    }.decodeList<GroupMemberRow>()
                val members = rows.map { row ->
                    GroupMember(
                        uid = row.userId,
                        displayName = row.displayName?.ifBlank { "Student" } ?: "Student",
                        role = row.role ?: "member",
                        joinedAt = parseSupabaseTimestamp(row.joinedAt)
                    )
                }
                trySend(members)
            } catch (e: Exception) {
                Log.w(TAG, "observeGroupMembers select failed for $groupId: ${e.message}")
            }
        }

        launch { queryAndEmit() }

        val channelName = "members-$groupId-${System.nanoTime()}"
        val channel = SupabaseProvider.client.channel(channelName)

        val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "group_members"
            filter("group_id", FilterOperator.EQ, groupId)
        }

        val job = launch {
            launch {
                try {
                    channel.subscribe()
                } catch (e: Exception) {
                    Log.w(TAG, "Realtime subscribe failed: ${e.message}")
                }
                // Anything that changed while connecting was missed: read once more.
                queryAndEmit()
            }
            try {
                merge(changeFlow.map { }, localChanges, pollTicker())
                    .debounce(100L)
                    .collect { queryAndEmit() }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Refresh loop stopped: ${e.message}")
            }
        }

        awaitClose {
            job.cancel()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    channel.unsubscribe()
                    SupabaseProvider.client.realtime.removeChannel(channel)
                } catch (e: Exception) {
                    Log.w(TAG, "Error cleaning up channel $channelName: ${e.message}")
                }
            }
        }
    }

    /**
     * Observes tasks for a group using Supabase.
     */
    fun observeGroupTasks(groupId: String): Flow<List<GroupTask>> =
        combine(observeGroupTasksFromServer(groupId), pendingTasks) { server, pending ->
            withPendingTasks(groupId, server, pending)
        }

    @OptIn(FlowPreview::class)
    private fun observeGroupTasksFromServer(groupId: String): Flow<List<GroupTask>> = callbackFlow {
        if (groupId.isBlank() || effectiveUid() == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        suspend fun queryAndEmit() {
            try {
                val rows = SupabaseProvider.client.postgrest["group_tasks"]
                    .select {
                        filter {
                            eq("group_id", groupId)
                        }
                    }.decodeList<GroupTaskRow>()
                trySend(rows.map { it.toGroupTask() })
            } catch (e: Exception) {
                Log.w(TAG, "observeGroupTasks select failed for $groupId: ${e.message}")
            }
        }

        launch { queryAndEmit() }

        val channelName = "tasks-$groupId-${System.nanoTime()}"
        val channel = SupabaseProvider.client.channel(channelName)

        val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "group_tasks"
        }

        val job = launch {
            launch {
                try {
                    channel.subscribe()
                } catch (e: Exception) {
                    Log.w(TAG, "Realtime subscribe failed: ${e.message}")
                }
                // Anything that changed while connecting was missed: read once more.
                queryAndEmit()
            }
            try {
                merge(changeFlow.map { }, localChanges, pollTicker())
                    .debounce(100L)
                    .collect { queryAndEmit() }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Refresh loop stopped: ${e.message}")
            }
        }

        awaitClose {
            job.cancel()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    channel.unsubscribe()
                    SupabaseProvider.client.realtime.removeChannel(channel)
                } catch (e: Exception) {
                    Log.w(TAG, "Error cleaning up channel $channelName: ${e.message}")
                }
            }
        }
    }

    /**
     * Observes courses for a group using Supabase.
     */
    @OptIn(FlowPreview::class)
    fun observeGroupCourses(groupId: String): Flow<List<GroupCourse>> = callbackFlow {
        if (groupId.isBlank() || effectiveUid() == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        suspend fun queryAndEmit() {
            try {
                val rows = SupabaseProvider.client.postgrest["group_courses"]
                    .select {
                        filter {
                            eq("group_id", groupId)
                        }
                    }.decodeList<GroupCourseRow>()
                val sorted = rows.map { it.toGroupCourse() }.sortedBy { it.code.lowercase() }
                trySend(sorted)
            } catch (e: Exception) {
                Log.w(TAG, "observeGroupCourses select failed for $groupId: ${e.message}")
            }
        }

        launch { queryAndEmit() }

        val channelName = "courses-$groupId-${System.nanoTime()}"
        val channel = SupabaseProvider.client.channel(channelName)

        val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "group_courses"
        }

        val job = launch {
            launch {
                try {
                    channel.subscribe()
                } catch (e: Exception) {
                    Log.w(TAG, "Realtime subscribe failed: ${e.message}")
                }
                // Anything that changed while connecting was missed: read once more.
                queryAndEmit()
            }
            try {
                merge(changeFlow.map { }, localChanges, pollTicker())
                    .debounce(100L)
                    .collect { queryAndEmit() }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Refresh loop stopped: ${e.message}")
            }
        }

        awaitClose {
            job.cancel()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    channel.unsubscribe()
                    SupabaseProvider.client.realtime.removeChannel(channel)
                } catch (e: Exception) {
                    Log.w(TAG, "Error cleaning up channel $channelName: ${e.message}")
                }
            }
        }
    }

    /** Chat: the newest 100 messages of a group, oldest first. Live: updates when anyone sends. */
    fun observeGroupMessages(groupId: String): Flow<List<GroupMessage>> =
        combine(observeGroupMessagesFromServer(groupId), pendingMessages) { server, pending ->
            withPendingMessages(groupId, server, pending)
        }

    @OptIn(FlowPreview::class)
    private fun observeGroupMessagesFromServer(groupId: String): Flow<List<GroupMessage>> = callbackFlow {
        if (groupId.isBlank() || effectiveUid() == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        // Only one read runs at a time, so a slow old answer can never overwrite a newer one.
        val queryLock = kotlinx.coroutines.sync.Mutex()

        launch {
            flushChatOutbox(groupId)
            val queuedLocal = getQueuedOutboxMessages(groupId).map { item ->
                GroupMessage(
                    id = item.localId,
                    groupId = item.groupId,
                    text = item.text,
                    senderId = item.uid,
                    senderName = item.senderName,
                    createdAtMillis = item.createdAtMillis,
                    replyToId = item.replyToId?.toString(),
                    replyToName = item.replyToName,
                    replyToText = item.replyToText
                )
            }
            if (queuedLocal.isNotEmpty()) {
                pendingMessages.update { current ->
                    (current + queuedLocal).distinctBy { it.id }
                }
            }
        }

        suspend fun queryAndEmit() {
            queryLock.lock()
            try {
                val pageLimit = chatPageLimits[groupId] ?: 50
                // The timeout makes sure one hung request can never freeze the chat for good.
                val rows = withTimeout(TIMEOUT_MILLIS) {
                    SupabaseProvider.client.postgrest["group_messages"]
                        .select {
                            filter {
                                eq("group_id", groupId)
                            }
                            order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                            limit(pageLimit.toLong())
                        }.decodeList<GroupMessageRow>()
                }
                trySend(rows.map { it.toGroupMessage() }.reversed())
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                Log.w(TAG, "observeGroupMessages select timed out for $groupId")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "observeGroupMessages select failed for $groupId: ${e.message}")
            } finally {
                queryLock.unlock()
            }
        }

        launch { queryAndEmit() }

        val channelName = "messages-$groupId-${System.nanoTime()}"
        val channel = SupabaseProvider.client.channel(channelName)

        val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "group_messages"
            filter("group_id", FilterOperator.EQ, groupId)
        }

        val job = launch {
            launch {
                try {
                    channel.subscribe()
                } catch (e: Exception) {
                    Log.w(TAG, "Realtime subscribe failed for messages: ${e.message}")
                }
                // Anything that changed while connecting was missed: read once more.
                queryAndEmit()
            }

            // Fast path: realtime events and changes made on this phone.
            launch {
                try {
                    merge(changeFlow.map { }, localChanges)
                        .debounce(100L)
                        .collect { queryAndEmit() }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Messages realtime loop stopped: ${e.message}")
                }
            }

            // Safety net: its own loop, so nothing that goes wrong with realtime can stop it.
            // It keeps running for as long as the chat is being watched.
            launch {
                while (true) {
                    delay(CHAT_POLL_MILLIS)
                    if (AppVisibility.isForeground && isGoodConnection()) {
                        queryAndEmit()
                    }
                }
            }
        }

        awaitClose {
            job.cancel()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    channel.unsubscribe()
                    SupabaseProvider.client.realtime.removeChannel(channel)
                } catch (e: Exception) {
                    Log.w(TAG, "Error cleaning up channel $channelName: ${e.message}")
                }
            }
        }
    }

    /** Chat tail: the newest 50 messages of a group, oldest first, with cache vs server indicator. */
    @OptIn(FlowPreview::class)
    fun observeGroupChatTail(groupId: String): Flow<ChatTail> = callbackFlow {
        if (groupId.isBlank() || effectiveUid() == null) {
            trySend(ChatTail(emptyList(), fromServer = true))
            close()
            return@callbackFlow
        }

        suspend fun queryAndEmit() {
            try {
                val rows = SupabaseProvider.client.postgrest["group_messages"]
                    .select {
                        filter {
                            eq("group_id", groupId)
                        }
                        order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                        limit(50)
                    }.decodeList<GroupMessageRow>()
                trySend(ChatTail(rows.map { it.toGroupMessage() }.reversed(), fromServer = true))
            } catch (e: Exception) {
                Log.w(TAG, "observeGroupChatTail failed for $groupId: ${e.message}")
            }
        }

        launch { queryAndEmit() }

        val channelName = "chat-tail-$groupId-${System.nanoTime()}"
        val channel = SupabaseProvider.client.channel(channelName)

        val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "group_messages"
            filter("group_id", FilterOperator.EQ, groupId)
        }

        val job = launch {
            launch {
                try {
                    channel.subscribe()
                } catch (e: Exception) {
                    Log.w(TAG, "Realtime subscribe failed for chat tail: ${e.message}")
                }
                // Anything that changed while connecting was missed: read once more.
                queryAndEmit()
            }
            try {
                merge(changeFlow.map { }, localChanges, pollTicker())
                    .debounce(100L)
                    .collect { queryAndEmit() }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Chat tail refresh loop stopped: ${e.message}")
            }
        }

        awaitClose {
            job.cancel()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    channel.unsubscribe()
                    SupabaseProvider.client.realtime.removeChannel(channel)
                } catch (e: Exception) {
                    Log.w(TAG, "Error cleaning up channel $channelName: ${e.message}")
                }
            }
        }
    }

    /**
     * Reads up to 20 new messages created after [afterMillis] from the server once.
     * When [afterMillis] is negative, fetches only the single newest message (used to set a baseline).
     * Returns null on any failure so failures never throw out of this function.
     */
    suspend fun getNewMessagesOnce(groupId: String, afterMillis: Long): ChatTail? = try {
        if (groupId.isBlank() || currentSupabaseUid() == null) null
        else withTimeout(TIMEOUT_MILLIS) {
            val newestOnly = afterMillis < 0L
            val rows = SupabaseProvider.client.postgrest["group_messages"]
                .select {
                    filter {
                        eq("group_id", groupId)
                        if (!newestOnly) {
                            val iso = java.time.Instant.ofEpochMilli(afterMillis).toString()
                            gt("created_at", iso)
                        }
                    }
                    if (newestOnly) {
                        order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                        limit(1)
                    } else {
                        order("created_at", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
                        limit(20)
                    }
                }.decodeList<GroupMessageRow>()

            val messages = rows.map { it.toGroupMessage() }
            val ordered = if (newestOnly) messages.reversed() else messages
            ChatTail(ordered, fromServer = true)
        }
    } catch (_: Exception) {
        null
    }

    /** Sends a chat message. Non-blocking: it also works offline and goes out when you are back online. */
    fun sendMessage(
        groupId: String,
        uid: String,
        rawDisplayName: String,
        text: String,
        replyTo: GroupMessage? = null,
        onFailure: (Exception) -> Unit
    ) {
        val myUid = currentSupabaseUid()
        if (myUid == null) {
            onFailure(Exception("Not signed in"))
            return
        }
        val cleanText = text.trim().take(500)
        if (cleanText.isEmpty()) return
        val cleanName = rawDisplayName.trim().take(60).ifEmpty { "Student" }

        val replyName = replyTo?.senderName
        val replyText = replyTo?.text?.trim()?.take(120)
        val replyId = replyTo?.id?.toLongOrNull()

        val localMessage = GroupMessage(
            id = "local-${System.nanoTime()}",
            groupId = groupId,
            text = cleanText,
            senderId = myUid,
            senderName = cleanName,
            createdAtMillis = System.currentTimeMillis(),
            replyToId = replyId?.toString(),
            replyToName = replyName,
            replyToText = replyText
        )

        pendingMessages.update { list ->
            list.filter { System.currentTimeMillis() - it.createdAtMillis < PENDING_MAX_AGE_MILLIS } + localMessage
        }

        val payload = CreateGroupMessagePayload(
            groupId = groupId,
            senderId = myUid,
            senderName = cleanName,
            text = cleanText,
            replyToId = replyId,
            replyToName = replyName,
            replyToText = replyText
        )

        val outboxItem = PendingOutboxMessage(
            localId = localMessage.id,
            groupId = groupId,
            uid = myUid,
            senderName = cleanName,
            text = cleanText,
            replyToId = replyId,
            replyToName = replyName,
            replyToText = replyText,
            createdAtMillis = localMessage.createdAtMillis
        )
        val mutex = outboxMutexes.computeIfAbsent(groupId) { Mutex() }
        inFlightSends.add(localMessage.id)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                mutex.withLock {
                    val currentQueued = getQueuedOutboxMessages(groupId)
                    saveQueuedOutboxMessages(groupId, currentQueued + outboxItem)
                }
                val result = SupabaseProvider.client.postgrest["group_messages"]
                    .insert(payload) { select() }
                val savedId = runCatching { result.decodeSingle<GroupMessageRow>().id?.toString() }.getOrNull()
                mutex.withLock {
                    val updatedQueued = getQueuedOutboxMessages(groupId).filter { it.localId != localMessage.id }
                    saveQueuedOutboxMessages(groupId, updatedQueued)
                }
                if (savedId != null) {
                    pendingMessages.update { list ->
                        list.map { if (it.id == localMessage.id) it.copy(id = savedId) else it }
                    }
                }
                localChanges.tryEmit(Unit)
            } catch (e: Exception) {
                Log.w(TAG, "sendMessage failed (retained in outbox): ${e.message}", e)
                // Message is kept in outbox queue and pendingMessages so user does not lose it
                onFailure(e)
            } finally {
                inFlightSends.remove(localMessage.id)
            }
        }
    }

    /** Deletes one chat message (your own, or any message if you are the owner or an admin). */
    fun deleteMessage(groupId: String, messageId: String, onFailure: (Exception) -> Unit) {
        if (messageId.startsWith("local-") || messageId.toLongOrNull() == null) {
            val updated = getQueuedOutboxMessages(groupId).filter { it.localId != messageId }
            saveQueuedOutboxMessages(groupId, updated)
            pendingMessages.update { list -> list.filter { it.id != messageId } }
            localChanges.tryEmit(Unit)
            return
        }
        val myUid = currentSupabaseUid()
        if (myUid == null) {
            onFailure(Exception("Not signed in"))
            return
        }
        val idLong = messageId.toLong()
        pendingMessages.update { list -> list.filter { it.id != messageId } }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                SupabaseProvider.client.postgrest["group_messages"].delete {
                    filter {
                        eq("id", idLong)
                        eq("group_id", groupId)
                    }
                }
                localChanges.tryEmit(Unit)
            } catch (e: Exception) {
                Log.w(TAG, "deleteMessage failed: ${e.message}", e)
                onFailure(e)
            }
        }
    }

    private fun <T : Any> Flow<T>.startWithNull(): Flow<T?> = flow {
        emit(null)
        collect { emit(it) }
    }

    /**
     * Like [observeGroupTasks], for app-level tracking using Supabase.
     */
    private fun observeGroupTasksForApp(uid: String, groupId: String): Flow<List<GroupTask>> =
        combine(observeGroupTasksForAppFromServer(uid, groupId), pendingTasks) { server, pending ->
            withPendingTasks(groupId, server, pending)
        }

    @OptIn(FlowPreview::class)
    private fun observeGroupTasksForAppFromServer(uid: String, groupId: String): Flow<List<GroupTask>> = callbackFlow {
        val myUid = currentSupabaseUid() ?: uid.takeIf { it.isNotBlank() } ?: com.example.util.AuthStorage.getLastUid(context)
        if (groupId.isBlank() || myUid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        suspend fun queryAndEmit() {
            try {
                val rows = SupabaseProvider.client.postgrest["group_tasks"]
                    .select {
                        filter {
                            eq("group_id", groupId)
                        }
                    }.decodeList<GroupTaskRow>()
                trySend(rows.map { it.toGroupTask() })
            } catch (e: Exception) {
                Log.w(TAG, "observeGroupTasksForApp select failed for $groupId: ${e.message}")
            }
        }

        launch { queryAndEmit() }

        val channelName = "app-tasks-$groupId-${System.nanoTime()}"
        val channel = SupabaseProvider.client.channel(channelName)

        val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "group_tasks"
        }

        val job = launch {
            launch {
                try {
                    channel.subscribe()
                } catch (e: Exception) {
                    Log.w(TAG, "Realtime subscribe failed: ${e.message}")
                }
                // Anything that changed while connecting was missed: read once more.
                queryAndEmit()
            }
            try {
                merge(changeFlow.map { }, localChanges, pollTicker())
                    .debounce(100L)
                    .collect { queryAndEmit() }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Refresh loop stopped: ${e.message}")
            }
        }

        awaitClose {
            job.cancel()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    channel.unsubscribe()
                    SupabaseProvider.client.realtime.removeChannel(channel)
                } catch (e: Exception) {
                    Log.w(TAG, "Error cleaning up channel $channelName: ${e.message}")
                }
            }
        }
    }

    private fun observeGroupSnapshot(uid: String, m: GroupMembership): Flow<GroupSnapshot?> {
        val myUid = currentSupabaseUid() ?: uid
        return combine(
            observeGroupTasksForApp(myUid, m.groupId).startWithNull(),
            observeGroupDoneTasks(myUid, m.groupId).startWithNull(),
            observeGroupCourses(m.groupId).startWithNull()
        ) { tasks, done, courses ->
            if (tasks == null || done == null) null
            else GroupSnapshot(
                groupId = m.groupId,
                groupName = m.name.ifBlank { m.username }.ifBlank { "Group" },
                tasks = tasks.filter { it.isForUser(myUid) },
                allTasks = tasks,
                done = done,
                courses = courses.orEmpty()
            )
        }
    }

    /**
     * Live view of every group the user is in: tasks and personal done marks, all groups at once.
     * Runs independently of which screen is open.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeGroupsState(uid: String): Flow<GroupsState> {
        val myUid = currentSupabaseUid() ?: uid
        return observeMyGroupMemberships(myUid).distinctUntilChanged().flatMapLatest { memberships ->
            val ids = memberships.map { it.groupId }.toSet()
            if (memberships.isEmpty()) {
                flowOf(GroupsState(ids, emptyList()))
            } else {
                combine(memberships.map { observeGroupSnapshot(myUid, it) }) { all ->
                    GroupsState(ids, all.filterNotNull())
                }
            }
        }
    }

    suspend fun getGroupTasksOnce(groupId: String): Result<List<GroupTask>> {
        if (groupId.isBlank()) {
            return Result.success(emptyList())
        }
        return try {
            val rows = SupabaseProvider.client.postgrest["group_tasks"]
                .select {
                    filter {
                        eq("group_id", groupId)
                    }
                }.decodeList<GroupTaskRow>()
            Result.success(rows.map { it.toGroupTask() })
        } catch (e: Exception) {
            Log.w(TAG, "getGroupTasksOnce failed for $groupId: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getDoneTaskIdsOnce(uid: String, groupId: String): Set<String> {
        val myUid = currentSupabaseUid() ?: return emptySet()
        if (groupId.isBlank()) return emptySet()
        return try {
            val rows = SupabaseProvider.client.postgrest["group_done"]
                .select(Columns.raw("task_id")) {
                    filter {
                        eq("user_id", myUid)
                        eq("group_id", groupId)
                    }
                }.decodeList<GroupDoneRow>()
            rows.map { it.taskId }.toSet()
        } catch (e: Exception) {
            Log.w(TAG, "getDoneTaskIdsOnce failed for $groupId: ${e.message}")
            emptySet()
        }
    }

    /**
     * One-shot version of [observeGroupsState] for the background worker. Returns null when the
     * group list itself could not be read, so the caller leaves existing alarms alone. A group
     * whose data cannot be read for any reason other than "not allowed" is left out of
     * [GroupsState.loaded], so its alarms are kept too.
     */
    suspend fun fetchGroupsStateOnce(uid: String): GroupsState? {
        try {
            SupabaseProvider.client.auth.awaitInitialization()
        } catch (_: Exception) {
        }
        val myUid = currentSupabaseUid() ?: return null
        return try {
            withTimeout(TIMEOUT_MILLIS * 2) {
                val memberships = try {
                    SupabaseProvider.client.postgrest["group_members"]
                        .select(Columns.raw("group_id, role, joined_at, groups(username, name)")) {
                            filter {
                                eq("user_id", myUid)
                            }
                        }.decodeList<GroupMembershipRow>()
                } catch (e: Exception) {
                    Log.w(TAG, "fetchGroupsStateOnce memberships select failed: ${e.message}")
                    return@withTimeout null
                }

                val ids = HashSet<String>()
                val loaded = ArrayList<GroupSnapshot>()
                for (m in memberships) {
                    val gid = m.groupId
                    ids.add(gid)
                    val name = (m.groups?.name ?: m.groups?.username ?: "").ifBlank { "Group" }
                    val tasksResult = getGroupTasksOnce(gid)
                    val failure = tasksResult.exceptionOrNull()
                    if (failure == null) {
                        val allTasks = tasksResult.getOrThrow()
                        loaded.add(
                            GroupSnapshot(
                                groupId = gid,
                                groupName = name,
                                tasks = allTasks.filter { it.isForUser(myUid) },
                                done = getDoneTaskIdsOnce(myUid, gid),
                                allTasks = allTasks
                            )
                        )
                    }
                }
                GroupsState(ids, loaded)
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchGroupsStateOnce failed: ${e.message}")
            null
        }
    }

    /**
     * Observes personal done marks for tasks in this group using Supabase.
     */
    fun observeGroupDoneTasks(uid: String, groupId: String): Flow<Set<String>> =
        combine(observeGroupDoneTasksFromServer(uid, groupId), pendingDone) { server, pending ->
            val now = System.currentTimeMillis()
            val active = pending.filter { (_, pair) -> now - pair.second < PENDING_DONE_MAX_AGE_MILLIS }
            var result = server
            for ((taskId, pair) in active) {
                val wantDone = pair.first
                result = if (wantDone) result + taskId else result - taskId
            }
            result
        }.distinctUntilChanged()

    @OptIn(FlowPreview::class)
    private fun observeGroupDoneTasksFromServer(uid: String, groupId: String): Flow<Set<String>> = callbackFlow {
        val myUid = currentSupabaseUid()
        if (groupId.isBlank() || myUid == null) {
            trySend(emptySet())
            close()
            return@callbackFlow
        }

        var retried = false
        suspend fun queryAndEmit() {
            try {
                val rows = SupabaseProvider.client.postgrest["group_done"]
                    .select(Columns.raw("task_id")) {
                        filter {
                            eq("user_id", myUid)
                            eq("group_id", groupId)
                        }
                    }.decodeList<GroupDoneRow>()
                val serverSet = rows.map { it.taskId }.toSet()
                trySend(serverSet)
                val now = System.currentTimeMillis()
                pendingDone.update { p -> p.filter { (_, pair) -> now - pair.second < PENDING_DONE_MAX_AGE_MILLIS } }
            } catch (e: Exception) {
                Log.w(TAG, "observeGroupDoneTasks select failed for $groupId: ${e.message}")
                if (!retried) {
                    retried = true
                    launch {
                        delay(3000L)
                        queryAndEmit()
                    }
                }
            }
        }

        launch { queryAndEmit() }

        val channelName = "done-$groupId-$myUid-${System.nanoTime()}"
        val channel = SupabaseProvider.client.channel(channelName)

        val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "group_done"
        }

        val job = launch {
            launch {
                try {
                    channel.subscribe()
                } catch (e: Exception) {
                    Log.w(TAG, "Realtime subscribe failed: ${e.message}")
                }
                // Anything that changed while connecting was missed: read once more.
                queryAndEmit()
            }
            try {
                merge(changeFlow.map { }, localChanges, pollTicker())
                    .debounce(100L)
                    .collect { queryAndEmit() }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Refresh loop stopped: ${e.message}")
            }
        }

        awaitClose {
            job.cancel()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    channel.unsubscribe()
                    SupabaseProvider.client.realtime.removeChannel(channel)
                } catch (e: Exception) {
                    Log.w(TAG, "Error cleaning up channel $channelName: ${e.message}")
                }
            }
        }
    }

    // Done ticks the server has not accepted yet (made with no internet). Key: "userId|groupId|taskId".
    private fun pendingDonePrefs() = context.getSharedPreferences("group_pending_done", Context.MODE_PRIVATE)

    /** Sends one done tick or untick to Supabase. True when the server accepted it. */
    private suspend fun pushDone(myUid: String, groupId: String, taskId: String, isDone: Boolean): Boolean {
        return try {
            if (isDone) {
                SupabaseProvider.client.postgrest["group_done"].upsert(
                    GroupDoneRow(userId = myUid, groupId = groupId, taskId = taskId)
                ) {
                    onConflict = "user_id,task_id"
                }
            } else {
                SupabaseProvider.client.postgrest["group_done"].delete {
                    filter {
                        eq("user_id", myUid)
                        eq("task_id", taskId)
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "pushDone failed (will retry when online): ${e.message}")
            false
        }
    }

    /** Forgets a pending tick, but only if nobody changed it again in the meantime. */
    private fun clearPendingIfUnchanged(key: String, wanted: Boolean) {
        val prefs = pendingDonePrefs()
        if (prefs.contains(key) && prefs.getBoolean(key, !wanted) == wanted) {
            prefs.edit().remove(key).apply()
        }
    }

    /** Sends the done ticks that could not be sent earlier. Call it when the internet is back. */
    suspend fun flushPendingDone() {
        val prefs = pendingDonePrefs()
        for ((key, value) in prefs.all.toMap()) {
            val wanted = value as? Boolean ?: continue
            val parts = key.split("|")
            if (parts.size != 3) continue
            if (pushDone(parts[0], parts[1], parts[2], wanted)) {
                clearPendingIfUnchanged(key, wanted)
            }
        }
        localChanges.tryEmit(Unit)
    }

    /** Signed out: unsent ticks of that account are dropped. */
    fun clearPendingDone() {
        pendingDonePrefs().edit().clear().apply()
    }

    /**
     * Set personal task done status. The tick shows at once (it is saved on the phone first), then
     * it is sent to Supabase. With no internet it is kept and sent later by [flushPendingDone].
     */
    fun setTaskDone(uid: String, groupId: String, taskId: String, isDone: Boolean) {
        val myUid = currentSupabaseUid() ?: uid.takeIf { it.isNotBlank() } ?: com.example.util.AuthStorage.getLastUid(context)
        if (myUid.isNullOrBlank() || groupId.isBlank() || taskId.isBlank()) return
        // A task that is still being saved has no server id yet.
        if (taskId.startsWith("local-")) return

        // Tick (or untick) on screen at once; the server catches up a moment later.
        pendingDone.update { it + (taskId to (isDone to System.currentTimeMillis())) }

        val pendingKey = "$myUid|$groupId|$taskId"
        pendingDonePrefs().edit().putBoolean(pendingKey, isDone).apply()

        CoroutineScope(Dispatchers.IO).launch {
            // 1) Save on the phone first, so the tick shows and survives a restart, also offline.
            try {
                GroupCacheRepository(context).updateDone(myUid, groupId, taskId, isDone)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to update done in local cache: ${e.message}")
            }
            // 2) Send to the server. If that fails, the tick stays in the pending list.
            if (pushDone(myUid, groupId, taskId, isDone)) {
                clearPendingIfUnchanged(pendingKey, isDone)
                localChanges.tryEmit(Unit)
            }
        }
    }
    /**
     * Creates a new group via Supabase.
     * Returns Result containing Pair(groupId, joinCode) on success.
     */
    suspend fun createGroup(
        name: String,
        username: String,
        uid: String,
        rawDisplayName: String
    ): Result<Pair<String, String>> {
        val cleanName = name.trim().take(60)
        val cleanUsername = username.trim().lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == '_' }
        val displayName = rawDisplayName.trim().take(60).ifEmpty { "Student" }

        if (cleanName.isBlank()) {
            return Result.failure(IllegalArgumentException("Group name cannot be blank"))
        }
        if (cleanUsername.length !in 3..30) {
            return Result.failure(IllegalArgumentException("Username must be 3-30 characters"))
        }

        val currentUid = currentSupabaseUid()
            ?: return Result.failure(Exception("Not signed in"))

        return try {
            withTimeout(TIMEOUT_MILLIS) {
                val params = buildJsonObject {
                    put("p_username", cleanUsername)
                    put("p_name", cleanName)
                    put("p_display_name", displayName)
                }
                val res = SupabaseProvider.client.postgrest.rpc("create_group", params)
                val pair = try {
                    val rows = res.decodeList<CreateGroupResult>()
                    val first = rows.firstOrNull() ?: throw IllegalStateException("Empty result from create_group")
                    Pair(first.groupId, first.joinCode)
                } catch (e: Exception) {
                    val single = res.decodeSingle<CreateGroupResult>()
                    Pair(single.groupId, single.joinCode)
                }
                Result.success(pair)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Result.failure(Exception("Could not reach the server. Check your connection and try again."))
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            val friendly = if (msg.contains("username_taken", ignoreCase = true) || msg.contains("taken", ignoreCase = true)) {
                "That username is taken"
            } else {
                "That username may have just been taken, or connection failed."
            }
            Result.failure(Exception(friendly))
        }
    }

    /**
     * Joins an existing group using username and join code via Supabase.
     * Returns Result with groupId on success.
     */
    suspend fun joinGroup(
        username: String,
        joinCode: String,
        uid: String,
        rawDisplayName: String
    ): Result<String> {
        val cleanUsername = username.trim().lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == '_' }
        val cleanCode = joinCode.trim().uppercase()
        val displayName = rawDisplayName.trim().take(60).ifEmpty { "Student" }

        if (cleanUsername.isBlank() || cleanCode.isBlank()) {
            return Result.failure(IllegalArgumentException("Username and join code are required"))
        }

        val currentUid = currentSupabaseUid()
            ?: return Result.failure(Exception("Not signed in"))

        return try {
            withTimeout(TIMEOUT_MILLIS) {
                val params = buildJsonObject {
                    put("p_username", cleanUsername)
                    put("p_join_code", cleanCode)
                    put("p_display_name", displayName)
                }
                val res = SupabaseProvider.client.postgrest.rpc("join_group", params)
                val groupId = try {
                    res.decodeAs<String>()
                } catch (_: Exception) {
                    res.data.trim().trim('"')
                }
                Result.success(groupId)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Result.failure(Exception("Could not reach the server. Check your connection and try again."))
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            val friendly = if (msg.contains("invalid_group_or_code", ignoreCase = true) ||
                msg.contains("wrong join code", ignoreCase = true)
            ) {
                "Wrong join code, or that group no longer exists."
            } else if (msg.contains("already in this group", ignoreCase = true) ||
                msg.contains("already_a_member", ignoreCase = true)
            ) {
                "You are already in this group"
            } else if (msg.contains("No group with that username", ignoreCase = true)) {
                "No group with that username"
            } else {
                "Wrong join code, or that group no longer exists."
            }
            Result.failure(Exception(friendly))
        }
    }

    /**
     * Check if a membership is stale. With Supabase there is no separate bookmark list,
     * so this returns false.
     */
    suspend fun checkAndCleanStaleMembership(uid: String, groupId: String): Boolean {
        return false
    }

    /**
     * Leave a group (for regular members).
     */
    suspend fun leaveGroup(groupId: String, uid: String): Result<Unit> {
        val myUid = currentSupabaseUid() ?: return Result.failure(Exception("Not signed in"))
        if (groupId.isBlank()) return Result.failure(IllegalArgumentException("Invalid groupId"))
        return try {
            withTimeout(TIMEOUT_MILLIS) {
                SupabaseProvider.client.postgrest["group_members"].delete {
                    filter {
                        eq("group_id", groupId)
                        eq("user_id", myUid)
                    }
                }
            }
            localChanges.tryEmit(Unit)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a group (owner only) from Supabase.
     */
    suspend fun deleteGroup(groupId: String, username: String, uid: String): Result<Unit> {
        if (groupId.isBlank()) return Result.failure(IllegalArgumentException("Invalid groupId"))
        val currentUid = currentSupabaseUid() ?: return Result.failure(Exception("Not signed in"))
        return try {
            withTimeout(TIMEOUT_MILLIS) {
                SupabaseProvider.client.postgrest["groups"].delete {
                    filter {
                        eq("id", groupId)
                    }
                }
            }
            localChanges.tryEmit(Unit)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Edit group name and username (owner only).
     */
    suspend fun updateGroupProfile(groupId: String, name: String, username: String): Result<Unit> {
        val cleanName = name.trim().take(60)
        val cleanUsername = username.trim().lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == '_' }
        if (cleanName.isBlank()) return Result.failure(IllegalArgumentException("Group name cannot be blank"))
        if (cleanUsername.length !in 3..30) return Result.failure(IllegalArgumentException("Username must be 3-30 characters"))
        val currentUid = currentSupabaseUid() ?: return Result.failure(Exception("Not signed in"))

        return try {
            withTimeout(TIMEOUT_MILLIS) {
                try {
                    val params = buildJsonObject {
                        put("p_group_id", groupId)
                        put("p_name", cleanName)
                        put("p_username", cleanUsername)
                    }
                    SupabaseProvider.client.postgrest.rpc("update_group_profile", params)
                } catch (rpcEx: Exception) {
                    val msg = rpcEx.message.orEmpty()
                    if (msg.contains("username_taken", ignoreCase = true) || msg.contains("duplicate key", ignoreCase = true)) {
                        throw Exception("That username is taken")
                    }
                    SupabaseProvider.client.postgrest["groups"].update(
                        UpdateGroupProfilePayload(name = cleanName, username = cleanUsername)
                    ) {
                        filter {
                            eq("id", groupId)
                            eq("owner_id", currentUid)
                        }
                    }
                }
            }
            localChanges.tryEmit(Unit)
            Result.success(Unit)
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            val friendly = if (msg.contains("username_taken", ignoreCase = true) || msg.contains("23505")) {
                "That username is taken"
            } else {
                e.message ?: "Failed to update group"
            }
            Result.failure(Exception(friendly))
        }
    }

    /**
     * Regenerates a new 8-character join code (owner only).
     */
    suspend fun regenerateGroupCode(groupId: String): Result<String> {
        val currentUid = currentSupabaseUid() ?: return Result.failure(Exception("Not signed in"))
        return try {
            val newCode = withTimeout(TIMEOUT_MILLIS) {
                try {
                    val params = buildJsonObject { put("p_group_id", groupId) }
                    val res = SupabaseProvider.client.postgrest.rpc("regenerate_group_code", params)
                    res.decodeAs<String>()
                } catch (rpcEx: Exception) {
                    val code = generateJoinCode()
                    SupabaseProvider.client.postgrest["groups"].update(
                        UpdateGroupCodePayload(joinCode = code)
                    ) {
                        filter {
                            eq("id", groupId)
                            eq("owner_id", currentUid)
                        }
                    }
                    code
                }
            }
            localChanges.tryEmit(Unit)
            Result.success(newCode)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Create group task in Supabase (non-blocking).
     */
    fun createTask(
        groupId: String,
        title: String,
        description: String?,
        courseLabel: String?,
        dueDateEpochMillis: Long,
        dueTimeHour: Int,
        dueTimeMinute: Int,
        priority: String,
        reminderOptions: String,
        assignedTo: List<String>,
        uid: String,
        rawDisplayName: String,
        onFailure: (Exception) -> Unit
    ) {
        val myUid = currentSupabaseUid()
        if (myUid == null) {
            onFailure(Exception("Not signed in"))
            return
        }

        val cleanTitle = title.trim().take(200)
        val cleanDesc = description?.trim()?.take(2000)?.ifBlank { null }
        val cleanCourse = courseLabel?.trim()?.take(60)?.ifBlank { null }
        val cleanName = rawDisplayName.trim().take(60).ifEmpty { "Student" }
        val cleanAssigned = assignedTo.distinct().take(200)
        val cleanReminders = reminderOptions.trim().ifBlank { null }

        val payload = CreateGroupTaskPayload(
            groupId = groupId,
            title = cleanTitle,
            description = cleanDesc,
            courseLabel = cleanCourse,
            dueDateEpochMillis = dueDateEpochMillis,
            dueTimeHour = dueTimeHour,
            dueTimeMinute = dueTimeMinute,
            priority = priority,
            reminderOptions = cleanReminders,
            assignedTo = cleanAssigned,
            createdBy = myUid,
            createdByName = cleanName
        )

        // Show the task on screen at once; the server confirms it a moment later.
        val localTask = GroupTask(
            id = "local-${System.nanoTime()}",
            groupId = groupId,
            title = cleanTitle,
            description = cleanDesc,
            courseLabel = cleanCourse,
            dueDateEpochMillis = dueDateEpochMillis,
            dueTimeHour = dueTimeHour,
            dueTimeMinute = dueTimeMinute,
            priority = priority,
            createdBy = myUid,
            createdByName = cleanName,
            createdAt = Timestamp(Date()),
            updatedAt = Timestamp(Date()),
            reminderOptions = cleanReminders,
            assignedTo = cleanAssigned
        )
        pendingTasks.update { list ->
            list.filter { System.currentTimeMillis() - (it.createdAt?.toDate()?.time ?: 0L) < PENDING_MAX_AGE_MILLIS } + localTask
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                SupabaseProvider.client.postgrest["group_tasks"].insert(payload)
                localChanges.tryEmit(Unit)
            } catch (e: Exception) {
                Log.w(TAG, "createTask failed: ${e.message}", e)
                pendingTasks.update { list -> list.filter { it.id != localTask.id } }
                onFailure(e)
            }
        }
    }

    /**
     * Edit group task in Supabase (non-blocking).
     */
    fun updateTask(
        groupId: String,
        taskId: String,
        title: String,
        description: String?,
        courseLabel: String?,
        dueDateEpochMillis: Long,
        dueTimeHour: Int,
        dueTimeMinute: Int,
        priority: String,
        reminderOptions: String,
        assignedTo: List<String>,
        onFailure: (Exception) -> Unit
    ) {
        val myUid = currentSupabaseUid()
        if (myUid == null) {
            onFailure(Exception("Not signed in"))
            return
        }

        val cleanTitle = title.trim().take(200)
        val cleanDesc = description?.trim()?.take(2000)?.ifBlank { null }
        val cleanCourse = courseLabel?.trim()?.take(60)?.ifBlank { null }
        val cleanAssigned = assignedTo.distinct().take(200)
        val cleanReminders = reminderOptions.trim().ifBlank { null }

        // So this phone does not announce the user's own edit back to them.
        GroupTaskProcessor.rememberOwnEdit(
            context,
            groupId,
            taskId,
            computeDeadlineMillis(dueDateEpochMillis, dueTimeHour, dueTimeMinute),
            cleanTitle
        )

        val payload = UpdateGroupTaskPayload(
            title = cleanTitle,
            description = cleanDesc,
            courseLabel = cleanCourse,
            dueDateEpochMillis = dueDateEpochMillis,
            dueTimeHour = dueTimeHour,
            dueTimeMinute = dueTimeMinute,
            priority = priority,
            reminderOptions = cleanReminders,
            assignedTo = cleanAssigned
        )

        CoroutineScope(Dispatchers.IO).launch {
            try {
                SupabaseProvider.client.postgrest["group_tasks"].update(payload) {
                    filter {
                        eq("id", taskId)
                        eq("group_id", groupId)
                    }
                }
                localChanges.tryEmit(Unit)
            } catch (e: Exception) {
                Log.w(TAG, "updateTask failed: ${e.message}", e)
                GroupTaskProcessor.forgetOwnTask(context, groupId, taskId)
                onFailure(e)
            }
        }
    }

    /**
     * Delete a task from Supabase (non-blocking).
     */
    fun deleteTask(groupId: String, taskId: String, onFailure: (Exception) -> Unit) {
        GroupTaskProcessor.forgetOwnTask(context, groupId, taskId)
        val myUid = currentSupabaseUid()
        if (myUid == null) {
            onFailure(Exception("Not signed in"))
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                SupabaseProvider.client.postgrest["group_tasks"].delete {
                    filter {
                        eq("id", taskId)
                        eq("group_id", groupId)
                    }
                }
                localChanges.tryEmit(Unit)
            } catch (e: Exception) {
                Log.w(TAG, "deleteTask failed: ${e.message}", e)
                onFailure(e)
            }
        }
    }
    
    /**
     * Change a member's role between "member" and "admin" in Supabase.
     */
    suspend fun setMemberRole(groupId: String, memberUid: String, newRole: String): Result<Unit> {
        if (newRole != "admin" && newRole != "member") {
            return Result.failure(IllegalArgumentException("Invalid role"))
        }
        if (groupId.isBlank() || memberUid.isBlank()) {
            return Result.failure(IllegalArgumentException("Invalid groupId or memberUid"))
        }
        return try {
            withTimeout(TIMEOUT_MILLIS) {
                SupabaseProvider.client.postgrest["group_members"].update(
                    UpdateRolePayload(role = newRole)
                ) {
                    filter {
                        eq("group_id", groupId)
                        eq("user_id", memberUid)
                    }
                }
            }
            localChanges.tryEmit(Unit)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Transfer group ownership to another member.
     */
    suspend fun transferOwnership(groupId: String, newOwnerUid: String): Result<Unit> {
        val currentUid = currentSupabaseUid() ?: return Result.failure(Exception("Not signed in"))
        if (groupId.isBlank() || newOwnerUid.isBlank() || newOwnerUid == currentUid) {
            return Result.failure(IllegalArgumentException("Invalid parameters for ownership transfer"))
        }

        return try {
            withTimeout(TIMEOUT_MILLIS) {
                try {
                    val params = buildJsonObject {
                        put("p_group_id", groupId)
                        put("p_new_owner_id", newOwnerUid)
                    }
                    SupabaseProvider.client.postgrest.rpc("transfer_group_ownership", params)
                } catch (_: Exception) {
                    // Fallback to direct table updates
                    SupabaseProvider.client.postgrest["group_members"].update(
                        UpdateRolePayload(role = "owner")
                    ) {
                        filter {
                            eq("group_id", groupId)
                            eq("user_id", newOwnerUid)
                        }
                    }
                    SupabaseProvider.client.postgrest["group_members"].update(
                        UpdateRolePayload(role = "admin")
                    ) {
                        filter {
                            eq("group_id", groupId)
                            eq("user_id", currentUid)
                        }
                    }
                    SupabaseProvider.client.postgrest["groups"].update(
                        buildJsonObject { put("owner_id", newOwnerUid) }
                    ) {
                        filter {
                            eq("id", groupId)
                        }
                    }
                }
            }
            localChanges.tryEmit(Unit)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Remove a member from the group in Supabase.
     */
    suspend fun removeMember(groupId: String, memberUid: String): Result<Unit> {
        if (groupId.isBlank() || memberUid.isBlank()) {
            return Result.failure(IllegalArgumentException("Invalid groupId or memberUid"))
        }
        return try {
            withTimeout(TIMEOUT_MILLIS) {
                SupabaseProvider.client.postgrest["group_members"].delete {
                    filter {
                        eq("group_id", groupId)
                        eq("user_id", memberUid)
                    }
                }
            }
            localChanges.tryEmit(Unit)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Create a group course in Supabase (non-blocking).
     */
    fun createGroupCourse(
        groupId: String,
        code: String,
        name: String,
        instructor: String,
        colorHex: String,
        uid: String,
        onFailure: (Exception) -> Unit = {}
    ) {
        val myUid = currentSupabaseUid()
        if (myUid == null) {
            onFailure(Exception("Not signed in"))
            return
        }
        val payload = CreateGroupCoursePayload(
            groupId = groupId,
            code = code.trim().take(30).uppercase(),
            name = name.trim().take(100),
            instructor = instructor.trim().take(60),
            colorHex = colorHex.ifBlank { "#4F46E5" },
            createdBy = myUid
        )
        CoroutineScope(Dispatchers.IO).launch {
            try {
                SupabaseProvider.client.postgrest["group_courses"].insert(payload)
                localChanges.tryEmit(Unit)
            } catch (e: Exception) {
                Log.w(TAG, "Create group course failed: ${e.message}", e)
                val msg = e.message.orEmpty().lowercase()
                val isDuplicate = msg.contains("duplicate key") || msg.contains("23505") || msg.contains("unique")
                val err = if (isDuplicate) Exception("This group already has a course with that code.") else e
                onFailure(err)
            }
        }
    }

    /**
     * Update a group course in Supabase (non-blocking).
     */
    fun updateGroupCourse(
        groupId: String,
        courseId: String,
        code: String,
        name: String,
        instructor: String,
        colorHex: String,
        onFailure: (Exception) -> Unit = {}
    ) {
        val myUid = currentSupabaseUid()
        if (myUid == null) {
            onFailure(Exception("Not signed in"))
            return
        }
        val payload = UpdateGroupCoursePayload(
            code = code.trim().take(30).uppercase(),
            name = name.trim().take(100),
            instructor = instructor.trim().take(60),
            colorHex = colorHex.ifBlank { "#4F46E5" }
        )
        CoroutineScope(Dispatchers.IO).launch {
            try {
                SupabaseProvider.client.postgrest["group_courses"].update(payload) {
                    filter {
                        eq("id", courseId)
                        eq("group_id", groupId)
                    }
                }
                localChanges.tryEmit(Unit)
            } catch (e: Exception) {
                Log.w(TAG, "Update group course failed: ${e.message}", e)
                val msg = e.message.orEmpty().lowercase()
                val isDuplicate = msg.contains("duplicate key") || msg.contains("23505") || msg.contains("unique")
                val err = if (isDuplicate) Exception("This group already has a course with that code.") else e
                onFailure(err)
            }
        }
    }

    /**
     * Delete a group course in Supabase (non-blocking).
     */
    fun deleteGroupCourse(
        groupId: String,
        courseId: String,
        onFailure: (Exception) -> Unit = {}
    ) {
        val myUid = currentSupabaseUid()
        if (myUid == null) {
            onFailure(Exception("Not signed in"))
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                SupabaseProvider.client.postgrest["group_courses"].delete {
                    filter {
                        eq("id", courseId)
                        eq("group_id", groupId)
                    }
                }
                localChanges.tryEmit(Unit)
            } catch (e: Exception) {
                Log.w(TAG, "Delete group course failed: ${e.message}", e)
                onFailure(e)
            }
        }
    }
}