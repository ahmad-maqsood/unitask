package com.example.util

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList

/** What the person picked for the app language. SYSTEM follows the phone. */
enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    ENGLISH("en"),
    URDU("ur");

    companion object {
        /** The choice for a stored language tag such as "ur" or "ur-PK". Anything unknown means SYSTEM. */
        fun fromTag(tag: String?): AppLanguage {
            val language = tag?.substringBefore('-')?.substringBefore('_')?.lowercase().orEmpty()
            return entries.firstOrNull { it != SYSTEM && it.tag == language } ?: SYSTEM
        }

        /** Android only lets an app change its own language from Android 13 (API 33). */
        val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

        fun current(context: Context): AppLanguage {
            if (!isSupported) return SYSTEM
            val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            return if (locales == null || locales.isEmpty) SYSTEM else fromTag(locales[0]?.toLanguageTag())
        }

        /** Applies the choice. Android restarts the screen by itself, so the new language shows at once. */
        fun apply(context: Context, language: AppLanguage) {
            if (!isSupported) return
            context.getSystemService(LocaleManager::class.java)?.applicationLocales =
                LocaleList.forLanguageTags(language.tag)
        }
    }
}
