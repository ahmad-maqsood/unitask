package com.example.util.drive

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.SocketTimeoutException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.prepareGet
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.Instant

/**
 * Google Drive REST API v3 client using Ktor without external Google API client libraries.
 * Interacts only with the appDataFolder space.
 */
class DriveRestClient(
    private val tokenProvider: suspend (forceRefresh: Boolean) -> String,
    engine: HttpClientEngine = OkHttp.create(),
    private val retryDelaysMs: List<Long> = listOf(1000L, 2000L, 4000L)
) : DriveStore {

    private val client: HttpClient = HttpClient(engine) {
        install(HttpTimeout) {
            connectTimeoutMillis = 30_000L
            requestTimeoutMillis = 120_000L
        }
    }

    /**
     * Lists files in appDataFolder matching the optional name prefix.
     * Follows nextPageToken until all pages are retrieved.
     */
    override suspend fun list(namePrefix: String?): List<DriveFile> = executeWithRetry { token ->
        val result = mutableListOf<DriveFile>()
        var pageToken: String? = null
        do {
            val response = client.get("https://www.googleapis.com/drive/v3/files") {
                header(HttpHeaders.Authorization, "Bearer $token")
                parameter("spaces", "appDataFolder")
                parameter("pageSize", "1000")
                parameter("fields", "nextPageToken,files(id,name,size,createdTime,modifiedTime,appProperties)")
                if (namePrefix != null) {
                    val escaped = namePrefix.replace("\\", "\\\\").replace("'", "\\'")
                    parameter("q", "name contains '$escaped'")
                }
                if (!pageToken.isNullOrEmpty()) {
                    parameter("pageToken", pageToken)
                }
            }
            if (response.status.value !in 200..299) {
                throw DriveHttpException(response.status.value, response.bodyAsText())
            }
            val json = JSONObject(response.bodyAsText())
            val filesArray = json.optJSONArray("files")
            if (filesArray != null) {
                for (i in 0 until filesArray.length()) {
                    val fileObj = filesArray.optJSONObject(i) ?: continue
                    val driveFile = parseDriveFile(fileObj)
                    if (namePrefix == null || driveFile.name.startsWith(namePrefix)) {
                        result.add(driveFile)
                    }
                }
            }
            pageToken = json.optString("nextPageToken", "").ifEmpty { null }
        } while (pageToken != null)
        result
    }

    /**
     * Creates file metadata in appDataFolder without content.
     */
    override suspend fun create(name: String, props: Map<String, String>): DriveFile = executeWithRetry { token ->
        val bodyJson = JSONObject().apply {
            put("name", name)
            put("parents", JSONArray().put("appDataFolder"))
            val propsObj = JSONObject()
            for ((k, v) in props) {
                propsObj.put(k, v)
            }
            put("appProperties", propsObj)
        }.toString()

        val response = client.post("https://www.googleapis.com/drive/v3/files") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            parameter("fields", "id,name,size,createdTime,modifiedTime,appProperties")
            setBody(bodyJson)
        }
        if (response.status.value !in 200..299) {
            throw DriveHttpException(response.status.value, response.bodyAsText())
        }
        parseDriveFile(JSONObject(response.bodyAsText()))
    }

    /**
     * Uploads in-memory byte content to an existing file.
     */
    override suspend fun upload(fileId: String, bytes: ByteArray): DriveFile = executeWithRetry { token ->
        val response = client.patch("https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media&fields=id,name,size,createdTime,modifiedTime,appProperties") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.OctetStream)
            setBody(bytes)
        }
        if (response.status.value !in 200..299) {
            throw DriveHttpException(response.status.value, response.bodyAsText())
        }
        parseDriveFile(JSONObject(response.bodyAsText()))
    }

    /**
     * Uploads a file from disk. For files up to 4 MB uses simple media upload.
     * For larger files initiates a resumable session and streams the file via PUT.
     */
    override suspend fun uploadFile(fileId: String, file: File): DriveFile {
        if (file.length() <= FOUR_MB) {
            val bytes = file.readBytes()
            return upload(fileId, bytes)
        }
        return executeWithRetry { token ->
            val sessionResponse = client.patch("https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=resumable&fields=id,name,size,createdTime,modifiedTime,appProperties") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header("X-Upload-Content-Length", file.length().toString())
                timeout {
                    requestTimeoutMillis = TEN_MINUTES_MS
                }
            }
            if (sessionResponse.status.value !in 200..299) {
                throw DriveHttpException(sessionResponse.status.value, sessionResponse.bodyAsText())
            }
            val sessionUrl = sessionResponse.headers[HttpHeaders.Location]
                ?: throw DriveException(DriveException.Kind.SERVER, "Missing Location header for resumable upload")

            val putResponse = client.put(sessionUrl) {
                timeout {
                    requestTimeoutMillis = TEN_MINUTES_MS
                }
                setBody(FileStreamContent(file))
            }
            if (putResponse.status.value !in 200..299) {
                throw DriveHttpException(putResponse.status.value, putResponse.bodyAsText())
            }
            parseDriveFile(JSONObject(putResponse.bodyAsText()))
        }
    }

    /**
     * Updates appProperties on an existing Drive file.
     */
    override suspend fun setProps(fileId: String, props: Map<String, String>): DriveFile = executeWithRetry { token ->
        val propsObj = JSONObject()
        for ((k, v) in props) {
            propsObj.put(k, v)
        }
        val bodyJson = JSONObject().put("appProperties", propsObj).toString()
        val response = client.patch("https://www.googleapis.com/drive/v3/files/$fileId?fields=id,name,size,createdTime,modifiedTime,appProperties") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(bodyJson)
        }
        if (response.status.value !in 200..299) {
            throw DriveHttpException(response.status.value, response.bodyAsText())
        }
        parseDriveFile(JSONObject(response.bodyAsText()))
    }

    /**
     * Downloads file contents as a ByteArray.
     */
    override suspend fun download(fileId: String): ByteArray = executeWithRetry { token ->
        val response = client.get("https://www.googleapis.com/drive/v3/files/$fileId?alt=media") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        if (response.status.value !in 200..299) {
            throw DriveHttpException(response.status.value, response.bodyAsText())
        }
        response.body<ByteArray>()
    }

    /**
     * Streams file contents from Drive into a target file safely.
     * Writes into target.part first and verifies Content-Length if present.
     */
    override suspend fun downloadToFile(fileId: String, target: File): Unit = executeWithRetry { token ->
        val parent = target.parentFile ?: File(".")
        val partFile = File(parent, "${target.name}.part")
        if (partFile.exists()) {
            partFile.delete()
        }
        try {
            client.prepareGet("https://www.googleapis.com/drive/v3/files/$fileId?alt=media") {
                header(HttpHeaders.Authorization, "Bearer $token")
                timeout {
                    requestTimeoutMillis = TEN_MINUTES_MS
                }
            }.execute { response ->
                if (response.status.value !in 200..299) {
                    throw DriveHttpException(response.status.value, response.bodyAsText())
                }
                val expectedLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                val channel = response.bodyAsChannel()
                var totalBytes = 0L
                partFile.outputStream().use { os ->
                    val buffer = ByteArray(8192)
                    while (!channel.isClosedForRead) {
                        val read = channel.readAvailable(buffer, 0, buffer.size)
                        if (read <= 0) break
                        os.write(buffer, 0, read)
                        totalBytes += read
                    }
                }
                if (expectedLength != null && totalBytes != expectedLength) {
                    throw DriveException(
                        DriveException.Kind.SERVER,
                        "Downloaded size $totalBytes does not match Content-Length $expectedLength"
                    )
                }
            }
            if (target.exists()) {
                target.delete()
            }
            if (!partFile.renameTo(target)) {
                partFile.copyTo(target, overwrite = true)
                partFile.delete()
            }
        } catch (e: Throwable) {
            if (partFile.exists()) {
                partFile.delete()
            }
            throw e
        }
    }

    /**
     * Deletes a file by its Drive ID.
     */
    override suspend fun delete(fileId: String): Unit = executeWithRetry { token ->
        val response = client.delete("https://www.googleapis.com/drive/v3/files/$fileId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        if (response.status.value !in 200..299) {
            throw DriveHttpException(response.status.value, response.bodyAsText())
        }
    }

    fun close() {
        client.close()
    }

    private suspend fun <T> executeWithRetry(block: suspend (token: String) -> T): T {
        var forceRefresh = false
        var hasRetried401 = false
        var serverRetriesUsed = 0

        while (true) {
            val token = try {
                tokenProvider(forceRefresh)
            } catch (e: Exception) {
                if (e is DriveException) throw e
                throw DriveException(DriveException.Kind.AUTH, "Failed to get auth token: ${e.message}")
            }
            forceRefresh = false

            try {
                return block(token)
            } catch (e: DriveHttpException) {
                when {
                    e.statusCode == 401 -> {
                        if (!hasRetried401) {
                            hasRetried401 = true
                            forceRefresh = true
                            continue
                        } else {
                            throw DriveException(DriveException.Kind.AUTH, "Authentication failed with HTTP 401")
                        }
                    }
                    e.statusCode == 403 -> {
                        if (isQuotaExceeded(e.errorBody)) {
                            throw DriveException(DriveException.Kind.QUOTA, "Storage quota exceeded")
                        } else {
                            throw DriveException(DriveException.Kind.OTHER, "Access forbidden with HTTP 403")
                        }
                    }
                    e.statusCode == 404 -> {
                        throw DriveException(DriveException.Kind.OTHER, "Resource not found with HTTP 404")
                    }
                    e.statusCode == 429 || e.statusCode in 500..599 -> {
                        if (serverRetriesUsed < retryDelaysMs.size) {
                            val delayMs = retryDelaysMs[serverRetriesUsed]
                            serverRetriesUsed++
                            delay(delayMs)
                            continue
                        } else {
                            throw DriveException(DriveException.Kind.SERVER, "Server error with HTTP ${e.statusCode}")
                        }
                    }
                    else -> {
                        throw DriveException(DriveException.Kind.OTHER, "HTTP error ${e.statusCode}")
                    }
                }
            } catch (e: IOException) {
                throw DriveException(DriveException.Kind.NETWORK, "Network error: ${e.message}")
            } catch (e: HttpRequestTimeoutException) {
                throw DriveException(DriveException.Kind.NETWORK, "Request timeout")
            } catch (e: ConnectTimeoutException) {
                throw DriveException(DriveException.Kind.NETWORK, "Connection timeout")
            } catch (e: SocketTimeoutException) {
                throw DriveException(DriveException.Kind.NETWORK, "Socket timeout")
            } catch (e: CancellationException) {
                throw e
            } catch (e: DriveException) {
                throw e
            } catch (e: Exception) {
                throw DriveException(DriveException.Kind.OTHER, e.message ?: "Unexpected error")
            }
        }
    }

    private fun isQuotaExceeded(body: String?): Boolean {
        if (body.isNullOrBlank()) return false
        return try {
            val root = JSONObject(body)
            val error = root.optJSONObject("error")
            if (error != null) {
                val errorsArray = error.optJSONArray("errors")
                if (errorsArray != null) {
                    for (i in 0 until errorsArray.length()) {
                        val item = errorsArray.optJSONObject(i)
                        val reason = item?.optString("reason")
                        if (reason == "storageQuotaExceeded" || reason == "quotaExceeded") {
                            return true
                        }
                    }
                }
                val message = error.optString("message", "")
                if (message.contains("storageQuotaExceeded") || message.contains("quotaExceeded")) {
                    return true
                }
            }
            body.contains("storageQuotaExceeded") || body.contains("quotaExceeded")
        } catch (_: Exception) {
            body.contains("storageQuotaExceeded") || body.contains("quotaExceeded")
        }
    }

    private fun parseDriveFile(json: JSONObject): DriveFile {
        val id = json.optString("id", "")
        val name = json.optString("name", "")
        val size = if (json.has("size")) {
            json.optLong("size", 0L).let {
                if (it != 0L) it else json.optString("size", "0").toLongOrNull() ?: 0L
            }
        } else {
            0L
        }
        val createdTimeMillis = parseIsoTimestamp(json.optString("createdTime", ""))
        val modifiedTimeMillis = parseIsoTimestamp(json.optString("modifiedTime", ""))
        val props = mutableMapOf<String, String>()
        val appProperties = json.optJSONObject("appProperties")
        if (appProperties != null) {
            val keys = appProperties.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                props[key] = appProperties.optString(key, "")
            }
        }
        return DriveFile(
            id = id,
            name = name,
            size = size,
            createdTimeMillis = createdTimeMillis,
            modifiedTimeMillis = modifiedTimeMillis,
            props = props
        )
    }

    private fun parseIsoTimestamp(iso: String): Long {
        if (iso.isBlank()) return 0L
        return try {
            Instant.parse(iso).toEpochMilli()
        } catch (_: Exception) {
            0L
        }
    }

    private class FileStreamContent(private val file: File) : OutgoingContent.WriteChannelContent() {
        override val contentLength: Long = file.length()
        override val contentType: ContentType = ContentType.Application.OctetStream

        override suspend fun writeTo(channel: ByteWriteChannel) {
            file.inputStream().use { stream ->
                val buffer = ByteArray(8192)
                while (true) {
                    val read = stream.read(buffer)
                    if (read == -1) break
                    channel.writeFully(buffer, 0, read)
                }
            }
        }
    }

    private class DriveHttpException(val statusCode: Int, val errorBody: String?) : Exception()

    companion object {
        private const val FOUR_MB = 4 * 1024 * 1024L
        private const val TEN_MINUTES_MS = 10 * 60 * 1000L
    }
}
