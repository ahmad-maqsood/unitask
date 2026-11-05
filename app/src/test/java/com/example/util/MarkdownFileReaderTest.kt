package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownFileReaderTest {

    @Test
    fun readBytesWithPlainUtf8ReturnsUnchangedText() {
        val input = "# Title\n\nSome normal markdown content with *italic* and **bold**."
        val bytes = input.toByteArray(Charsets.UTF_8)
        val result = MarkdownFileReader.readBytes(bytes)

        assertTrue(result is MarkdownReadResult.Success)
        assertEquals(input, (result as MarkdownReadResult.Success).text)
    }

    @Test
    fun readBytesWithUtf8BomRemovesBom() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val content = "Hello world".toByteArray(Charsets.UTF_8)
        val bytes = bom + content
        val result = MarkdownFileReader.readBytes(bytes)

        assertTrue(result is MarkdownReadResult.Success)
        assertEquals("Hello world", (result as MarkdownReadResult.Success).text)
    }

    @Test
    fun readBytesWithExactly1MbIsAcceptedAndOver1MbReturnsTooLarge() {
        val exactly1Mb = ByteArray(1_000_000) { 'a'.code.toByte() }
        val result1Mb = MarkdownFileReader.readBytes(exactly1Mb)
        assertTrue(result1Mb is MarkdownReadResult.Success)

        val over1Mb = ByteArray(1_000_001) { 'a'.code.toByte() }
        val resultOver = MarkdownFileReader.readBytes(over1Mb)
        assertEquals(MarkdownReadResult.TooLarge, resultOver)
    }

    @Test
    fun readBytesWithNulBytesReturnsUnreadable() {
        val bytes = byteArrayOf(0x61, 0x00, 0x62)
        val result = MarkdownFileReader.readBytes(bytes)

        assertTrue(result is MarkdownReadResult.Unreadable)
        assertEquals(MarkdownReadResult.Binary, result)
    }

    @Test
    fun readBytesWithEmptyInputReturnsSuccessWithEmptyString() {
        val bytes = ByteArray(0)
        val result = MarkdownFileReader.readBytes(bytes)

        assertTrue(result is MarkdownReadResult.Success)
        assertEquals("", (result as MarkdownReadResult.Success).text)
    }

    @Test
    fun looksLikeMarkdownNameIdentifiesExtensionsCorrectly() {
        assertTrue(MarkdownFileReader.looksLikeMarkdownName("a.md"))
        assertTrue(MarkdownFileReader.looksLikeMarkdownName("A.MD"))
        assertTrue(MarkdownFileReader.looksLikeMarkdownName("notes.markdown"))
        assertTrue(MarkdownFileReader.looksLikeMarkdownName("x.txt"))

        assertFalse(MarkdownFileReader.looksLikeMarkdownName("photo.png"))
        assertFalse(MarkdownFileReader.looksLikeMarkdownName("md"))
        assertFalse(MarkdownFileReader.looksLikeMarkdownName(""))
        assertFalse(MarkdownFileReader.looksLikeMarkdownName(null))
    }
}

