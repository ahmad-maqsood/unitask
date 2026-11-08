package com.example.util

/**
 * Two spellings of one course code must become one course so tasks, slots, and notes
 * match reliably. Strips leading and trailing whitespace, removes all inner whitespace,
 * and converts characters to uppercase.
 */
fun normalizeCourseCode(code: String): String {
    return code.trim().replace("\\s+".toRegex(), "").uppercase()
}
