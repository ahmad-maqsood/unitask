package com.example.util.drive

import android.content.Context
import com.example.BuildConfig
import com.example.data.local.AppDatabase
import com.example.data.local.MY_TASKS_COURSE_CODE
import com.example.util.normalizeCourseCode
import com.example.util.BackupFileSource
import com.example.util.BackupResult
import com.example.util.BuiltBackup
import com.example.util.LocalBackupManager
import com.example.util.NoteImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Summary metadata of a Google Drive backup slot.
 */
data class DriveBackupInfo(
    val slotFileId: String,
    val seq: Long,
    val exportedAt: Long,
    val courses: Int,
    val tasks: Int,
    val notes: Int,
    val images: Int,
    val appVersion: String,
    val formatVersion: Int,
    val sizeBytes: Long,
    val attachments: Int = 0,
    val myTasks: Int = 0
)

/**
 * Result of a Google Drive backup execution.
 */
sealed class DriveBackupResult {
    data class Success(
        val info: DriveBackupInfo,
        val uploadedFiles: Int,
        val skippedFiles: Int
    ) : DriveBackupResult()

    data class Unchanged(val info: DriveBackupInfo) : DriveBackupResult()

    object EmptyBlocked : DriveBackupResult()

    data class Failure(
        val kind: DriveException.Kind,
        val message: String
    ) : DriveBackupResult()
}

/**
 * Engine for backing up and restoring user data and note attachments to Google Drive.
 * Rotates between two backup slot files in the hidden appDataFolder.
 */
object DriveBackupManager {

    private const val SLOT_1_NAME = "unitask-backup-1.json"
    private const val SLOT_2_NAME = "unitask-backup-2.json"
    private const val IMAGE_PREFIX = "img-"
    private const val ATTACHMENT_PREFIX = "att-"
    private const val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000L

    /** The two slots swap roles at most once per this long, so one of them is always older history. */
    const val HISTORY_ROTATE_MS = 24L * 60 * 60 * 1000L

    private val mutex = Mutex()

    /**
     * Lists all Drive file names that the given backup data references.
     * Step 4 will extend this to include settings.
     */
    fun referencedDriveFileNames(built: BuiltBackup): Set<String> {
        val names = mutableSetOf<String>()
        for (img in built.referencedImages) {
            names.add("$IMAGE_PREFIX$img")
        }
        val attArray = built.data.optJSONArray("attachments")
        if (attArray != null) {
            for (i in 0 until attArray.length()) {
                val o = attArray.optJSONObject(i) ?: continue
                val storedName = o.optString("storedName")
                if (storedName.isNotEmpty()) {
                    names.add("$ATTACHMENT_PREFIX$storedName")
                }
            }
        }
        for (att in built.referencedAttachments) {
            names.add("$ATTACHMENT_PREFIX$att")
        }
        return names
    }

    /**
     * Builds a human-readable summary of backup counts.
     */
    fun buildCountsText(info: DriveBackupInfo): String {
        val parts = mutableListOf(
            "${info.courses} courses",
            "${info.tasks} tasks"
        )
        if (info.myTasks > 0) {
            parts.add("${info.myTasks} My tasks")
        }
        parts.add("${info.notes} notes")
        if (info.attachments > 0) {
            parts.add("${info.attachments} attachments")
        }
        return parts.joinToString(", ")
    }

    /**
     * Inspects properties of both slots without downloading content and returns the latest valid slot info.
     */
    suspend fun latestInfo(store: DriveStore): DriveBackupInfo? {
        val files = store.list()
        val slot1 = files.firstOrNull { it.name == SLOT_1_NAME }
        val slot2 = files.firstOrNull { it.name == SLOT_2_NAME }
        val info1 = slot1?.let { infoFromSlot(it) }
        val info2 = slot2?.let { infoFromSlot(it) }
        return when {
            info1 != null && info2 != null -> if (info1.seq >= info2.seq) info1 else info2
            info1 != null -> info1
            info2 != null -> info2
            else -> null
        }
    }

    /**
     * Both backups on Drive that have readable details, newest first (zero, one or two entries).
     * The second entry is the older copy offered by "Use the older backup".
     */
    suspend fun listBackups(store: DriveStore): List<DriveBackupInfo> {
        val files = store.list()
        return listOf(SLOT_1_NAME, SLOT_2_NAME)
            .mapNotNull { name -> files.firstOrNull { it.name == name }?.let { infoFromSlot(it) } }
            .sortedByDescending { it.seq }
    }

