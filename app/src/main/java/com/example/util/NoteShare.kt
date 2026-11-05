package com.example.util

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object NoteShare {

    fun shareAsText(context: Context, title: String, body: String) {
        val text = if (title.isNotBlank()) {
            if (body.isNotBlank()) "$title\n\n$body" else title
        } else {
            body
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(intent, "Share note")
        if (context !is Activity) {
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(chooser)
        } catch (_: Exception) {
        }
    }

    fun shareAsMarkdownFile(context: Context, title: String, body: String) {
        val content = if (title.isNotBlank()) {
            if (body.isNotBlank()) "# $title\n\n$body" else "# $title"
        } else {
            body
        }

        val exportsDir = File(context.cacheDir, "exports")
        if (!exportsDir.exists()) {
            exportsDir.mkdirs()
        } else {
            exportsDir.listFiles()?.forEach { it.delete() }
        }

        val safeName = safeFileName(title)
        val file = File(exportsDir, "$safeName.md")
        file.writeText(content, Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/markdown"
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

    fun safeFileName(title: String): String {
        val replaced = StringBuilder()
        for (c in title) {
            if (c.isLetter() || c.isDigit() || c == ' ' || c == '-' || c == '_') {
                replaced.append(c)
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

