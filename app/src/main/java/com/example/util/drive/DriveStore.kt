package com.example.util.drive

import java.io.File

/**
 * Metadata for a file in the appDataFolder on Google Drive.
 */
data class DriveFile(
    val id: String,
    val name: String,
    val size: Long,
    val createdTimeMillis: Long,
    val modifiedTimeMillis: Long,
    val props: Map<String, String>
)

/**
 * Contract for Google Drive operations.
 * Allows unit testing with fakes and MockEngine.
 */
interface DriveStore {
    suspend fun list(namePrefix: String? = null): List<DriveFile>
    suspend fun create(name: String, props: Map<String, String> = emptyMap()): DriveFile
    suspend fun upload(fileId: String, bytes: ByteArray): DriveFile
    suspend fun uploadFile(fileId: String, file: File): DriveFile
    suspend fun setProps(fileId: String, props: Map<String, String>): DriveFile
    suspend fun download(fileId: String): ByteArray
    suspend fun downloadToFile(fileId: String, target: File)
    suspend fun delete(fileId: String)
}

/**
 * Exceptions thrown during Google Drive operations.
 */
class DriveException(
    val kind: Kind,
    message: String
) : Exception(message) {
    enum class Kind {
        AUTH,
        QUOTA,
        NETWORK,
        SERVER,
        OTHER
    }
}
