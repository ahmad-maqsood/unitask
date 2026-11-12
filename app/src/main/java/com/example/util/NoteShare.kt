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
    MD("Markdown file (.md)", "md", "text/markdown"),
    PDF("PDF document (.pdf)", "pdf", "application/pdf")
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
            NoteExportFormat.PDF -> {
                throw IllegalArgumentException("PDF is not a plain text format")
            }
        }
    }

    /**
     * Joins several notes into one Markdown text. Each note becomes a "##" section with its
     * title, and sections are separated by a horizontal rule. Pairs are (title, body).
     */
    fun combinedMarkdown(notes: List<Pair<String, String>>): String {
        return notes.joinToString("\n\n---\n\n") { (title, body) ->
            val heading = "## ${title.trim().ifBlank { "Untitled note" }}"
            if (body.isBlank()) heading else "$heading\n\n$body"
        }
    }

    /** One note going into a folder export. fileTitle names the file; title and body are the content. */
    data class FolderExportNote(val fileTitle: String, val title: String, val body: String)

    /**
     * Picks a file name for every note: the safe title plus ".md", with " (2)", " (3)" and so on
     * added when two notes would share a name (ignoring capitals, as some phones do).
     */
    fun uniqueMarkdownNames(fileTitles: List<String>): List<String> {
        val used = HashSet<String>()
        return fileTitles.map { fileTitle ->
            val base = safeFileName(fileTitle)
            var candidate = "$base.md"
            var counter = 2
            while (!used.add(candidate.lowercase())) {
                candidate = "$base ($counter).md"
                counter++
            }
            candidate
        }
    }

    /** Writes every note as its own Markdown file inside one zip. Does not close the stream. */
    fun writeFolderZip(notes: List<FolderExportNote>, out: java.io.OutputStream) {
        val names = uniqueMarkdownNames(notes.map { it.fileTitle })
        val zip = java.util.zip.ZipOutputStream(out)
        notes.forEachIndexed { index, note ->
            zip.putNextEntry(java.util.zip.ZipEntry(names[index]))
            zip.write(exportText(note.title, note.body, NoteExportFormat.MD).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        zip.finish()
        zip.flush()
    }

    /** Builds a zip of the folder's notes in the cache and opens the share sheet. Returns false when it could not. */
    suspend fun shareFolderZip(
        context: Context,
        folderName: String,
        notes: List<FolderExportNote>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val exportsDir = File(context.cacheDir, "exports")
            if (!exportsDir.exists()) exportsDir.mkdirs() else exportsDir.listFiles()?.forEach { it.delete() }
            val file = File(exportsDir, folderZipFileName(folderName))
            file.outputStream().use { writeFolderZip(notes, it) }

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                clipData = ClipData.newRawUri("", uri)
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, folderName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(sendIntent, "Share folder")
            if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            withContext(Dispatchers.Main) {
                try {
                    context.startActivity(chooser)
                } catch (_: Exception) {
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Writes the folder's zip to a file the person chose (Save to device). */
    suspend fun saveFolderZipToUri(
        context: Context,
        uri: Uri,
        notes: List<FolderExportNote>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val stream = context.contentResolver.openOutputStream(uri, "wt") ?: return@withContext false
            stream.use { writeFolderZip(notes, it) }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun folderZipFileName(folderName: String): String = "${safeFileName(folderName)}.zip"

    fun fileNameFor(title: String, format: NoteExportFormat): String =
        "${safeFileName(title)}.${format.extension}"

    suspend fun shareAsFileAsync(
        context: Context,
        title: String,
        body: String,
        format: NoteExportFormat
    ) = withContext(Dispatchers.IO) {
        val exportsDir = File(context.cacheDir, "exports")
        if (!exportsDir.exists()) {
            exportsDir.mkdirs()
        } else {
            exportsDir.listFiles()?.forEach { it.delete() }
        }

        val fileName = fileNameFor(title, format)
        val file = File(exportsDir, fileName)
        if (format == NoteExportFormat.PDF) {
            file.outputStream().use { os ->
                NotePdf.write(context, title, body, os)
                os.flush()
            }
        } else {
            val content = exportText(title, body, format)
            file.writeText(content, Charsets.UTF_8)
        }

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
        withContext(Dispatchers.Main) {
            try {
                context.startActivity(chooser)
            } catch (_: Exception) {
            }
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
                NoteExportFormat.PDF -> {
                    val outputStream = context.contentResolver.openOutputStream(uri, "wt") ?: return@withContext false
                    outputStream.use { os ->
                        NotePdf.write(context, title, body, os)
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
