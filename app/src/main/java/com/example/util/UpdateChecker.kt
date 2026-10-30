package com.example.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

sealed class UpdateResult {
    data class Available(val latestVersion: String, val pageUrl: String) : UpdateResult()
    data class UpToDate(val currentVersion: String) : UpdateResult()
    data class Failed(val message: String) : UpdateResult()
}

/**
 * Asks GitHub for the newest release of the app and compares its tag (for example "v1.0.1")
 * with this app's versionName (app/build.gradle.kts). Remember to raise versionName
 * whenever you publish a new release.
 */
object UpdateChecker {

    private const val LATEST_URL =
        "https://api.github.com/repos/ahmad-maqsood/unitask/releases/latest"
    private const val RELEASES_PAGE =
        "https://github.com/ahmad-maqsood/unitask/releases/latest"

    fun currentVersion(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
    } catch (_: Exception) {
        "0"
    }

    suspend fun check(context: Context): UpdateResult = withContext(Dispatchers.IO) {
        val current = currentVersion(context)
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(LATEST_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "UniTask-Android/$current")
            }
            val code = connection.responseCode
            if (code == 200) {
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val tag = json.optString("tag_name")
                if (tag.isBlank()) {
                    UpdateResult.Failed("No release found on GitHub.")
                } else if (isNewer(tag, current)) {
                    UpdateResult.Available(tag, json.optString("html_url").ifBlank { RELEASES_PAGE })
                } else {
                    UpdateResult.UpToDate(current)
                }
            } else if (code == 404) {
                UpdateResult.Failed("No release has been published on GitHub yet.")
            } else {
                UpdateResult.Failed("GitHub answered with error $code.")
            }
        } catch (_: Exception) {
            UpdateResult.Failed("Could not reach GitHub. Check your internet connection.")
        } finally {
            connection?.disconnect()
        }
    }

    /** "v1.2.0" is newer than "1.1"; missing parts count as 0. */
    fun isNewer(latestTag: String, current: String): Boolean {
        val a = versionParts(latestTag)
        val b = versionParts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun versionParts(version: String): List<Int> =
        version.trim().removePrefix("v").removePrefix("V")
            .split(".", "-", "+")
            .map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
}
