package com.example.util

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build

/**
 * Which sound UniTask alerts use.
 * Android fixes a channel's sound when the channel is created, so each sound has its own channel id.
 *  - "default" (Sound 1) keeps the original channel id, so existing users are untouched.
 *  - "system" keeps the original "_system" id.
 * Built-in sounds are files in res/raw. A missing file is hidden from the list.
 */
object NotificationSound {
    private const val PREFS = "unitask_settings"
    private const val KEY_USE_SYSTEM = "use_system_sound" // old setting, only read for migration
    private const val KEY_CHOICE = "sound_choice"
    private const val KEY_PHONE_URI = "sound_phone_uri"

    const val CHOICE_DEFAULT = "default"
    const val CHOICE_SYSTEM = "system"
    const val CHOICE_PHONE = "phone"

    data class BuiltIn(val key: String, val label: String, val rawName: String)

    val BUILT_IN = listOf(
        BuiltIn(CHOICE_DEFAULT, "Sound 1 (UniTask)", "notification_sound"),
        BuiltIn("s2", "Sound 2", "sound_2"),
        BuiltIn("s3", "Sound 3", "sound_3"),
        BuiltIn("s4", "Sound 4", "sound_4")
    )

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun rawUri(context: Context, rawName: String): Uri? {
        val id = context.resources.getIdentifier(rawName, "raw", context.packageName)
        return if (id == 0) null else Uri.parse("android.resource://${context.packageName}/$id")
    }

    /** Built-in sounds whose file really exists. Sound 1 is always listed. */
    fun availableBuiltIn(context: Context): List<BuiltIn> =
        BUILT_IN.filter { it.key == CHOICE_DEFAULT || rawUri(context, it.rawName) != null }

    fun phoneUri(context: Context): Uri? =
        prefs(context).getString(KEY_PHONE_URI, null)?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }

    /** The saved choice. Old installs have no choice yet, so the old switch decides. */
    fun choice(context: Context): String {
        val p = prefs(context)
        val saved = p.getString(KEY_CHOICE, null)
        val choice = saved ?: if (p.getBoolean(KEY_USE_SYSTEM, false)) CHOICE_SYSTEM else CHOICE_SYSTEM
        if (choice == CHOICE_PHONE && phoneUri(context) == null) return CHOICE_SYSTEM
        if (BUILT_IN.any { it.key == choice } && choice != CHOICE_DEFAULT &&
            availableBuiltIn(context).none { it.key == choice }
        ) return CHOICE_SYSTEM
        return choice
    }

    fun setChoice(context: Context, key: String) {
        prefs(context).edit().putString(KEY_CHOICE, key).apply()
    }

    fun setPhoneSound(context: Context, uri: Uri) {
        prefs(context).edit()
            .putString(KEY_PHONE_URI, uri.toString())
            .putString(KEY_CHOICE, CHOICE_PHONE)
            .apply()
    }

    /** Short text for the Settings row. */
    fun label(context: Context): String = when (val c = choice(context)) {
        CHOICE_SYSTEM -> "Phone default"
        CHOICE_PHONE -> {
            val title = try {
                phoneUri(context)?.let { RingtoneManager.getRingtone(context, it)?.getTitle(context) }
            } catch (_: Exception) { null }
            "From phone: ${title ?: "custom sound"}"
        }
        else -> BUILT_IN.firstOrNull { it.key == c }?.label ?: BUILT_IN.first().label
    }

    /** Sound for the notification itself. null means "use the phone's default". */
    fun uri(context: Context): Uri? = when (val c = choice(context)) {
        CHOICE_SYSTEM -> null
        CHOICE_PHONE -> phoneUri(context)
        else -> BUILT_IN.firstOrNull { it.key == c }?.let { rawUri(context, it.rawName) }
    }

    /** Each sound gets its own channel id. Sound 1 and Phone default keep the ids from before. */
    fun channelId(context: Context, baseId: String): String = when (val c = choice(context)) {
        CHOICE_DEFAULT -> baseId
        CHOICE_SYSTEM -> baseId + "_system"
        CHOICE_PHONE -> baseId + "_phone_" + (phoneUri(context)?.toString()?.hashCode()?.let { Math.abs(it) } ?: 0)
        else -> baseId + "_" + c
    }

    /** Removes the channels of the sounds that are NOT in use, so the phone's settings stay clean. */
    fun deleteOtherChannels(context: Context, manager: NotificationManager, baseId: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val keep = channelId(context, baseId)
        try {
            manager.notificationChannels
                .map { it.id }
                .filter { (it == baseId || it.startsWith(baseId + "_")) && it != keep }
                .forEach { manager.deleteNotificationChannel(it) }
        } catch (_: Exception) {}
    }

    fun audioAttributes(): AudioAttributes =
        AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .build()
}