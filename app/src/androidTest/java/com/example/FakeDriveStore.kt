package com.example

import com.example.util.drive.DriveException
import com.example.util.drive.DriveFile
import com.example.util.drive.DriveStore
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-memory DriveStore fake for Android instrumented smoke tests without network calls.
 */
class FakeDriveStore : DriveStore {

    data class StoredFile(
        val id: String,
        var name: String,
        var bytes: ByteArray,
        val createdTimeMillis: Long,
        var modifiedTimeMillis: Long,
        val props: MutableMap<String, String>
    )

    val files = mutableMapOf<String, StoredFile>()
    val callLog = mutableListOf<String>()
    private val idCounter = AtomicInteger(1)

    var failOnCallNumber: Int? = null
    var failOnMethod: String? = null
    var onUpload: ((fileId: String) -> Unit)? = null
    var onDelete: ((fileId: String) -> Unit)? = null
    var defaultException: Exception = DriveException(DriveException.Kind.SERVER, "Fake Drive error")

    private fun checkFailure(method: String) {
        callLog.add(method)
        val currentCall = callLog.size
        if (failOnCallNumber == currentCall || failOnMethod == method) {
            throw defaultException
        }
    }

    override suspend fun list(namePrefix: String?): List<DriveFile> {
        checkFailure("list")
        return files.values
            .filter { namePrefix == null || it.name.startsWith(namePrefix) }
            .map { it.toDriveFile() }
    }

    override suspend fun create(name: String, props: Map<String, String>): DriveFile {
        checkFailure("create")
        val id = "fake_file_${idCounter.getAndIncrement()}"
        val now = System.currentTimeMillis()
        val stored = StoredFile(
            id = id,
            name = name,
            bytes = ByteArray(0),
            createdTimeMillis = now,
            modifiedTimeMillis = now,
            props = props.toMutableMap()
        )
        files[id] = stored
        return stored.toDriveFile()
    }

    override suspend fun upload(fileId: String, bytes: ByteArray): DriveFile {
        checkFailure("upload")
        onUpload?.invoke(fileId)
        val stored = files[fileId]
            ?: throw DriveException(DriveException.Kind.OTHER, "File not found: $fileId")
        stored.bytes = bytes.copyOf()
        stored.modifiedTimeMillis = System.currentTimeMillis()
        return stored.toDriveFile()
    }

    override suspend fun uploadFile(fileId: String, file: File): DriveFile {
        checkFailure("uploadFile")
        return upload(fileId, file.readBytes())
    }

    override suspend fun setProps(fileId: String, props: Map<String, String>): DriveFile {
        checkFailure("setProps")
        val stored = files[fileId]
            ?: throw DriveException(DriveException.Kind.OTHER, "File not found: $fileId")
        stored.props.putAll(props)
        stored.modifiedTimeMillis = System.currentTimeMillis()
        return stored.toDriveFile()
    }

    override suspend fun download(fileId: String): ByteArray {
        checkFailure("download")
        val stored = files[fileId]
            ?: throw DriveException(DriveException.Kind.OTHER, "File not found: $fileId")
        return stored.bytes.copyOf()
    }

    override suspend fun downloadToFile(fileId: String, target: File) {
        checkFailure("downloadToFile")
        val bytes = download(fileId)
        val parent = target.parentFile ?: File(".")
        val part = File(parent, "${target.name}.part")
        part.writeBytes(bytes)
        if (target.exists()) target.delete()
        part.renameTo(target)
    }

    override suspend fun delete(fileId: String) {
        checkFailure("delete")
        onDelete?.invoke(fileId)
        files.remove(fileId)
    }

    private fun StoredFile.toDriveFile() = DriveFile(
        id = id,
        name = name,
        size = bytes.size.toLong(),
        createdTimeMillis = createdTimeMillis,
        modifiedTimeMillis = modifiedTimeMillis,
        props = props.toMap()
    )
}
