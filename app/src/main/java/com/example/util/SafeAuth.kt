package com.example.util

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

/** Returns FirebaseAuth, or null if Firebase is not set up (instead of crashing). */
fun safeAuth(): FirebaseAuth? = try {
    Firebase.auth
} catch (_: Exception) {
    null
}