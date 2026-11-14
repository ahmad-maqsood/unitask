package com.example

import android.app.Application
import com.example.util.crash.CrashReporter

class UniTaskApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Lets every time label follow the phone's 12-hour / 24-hour setting.
        com.example.util.TimeFormatter.init(this)
        // Saves unexpected crashes to a log file, so the next start can offer to send it.
        CrashReporter.install(this)
        // Backs up to Google Drive shortly after data changes. Does nothing unless Drive backup is on.
        // It starts here, not only when a screen opens, so a change made by a notification button
        // while the app is closed is also noticed.
        com.example.util.drive.DriveChangeWatcher.start(this)
    }
}
