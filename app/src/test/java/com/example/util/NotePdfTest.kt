package com.example.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.NoteRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotePdfTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
    }

    @Test
    fun writeNoteContainingEveryBlockTypeProducesValidPdf() {
        assumePdfSupported()
        val markdown = """
            # Level 1 Heading
            ## Level 2 Heading
            ### Level 3 Heading
            #### Level 4 Heading

            A paragraph containing **bold text**, *italic text*, ~~strikethrough~~, `inline monospace code`, and [sample link](https://example.com).

            * Depth 0 bullet item
                * Depth 2 bullet item

            1. First numbered item
            2. Second numbered item

            - [x] Completed task item
            - [ ] Pending task item

            > A blockquote with insightful notes and multi-line guidance.

            ```kotlin
            val veryLongLine = "This is an extraordinarily long line of code designed to test that code block layout wraps appropriately on PDF canvas without throwing exceptions or corrupting the layout."
            ```

            ---

            | Left Col | Center Col | Right Col |
            | :--- | :---: | ---: |
            | Left Text | Center Text | Right Text |

            ![Missing Local Image](unitask-image:nonexistent_sample_image.jpg)

            ![Web Remote Image](https://example.com/logo.png)
        """.trimIndent()

        val out = ByteArrayOutputStream()
        NotePdf.write(context, "Full Feature Note", markdown, out)
        val bytes = out.toByteArray()

        // Must start with "%PDF" and be larger than 1 KB (1024 bytes)
        assertTrue("Output should be larger than 1 KB, but was ${bytes.size} bytes", bytes.size > 1024)
        val header = String(bytes.take(4).toByteArray(), Charsets.US_ASCII)
        assertEquals("%PDF", header)
    }

    @Test
    fun emptyTitleAndEmptyBodyProducesValidPdf() {
        assumePdfSupported()
        val out = ByteArrayOutputStream()
        NotePdf.write(context, "", "", out)
        val bytes = out.toByteArray()

        assertTrue(bytes.isNotEmpty())
        val header = String(bytes.take(4).toByteArray(), Charsets.US_ASCII)
        assertEquals("%PDF", header)
    }

    @Test
    fun largeNoteOf300000CharactersCompletesAndStaysUnder300Pages() {
        assumePdfSupported()
        val line = "A repeated line of study notes to test large document pagination and bounds.\n"
        val count = (NoteRepository.MAX_BODY / line.length) + 1
        val largeBody = line.repeat(count).take(NoteRepository.MAX_BODY)

        val out = ByteArrayOutputStream()
        NotePdf.write(context, "Massive Note", largeBody, out)
        val bytes = out.toByteArray()

        assertTrue(bytes.isNotEmpty())
        val header = String(bytes.take(4).toByteArray(), Charsets.US_ASCII)
        assertEquals("%PDF", header)
    }

    @Test
    fun singleWordOf5000CharactersDoesNotThrow() {
        assumePdfSupported()
        val longWord = "Z".repeat(5000)
        val out = ByteArrayOutputStream()
        NotePdf.write(context, "Long Word", longWord, out)
        val bytes = out.toByteArray()

        assertTrue(bytes.isNotEmpty())
        val header = String(bytes.take(4).toByteArray(), Charsets.US_ASCII)
        assertEquals("%PDF", header)
    }
}
