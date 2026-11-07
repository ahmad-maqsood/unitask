package com.example.util.drive

/**
 * Pure policy functions determining when existing Supabase sync users
 * should be prompted to back up to Google Drive.
 */
object DriveMigrationPrompt {

    const val MAX_PROMPT_COUNT = 3
    const val RETRY_INTERVAL_DAYS = 3L
    private const val THREE_DAYS_MS = 3L * 24 * 60 * 60 * 1000L

    /**
     * True only when the user has data, used cloud sync before, has never connected
     * Google Drive, has seen the prompt fewer than 3 times, and the retry backoff has passed.
     */
    fun shouldPrompt(
        hasData: Boolean,
        usedCloudSync: Boolean,
        driveConnected: Boolean,
        promptCount: Int,
        nextPromptAt: Long,
        now: Long
    ): Boolean {
        return hasData &&
            usedCloudSync &&
            !driveConnected &&
            promptCount < MAX_PROMPT_COUNT &&
            now >= nextPromptAt
    }

    /**
     * Increments the prompt count by 1 and schedules the next prompt for 3 days later.
     */
    fun afterLater(count: Int, now: Long): Pair<Int, Long> {
        return Pair(count + 1, now + THREE_DAYS_MS)
    }

    /**
     * Sets prompt count to 3 so the dialog will never be shown again.
     */
    fun afterNever(count: Int = 0): Int {
        return MAX_PROMPT_COUNT
    }
}
