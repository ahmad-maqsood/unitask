package com.example

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NoPersonalCloudSyncTest {

    @Test
    fun noPersonalCloudSyncInMainSource() {
        val candidates = listOf(
            File("app/src/main/java"),
            File("src/main/java")
        )
        val sourceDir = candidates.firstOrNull { it.isDirectory }
            ?: throw IllegalStateException("Cannot find main source directory in ${candidates.map { it.absolutePath }}")

        val forbiddenTokens = listOf(
            "postgrest[\"courses\"]",
            "postgrest[\"tasks\"]",
            "postgrest[\"slots\"]",
            "postgrest[\"notes\"]",
            "from(\"courses\")",
            "from(\"tasks\")",
            "from(\"slots\")",
            "from(\"notes\")",
            "SupabaseSyncManager",
            "NoteSync"
        )

        val offending = mutableListOf<String>()

        sourceDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val relativePath = file.relativeTo(sourceDir).path.replace('\\', '/')
                if (relativePath.endsWith("util/SupabaseProvider.kt")) {
                    return@forEach
                }
                val text = file.readText()
                val found = forbiddenTokens.filter { token -> text.contains(token) }
                if (found.isNotEmpty()) {
                    offending.add("${file.path} contains: ${found.joinToString(", ")}")
                }
            }

        assertTrue(
            "Personal cloud sync references found in main source files:\n" + offending.joinToString("\n"),
            offending.isEmpty()
        )
    }
}
