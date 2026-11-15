package com.example.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.example.Repositories
import com.example.data.groups.GroupTaskEntry
import com.example.data.local.TaskItem
import com.example.reminder.GroupTaskOffStore
import kotlinx.coroutines.launch

/**
 * Returns the action behind "Turn off task" / "Turn on task" in a task card's menu.
 *
 * It does the work itself, so every screen that shows a [TaskCard] (My tasks, Deadlines, the
 * calendar view and the course sheet) gets the menu item without any extra wiring:
 *  - a personal task is saved as turned off in the database (its alarms are removed or set again);
 *  - a group task is turned off on this phone only, the other members are not affected.
 */
@Composable
internal fun rememberTaskEnabledToggle(groupEntry: GroupTaskEntry?): (TaskItem) -> Unit {
    val appContext = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val latestGroupEntry by rememberUpdatedState(groupEntry)
    return remember(appContext, scope) {
        val toggle: (TaskItem) -> Unit = { task ->
            if (isGroupTaskItem(task)) {
                latestGroupEntry?.let { entry ->
                    // task.isEnabled is the state the person is looking at, so the new state is the opposite.
                    GroupTaskOffStore.setOff(appContext, entry.task.groupId, entry.task.id, off = task.isEnabled)
                }
            } else {
                scope.launch { Repositories.app(appContext).setTaskEnabled(task.id, enabled = !task.isEnabled) }
            }
        }
        toggle
    }
}
