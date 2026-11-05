package com.example.util.markdown

/**
 * Checks whether a URL scheme is in the safe allow-list (http, https, mailto).
 * Any other scheme (file, content, intent, javascript, etc.) or relative text returns false.
 */
fun isSafeLinkUrl(url: String): Boolean {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return false
    val lower = trimmed.lowercase()
    return lower.startsWith("https://") ||
            lower.startsWith("http://") ||
            lower.startsWith("mailto:")
}

