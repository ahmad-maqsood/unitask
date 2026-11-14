package com.example.util

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.Course
import com.example.data.local.isArchived
import com.example.data.local.LectureSlot
import com.example.data.local.MY_TASKS_COURSE_CODE
import com.example.data.local.Note
import com.example.data.local.NoteFolder
import com.example.data.local.Priority
import com.example.data.local.TaskAttachment
import com.example.data.local.TaskItem
import com.example.data.local.TaskStatus
import com.example.data.local.computeDeadlineMillis
import com.example.data.local.decodeReminderOptions
import com.example.data.local.decodeRepeatRule
import com.example.data.local.encodeReminderOptions
import com.example.data.repository.NoteRepository
import com.example.reminder.ClassReminderScheduler
import com.example.reminder.ReminderScheduler
import com.example.util.drive.SettingsSnapshot
import com.example.util.markdown.referencedImageNames
import com.example.util.timer.TimerHistoryStore
import com.example.util.timer.TimerSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class BackupCounts(
    val courses: Int,
    val slots: Int,
    val tasks: Int,
    val myTasks: Int = 0,
    val notes: Int = 0,
    val images: Int = 0,
    val skippedImages: Int = 0,
    val attachments: Int = 0,
    val skippedAttachments: Int = 0,
    val settingsApplied: Int = 0,
    /** Study sessions: how many were saved into a backup, or added back by a restore. */
    val sessions: Int = 0
)

/**
 * In-memory representation of exported data ready to be packaged or uploaded.
 */
data class BuiltBackup(
    val data: JSONObject,
    val counts: BackupCounts,
    val referencedImages: List<String>,
    val referencedAttachments: List<String> = emptyList()
)

/**
 * Source for fetching external backup files during a restore.
 */
interface BackupFileSource {
    suspend fun noteImage(name: String): ByteArray?
    suspend fun downloadAttachment(storedName: String, target: File): Boolean = false
}

sealed class BackupResult {
    data class Success(val counts: BackupCounts) : BackupResult()
    data class Failure(val message: String, val wrongPassword: Boolean = false) : BackupResult()
}

/**
 * Offline backup of courses, timetable slots and assignments to ONE .json file.
 *
 * The file is chosen through the system file picker (Storage Access Framework), so the app needs
 * no storage permission and works with scoped storage on every Android version. The user can save
 * it to Downloads, Google Drive, a USB drive, and so on, and restore it on any phone without
 * signing in. An optional password encrypts the content (AES-256-GCM, key from PBKDF2).
 *
 * Restoring MERGES: what is already on the phone is kept, missing items are added, and items the
 * user deleted earlier come back. Study sessions from the timer are merged the same way: ones
 * already on the phone are kept and only missing ones are added. App settings are included, but
 * attachment files are not part of this file backup.
 */
object LocalBackupManager {

    private const val FORMAT = "unitask-backup"
    private const val FORMAT_VERSION = 1
    private const val MAX_FILE_BYTES = 20 * 1024 * 1024
    const val MAX_BACKUP_IMAGE_BYTES: Long = 20L * 1024 * 1024
    const val MAX_ATTACHMENT_BYTES: Long = 25L * 1024 * 1024
    private const val MAX_RESTORED_SESSIONS = 5_000
    private val ATTACHMENT_NAME_REGEX = Regex("^[A-Za-z0-9._-]{1,200}$")

    fun isValidAttachmentName(name: String): Boolean {
        return name.isNotEmpty() && !name.contains("..") && ATTACHMENT_NAME_REGEX.matches(name)
    }

    private const val PBKDF2_ITERATIONS = 600_000
    private const val MIN_ITERATIONS = 10_000
    private const val MAX_ITERATIONS = 2_000_000
    // SHA-256 is missing before Android 8; the one used is written into the file.
    private val KDF_CHOICES = listOf("PBKDF2WithHmacSHA256", "PBKDF2WithHmacSHA1")

    enum class Inspection { PLAIN, ENCRYPTED, INVALID }

    // ------------------------------------------------------------------ inspect