    /**
     * Backs up database data and note images to Google Drive.
     */
    suspend fun backUp(
        context: Context,
        store: DriveStore,
        allowEmpty: Boolean = false,
        force: Boolean = false,
        rotateAfterMs: Long = HISTORY_ROTATE_MS,
        onProgress: ((done: Int, total: Int) -> Unit)? = null
    ): DriveBackupResult = mutex.withLock {
        try {
            // (a) Empty guard: check before any network call
            if (!allowEmpty && isDatabaseEmpty(context)) {
                return DriveBackupResult.EmptyBlocked
            }

            // (b) Build data without inline images, attach app settings, and compute hash
            val prefs = DrivePrefs(context)
            val built = LocalBackupManager.buildData(
                context = context,
                inlineImages = false,
                includeAttachments = prefs.includeAttachments
            )

            // Settings are always included in Drive backups
            built.data.put("settings", SettingsSnapshot.capture(context))

            val dataString = built.data.toString()
            val dataHash = sha256Hex(dataString)

            val envelope = JSONObject().apply {
                put("format", "unitask-backup")
                put("version", 1)
                put("app", "UniTask")
                put("exportedAt", System.currentTimeMillis())
                put("encrypted", false)
                put("data", built.data)
            }
            val envelopeBytes = envelope.toString(2).toByteArray(Charsets.UTF_8)

            // (c) List folder
            val folderFiles = store.list()
            val slot1 = folderFiles.firstOrNull { it.name == SLOT_1_NAME }
            val slot2 = folderFiles.firstOrNull { it.name == SLOT_2_NAME }
            val latestSlot = pickLatestSlot(slot1, slot2)

            // (d) Unchanged check
            if (!force && latestSlot != null) {
                val latestHash = latestSlot.props["dataHash"]
                val hasMyTasks = latestSlot.props.containsKey("myTasks")
                if (latestHash != null && latestHash == dataHash && hasMyTasks) {
                    val info = infoFromSlot(latestSlot)
                    if (info != null) {
                        val checkedAt = System.currentTimeMillis()
                        prefs.lastAttemptAt = checkedAt
                        // Nothing new to upload means the Drive copy is current.
                        prefs.lastSafeAt = checkedAt
                        return DriveBackupResult.Unchanged(info)
                    }
                }
            }

            // (e) Upload new images and attachments
            val existingDriveImageNames = folderFiles
                .filter { it.name.startsWith(IMAGE_PREFIX) }
                .map { it.name.removePrefix(IMAGE_PREFIX) }
                .toSet()

            val imagesToUpload = built.referencedImages.filter { it !in existingDriveImageNames }

            val attachmentsDir = File(context.filesDir, "attachments")
            val existingDriveAttachmentsByName = folderFiles
                .filter { it.name.startsWith(ATTACHMENT_PREFIX) }
                .associateBy { it.name }

            val distinctAttachmentNames = built.referencedAttachments.distinct()
            val attachmentsToUpload = mutableListOf<Pair<String, String?>>()
            for (storedName in distinctAttachmentNames) {
                val localFile = File(attachmentsDir, storedName)
                if (!localFile.exists() || !localFile.isFile) continue
                val driveFileName = "$ATTACHMENT_PREFIX$storedName"
                val existingDrive = existingDriveAttachmentsByName[driveFileName]
                if (existingDrive == null) {
                    attachmentsToUpload.add(Pair(storedName, null))
                } else if (existingDrive.size != localFile.length()) {
                    attachmentsToUpload.add(Pair(storedName, existingDrive.id))
                }
            }

            var uploadedFilesCount = 0
            val skippedImagesCount = built.referencedImages.size - imagesToUpload.size
            val skippedAttachmentsCount = distinctAttachmentNames.size - attachmentsToUpload.size
            val skippedFilesCount = skippedImagesCount + skippedAttachmentsCount
            val totalWork = imagesToUpload.size + attachmentsToUpload.size + 1

            onProgress?.invoke(0, totalWork)

            for (imageName in imagesToUpload) {
                val file = NoteImages.imageFile(context, imageName)
                if (file != null && file.exists()) {
                    val created = store.create("$IMAGE_PREFIX$imageName")
                    store.uploadFile(created.id, file)
                    uploadedFilesCount++
                    onProgress?.invoke(uploadedFilesCount, totalWork)
                }
            }

            for ((storedName, existingFileId) in attachmentsToUpload) {
                val localFile = File(attachmentsDir, storedName)
                if (localFile.exists() && localFile.isFile) {
                    val fileId = existingFileId ?: store.create("$ATTACHMENT_PREFIX$storedName").id
                    store.uploadFile(fileId, localFile)
                    uploadedFilesCount++
                    onProgress?.invoke(uploadedFilesCount, totalWork)
                }
            }

            // (f) Pick older slot (or create slot 1, then slot 2) and upload envelope bytes
            val slotChoice = pickTargetSlot(store, slot1, slot2, System.currentTimeMillis(), rotateAfterMs)
            val targetSlot = slotChoice.file
            val uploadedFile = store.upload(targetSlot.id, envelopeBytes)
            if (uploadedFile.size != envelopeBytes.size.toLong()) {
                throw DriveException(DriveException.Kind.SERVER, "Uploaded backup size mismatch")
            }
            uploadedFilesCount++
            onProgress?.invoke(uploadedFilesCount, totalWork)

            // (g) Write properties with seq = max existing seq + 1
            val maxExistingSeq = maxOf(
                slot1?.props?.get("seq")?.toLongOrNull() ?: 0L,
                slot2?.props?.get("seq")?.toLongOrNull() ?: 0L
            )
            val newSeq = maxExistingSeq + 1
            val now = System.currentTimeMillis()
            val props = mapOf(
                "seq" to newSeq.toString(),
                "exportedAt" to now.toString(),
                "courses" to built.counts.courses.toString(),
                "tasks" to built.counts.tasks.toString(),
                "myTasks" to built.counts.myTasks.toString(),
                "notes" to built.counts.notes.toString(),
                "images" to built.referencedImages.size.toString(),
                "attachments" to built.counts.attachments.toString(),
                "appVersion" to BuildConfig.VERSION_NAME,
                "formatVersion" to "1",
                "dataHash" to dataHash,
                "rotatedAt" to slotChoice.rotatedAt.toString()
            )
            val finalizedSlot = store.setProps(targetSlot.id, props)

            // (h) Prune unreferenced images and attachments older than 30 days
            val referencedDriveNames = referencedDriveFileNames(built)
            for (f in folderFiles) {
                val isImageOrAttachment = f.name.startsWith(IMAGE_PREFIX) || f.name.startsWith(ATTACHMENT_PREFIX)
                if (isImageOrAttachment && f.name !in referencedDriveNames) {
                    val age = now - f.createdTimeMillis
                    if (age > THIRTY_DAYS_MS) {
                        try {
                            store.delete(f.id)
                        } catch (e: Exception) {
                            // A failed prune is not fatal: it is tried again on the next backup.
                            android.util.Log.w("UniTask", "Drive prune failed", e)
                        }
                    }
                }
            }

            val info = DriveBackupInfo(
                slotFileId = finalizedSlot.id,
                seq = newSeq,
                exportedAt = now,
                courses = built.counts.courses,
                tasks = built.counts.tasks,
                notes = built.counts.notes,
                images = built.referencedImages.size,
                appVersion = BuildConfig.VERSION_NAME,
                formatVersion = 1,
                sizeBytes = envelopeBytes.size.toLong(),
                attachments = built.counts.attachments,
                myTasks = built.counts.myTasks
            )

            // (i) Update DrivePrefs
            prefs.lastSuccessAt = now
            prefs.lastSafeAt = now
            prefs.lastAttemptAt = now
            prefs.lastHash = dataHash
            prefs.lastCounts = buildCountsText(info)
            prefs.lastError = null
            prefs.consecutiveFailures = 0
            prefs.failureNotified = false
            prefs.firstFailureAt = 0L

            DriveBackupResult.Success(
                info = info,
                uploadedFiles = uploadedFilesCount,
                skippedFiles = skippedFilesCount
            )
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            val prefs = DrivePrefs(context)
            prefs.lastAttemptAt = System.currentTimeMillis()
            prefs.lastError = e.message ?: "Backup failed"
            prefs.consecutiveFailures += 1
            val kind = if (e is DriveException) e.kind else DriveException.Kind.OTHER
            DriveBackupResult.Failure(kind, e.message ?: "Backup failed")
        }
    }

