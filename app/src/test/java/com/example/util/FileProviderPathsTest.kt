package com.example.util

import android.content.Context
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FileProviderPathsTest {

    private lateinit var context: Context
    private val authority get() = "${context.packageName}.fileprovider"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
    }

    @After
    fun tearDown() {
        File(context.cacheDir, "exports").deleteRecursively()
        File(context.filesDir, "attachments").deleteRecursively()
        File(context.filesDir, "databases").deleteRecursively()
        File(context.cacheDir, "c.txt").delete()
    }

    @Test
    fun getUriForFileAllowsConfiguredPathsAndRejectsOthers() {
        // Allowed: cacheDir/exports/a.md
        val exportsDir = File(context.cacheDir, "exports")
        exportsDir.mkdirs()
        val exportFile = File(exportsDir, "a.md")
        exportFile.writeText("test markdown")

        val exportUri = FileProvider.getUriForFile(context, authority, exportFile)
        assertNotNull(exportUri)
        assertTrue(exportUri.toString().startsWith("content://$authority/exports/"))

        // Allowed: filesDir/attachments/b.pdf
        val attachmentsDir = File(context.filesDir, "attachments")
        attachmentsDir.mkdirs()
        val attachmentFile = File(attachmentsDir, "b.pdf")
        attachmentFile.writeText("test pdf")

        val attachmentUri = FileProvider.getUriForFile(context, authority, attachmentFile)
        assertNotNull(attachmentUri)
        assertTrue(attachmentUri.toString().startsWith("content://$authority/attachments/"))

        // Rejected: filesDir/databases/x.db
        val databasesDir = File(context.filesDir, "databases")
        databasesDir.mkdirs()
        val dbFile = File(databasesDir, "x.db")
        dbFile.writeText("dummy database")

        assertThrows(IllegalArgumentException::class.java) {
            FileProvider.getUriForFile(context, authority, dbFile)
        }

        // Rejected: cacheDir/c.txt in the cache root
        val cacheRootFile = File(context.cacheDir, "c.txt")
        cacheRootFile.writeText("cache root file")

        assertThrows(IllegalArgumentException::class.java) {
            FileProvider.getUriForFile(context, authority, cacheRootFile)
        }
    }
}
