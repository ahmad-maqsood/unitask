package com.example.util.crash

import android.app.Application
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.system.exitProcess

/** The newest crash that the person has not seen yet. */
data class CrashReport(val entry: String, val summary: String)

/**
 * Saves unexpected crashes to a small log file so the next start can offer to send it.
 * UniTask has no crash reporting service: nothing leaves the phone unless the person shares it.
 */
object CrashReporter {

    private const val DIR = "crash"
    private const val LOG_FILE = "crash_log.txt"
    private const val UNSEEN_FILE = "crash_unseen"
    private const val SHARE_FILE = "unitask-crash-log.txt"

    /**
     * Makes every crash go through [record] first. The earlier handler still runs afterwards, so
     * the system closes the app exactly as it did before.
     */
    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            record(app, thread, error)
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }

    /** Writes one crash to the log. Never throws: the logger must not make a crash worse. */
    fun record(context: Context, thread: Thread, error: Throwable) {
        try {
            val entry = CrashLog.formatEntry(
                timeMillis = System.currentTimeMillis(),
                appVersion = BuildConfig.VERSION_NAME,
                androidInfo = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                device = "${Build.MANUFACTURER} ${Build.MODEL}",
                threadName = thread.name,
                error = error
            )
            val dir = File(context.filesDir, DIR)
            dir.mkdirs()
            val logFile = File(dir, LOG_FILE)
            val existing = if (logFile.exists()) logFile.readText() else ""
            logFile.writeText(CrashLog.appendEntry(existing, entry))
            File(dir, UNSEEN_FILE).writeText("1")
        } catch (_: Throwable) {
            // Nothing else can be done while the app is already crashing.
        }
    }

    /** The newest crash that was not dismissed yet, or null. Reads a file, so call it off the main thread. */
    fun loadUnseen(context: Context): CrashReport? {
        return try {
            val dir = File(context.filesDir, DIR)
            if (!File(dir, UNSEEN_FILE).exists()) return null
            val logFile = File(dir, LOG_FILE)
            if (!logFile.exists()) return null
            val entry = CrashLog.latestEntry(logFile.readText()) ?: return null
            CrashReport(entry = entry, summary = CrashLog.summaryOf(entry))
        } catch (_: Exception) {
            null
        }
    }

    /** Remembers that the person has seen the latest crash. The log file itself stays. */
    fun markSeen(context: Context) {
        try {
            File(File(context.filesDir, DIR), UNSEEN_FILE).delete()
        } catch (_: Exception) {
        }
    }

    /** Opens the share sheet with the whole crash log as a text file. */
    suspend fun shareLog(context: Context) = withContext(Dispatchers.IO) {
        try {
            val logFile = File(File(context.filesDir, DIR), LOG_FILE)
            if (!logFile.exists()) return@withContext
            // The cache folder "exports" is already allowed in file_paths.xml
            val exportsDir = File(context.cacheDir, "exports")
            exportsDir.mkdirs()
            val shared = File(exportsDir, SHARE_FILE)
            logFile.copyTo(shared, overwrite = true)

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", shared)
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                clipData = ClipData.newRawUri("", uri)
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "UniTask crash log (${BuildConfig.VERSION_NAME})")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(sendIntent, "Share crash log")
            if (context !is android.app.Activity) {
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            withContext(Dispatchers.Main) {
                try {
                    context.startActivity(chooser)
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
    }
}
