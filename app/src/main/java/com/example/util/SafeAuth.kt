package com.example.util

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** The signed-in person, as the screens need it. [uid] is the Supabase user id. */
data class AppUser(
    val uid: String,
    val email: String?,
    val displayName: String?
)

fun UserInfo.toAppUser(): AppUser = AppUser(
    uid = id,
    email = email,
    displayName = (userMetadata?.get("full_name") ?: userMetadata?.get("name"))
        ?.jsonPrimitive?.contentOrNull
)

/** The signed-in person right now, or null. May be null for a moment right after app start. */
fun currentAppUser(): AppUser? = try {
    SupabaseProvider.client.auth.currentUserOrNull()?.toAppUser()
} catch (_: Exception) {
    null
}

/** Like [currentAppUser], but first waits until the saved session has been read. For background workers. */
suspend fun awaitAppUser(): AppUser? = try {
    SupabaseProvider.client.auth.awaitInitialization()
    SupabaseProvider.client.auth.currentUserOrNull()?.toAppUser()
} catch (_: Exception) {
    null
}