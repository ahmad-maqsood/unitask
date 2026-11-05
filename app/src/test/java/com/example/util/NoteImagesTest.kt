package com.example.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.Note
import com.example.util.markdown.EditorText
import com.example.util.markdown.insertImageLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NoteImagesTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.getDatabase(context)
        runBlocking(Dispatchers.IO) { db.clearAllTables() }
    }

    @After
    fun tearDown() {
        db.close()
        AppDatabase::class.java.getDeclaredField("INSTANCE")
            .apply { isAccessible = true }
            .set(null, null)
        val dir = File(context.filesDir, "note_images")
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }

    // -------------------------------------------------------------------------
    // isValidImageName tests
    // -------------------------------------------------------------------------

    @Test
    fun isValidImageNameAcceptsValidNames() {
        assertTrue(NoteImages.isValidImageName("12345678.jpg"))
        assertTrue(NoteImages.isValidImageName("my_image-12.jpg"))
        val uuidName = UUID.randomUUID().toString() + ".jpg"
        assertTrue(NoteImages.isValidImageName(uuidName))
        val maxLenName = "a".repeat(64) + ".jpg"
        assertTrue(NoteImages.isValidImageName(maxLenName))
    }

    @Test
    fun isValidImageNameRejectsInvalidNames() {
        assertFalse(NoteImages.isValidImageName("../a.jpg"))
        assertFalse(NoteImages.isValidImageName("a/b.jpg"))
        assertFalse(NoteImages.isValidImageName("x.png"))
        assertFalse(NoteImages.isValidImageName(""))
        assertFalse(NoteImages.isValidImageName("1234567.jpg")) // 7 chars: too short (< 8)
        assertFalse(NoteImages.isValidImageName("a".repeat(65) + ".jpg")) // 65 chars: too long (> 64)
        assertFalse(NoteImages.isValidImageName("image.jpeg"))
        assertFalse(NoteImages.isValidImageName("invalid@char.jpg"))
    }

    // -------------------------------------------------------------------------
    // computeSampleSize tests
    // -------------------------------------------------------------------------

    @Test
    fun computeSampleSizeHandlesSmallSizes() {
        // Image longest edge smaller than maxEdge (1600) returns 1
        assertEquals(1, NoteImages.computeSampleSize(800, 600, 1600))
        assertEquals(1, NoteImages.computeSampleSize(500, 1500, 1600))
    }

    @Test
    fun computeSampleSizeHandlesLargeSizes() {
        // Longest edge 4000: 4000 / 2 = 2000 >= 1600, 4000 / 4 = 1000 < 1600 -> sampleSize 2
        assertEquals(2, NoteImages.computeSampleSize(4000, 3000, 1600))
        // Longest edge 8000: 8000 / 4 = 2000 >= 1600, 8000 / 8 = 1000 < 1600 -> sampleSize 4
        assertEquals(4, NoteImages.computeSampleSize(8000, 6000, 1600))
    }

    @Test
    fun computeSampleSizeHandlesExactMultiples() {
        // 3200 / 2 = 1600 >= 1600 -> sampleSize 2
        assertEquals(2, NoteImages.computeSampleSize(3200, 1000, 1600))
        // 1600 / 2 = 800 < 1600 -> sampleSize 1
        assertEquals(1, NoteImages.computeSampleSize(1600, 1000, 1600))
    }

    @Test
    fun computeSampleSizeHandlesZeroAndNegativeSizes() {
        assertEquals(1, NoteImages.computeSampleSize(0, 0, 1600))
        assertEquals(1, NoteImages.computeSampleSize(-10, 1000, 1600))
        assertEquals(1, NoteImages.computeSampleSize(1000, -5, 1600))
        assertEquals(1, NoteImages.computeSampleSize(1000, 1000, 0))
        assertEquals(1, NoteImages.computeSampleSize(1000, 1000, -100))
    }

    // -------------------------------------------------------------------------
    // countImageReferences tests
    // -------------------------------------------------------------------------

    @Test
    fun countImageReferencesReturnsZeroWhenNone() {
        assertEquals(0, NoteImages.countImageReferences(""))
        assertEquals(0, NoteImages.countImageReferences("Just plain text with no pictures."))
    }

    @Test
    fun countImageReferencesCountsSeveralReferences() {
        val body = """
            Here is photo 1: ![](unitask-image:photo111.jpg)
            Here is photo 2: ![](unitask-image:photo222.jpg)
        """.trimIndent()
        assertEquals(2, NoteImages.countImageReferences(body))
    }

    @Test
    fun countImageReferencesCountsDuplicates() {
        val body = "![](unitask-image:photo111.jpg) and again ![](unitask-image:photo111.jpg)"
        assertEquals(2, NoteImages.countImageReferences(body))
    }

    @Test
    fun countImageReferencesIgnoresOrdinaryImages() {
        val body = """
            ![](https://example.com/pic.png)
            ![Alt](http://mysite.org/image.jpg)
            ![](unitask-image:local123.jpg)
        """.trimIndent()
        assertEquals(1, NoteImages.countImageReferences(body))
    }

    // -------------------------------------------------------------------------
    // insertImageLine tests
    // -------------------------------------------------------------------------

    @Test
    fun insertImageLineMidLineAddsNewlinesBeforeAndAfter() {
        val t = EditorText("Hello World", 5, 5)
        val res = insertImageLine(t, "![](unitask-image:img12345.jpg)")
        assertEquals("Hello\n![](unitask-image:img12345.jpg)\n World", res.text)
        assertEquals(38, res.selStart)
        assertEquals(38, res.selEnd)
    }

    @Test
    fun insertImageLineStartOfLineDoesNotAddLeadingNewline() {
        val t = EditorText("Line 1\nLine 2", 7, 7) // Cursor at start of "Line 2"
        val res = insertImageLine(t, "![](unitask-image:img12345.jpg)")
        assertEquals("Line 1\n![](unitask-image:img12345.jpg)\nLine 2", res.text)
        assertEquals(39, res.selStart)
        assertEquals(39, res.selEnd)
    }

    @Test
    fun insertImageLineStartOfTextDoesNotAddLeadingNewline() {
        val t = EditorText("Hello", 0, 0)
        val res = insertImageLine(t, "![](unitask-image:img12345.jpg)")
        assertEquals("![](unitask-image:img12345.jpg)\nHello", res.text)
        assertEquals(32, res.selStart)
    }

    @Test
    fun insertImageLineEndOfTextAddsLeadingAndTrailingNewline() {
        val t = EditorText("Hello", 5, 5)
        val res = insertImageLine(t, "![](unitask-image:img12345.jpg)")
        assertEquals("Hello\n![](unitask-image:img12345.jpg)\n", res.text)
        assertEquals(38, res.selStart)
    }

    // -------------------------------------------------------------------------
    // cleanupOrphans tests
    // -------------------------------------------------------------------------

    @Test
    fun cleanupOrphansKeepsReferencedAndTrashedFilesAndDeletesOthers() = runBlocking {
        val imgDir = File(context.filesDir, "note_images").apply { mkdirs() }

        val activeImg = "active123.jpg"
        val trashedImg = "trashed12.jpg"
        val orphanImg = "orphan123.jpg"

        File(imgDir, activeImg).writeText("fake jpg 1")
        File(imgDir, trashedImg).writeText("fake jpg 2")
        File(imgDir, orphanImg).writeText("fake jpg 3")
        // Make the orphan look old; files younger than 60 seconds are protected
        File(imgDir, orphanImg).setLastModified(System.currentTimeMillis() - 120_000L)
        // Active note references activeImg
        
        db.noteDao().insert(
            Note(
                id = 1L,
                title = "Active Note",
                body = "Look at this: ![](unitask-image:$activeImg)",
                isDeleted = false
            )
        )

        // Trashed note references trashedImg
        db.noteDao().insert(
            Note(
                id = 2L,
                title = "Trashed Note",
                body = "Deleted note picture: ![](unitask-image:$trashedImg)",
                isDeleted = true,
                deletedAt = System.currentTimeMillis()
            )
        )

        val deletedCount = NoteImages.cleanupOrphans(context, db.noteDao())
        assertEquals(1, deletedCount)

        assertTrue("Active image must remain", File(imgDir, activeImg).exists())
        assertTrue("Trashed note image must remain", File(imgDir, trashedImg).exists())
        assertFalse("Orphan image must be deleted", File(imgDir, orphanImg).exists())
    }
}

