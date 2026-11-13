package com.example.data.groups

import kotlinx.serialization.Serializable

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
