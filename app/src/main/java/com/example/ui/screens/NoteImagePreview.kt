package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.util.NoteImages
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun NoteImagePreview(src: String, alt: String) {
    if (!src.startsWith(NoteImages.PREFIX)) {
        MarkdownImageFallback(src = src, alt = alt)
        return
    }

    val name = src.substring(NoteImages.PREFIX.length).trim()
    if (!NoteImages.isValidImageName(name)) {
        MarkdownImageFallback(src = src, alt = alt)
        return
    }

    val context = LocalContext.current
    val density = LocalDensity.current
    val screenWidthPx = remember(context, density) {
        context.resources.displayMetrics.widthPixels
    }

    val bitmapState = produceState<Bitmap?>(initialValue = null, key1 = name) {
        value = withContext(Dispatchers.IO) {
            try {
                val file = NoteImages.imageFile(context, name) ?: return@withContext null
                if (!file.exists()) return@withContext null

                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
                if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return@withContext null

                val targetWidth = if (screenWidthPx > 0) screenWidthPx else 1080
                val sampleSize = NoteImages.computeSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, targetWidth)
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
            } catch (_: Exception) {
                null
            }
        }
    }

    val bitmap = bitmapState.value
    var showFullScreen by remember { mutableStateOf(false) }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = alt.ifBlank { "Note image" },
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { showFullScreen = true }
                .testTag("note_preview_image")
        )

        if (showFullScreen) {
            Dialog(
                onDismissRequest = { showFullScreen = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .clickable { showFullScreen = false },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = alt.ifBlank { "Note image" },
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { showFullScreen = false }
                    )
                }
            }
        }
    } else {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .testTag("note_image_missing_placeholder")
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Image not available on this phone",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
