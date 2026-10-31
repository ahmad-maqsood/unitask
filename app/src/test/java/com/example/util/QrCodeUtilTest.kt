package com.example.util

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QrCodeUtilTest {

    @Test
    fun testBuildAndParseGroupInviteUri() {
        val uri = QrCodeUtil.buildGroupInviteUri("alice", "ABCD1234")
        assertTrue(uri.startsWith("unitask://join"))
        assertTrue(uri.contains("username=alice"))
        assertTrue(uri.contains("code=ABCD1234"))

        val parsed = QrCodeUtil.parseGroupInvite(uri)
        assertNotNull(parsed)
        assertEquals("alice", parsed?.first)
        assertEquals("ABCD1234", parsed?.second)
    }

    @Test
    fun testParseGroupInvite_fallbackRegex() {
        val text = "Join my group! user=bob code=XYZ9876"
        val parsed = QrCodeUtil.parseGroupInvite(text)
        assertNotNull(parsed)
        assertEquals("bob", parsed?.first)
        assertEquals("XYZ9876", parsed?.second)
    }

    @Test
    fun testGenerateQrBitmap_emptyContentReturnsNull() {
        val bitmap = QrCodeUtil.generateQrBitmap("", sizePx = 200)
        assertNull(bitmap)
    }

    @Test
    fun testGenerateAndDecodeQrBitmap_roundTrip() {
        val content = "unitask://join?username=charlie&code=WXYZ5678"
        val bitmap = QrCodeUtil.generateQrBitmap(content, sizePx = 300)
        assertNotNull(bitmap)
        assertEquals(300, bitmap?.width)
        assertEquals(300, bitmap?.height)

        val decoded = bitmap?.let { QrCodeUtil.decodeQrFromBitmap(it) }
        assertEquals(content, decoded)
    }

    @Test
    fun testDecodeQrFromBitmap_blankBitmapReturnsNull() {
        val blankBitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val decoded = QrCodeUtil.decodeQrFromBitmap(blankBitmap)
        assertNull(decoded)
    }
}

