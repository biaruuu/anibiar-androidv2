package com.anibiar.app.data

import kotlinx.coroutines.flow.MutableStateFlow

/** Picture-in-picture state shared between the activity and the player. */
object Pip {
    val active = MutableStateFlow(false)
    /** True while a video is playing on screen, so leaving the app should shrink it into a floating window. */
    @Volatile var canEnter = false
}
