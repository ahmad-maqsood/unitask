package com.example.util

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLanguageTest {
    @Test
    fun knownTagsMapToTheirChoice() {
        assertEquals(AppLanguage.URDU, AppLanguage.fromTag("ur"))
        assertEquals(AppLanguage.URDU, AppLanguage.fromTag("ur-PK"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en_US"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("EN"))
    }

    @Test
    fun unknownOrEmptyTagMeansFollowThePhone() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag(""))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag("fr"))
    }

    @Test
    fun localesConfigListsEveryChoice() {
        val config = File("src/main/res/xml/locales_config.xml").readText()
        AppLanguage.entries.filter { it != AppLanguage.SYSTEM }.forEach {
            assertTrue("locales_config.xml is missing ${it.tag}", config.contains("android:name=\"${it.tag}\""))
            assertTrue("no values-${it.tag} folder", it == AppLanguage.ENGLISH || File("src/main/res/values-${it.tag}/strings.xml").exists())
        }
    }
}
