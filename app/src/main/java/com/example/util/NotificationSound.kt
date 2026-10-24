package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.net.Uri

/**
 * The custom notification sound is the file app/src/main/res/raw/notification_sound.(wav|mp3|ogg).
 * The user can switch to the phone's own notification sound in Settings.
 * When the system sound is chosen (or the file is missing), [uri] returns null and the phone default plays.
 */
object NotificationSound {
    private const val PREFS = "unitask_settings"
    private const val KEY_USE_SYSTEM = "use_system_sound"

    fun useSystem(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_USE_SYSTEM, false)

    fun setUseSystem(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_USE_SYSTEM, value)
            .apply()
    }

    /** Android fixes the sound when a channel is created, so each sound choice gets its own channel id. */
    fun channelId(context: Context, baseId: String): String =
        if (useSystem(context)) baseId + "_system" else baseId

    /** The channel of the sound that is NOT in use, so it can be removed from the phone's settings. */
    fun otherChannelId(context: Context, baseId: String): String =
        if (useSystem(context)) baseId else baseId + "_system"

    fun uri(context: Context): Uri? {
        if (useSystem(context)) return null
        val id = context.resources.getIdentifier("notification_sound", "raw", context.packageName)
        return if (id == 0) null else Uri.parse("android.resource://${context.packageName}/$id")
    }

    fun audioAttributes(): AudioAttributes =
        AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .build()
}