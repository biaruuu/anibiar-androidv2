package com.anibiar.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** "Add to List": the ids the user saved, kept on-device. */
class Watchlist(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("watchlist", Context.MODE_PRIVATE)
    private val _ids = MutableStateFlow(
        prefs.getStringSet("ids", emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet(),
    )
    val ids: StateFlow<Set<Int>> = _ids.asStateFlow()

    fun toggle(id: Int) {
        val next = if (id in _ids.value) _ids.value - id else _ids.value + id
        _ids.value = next
        prefs.edit().putStringSet("ids", next.map { it.toString() }.toSet()).apply()
    }
}
