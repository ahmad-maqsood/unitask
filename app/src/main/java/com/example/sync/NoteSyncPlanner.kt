package com.example.sync

import com.example.data.local.Note

data class CloudNote(
    val docId: String,
    val title: String,
    val body: String,
    val courseCode: String,
    val isPinned: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

sealed interface NoteSyncAction {
    data class InsertLocal(val cloud: CloudNote) : NoteSyncAction
    data class OverwriteLocal(val localId: Long, val cloud: CloudNote) : NoteSyncAction
    data class CreateConflictCopy(val localId: Long) : NoteSyncAction
    data class DeleteLocal(val localId: Long) : NoteSyncAction
    data class DeleteCloud(val docId: String, val localId: Long) : NoteSyncAction
    data class PushLocal(val localId: Long) : NoteSyncAction
}

fun planNoteSync(
    local: List<Note>,
    cloud: List<CloudNote>,
    courseCodeOf: (Long?) -> String
): List<NoteSyncAction> {
    val cloudByDocId = cloud.associateBy { it.docId }
    val localByCloudId = local.filter { it.cloudId.isNotBlank() }.associateBy { it.cloudId }

    val conflictCopies = mutableListOf<NoteSyncAction.CreateConflictCopy>()
    val overwritesAndInserts = mutableListOf<NoteSyncAction>()
    val localDeletes = mutableListOf<NoteSyncAction.DeleteLocal>()
    val cloudDeletes = mutableListOf<NoteSyncAction.DeleteCloud>()
    val pushes = mutableListOf<NoteSyncAction.PushLocal>()

    // Evaluate rules a through d for each cloud row
    for (c in cloud) {
        val matched = localByCloudId[c.docId]
        if (matched == null) {
            // Rule a: Cloud row with no local match
            overwritesAndInserts.add(NoteSyncAction.InsertLocal(c))
        } else if (matched.isDeleted) {
            // Rule b: Matched, local is deleted (the user deleted it here)
            cloudDeletes.add(NoteSyncAction.DeleteCloud(c.docId, matched.id))
        } else if (matched.isSynced) {
            // Rule c: Matched, local not deleted, local isSynced = true
            if (c.updatedAtMillis > matched.cloudUpdatedAt) {
                overwritesAndInserts.add(NoteSyncAction.OverwriteLocal(matched.id, c))
            }
        } else {
            // Rule d: Matched, local not deleted, local isSynced = false (edited here since last sync)
            if (c.updatedAtMillis == matched.cloudUpdatedAt) {
                // Cloud unchanged: push local edits
                pushes.add(NoteSyncAction.PushLocal(matched.id))
            } else {
                val isContentIdentical = matched.title == c.title &&
                    matched.body == c.body &&
                    matched.isPinned == c.isPinned &&
                    courseCodeOf(matched.courseId) == c.courseCode

                if (isContentIdentical) {
                    // Content identical: overwrite local to update cloudUpdatedAt and mark synced
                    overwritesAndInserts.add(NoteSyncAction.OverwriteLocal(matched.id, c))
                } else {
                    // Conflict: preserve local version as conflict copy, overwrite local with cloud version
                    conflictCopies.add(NoteSyncAction.CreateConflictCopy(matched.id))
                    overwritesAndInserts.add(NoteSyncAction.OverwriteLocal(matched.id, c))
                }
            }
        }
    }

    // Evaluate rule e candidates count for empty cloud glitch check
    // Candidates: local notes with cloudId not in cloud list, not deleted, and synced
    val deleteCandidates = local.filter {
        it.cloudId.isNotBlank() && it.cloudId !in cloudByDocId && !it.isDeleted && it.isSynced
    }
    val isGlitch = SupabaseSyncManager.looksLikeEmptyCloudGlitch(cloud.size, deleteCandidates.size)

    // Evaluate rule e and f for each local note
    for (n in local) {
        if (n.cloudId.isNotBlank()) {
            if (n.cloudId !in cloudByDocId) {
                // Rule e: Local note with a cloudId that is NOT in the cloud list
                if (n.isDeleted && !n.isSynced) {
                    cloudDeletes.add(NoteSyncAction.DeleteCloud(n.cloudId, n.id))
                } else if (!n.isDeleted && n.isSynced) {
                    if (!isGlitch) {
                        localDeletes.add(NoteSyncAction.DeleteLocal(n.id))
                    }
                } else if (!n.isDeleted && !n.isSynced) {
                    pushes.add(NoteSyncAction.PushLocal(n.id))
                }
            }
        } else {
            // Rule f: Local note with cloudId ""
            if (!n.isDeleted && !n.isSynced) {
                pushes.add(NoteSyncAction.PushLocal(n.id))
            }
            // Local deleted note with cloudId "": nothing
        }
    }

    // Order: conflict copies first, then overwrites and inserts, then local deletes, then cloud deletes, then pushes
    // Ensure no duplicate actions per note
    val orderedActions = mutableListOf<NoteSyncAction>()
    orderedActions.addAll(conflictCopies.distinctBy { it.localId })
    orderedActions.addAll(overwritesAndInserts.distinct())
    orderedActions.addAll(localDeletes.distinctBy { it.localId })
    orderedActions.addAll(cloudDeletes.distinctBy { it.docId })
    orderedActions.addAll(pushes.distinctBy { it.localId })

    return orderedActions
}

