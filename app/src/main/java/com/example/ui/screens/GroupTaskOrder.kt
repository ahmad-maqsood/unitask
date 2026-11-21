package com.example.ui.screens

import com.example.data.groups.GroupTask

/**
 * The order of a group's task list: open tasks by deadline (soonest first), then the tasks this
 * person has finished, also by deadline (soonest first).
 *
 * Finished tasks keep the same relative order as before they were marked done. Reversing them made
 * the list flip every time the done marks arrived, so two finished tasks swapped places on every open.
 *
 * [byDeadline] must already be sorted soonest first. The sort is stable, so tasks with the same
 * deadline keep their order.
 */
internal fun orderGroupTasks(byDeadline: List<GroupTask>, doneIds: Set<String>): List<GroupTask> {
    if (doneIds.isEmpty()) return byDeadline
    val open = byDeadline.filter { it.id !in doneIds }
    val done = byDeadline.filter { it.id in doneIds }
    return open + done
}
