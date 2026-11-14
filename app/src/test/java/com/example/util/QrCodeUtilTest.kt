package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

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
    fun testBuildAndParseGroupInviteWebLink() {
        val link = QrCodeUtil.buildGroupInviteWebLink("alice", "ABCD1234")
        assertTrue(link.startsWith("https://gen-lang-client-0196140574.firebaseapp.com/join/"))

        val parsed = QrCodeUtil.parseGroupInvite(link)
        assertNotNull(parsed)
        assertEquals("alice", parsed?.first)
        assertEquals("ABCD1234", parsed?.second)
    }

    @Test
    fun testParseGroupInvite_acceptsLinksMadeBeforeTheMove() {
        val parsed = QrCodeUtil.parseGroupInvite(
            "https://ahmad-maqsood.github.io/unitask/join/?username=alice&code=ABCD1234"
        )
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
    fun testParseGroupInvite_ordinaryTextIsNotAnInvite() {
        // "you there" ends in "u" and "music class" ends in "c": neither is a key.
        assertNull(QrCodeUtil.parseGroupInvite("thank you there, see you in music class tomorrow"))
        assertNull(QrCodeUtil.parseGroupInvite("https://example.com/menu"))
    }

    @Test
    fun testParseGroupInvite_shortKeysStillWork() {
        val parsed = QrCodeUtil.parseGroupInvite("u: bob c: XYZ9876")
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

    @Test
    fun testDecodeQrFromUri_roundTrip() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val content = "unitask://join?username=david&code=TEST1234"
        val bitmap = QrCodeUtil.generateQrBitmap(content, sizePx = 300)
        assertNotNull(bitmap)

        val tempFile = File(context.cacheDir, "test_qr_temp.png")
        FileOutputStream(tempFile).use { out ->
            bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        try {
            val uri = Uri.fromFile(tempFile)
            val decoded = QrCodeUtil.decodeQrFromUri(context, uri)
            assertEquals(content, decoded)
        } finally {
            tempFile.delete()
        }
    }
}

