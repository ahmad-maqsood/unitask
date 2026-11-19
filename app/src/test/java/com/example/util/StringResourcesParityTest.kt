package com.example.util

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Keeps the English and Urdu string files in step, so no text is missing in one language. */
class StringResourcesParityTest {

    private fun resFile(dir: String): File {
        val candidates = listOf(
            File("src/main/res/$dir/strings.xml"),
            File("app/src/main/res/$dir/strings.xml")
        )
        return candidates.first { it.exists() }
    }

    private fun names(dir: String): List<String> =
        Regex("""<(?:string|plurals)\s+name="([^"]+)"""").findAll(resFile(dir).readText()).map { it.groupValues[1] }.toList()

    @Test
    fun noDuplicateNames() {
        for (dir in listOf("values", "values-ur")) {
            val all = names(dir)
            assertEquals("Duplicate names in $dir", all.size, all.toSet().size)
        }
    }

    @Test
    fun englishAndUrduHaveTheSameNames() {
        val en = names("values").toSet()
        val ur = names("values-ur").toSet()
        assertTrue("Missing in values-ur: ${en - ur}", (en - ur).isEmpty())
        assertTrue("Missing in values: ${ur - en}", (ur - en).isEmpty())
    }
}
