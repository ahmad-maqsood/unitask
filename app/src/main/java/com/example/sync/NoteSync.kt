package com.example.sync

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.Note
import com.example.data.repository.NoteRepository
import com.example.util.SupabaseProvider
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
internal data class NoteRow(
    @SerialName("user_id") val userId: String,
    @SerialName("doc_id") val docId: String,
    val title: String = "",
    val body: String = "",
    @SerialName("course_code") val courseCode: String = "",
    @SerialName("is_pinned") val isPinned: Boolean = false,
    @SerialName("created_at_millis") val createdAtMillis: Long = 0L,
    @SerialName("updated_at_millis") val updatedAtMillis: Long = 0L
)

@Serializable
internal data class NoteHeaderRow(
    @SerialName("doc_id") val docId: String,
    @SerialName("updated_at_millis") val updatedAtMillis: Long = 0L
)

object NoteSync {

    private const val TAG = "NoteSync"
    private const val BODY_FETCH_CHUNK = 50

    private suspend fun fetchHeaders(userId: String): List<CloudNoteHeader> {
        return SupabaseSyncManager.fetchAllRows<NoteHeaderRow>("notes", userId) { from, to ->
            SupabaseProvider.client.postgrest["notes"]
                .select(columns = Columns.list("doc_id", "updated_at_millis")) {
                    filter {
                        eq("user_id", userId)
                    }
                    order("doc_id", Order.ASCENDING)
                    range(from, to)
                }.decodeList<NoteHeaderRow>()
        }.map { CloudNoteHeader(it.docId, it.updatedAtMillis) }
    }

    private suspend fun fetchRows(userId: String, docIds: List<String>): List<NoteRow> {
        val rows = mutableListOf<NoteRow>()
        for (chunk in docIds.chunked(BODY_FETCH_CHUNK)) {
            rows += SupabaseProvider.client.postgrest["notes"]
                .select {
                    filter {
                        eq("user_id", userId)
                        isIn("doc_id", chunk)
                    }
                }.decodeList<NoteRow>()
        }
        return rows
    }
    suspend fun sync(context: Context, database: AppDatabase, userId: String) {
        val noteDao = database.noteDao()
        val courseDao = database.courseDao()

        // 1. Ask the cloud which notes exist and when they changed (no text yet), then
        //    download the text only of notes that are new here or changed in the cloud
        val (cloudNotes, localNotes) = try {
            val headers = fetchHeaders(userId)
            val wanted = docIdsNeedingBody(noteDao.getAllNotesSync(), headers)
            val rowsByDocId = fetchRows(userId, wanted).associateBy { it.docId }

            // Read local notes after the download, as before, so the plan sees fresh state
            val freshLocal = noteDao.getAllNotesSync()

            // A note changed while the download ran and now needs text we do not have:
            // skip this pass, the next one picks it up
            val stillMissing = docIdsNeedingBody(freshLocal, headers).any { it !in rowsByDocId }
            if (stillMissing) return

            // Notes that did not change get a stub with no text. The planner only reads
            // docId and updatedAtMillis for them, and never overwrites anything from a stub.
            val merged = headers.map { h ->
                val row = rowsByDocId[h.docId]
                if (row != null) {
                    CloudNote(
                        docId = row.docId,
                        title = row.title,
                        body = row.body,
                        courseCode = row.courseCode,
                        isPinned = row.isPinned,
                        createdAtMillis = row.createdAtMillis,
                        updatedAtMillis = row.updatedAtMillis
                    )
                } else {
                    CloudNote(h.docId, "", "", "", false, 0L, h.updatedAtMillis)
                }
            }
            merged to freshLocal
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "Failed to fetch notes from Supabase: ${e.message}")
            return
        }

        // 2. Fetch courses
        val allCourses = courseDao.getAllCoursesList()
        val courseCodeById = allCourses.associate { it.id to SupabaseSyncManager.normalizeCode(it.code) }
        val courseCodeOf: (Long?) -> String = { id -> if (id != null) courseCodeById[id] ?: "" else "" }

        // 3. Plan sync
        val plan = planNoteSync(localNotes, cloudNotes, courseCodeOf)

