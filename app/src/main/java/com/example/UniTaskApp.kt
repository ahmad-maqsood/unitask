package com.example

import android.app.Application
import com.example.util.crash.CrashReporter

class UniTaskApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Saves unexpected crashes to a log file, so the next start can offer to send it.
        CrashReporter.install(this)
    }
}
