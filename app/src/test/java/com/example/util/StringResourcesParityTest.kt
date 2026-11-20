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

    /** One placeholder such as %1$s or %d, as (position, type). Plain ones are numbered in order. */
    private fun placeholders(text: String): Set<Pair<Int, Char>> {
        var next = 1
        return Regex("""%(?:(\d+)\$)?([sd])""").findAll(text).map {
            val position = it.groupValues[1].toIntOrNull() ?: next++
            position to it.groupValues[2].single()
        }.toSet()
    }

    private fun texts(dir: String): Map<String, String> {
        val xml = resFile(dir).readText()
        val result = LinkedHashMap<String, String>()
        Regex("""<string\s+name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml).forEach { result[it.groupValues[1]] = it.groupValues[2] }
        // A plural counts as one text per quantity, named plural:quantity.
        Regex("""<plurals\s+name="([^"]+)"[^>]*>(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml).forEach { plural ->
                Regex("""<item\s+quantity="([^"]+)"[^>]*>(.*?)</item>""", RegexOption.DOT_MATCHES_ALL)
                    .findAll(plural.groupValues[2])
                    .forEach { result["${plural.groupValues[1]}:${it.groupValues[1]}"] = it.groupValues[2] }
            }
        return result
    }

    /**
     * An Urdu text may leave a placeholder out (for example an optional plural suffix) but must
     * never use one the English text does not have, or format() would crash on that screen.
     */
    @Test
    fun urduUsesOnlyPlaceholdersThatEnglishHas() {
        val en = texts("values")
        val problems = texts("values-ur").mapNotNull { (key, urdu) ->
            val english = en[key.substringBefore(':') ] ?: en[key] ?: return@mapNotNull null
            val allowed = placeholders(english)
            val extra = placeholders(urdu) - allowed
            if (extra.isEmpty()) null else "$key uses $extra but English has $allowed"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}
