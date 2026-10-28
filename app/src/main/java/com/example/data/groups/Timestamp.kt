package com.example.data.groups

import java.util.Date

/** Minimal timestamp type for group data. */
data class Timestamp(private val millis: Long) {
    constructor(date: Date) : this(date.time)

    fun toDate(): Date = Date(millis)
}