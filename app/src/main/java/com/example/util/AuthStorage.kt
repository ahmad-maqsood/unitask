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
}
