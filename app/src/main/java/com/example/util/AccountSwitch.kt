package com.example.util

import android.content.Context
import androidx.work.WorkManager
import com.example.data.groups.GroupCacheRepository
import com.example.data.groups.GroupChatTracker
import com.example.data.groups.GroupRepository
import com.example.reminder.GroupChatNotifier
import com.example.reminder.GroupReminderScheduler
import com.example.reminder.GroupTaskProcessor

/**
 * Returns true only when both user ids are non-blank and different.
 * This indicates that a different account has signed in and group state
 * (group caches and group alarms) should be reset. Personal data on the
 * phone is untouched.
 */
fun shouldResetGroupState(previousUid: String?, newUid: String?): Boolean {
    return !previousUid.isNullOrBlank() && !newUid.isNullOrBlank() && previousUid != newUid
}

/**
 * Resets group-only caches, tracker records, pending done ticks, and group reminders
 * when switching to a different user account. Personal data (courses, tasks, slots,
 * notes, reminders) is preserved on the phone.
 */
suspend fun resetGroupStateOnAccountSwitch(context: Context) {
    runCatching { GroupReminderScheduler(context).cancelGroupsNotIn(emptySet()) }
    runCatching { GroupRepository.clearCache(context) }
    runCatching { GroupCacheRepository(context).clearCache() }
    runCatching { GroupTaskProcessor(context).clearAll() }
    runCatching { GroupRepository(context).clearPendingDone() }
    runCatching { GroupChatTracker(context).clearAll() }
    runCatching { GroupChatNotifier(context).clearAll() }
}

/**
 * Clears group caches, reminders, background sync, and auth storage when an online
 * account is deleted. Personal data and local note images remain safe on the phone.
 */
fun clearGroupDataForDeletedAccount(context: Context) {
    runCatching { GroupReminderScheduler(context).cancelGroupsNotIn(emptySet()) }
    runCatching { GroupTaskProcessor(context).clearAll() }
    runCatching { GroupRepository.clearCache(context) }
    runCatching { GroupChatTracker(context).clearAll() }
    runCatching { GroupChatNotifier(context).clearAll() }
    runCatching { AuthStorage.clear(context) }
    runCatching { WorkManager.getInstance(context).cancelUniqueWork("unitask_periodic_sync") }
}