    /** Tells the UI whether the chosen file needs a password before restoring. */
    suspend fun inspect(context: Context, uri: Uri): Inspection = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(readFile(context, uri))
            when {
                root.optString("format") != FORMAT -> Inspection.INVALID
                root.optBoolean("encrypted", false) -> Inspection.ENCRYPTED
                else -> Inspection.PLAIN
            }
        } catch (_: Exception) {
            Inspection.INVALID
        }
    }

    // ------------------------------------------------------------------ buildData

    /**
     * Reads database and constructs the data JSONObject and summary counts.
     * With inlineImages = false, images are not embedded into the JSON and their names
     * are stored under "noteImageNames".
     */
    suspend fun buildData(
        context: Context,
        inlineImages: Boolean,
        maxImageBytes: Long = MAX_BACKUP_IMAGE_BYTES,
        includeAttachments: Boolean = false
    ): BuiltBackup =
        withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            val courses = db.courseDao().getAllCoursesListIncludingArchived().filter { !it.isDeleted }
            val codeById = courses.associate { it.id to it.code }
            val slots = db.lectureSlotDao().getAllSlotsSyncIncludingArchived()
                .filter { !it.isDeleted && it.courseId in codeById }
            val tasks = db.taskDao().getAllActiveTasksSyncIncludingArchived()
                .filter { !it.isDeleted && it.courseId in codeById }
            val notes = db.noteDao().getActiveNotesSyncIncludingArchived()
            val noteFolders = db.noteDao().getAllFoldersSync()
            val folderNameById = noteFolders.associate { it.id to it.name }

            val courseArray = JSONArray()
            for (c in courses) {
                val obj = JSONObject()
                    .put("code", c.code)
                    .put("name", c.name)
                    .put("instructor", c.instructor)
                    .put("colorHex", c.colorHex)
                    .put("location", c.location)
                if (c.archivedSemester.isNotBlank()) {
                    obj.put("archivedSemester", c.archivedSemester)
                }
                courseArray.put(obj)
            }
            val slotArray = JSONArray()
            for (s in slots) {
                slotArray.put(
                    JSONObject()
                        .put("courseCode", codeById[s.courseId] ?: "")
                        .put("dayOfWeek", s.dayOfWeek)
                        .put("startHour", s.startHour)
                        .put("startMinute", s.startMinute)
                        .put("endHour", s.endHour)
                        .put("endMinute", s.endMinute)
                        .put("classroom", s.classroom)
                        .put("instructor", s.instructor)
                )
            }
            val taskArray = JSONArray()
            for (t in tasks) {
                taskArray.put(
                    JSONObject()
                        .put("courseCode", codeById[t.courseId] ?: "")
                        .put("title", t.title)
                        .put("description", t.description)
                        .put("dueDateEpochMillis", t.dueDateEpochMillis)
                        .put("dueTimeHour", t.dueTimeHour)
                        .put("dueTimeMinute", t.dueTimeMinute)
                        .put("priority", t.priority.name)
                        .put("status", t.status.name)
                        .put("reminderOptions", encodeReminderOptions(t.reminderOptions))
                        .put("createdAt", t.createdAt)
                        .put("repeatRule", t.repeatRule)
                        .put("completedAtMillis", t.completedAtMillis)
                        .put("repeatAnchorDay", t.repeatAnchorDay)
                        .put("autoDeleteWhenDone", t.autoDeleteWhenDone)
                )
            }
            val noteArray = JSONArray()
            for (n in notes) {
                val noteObj = JSONObject()
                    .put("title", n.title)
                    .put("body", n.body)
                    .put("courseCode", n.courseId?.let { codeById[it] } ?: "")
                    .put("isPinned", n.isPinned)
                    .put("keepScreenOn", n.keepScreenOn)
                    .put("createdAt", n.createdAt)
                    .put("updatedAt", n.updatedAt)
                // Folders are saved by name, because ids differ from phone to phone.
                // The key is left out for unfiled notes, so a backup of someone who never
                // used folders is what it was before they existed.
                n.folderId?.let { folderNameById[it] }?.let { noteObj.put("folderName", it) }
                noteArray.put(noteObj)
            }

            val referencedNames = LinkedHashSet<String>()
            for (n in notes) {
                referencedNames.addAll(referencedImageNames(n.body))
            }

            val existingImages = mutableListOf<String>()
            for (name in referencedNames) {
                if (!NoteImages.isValidImageName(name)) continue
                val file = NoteImages.imageFile(context, name) ?: continue
                if (file.exists() && file.isFile) {
                    existingImages.add(name)
                }
            }

            val data = JSONObject()
                .put("courses", courseArray)
                .put("slots", slotArray)
                .put("tasks", taskArray)
                .put("notes", noteArray)

            // Saved so folders that are still empty survive a restore too
            if (noteFolders.isNotEmpty()) {
                val folderArray = JSONArray()
                val folderColors = JSONObject()
                for (f in noteFolders) {
                    folderArray.put(f.name)
                    if (f.colorHex.isNotEmpty()) folderColors.put(f.name, f.colorHex)
                }
                data.put("noteFolders", folderArray)
                // Left out when no folder has a colour. Older backups simply have no colours.
                if (folderColors.length() > 0) data.put("noteFolderColors", folderColors)
            }

            // Study history. The key is left out when there is none, so a backup of someone who
            // never used the timer is byte-for-byte what it was before this existed.
            val studySessions = TimerHistoryStore.get(context).sessions.value
            if (studySessions.isNotEmpty()) {
                val sessionArray = JSONArray()
                studySessions.forEach { sessionArray.put(TimerHistoryStore.sessionToJson(it)) }
                data.put("timerSessions", sessionArray)
            }

            // Progress history: when tasks were ticked, so the streak and weekly bars survive a
            // restore. Only the times are saved (task numbers change on another phone). The key is
            // left out when there is none.
            val completionTimes = CompletionLog.entries(context).map { it.completedAt }.distinct()
            if (completionTimes.isNotEmpty()) {
                val completionArray = JSONArray()
                completionTimes.forEach { completionArray.put(it) }
                data.put("completionLog", completionArray)
            }

            var exportedImagesCount = 0
            var skippedImagesCount = 0

            if (inlineImages) {
                var totalImageBytes = 0L
                val imageArray = JSONArray()
                for (name in existingImages) {
                    val file = NoteImages.imageFile(context, name) ?: continue
                    val fileSize = file.length()
                    if (totalImageBytes + fileSize > maxImageBytes) {
                        skippedImagesCount++
                        continue
                    }

                    val bytes = try {
                        file.readBytes()
                    } catch (e: Exception) {
                        Log.w("UniTask", "Could not read the image file for the backup", e)
                        // Count it, so the backup summary does not claim every image was saved.
                        skippedImagesCount++
                        continue
                    }

                    imageArray.put(
                        JSONObject()
                            .put("name", name)
                            .put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))
                    )
                    totalImageBytes += fileSize
                    exportedImagesCount++
                }
                data.put("noteImages", imageArray)
            } else {
                val namesArray = JSONArray()
                for (name in existingImages) {
                    namesArray.put(name)
                }
                data.put("noteImageNames", namesArray)
                exportedImagesCount = existingImages.size
                skippedImagesCount = 0
            }

            val exportedCoursesCount = courses.count {
                normalizeCourseCode(it.code) != MY_TASKS_COURSE_CODE && !it.isArchived
            }
            val exportedMyTasksCount = tasks.count {
                val code = codeById[it.courseId] ?: ""
                normalizeCourseCode(code) == MY_TASKS_COURSE_CODE
            }
            val exportedTasksCount = tasks.size - exportedMyTasksCount

            val referencedAttachments = mutableListOf<String>()
            var exportedAttachmentsCount = 0
            var skippedAttachmentsCount = 0

            if (includeAttachments) {
                val attachmentsDir = File(context.filesDir, "attachments")
                val canonicalDirPath = attachmentsDir.canonicalFile.path
                val prefix = if (canonicalDirPath.endsWith(File.separator)) canonicalDirPath else canonicalDirPath + File.separator
                val attDao = db.taskAttachmentDao()
                val attachmentArray = JSONArray()

                for ((taskIndex, task) in tasks.withIndex()) {
                    val taskAtts = attDao.getAttachmentsForTaskSync(task.id)
                    for (att in taskAtts) {
                        val storedName = File(att.relativePath).name
                        if (!isValidAttachmentName(storedName)) {
                            skippedAttachmentsCount++
                            continue
                        }
                        val file = File(attachmentsDir, storedName)
                        if (!file.canonicalFile.path.startsWith(prefix) || !file.exists() || !file.isFile) {
                            skippedAttachmentsCount++
                            continue
                        }
                        val fileSize = file.length()
                        if (fileSize <= 0L || fileSize > MAX_ATTACHMENT_BYTES) {
                            skippedAttachmentsCount++
                            continue
                        }

                        val entry = JSONObject()
                            .put("taskIndex", taskIndex)
                            .put("fileName", att.fileName)
                            .put("storedName", storedName)
                            .put("fileSize", fileSize)
                            .put("mimeType", att.mimeType)
                            .put("createdAt", att.createdAt)
                        attachmentArray.put(entry)
                        referencedAttachments.add(storedName)
                        exportedAttachmentsCount++
                    }
                }
                data.put("attachments", attachmentArray)
            }

            BuiltBackup(
                data = data,
                counts = BackupCounts(
                    courses = exportedCoursesCount,
                    slots = slots.size,
                    tasks = exportedTasksCount,
                    myTasks = exportedMyTasksCount,
                    notes = notes.size,
                    images = exportedImagesCount,
                    skippedImages = skippedImagesCount,
                    attachments = exportedAttachmentsCount,
                    skippedAttachments = skippedAttachmentsCount,
                    sessions = studySessions.size
                ),
                referencedImages = existingImages,
                referencedAttachments = referencedAttachments
            )
        }

    // ------------------------------------------------------------------ export

    suspend fun export(
        context: Context,
        uri: Uri,
        password: String?,
        maxImageBytes: Long = MAX_BACKUP_IMAGE_BYTES
    ): BackupResult =
        withContext(Dispatchers.IO) {
            try {
                val built = buildData(context, inlineImages = true, maxImageBytes = maxImageBytes)
                // The same allowlisted app settings the Drive backup carries, so restoring a backup
                // file also brings back the phone's setup (theme, daily summary, timer and so on).
                built.data.put("settings", SettingsSnapshot.capture(context))

                val envelope = JSONObject()
                    .put("format", FORMAT)
                    .put("version", FORMAT_VERSION)
                    .put("app", "UniTask")
                    .put("exportedAt", System.currentTimeMillis())
                if (password.isNullOrEmpty()) {
                    envelope.put("encrypted", false)
                    envelope.put("data", built.data)
                } else {
                    envelope.put("encrypted", true)
                    encryptInto(envelope, built.data.toString(), password)
                }

                val stream = context.contentResolver.openOutputStream(uri, "wt")
                    ?: return@withContext BackupResult.Failure("Could not write to the chosen location.")
                stream.use { it.write(envelope.toString(2).toByteArray(Charsets.UTF_8)) }

                BackupReminder.recordLocalExport(context)
                BackupResult.Success(built.counts)
            } catch (e: Exception) {
                BackupResult.Failure(e.message ?: "The backup could not be saved.")
            }
        }

    // ------------------------------------------------------------------ restore

    suspend fun restore(context: Context, uri: Uri, password: String?): BackupResult =
        withContext(Dispatchers.IO) {
            try {
                val root = JSONObject(readFile(context, uri))
                if (root.optString("format") != FORMAT) {
                    return@withContext BackupResult.Failure("This is not a UniTask backup file.")
                }
                if (root.optInt("version", 0) > FORMAT_VERSION) {
                    return@withContext BackupResult.Failure(
                        "This backup was made by a newer version of UniTask. Update the app and try again."
                    )
                }

                val data: JSONObject = if (root.optBoolean("encrypted", false)) {
                    if (password.isNullOrEmpty()) {
                        return@withContext BackupResult.Failure("Enter the backup password.", wrongPassword = true)
                    }
                    val plain = try {
                        decrypt(root, password)
                    } catch (_: GeneralSecurityException) {
                        return@withContext BackupResult.Failure(
                            "Wrong password, or the backup file is damaged.",
                            wrongPassword = true
                        )
                    }
                    JSONObject(plain)
                } else {
                    root.getJSONObject("data")
                }

                val counts = applyBackupData(context, data, source = null)
                BackupResult.Success(counts)
            } catch (_: JSONException) {
                BackupResult.Failure("The file is damaged or is not a UniTask backup.")
            } catch (e: Exception) {
                BackupResult.Failure(e.message ?: "The backup could not be restored.")
            }
        }

    suspend fun applyBackupData(
        context: Context,
        data: JSONObject,
        source: BackupFileSource? = null
    ): BackupCounts = applyBackup(context, data, source)

    private suspend fun applyBackup(
        context: Context,
        data: JSONObject,
        source: BackupFileSource? = null
    ): BackupCounts {
        val db = AppDatabase.getDatabase(context)
        val courseDao = db.courseDao()
        val slotDao = db.lectureSlotDao()
        val taskDao = db.taskDao()
        val noteDao = db.noteDao()
        val reminderScheduler = ReminderScheduler(context)

        var addedCourses = 0
        var addedSlots = 0
        var addedTasks = 0
        var addedMyTasks = 0
        var addedNotes = 0
        val taskIdByIndex = HashMap<Int, Long>()

        // One transaction: either everything is restored or nothing is changed.
        db.withTransaction {
            val idByCode = HashMap<String, Long>()

            // ---- courses
            val courseArray = data.optJSONArray("courses") ?: JSONArray()
            for (i in 0 until courseArray.length()) {
                val o = courseArray.optJSONObject(i) ?: continue
                val code = o.optString("code").trim().uppercase()
                if (code.isBlank()) continue
                val norm = normalizeCourseCode(code)
                if (idByCode.containsKey(norm)) continue

                val name = o.optString("name").trim().ifBlank { code }
                val instructor = o.optString("instructor").trim()
                val colorHex = o.optString("colorHex").ifBlank { "#4F46E5" }
                val location = o.optString("location").trim()
                val archivedSemester = o.optString("archivedSemester").trim()

                val existing = courseDao.getCourseByNormalizedCode(norm)
                    ?: courseDao.getAnyCourseByNormalizedCode(norm)
                val isMyTasksCourse = norm == MY_TASKS_COURSE_CODE
                if (existing == null) {
                    val id = courseDao.insertCourse(
                        Course(
                            name = name,
                            code = code,
                            instructor = instructor,
                            colorHex = colorHex,
                            location = location,
                            isSynced = false,
                            archivedSemester = archivedSemester
                        )
                    )
                    idByCode[norm] = id
                    if (!isMyTasksCourse) addedCourses++
                } else if (existing.isDeleted) {
                    courseDao.updateCourse(
                        existing.copy(
                            name = name,
                            code = code,
                            instructor = instructor,
                            colorHex = colorHex,
                            location = location,
                            isDeleted = false,
                            isSynced = false
                        )
                    )
                    idByCode[norm] = existing.id
                    if (!isMyTasksCourse) addedCourses++
                } else {
                    // Already on this phone: keep what is here.
                    idByCode[norm] = existing.id
                }
            }

            // ---- timetable slots
            val slotArray = data.optJSONArray("slots") ?: JSONArray()
            for (i in 0 until slotArray.length()) {
                val o = slotArray.optJSONObject(i) ?: continue
                val norm = normalizeCourseCode(o.optString("courseCode"))
                val courseId = idByCode[norm] ?: continue
                val day = o.optInt("dayOfWeek", 0)
                val startHour = o.optInt("startHour", -1)
                val startMinute = o.optInt("startMinute", -1)
                val endHour = o.optInt("endHour", -1)
                val endMinute = o.optInt("endMinute", -1)
                if (day !in 1..7 || startHour !in 0..23 || endHour !in 0..23) continue
                if (startMinute !in 0..59 || endMinute !in 0..59) continue
                if (endHour * 60 + endMinute <= startHour * 60 + startMinute) continue
                val classroom = o.optString("classroom").trim()
                val instructor = o.optString("instructor").trim()

                val existing = slotDao.findMatchingSlot(courseId, day, startHour, startMinute)
                if (existing == null) {
                    val newSlot = LectureSlot(
                        courseId = courseId,
                        dayOfWeek = day,
                        startHour = startHour,
                        startMinute = startMinute,
                        endHour = endHour,
                        endMinute = endMinute,
                        classroom = classroom,
                        instructor = instructor,
                        isSynced = false
                    )

                    slotDao.insertSlot(newSlot)
                    addedSlots++
                } else if (existing.isDeleted) {

                    slotDao.updateSlot(
                        existing.copy(
                            endHour = endHour,
                            endMinute = endMinute,
                            classroom = classroom,
                            instructor = instructor,
                            isDeleted = false,
                            deletedAt = 0L,
                            isSynced = false
                        )
                    )
                    addedSlots++
                }
            }

            // ---- assignments
            val knownByCourse = HashMap<Long, List<TaskItem>>()
            val taskArray = data.optJSONArray("tasks") ?: JSONArray()

            for (i in 0 until taskArray.length()) {
                val o = taskArray.optJSONObject(i) ?: continue
                val norm = normalizeCourseCode(o.optString("courseCode"))
                val courseId = idByCode[norm] ?: continue
                val title = o.optString("title").trim()
                if (title.isBlank()) continue
                val dueEpoch = o.optLong("dueDateEpochMillis", 0L)
                if (dueEpoch <= 0L) continue
                val hour = o.optInt("dueTimeHour", 23).coerceIn(0, 23)
                val minute = o.optInt("dueTimeMinute", 59).coerceIn(0, 59)
                val deadline = computeDeadlineMillis(dueEpoch, hour, minute)
                val priority = try {
                    Priority.valueOf(o.optString("priority", "MEDIUM"))
                } catch (_: Exception) {
                    Priority.MEDIUM
                }
                val status = try {
                    TaskStatus.valueOf(o.optString("status", "PENDING"))
                } catch (_: Exception) {
                    TaskStatus.PENDING
                }
                val reminders = decodeReminderOptions(o.optString("reminderOptions", "NONE"))
                val description = o.optString("description")
                val createdAt = o.optLong("createdAt", System.currentTimeMillis())

                val rawRepeat = if (o.has("repeatRule")) o.optString("repeatRule") else "NONE"
                val repeatRule = decodeRepeatRule(rawRepeat).name
                val completedAtMillis = o.optLong("completedAtMillis", 0L)
                val repeatAnchorDay = o.optInt("repeatAnchorDay", 0).coerceIn(0, 31)
                val autoDeleteWhenDone = o.optBoolean("autoDeleteWhenDone", false)
                val isMyTask = norm == MY_TASKS_COURSE_CODE

                val known = knownByCourse.getOrPut(courseId) { taskDao.getAnyTasksForCourseSync(courseId) }
                val match = if (repeatRule != "NONE") {
                    known.firstOrNull { it.title.trim() == title && it.createdAt == createdAt }
                } else {
                    known.firstOrNull {
                        it.title.trim() == title &&
                            computeDeadlineMillis(it.dueDateEpochMillis, it.dueTimeHour, it.dueTimeMinute) == deadline
                    }
                }

                if (match == null) {
                    val base = TaskItem(
                        courseId = courseId,
                        title = title,
                        description = description,
                        dueDateEpochMillis = dueEpoch,
                        dueTimeHour = hour,
                        dueTimeMinute = minute,
                        priority = priority,
                        status = status,
                        reminderOptions = reminders,
                        repeatRule = repeatRule,
                        completedAtMillis = completedAtMillis,
                        repeatAnchorDay = repeatAnchorDay,
                        autoDeleteWhenDone = autoDeleteWhenDone,
                        isSynced = false,
                        createdAt = createdAt
                    )
                    val toInsert = base.copy(reminderEpochMillis = reminderScheduler.calculateReminderMillis(base))
                    val newId = taskDao.insertTask(toInsert)
                    taskIdByIndex[i] = newId
                    knownByCourse[courseId] = known + toInsert.copy(id = newId)
                    if (isMyTask) addedMyTasks++ else addedTasks++
                } else if (match.isDeleted) {
                    val revived = match.copy(
                        description = description,
                        priority = priority,
                        status = status,
                        reminderOptions = reminders,
                        repeatRule = repeatRule,
                        completedAtMillis = completedAtMillis,
                        repeatAnchorDay = repeatAnchorDay,
                        autoDeleteWhenDone = autoDeleteWhenDone,
                        isDeleted = false,
                        deletedAt = 0L,
                        isSynced = false
                    )
                    taskDao.updateTask(
                        revived.copy(reminderEpochMillis = reminderScheduler.calculateReminderMillis(revived))
                    )
                    taskIdByIndex[i] = match.id
                    knownByCourse[courseId] = known.map { if (it.id == match.id) revived else it }
                    if (isMyTask) addedMyTasks++ else addedTasks++
                } else {
                    taskIdByIndex[i] = match.id
                }
            }

            // ---- note folders (matched by name, ignoring case, so a restore never doubles them up)
            val folderColorsJson = data.optJSONObject("noteFolderColors")
            val folderIdByName = HashMap<String, Long>()
            for (f in noteDao.getAllFoldersSync()) folderIdByName.putIfAbsent(f.name.lowercase(), f.id)
            suspend fun folderIdFor(rawName: String): Long? {
                val name = rawName.trim().replace(Regex("\\s+"), " ").take(NoteRepository.MAX_FOLDER_NAME)
                if (name.isEmpty()) return null
                folderIdByName[name.lowercase()]?.let { return it }
                // A colour from the backup is only used for a folder this restore creates; an existing
                // folder keeps the colour it already has
                val color = NoteRepository.FOLDER_COLORS.firstOrNull {
                    it.equals(folderColorsJson?.optString(name).orEmpty(), ignoreCase = true)
                } ?: ""
                val newId = noteDao.insertFolder(NoteFolder(name = name, colorHex = color))
                folderIdByName[name.lowercase()] = newId
                return newId
            }
            val folderNameArray = data.optJSONArray("noteFolders")
            if (folderNameArray != null) {
                for (i in 0 until folderNameArray.length()) {
                    folderIdFor(folderNameArray.optString(i))
                }
            }

            // ---- notes
            val existingNotes = noteDao.getActiveNotesSyncIncludingArchived().toMutableList()
            val noteArray = data.optJSONArray("notes") ?: JSONArray()
            for (i in 0 until noteArray.length()) {
                val o = noteArray.optJSONObject(i) ?: continue
                val rawCourseCode = o.optString("courseCode").trim()
                val courseId = if (rawCourseCode.isNotBlank()) {
                    val norm = normalizeCourseCode(rawCourseCode)
                    idByCode[norm]
                } else {
                    null
                }

                // Same limits as the editor, so one oversized note cannot make a cloud batch fail
                val title = o.optString("title").take(NoteRepository.MAX_TITLE)
                val body = o.optString("body").take(NoteRepository.MAX_BODY)
                val isPinned = o.optBoolean("isPinned", false)
                val keepScreenOn = o.optBoolean("keepScreenOn", false)
                val createdAt = o.optLong("createdAt", System.currentTimeMillis())
                val updatedAt = o.optLong("updatedAt", createdAt)
                val folderId = folderIdFor(o.optString("folderName"))

                val duplicate = existingNotes.firstOrNull {
                    it.title == title && it.body == body && it.createdAt == createdAt
                }
                if (duplicate != null) {
                    continue
                }

                val newNote = Note(
                    title = title,
                    body = body,
                    courseId = courseId,
                    folderId = folderId,
                    isPinned = isPinned,
                    keepScreenOn = keepScreenOn,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                    isDeleted = false,
                    deletedAt = 0L,
                    isSynced = false,
                    cloudId = ""
                )
                val newId = noteDao.insert(newNote)
                existingNotes.add(newNote.copy(id = newId))
                addedNotes++
            }
        }

        // ---- note images
        var addedImages = 0
        var skippedImages = 0

        val imageArray = data.optJSONArray("noteImages")
        if (imageArray != null) {
            for (i in 0 until imageArray.length()) {
                try {
                    val o = imageArray.optJSONObject(i) ?: continue
                    val name = o.optString("name")
                    if (!NoteImages.isValidImageName(name)) continue
                    val rawData = o.optString("data")
                    if (rawData.isEmpty()) continue
                    val decoded = try {
                        Base64.decode(rawData, Base64.NO_WRAP)
                    } catch (e: Exception) {
                        Log.w("UniTask", "Could not decode an image from the backup file", e)
                        skippedImages++
                        continue
                    }
                    if (decoded.size > 5 * 1024 * 1024) {
                        skippedImages++
                        continue
                    }
                    val targetFile = NoteImages.imageFile(context, name) ?: continue
                    if (targetFile.exists()) continue
                    try {
                        targetFile.writeBytes(decoded)
                        addedImages++
                    } catch (e: Exception) {
                        Log.w("UniTask", "Could not write a restored image", e)
                        skippedImages++
                    }
                } catch (e: Exception) {
                    Log.w("UniTask", "Could not restore an image from the backup", e)
                    skippedImages++
                }
            }
        }

        val imageNamesArray = data.optJSONArray("noteImageNames")
        if (imageNamesArray != null && source != null) {
            for (i in 0 until imageNamesArray.length()) {
                try {
                    val name = imageNamesArray.optString(i)
                    if (!NoteImages.isValidImageName(name)) continue
                    val targetFile = NoteImages.imageFile(context, name) ?: continue
                    if (targetFile.exists()) continue

                    val bytes = try {
                        source.noteImage(name)
                    } catch (e: Exception) {
                        Log.w("UniTask", "Could not download a note image", e)
                        null
                    }
                    if (bytes == null) {
                        skippedImages++
                        continue
                    }
                    if (bytes.size > 5 * 1024 * 1024) {
                        skippedImages++
                        continue
                    }
                    try {
                        targetFile.writeBytes(bytes)
                        addedImages++
                    } catch (e: Exception) {
                        Log.w("UniTask", "Could not write a restored image", e)
                        skippedImages++
                    }
                } catch (e: Exception) {
                    Log.w("UniTask", "Could not restore an image from the backup", e)
                    skippedImages++
                }
            }
        }

        // ---- assignment attachments
        var addedAttachments = 0
        var skippedAttachments = 0

        val attachmentArray = data.optJSONArray("attachments")
        if (attachmentArray != null && source != null) {
            val attachmentsDir = File(context.filesDir, "attachments")
            if (!attachmentsDir.exists()) {
                attachmentsDir.mkdirs()
            }
            val canonicalDirPath = attachmentsDir.canonicalFile.path
            val prefix = if (canonicalDirPath.endsWith(File.separator)) canonicalDirPath else canonicalDirPath + File.separator
            val taskAttachmentDao = db.taskAttachmentDao()

            for (i in 0 until attachmentArray.length()) {
                try {
                    val o = attachmentArray.optJSONObject(i)
                    if (o == null) {
                        skippedAttachments++
                        continue
                    }
                    val taskIndex = o.optInt("taskIndex", -1)
                    val taskId = taskIdByIndex[taskIndex]
                    if (taskId == null) {
                        skippedAttachments++
                        continue
                    }

                    val storedName = o.optString("storedName")
                    if (!isValidAttachmentName(storedName)) {
                        skippedAttachments++
                        continue
                    }

                    val fileName = o.optString("fileName").trim().ifEmpty { storedName }
                    val fileSize = o.optLong("fileSize", -1L)
                    if (fileSize <= 0L || fileSize > MAX_ATTACHMENT_BYTES) {
                        skippedAttachments++
                        continue
                    }
                    val mimeType = o.optString("mimeType", "*/*").ifEmpty { "*/*" }
                    val createdAt = o.optLong("createdAt", System.currentTimeMillis())

                    val existingTaskAtts = taskAttachmentDao.getAttachmentsForTaskSync(taskId)
                    if (existingTaskAtts.any { it.fileName == fileName && it.fileSize == fileSize }) {
                        continue
                    }

                    var destName = storedName
                    var destFile = File(attachmentsDir, destName)
                    if (destFile.exists()) {
                        var counter = 1
                        destName = "restored_${System.currentTimeMillis()}_$storedName"
                        destFile = File(attachmentsDir, destName)
                        while (destFile.exists()) {
                            destName = "restored_${System.currentTimeMillis()}_${counter}_$storedName"
                            destFile = File(attachmentsDir, destName)
                            counter++
                        }
                    }

                    if (!destFile.canonicalFile.path.startsWith(prefix)) {
                        skippedAttachments++
                        continue
                    }

                    val downloaded = try {
                        source.downloadAttachment(storedName, destFile)
                    } catch (e: Exception) {
                        Log.w("UniTask", "Could not download an attachment", e)
                        false
                    }

                    if (!downloaded || !destFile.exists() || destFile.length() != fileSize || destFile.length() > MAX_ATTACHMENT_BYTES) {
                        try {
                            if (destFile.exists()) destFile.delete()
                        } catch (e: Exception) { Log.w("UniTask", "Could not delete the partly downloaded attachment ${destFile.name}", e) }
                        skippedAttachments++
                        continue
                    }

                    val attachment = TaskAttachment(
                        taskId = taskId,
                        fileName = fileName,
                        relativePath = "attachments/$destName",
                        fileSize = destFile.length(),
                        mimeType = mimeType,
                        createdAt = createdAt
                    )
                    taskAttachmentDao.insertAttachment(attachment)
                    addedAttachments++
                } catch (e: Exception) {
                    Log.w("UniTask", "Could not restore an attachment", e)
                    skippedAttachments++
                }
            }
        }

        // ---- app settings
        val settingsApplied = if (data.has("settings")) {
            SettingsSnapshot.apply(context, data.optJSONObject("settings"))
        } else {
            0
        }

        // ---- study history (merged: sessions already on this phone are kept)
        val sessionArray = data.optJSONArray("timerSessions")
        val addedSessions = if (sessionArray != null) {
            val parsed = ArrayList<TimerSession>()
            for (i in 0 until minOf(sessionArray.length(), MAX_RESTORED_SESSIONS)) {
                val o = sessionArray.optJSONObject(i) ?: continue
                val session = try {
                    TimerHistoryStore.sessionFromJson(o)
                } catch (e: Exception) {
                    Log.w("UniTask", "Skipped a study session that could not be read", e)
                    null
                }
                if (session != null) parsed.add(session)
            }
            TimerHistoryStore.get(context).addAll(parsed)
        } else {
            0
        }

        // ---- progress history (merged: ticks already on this phone are kept)
        val completionArray = data.optJSONArray("completionLog")
        if (completionArray != null) {
            val times = ArrayList<Long>()
            for (i in 0 until minOf(completionArray.length(), CompletionLog.MAX_ITEMS)) {
                val time = completionArray.optLong(i, 0L)
                if (time > 0L) times.add(time)
            }
            CompletionLog.restoreTimes(context, times)
        }

        // Data is safe in Room now: put alarms back.
        reminderScheduler.rescheduleAll(db)
        ClassReminderScheduler(context).rescheduleAll(db)

        return BackupCounts(
            courses = addedCourses,
            slots = addedSlots,
            tasks = addedTasks,
            myTasks = addedMyTasks,
            notes = addedNotes,
            images = addedImages,
            skippedImages = skippedImages,
            attachments = addedAttachments,
            skippedAttachments = skippedAttachments,
            settingsApplied = settingsApplied,
            sessions = addedSessions
        )
    }

    // ------------------------------------------------------------------ file + crypto helpers

    private fun readFile(context: Context, uri: Uri): String {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Could not open the chosen file.")
        val text = input.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_FILE_BYTES) {
                    throw IllegalStateException("This file is too large to be a UniTask backup.")
                }
                out.write(buffer, 0, read)
            }
            out.toString("UTF-8")
        }
        return text.removePrefix("\uFEFF")
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int, kdf: String): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        try {
            val keyBytes = SecretKeyFactory.getInstance(kdf).generateSecret(spec).encoded
            return SecretKeySpec(keyBytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun encryptInto(envelope: JSONObject, plain: String, password: String) {
        val random = SecureRandom()
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val iv = ByteArray(12).also { random.nextBytes(it) }

        var chosenKdf = ""
        var key: SecretKeySpec? = null
        for (kdf in KDF_CHOICES) {
            try {
                key = deriveKey(password, salt, PBKDF2_ITERATIONS, kdf)
                chosenKdf = kdf
                break
            } catch (_: Exception) {
                // try the next one
            }
        }
        val secret = key ?: throw IllegalStateException("Encryption is not available on this phone.")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secret, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))

        envelope.put("kdf", chosenKdf)
        envelope.put("iterations", PBKDF2_ITERATIONS)
        envelope.put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
        envelope.put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
        envelope.put("payload", Base64.encodeToString(encrypted, Base64.NO_WRAP))
    }

    /** Throws [GeneralSecurityException] when the password is wrong or the data was changed. */
    private fun decrypt(root: JSONObject, password: String): String {
        val kdf = root.optString("kdf")
        val iterations = root.optInt("iterations", 0)
        if (kdf !in KDF_CHOICES || iterations !in MIN_ITERATIONS..MAX_ITERATIONS) {
            throw JSONException("Unsupported encryption settings")
        }
        val salt = Base64.decode(root.getString("salt"), Base64.NO_WRAP)
        val iv = Base64.decode(root.getString("iv"), Base64.NO_WRAP)
        val payload = Base64.decode(root.getString("payload"), Base64.NO_WRAP)

        val key = deriveKey(password, salt, iterations, kdf)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return String(cipher.doFinal(payload), Charsets.UTF_8)
    }
}
