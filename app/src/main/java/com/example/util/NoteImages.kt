package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.example.data.local.NoteDao
import com.example.util.markdown.referencedImageNames
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.roundToInt

object NoteImages {

    const val PREFIX = "unitask-image:"
    const val MAX_IMAGES_PER_NOTE = 10
    const val MAX_EDGE_PX = 1600
    const val JPEG_QUALITY = 80

    private val VALID_NAME_REGEX = Regex("""^[A-Za-z0-9_-]{8,64}\.jpg$""")

    fun isValidImageName(name: String): Boolean {
        return name.matches(VALID_NAME_REGEX)
    }

    fun imageFile(context: Context, name: String): File? {
        if (!isValidImageName(name)) return null
        val dir = File(context.filesDir, "note_images")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, name)
    }

    fun computeSampleSize(width: Int, height: Int, maxEdge: Int): Int {
        if (width <= 0 || height <= 0 || maxEdge <= 0) return 1
        val longest = maxOf(width, height)
        var sampleSize = 1
        while (longest / (sampleSize * 2) >= maxEdge) {
            sampleSize *= 2
        }
        return sampleSize
    }

    fun imageMarkdown(name: String): String = "![](unitask-image:" + name + ")"

    fun countImageReferences(body: String): Int {
        if (body.isEmpty()) return 0
        var count = 0
        var idx = 0
        while (true) {
            val found = body.indexOf(PREFIX, idx)
            if (found == -1) break
            count++
            idx = found + PREFIX.length
        }
        return count
    }

    suspend fun importImage(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: return@withContext null

            if (options.outWidth <= 0 || options.outHeight <= 0) return@withContext null

            // Read EXIF orientation using InputStream
            var rotationDegrees = 0
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    val orientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                    rotationDegrees = when (orientation) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        else -> 0
                    }
                }
            } catch (_: Exception) {
            }

            val sampleSize = computeSampleSize(options.outWidth, options.outHeight, MAX_EDGE_PX)
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            val sampledBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return@withContext null

            val rotatedBitmap = if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                val rotated = Bitmap.createBitmap(
                    sampledBitmap, 0, 0,
                    sampledBitmap.width, sampledBitmap.height,
                    matrix, true
                )
                if (rotated != sampledBitmap) {
                    sampledBitmap.recycle()
                }
                rotated
            } else {
                sampledBitmap
            }

            val curLongest = maxOf(rotatedBitmap.width, rotatedBitmap.height)
            val finalBitmap = if (curLongest > MAX_EDGE_PX) {
                val scale = MAX_EDGE_PX.toFloat() / curLongest
                val targetWidth = (rotatedBitmap.width * scale).roundToInt().coerceAtLeast(1)
                val targetHeight = (rotatedBitmap.height * scale).roundToInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(rotatedBitmap, targetWidth, targetHeight, true)
                if (scaled != rotatedBitmap) {
                    rotatedBitmap.recycle()
                }
                scaled
            } else {
                rotatedBitmap
            }

            val fileName = UUID.randomUUID().toString() + ".jpg"
            val file = imageFile(context, fileName) ?: run {
                finalBitmap.recycle()
                return@withContext null
            }

            FileOutputStream(file).use { out ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            finalBitmap.recycle()
            fileName
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    suspend fun cleanupOrphans(context: Context, noteDao: NoteDao): Int = withContext(Dispatchers.IO) {
        try {
            val allNotes = noteDao.getAllNotesSync()
            val referencedNames = mutableSetOf<String>()
            for (note in allNotes) {
                referencedNames.addAll(referencedImageNames(note.body))
            }

            val dir = File(context.filesDir, "note_images")
            if (!dir.exists() || !dir.isDirectory) return@withContext 0

            var deletedCount = 0
            val files = dir.listFiles() ?: return@withContext 0
            for (file in files) {
                if (file.isFile && file.name !in referencedNames &&
                    file.lastModified() < System.currentTimeMillis() - 60_000L) {
                    if (file.delete()) {
                        deletedCount++
                    }
                }
            }
            deletedCount
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            0
        }
    }
}

