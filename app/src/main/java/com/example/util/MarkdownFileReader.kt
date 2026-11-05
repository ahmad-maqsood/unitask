package com.example.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Result of reading a Markdown file.
 */
sealed class MarkdownReadResult {
    data class Success(val text: String) : MarkdownReadResult()
    object TooLarge : MarkdownReadResult()
    open class Unreadable : MarkdownReadResult() {
        companion object : Unreadable()
    }
    object Binary : Unreadable()
}

/**
 * Helper to safely read Markdown and text files from external apps or local storage.
 * Enforces a 1 MB file size limit and detects binary files.
 */
object MarkdownFileReader {

    const val MAX_FILE_BYTES: Long = 1_000_000L

    /**
     * Checks if a file name looks like a Markdown or plain text document based on its extension.
     */
    fun looksLikeMarkdownName(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val lower = name.lowercase()
        return lower.endsWith(".md") ||
            lower.endsWith(".markdown") ||
            lower.endsWith(".mdown") ||
            lower.endsWith(".mkd") ||
            lower.endsWith(".txt")
    }

    /**
     * Reads a byte array into a MarkdownReadResult.
     * Pure function for testing without Android dependencies.
     */
    fun readBytes(bytes: ByteArray): MarkdownReadResult {
        if (bytes.size > MAX_FILE_BYTES) {
            return MarkdownReadResult.TooLarge
        }
        if (bytes.isEmpty()) {
            return MarkdownReadResult.Success("")
        }

        // Check for binary data by scanning for NUL bytes.
        val hasNul = bytes.any { it == 0.toByte() }
        if (hasNul) {
            return MarkdownReadResult.Binary
        }

        return try {
            val decoded = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
            MarkdownReadResult.Success(decoded)
        } catch (_: Exception) {
            MarkdownReadResult.Unreadable
        }
    }

    /**
     * Reads a file from a content or file URI up to MAX_FILE_BYTES.
     * Resolves the display name via OpenableColumns if available.
     */
    suspend fun readFromUri(context: Context, uri: Uri): Pair<String, MarkdownReadResult> =
        withContext(Dispatchers.IO) {
            var displayName = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null } ?: "note.txt"
            try {
                context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIdx != -1) {
                            val name = cursor.getString(nameIdx)
                            if (!name.isNullOrBlank()) {
                                displayName = name
                            }
                        }
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIdx != -1 && !cursor.isNull(sizeIdx)) {
                            val size = cursor.getLong(sizeIdx)
                            if (size > MAX_FILE_BYTES) {
                                return@withContext displayName to MarkdownReadResult.TooLarge
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }

            try {
                val stream = context.contentResolver.openInputStream(uri)
                    ?: return@withContext displayName to MarkdownReadResult.Unreadable

                stream.use { input ->
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_FILE_BYTES) {
                            return@withContext displayName to MarkdownReadResult.TooLarge
                        }
                        out.write(buffer, 0, read)
                    }
                    val result = readBytes(out.toByteArray())
                    displayName to result
                }
            } catch (_: Exception) {
                displayName to MarkdownReadResult.Unreadable
            }
        }
}

