package com.example.data.groups

import java.util.Date

/** Minimal stand-in for the old Firebase Timestamp. */
data class Timestamp(private val millis: Long) {
    constructor(date: Date) : this(date.time)

    fun toDate(): Date = Date(millis)
}