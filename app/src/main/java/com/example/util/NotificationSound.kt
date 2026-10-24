package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.net.Uri

/**
 * The custom notification sound is the file app/src/main/res/raw/notification_sound.(wav|mp3|ogg).
 * If that file is missing, [uri] returns null and notifications use the phone's default sound.
 */
object NotificationSound {

    fun uri(context: Context): Uri? {
        val id = context.resources.getIdentifier("notification_sound", "raw", context.packageName)
        return if (id == 0) null else Uri.parse("android.resource://${context.packageName}/$id")
    }

    fun audioAttributes(): AudioAttributes =
        AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .build()
}
