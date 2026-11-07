package com.example.util

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class NoteExportFormat(
    val label: String,
    val extension: String,
    val mimeType: String
) {
    TXT("Text file (.txt)", "txt", "text/plain"),
    MD("Markdown file (.md)", "md", "text/markdown")
}

object NoteShare {

    fun exportText(title: String, body: String, format: NoteExportFormat): String {
        return when (format) {
            NoteExportFormat.MD -> {
                if (title.isNotBlank()) {
                    if (body.isNotBlank()) "# $title\n\n$body" else "# $title"
                } else {
                    body
                }
            }
            NoteExportFormat.TXT -> {
                if (title.isNotBlank()) {
                    if (body.isNotBlank()) "$title\n\n$body" else title
                } else {
                    body
                }
            }
        }
    }

    fun fileNameFor(title: String, format: NoteExportFormat): String =
        "${safeFileName(title)}.${format.extension}"

    fun shareAsFile(context: Context, title: String, body: String, format: NoteExportFormat) {
        val content = exportText(title, body, format)

        val exportsDir = File(context.cacheDir, "exports")
        if (!exportsDir.exists()) {
            exportsDir.mkdirs()
        } else {
            exportsDir.listFiles()?.forEach { it.delete() }
        }

        val fileName = fileNameFor(title, format)
        val file = File(exportsDir, fileName)
        file.writeText(content, Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = format.mimeType
            clipData = ClipData.newRawUri("", uri)
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(sendIntent, "Share note")
        if (context !is Activity) {
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(chooser)
        } catch (_: Exception) {
        }
    }

    suspend fun saveToUri(
        context: Context,
        uri: Uri,
        title: String,
        body: String,
        format: NoteExportFormat
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            when (format) {
                NoteExportFormat.TXT, NoteExportFormat.MD -> {
                    val text = exportText(title, body, format)
                    val outputStream = context.contentResolver.openOutputStream(uri, "wt") ?: return@withContext false
                    outputStream.use { os ->
                        os.write(text.toByteArray(Charsets.UTF_8))
                        os.flush()
                    }
                    true
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    fun safeFileName(title: String): String {
        val replaced = StringBuilder()
        for (c in title) {
            if (c.isLetter() || c.isDigit() || c == '-' || c == '_') {
                replaced.append(c)
            } else if (c.isWhitespace()) {
                replaced.append(' ')
            } else {
                replaced.append('_')
            }
        }
        val trimmed = replaced.toString().trim()
        val limited = trimmed.take(60).trim()
        return limited.ifEmpty { "note" }
    }

    fun noteFromImportedText(fileName: String, text: String): Pair<String, String> {
        val lines = text.lines()
        val firstNonBlankIndex = lines.indexOfFirst { it.isNotBlank() }

        if (firstNonBlankIndex != -1) {
            val firstLine = lines[firstNonBlankIndex].trim()
            if (firstLine.startsWith("# ")) {
                val headingTitle = firstLine.substring(2).trim()
                val remainingLines = lines.drop(firstNonBlankIndex + 1)
                val bodyLines = remainingLines.dropWhile { it.isBlank() }
                return headingTitle to bodyLines.joinToString("\n")
            }
        }

        val baseName = if (fileName.contains('.')) {
            fileName.substringBeforeLast('.')
        } else {
            fileName
        }
        return baseName to text
    }
}
