package com.example.util

import com.example.util.markdown.isSafeLinkUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownLinksTest {

    @Test
    fun httpsUrlIsSafe() {
        assertTrue(isSafeLinkUrl("https://example.com"))
        assertTrue(isSafeLinkUrl("https://unitask.app/notes?id=1#sec"))
    }

    @Test
    fun httpUrlIsSafe() {
        assertTrue(isSafeLinkUrl("http://example.com"))
        assertTrue(isSafeLinkUrl("http://localhost:8080/api"))
    }

    @Test
    fun mailtoUrlIsSafe() {
        assertTrue(isSafeLinkUrl("mailto:support@example.com"))
        assertTrue(isSafeLinkUrl("mailto:alice.bob@university.edu?subject=Notes"))
    }

    @Test
    fun upperCaseHttpsIsSafe() {
        assertTrue(isSafeLinkUrl("HTTPS://EXAMPLE.COM"))
        assertTrue(isSafeLinkUrl("Https://Example.Com/Page"))
    }

    @Test
    fun upperCaseHttpIsSafe() {
        assertTrue(isSafeLinkUrl("HTTP://EXAMPLE.COM"))
        assertTrue(isSafeLinkUrl("Http://Example.Com/Api"))
    }

    @Test
    fun upperCaseMailtoIsSafe() {
        assertTrue(isSafeLinkUrl("MAILTO:HELLO@EXAMPLE.COM"))
        assertTrue(isSafeLinkUrl("Mailto:Hello@Example.Com"))
    }

    @Test
    fun leadingAndTrailingSpacesTrimmed() {
        assertTrue(isSafeLinkUrl("   https://example.com"))
        assertTrue(isSafeLinkUrl("https://example.com   "))
        assertTrue(isSafeLinkUrl("   https://example.com   "))
        assertTrue(isSafeLinkUrl("\t\nhttp://example.com\n "))
    }

    @Test
    fun fileSchemeIsRejected() {
        assertFalse(isSafeLinkUrl("file:///android_asset/secret.txt"))
        assertFalse(isSafeLinkUrl("FILE:///sdcard/download.apk"))
    }

    @Test
    fun contentSchemeIsRejected() {
        assertFalse(isSafeLinkUrl("content://media/external/images/media/1"))
        assertFalse(isSafeLinkUrl("CONTENT://contacts/people"))
    }

    @Test
    fun intentSchemeIsRejected() {
        assertFalse(isSafeLinkUrl("intent:#Intent;action=android.intent.action.VIEW;end"))
        assertFalse(isSafeLinkUrl("INTENT://something"))
    }

    @Test
    fun javascriptSchemeIsRejected() {
        assertFalse(isSafeLinkUrl("javascript:alert(1)"))
        assertFalse(isSafeLinkUrl("JAVASCRIPT:void(0)"))
    }

    @Test
    fun emptyAndWhitespaceUrlsAreRejected() {
        assertFalse(isSafeLinkUrl(""))
        assertFalse(isSafeLinkUrl("   "))
        assertFalse(isSafeLinkUrl("\t\n"))
    }

    @Test
    fun relativeTextWithoutSchemeIsRejected() {
        assertFalse(isSafeLinkUrl("notes/my-note"))
        assertFalse(isSafeLinkUrl("index.html"))
        assertFalse(isSafeLinkUrl("www.example.com"))
        assertFalse(isSafeLinkUrl("ftp://ftp.example.com"))
        assertFalse(isSafeLinkUrl("data:text/html,hello"))
        assertFalse(isSafeLinkUrl("tel:+1234567890"))
    }
}

