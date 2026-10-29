package com.example.data.groups

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.GroupCacheEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class CachedGroupTask(
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
    val reminderOptions: String? = null,
    val assignedTo: List<String> = emptyList(),
    val createdAtMillis: Long? = null,
    val updatedAtMillis: Long? = null
)

@Serializable
internal data class CachedGroupCourse(
    val id: String = "",
    val groupId: String = "",
    val code: String = "",
    val name: String = "",
    val instructor: String = "",
    val colorHex: String = "#4F46E5"
)

private fun GroupTask.toCached(): CachedGroupTask = CachedGroupTask(
    id = id,
    groupId = groupId,
    title = title,
    description = description,
    courseLabel = courseLabel,
    dueDateEpochMillis = dueDateEpochMillis,
    dueTimeHour = dueTimeHour,
    dueTimeMinute = dueTimeMinute,
    priority = priority,
    createdBy = createdBy,
    createdByName = createdByName,
    reminderOptions = reminderOptions,
    assignedTo = assignedTo,
    createdAtMillis = createdAt?.toDate()?.time,
    updatedAtMillis = updatedAt?.toDate()?.time
)

private fun CachedGroupTask.toGroupTask(): GroupTask = GroupTask(
    id = id,
    groupId = groupId,
    title = title,
    description = description,
    courseLabel = courseLabel,
    dueDateEpochMillis = dueDateEpochMillis,
    dueTimeHour = dueTimeHour,
    dueTimeMinute = dueTimeMinute,
    priority = priority,
    createdBy = createdBy,
    createdByName = createdByName,
    reminderOptions = reminderOptions,
    assignedTo = assignedTo,
    createdAt = createdAtMillis?.let { Timestamp(it) },
    updatedAt = updatedAtMillis?.let { Timestamp(it) }
)

private fun GroupCourse.toCached(): CachedGroupCourse = CachedGroupCourse(
    id = id,
    groupId = groupId,
    code = code,
    name = name,
    instructor = instructor,
    colorHex = colorHex
)

private fun CachedGroupCourse.toGroupCourse(): GroupCourse = GroupCourse(
    id = id,
    groupId = groupId,
    code = code,
    name = name,
    instructor = instructor,
    colorHex = colorHex
)

private val cacheJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

class GroupCacheRepository(context: Context) {
    private val dao = AppDatabase.getDatabase(context).groupCacheDao()

    /** Turns the saved rows of one user into the state the screens and alarms use. */
    private fun toState(userId: String, entities: List<GroupCacheEntity>): GroupsState {
        val snapshots = entities.map { entity ->
            val allTasks = try {
                cacheJson.decodeFromString<List<CachedGroupTask>>(entity.tasksJson).map { it.toGroupTask() }
            } catch (_: Exception) {
                emptyList()
            }
            val doneSet = try {
                cacheJson.decodeFromString<List<String>>(entity.doneJson).toSet()
            } catch (_: Exception) {
                emptySet()
            }
            val courses = try {
                cacheJson.decodeFromString<List<CachedGroupCourse>>(entity.coursesJson).map { it.toGroupCourse() }
            } catch (_: Exception) {
                emptyList()
            }
            GroupSnapshot(
                groupId = entity.groupId,
                groupName = entity.groupName,
                tasks = allTasks.filter { it.isForUser(userId) },
                done = doneSet,
                allTasks = allTasks,
                courses = courses
            )
        }
        return GroupsState(
            groupIds = entities.map { it.groupId }.toSet(),
            loaded = snapshots,
            isFromCache = true
        )
    }

    /** One-time read of what is saved on the phone. Null when nothing is saved. */
    suspend fun getGroupsState(userId: String): GroupsState? {
        if (userId.isBlank()) return null
        return try {
            val entities = dao.getForUser(userId)
            if (entities.isEmpty()) null else toState(userId, entities)
        } catch (e: Exception) {
            Log.w("GroupCacheRepository", "Failed to read cached group state: ${e.message}")
            null
        }
    }

    /**
     * Live view of what is saved on the phone. It needs no internet, and it emits again every time
     * the saved data changes (a sync, a deleted task, a done tick). The Deadlines tab reads this.
     */
    fun observeGroupsState(userId: String): Flow<GroupsState> {
        if (userId.isBlank()) {
            return kotlinx.coroutines.flow.flowOf(GroupsState(emptySet(), emptyList(), isFromCache = true))
        }
        return dao.observeForUser(userId)
            .map { entities -> toState(userId, entities) }
            .flowOn(Dispatchers.Default)
    }

    /**
     * Saves what the server just told us. Groups that are in [GroupsState.loaded] are replaced
     * (so tasks deleted in the group disappear); groups still loading keep their old copy;
     * groups the user left are removed. Rows that did not change are not written again.
     */
    suspend fun saveGroupsState(userId: String, state: GroupsState) {
        if (userId.isBlank() || state.isFromCache) return
        try {
            val existing = dao.getForUser(userId).associateBy { it.groupId }
            val changed = ArrayList<GroupCacheEntity>()
            for (snapshot in state.loaded) {
                // "local-" tasks are still being saved on this phone and have no real id yet.
                val tasksJson = cacheJson.encodeToString(
                    snapshot.allTasks
                        .filterNot { it.id.startsWith("local-") }
                        .sortedBy { it.id }
                        .map { it.toCached() }
                )
                val doneJson = cacheJson.encodeToString(snapshot.done.toList().sorted())
                val coursesJson = cacheJson.encodeToString(
                    snapshot.courses.sortedBy { it.id }.map { it.toCached() }
                )
                val old = existing[snapshot.groupId]
                val same = old != null &&
                    old.groupName == snapshot.groupName &&
                    old.tasksJson == tasksJson &&
                    old.doneJson == doneJson &&
                    old.coursesJson == coursesJson
                if (!same) {
                    changed.add(
                        GroupCacheEntity(
                            userId = userId,
                            groupId = snapshot.groupId,
                            groupName = snapshot.groupName,
                            tasksJson = tasksJson,
                            doneJson = doneJson,
                            coursesJson = coursesJson,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
            dao.deleteGroupsNotIn(userId, state.groupIds.toList())
            if (changed.isNotEmpty()) {
                dao.insertOrReplace(changed)
            }
        } catch (e: Exception) {
            Log.w("GroupCacheRepository", "Failed to save group cache: ${e.message}")
        }
    }

    suspend fun clearCache(userId: String? = null) {
        try {
            if (userId != null) {
                dao.clearForUser(userId)
            } else {
                dao.clearAll()
            }
        } catch (e: Exception) {
            Log.w("GroupCacheRepository", "Failed to clear group cache: ${e.message}")
        }
    }

    suspend fun updateDone(userId: String, groupId: String, taskId: String, isDone: Boolean) {
        if (userId.isBlank() || groupId.isBlank() || taskId.isBlank()) return
        try {
            val entity = dao.getGroup(userId, groupId) ?: return
            val currentDone = try {
                cacheJson.decodeFromString<List<String>>(entity.doneJson).toMutableSet()
            } catch (_: Exception) {
                mutableSetOf()
            }
            if (isDone) currentDone.add(taskId) else currentDone.remove(taskId)
            dao.updateDone(userId, groupId, cacheJson.encodeToString(currentDone.toList()))
        } catch (e: Exception) {
            Log.w("GroupCacheRepository", "Failed to update done in cache: ${e.message}")
        }
    }
}