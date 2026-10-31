package com.example.util

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object QrCodeUtil {

    private const val URI_SCHEME = "unitask"
    private const val URI_HOST = "join"

    /**
     * Builds a universal join link: unitask://join?username=...&code=...
     */
    fun buildGroupInviteUri(username: String, joinCode: String): String {
        return "$URI_SCHEME://$URI_HOST?username=${Uri.encode(username.trim())}&code=${Uri.encode(joinCode.trim())}"
    }

    /**
     * Parses username and joinCode from an invite link, QR text, or message.
     */
    fun parseGroupInvite(text: String): Pair<String, String>? {
        val trimmed = text.trim()
        try {
            val uri = Uri.parse(trimmed)
            if (uri.scheme.equals(URI_SCHEME, ignoreCase = true) || uri.host.equals("unitask.app", ignoreCase = true)) {
                val username = uri.getQueryParameter("username")
                val code = uri.getQueryParameter("code")
                if (!username.isNullOrBlank() && !code.isNullOrBlank()) {
                    return username.trim() to code.trim()
                }
            }
        } catch (_: Exception) {}

        // Fallback: search regex for username and code in arbitrary text
        val uRegex = Regex("""(?:username|user|u)[=:\s]+([a-zA-Z0-9_]{3,30})""", RegexOption.IGNORE_CASE)
        val cRegex = Regex("""(?:code|join_code|c)[=:\s]+([a-zA-Z0-9]{4,12})""", RegexOption.IGNORE_CASE)
        val uMatch = uRegex.find(trimmed)?.groupValues?.getOrNull(1)
        val cMatch = cRegex.find(trimmed)?.groupValues?.getOrNull(1)
        if (!uMatch.isNullOrBlank() && !cMatch.isNullOrBlank()) {
            return uMatch.trim() to cMatch.trim()
        }

        return null
    }

    /**
     * Generates a QR Code Bitmap directly using ZXing QRCodeWriter.
     */
    fun generateQrBitmap(content: String, sizePx: Int = 512): Bitmap? {
        if (content.isEmpty() || sizePx <= 0) return null
        return try {
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 1
            )
            val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            bitmap
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Decodes a QR code from a Bitmap using ZXing MultiFormatReader.
     */
    fun decodeQrFromBitmap(bitmap: Bitmap): String? {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val source = RGBLuminanceSource(width, height, pixels)
            val binarizer = HybridBinarizer(source)
            val binaryBitmap = BinaryBitmap(binarizer)
            val reader = MultiFormatReader()
            val result = reader.decode(binaryBitmap)
            result.text
        } catch (_: NotFoundException) {
            null
        } catch (_: Throwable) {
            null
        }
    }
}

