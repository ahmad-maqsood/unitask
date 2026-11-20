package com.example.util

import android.app.NotificationManager
import android.content.Context
import com.example.R
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import java.io.File
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

    data class BuiltIn(val key: String, @androidx.annotation.StringRes val labelRes: Int, val rawName: String)

    val BUILT_IN = listOf(
        BuiltIn(CHOICE_DEFAULT, R.string.sound_builtin_1, "sound_1"),
        BuiltIn("s2", R.string.sound_builtin_2, "sound_2"),
        BuiltIn("s3", R.string.sound_builtin_3, "sound_3"),
        BuiltIn("s4", R.string.sound_builtin_4, "sound_4")
    )

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun rawUri(context: Context, rawName: String): Uri? {
        val id = context.resources.getIdentifier(rawName, "raw", context.packageName)
        return if (id == 0) null else "android.resource://${context.packageName}/$id".toUri()
    }

    /** Built-in sounds whose file really exists. Sound 1 is always listed. */
    fun availableBuiltIn(context: Context): List<BuiltIn> =
        BUILT_IN.filter { it.key == CHOICE_DEFAULT || rawUri(context, it.rawName) != null }

    fun phoneUri(context: Context): Uri? =
        prefs(context).getString(KEY_PHONE_URI, null)?.takeIf { it.isNotBlank() }?.toUri()

    /**
     * Android can restore old settings from the Google backup when the app is reinstalled.
     * That would bring back an old "Sound 1" choice on a fresh install. So on the first start of a
     * genuinely fresh install (not an update) we clear any restored sound choice once.
     * The marker file lives in noBackupFilesDir, which is never backed up or restored,
     * so it is always missing on a fresh install.
     */
    @Suppress("ApplySharedPref")
    private fun ensureFreshInstallDefaults(context: Context) {
        try {
            val marker = File(context.noBackupFilesDir, "sound_defaults_checked")
            if (marker.exists()) return
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val isFreshInstall = info.firstInstallTime == info.lastUpdateTime
            if (isFreshInstall) {
                prefs(context).edit(commit = true) {
                    remove(KEY_CHOICE)
                    remove(KEY_PHONE_URI)
                    remove(KEY_USE_SYSTEM)
                }
            }
            marker.createNewFile()
        } catch (_: Exception) {
            // If this check fails we simply keep the saved value; nothing breaks.
        }
    }

    /** The saved choice. If nothing is saved (fresh install), "Phone default" is used. */
    fun choice(context: Context): String {
        ensureFreshInstallDefaults(context)
        val choice = prefs(context).getString(KEY_CHOICE, null) ?: CHOICE_SYSTEM
        if (choice == CHOICE_PHONE && phoneUri(context) == null) return CHOICE_SYSTEM
        if (BUILT_IN.any { it.key == choice } && choice != CHOICE_DEFAULT &&
            availableBuiltIn(context).none { it.key == choice }
        ) return CHOICE_SYSTEM
        return choice
    }

    fun setChoice(context: Context, key: String) {
        prefs(context).edit { putString(KEY_CHOICE, key) }
    }

    fun setPhoneSound(context: Context, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Exception) { Log.w("UniTask", "Could not keep access to the chosen sound file", e) }
        prefs(context).edit {
            putString(KEY_PHONE_URI, uri.toString())
            putString(KEY_CHOICE, CHOICE_PHONE)
        }
    }

    /** Short text for the Settings row. */
    fun label(context: Context): String = when (val c = choice(context)) {
        CHOICE_SYSTEM -> context.getString(R.string.sound_phone_default)
        CHOICE_PHONE -> {
            val title = try {
                phoneUri(context)?.let { RingtoneManager.getRingtone(context, it)?.getTitle(context) }
            } catch (_: Exception) { null }
            context.getString(R.string.sound_from_phone_format, title ?: context.getString(R.string.sound_custom_fallback))
        }
        else -> context.getString((BUILT_IN.firstOrNull { it.key == c } ?: BUILT_IN.first()).labelRes)
    }

    /** Sound for the notification itself. null means "use the phone's default". */
    fun uri(context: Context): Uri? = when (val c = choice(context)) {
        CHOICE_SYSTEM -> null
        CHOICE_PHONE -> {
            try {
                phoneUri(context)?.also {
                    context.contentResolver.openInputStream(it)?.close()
                }
            } catch (_: Throwable) {
                null
            }
        }
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
        } catch (e: Exception) { Log.w("UniTask", "Could not remove old notification channels", e) }
    }

    fun audioAttributes(): AudioAttributes =
        AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .build()
}