package com.example.sync

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.Note
import com.example.data.repository.NoteRepository
import com.example.util.SupabaseProvider
import io.github.jan.supabase.postgrest.postgrest
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

object NoteSync {

    private const val TAG = "NoteSync"

    suspend fun sync(context: Context, database: AppDatabase, userId: String) {
        val noteDao = database.noteDao()
        val courseDao = database.courseDao()

        // 1. Fetch all cloud notes using internal paging helper
        val cloudRows = try {
            SupabaseSyncManager.fetchAllRows<NoteRow>("notes", userId)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "Failed to fetch notes from Supabase: ${e.message}")
            return
        }

        val cloudNotes = cloudRows.map { row ->
            CloudNote(
                docId = row.docId,
                title = row.title,
                body = row.body,
                courseCode = row.courseCode,
                isPinned = row.isPinned,
                createdAtMillis = row.createdAtMillis,
                updatedAtMillis = row.updatedAtMillis
            )
        }

        // 2. Fetch local notes and courses
        val allCourses = courseDao.getAllCoursesList()
        val courseCodeById = allCourses.associate { it.id to SupabaseSyncManager.normalizeCode(it.code) }
        val courseCodeOf: (Long?) -> String = { id -> if (id != null) courseCodeById[id] ?: "" else "" }
        val localNotes = noteDao.getAllNotesSync()

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
                    noteDao.deleteForever(action.localId)
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

