package com.example.data.groups

import android.content.Context
import com.example.R
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.security.SecureRandom

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
    val dueTimeHour: Int = 23,
    val dueTimeMinute: Int = 59,
    val priority: String = "MEDIUM",
    val createdBy: String = "",
    val createdByName: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

class GroupRepository(private val context: Context) {

    private val db: FirebaseFirestore
        get() {
            val databaseId = context.getString(R.string.firestore_database_id)
            return FirebaseFirestore.getInstance(databaseId)
        }

    companion object {
        private const val JOIN_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private const val TIMEOUT_MILLIS = 15000L

        fun generateJoinCode(): String {
            val random = SecureRandom()
            val sb = java.lang.StringBuilder(8)
            for (i in 0 until 8) {
                sb.append(JOIN_CODE_CHARS[random.nextInt(JOIN_CODE_CHARS.length)])
            }
            return sb.toString()
        }
    }

    /**
     * Observes the signed-in user's memberships bookmark list.
     */
    fun observeMyGroupMemberships(uid: String): Flow<List<GroupMembership>> = callbackFlow {
        if (uid.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val ref = db.collection("users").document(uid).collection("groupMemberships")
        val registration = ref.addSnapshotListener { snapshot, error ->
            if (error != null) {
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val list = snapshot.documents.mapNotNull { doc ->
                    val gid = doc.getString("groupId") ?: doc.id
                    val uname = doc.getString("username") ?: ""
                    val name = doc.getString("name") ?: uname
                    val role = doc.getString("role") ?: "member"
                    val joinedAt = doc.getTimestamp("joinedAt")
                    GroupMembership(gid, uname, name, role, joinedAt)
                }
                trySend(list)
            }
        }
        awaitClose { registration.remove() }
    }

    /**
     * Observes a specific group's main metadata document.
     */
    fun observeGroup(groupId: String): Flow<GroupDetail?> = callbackFlow {
        if (groupId.isBlank()) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val ref = db.collection("groups").document(groupId)
        val registration = ref.addSnapshotListener { snapshot, error ->
            if (error != null) {
                // Return null if permission denied or not found
                trySend(null)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val detail = GroupDetail(
                    id = snapshot.id,
                    username = snapshot.getString("username") ?: "",
                    name = snapshot.getString("name") ?: "",
                    ownerId = snapshot.getString("ownerId") ?: "",
                    joinCode = snapshot.getString("joinCode") ?: ""
                )
                trySend(detail)
            } else {
                trySend(null)
            }
        }
        awaitClose { registration.remove() }
    }

    /**
     * Observes the list of members for a group.
     */
    fun observeGroupMembers(groupId: String): Flow<List<GroupMember>> = callbackFlow {
        if (groupId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val ref = db.collection("groups").document(groupId).collection("members")
        val registration = ref.addSnapshotListener { snapshot, error ->
            if (error != null) {
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val list = snapshot.documents.mapNotNull { doc ->
                    val uid = doc.getString("uid") ?: doc.id
                    val displayName = doc.getString("displayName") ?: "Student"
                    val role = doc.getString("role") ?: "member"
                    val joinedAt = doc.getTimestamp("joinedAt")
                    GroupMember(uid, displayName, role, joinedAt)
                }
                trySend(list)
            }
        }
        awaitClose { registration.remove() }
    }

    /**
     * Observes tasks for a group.
     */
    fun observeGroupTasks(groupId: String): Flow<List<GroupTask>> = callbackFlow {
        if (groupId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val ref = db.collection("groups").document(groupId).collection("tasks")
        val registration = ref.addSnapshotListener { snapshot, error ->
            if (error != null) {
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val list = snapshot.documents.mapNotNull { doc ->
                    val title = doc.getString("title") ?: return@mapNotNull null
                    val desc = doc.getString("description")
                    val courseLabel = doc.getString("courseLabel")
                    val dueEpoch = doc.getLong("dueDateEpochMillis") ?: 0L
                    val dueHour = doc.getLong("dueTimeHour")?.toInt() ?: 23
                    val dueMin = doc.getLong("dueTimeMinute")?.toInt() ?: 59
                    val priority = doc.getString("priority") ?: "MEDIUM"
                    val createdBy = doc.getString("createdBy") ?: ""
                    val createdByName = doc.getString("createdByName") ?: ""
                    val createdAt = doc.getTimestamp("createdAt")
                    val updatedAt = doc.getTimestamp("updatedAt")
                    GroupTask(
                        id = doc.id,
                        groupId = groupId,
                        title = title,
                        description = desc,
                        courseLabel = courseLabel,
                        dueDateEpochMillis = dueEpoch,
                        dueTimeHour = dueHour,
                        dueTimeMinute = dueMin,
                        priority = priority,
                        createdBy = createdBy,
                        createdByName = createdByName,
                        createdAt = createdAt,
                        updatedAt = updatedAt
                    )
                }
                trySend(list)
            }
        }
        awaitClose { registration.remove() }
    }

    /**
     * Observes personal done marks for tasks in this group.
     */
    fun observeGroupDoneTasks(uid: String, groupId: String): Flow<Set<String>> = callbackFlow {
        if (uid.isBlank() || groupId.isBlank()) {
            trySend(emptySet())
            close()
            return@callbackFlow
        }

        val ref = db.collection("users").document(uid).collection("groupDone")
            .whereEqualTo("groupId", groupId)

        val registration = ref.addSnapshotListener { snapshot, error ->
            if (error != null) {
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val doneTaskIds = snapshot.documents.mapNotNull { it.getString("taskId") }.toSet()
                trySend(doneTaskIds)
            }
        }
        awaitClose { registration.remove() }
    }

    /**
     * Set personal task done status.
     */
    fun setTaskDone(uid: String, groupId: String, taskId: String, isDone: Boolean) {
        if (uid.isBlank() || groupId.isBlank() || taskId.isBlank()) return
        val docRef = db.collection("users").document(uid).collection("groupDone").document("${groupId}_${taskId}")
        if (isDone) {
            val data = hashMapOf<String, Any>(
                "groupId" to groupId,
                "taskId" to taskId,
                "doneAt" to FieldValue.serverTimestamp()
            )
            docRef.set(data)
        } else {
            docRef.delete()
        }
    }

    /**
     * Creates a new group.
     * Returns Result containing the join code on success.
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
        if (cleanUsername.length !in 3..20) {
            return Result.failure(IllegalArgumentException("Username must be 3-20 characters"))
        }

        return try {
            withTimeout(TIMEOUT_MILLIS) {
                // 1. Pre-check username
                val nameDoc = db.collection("groupNames").document(cleanUsername).get().await()
                if (nameDoc.exists()) {
                    return@withTimeout Result.failure(IllegalStateException("That username is taken"))
                }

                // 2. Generate join code and groupId
                val joinCode = generateJoinCode()
                val groupId = db.collection("groups").document().id

                // 3. Commit ONE WriteBatch with exactly 4 writes
                val batch = db.batch()

                val groupNameRef = db.collection("groupNames").document(cleanUsername)
                val groupRef = db.collection("groups").document(groupId)
                val memberRef = groupRef.collection("members").document(uid)
                val membershipRef = db.collection("users").document(uid).collection("groupMemberships").document(groupId)

                batch.set(groupNameRef, hashMapOf(
                    "groupId" to groupId,
                    "ownerId" to uid,
                    "createdAt" to FieldValue.serverTimestamp()
                ))

                batch.set(groupRef, hashMapOf(
                    "username" to cleanUsername,
                    "name" to cleanName,
                    "ownerId" to uid,
                    "joinCode" to joinCode,
                    "createdAt" to FieldValue.serverTimestamp()
                ))

                batch.set(memberRef, hashMapOf(
                    "uid" to uid,
                    "displayName" to displayName,
                    "role" to "owner",
                    "joinedAt" to FieldValue.serverTimestamp()
                ))

                batch.set(membershipRef, hashMapOf(
                    "groupId" to groupId,
                    "username" to cleanUsername,
                    "name" to cleanName,
                    "role" to "owner",
                    "joinedAt" to FieldValue.serverTimestamp()
                ))

                batch.commit().await()
                Result.success(Pair(groupId, joinCode))
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Result.failure(Exception("Could not reach the server. Check your connection and try again."))
        } catch (e: Exception) {
            Result.failure(Exception("That username may have just been taken, or connection failed."))
        }
    }

    /**
     * Joins an existing group using username and join code.
     * Returns Result with groupId on success.
     */
    suspend fun joinGroup(
        username: String,
        joinCode: String,
        uid: String,
        rawDisplayName: String
    ): Result<String> {
        val cleanUsername = username.trim().lowercase()
        val cleanCode = joinCode.trim().uppercase()
        val displayName = rawDisplayName.trim().take(60).ifEmpty { "Student" }

        if (cleanUsername.isBlank() || cleanCode.isBlank()) {
            return Result.failure(IllegalArgumentException("Username and join code are required"))
        }

        return try {
            withTimeout(TIMEOUT_MILLIS) {
                // 1. Get groupNames/{username}
                val nameDoc = db.collection("groupNames").document(cleanUsername).get().await()
                if (!nameDoc.exists()) {
                    return@withTimeout Result.failure(IllegalArgumentException("No group with that username"))
                }

                val groupId = nameDoc.getString("groupId")
                    ?: return@withTimeout Result.failure(IllegalArgumentException("No group with that username"))

                                // Check the real member document (not the bookmark) so that someone who was
                // removed from the group can join again with the code.
                val existingMember = try {
                    db.collection("groups").document(groupId)
                        .collection("members").document(uid).get().await()
                } catch (_: FirebaseFirestoreException) {
                    null
                }
                if (existingMember != null && existingMember.exists()) {
                    return@withTimeout Result.failure(IllegalStateException("You are already in this group"))
                }

                // 2. Commit ONE WriteBatch
                val batch = db.batch()
                val memberRef = db.collection("groups").document(groupId).collection("members").document(uid)
                val membershipRef = db.collection("users").document(uid).collection("groupMemberships").document(groupId)

                batch.set(memberRef, hashMapOf(
                    "uid" to uid,
                    "displayName" to displayName,
                    "role" to "member",
                    "joinCode" to cleanCode,
                    "joinedAt" to FieldValue.serverTimestamp()
                ))

                batch.set(membershipRef, hashMapOf(
                    "groupId" to groupId,
                    "username" to cleanUsername,
                    "name" to cleanUsername,
                    "role" to "member",
                    "joinedAt" to FieldValue.serverTimestamp()
                ))

                try {
                    batch.commit().await()
                } catch (e: FirebaseFirestoreException) {
                    return@withTimeout Result.failure(Exception("Wrong join code, or that group no longer exists."))
                }

                // 3. After joining succeeds, read groups/{groupId} and update name in groupMemberships
                try {
                    val groupDoc = db.collection("groups").document(groupId).get().await()
                    val realName = groupDoc.getString("name")
                    if (!realName.isNullOrBlank()) {
                        membershipRef.update("name", realName.take(60)).await()
                    }
                } catch (_: Exception) {
                    // Non-fatal, membership already created with username
                }

                Result.success(groupId)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Result.failure(Exception("Could not reach the server. Check your connection and try again."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Check if a membership is stale (group no longer exists or permission denied).
     * If so, safely delete it. Never delete on UNAVAILABLE or network error.
     */
    suspend fun checkAndCleanStaleMembership(uid: String, groupId: String): Boolean {
        if (uid.isBlank() || groupId.isBlank()) return false
        return try {
            val groupDoc = db.collection("groups").document(groupId).get().await()
            if (!groupDoc.exists()) {
                db.collection("users").document(uid).collection("groupMemberships").document(groupId).delete().await()
                true
            } else {
                false
            }
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ||
                e.code == FirebaseFirestoreException.Code.NOT_FOUND
            ) {
                try {
                    db.collection("users").document(uid).collection("groupMemberships").document(groupId).delete().await()
                } catch (_: Exception) {}
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Leave a group (for regular members).
     */
    suspend fun leaveGroup(groupId: String, uid: String): Result<Unit> {
        return try {
            val batch = db.batch()
            val memberRef = db.collection("groups").document(groupId).collection("members").document(uid)
            val membershipRef = db.collection("users").document(uid).collection("groupMemberships").document(groupId)

            batch.delete(memberRef)
            batch.delete(membershipRef)

            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a group (owner only).
     * 1. Delete every document in groups/{groupId}/tasks (batches <= 400).
     * 2. Delete every member document in groups/{groupId}/members except own (batches <= 400).
     * 3. ONE final batch: delete own member doc, group doc, groupNames doc, and user's groupMembership doc.
     */
    suspend fun deleteGroup(groupId: String, username: String, uid: String): Result<Unit> {
        return try {
            // 1. Delete tasks in batches <= 400
            val tasksSnapshot = db.collection("groups").document(groupId).collection("tasks").get().await()
            val taskDocs = tasksSnapshot.documents
            for (chunk in taskDocs.chunked(400)) {
                val batch = db.batch()
                for (doc in chunk) {
                    batch.delete(doc.reference)
                }
                batch.commit().await()
            }

            // 2. Delete all members except own in batches <= 400
            val membersSnapshot = db.collection("groups").document(groupId).collection("members").get().await()
            val otherMembers = membersSnapshot.documents.filter { it.id != uid }
            for (chunk in otherMembers.chunked(400)) {
                val batch = db.batch()
                for (doc in chunk) {
                    batch.delete(doc.reference)
                }
                batch.commit().await()
            }

            // 3. Final batch
            val finalBatch = db.batch()
            val ownMemberRef = db.collection("groups").document(groupId).collection("members").document(uid)
            val groupRef = db.collection("groups").document(groupId)
            val groupNameRef = db.collection("groupNames").document(username)
            val membershipRef = db.collection("users").document(uid).collection("groupMemberships").document(groupId)

            finalBatch.delete(ownMemberRef)
            finalBatch.delete(groupRef)
            finalBatch.delete(groupNameRef)
            finalBatch.delete(membershipRef)

            finalBatch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Create group task (non-blocking offline write).
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
        uid: String,
        rawDisplayName: String,
        onFailure: (Exception) -> Unit
    ) {
        val taskRef = db.collection("groups").document(groupId).collection("tasks").document()
        val data = hashMapOf<String, Any>(
            "title" to title.trim().take(200),
            "dueDateEpochMillis" to dueDateEpochMillis,
            "dueTimeHour" to dueTimeHour,
            "dueTimeMinute" to dueTimeMinute,
            "priority" to priority,
            "createdBy" to uid,
            "createdByName" to rawDisplayName.trim().take(60).ifEmpty { "Student" },
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val cleanDesc = description?.trim()?.take(2000)
        if (!cleanDesc.isNullOrBlank()) {
            data["description"] = cleanDesc
        }

        val cleanCourse = courseLabel?.trim()?.take(60)
        if (!cleanCourse.isNullOrBlank()) {
            data["courseLabel"] = cleanCourse
        }

        taskRef.set(data).addOnFailureListener { e ->
            onFailure(e)
        }
    }

    /**
     * Edit group task using update() with only modified fields + updatedAt.
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
        onFailure: (Exception) -> Unit
    ) {
        val taskRef = db.collection("groups").document(groupId).collection("tasks").document(taskId)
        val updates = hashMapOf<String, Any>(
            "title" to title.trim().take(200),
            "dueDateEpochMillis" to dueDateEpochMillis,
            "dueTimeHour" to dueTimeHour,
            "dueTimeMinute" to dueTimeMinute,
            "priority" to priority,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val cleanDesc = description?.trim()?.take(2000)
        if (cleanDesc.isNullOrBlank()) {
            updates["description"] = FieldValue.delete()
        } else {
            updates["description"] = cleanDesc
        }

        val cleanCourse = courseLabel?.trim()?.take(60)
        if (cleanCourse.isNullOrBlank()) {
            updates["courseLabel"] = FieldValue.delete()
        } else {
            updates["courseLabel"] = cleanCourse
        }

        taskRef.update(updates).addOnFailureListener { e ->
            onFailure(e)
        }
    }

    /**
     * Delete a task.
     */
    fun deleteTask(groupId: String, taskId: String, onFailure: (Exception) -> Unit) {
        val taskRef = db.collection("groups").document(groupId).collection("tasks").document(taskId)
        taskRef.delete().addOnFailureListener { e ->
            onFailure(e)
        }
    }
    
    /**
     * Change a member's role between "member" and "admin" (owner only, enforced by the rules).
     */
    suspend fun setMemberRole(groupId: String, memberUid: String, newRole: String): Result<Unit> {
        if (newRole != "admin" && newRole != "member") {
            return Result.failure(IllegalArgumentException("Invalid role"))
        }
        return try {
            withTimeout(TIMEOUT_MILLIS) {
                db.collection("groups").document(groupId)
                    .collection("members").document(memberUid)
                    .update("role", newRole).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Remove a member from the group (owner can remove anyone, admin can remove plain members).
     */
    suspend fun removeMember(groupId: String, memberUid: String): Result<Unit> {
        return try {
            withTimeout(TIMEOUT_MILLIS) {
                db.collection("groups").document(groupId)
                    .collection("members").document(memberUid)
                    .delete().await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