        // 4. Apply non-push actions in order
        for (action in plan) {
            when (action) {
                is NoteSyncAction.CreateConflictCopy -> {
                    val local = noteDao.getNoteById(action.localId) ?: continue
                    val conflictTitle = ("Conflicted copy: " + local.title).take(NoteRepository.MAX_TITLE)
                    val copyNote = Note(
                        title = conflictTitle,
                        body = local.body,
                        courseId = local.courseId,
                        isPinned = false,
                        createdAt = local.createdAt,
                        updatedAt = System.currentTimeMillis(),
                        isDeleted = false,
                        deletedAt = 0L,
                        isSynced = false,
                        cloudId = "",
                        cloudUpdatedAt = 0L
                    )
                    noteDao.insert(copyNote)
                }

                is NoteSyncAction.InsertLocal -> {
                    val courseId = if (action.cloud.courseCode.isNotBlank()) {
                        val norm = SupabaseSyncManager.normalizeCode(action.cloud.courseCode)
                        courseDao.getCourseByNormalizedCode(norm)?.id
                    } else {
                        null
                    }
                    val newNote = Note(
                        title = action.cloud.title,
                        body = action.cloud.body,
                        courseId = courseId,
                        isPinned = action.cloud.isPinned,
                        createdAt = action.cloud.createdAtMillis,
                        updatedAt = action.cloud.updatedAtMillis,
                        isDeleted = false,
                        deletedAt = 0L,
                        isSynced = true,
                        cloudId = action.cloud.docId,
                        cloudUpdatedAt = action.cloud.updatedAtMillis
                    )
                    noteDao.insert(newNote)
                }

                is NoteSyncAction.OverwriteLocal -> {
                    val local = noteDao.getNoteById(action.localId) ?: continue
                    val courseId = if (action.cloud.courseCode.isNotBlank()) {
                        val norm = SupabaseSyncManager.normalizeCode(action.cloud.courseCode)
                        courseDao.getCourseByNormalizedCode(norm)?.id
                    } else {
                        null
                    }
                    noteDao.update(
                        local.copy(
                            title = action.cloud.title,
                            body = action.cloud.body,
                            courseId = courseId,
                            isPinned = action.cloud.isPinned,
                            createdAt = action.cloud.createdAtMillis,
                            updatedAt = action.cloud.updatedAtMillis,
                            isDeleted = false,
                            deletedAt = 0L,
                            isSynced = true,
                            cloudId = action.cloud.docId,
                            cloudUpdatedAt = action.cloud.updatedAtMillis
                        )
                    )
                }

                is NoteSyncAction.DeleteLocal -> {
                    // Deleted on another phone: keep a recoverable copy in this phone's trash.
                    // Marked synced so it is not sent back; restoring it pushes it again.
                    val local = noteDao.getNoteById(action.localId) ?: continue
                    noteDao.softDelete(local.id, System.currentTimeMillis())
                    noteDao.markSynced(local.id, local.cloudId, 0L)
                }

                is NoteSyncAction.DeleteCloud -> {
                    try {
                        SupabaseProvider.client.postgrest["notes"].delete {
                            filter {
                                eq("user_id", userId)
                                eq("doc_id", action.docId)
                            }
                        }
                        // On success mark local row isSynced = true (row stays in trash until 30-day purge)
                        noteDao.markSynced(
                            id = action.localId,
                            cloudId = action.docId,
                            cloudUpdatedAt = 0L
                        )
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        Log.w(TAG, "Cloud delete note failed: ${e.message}")
                    }
                }

                is NoteSyncAction.PushLocal -> {
                    // Handled in batch push step below
                }
            }
        }

        // 5. Push: batch upload local changes to Supabase
        // A restored (un-trashed) note has isSynced = false and keeps its cloudId,
        // so the push recreates the cloud row. Nothing extra needed.
        val pushActions = plan.filterIsInstance<NoteSyncAction.PushLocal>()
        if (pushActions.isNotEmpty()) {
            val notesToPush = mutableListOf<Pair<Note, NoteRow>>()
            for (action in pushActions) {
                val current = noteDao.getNoteById(action.localId) ?: continue
                if (current.isDeleted) continue

                val docId = if (current.cloudId.isNotBlank()) {
                    current.cloudId
                } else {
                    val newUuid = UUID.randomUUID().toString()
                    noteDao.setCloudId(current.id, newUuid)
                    newUuid
                }

                val courseCode = current.courseId?.let { courseCodeById[it] } ?: ""

                val row = NoteRow(
                    userId = userId,
                    docId = docId,
                    title = current.title,
                    body = current.body,
                    courseCode = courseCode,
                    isPinned = current.isPinned,
                    createdAtMillis = current.createdAt,
                    updatedAtMillis = current.updatedAt
                )
                notesToPush.add(current.copy(cloudId = docId) to row)
            }

            if (notesToPush.isNotEmpty()) {
                try {
                    SupabaseProvider.client.postgrest["notes"].upsert(notesToPush.map { it.second }) {
                        onConflict = "user_id,doc_id"
                    }
                    for ((note, row) in notesToPush) {
                        val current = noteDao.getNoteById(note.id) ?: continue
                        if (current.updatedAt == note.updatedAt &&
                            current.title == note.title &&
                            current.body == note.body &&
                            current.isPinned == note.isPinned &&
                            current.courseId == note.courseId &&
                            !current.isDeleted
                        ) {
                            noteDao.markSynced(
                                id = current.id,
                                cloudId = row.docId,
                                cloudUpdatedAt = row.updatedAtMillis
                            )
                        } else {
                            // Note was edited during upload; keep isSynced = false so next pass pushes newer version
                        }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.w(TAG, "Failed to push notes: ${e.message}")
                }
            }
        }
    }
}

