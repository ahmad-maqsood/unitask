package com.example.data.groups

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Date

internal fun parseSupabaseTimestamp(s: String?): Timestamp? {
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
internal data class CreateGroupResult(
    @SerialName("group_id") val groupId: String,
    @SerialName("join_code") val joinCode: String
)

@Serializable
internal data class EmbeddedGroup(
    val username: String? = null,
    val name: String? = null
)

@Serializable
internal data class GroupMembershipRow(
    @SerialName("group_id") val groupId: String,
    val role: String? = "member",
    @SerialName("joined_at") val joinedAt: String? = null,
    val groups: EmbeddedGroup? = null
)

@Serializable
internal data class CachedMembership(
    val groupId: String,
    val username: String,
    val name: String,
    val role: String,
    val joinedAtEpochMillis: Long? = null
)

@Serializable
internal data class CachedMembershipsPayload(
    val uid: String,
    val memberships: List<CachedMembership>
)

@Serializable
internal data class GroupDetailRow(
    val id: String,
    val username: String? = null,
    val name: String? = null,
    @SerialName("owner_id") val ownerId: String? = null,
    @SerialName("join_code") val joinCode: String? = null
)

@Serializable
internal data class GroupMemberRow(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String? = null,
    val role: String? = "member",
    @SerialName("joined_at") val joinedAt: String? = null
)

@Serializable
internal data class UpdateRolePayload(
    val role: String
)

@Serializable
internal data class UpdateGroupProfilePayload(
    val name: String,
    val username: String
)

@Serializable
internal data class UpdateGroupCodePayload(
    @SerialName("join_code") val joinCode: String
)

@Serializable
internal data class GroupTaskRow(
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

internal fun GroupTaskRow.toGroupTask(): GroupTask = GroupTask(
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
internal data class CreateGroupTaskPayload(
    /** Made on the phone, so a new task has its real id from the start. */
    val id: String,
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
internal data class UpdateGroupTaskPayload(
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
internal data class GroupCourseRow(
    val id: String = "",
    @SerialName("group_id") val groupId: String = "",
    val code: String = "",
    val name: String = "",
    val instructor: String = "",
    @SerialName("color_hex") val colorHex: String? = "#4F46E5",
    @SerialName("created_by") val createdBy: String = "",
    @SerialName("created_at") val createdAt: String? = null
)

internal fun GroupCourseRow.toGroupCourse(): GroupCourse = GroupCourse(
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
internal data class CreateGroupCoursePayload(
    @SerialName("group_id") val groupId: String,
    val code: String,
    val name: String,
    val instructor: String,
    @SerialName("color_hex") val colorHex: String,
    @SerialName("created_by") val createdBy: String
)

@Serializable
internal data class UpdateGroupCoursePayload(
    val code: String,
    val name: String,
    val instructor: String,
    @SerialName("color_hex") val colorHex: String
)

@Serializable
internal data class GroupDoneRow(
    @SerialName("user_id") val userId: String = "",
    @SerialName("group_id") val groupId: String = "",
    @SerialName("task_id") val taskId: String = ""
)
