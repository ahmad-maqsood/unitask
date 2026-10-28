package com.example.util

import com.example.BuildConfig
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.json.Json

object SupabaseProvider {
    val client by lazy {
        createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY) {
            defaultSerializer = KotlinXSerializer(Json {
                encodeDefaults = true
                ignoreUnknownKeys = true
            })
            install(Auth)
            install(Postgrest)
            install(Realtime)
        }
    }
}

fun currentSupabaseUid(): String? = try { SupabaseProvider.client.auth.currentUserOrNull()?.id } catch (_: Exception) { null }