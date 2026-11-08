package com.example.util

/**
 * Returns true only when both user ids are non-blank and different.
 * This indicates that a different account has signed in and group state
 * (group caches and group alarms) should be reset. Personal data on the
 * phone is untouched.
 */
fun shouldResetGroupState(previousUid: String?, newUid: String?): Boolean {
    return !previousUid.isNullOrBlank() && !newUid.isNullOrBlank() && previousUid != newUid
}