    /**
     * Restores backup from Google Drive by downloading the latest valid slot and streaming note images.
     *
     * With [slotFileId] only that one backup is read, and there is no fallback to the other one:
     * a person who chose the older backup must not silently get the newer one. If it is damaged,
     * the restore fails with a message.
     */
    suspend fun restore(
        context: Context,
        store: DriveStore,
        onProgress: ((Int, Int) -> Unit)? = null,
        slotFileId: String? = null
    ): BackupResult = mutex.withLock {
        try {
            val files = store.list()
            val slot1 = files.firstOrNull { it.name == SLOT_1_NAME }
            val slot2 = files.firstOrNull { it.name == SLOT_2_NAME }

            if (slot1 == null && slot2 == null) {
                return BackupResult.Failure("No backup found on Google Drive.")
            }

            val seq1 = slot1?.props?.get("seq")?.toLongOrNull() ?: -1L
            val seq2 = slot2?.props?.get("seq")?.toLongOrNull() ?: -1L
            val candidates = when {
                slotFileId != null -> {
                    val chosen = listOfNotNull(slot1, slot2).firstOrNull { it.id == slotFileId }
                        ?: return BackupResult.Failure(
                            "That backup is no longer on Google Drive. Close this and try again."
                        )
                    listOf(chosen)
                }
                seq1 >= seq2 -> listOfNotNull(slot1, slot2)
                else -> listOfNotNull(slot2, slot1)
            }

            var validData: JSONObject? = null
            for (slot in candidates) {
                try {
                    val bytes = store.download(slot.id)
                    val text = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
                    val root = JSONObject(text)
                    if (root.optString("format") != "unitask-backup") {
                        continue
                    }
                    val version = root.optInt("version", 0)
                    if (version > 1) {
                        return BackupResult.Failure(
                            "This backup was made by a newer version of UniTask. Update the app and try again."
                        )
                    }
                    val data = root.optJSONObject("data") ?: continue
                    val expectedHash = slot.props["dataHash"]
                    if (!expectedHash.isNullOrBlank()) {
                        val actualHash = sha256Hex(data.toString())
                        if (actualHash != expectedHash) {
                            continue
                        }
                    }
                    validData = data
                    break
                } catch (e: Exception) {
                    // slot damaged, try fallback slot
                    android.util.Log.w("UniTask", "Drive backup slot unreadable, trying the other one", e)
                }
            }

            if (validData == null) {
                return BackupResult.Failure("The backup on Google Drive is damaged and could not be restored.")
            }

            val imageIdByName = files
                .filter { it.name.startsWith(IMAGE_PREFIX) }
                .associate { it.name.removePrefix(IMAGE_PREFIX) to it.id }

            val attachmentIdByName = files
                .filter { it.name.startsWith(ATTACHMENT_PREFIX) }
                .associate { it.name.removePrefix(ATTACHMENT_PREFIX) to it.id }

            val source = object : BackupFileSource {
                override suspend fun noteImage(name: String): ByteArray? {
                    val fileId = imageIdByName[name] ?: return null
                    return try {
                        store.download(fileId)
                    } catch (_: Exception) {
                        null
                    }
                }

                override suspend fun downloadAttachment(storedName: String, target: File): Boolean {
                    val fileId = attachmentIdByName[storedName] ?: return false
                    return try {
                        store.downloadToFile(fileId, target)
                        true
                    } catch (_: Exception) {
                        false
                    }
                }
            }

            val counts = LocalBackupManager.applyBackupData(context, validData, source)
            BackupResult.Success(counts)
        } catch (e: DriveException) {
            val message = when (e.kind) {
                DriveException.Kind.AUTH -> "Google Drive access was lost. Reconnect and try again."
                DriveException.Kind.QUOTA -> "Your Google Drive is full."
                DriveException.Kind.NETWORK -> "No internet connection."
                DriveException.Kind.SERVER, DriveException.Kind.OTHER -> "Google Drive had a problem. Try again later."
            }
            BackupResult.Failure(message)
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            BackupResult.Failure(e.message ?: "Google Drive had a problem. Try again later.")
        }
    }

