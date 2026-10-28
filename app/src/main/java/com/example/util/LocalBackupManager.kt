package com.example.util

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.Course
import com.example.data.local.LectureSlot
import com.example.data.local.Priority
import com.example.data.local.TaskItem
import com.example.data.local.TaskStatus
import com.example.data.local.computeDeadlineMillis
import com.example.data.local.decodeReminderOptions
import com.example.data.local.encodeReminderOptions
import com.example.reminder.ClassReminderScheduler
import com.example.reminder.ReminderScheduler
import com.example.sync.SupabaseSyncManager
import com.example.sync.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class BackupCounts(val courses: Int, val slots: Int, val tasks: Int)

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
 * user deleted earlier come back. Attachment files are not part of the backup.
 */
object LocalBackupManager {

    private const val FORMAT = "unitask-backup"
    private const val FORMAT_VERSION = 1
    private const val MAX_FILE_BYTES = 20 * 1024 * 1024
    private const val PBKDF2_ITERATIONS = 120_000
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

    // ------------------------------------------------------------------ export

    suspend fun export(context: Context, uri: Uri, password: String?): BackupResult =
        withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(context)
                val courses = db.courseDao().getAllCoursesList().filter { !it.isDeleted }
                val codeById = courses.associate { it.id to it.code }
                val slots = db.lectureSlotDao().getAllSlotsSync()
                    .filter { !it.isDeleted && it.courseId in codeById }
                val tasks = db.taskDao().getAllActiveTasksSync()
                    .filter { !it.isDeleted && it.courseId in codeById }

                val courseArray = JSONArray()
                for (c in courses) {
                    courseArray.put(
                        JSONObject()
                            .put("code", c.code)
                            .put("name", c.name)
                            .put("instructor", c.instructor)
                            .put("colorHex", c.colorHex)
                            .put("location", c.location)
                    )
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
                    )
                }
                val data = JSONObject()
                    .put("courses", courseArray)
                    .put("slots", slotArray)
                    .put("tasks", taskArray)

                val envelope = JSONObject()
                    .put("format", FORMAT)
                    .put("version", FORMAT_VERSION)
                    .put("app", "UniTask")
                    .put("exportedAt", System.currentTimeMillis())
                if (password.isNullOrEmpty()) {
                    envelope.put("encrypted", false)
                    envelope.put("data", data)
                } else {
                    envelope.put("encrypted", true)
                    encryptInto(envelope, data.toString(), password)
                }

                val stream = context.contentResolver.openOutputStream(uri, "wt")
                    ?: return@withContext BackupResult.Failure("Could not write to the chosen location.")
                stream.use { it.write(envelope.toString(2).toByteArray(Charsets.UTF_8)) }

                BackupResult.Success(BackupCounts(courses.size, slots.size, tasks.size))
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

                val counts = applyBackup(context, data)
                BackupResult.Success(counts)
            } catch (_: JSONException) {
                BackupResult.Failure("The file is damaged or is not a UniTask backup.")
            } catch (e: Exception) {
                BackupResult.Failure(e.message ?: "The backup could not be restored.")
            }
        }

    private suspend fun applyBackup(context: Context, data: JSONObject): BackupCounts {
        val db = AppDatabase.getDatabase(context)
        val courseDao = db.courseDao()
        val slotDao = db.lectureSlotDao()
        val taskDao = db.taskDao()
        val reminderScheduler = ReminderScheduler(context)

        var addedCourses = 0
        var addedSlots = 0
        var addedTasks = 0

        // One transaction: either everything is restored or nothing is changed.
        db.withTransaction {
            val idByCode = HashMap<String, Long>()

            // ---- courses
            val courseArray = data.optJSONArray("courses") ?: JSONArray()
            for (i in 0 until courseArray.length()) {
                val o = courseArray.optJSONObject(i) ?: continue
                val code = o.optString("code").trim().uppercase()
                if (code.isBlank()) continue
                val norm = SupabaseSyncManager.normalizeCode(code)
                if (idByCode.containsKey(norm)) continue

                SupabaseSyncManager.clearPendingDeletionForCourse(norm)
                val name = o.optString("name").trim().ifBlank { code }
                val instructor = o.optString("instructor").trim()
                val colorHex = o.optString("colorHex").ifBlank { "#4F46E5" }
                val location = o.optString("location").trim()

                val existing = courseDao.getCourseByNormalizedCode(norm)
                    ?: courseDao.getAnyCourseByNormalizedCode(norm)
                if (existing == null) {
                    val id = courseDao.insertCourse(
                        Course(
                            name = name,
                            code = code,
                            instructor = instructor,
                            colorHex = colorHex,
                            location = location,
                            isSynced = false
                        )
                    )
                    idByCode[norm] = id
                    addedCourses++
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
                    addedCourses++
                } else {
                    // Already on this phone: keep what is here.
                    idByCode[norm] = existing.id
                }
            }

            // ---- timetable slots
            val slotArray = data.optJSONArray("slots") ?: JSONArray()
            for (i in 0 until slotArray.length()) {
                val o = slotArray.optJSONObject(i) ?: continue
                val norm = SupabaseSyncManager.normalizeCode(o.optString("courseCode"))
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
                    SupabaseSyncManager.clearPendingDeletionForSlot(
                        SupabaseSyncManager.getSlotDocId(newSlot, norm)
                    )
                    slotDao.insertSlot(newSlot)
                    addedSlots++
                } else if (existing.isDeleted) {
                    SupabaseSyncManager.clearPendingDeletionForSlot(
                        SupabaseSyncManager.getSlotDocId(existing, norm)
                    )
                    slotDao.updateSlot(
                        existing.copy(
                            endHour = endHour,
                            endMinute = endMinute,
                            classroom = classroom,
                            instructor = instructor,
                            isDeleted = false,
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
                val norm = SupabaseSyncManager.normalizeCode(o.optString("courseCode"))
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

                val known = knownByCourse.getOrPut(courseId) { taskDao.getAnyTasksForCourseSync(courseId) }
                val match = known.firstOrNull {
                    it.title.trim() == title &&
                        computeDeadlineMillis(it.dueDateEpochMillis, it.dueTimeHour, it.dueTimeMinute) == deadline
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
                        isSynced = false,
                        createdAt = o.optLong("createdAt", System.currentTimeMillis())
                    )
                    val toInsert = base.copy(reminderEpochMillis = reminderScheduler.calculateReminderMillis(base))
                    val newId = taskDao.insertTask(toInsert)
                    knownByCourse[courseId] = known + toInsert.copy(id = newId)
                    addedTasks++
                } else if (match.isDeleted) {
                    val revived = match.copy(
                        description = description,
                        priority = priority,
                        status = status,
                        reminderOptions = reminders,
                        isDeleted = false,
                        isSynced = false
                    )
                    taskDao.updateTask(
                        revived.copy(reminderEpochMillis = reminderScheduler.calculateReminderMillis(revived))
                    )
                    addedTasks++
                }
            }
        }

        // Data is safe in Room now: put alarms back and let the cloud sync pick the new rows up.
        reminderScheduler.rescheduleAll(db)
        ClassReminderScheduler(context).rescheduleAll(db)
        try {
            if (currentSupabaseUid() != null) SyncScheduler.scheduleSync(context)
        } catch (_: Exception) {
        }

        return BackupCounts(addedCourses, addedSlots, addedTasks)
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