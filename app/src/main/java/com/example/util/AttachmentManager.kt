package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class SavedAttachmentInfo(
    val fileName: String,
    val relativePath: String,
    val fileSize: Long,
    val mimeType: String
)

object AttachmentManager {

    private const val TAG = "AttachmentManager"
    private const val ATTACHMENTS_DIR = "attachments"

    suspend fun copyUriToInternalStorage(context: Context, uri: Uri): SavedAttachmentInfo? = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            var displayName = "attachment_${System.currentTimeMillis()}"
            var size = 0L

            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        displayName = cursor.getString(nameIndex) ?: displayName
                    }
                    if (sizeIndex != -1) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }

            val mimeType = contentResolver.getType(uri) ?: when {
                displayName.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
                displayName.endsWith(".png", ignoreCase = true) -> "image/png"
                displayName.endsWith(".jpg", ignoreCase = true) || displayName.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
                displayName.endsWith(".doc", ignoreCase = true) || displayName.endsWith(".docx", ignoreCase = true) -> "application/msword"
                displayName.endsWith(".txt", ignoreCase = true) -> "text/plain"
                else -> "*/*"
            }

            val dir = File(context.filesDir, ATTACHMENTS_DIR)
            if (!dir.exists()) {
                dir.mkdirs()
            }

            // Sanitize file name and make unique
            val cleanName = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val targetFileName = "${System.currentTimeMillis()}_$cleanName"
            val targetFile = File(dir, targetFileName)

            contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(targetFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            val finalSize = if (size > 0) size else targetFile.length()
            val relativePath = "$ATTACHMENTS_DIR/$targetFileName"

            SavedAttachmentInfo(
                fileName = displayName,
                relativePath = relativePath,
                fileSize = finalSize,
                mimeType = mimeType
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy attachment: ${e.message}", e)
            null
        }
    }

    fun openAttachment(context: Context, relativePath: String, mimeType: String) {
        try {
            val file = File(context.filesDir, relativePath)
            if (!file.exists()) {
                Toast.makeText(context, "Attachment file not found locally", Toast.LENGTH_SHORT).show()
                return
            }

            val authority = "${context.packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(context, authority, file)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Cannot open attachment: ${e.message}", e)
            Toast.makeText(context, "No app available to open this file", Toast.LENGTH_SHORT).show()
        }
    }

    suspend fun deleteAttachmentFile(context: Context, relativePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, relativePath)
            if (file.exists()) file.delete() else true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete file: ${e.message}", e)
            false
        }
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> "%.1f MB".format(bytes.toFloat() / (1024 * 1024))
            bytes >= 1024 -> "%d KB".format(bytes / 1024)
            else -> "$bytes B"
        }
    }
}
