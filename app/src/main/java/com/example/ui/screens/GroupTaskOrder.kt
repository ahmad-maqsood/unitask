package com.example.ui.screens

import com.example.data.groups.GroupTask

/**
 * The order of a group's task list, the same as the Deadlines tab: open tasks by deadline (soonest
 * first), then the tasks this person has finished, newest deadline first.
 *
 * [byDeadline] must already be sorted soonest first. The sort is stable, so tasks with the same
 * deadline keep their order.
 */
internal fun orderGroupTasks(byDeadline: List<GroupTask>, doneIds: Set<String>): List<GroupTask> {
    if (doneIds.isEmpty()) return byDeadline
    val open = byDeadline.filter { it.id !in doneIds }
    val done = byDeadline.filter { it.id in doneIds }.asReversed()
    return open + done
}
