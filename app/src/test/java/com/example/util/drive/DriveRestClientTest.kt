package com.example.util.drive

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.IOException

class DriveRestClientTest {

    @Test
    fun list_followsTwoPagesAndFiltersByPrefix() = runTest {
        var pageCount = 0
        val engine = MockEngine { request ->
            assertEquals("appDataFolder", request.url.parameters["spaces"])
            assertEquals("1000", request.url.parameters["pageSize"])
            assertEquals("Bearer token123", request.headers[HttpHeaders.Authorization])

            when (request.url.parameters["pageToken"]) {
                null -> {
                    pageCount++
                    assertEquals("name contains 'backup_'", request.url.parameters["q"])
                    respond(
                        content = """
                        {
                          "nextPageToken": "page_2",
                          "files": [
                            {
                              "id": "file_1",
                              "name": "backup_2026_01.json",
                              "size": 120,
                              "createdTime": "2026-01-01T00:00:00Z",
                              "modifiedTime": "2026-01-01T01:00:00Z",
                              "appProperties": { "type": "db" }
                            },
                            {
                              "id": "file_skip",
                              "name": "other_note.txt",
                              "size": 50,
                              "createdTime": "2026-01-01T00:00:00Z",
                              "modifiedTime": "2026-01-01T01:00:00Z"
                            }
                          ]
                        }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                "page_2" -> {
                    pageCount++
                    respond(
                        content = """
                        {
                          "files": [
                            {
                              "id": "file_2",
                              "name": "backup_2026_02.json",
                              "size": "240",
                              "createdTime": "2026-01-02T00:00:00Z",
                              "modifiedTime": "2026-01-02T01:00:00Z"
                            }
                          ]
                        }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                else -> error("Unexpected pageToken: ${request.url.parameters["pageToken"]}")
            }
        }

        val client = DriveRestClient(tokenProvider = { "token123" }, engine = engine)
        val files = client.list("backup_")

        assertEquals(2, pageCount)
        assertEquals(2, files.size)
        assertEquals("file_1", files[0].id)
        assertEquals("backup_2026_01.json", files[0].name)
        assertEquals(120L, files[0].size)
        assertEquals("db", files[0].props["type"])
        assertEquals("file_2", files[1].id)
        assertEquals(240L, files[1].size)
    }

    @Test
    fun list_quoteInPrefixIsEscaped() = runTest {
        var requestedQ: String? = null
        val engine = MockEngine { request ->
            requestedQ = request.url.parameters["q"]
            respond(
                content = """{ "files": [] }""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = DriveRestClient(tokenProvider = { "token123" }, engine = engine)
        client.list("user's\\prefix")

        assertEquals("name contains 'user\\'s\\\\prefix'", requestedQ)
    }

    @Test
    fun create_thenUpload_sendsRightUrlsMethodsAndBytes() = runTest {
        var createdFile = false
        var uploadedBytes = false
        val engine = MockEngine { request ->
            when (request.method) {
                HttpMethod.Post -> {
                    assertEquals("https://www.googleapis.com/drive/v3/files", request.url.toString().substringBefore("?"))
                    assertEquals("Bearer token123", request.headers[HttpHeaders.Authorization])
                    val bodyString = request.body.toByteArray().decodeToString()
                    assertTrue(bodyString.contains("my_backup.json"))
                    assertTrue(bodyString.contains("appDataFolder"))
                    assertTrue(bodyString.contains("checksum"))
                    createdFile = true
                    respond(
                        content = """
                        {
                          "id": "new_file_id",
                          "name": "my_backup.json",
                          "size": 0,
                          "createdTime": "2026-01-01T00:00:00Z",
                          "modifiedTime": "2026-01-01T00:00:00Z"
                        }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                HttpMethod.Patch -> {
                    assertTrue(request.url.toString().contains("/upload/drive/v3/files/new_file_id"))
                    assertEquals("media", request.url.parameters["uploadType"])
                    assertEquals(ContentType.Application.OctetStream, request.body.contentType)
                    val payload = request.body.toByteArray()
                    assertTrue(payload.contentEquals(byteArrayOf(1, 2, 3, 4)))
                    uploadedBytes = true
                    respond(
                        content = """
                        {
                          "id": "new_file_id",
                          "name": "my_backup.json",
                          "size": 4,
                          "createdTime": "2026-01-01T00:00:00Z",
                          "modifiedTime": "2026-01-01T00:00:00Z"
                        }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                else -> error("Unexpected method: ${request.method}")
            }
        }

        val client = DriveRestClient(tokenProvider = { "token123" }, engine = engine)
        val created = client.create("my_backup.json", mapOf("checksum" to "abc"))
        assertEquals("new_file_id", created.id)
        assertTrue(createdFile)

        val uploaded = client.upload("new_file_id", byteArrayOf(1, 2, 3, 4))
        assertEquals(4L, uploaded.size)
        assertTrue(uploadedBytes)
    }

    @Test
    fun tokenRefresh_401ThenSuccess_asksTokenProviderWithForceRefreshOnce() = runTest {
        val tokenCalls = mutableListOf<Boolean>()
        var requestIndex = 0

        val engine = MockEngine { request ->
            requestIndex++
            if (requestIndex == 1) {
                assertEquals("Bearer token_old", request.headers[HttpHeaders.Authorization])
                respond(
                    content = """{"error": {"code": 401, "message": "Invalid Credentials"}}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                assertEquals("Bearer token_refreshed", request.headers[HttpHeaders.Authorization])
                respond(
                    content = """{"files": []}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }

        val client = DriveRestClient(
            tokenProvider = { forceRefresh ->
                tokenCalls.add(forceRefresh)
                if (forceRefresh) "token_refreshed" else "token_old"
            },
            engine = engine
        )

        val result = client.list()
        assertEquals(emptyList<DriveFile>(), result)
        assertEquals(2, tokenCalls.size)
        assertEquals(listOf(false, true), tokenCalls)
        assertEquals(2, requestIndex)
    }

    @Test
    fun two401InARow_givesAuthException() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"error": {"code": 401, "message": "Unauthorized"}}""",
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = DriveRestClient(
            tokenProvider = { "token" },
            engine = engine
        )

        try {
            client.list()
            fail("Expected DriveException with AUTH kind")
        } catch (e: DriveException) {
            assertEquals(DriveException.Kind.AUTH, e.kind)
        }
    }

    @Test
    fun storageQuotaExceeded_givesQuotaException() = runTest {
        val engine = MockEngine {
            respond(
                content = """
                {
                  "error": {
                    "code": 403,
                    "message": "The user's Drive storage quota has been exceeded.",
                    "errors": [
                      {
                        "domain": "usageLimits",
                        "reason": "storageQuotaExceeded",
                        "message": "The user's Drive storage quota has been exceeded."
                      }
                    ]
                  }
                }
                """.trimIndent(),
                status = HttpStatusCode.Forbidden,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = DriveRestClient(tokenProvider = { "token" }, engine = engine)
        try {
            client.list()
            fail("Expected DriveException with QUOTA kind")
        } catch (e: DriveException) {
            assertEquals(DriveException.Kind.QUOTA, e.kind)
        }
    }

    @Test
    fun otherForbidden_givesOtherException() = runTest {
        val engine = MockEngine {
            respond(
                content = """
                {
                  "error": {
                    "code": 403,
                    "message": "Access not configured",
                    "errors": [{ "reason": "accessNotConfigured" }]
                  }
                }
                """.trimIndent(),
                status = HttpStatusCode.Forbidden,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = DriveRestClient(tokenProvider = { "token" }, engine = engine)
        try {
            client.list()
            fail("Expected DriveException with OTHER kind")
        } catch (e: DriveException) {
            assertEquals(DriveException.Kind.OTHER, e.kind)
        }
    }

    @Test
    fun serverError_500Then200_succeedsAfterOneRetry() = runTest {
        var callCount = 0
        val engine = MockEngine {
            callCount++
            if (callCount == 1) {
                respond(
                    content = "Server temporary failure",
                    status = HttpStatusCode.InternalServerError
                )
            } else {
                respond(
                    content = """{"files": []}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }

        val client = DriveRestClient(
            tokenProvider = { "token" },
            engine = engine,
            retryDelaysMs = listOf(1L, 1L, 1L)
        )

        val result = client.list()
        assertEquals(emptyList<DriveFile>(), result)
        assertEquals(2, callCount)
    }

    @Test
    fun serverError_three500_givesServerException() = runTest {
        var callCount = 0
        val engine = MockEngine {
            callCount++
            respond(
                content = "Server dead",
                status = HttpStatusCode.InternalServerError
            )
        }

        val client = DriveRestClient(
            tokenProvider = { "token" },
            engine = engine,
            retryDelaysMs = listOf(1L, 1L, 1L)
        )

        try {
            client.list()
            fail("Expected DriveException with SERVER kind")
        } catch (e: DriveException) {
            assertEquals(DriveException.Kind.SERVER, e.kind)
        }
        assertEquals(4, callCount) // Initial call + 3 retries
    }

    @Test
    fun download_returnsBytes() = runTest {
        val expected = byteArrayOf(10, 20, 30, 40)
        val engine = MockEngine { request ->
            assertEquals("https://www.googleapis.com/drive/v3/files/file_abc?alt=media", request.url.toString())
            respond(
                content = expected,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/octet-stream")
            )
        }

        val client = DriveRestClient(tokenProvider = { "token" }, engine = engine)
        val actual = client.download("file_abc")
        assertTrue(expected.contentEquals(actual))
    }

    @Test
    fun uploadFile_oneMbUsesMedia_sixMbUsesResumablePut() = runTest {
        val tempDir = File.createTempFile("drivetest", "dir").apply {
            delete()
            mkdir()
        }
        try {
            val oneMbFile = File(tempDir, "one_mb.bin").apply {
                writeBytes(ByteArray(1024 * 1024))
            }
            val sixMbFile = File(tempDir, "six_mb.bin").apply {
                writeBytes(ByteArray(6 * 1024 * 1024))
            }

            var usedMediaPatch = false
            var usedResumablePatch = false
            var usedPutToLocation = false

            val engine = MockEngine { request ->
                when (request.method) {
                    HttpMethod.Patch -> {
                        val uploadType = request.url.parameters["uploadType"]
                        if (uploadType == "media") {
                            usedMediaPatch = true
                            respond(
                                content = """{"id": "file_1mb", "name": "one_mb.bin", "size": 1048576}""",
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json")
                            )
                        } else if (uploadType == "resumable") {
                            usedResumablePatch = true
                            assertEquals((6 * 1024 * 1024).toString(), request.headers["X-Upload-Content-Length"])
                            respond(
                                content = "",
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.Location, "https://upload.example.com/session_789")
                            )
                        } else {
                            error("Unexpected uploadType: $uploadType")
                        }
                    }
                    HttpMethod.Put -> {
                        assertEquals("https://upload.example.com/session_789", request.url.toString())
                        assertEquals(6 * 1024 * 1024L, request.body.contentLength)
                        usedPutToLocation = true
                        respond(
                            content = """{"id": "file_6mb", "name": "six_mb.bin", "size": 6291456}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }
                    else -> error("Unexpected method: ${request.method}")
                }
            }

            val client = DriveRestClient(tokenProvider = { "token" }, engine = engine)

            val smallResult = client.uploadFile("file_1mb", oneMbFile)
            assertTrue(usedMediaPatch)
            assertEquals("file_1mb", smallResult.id)

            val largeResult = client.uploadFile("file_6mb", sixMbFile)
            assertTrue(usedResumablePatch)
            assertTrue(usedPutToLocation)
            assertEquals("file_6mb", largeResult.id)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun downloadToFile_writesTargetOnlyWhenComplete_andLeavesNoPartFileOnFailure() = runTest {
        val tempDir = File.createTempFile("drivetest_download", "dir").apply {
            delete()
            mkdir()
        }
        try {
            // Scenario 1: Complete and successful download
            val targetSuccess = File(tempDir, "success.txt")
            val partSuccess = File(tempDir, "success.txt.part")
            val engineSuccess = MockEngine {
                respond(
                    content = "complete data".toByteArray(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(
                        HttpHeaders.ContentLength to listOf("complete data".toByteArray().size.toString()),
                        HttpHeaders.ContentType to listOf("text/plain")
                    )
                )
            }
            val clientSuccess = DriveRestClient(tokenProvider = { "token" }, engine = engineSuccess)
            clientSuccess.downloadToFile("file_ok", targetSuccess)
            assertTrue(targetSuccess.exists())
            assertEquals("complete data", targetSuccess.readText())
            assertFalse(partSuccess.exists())

            // Scenario 2: Size mismatch (Content-Length says 100, body only has 5 bytes)
            val targetMismatch = File(tempDir, "mismatch.txt")
            val partMismatch = File(tempDir, "mismatch.txt.part")
            val engineMismatch = MockEngine {
                respond(
                    content = "short".toByteArray(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(
                        HttpHeaders.ContentLength to listOf("100"),
                        HttpHeaders.ContentType to listOf("text/plain")
                    )
                )
            }
            val clientMismatch = DriveRestClient(
                tokenProvider = { "token" },
                engine = engineMismatch,
                retryDelaysMs = listOf(1L)
            )
            try {
                clientMismatch.downloadToFile("file_mismatch", targetMismatch)
                fail("Expected DriveException on size mismatch")
            } catch (e: DriveException) {
                assertEquals(DriveException.Kind.SERVER, e.kind)
            }
            assertFalse(targetMismatch.exists())
            assertFalse(partMismatch.exists())

            // Scenario 3: Mid-stream network error
            val targetError = File(tempDir, "error.txt")
            val partError = File(tempDir, "error.txt.part")
            val engineError = MockEngine {
                throw IOException("Connection reset mid-stream")
            }
            val clientError = DriveRestClient(
                tokenProvider = { "token" },
                engine = engineError,
                retryDelaysMs = listOf(1L)
            )
            try {
                clientError.downloadToFile("file_err", targetError)
                fail("Expected DriveException on network error")
            } catch (e: DriveException) {
                assertEquals(DriveException.Kind.NETWORK, e.kind)
            }
            assertFalse(targetError.exists())
            assertFalse(partError.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
