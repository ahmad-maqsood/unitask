package com.example.util

import android.content.Context
import com.example.R

/** The "Due Fri, Nov 7 at 5:00 PM" line shown in reminders, in the language the app is using. */
object DueText {
    fun dueAt(context: Context, millis: Long): String =
        context.getString(R.string.reminder_due_at, DateFormats.shortDate(millis), DateFormats.clock(millis))
}
