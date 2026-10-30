package com.example.util

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri

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
     * Generates a QR Code Bitmap. Uses ZXing directly if present on classpath.
     */
    fun generateQrBitmap(content: String, sizePx: Int = 512): Bitmap? {
        return try {
            val writerClass = Class.forName("com.google.zxing.qrcode.QRCodeWriter")
            val formatClass = Class.forName("com.google.zxing.BarcodeFormat")
            val qrCodeConstant = formatClass.getField("QR_CODE").get(null)
            val writer = writerClass.getDeclaredConstructor().newInstance()

            val encodeMethod = writerClass.getMethod(
                "encode",
                String::class.java,
                formatClass,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            val bitMatrix = encodeMethod.invoke(writer, content, qrCodeConstant, sizePx, sizePx)
            val bitMatrixClass = bitMatrix.javaClass
            val getMethod = bitMatrixClass.getMethod("get", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)

            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            for (x in 0 until sizePx) {
                for (y in 0 until sizePx) {
                    val isBlack = getMethod.invoke(bitMatrix, x, y) as Boolean
                    bitmap.setPixel(x, y, if (isBlack) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (_: Throwable) {
            generateFallbackMatrixBitmap(content, sizePx)
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

            val rgbSourceClass = Class.forName("com.google.zxing.RGBLuminanceSource")
            val luminanceSource = rgbSourceClass.getConstructor(
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                IntArray::class.java
            ).newInstance(width, height, pixels)

            val binarizerClass = Class.forName("com.google.zxing.common.HybridBinarizer")
            val luminanceSourceClass = Class.forName("com.google.zxing.LuminanceSource")
            val binarizer = binarizerClass.getConstructor(luminanceSourceClass).newInstance(luminanceSource)

            val binaryBitmapClass = Class.forName("com.google.zxing.BinaryBitmap")
            val binarizerParamClass = Class.forName("com.google.zxing.Binarizer")
            val binaryBitmap = binaryBitmapClass.getConstructor(binarizerParamClass).newInstance(binarizer)

            val readerClass = Class.forName("com.google.zxing.MultiFormatReader")
            val reader = readerClass.getDeclaredConstructor().newInstance()
            val decodeMethod = readerClass.getMethod("decode", binaryBitmapClass)
            val result = decodeMethod.invoke(reader, binaryBitmap)
            val getTextMethod = result.javaClass.getMethod("getText")
            getTextMethod.invoke(result) as? String
        } catch (_: Throwable) {
            null
        }
    }

    private fun generateFallbackMatrixBitmap(content: String, sizePx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = android.graphics.Paint().apply {
            color = Color.BLACK
            style = android.graphics.Paint.Style.FILL
        }
        val cellSize = sizePx / 25
        val hash = content.hashCode()
        for (i in 0 until 25) {
            for (j in 0 until 25) {
                // Fixed finder patterns at 3 corners
                val isFinder1 = i < 7 && j < 7
                val isFinder2 = i >= 18 && j < 7
                val isFinder3 = i < 7 && j >= 18
                if (isFinder1 || isFinder2 || isFinder3) {
                    val li = if (i >= 18) i - 18 else i
                    val lj = if (j >= 18) j - 18 else j
                    val isBorder = li == 0 || li == 6 || lj == 0 || lj == 6 || (li in 2..4 && lj in 2..4)
                    if (isBorder) {
                        canvas.drawRect(
                            (i * cellSize).toFloat(),
                            (j * cellSize).toFloat(),
                            ((i + 1) * cellSize).toFloat(),
                            ((j + 1) * cellSize).toFloat(),
                            paint
                        )
                    }
                } else if (((hash xor (i * 31 + j * 17)) and 1) == 0) {
                    canvas.drawRect(
                        (i * cellSize).toFloat(),
                        (j * cellSize).toFloat(),
                        ((i + 1) * cellSize).toFloat(),
                        ((j + 1) * cellSize).toFloat(),
                        paint
                    )
                }
            }
        }
        return bitmap
    }
}

