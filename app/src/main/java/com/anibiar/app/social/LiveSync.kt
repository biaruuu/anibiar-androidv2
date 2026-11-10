package com.anibiar.app.social

import com.anibiar.app.AniBiarApp
import com.anibiar.app.data.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Makes owner changes (announcements, update releases) show up without reopening the app.
 * [tick] changes when the owner pings the shared channel and, as a safety net, every 30 s while the app is on screen.
 * Screens that load owner data re-load whenever it changes. The ping carries no data; each device re-reads what its own account may see.
 */
object LiveSync {
    private const val CHANNEL = "kiro-live"
    private val _tick = MutableStateFlow(0L)
    val tick: StateFlow<Long> = _tick
    @Volatile private var rt: Realtime? = null

    fun ping(app: AniBiarApp) {
        _tick.value = _tick.value + 1
        rt?.send(CHANNEL, "refresh", buildJsonObject { put("t", System.currentTimeMillis()) })
    }

    fun start(app: AniBiarApp, scope: CoroutineScope) {
        scope.launch {
            AppState.fg.collectLatest { fg ->
                if (!fg) return@collectLatest
                _tick.value = _tick.value + 1
                val realtime = app.social.realtime
                rt = realtime
                val flow = realtime.join(CHANNEL)
                val poll = launch { while (true) { delay(30_000); _tick.value = _tick.value + 1 } }
                try { flow.collect { if (it.event == "refresh") _tick.value = _tick.value + 1 } }
                finally { poll.cancel(); realtime.leave(CHANNEL); rt = null }
            }
        }
    }
}
