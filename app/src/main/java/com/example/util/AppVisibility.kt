package com.example.util

import kotlinx.coroutines.flow.MutableSharedFlow

object AppVisibility {
    @Volatile
    var isForeground: Boolean = false
        set(value) {
            val cameBack = value && !field
            field = value
            if (cameBack) comesToForeground.tryEmit(Unit)
        }

    /** Fires each time the app goes from the background to the foreground. */
    val comesToForeground = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}