    internal suspend fun isDatabaseEmpty(context: Context): Boolean = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val courses = db.courseDao().getAllCoursesListIncludingArchived().filter {
            !it.isDeleted && normalizeCourseCode(it.code) != MY_TASKS_COURSE_CODE
        }
        if (courses.isNotEmpty()) return@withContext false

        val tasks = db.taskDao().getAllActiveTasksSyncIncludingArchived().filter { !it.isDeleted }
        if (tasks.isNotEmpty()) return@withContext false

        val notes = db.noteDao().getActiveNotesSyncIncludingArchived()
        if (notes.isNotEmpty()) return@withContext false

        true
    }

    /** The slot a backup is written into, and when that slot last became the newest copy. */
    private data class SlotChoice(val file: DriveFile, val rotatedAt: Long)

    /**
     * Chooses where the next backup goes.
     *
     * Backups can now happen minutes apart. If every one rotated to the other slot, both slots
     * would soon hold the same recent state and an older good copy would be gone. So the newest
     * slot is overwritten in place, and the other slot is kept as history. The slots swap roles
     * only when the newest slot became the newest at least [rotateAfterMs] ago (a day by default),
     * which leaves the history copy one to two days old. A slot without a "rotatedAt" mark, made
     * by an older app version, counts as due and rotates once.
     */
    private suspend fun pickTargetSlot(
        store: DriveStore,
        slot1: DriveFile?,
        slot2: DriveFile?,
        now: Long,
        rotateAfterMs: Long
    ): SlotChoice {
        if (slot1 == null) {
            return SlotChoice(store.create(SLOT_1_NAME), now)
        }
        if (slot2 == null) {
            return SlotChoice(store.create(SLOT_2_NAME), now)
        }
        val seq1 = slot1.props["seq"]?.toLongOrNull() ?: 0L
        val seq2 = slot2.props["seq"]?.toLongOrNull() ?: 0L
        val older = if (seq1 <= seq2) slot1 else slot2
        val latest = if (seq1 <= seq2) slot2 else slot1

        val latestRotatedAt = latest.props["rotatedAt"]?.toLongOrNull() ?: 0L
        val sinceRotation = now - latestRotatedAt
        // A clock set back (negative) counts as due, so history is never stuck.
        val keepHistory = latestRotatedAt > 0L && sinceRotation in 0 until rotateAfterMs
        return if (keepHistory) SlotChoice(latest, latestRotatedAt) else SlotChoice(older, now)
    }

    private fun pickLatestSlot(slot1: DriveFile?, slot2: DriveFile?): DriveFile? {
        val info1 = slot1?.let { infoFromSlot(it) }
        val info2 = slot2?.let { infoFromSlot(it) }
        return when {
            info1 != null && info2 != null -> if (info1.seq >= info2.seq) slot1 else slot2
            info1 != null -> slot1
            info2 != null -> slot2
            else -> null
        }
    }

    internal fun infoFromSlot(file: DriveFile): DriveBackupInfo? {
        val seq = file.props["seq"]?.toLongOrNull() ?: return null
        if (seq <= 0) return null
        val exportedAt = file.props["exportedAt"]?.toLongOrNull() ?: return null
        val formatVersion = file.props["formatVersion"]?.toIntOrNull() ?: return null
        val courses = file.props["courses"]?.toIntOrNull() ?: 0
        val tasks = file.props["tasks"]?.toIntOrNull() ?: 0
        val myTasks = file.props["myTasks"]?.toIntOrNull() ?: 0
        val notes = file.props["notes"]?.toIntOrNull() ?: 0
        val images = file.props["images"]?.toIntOrNull() ?: 0
        val attachments = file.props["attachments"]?.toIntOrNull() ?: 0
        val appVersion = file.props["appVersion"] ?: ""
        return DriveBackupInfo(
            slotFileId = file.id,
            seq = seq,
            exportedAt = exportedAt,
            courses = courses,
            tasks = tasks,
            notes = notes,
            images = images,
            appVersion = appVersion,
            formatVersion = formatVersion,
            sizeBytes = file.size,
            attachments = attachments,
            myTasks = myTasks
        )
    }

    internal suspend fun computeLocalDataHash(context: Context): String {
        val prefs = DrivePrefs(context)
        val built = LocalBackupManager.buildData(
            context = context,
            inlineImages = false,
            includeAttachments = prefs.includeAttachments
        )
        built.data.put("settings", SettingsSnapshot.capture(context))
        return sha256Hex(built.data.toString())
    }

    internal fun sha256Hex(text: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
