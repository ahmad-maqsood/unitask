package com.example.util

import android.graphics.pdf.PdfDocument
import org.junit.Assume.assumeTrue

/**
 * Robolectric cannot create real PDFs (the native part is missing), so tests that
 * generate a PDF skip themselves there instead of failing. Real PDF output is
 * checked on a phone or emulator.
 */
internal fun assumePdfSupported() {
    val supported = try {
        val doc = PdfDocument()
        try {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(100, 100, 1).create())
            doc.finishPage(page)
            true
        } finally {
            doc.close()
        }
    } catch (_: Exception) {
        false
    }
    assumeTrue("PdfDocument is not available in this test environment", supported)
}