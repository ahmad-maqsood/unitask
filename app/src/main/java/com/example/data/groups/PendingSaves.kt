package com.example.data.groups

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Job

/**
 * Keeps track of saves that are still running (a new task on its way to the server).
 *
 * Anything done to an item before its save has finished, such as deleting, editing or ticking it,
 * must wait for the save first. Otherwise the server does not have the item yet, the action finds
 * nothing, and the save then lands and the item comes back as if nothing had happened.
 */
internal class PendingSaves {
    private val jobs = ConcurrentHashMap<String, Job>()

    /** Remember [job] as the save of [id]. It forgets itself when the job finishes. */
    fun track(id: String, job: Job) {
        jobs[id] = job
        job.invokeOnCompletion { jobs.remove(id, job) }
    }

    /** True while the save of [id] is still running. */
    fun isSaving(id: String): Boolean = jobs.containsKey(id)

    /** Returns when the save of [id] has finished, or at once if nothing is being saved for it. */
    suspend fun awaitSave(id: String) {
        jobs[id]?.join()
    }
}

/**
 * Server tasks plus tasks added on this phone that the server has not shown yet.
 *
 * A pending task counts as shown once the server list has a task with the same id. Matching by title
 * and due date would wrongly merge two different tasks that share both.
 */
internal fun mergePendingTasks(
    groupId: String,
    server: List<GroupTask>,
    pending: List<GroupTask>,
    now: Long,
    maxAgeMillis: Long
): List<GroupTask> {
    val serverIds = server.map { it.id }.toSet()
    val extra = pending.filter { p ->
        p.groupId == groupId &&
            p.id !in serverIds &&
            now - (p.createdAt?.toDate()?.time ?: 0L) < maxAgeMillis
    }
    return server + extra
}
