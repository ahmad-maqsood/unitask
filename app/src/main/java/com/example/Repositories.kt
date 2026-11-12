package com.example

import android.content.Context
import com.example.data.groups.GroupRepository
import com.example.data.local.AppDatabase
import com.example.data.repository.AppRepository
import com.example.reminder.ReminderScheduler

/**
 * The one place that builds the repositories, so screens, receivers and workers share the same
 * copy instead of each making its own. It always uses the application context, so it never holds
 * on to an Activity or a receiver's short-lived context.
 *
 * Each repository is remembered together with what it was built from. If that changes (the unit
 * tests swap the database and the application between tests), a fresh one is built instead of
 * handing out one that points at something old.
 */
object Repositories {

    private val lock = Any()

    @Volatile
    private var cachedGroup: Pair<Context, GroupRepository>? = null

    @Volatile
    private var cachedApp: Pair<AppDatabase, AppRepository>? = null

    fun group(context: Context): GroupRepository {
        val appContext = context.applicationContext
        cachedGroup?.let { if (it.first === appContext) return it.second }
        return synchronized(lock) {
            cachedGroup?.takeIf { it.first === appContext }?.second
                ?: GroupRepository(appContext).also { cachedGroup = appContext to it }
        }
    }

    fun app(context: Context): AppRepository {
        val appContext = context.applicationContext
        val db = AppDatabase.getDatabase(appContext)
        cachedApp?.let { if (it.first === db) return it.second }
        return synchronized(lock) {
            cachedApp?.takeIf { it.first === db }?.second
                ?: AppRepository(
                    context = appContext,
                    courseDao = db.courseDao(),
                    taskDao = db.taskDao(),
                    taskAttachmentDao = db.taskAttachmentDao(),
                    lectureSlotDao = db.lectureSlotDao(),
                    reminderScheduler = ReminderScheduler(appContext),
                    noteDao = db.noteDao(),
                    database = db
                ).also { cachedApp = db to it }
        }
    }
}
