package com.example.util

import android.content.Context

/**
 * Persists the last known Supabase user ID on the device.
 * When the phone is offline and Supabase returns SessionStatus.RefreshFailure,
 * this allows the app to know which user is signed in and load their offline cache.
 * Cleared immediately on explicit sign-out.
 */
object AuthStorage {
    private const val PREFS = "unitask_auth_storage"
    private const val KEY_LAST_UID = "last_known_supabase_uid"
    private const val KEY_LAST_SYNCED_UID = "last_synced_supabase_uid"
    private const val KEY_LAST_SYNCED_TIMESTAMP = "last_synced_timestamp"

    fun getLastUid(context: Context): String? {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_UID, null)
    }

    fun setLastUid(context: Context, uid: String?) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (uid.isNullOrBlank()) {
            prefs.edit().remove(KEY_LAST_UID).apply()
        } else {
            prefs.edit().putString(KEY_LAST_UID, uid).apply()
        }
    }

    fun getLastSyncedUid(context: Context): String? {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_SYNCED_UID, null)
    }

    fun setLastSyncedUid(context: Context, uid: String?) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (uid.isNullOrBlank()) {
            prefs.edit().remove(KEY_LAST_SYNCED_UID).apply()
        } else {
            prefs.edit().putString(KEY_LAST_SYNCED_UID, uid).apply()
        }
    }

    fun getLastSyncedTimestamp(context: Context): Long {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_SYNCED_TIMESTAMP, 0L)
    }

    fun setLastSyncedTimestamp(context: Context, timestamp: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_SYNCED_TIMESTAMP, timestamp)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
