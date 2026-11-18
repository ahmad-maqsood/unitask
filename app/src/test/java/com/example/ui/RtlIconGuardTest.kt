package com.example.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Right-to-left guard: icons that point left or right must be the auto-mirrored versions,
 * otherwise the arrow points the wrong way when the app is in Urdu.
 */
class RtlIconGuardTest {
    private val directional = listOf(
        "ArrowBack", "ArrowForward", "KeyboardArrowLeft", "KeyboardArrowRight",
        "ChevronLeft", "ChevronRight", "Sort", "ViewList", "FormatListBulleted", "ExitToApp"
    )

    @Test
    fun directionalIconsAreAutoMirrored() {
        val offenders = File("src/main/java").walkTopDown()
            .filter { it.extension == "kt" }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { i, line ->
                    val hit = directional.any { Regex("Icons\\.(Default|Filled)\\.$it\\b").containsMatchIn(line) }
                    if (hit) "${file.name}:${i + 1}" else null
                }
            }.toList()
        assertTrue("Use Icons.AutoMirrored.Filled for: $offenders", offenders.isEmpty())
    }

    @Test
    fun noAbsoluteLeftRightTextAlignOrPadding() {
        val offenders = File("src/main/java").walkTopDown()
            .filter { it.extension == "kt" }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { i, line ->
                    val hit = Regex("TextAlign\\.(Left|Right)\\b|Arrangement\\.(Left|Right)\\b|absolutePadding|absoluteOffset").containsMatchIn(line)
                    if (hit) "${file.name}:${i + 1}" else null
                }
            }.toList()
        assertTrue("Use Start/End instead of Left/Right: $offenders", offenders.isEmpty())
    }
}
